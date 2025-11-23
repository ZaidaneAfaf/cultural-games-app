package com.boardgames.rag.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class OllamaResponseService {

    private final ChatClient chatClient;
    private final ExecutorService executorService;
    private static final int TIMEOUT_SECONDS = 10;

    // Cache des réponses
    private final Map<String, CachedResponse> responseCache;
    private final Map<String, Integer> searchCounters;
    private final int MAX_CACHE_SIZE = 50;
    private final int POPULAR_THRESHOLD = 3;

    public OllamaResponseService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.executorService = Executors.newCachedThreadPool();
        this.responseCache = new ConcurrentHashMap<>();
        this.searchCounters = new ConcurrentHashMap<>();
    }

    /**
     * Génère une réponse NATURELLE avec cache intelligent
     */
    public String generateNaturalResponse(String question, List<Map<String, Object>> gameContext) {
        String cacheKey = getCacheKey(gameContext);
        searchCounters.merge(cacheKey, 1, Integer::sum);
        int searchCount = searchCounters.getOrDefault(cacheKey, 0);

        // Vérifier le cache
        CachedResponse cached = responseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            long age = System.currentTimeMillis() - cached.timestamp;
            System.out.println("💾 [CACHE HIT] Réponse récupérée du cache (" + searchCount + " recherches, age: " + age + "ms)");
            return cached.response;
        }

        System.out.println("🤖 [OLLAMA] Génération réponse naturelle... (recherche #" + searchCount + ")");
        long startTime = System.currentTimeMillis();

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                if (gameContext.isEmpty()) {
                    return askOllamaDirectly(question);
                }
                return askOllamaWithContext(question, gameContext);
            }, executorService);

            String response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // FORÇAGE DU FRANÇAIS - Vérifier et corriger si nécessaire
            response = ensureFrenchResponse(response, question);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⚡ Ollama répondu en " + duration + "ms");

            // Mettre en cache
            cacheResponse(cacheKey, response, searchCount >= POPULAR_THRESHOLD);

            return response;

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            System.err.println("⏰ TIMEOUT Ollama après " + duration + "ms - Utilisation fallback");

            if (!gameContext.isEmpty()) {
                return generateFallbackResponse(gameContext);
            }

            return "⏰ La génération de la réponse prend trop de temps. Veuillez réessayer.";
        } catch (Exception e) {
            System.err.println("❌ Erreur Ollama: " + e.getMessage());
            return "❌ Erreur lors de la génération de la réponse. Vérifiez qu'Ollama est bien démarré.";
        }
    }

    /**
     * FORÇAGE DU FRANÇAIS - Vérifie et corrige la langue
     */
    private String ensureFrenchResponse(String response, String originalQuestion) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        // Détecter si la réponse est en anglais
        boolean isEnglish = detectEnglish(response);

        if (isEnglish) {
            System.out.println("🔄 Détection: Réponse en anglais, regénération en français...");

            // Regénérer en français avec des consignes plus strictes
            String frenchPrompt = String.format("""
                TRÈS IMPORTANT : RÉPONDS UNIQUEMENT EN FRANÇAIS. NE UTILISE PAS UN MOT D'ANGLAIS.
                
                Question originale : "%s"
                
                Ta réponse précédente (à ignorer) : %s
                
                Recrée une réponse COMPLÈTEMENT EN FRANÇAIS :
                - Utilise uniquement la langue française
                - Pas de mots anglais
                - Style naturel et conversationnel
                - 3-4 paragraphes maximum
                """, originalQuestion, response.substring(0, Math.min(100, response.length())));

            try {
                String correctedResponse = chatClient.prompt()
                        .user(frenchPrompt)
                        .call()
                        .content();

                // Vérifier à nouveau
                if (!detectEnglish(correctedResponse)) {
                    System.out.println("✅ Correction réussie - Réponse maintenant en français");
                    return correctedResponse;
                } else {
                    System.out.println("⚠️  La correction a échoué, utilisation du fallback français");
                    return generateFrenchFallback(originalQuestion);
                }
            } catch (Exception e) {
                System.err.println("❌ Erreur lors de la correction française: " + e.getMessage());
                return generateFrenchFallback(originalQuestion);
            }
        }

        return response;
    }

    /**
     * Détecte si le texte est en anglais
     */
    private boolean detectEnglish(String text) {
        if (text == null) return false;

        String lowerText = text.toLowerCase();

        // Mots clés anglais courants dans les réponses Ollama
        String[] englishIndicators = {
                "the", "and", "is", "in", "to", "of", "a", "that", "it", "with",
                "for", "as", "was", "on", "are", "this", "game", "player", "players",
                "rules", "strategy", "board", "card", "piece", "move", "win", "play"
        };

        // Mots clés français pour contre-vérifier
        String[] frenchIndicators = {
                "le", "la", "les", "un", "une", "des", "est", "dans", "pour", "avec",
                "sur", "qui", "que", "jeu", "joueur", "joueurs", "règles", "stratégie",
                "plateau", "carte", "pièce", "déplacement", "gagner", "jouer"
        };

        int englishCount = 0;
        int frenchCount = 0;

        for (String word : englishIndicators) {
            if (lowerText.contains(" " + word + " ") || lowerText.startsWith(word + " ")) {
                englishCount++;
            }
        }

        for (String word : frenchIndicators) {
            if (lowerText.contains(" " + word + " ") || lowerText.startsWith(word + " ")) {
                frenchCount++;
            }
        }

        // Si plus d'indicateurs anglais que français, considérer comme anglais
        return englishCount > frenchCount;
    }

    /**
     * Fallback en français garanti
     */
    private String generateFrenchFallback(String question) {
        return "Je ne peux pas fournir une réponse détaillée en français pour le moment. " +
                "Veuillez réessayer ou consulter les informations disponibles dans notre base de données.";
    }

    /**
     * Ollama répond SANS données (pure IA) - Version française renforcée
     */
    private String askOllamaDirectly(String question) {
        String prompt = String.format("""
            TRÈS IMPORTANT : RÉPONDS UNIQUEMENT EN FRANÇAIS. N'UTILISE AUCUN MOT ANGLAIS.
            
            Décris le jeu "%s" EN FRANÇAIS de manière complète :
            
            STRUCTURE OBLIGATOIRE (en français) :
            1. Description et contexte historique
            2. Règles principales et objectif du jeu  
            3. Conseils stratégiques de base
            4. Public cible et durée typique
            
            STYLE : Conversationnel, naturel, engageant.
            LANGUE : Français exclusivement.
            LONGUEUR : 4-5 paragraphes maximum.
            
            COMMENCE DIRECTEMENT TA RÉPONSE EN FRANÇAIS :
            """, question);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return ensureFrenchResponse(response, question);
        } catch (Exception e) {
            throw new RuntimeException("Ollama error: " + e.getMessage());
        }
    }

    /**
     * Ollama répond AVEC données enrichies - Version française renforcée
     */
    private String askOllamaWithContext(String question, List<Map<String, Object>> gameContext) {
        String contextData = formatContextForOllama(gameContext);

        String prompt = String.format("""
            TRÈS IMPORTANT : RÉPONDS UNIQUEMENT EN FRANÇAIS. PAS UN MOT D'ANGLAIS.
            
            CONTEXTE DU JEU :
            %s
            
            QUESTION : %s
            
            CRÉE UNE RÉPONSE EN FRANÇAIS avec :
            - 1 paragraphe sur l'histoire et le contexte
            - 1 paragraphe sur les règles principales  
            - 1 paragraphe de conseils pratiques
            - Style conversationnel et naturel
            
            UTILISE UNIQUEMENT LES INFORMATIONS DU CONTEXTE.
            RÉPONDS DIRECTEMENT EN FRANÇAIS :
            """, contextData, question);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return ensureFrenchResponse(response, question);
        } catch (Exception e) {
            throw new RuntimeException("Ollama error: " + e.getMessage());
        }
    }

    /**
     * Formate le contexte pour Ollama
     */
    private String formatContextForOllama(List<Map<String, Object>> gameContext) {
        if (gameContext.isEmpty()) return "";

        Map<String, Object> game = gameContext.get(0);
        StringBuilder context = new StringBuilder();

        context.append("Nom: ").append(game.get("name"));
        if (game.get("yearPublished") != null) {
            int year = (int) game.get("yearPublished");
            if (year < 0) {
                context.append(" (").append(Math.abs(year)).append(" av. J.-C.)");
            } else {
                context.append(" (").append(year).append(")");
            }
        }
        context.append("\n");

        if (game.get("description") != null) {
            context.append("Description: ").append(cleanText(game.get("description").toString())).append("\n");
        }

        if (game.get("wikipediaSummary") != null) {
            context.append("Histoire: ").append(cleanText(game.get("wikipediaSummary").toString())).append("\n");
        }

        if (game.get("rules") != null) {
            String rules = cleanText(game.get("rules").toString());
            context.append("Règles: ").append(rules.length() > 300 ? rules.substring(0, 300) + "..." : rules).append("\n");
        }

        if (game.get("minPlayers") != null && game.get("maxPlayers") != null) {
            context.append("Joueurs: ").append(game.get("minPlayers")).append("-").append(game.get("maxPlayers")).append("\n");
        }

        if (game.get("averageRating") != null) {
            context.append("Note moyenne: ").append(String.format("%.1f/10", game.get("averageRating"))).append("\n");
        }

        if (game.get("complexityWeight") != null) {
            context.append("Complexité: ").append(String.format("%.1f/5", game.get("complexityWeight"))).append("\n");
        }

        if (game.get("categories") != null && !((List<?>) game.get("categories")).isEmpty()) {
            context.append("Catégories: ").append(game.get("categories")).append("\n");
        }

        return context.toString();
    }

    /**
     * Génère une réponse de secours si Ollama timeout
     */
    private String generateFallbackResponse(List<Map<String, Object>> gameContext) {
        Map<String, Object> game = gameContext.get(0);
        StringBuilder response = new StringBuilder();

        String name = game.get("name").toString();
        Integer year = game.get("yearPublished") != null ? (Integer) game.get("yearPublished") : null;

        // Introduction en français
        if (year != null && year < 0) {
            response.append("**").append(name).append("** est un jeu antique fascinant qui remonte à ")
                    .append(Math.abs(year)).append(" ans avant notre ère. ");
        } else if (year != null) {
            response.append("**").append(name).append("** est un jeu publié en ").append(year).append(". ");
        } else {
            response.append("**").append(name).append("** est un jeu de société captivant. ");
        }

        // Description
        if (game.get("description") != null) {
            response.append(cleanText(game.get("description").toString())).append("\n\n");
        }

        // Histoire
        if (game.get("wikipediaSummary") != null) {
            response.append("**Contexte historique** : ").append(cleanText(game.get("wikipediaSummary").toString())).append("\n\n");
        }

        // Règles
        if (game.get("rules") != null) {
            response.append("**Règles principales** : ").append(cleanText(game.get("rules").toString())).append("\n\n");
        }

        // Infos pratiques
        List<String> infos = new ArrayList<>();
        if (game.get("minPlayers") != null && game.get("maxPlayers") != null) {
            infos.add("👥 " + game.get("minPlayers") + "-" + game.get("maxPlayers") + " joueurs");
        }
        if (game.get("averageRating") != null) {
            infos.add("⭐ " + String.format("%.1f/10", game.get("averageRating")));
        }
        if (game.get("complexityWeight") != null) {
            infos.add("🧠 Complexité " + String.format("%.1f/5", game.get("complexityWeight")));
        }

        if (!infos.isEmpty()) {
            response.append("**Caractéristiques** : ").append(String.join(" • ", infos));
        }

        return response.toString();
    }

    // [Les méthodes restantes restent identiques...]
    private String getCacheKey(List<Map<String, Object>> gameContext) {
        if (gameContext.isEmpty()) return "empty";
        Map<String, Object> game = gameContext.get(0);
        String name = game.get("name") != null ? game.get("name").toString() : "unknown";
        String year = game.get("yearPublished") != null ? game.get("yearPublished").toString() : "";
        return (name + "_" + year).toLowerCase().replaceAll("[^a-z0-9_]", "");
    }

    private void cacheResponse(String key, String response, boolean isPopular) {
        long ttl = isPopular ? 24 * 60 * 60 * 1000L : 60 * 60 * 1000L;
        responseCache.put(key, new CachedResponse(response, System.currentTimeMillis(), ttl, isPopular));
        if (responseCache.size() > MAX_CACHE_SIZE) cleanCache();
        System.out.println("💾 [CACHE] Réponse mise en cache (" + (isPopular ? "POPULAIRE" : "normal") + ")");
    }

    private void cleanCache() {
        // [Implémentation existante]
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private static class CachedResponse {
        final String response;
        final long timestamp;
        final long ttl;
        final boolean isPopular;
        CachedResponse(String response, long timestamp, long ttl, boolean isPopular) {
            this.response = response;
            this.timestamp = timestamp;
            this.ttl = ttl;
            this.isPopular = isPopular;
        }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > ttl; }
    }

    public String generateTurboResponse(String question, List<Map<String, Object>> gameContext) {
        return generateNaturalResponse(question, gameContext);
    }
}