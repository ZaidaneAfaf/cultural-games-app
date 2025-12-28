package com.example.projets5.service;

import com.example.projets5.model.BoardGame;
import com.example.projets5.repository.GameRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagService {

    private final GameRepository gameRepository;
    private final QdrantClient qdrant;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final WikipediaService wikipediaService;

    @Value("${rag.collection:boardgames}")
    private String collection;

    @Value("${rag.top-k:5}")
    private int topK;

    public Map<String, Object> ask(String question, Map<String, Object> filters) {

        long start = System.currentTimeMillis();   // pour mesurer la latence

        // 1) Embedding de la question
        float[] vec = embeddingModel.embed(question);
        List<Float> queryVector = new ArrayList<>(vec.length);
        for (float v : vec) {
            queryVector.add(v);
        }

        // 2) Recherche Qdrant
        List<ScoredPoint> scoredPoints;
        try {
            scoredPoints = qdrant.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collection)
                            .addAllVector(queryVector)
                            .setLimit(topK)
                            .build()
            ).get();
        } catch (Exception e) {
            return Map.of(
                    "question", question,
                    "answer", "Erreur Qdrant : " + e.getMessage(),
                    "contexts", List.of()
            );
        }

        // 3) Extraire les IDs stockés dans le payload ("mongoId")
        List<String> ids = new ArrayList<>();
        for (ScoredPoint sp : scoredPoints) {
            JsonWithInt.Value idVal = sp.getPayloadMap().get("mongoId");
            if (idVal != null && idVal.hasStringValue()) {
                ids.add(idVal.getStringValue());
            }
        }

        // 4) Charger les jeux depuis MongoDB
        List<BoardGame> games = gameRepository.findAllById(ids);

        // 5) Construire les contexts pour le LLM ET pour RAGAS
        List<String> contexts = new ArrayList<>();
        StringBuilder fullContext = new StringBuilder();

        for (BoardGame g : games) {

            StringBuilder block = new StringBuilder();
            block.append("Game: ").append(g.getName()).append(". ");

            if (g.getDescription() != null) {
                block.append("Description: ").append(g.getDescription()).append(". ");
            }

            if (g.getCategories() != null && !g.getCategories().isEmpty()) {
                block.append("Categories: ").append(String.join(", ", g.getCategories())).append(". ");
            }

            // Enrichissement Wikipédia
            try {
                String title = wikipediaService.searchTitle(g.getName());
                if (title != null) {
                    String extract = wikipediaService.getExtract(title);
                    if (extract != null && !extract.isBlank()) {
                        String shortExtract = extract.length() > 600
                                ? extract.substring(0, 600) + "..."
                                : extract;
                        block.append("Wikipedia context (").append(title).append("): ")
                                .append(shortExtract).append(". ");
                    }
                }
            } catch (Exception ex) {
                System.out.println("Erreur Wikipedia pour " + g.getName() + " : " + ex.getMessage());
            }

            String blockStr = block.toString();
            contexts.add(blockStr);              // <<< indispensable pour RAGAS
            fullContext.append(blockStr).append("\n");
        }

        // 6) Construire le prompt final (texte block Java 17 OK)
        String promptText = """
                You are Eldranor, the Eternal Sage of Board Games,
                a wise old traveler who has witnessed countless civilizations
                and studied thousands of board games across history, culture, and society.

                Your mission:
                - Explain the historical origins and cultural influences of the games mentioned.
                - Describe any historical evolution, mutations, or transformations they went through.
                - Explain their social and cultural impact when such information exists.
                - If a gameplay scene is described, interpret its strategic meaning
                  and link it to historical or cultural relevance, ONLY if the context allows it.
                - If the question is incomplete, you may propose multiple POSSIBLE interpretations
                  ONLY if they are suggested or supported by the context, and explain why.

                STRICT RULES:
                - You MUST answer ONLY using the information provided in the context.
                - Do NOT invent or guess anything beyond the context.
                - If information is missing, clearly say what is unknown.
                - If the question is unrelated to the context, outside its scope,
                  or impossible to answer based on the context, respond strictly with:
                  "Sorry, I cannot answer this because it is outside the provided context."
                - Stay always in character as a calm, wise, culturally knowledgeable sage.
                - Keep your explanation structured, insightful, and pedagogical.

                Response structure:
                - Short introduction, in the tone of an ancient wise sage.
                - Historical and cultural explanation (if available).
                - Evolution / transformations (if available).
                - Social and cultural impact (if available).
                - Interpretation of the gameplay scene if applicable.
                - Clear statement of missing or unavailable information.

                Context:
                %s

                Question:
                %s
                """.formatted(fullContext.toString(), question);

        // 7) Appel LLM (Ollama via Spring AI)
        String answer = chatModel.call(promptText);

        long elapsed = System.currentTimeMillis() - start;

        // 8) Format 100% compatible RAGAS
        return Map.of(
                "question", question,
                "answer", answer,
                "contexts", contexts,  // <<< indispensable pour RAGAS
                "metadata", Map.of(
                        "latency_ms", elapsed,
                        "topK", topK,
                        "source", "mongo + wikipedia"
                )
        );
    }
}


