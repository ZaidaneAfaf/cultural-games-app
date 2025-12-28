package com.boardgames.rag.controller;

import com.boardgames.rag.service.rag.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    // ============ ENDPOINTS EXISTANTS (ORIGINAUX) ============

    // POST original
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchPost(@RequestBody Map<String, String> request) {
        String query = request.get("query");

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query cannot be empty"
            ));
        }

        return executeSearch(query, "ADAPTIVE", 10);
    }

    // GET ajouté
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchGet(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query parameter is required"
            ));
        }

        return executeSearch(query, "ADAPTIVE", 10);
    }

    // ============ NOUVEAUX ENDPOINTS POUR LES STRATÉGIES ============

    /**
     * 🔥 GET avec stratégie spécifique
     */
    @GetMapping("/search/strategy")
    public ResponseEntity<Map<String, Object>> searchWithStrategyGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "ADAPTIVE") String strategy,
            @RequestParam(defaultValue = "10") int maxResults) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query parameter is required"
            ));
        }

        return executeSearch(query, strategy, maxResults);
    }

    /**
     * 🔥 POST avec stratégie spécifique
     */
    @PostMapping("/search/strategy")
    public ResponseEntity<Map<String, Object>> searchWithStrategyPost(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        String strategy = (String) request.getOrDefault("strategy", "ADAPTIVE");
        Integer maxResults = (Integer) request.getOrDefault("maxResults", 10);

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query cannot be empty"
            ));
        }

        return executeSearch(query, strategy, maxResults);
    }

    /**
     * 🔥 ÉVALUATION DES STRATÉGIES (GET)
     */
    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateStrategiesGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query parameter is required"
            ));
        }

        System.out.println("\n🧪 Évaluation des stratégies pour: \"" + query + "\"");

        try {
            long startTime = System.currentTimeMillis();

            Map<String, String> evaluationResults = ragService.evaluateStrategies(query, maxResults);

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("question", query);
            result.put("evaluation", evaluationResults);
            result.put("processingTime", duration + "ms");
            result.put("timestamp", new java.util.Date().toString());

            System.out.println("✅ Évaluation terminée (" + duration + "ms)");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Erreur évaluation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Erreur lors de l'évaluation: " + e.getMessage()
            ));
        }
    }

    /**
     * 🔥 ÉVALUATION DES STRATÉGIES (POST)
     */
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateStrategiesPost(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        Integer maxResults = (Integer) request.getOrDefault("maxResults", 5);

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query cannot be empty"
            ));
        }

        System.out.println("\n🧪 Évaluation POST des stratégies pour: \"" + query + "\"");

        try {
            long startTime = System.currentTimeMillis();

            Map<String, String> evaluationResults = ragService.evaluateStrategies(query, maxResults);

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("question", query);
            result.put("evaluation", evaluationResults);
            result.put("processingTime", duration + "ms");
            result.put("timestamp", new java.util.Date().toString());

            System.out.println("✅ Évaluation terminée (" + duration + "ms)");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Erreur évaluation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Erreur lors de l'évaluation: " + e.getMessage()
            ));
        }
    }

    /**
     * 🔥 LISTE DES STRATÉGIES DISPONIBLES
     */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> getAvailableStrategies() {
        Map<String, String> strategies = Map.of(
                "ZERO_SHOT", "Prompting de base sans exemples",
                "FEW_SHOT", "Avec exemples de reponses",
                "CHAIN_OF_THOUGHT", "Raisonnement etape par etape",
                "SELF_CONSISTENCY", "Auto-coherence avec evaluation",
                "ADAPTIVE", "Selection automatique (par defaut)"
        );

        return ResponseEntity.ok(Map.of(
                "strategies", strategies,
                "count", strategies.size(),
                "default", "ADAPTIVE"
        ));
    }

    /**
     * 🔥 TEST DE SANTÉ
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "RAG System with Advanced Prompting",
                "timestamp", new java.util.Date().toString(),
                "endpoints", Map.of(
                        "GET", "/api/rag/search?query=...",
                        "POST", "/api/rag/search (body: {\"query\": \"...\"})",
                        "strategies", "/api/rag/strategies",
                        "evaluate", "/api/rag/evaluate?query=..."
                )
        ));
    }

    /**
     * 🔥 TEST RAPIDE
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        try {
            // Test avec une question simple
            String testQuestion = "C'est quoi Catan ?";
            RagService.RagResponse response = ragService.searchWithStrategy(testQuestion, 3, "ADAPTIVE");

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "RAG Service with Prompting Strategies is running",
                    "test", Map.of(
                            "question", testQuestion,
                            "strategy_used", response.getPromptingStrategy(),
                            "games_found", response.getRelevantGames().size(),
                            "answer_preview", response.getAnswer().substring(0, Math.min(100, response.getAnswer().length())) + "..."
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "RAG Service is running (test query failed: " + e.getMessage() + ")"
            ));
        }
    }

    // ============ LOGIQUE COMMUNE AMÉLIORÉE ============

    private ResponseEntity<Map<String, Object>> executeSearch(String query, String strategy, int maxResults) {
        System.out.println("\n📥 Requête RAG reçue: \"" + query + "\"");
        System.out.println("🎯 Stratégie: " + strategy);
        System.out.println("📊 Max résultats: " + maxResults);

        try {
            long startTime = System.currentTimeMillis();

            RagService.RagResponse response = ragService.searchWithStrategy(query, maxResults, strategy);

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("answer", response.getAnswer());
            result.put("games", response.getRelevantGames());
            result.put("totalResults", response.getTotalVectorResults());
            result.put("processingTime", duration + "ms");
            result.put("promptingStrategy", response.getPromptingStrategy());
            result.put("query", query);

            System.out.println("✅ Réponse envoyée (" + duration + "ms)");
            System.out.println("   - Stratégie utilisée: " + response.getPromptingStrategy());
            System.out.println("   - Jeux trouvés: " + response.getTotalVectorResults());
            System.out.println("   - Longueur réponse: " + response.getAnswer().length() + " caractères\n");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Erreur traitement: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Une erreur s'est produite: " + e.getMessage(),
                    "query", query,
                    "strategy", strategy
            ));
        }
    }
}