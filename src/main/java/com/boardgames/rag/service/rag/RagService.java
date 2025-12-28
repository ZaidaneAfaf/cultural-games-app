package com.boardgames.rag.service.rag;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.embedding.VectorStoreService;
import com.boardgames.rag.service.ai.OllamaResponseService;
import com.boardgames.rag.service.intent.IntentDetectionService;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final GameRepository gameRepository;
    private final OllamaResponseService ollamaResponseService;
    private final IntentDetectionService intentDetectionService;
    private final ExecutorService executorService;

    // 🔥 Configuration
    @Value("${app.prompting.strategies.enabled:true}")
    private boolean strategiesEnabled;

    @Value("${app.prompting.strategies.default:ADAPTIVE}")
    private String defaultStrategy;

    @Value("${app.prompting.threshold.complex-question-length:100}")
    private int complexQuestionThreshold;

    @Value("${app.prompting.threshold.min-games-for-few-shot:2}")
    private int minGamesForFewShot;

    @Value("${app.prompting.evaluation.enabled:true}")
    private boolean evaluationEnabled;

    // 🔥 Stratégies de prompting
    public enum PromptingStrategy {
        ZERO_SHOT,      // Prompting de base
        FEW_SHOT,       // Avec exemples
        CHAIN_OF_THOUGHT, // Raisonnement étape par étape
        SELF_CONSISTENCY, // Auto-cohérence
        ADAPTIVE         // Choix adaptatif
    }

    public RagService(VectorStoreService vectorStoreService,
                      GameRepository gameRepository,
                      OllamaResponseService ollamaResponseService,
                      IntentDetectionService intentDetectionService) {
        this.vectorStoreService = vectorStoreService;
        this.gameRepository = gameRepository;
        this.ollamaResponseService = ollamaResponseService;
        this.intentDetectionService = intentDetectionService;
        this.executorService = Executors.newCachedThreadPool();
    }

    @PostConstruct
    public void init() {
        System.out.println("🚀 RagService initialisé");
        System.out.println("🎯 Stratégies activées: " + strategiesEnabled);
        System.out.println("🎯 Stratégie par défaut: " + defaultStrategy);
    }

    /**
     * 🎯 RECHERCHE INTELLIGENTE AVEC DÉTECTION D'INTENTION
     */
    public RagResponse searchWithRag(String question, int maxResults) {
        return searchWithRag(question, maxResults, PromptingStrategy.ADAPTIVE);
    }

    /**
     * 🔥 VERSION AVEC STRATÉGIE DE PROMPTING
     */
    public RagResponse searchWithRag(String question, int maxResults, PromptingStrategy strategy) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 RAG: \"" + question + "\"");
        System.out.println("🎯 Stratégie: " + strategy);
        System.out.println("=".repeat(60));

        long startTime = System.currentTimeMillis();

        try {
            // 🔥 Salutations rapides
            if (isSimpleGreeting(question)) {
                return new RagResponse(
                        "Bonjour ! 👋 Je peux vous aider à trouver des jeux de société. Posez-moi une question !",
                        new ArrayList<>(),
                        0,
                        strategy.toString()
                );
            }

            // 🔥 DÉTECTION D'INTENTION
            IntentDetectionService.IntentResult intent = intentDetectionService.detectIntent(question);
            IntentDetectionService.IntentType intentType = intent.getType();

            System.out.println("🎯 [RAG] Intent détecté: " + intentType);

            // 🔥 GESTION DES RÉPONSES DIRECTES
            if (intent.hasDirectResponse()) {
                long totalTime = System.currentTimeMillis() - startTime;
                System.out.println("⚡ Terminé en " + totalTime + "ms (réponse directe)");
                return new RagResponse(intent.getDirectResponse(), new ArrayList<>(), 0, strategy.toString());
            }

            // 🔥 TRAITEMENT PAR TYPE D'INTENTION
            if (intentType == IntentDetectionService.IntentType.THEORETICAL_QUESTION) {
                System.out.println("💭 [RAG] Génération réponse théorique");
                String theoreticalResponse = intentDetectionService.generateTheoreticalResponse(question);
                long totalTime = System.currentTimeMillis() - startTime;
                System.out.println("⚡ Terminé en " + totalTime + "ms (théorique)");
                return new RagResponse(theoreticalResponse, new ArrayList<>(), 0, strategy.toString());
            }

            if (intentType == IntentDetectionService.IntentType.ARCHAEOLOGICAL_IDENTIFICATION) {
                System.out.println("🏛️ [RAG] Analyse archéologique");
                String archaeologicalAnalysis = intentDetectionService.generateArchaeologicalAnalysis(question);
                long totalTime = System.currentTimeMillis() - startTime;
                System.out.println("⚡ Terminé en " + totalTime + "ms (archéologique)");
                return new RagResponse(archaeologicalAnalysis, new ArrayList<>(), 0, strategy.toString());
            }

            // 🔥 RECHERCHE DE JEUX
            System.out.println("🔍 [RAG] Recherche de jeux dans la base");
            List<Document> vectorResults = vectorStoreService.searchGames(question, maxResults, intentType);
            List<Game> games = getGamesFromDocuments(vectorResults);

            System.out.println("📊 [RAG] " + games.size() + " jeu(x) trouvé(s)");

            String response;

            if (games.isEmpty()) {
                System.out.println("⚠️ [RAG] Aucun jeu trouvé → génération suggestions");
                response = generateNoGamesResponse(question, strategy);
            } else {
                System.out.println("✅ [RAG] Génération réponse avec jeux trouvés");
                response = generateGamesResponse(games, question, intentType, strategy);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("⚡ RAG terminé en " + totalTime + "ms");

            return new RagResponse(response, games, games.size(), strategy.toString());

        } catch (Exception e) {
            System.err.println("❌ Erreur RAG: " + e.getMessage());
            e.printStackTrace();
            return new RagResponse(
                    "Désolé, une erreur s'est produite lors de la recherche. Pouvez-vous reformuler votre question ?",
                    new ArrayList<>(),
                    0,
                    strategy.toString()
            );
        }
    }

    /**
     * 🔥 GÉNÉRATION RÉPONSE AVEC STRATÉGIE
     */
    private String generateGamesResponse(List<Game> games, String question,
                                         IntentDetectionService.IntentType intentType,
                                         PromptingStrategy strategy) {
        List<Game> topGames = games.stream().limit(3).collect(Collectors.toList());

        try {
            List<Map<String, Object>> gameContext = topGames.stream()
                    .map(this::createEnrichedGameContext)
                    .collect(Collectors.toList());

            // 🔥 Sélection de la stratégie adaptative
            PromptingStrategy finalStrategy = selectOptimalStrategy(question, intentType, games.size(), strategy);
            System.out.println("🎯 [RAG] Stratégie finale: " + finalStrategy);

            String response;

            switch (finalStrategy) {
                case FEW_SHOT:
                    response = generateWithFewShot(gameContext, question);
                    break;
                case CHAIN_OF_THOUGHT:
                    response = generateWithChainOfThought(gameContext, question);
                    break;
                case SELF_CONSISTENCY:
                    response = generateWithSelfConsistency(gameContext, question);
                    break;
                case ZERO_SHOT:
                default:
                    response = generateWithZeroShot(gameContext, question, intentType, topGames);
                    break;
            }

            if (response != null && !response.trim().isEmpty() && response.length() > 50) {
                System.out.println("✅ [RAG] Réponse générée (" + response.length() + " caractères)");
                return response;
            }

            System.err.println("⚠️ [RAG] Réponse insuffisante → Fallback");
            return generateFallbackResponse(topGames, question);

        } catch (Exception e) {
            System.err.println("❌ [RAG] Erreur: " + e.getMessage() + " → Fallback");
            return generateFallbackResponse(topGames, question);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC ZERO-SHOT (CORRIGÉ)
     */
    private String generateWithZeroShot(List<Map<String, Object>> gameContext, String question,
                                        IntentDetectionService.IntentType intentType, List<Game> topGames) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                        ollamaResponseService.generateQuickResponse(question, gameContext),
                executorService
        );

        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⏰ [ZERO-SHOT] Interrompu");
            throw new RuntimeException("Interrompu", e);
        } catch (ExecutionException e) {
            System.err.println("❌ [ZERO-SHOT] Erreur d'exécution: " + e.getCause().getMessage());
            throw new RuntimeException("Erreur d'exécution", e);
        } catch (TimeoutException e) {
            System.err.println("⏰ [ZERO-SHOT] Timeout après 20s");
            future.cancel(true);
            throw new RuntimeException("Timeout Zero-Shot", e);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC FEW-SHOT (CORRIGÉ)
     */
    private String generateWithFewShot(List<Map<String, Object>> gameContext, String question) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                        ollamaResponseService.generateWithFewShotPrompting(question, gameContext),
                executorService
        );

        try {
            return future.get(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⏰ [FEW-SHOT] Interrompu");
            throw new RuntimeException("Interrompu", e);
        } catch (ExecutionException e) {
            System.err.println("❌ [FEW-SHOT] Erreur d'exécution: " + e.getCause().getMessage());
            throw new RuntimeException("Erreur d'exécution", e);
        } catch (TimeoutException e) {
            System.err.println("⏰ [FEW-SHOT] Timeout après 25s");
            future.cancel(true);
            throw new RuntimeException("Timeout Few-Shot", e);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC CHAIN-OF-THOUGHT (CORRIGÉ)
     */
    private String generateWithChainOfThought(List<Map<String, Object>> gameContext, String question) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                        ollamaResponseService.generateWithChainOfThought(question, gameContext),
                executorService
        );

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⏰ [CHAIN-OF-THOUGHT] Interrompu");
            throw new RuntimeException("Interrompu", e);
        } catch (ExecutionException e) {
            System.err.println("❌ [CHAIN-OF-THOUGHT] Erreur d'exécution: " + e.getCause().getMessage());
            throw new RuntimeException("Erreur d'exécution", e);
        } catch (TimeoutException e) {
            System.err.println("⏰ [CHAIN-OF-THOUGHT] Timeout après 30s");
            future.cancel(true);
            throw new RuntimeException("Timeout Chain-of-Thought", e);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC SELF-CONSISTENCY (CORRIGÉ)
     */
    private String generateWithSelfConsistency(List<Map<String, Object>> gameContext, String question) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                        ollamaResponseService.generateWithSelfConsistency(question, gameContext),
                executorService
        );

        try {
            return future.get(35, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⏰ [SELF-CONSISTENCY] Interrompu");
            throw new RuntimeException("Interrompu", e);
        } catch (ExecutionException e) {
            System.err.println("❌ [SELF-CONSISTENCY] Erreur d'exécution: " + e.getCause().getMessage());
            throw new RuntimeException("Erreur d'exécution", e);
        } catch (TimeoutException e) {
            System.err.println("⏰ [SELF-CONSISTENCY] Timeout après 35s");
            future.cancel(true);
            throw new RuntimeException("Timeout Self-Consistency", e);
        }
    }

    /**
     * 🔥 SÉLECTION STRATÉGIE OPTIMALE
     */
    private PromptingStrategy selectOptimalStrategy(String question,
                                                    IntentDetectionService.IntentType intentType,
                                                    int gameCount,
                                                    PromptingStrategy requestedStrategy) {
        // Si stratégie spécifique demandée
        if (requestedStrategy != PromptingStrategy.ADAPTIVE) {
            return requestedStrategy;
        }

        // Si stratégies désactivées
        if (!strategiesEnabled) {
            return PromptingStrategy.ZERO_SHOT;
        }

        // Sélection adaptative
        String lowerQuestion = question.toLowerCase();

        // Questions complexes → Chain-of-Thought
        if (lowerQuestion.contains("différence") || lowerQuestion.contains("compar") ||
                lowerQuestion.contains("pourquoi") || lowerQuestion.contains("explique") ||
                question.length() > complexQuestionThreshold) {
            return PromptingStrategy.CHAIN_OF_THOUGHT;
        }

        // Recommandations avec plusieurs jeux → Few-Shot
        if ((lowerQuestion.contains("meilleurs") || lowerQuestion.contains("recommand") ||
                lowerQuestion.contains("top") || lowerQuestion.contains("suggère")) &&
                gameCount >= minGamesForFewShot) {
            return PromptingStrategy.FEW_SHOT;
        }

        // Questions précises → Self-Consistency
        if (lowerQuestion.contains("combien") || lowerQuestion.contains("quand") ||
                lowerQuestion.contains("où") || lowerQuestion.contains("qui") ||
                lowerQuestion.matches(".*\\d+.*")) {
            return PromptingStrategy.SELF_CONSISTENCY;
        }

        // Par défaut → Zero-Shot
        return PromptingStrategy.ZERO_SHOT;
    }

    /**
     * 🔥 CRÉATION CONTEXTE ENRICHI
     */
    private Map<String, Object> createEnrichedGameContext(Game game) {
        Map<String, Object> context = new HashMap<>();

        context.put("name", game.getName());
        context.put("yearPublished", game.getYearPublished());
        context.put("averageRating", game.getAverageRating());
        context.put("complexityWeight", game.getComplexityWeight());
        context.put("minPlayers", game.getMinPlayers());
        context.put("maxPlayers", game.getMaxPlayers());

        String description = game.getDescription();
        if (description != null) {
            context.put("description", truncate(description, 400));
        }

        if (game.getCategories() != null && !game.getCategories().isEmpty()) {
            context.put("categories", String.join(", ", game.getCategories()));
        }

        if (game.getLudiiMetadata() != null && game.getLudiiMetadata().getOrigin() != null) {
            context.put("origin", game.getLudiiMetadata().getOrigin());
        }

        context.put("source", game.getSource().toString());

        return context;
    }

    /**
     * 🔥 GÉNÉRATION RÉPONSE QUAND AUCUN JEU TROUVÉ (CORRIGÉ)
     */
    private String generateNoGamesResponse(String question, PromptingStrategy strategy) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            String prompt = String.format("""
                L'utilisateur cherche: "%s"
                Aucun jeu correspondant trouvé.
                
                Suggère 3-5 jeux populaires pertinents.
                Explique pourquoi ils pourraient correspondre.
                Mentionne comment reformuler la recherche.
                
                Ton amical et professionnel.
                Réponse en français, 150-250 mots.
                """, question);

            return ollamaResponseService.generateFromPrompt(prompt);
        }, executorService);

        try {
            String response = future.get(20, TimeUnit.SECONDS);

            if (response != null && !response.trim().isEmpty() && response.length() > 50) {
                System.out.println("✅ [RAG] Suggestions générées");
                return response;
            }

            return generateNoGamesFallback(question);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⏰ [NO-GAMES] Interrompu");
            return generateNoGamesFallback(question);
        } catch (ExecutionException e) {
            System.err.println("❌ [NO-GAMES] Erreur: " + e.getCause().getMessage());
            return generateNoGamesFallback(question);
        } catch (TimeoutException e) {
            System.err.println("⏰ [NO-GAMES] Timeout après 20s");
            future.cancel(true);
            return generateNoGamesFallback(question);
        }
    }

    /**
     * 🔥 RÉPONSE FALLBACK AVEC JEUX
     */
    private String generateFallbackResponse(List<Game> games, String question) {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(games.size(), 3);

        sb.append(String.format("🎮 **%d jeu(x) trouvé(s)**\n\n", count));

        for (int i = 0; i < count; i++) {
            Game game = games.get(i);
            sb.append(formatGameInfoDetailed(game)).append("\n\n");
        }

        if (games.size() > 3) {
            sb.append(String.format("... et %d autre(s) jeu(x)\n\n", games.size() - 3));
        }

        sb.append("💡 **Besoin de plus de détails ?** Posez une question sur un jeu spécifique !");

        return sb.toString();
    }

    /**
     * 🔥 RÉPONSE FALLBACK SANS JEUX
     */
    private String generateNoGamesFallback(String question) {
        return String.format("""
            🔍 **Aucun jeu trouvé pour :** "%s"
            
            **✨ Jeux populaires :**
            • **Carcassonne** - Placement de tuiles, 2-5 joueurs
            • **Catan** - Colonisation, 3-4 joueurs
            • **Pandemic** - Coopératif, 2-4 joueurs
            
            **🔎 Essayez :**
            • Par nom : "Catan", "Chess", "Senet"
            • Par type : "jeux coopératifs", "jeux de stratégie"
            • Par critères : "jeux pour 4 joueurs"
            """, question);
    }

    /**
     * 🔥 FORMATAGE INFO JEU
     */
    private String formatGameInfoDetailed(Game game) {
        StringBuilder info = new StringBuilder();

        info.append("🎲 **").append(game.getName()).append("**");
        if (game.getYearPublished() != null) {
            info.append(" (").append(game.getYearPublished()).append(")");
        }
        info.append("\n");

        List<String> infoLine = new ArrayList<>();

        if (game.getAverageRating() != null) {
            infoLine.add("⭐ " + String.format("%.1f/10", game.getAverageRating()));
        }

        if (game.getComplexityWeight() != null) {
            String complexity = game.getComplexityWeight() <= 2.0 ? "Simple" :
                    game.getComplexityWeight() <= 3.5 ? "Moyen" : "Complexe";
            infoLine.add("🎲 " + complexity + " (" + String.format("%.1f/5", game.getComplexityWeight()) + ")");
        }

        if (game.getMinPlayers() != null && game.getMaxPlayers() != null) {
            infoLine.add("👥 " + game.getMinPlayers() + "-" + game.getMaxPlayers() + " joueurs");
        }

        if (!infoLine.isEmpty()) {
            info.append(String.join(" | ", infoLine)).append("\n");
        }

        if (game.getLudiiMetadata() != null && game.getLudiiMetadata().getOrigin() != null) {
            info.append("🏛️ **Origine** : ").append(game.getLudiiMetadata().getOrigin()).append("\n");
        }

        if (game.getCategories() != null && !game.getCategories().isEmpty()) {
            List<String> topCategories = game.getCategories().stream().limit(4).collect(Collectors.toList());
            info.append("🏷️ ").append(String.join(", ", topCategories)).append("\n");
        }

        if (game.getDescription() != null) {
            String shortDesc = game.getDescription().length() > 200
                    ? game.getDescription().substring(0, 200) + "..."
                    : game.getDescription();
            info.append("\n").append(shortDesc);
        }

        return info.toString();
    }

    /**
     * 🔥 UTILITAIRES
     */
    private boolean isSimpleGreeting(String message) {
        String lower = message.toLowerCase().trim();
        String[] greetings = {"bonjour", "salut", "hello", "hi", "hey", "bonsoir"};
        return Arrays.stream(greetings).anyMatch(lower::equals);
    }

    private List<Game> getGamesFromDocuments(List<Document> documents) {
        Set<String> gameIds = documents.stream()
                .map(doc -> (String) doc.getMetadata().get("gameId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (gameIds.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(gameRepository.findAllById(gameIds));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 🔥 CLASSE RÉPONSE
     */
    public static class RagResponse {
        private final String answer;
        private final List<Game> relevantGames;
        private final int totalVectorResults;
        private final String promptingStrategy;

        public RagResponse(String answer, List<Game> relevantGames, int totalVectorResults) {
            this(answer, relevantGames, totalVectorResults, "ZERO_SHOT");
        }

        public RagResponse(String answer, List<Game> relevantGames, int totalVectorResults, String promptingStrategy) {
            this.answer = answer;
            this.relevantGames = relevantGames;
            this.totalVectorResults = totalVectorResults;
            this.promptingStrategy = promptingStrategy;
        }

        public String getAnswer() { return answer; }
        public List<Game> getRelevantGames() { return relevantGames; }
        public int getTotalVectorResults() { return totalVectorResults; }
        public String getPromptingStrategy() { return promptingStrategy; }
    }

    /**
     * 🔥 ENDPOINT AVEC STRATÉGIE CHOISIE
     */
    public RagResponse searchWithStrategy(String question, int maxResults, String strategyName) {
        try {
            PromptingStrategy strategy = PromptingStrategy.valueOf(strategyName.toUpperCase());
            return searchWithRag(question, maxResults, strategy);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Stratégie inconnue: " + strategyName + " → ADAPTIVE");
            return searchWithRag(question, maxResults, PromptingStrategy.ADAPTIVE);
        }
    }

    /**
     * 🔥 ÉVALUATION DES STRATÉGIES
     */
    public Map<String, String> evaluateStrategies(String question, int maxResults) {
        if (!evaluationEnabled) {
            System.out.println("⚠️ Évaluation désactivée");
            return Map.of("INFO", "Évaluation désactivée dans la configuration");
        }

        Map<String, String> results = new LinkedHashMap<>();

        for (PromptingStrategy strategy : PromptingStrategy.values()) {
            if (strategy != PromptingStrategy.ADAPTIVE) {
                try {
                    System.out.println("\n🧪 Évaluation: " + strategy);
                    long startTime = System.currentTimeMillis();

                    RagResponse response = searchWithRag(question, maxResults, strategy);

                    long duration = System.currentTimeMillis() - startTime;
                    String evaluation = String.format(
                            "Durée: %dms | Jeux: %d | Longueur: %d car.",
                            duration,
                            response.getRelevantGames().size(),
                            response.getAnswer().length()
                    );

                    results.put(strategy.toString(), evaluation);
                    System.out.println("✅ " + strategy + ": " + evaluation);

                } catch (Exception e) {
                    results.put(strategy.toString(), "ERREUR: " + e.getMessage());
                    System.err.println("❌ Erreur avec " + strategy + ": " + e.getMessage());
                }
            }
        }

        return results;
    }
}