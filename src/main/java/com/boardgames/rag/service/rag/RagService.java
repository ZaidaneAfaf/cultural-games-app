package com.boardgames.rag.service.rag;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.embedding.VectorStoreService;
import com.boardgames.rag.service.wikipedia.WikipediaService;
import com.boardgames.rag.service.ai.OllamaResponseService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final GameRepository gameRepository;
    private final WikipediaService wikipediaService;
    private final OllamaResponseService ollamaResponseService;
    private final Map<String, String> responseCache;

    public RagService(VectorStoreService vectorStoreService,
                      GameRepository gameRepository,
                      WikipediaService wikipediaService,
                      OllamaResponseService ollamaResponseService) {
        this.vectorStoreService = vectorStoreService;
        this.gameRepository = gameRepository;
        this.wikipediaService = wikipediaService;
        this.ollamaResponseService = ollamaResponseService;
        this.responseCache = new ConcurrentHashMap<>();
    }

    /**
     * RAG ULTRA-RAPIDE - Recherche exacte d'abord
     */
    public RagResponse searchWithRag(String question, int maxResults) {
        System.out.println("🚀 RAG ULTRA-RAPIDE: \"" + question + "\"");
        long startTime = System.currentTimeMillis();

        try {
            List<Game> relevantGames = new ArrayList<>();

            // 1. D'ABORD recherche par nom (très rapide)
            if (isSpecificGameSearch(question)) {
                String gameName = extractGameName(question);

                // TOUJOURS utiliser findByNameContainingIgnoreCase (gère les doublons)
                List<Game> exactMatches = gameRepository.findByNameContainingIgnoreCase(gameName);

                if (!exactMatches.isEmpty()) {
                    // Filtrer pour ne garder que les meilleurs résultats
                    List<Game> filteredMatches = filterBestMatches(exactMatches, gameName);
                    relevantGames.addAll(filteredMatches);
                    System.out.println("🎯 Recherche trouvée: " + filteredMatches.size() + " jeux sur " + exactMatches.size() + " trouvés");
                }
            }

            // 2. ENSUITE recherche vectorielle si besoin
            if (relevantGames.isEmpty()) {
                List<Document> vectorResults = vectorStoreService.searchGames(question, Math.min(maxResults, 3));
                System.out.println("📊 " + vectorResults.size() + " résultats vectoriels");
                relevantGames = getRelevantGamesOptimized(vectorResults, 3);
            }

            // Limiter à 3 jeux maximum pour la performance
            if (relevantGames.size() > 3) {
                relevantGames = relevantGames.subList(0, 3);
            }

            System.out.println("🎮 " + relevantGames.size() + " jeux récupérés");

            if (relevantGames.isEmpty()) {
                return new RagResponse(
                        "🔍 Je n'ai pas trouvé de jeux correspondant à \"" + question + "\".\n\n" +
                                "💡 **Conseil** : Essayez avec le nom exact d'un jeu ou des termes plus généraux.",
                        new ArrayList<>(),
                        0
                );
            }

            // 3. Conversion rapide pour le contexte
            List<Map<String, Object>> gameContext = relevantGames.stream()
                    .map(this::gameToMap)
                    .collect(Collectors.toList());

            // 4. RÉPONSE INSTANTANÉE ChatGPT-like
            String response = ollamaResponseService.generateNaturalResponse(question, gameContext);

            // 5. Wikipedia en arrière-plan (très rapide, 1 jeu seulement)
            launchQuickWikipediaEnrichment(relevantGames);

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("⚡ RAG terminé en " + totalTime + "ms");

            return new RagResponse(response, relevantGames, relevantGames.size());

        } catch (Exception e) {
            System.err.println("❌ Erreur RAG: " + e.getMessage());
            return new RagResponse(
                    "⚠️ Désolé, un problème technique est survenu. Veuillez réessayer dans quelques instants.",
                    new ArrayList<>(),
                    0
            );
        }
    }

    /**
     * Filtre les meilleurs résultats parmi les doublons - VERSION CORRIGÉE
     */
    private List<Game> filterBestMatches(List<Game> matches, String searchedName) {
        if (matches.size() == 1) {
            return matches;
        }

        System.out.println("🔍 Filtrage de " + matches.size() + " résultats pour: " + searchedName);

        // Étape 1: SÉPARER les noms exacts des noms partiels
        List<Game> exactMatches = matches.stream()
                .filter(game -> game.getName().equalsIgnoreCase(searchedName))
                .collect(Collectors.toList());

        List<Game> partialMatches = matches.stream()
                .filter(game -> !game.getName().equalsIgnoreCase(searchedName))
                .collect(Collectors.toList());

        System.out.println("✅ " + exactMatches.size() + " noms exacts, " + partialMatches.size() + " noms partiels");

        // Étape 2: PRIORITÉ ABSOLUE aux noms exacts
        if (!exactMatches.isEmpty()) {
            // Trier les noms exacts par note (meilleure note d'abord)
            List<Game> sortedExactMatches = exactMatches.stream()
                    .sorted((g1, g2) -> compareGamesByRating(g1, g2))
                    .collect(Collectors.toList());

            // Prendre maximum 2 noms exacts
            List<Game> result = sortedExactMatches.stream()
                    .limit(2)
                    .collect(Collectors.toList());

            System.out.println("🎯 Gardé " + result.size() + " noms exacts (priorité absolue)");
            return result;
        }

        // Étape 3: Si pas de noms exacts, prendre les meilleurs noms partiels
        System.out.println("ℹ️ Aucun nom exact, prise des meilleurs noms partiels par note");
        List<Game> result = partialMatches.stream()
                .sorted((g1, g2) -> compareGamesByRating(g1, g2))
                .limit(2)
                .collect(Collectors.toList());

        System.out.println("🎯 Gardé " + result.size() + " noms partiels");
        return result;
    }

    /**
     * Compare deux jeux par leur note
     */
    private int compareGamesByRating(Game g1, Game g2) {
        // Jeux avec note d'abord
        if (g1.getAverageRating() != null && g2.getAverageRating() == null) return -1;
        if (g1.getAverageRating() == null && g2.getAverageRating() != null) return 1;
        if (g1.getAverageRating() != null && g2.getAverageRating() != null) {
            return Double.compare(g2.getAverageRating(), g1.getAverageRating()); // Descendant
        }
        return 0;
    }

    /**
     * Extrait le nom du jeu de la question - VERSION AMÉLIORÉE
     */
    private String extractGameName(String question) {
        String lower = question.toLowerCase().trim();

        // Noms exacts pour les recherches spécifiques
        if (lower.equals("go")) return "Go";
        if (lower.equals("go fish")) return "Go Fish";
        if (lower.equals("senet")) return "Senet";
        if (lower.equals("chess") || lower.equals("échecs")) return "Chess";
        if (lower.equals("catan")) return "Catan";
        if (lower.equals("war")) return "War";
        if (lower.equals("risk")) return "Risk";
        if (lower.equals("monopoly")) return "Monopoly";
        if (lower.equals("scrabble")) return "Scrabble";
        if (lower.equals("cluedo")) return "Cluedo";
        if (lower.equals("trivial pursuit")) return "Trivial Pursuit";
        if (lower.equals("azul")) return "Azul";
        if (lower.equals("ticket to ride")) return "Ticket to Ride";

        // Recherches contenant pour les variantes
        if (lower.contains("monopoly")) return "Monopoly";
        if (lower.contains("scrabble")) return "Scrabble";
        if (lower.contains("cluedo")) return "Cluedo";
        if (lower.contains("trivial")) return "Trivial Pursuit";
        if (lower.contains("azul")) return "Azul";
        if (lower.contains("ticket to ride")) return "Ticket to Ride";

        return question.trim();
    }

    /**
     * Détecte si c'est une recherche spécifique de jeu
     */
    private boolean isSpecificGameSearch(String question) {
        String lower = question.toLowerCase().trim();

        // Recherches EXACTES pour les noms courts
        if (lower.equals("go") || lower.equals("go fish") || lower.equals("senet") ||
                lower.equals("chess") || lower.equals("échecs") || lower.equals("catan") ||
                lower.equals("war") || lower.equals("risk") || lower.equals("monopoly") ||
                lower.equals("scrabble") || lower.equals("cluedo") || lower.equals("trivial pursuit") ||
                lower.equals("azul") || lower.equals("ticket to ride")) {
            return true;
        }

        // Recherches contenant pour les noms longs
        if (lower.contains("monopoly") || lower.contains("scrabble") ||
                lower.contains("cluedo") || lower.contains("trivial") ||
                lower.contains("azul") || lower.contains("ticket to ride")) {
            return true;
        }

        // Recherches par type
        if (lower.contains("règle") || lower.contains("règles") ||
                lower.contains("comment jouer") || lower.contains("histoire") ||
                lower.contains("historique") || question.length() < 20) {
            return true;
        }

        return false;
    }

    /**
     * Wikipedia rapide en arrière-plan
     */
    private void launchQuickWikipediaEnrichment(List<Game> relevantGames) {
        new Thread(() -> {
            try {
                if (!relevantGames.isEmpty()) {
                    Game mainGame = relevantGames.get(0);
                    // Timeout court pour Wikipedia
                    WikipediaService.WikipediaResult result = wikipediaService.getGameSummaryWithTimeout(mainGame.getName(), 2000);
                    if (result != null && result.getSummary() != null) {
                        mainGame.setWikipediaSummary(result.getSummary());
                        System.out.println("🌐 [BACKGROUND] Wikipedia chargé pour: " + mainGame.getName());
                    }
                }
            } catch (Exception e) {
                // Ignorer silencieusement
            }
        }).start();
    }

    /**
     * Récupération OPTIMISÉE des jeux
     */
    private List<Game> getRelevantGamesOptimized(List<Document> vectorResults, int maxResults) {
        Set<String> gameIds = vectorResults.stream()
                .map(doc -> (String) doc.getMetadata().get("gameId"))
                .filter(Objects::nonNull)
                .limit(maxResults)
                .collect(Collectors.toSet());

        if (gameIds.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(gameRepository.findAllById(gameIds));
    }

    /**
     * Conversion jeu -> Map
     */
    private Map<String, Object> gameToMap(Game game) {
        Map<String, Object> gameMap = new HashMap<>();
        gameMap.put("name", game.getName());
        gameMap.put("description", game.getDescription());
        gameMap.put("yearPublished", game.getYearPublished());
        gameMap.put("averageRating", game.getAverageRating());
        gameMap.put("complexityWeight", game.getComplexityWeight());
        gameMap.put("minPlayers", game.getMinPlayers());
        gameMap.put("maxPlayers", game.getMaxPlayers());
        gameMap.put("categories", game.getCategories());
        gameMap.put("source", game.getSource().toString());
        gameMap.put("wikipediaSummary", game.getWikipediaSummary());

        // Ajouter les règles si disponibles
        if (game.getRulesets() != null && !game.getRulesets().isEmpty()) {
            gameMap.put("rules", game.getRulesets().get(0).getRules());
        }

        // Ajouter les métadonnées Ludii si disponibles
        if (game.getLudiiMetadata() != null) {
            gameMap.put("origin", game.getLudiiMetadata().getOrigin());
            gameMap.put("evidenceRange", game.getLudiiMetadata().getEvidenceRange());
        }

        return gameMap;
    }

    /**
     * Vide le cache
     */
    public void clearCache() {
        responseCache.clear();
        System.out.println("🧹 Cache RAG vidé - " + responseCache.size() + " entrées supprimées");
    }

    /**
     * Réponse RAG
     */
    public static class RagResponse {
        private final String answer;
        private final List<Game> relevantGames;
        private final int totalVectorResults;

        public RagResponse(String answer, List<Game> relevantGames, int totalVectorResults) {
            this.answer = answer;
            this.relevantGames = relevantGames;
            this.totalVectorResults = totalVectorResults;
        }

        public String getAnswer() { return answer; }
        public List<Game> getRelevantGames() { return relevantGames; }
        public int getTotalVectorResults() { return totalVectorResults; }
    }
}