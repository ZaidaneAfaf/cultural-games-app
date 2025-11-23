package com.boardgames.rag.service.embedding;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final GameRepository gameRepository;

    public VectorStoreService(VectorStore vectorStore,
                              EmbeddingModel embeddingModel,
                              GameRepository gameRepository) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.gameRepository = gameRepository;
    }

    /**
     * Indexe tous les jeux dans Qdrant avec gestion robuste
     */
    public void indexAllGames() {
        System.out.println("=== INDEXATION VECTORIELLE DES JEUX ===");

        List<Game> allGames = gameRepository.findAll();
        System.out.println("📊 " + allGames.size() + " jeux à indexer");

        if (allGames.isEmpty()) {
            System.out.println("⚠️ Aucun jeu trouvé en base de données");
            return;
        }

        // Vérifications préalables
        if (!testConnections()) {
            throw new RuntimeException("Connexions aux services non disponibles");
        }

        // Indexation avec gestion d'erreur robuste
        int successCount = 0;
        int batchSize = 3; // Lot réduit mais pas trop
        int totalBatches = (int) Math.ceil((double) allGames.size() / batchSize);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int start = batchNum * batchSize;
            int end = Math.min(start + batchSize, allGames.size());
            List<Game> batch = allGames.subList(start, end);

            System.out.printf("📦 Lot %d/%d (%d jeux)%n",
                    batchNum + 1, totalBatches, batch.size());

            boolean success = processBatch(batch, batchNum + 1);

            if (success) {
                successCount += batch.size();
                System.out.println("✅ Lot " + (batchNum + 1) + " indexé avec succès");

                // Pause stratégique entre les lots
                if (batchNum < totalBatches - 1) {
                    try {
                        Thread.sleep(500); // 500ms entre les lots
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                System.err.println("❌ Échec du lot " + (batchNum + 1) + ", tentative individuelle...");

                // Tentative jeu par jeu pour ce lot
                for (Game game : batch) {
                    if (processSingleGame(game)) {
                        successCount++;
                    }
                }
            }
        }

        System.out.println("📊 Indexation terminée: " + successCount + "/" + allGames.size() + " jeux indexés");
    }

    /**
     * Traite un lot de jeux
     */
    private boolean processBatch(List<Game> batch, int batchNum) {
        try {
            List<Document> documents = batch.stream()
                    .map(this::gameToDocument)
                    .collect(Collectors.toList());

            vectorStore.add(documents);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Erreur lot " + batchNum + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Traite un jeu individuellement
     */
    private boolean processSingleGame(Game game) {
        try {
            Document doc = gameToDocument(game);
            vectorStore.add(List.of(doc));
            System.out.println("  ✅ " + game.getName() + " indexé");
            return true;
        } catch (Exception e) {
            System.err.println("  ❌ " + game.getName() + " échoué: " + e.getMessage());
            return false;
        }
    }

    /**
     * Teste toutes les connexions
     */
    private boolean testConnections() {
        return testOllamaConnection() && testQdrantConnection();
    }

    private boolean testQdrantConnection() {
        System.out.println("🔌 Test connexion Qdrant...");
        try {
            // Test simple avec une recherche vide
            vectorStore.similaritySearch(SearchRequest.query("test").withTopK(1));
            System.out.println("✅ Qdrant opérationnel");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Qdrant inaccessible: " + e.getMessage());
            return false;
        }
    }

    private boolean testOllamaConnection() {
        System.out.println("🔌 Test connexion Ollama...");
        try {
            embeddingModel.embed("test");
            System.out.println("✅ Ollama opérationnel");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Ollama inaccessible: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convertit un jeu en Document (version optimisée)
     */
    private Document gameToDocument(Game game) {
        // Texte concis pour l'embedding
        StringBuilder content = new StringBuilder();
        content.append(game.getName()).append(". ");

        if (game.getDescription() != null) {
            content.append(game.getDescription()).append(" ");
        }

        if (game.getCategories() != null) {
            content.append("Catégories: ").append(String.join(", ", game.getCategories())).append(" ");
        }

        if (game.getMinPlayers() != null && game.getMaxPlayers() != null) {
            content.append("Pour ").append(game.getMinPlayers())
                    .append(" à ").append(game.getMaxPlayers()).append(" joueurs. ");
        }

        // Métadonnées essentielles
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gameId", game.getId());
        metadata.put("name", game.getName());
        metadata.put("source", game.getSource().toString());

        if (game.getYearPublished() != null) {
            metadata.put("year", game.getYearPublished());
        }
        if (game.getAverageRating() != null) {
            metadata.put("rating", game.getAverageRating());
        }
        if (game.getComplexityWeight() != null) {
            metadata.put("complexity", game.getComplexityWeight());
        }

        return new Document(content.toString().trim(), metadata);
    }

    /**
     * Recherche de jeux par similarité sémantique - VERSION CORRIGÉE
     */
    public List<Document> searchGames(String query, int maxResults) {
        try {
            // UTILISEZ SearchRequest au lieu des paramètres directs
            SearchRequest searchRequest = SearchRequest.query(query).withTopK(maxResults);
            return vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            System.err.println("❌ Erreur recherche: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}