package com.example.projets5.service;

import com.example.projets5.model.BoardGame;
import com.example.projets5.repository.GameRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points.PointStruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
@RequiredArgsConstructor
public class IndexService {

    private final GameRepository gameRepository;
    private final QdrantClient qdrant;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.collection:boardgames}")
    private String collection;

    @Value("${rag.embedding-dim:768}")
    private int embeddingDim;

    // nombre max de docs à indexer par run
    @Value("${rag.max-docs:10000}")
    private int maxDocs;

    // ================== 1) Collection ==================

    private void ensureCollectionExists() {
        try {
            qdrant.getCollectionInfoAsync(collection).get();
            System.out.println("✅ Collection Qdrant '" + collection + "' déjà existante.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException sre
                    && sre.getStatus().getCode() == Status.Code.NOT_FOUND) {

                System.out.println("ℹ️ Collection '" + collection + "' absente, création...");

                Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                        .setSize(embeddingDim)
                        .setDistance(Collections.Distance.Cosine)
                        .build();

                try {
                    qdrant.createCollectionAsync(collection, vectorParams).get();
                    System.out.println("✅ Collection Qdrant '" + collection + "' créée.");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrompu pendant la création de la collection Qdrant", ie);
                } catch (ExecutionException ce) {
                    throw new RuntimeException("Erreur lors de la création de la collection Qdrant", ce);
                }

            } else {
                throw new RuntimeException("Erreur en vérifiant la collection Qdrant", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompu pendant la vérification de la collection Qdrant", e);
        }
    }

    // ================== 2) Index complet (à partir de 0) ==================

    public void rebuildIndex() {
        // startPage = 0
        rebuildIndexFromPage(0);
    }

    // ================== 3) Index à partir d’une page donnée ==================

    /**
     * Reprend l’indexation à partir d’une page MongoDB donnée.
     *
     * @param startPage numéro de page (0-based) dans Mongo.
     *                  Ex : startPage=10 avec pageSize=1000 => commence au jeu n° 10 000.
     */
    public void rebuildIndexFromPage(int startPage) {
        ensureCollectionExists();

        int pageSize = 1000;      // tu peux l'externaliser si tu veux
        int processed = 0;
        int pageNumber = startPage;

        System.out.println("🏁 Début indexation à partir de la page " + startPage
                + " (max " + maxDocs + " jeux pour ce run)...");

        List<PointStruct> batch = new ArrayList<>();

        while (processed < maxDocs) {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            Page<BoardGame> page = gameRepository.findAll(pageable);

            if (!page.hasContent()) {
                System.out.println("ℹ️ Plus de jeux en base, arrêt de l’indexation.");
                break;
            }

            for (BoardGame g : page.getContent()) {
                if (processed >= maxDocs) {
                    break;
                }

                String text = buildGameText(g);
                float[] vector = embeddingModel.embed(text);

                List<Float> vecList = new ArrayList<>(vector.length);
                for (float v : vector) {
                    vecList.add(v);
                }

                PointStruct point = PointStruct.newBuilder()
                        .setId(id(UUID.randomUUID()))
                        .setVectors(vectors(vecList))
                        .putAllPayload(
                                Map.of(
                                        "mongoId", value(g.getId() != null ? g.getId() : ""),
                                        "name",    value(g.getName() != null ? g.getName() : "")
                                )
                        )
                        .build();

                batch.add(point);
                processed++;

                if (batch.size() >= 100) {
                    upsertBatch(batch);
                    batch.clear();
                }

                if (processed % 1000 == 0) {
                    System.out.println(" -> " + processed + " jeux indexés dans ce run (page courante : " + pageNumber + ")");
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }

        if (!batch.isEmpty()) {
            upsertBatch(batch);
        }

        System.out.println("✅ Index Qdrant reconstruit à partir de la page " + startPage
                + " (" + processed + " jeux indexés dans ce run).");
    }

    // ================== 4) Upsert batch ==================

    private void upsertBatch(List<PointStruct> points) {
        try {
            qdrant.upsertAsync(collection, points).get();
        } catch (Exception e) {
            System.err.println("Erreur lors de l'upsert Qdrant : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================== 5) Texte pour l’embedding ==================

    private String buildGameText(BoardGame g) {
        StringBuilder sb = new StringBuilder();
        sb.append(g.getName()).append(". ");

        if (g.getDescription() != null) {
            sb.append(g.getDescription()).append(". ");
        }

        if (g.getBgg() != null) {
            if (g.getBgg().getAvgRating() != null) {
                sb.append("Average rating: ").append(g.getBgg().getAvgRating()).append(". ");
            }
            if (g.getBgg().getGameWeight() != null) {
                sb.append("Weight: ").append(g.getBgg().getGameWeight()).append(". ");
            }
        }

        if (g.getLudii() != null) {
            if (g.getLudii().getOrigin() != null) {
                sb.append("Origin: ").append(g.getLudii().getOrigin()).append(". ");
            }
            if (g.getLudii().getDate() != null) {
                sb.append("Date: ").append(g.getLudii().getDate()).append(". ");
            }
        }

        if (g.getRulesets() != null) {
            g.getRulesets().stream()
                    .limit(3)
                    .forEach(r -> {
                        if (r.getSummary() != null) {
                            sb.append("Ruleset: ").append(r.getSummary()).append(". ");
                        }
                    });
        }

        if (g.getCategories() != null && !g.getCategories().isEmpty()) {
            sb.append("Categories: ")
                    .append(String.join(", ", g.getCategories()))
                    .append(". ");
        }

        return sb.toString();
    }
}
