package com.example.projets5.service;

import com.example.projets5.dto.RetrieveResponseDTO;
import com.example.projets5.model.BoardGame;
import com.example.projets5.repository.GameRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

import static io.qdrant.client.WithPayloadSelectorFactory.enable;

@Service
@RequiredArgsConstructor
public class RagService {

    private final GameRepository gameRepository;
    private final QdrantClient qdrant;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final WikipediaService wikipediaService;

    @Value("${rag.collection:boardgames}")
    private String collection;

    @Value("${rag.top-k:5}")
    private int topK;

    // ===================== PUBLIC API =====================

    public RetrieveResponseDTO retrieve(String question, Map<String, Object> filters) {
        var retrieval = internalRetrieve(question);

        return new RetrieveResponseDTO(
                question,
                retrieval.contexts,
                retrieval.ids,
                retrieval.games.stream().map(BoardGame::getName).toList(),
                retrieval.scores,
                retrieval.meta
        );
    }

    public Map<String, Object> ask(String question, Map<String, Object> filters) {

        long startTotal = System.currentTimeMillis();
        var retrieval = internalRetrieve(question);

        if (retrieval.contexts.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTotal;
            return Map.of(
                    "question", question,
                    "answer", "Sorry, I cannot answer this because it is outside the provided context.",
                    "contexts", List.of(),
                    "metadata", Map.of(
                            "latency_ms", elapsed,
                            "topK", topK,
                            "note", "no_context_found"
                    )
            );
        }

        String promptText = buildPrompt(retrieval.fullContext, question);

        long startLLM = System.currentTimeMillis();
        String raw;
        try {
            raw = chatClient.prompt(promptText).call().content();
        } catch (Exception e) {
            long elapsedTotal = System.currentTimeMillis() - startTotal;

            Map<String, Object> meta = new LinkedHashMap<>(retrieval.meta);
            meta.put("latency_ms", elapsedTotal);
            meta.put("llm_ms", System.currentTimeMillis() - startLLM);
            meta.put("topK", topK);
            meta.put("source", "mongo + wikipedia");

            meta.put("llm_error", true);
            meta.put("llm_error_type", e.getClass().getSimpleName());
            meta.put("llm_error_message", safeMsg(e));

            String msg = safeMsg(e).toLowerCase();
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("rate_limit_exceeded")) {
                meta.put("llm_error_code", 429);
                meta.put("note", "rate_limited");
            } else {
                meta.put("note", "llm_failed");
            }

            return Map.of(
                    "question", question,
                    "answer", "Sorry, I cannot answer this right now due to a temporary LLM issue. Please retry.",
                    "contexts", retrieval.contexts,
                    "metadata", meta
            );
        }

        String answer = stripThink(raw);
        long elapsedTotal = System.currentTimeMillis() - startTotal;

        Map<String, Object> meta = new LinkedHashMap<>(retrieval.meta);
        meta.put("latency_ms", elapsedTotal);
        meta.put("llm_ms", System.currentTimeMillis() - startLLM);
        meta.put("topK", topK);
        meta.put("source", "mongo + wikipedia");

        return Map.of(
                "question", question,
                "answer", answer,
                "contexts", retrieval.contexts,
                "metadata", meta
        );
    }

    // ===================== INTERNALS =====================

    private Retrieval internalRetrieve(String question) {

        long startTotal = System.currentTimeMillis();

        // 1) Embedding question
        long startEmbedding = System.currentTimeMillis();
        float[] vec = embeddingModel.embed(question);
        List<Float> queryVector = new ArrayList<>(vec.length);
        for (float v : vec) queryVector.add(v);
        long endEmbedding = System.currentTimeMillis();

        // 2) Qdrant search
        long startQdrant = System.currentTimeMillis();
        List<ScoredPoint> scoredPoints;
        try {
            scoredPoints = qdrant.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collection)
                            .addAllVector(queryVector)
                            .setLimit(topK)
                            .setWithPayload(enable(true))
                            .build()
            ).get();
        } catch (Exception e) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("error", "qdrant_error");
            meta.put("message", safeMsg(e));
            meta.put("embedding_ms", endEmbedding - startEmbedding);
            meta.put("latency_ms", System.currentTimeMillis() - startTotal);
            return new Retrieval(List.of(), "", List.of(), List.of(), List.of(), meta);
        }
        long endQdrant = System.currentTimeMillis();

        // 3) Extract mongoIds (if present) + fallback names + scores
        List<String> mongoIds = new ArrayList<>();
        List<String> fallbackNames = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        // also keep payload contexts fallback (if nothing in mongo)
        List<String> payloadContexts = new ArrayList<>();

        for (ScoredPoint sp : scoredPoints) {
            String mid = resolveMongoIdFromPayload(sp);      // ✅ ONLY payload mongoId, not qdrant id
            String nm  = resolveNameFromPayload(sp);         // ✅ name/gameName/title etc.

            if (mid != null) mongoIds.add(mid);
            if (nm != null) fallbackNames.add(nm);

            scores.add((double) sp.getScore());

            // last-resort context from payload if available
            String payloadCtx = buildContextFromPayload(sp);
            if (payloadCtx != null && !payloadCtx.isBlank()) {
                payloadContexts.add(payloadCtx);
            }
        }

        // 4) Load from Mongo (by mongoId first, fallback by name)
        long startMongo = System.currentTimeMillis();

        Map<String, BoardGame> byId = new HashMap<>();
        if (!mongoIds.isEmpty()) {
            List<BoardGame> gamesById = gameRepository.findAllById(mongoIds);
            for (BoardGame g : gamesById) byId.put(g.getId(), g);
        }

        Map<String, BoardGame> byName = new HashMap<>();
        if (!fallbackNames.isEmpty()) {
            // try batch query (case-insensitive)
            List<String> distinctNames = fallbackNames.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();

            if (!distinctNames.isEmpty()) {
                List<BoardGame> gamesByName = gameRepository.findByNameInIgnoreCase(distinctNames);
                for (BoardGame g : gamesByName) {
                    if (g.getName() != null) byName.put(g.getName().toLowerCase(), g);
                }
            }
        }

        // preserve qdrant order: pick id if exists, else pick byName, else null
        List<BoardGame> ordered = new ArrayList<>();
        List<String> orderedIds = new ArrayList<>();
        List<String> orderedNames = new ArrayList<>();

        for (ScoredPoint sp : scoredPoints) {
            String mid = resolveMongoIdFromPayload(sp);
            String nm  = resolveNameFromPayload(sp);

            BoardGame g = null;
            if (mid != null) g = byId.get(mid);
            if (g == null && nm != null) g = byName.get(nm.toLowerCase());

            if (g != null) {
                ordered.add(g);
                orderedIds.add(g.getId());
                orderedNames.add(g.getName());
            }
        }

        long endMongo = System.currentTimeMillis();

        // 5) Build contexts (prefer Mongo games; if none, fallback payload contexts)
        long startContext = System.currentTimeMillis();
        List<String> contexts = new ArrayList<>();
        StringBuilder fullContext = new StringBuilder();

        if (!ordered.isEmpty()) {
            for (BoardGame g : ordered) {
                String block = buildContextBlock(g);
                if (!block.isBlank()) {
                    contexts.add(block);
                    fullContext.append(block).append("\n");
                }
            }
        } else {
            // ✅ fallback: build contexts directly from Qdrant payload
            for (String pc : payloadContexts) {
                if (pc != null && !pc.isBlank()) {
                    contexts.add(pc);
                    fullContext.append(pc).append("\n");
                }
            }
        }
        long endContext = System.currentTimeMillis();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("embedding_ms", endEmbedding - startEmbedding);
        meta.put("qdrant_ms", endQdrant - startQdrant);
        meta.put("mongo_ms", endMongo - startMongo);
        meta.put("context_ms", endContext - startContext);
        meta.put("qdrant_points", scoredPoints.size());
        meta.put("payload_mongoIds_count", mongoIds.size());
        meta.put("payload_names_count", fallbackNames.size());
        meta.put("retrieved_ids", orderedIds);
        meta.put("retrieved_games", orderedNames);
        meta.put("latency_ms", System.currentTimeMillis() - startTotal);

        return new Retrieval(contexts, fullContext.toString(), orderedIds, ordered, scores, meta);
    }

    /**
     * ✅ IMPORTANT:
     * Do NOT fallback to Qdrant point id for mongo id.
     * That was the reason contexts were empty (Mongo ids != Qdrant ids).
     */
    private String resolveMongoIdFromPayload(ScoredPoint sp) {
        if (sp.getPayloadMap().containsKey("mongoId")
                && sp.getPayloadMap().get("mongoId").hasStringValue()) {
            return sp.getPayloadMap().get("mongoId").getStringValue();
        }
        return null;
    }

    private String resolveNameFromPayload(ScoredPoint sp) {
        // try common keys
        for (String k : List.of("name", "gameName", "title")) {
            if (sp.getPayloadMap().containsKey(k) && sp.getPayloadMap().get(k).hasStringValue()) {
                String v = sp.getPayloadMap().get(k).getStringValue();
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    private String buildContextFromPayload(ScoredPoint sp) {
        // If your index stored these, we can build a context without Mongo.
        String name = resolveNameFromPayload(sp);

        String desc = null;
        if (sp.getPayloadMap().containsKey("description") && sp.getPayloadMap().get("description").hasStringValue()) {
            desc = sp.getPayloadMap().get("description").getStringValue();
        } else if (sp.getPayloadMap().containsKey("desc") && sp.getPayloadMap().get("desc").hasStringValue()) {
            desc = sp.getPayloadMap().get("desc").getStringValue();
        }

        String cats = null;
        if (sp.getPayloadMap().containsKey("categories") && sp.getPayloadMap().get("categories").hasStringValue()) {
            cats = sp.getPayloadMap().get("categories").getStringValue();
        } else if (sp.getPayloadMap().containsKey("category") && sp.getPayloadMap().get("category").hasStringValue()) {
            cats = sp.getPayloadMap().get("category").getStringValue();
        }

        if ((name == null || name.isBlank()) && (desc == null || desc.isBlank()) && (cats == null || cats.isBlank())) {
            return "";
        }

        StringBuilder b = new StringBuilder();
        b.append("Game: ").append(name == null ? "Unknown" : name).append(". ");
        if (desc != null && !desc.isBlank()) b.append("Description: ").append(desc).append(". ");
        if (cats != null && !cats.isBlank()) b.append("Categories: ").append(cats).append(". ");

        return b.toString().trim();
    }

    private String buildContextBlock(BoardGame g) {
        StringBuilder block = new StringBuilder();
        block.append("Game: ").append(nullSafe(g.getName())).append(". ");

        if (g.getDescription() != null && !g.getDescription().isBlank()) {
            block.append("Description: ").append(g.getDescription()).append(". ");
        }
        if (g.getCategories() != null && !g.getCategories().isEmpty()) {
            block.append("Categories: ").append(String.join(", ", g.getCategories())).append(". ");
        }

        try {
            String title = wikipediaService.searchTitle(g.getName());
            if (title != null) {
                String extract = wikipediaService.getExtract(title);
                if (extract != null && !extract.isBlank()) {
                    String shortExtract = extract.length() > 600 ? extract.substring(0, 600) + "..." : extract;
                    block.append("Wikipedia context (").append(title).append("): ")
                            .append(shortExtract).append(". ");
                }
            }
        } catch (Exception ignored) {}

        return block.toString().trim();
    }

    // ✅ Prompt conseillé pour de meilleurs scores RAGAS (moins strict)
    private String buildPrompt(String fullContext, String question) {
        return """
                IMPORTANT — INTERNAL REASONING SAFETY RULE
                                You may reason internally to generate a rich and accurate answer,
                                but you must NEVER reveal your internal reasoning, analysis, or chain-of-thought.
                                Your output must contain ONLY the final answer.
                
                                ────────────────────────────────
                                ROLE & PERSONA
                                ────────────────────────────────
                                You are "GameBoardGenius", an old wise scholar devoted to board games.
                
                                You speak like a learned elder telling a story:
                                - calm, reflective, narrative
                                - pedagogical but never simplistic
                                - culturally and historically sensitive
                                - interactive and guiding, never authoritative
                
                                You do NOT mention that you are an AI, a model, or a system.
                
                                ────────────────────────────────
                                MISSION
                                ────────────────────────────────
                                Your mission is to explain and explore board games through their
                                historical, cultural, social, and mechanical dimensions.
                
                                Your answers must:
                                - read as a continuous, flowing narrative
                                - feel like a story being told, not a report
                                - contain depth, details, and logical progression
                                - remain strictly grounded in the provided CONTEXT
                
                                You must help the user:
                                - understand games even when their description is incomplete
                                - rediscover forgotten games through guided reflection
                                - learn through explanation, not through rigid structure
                
                                ────────────────────────────────
                                STRICT GROUNDING RULES (HIGHEST PRIORITY)
                                ────────────────────────────────
                                - Use ONLY information explicitly present in the CONTEXT.
                                - Do NOT invent names, dates, mechanics, themes, regions, or facts.
                                - Do NOT rely on external knowledge or unstated assumptions.
                                - Every factual statement must be traceable to CONTEXT.
                
                                If a requested element is NOT present in CONTEXT, say exactly:
                                "This information is not available in the provided context."
                
                                ────────────────────────────────
                                CONTROLLED INTERPRETATION (ALLOWED)
                                ────────────────────────────────
                                You MAY:
                                - connect ideas that are explicitly present in CONTEXT
                                - interpret historical, cultural, or social meaning ONLY if supported by CONTEXT
                                - infer relationships ONLY when clearly suggested by CONTEXT
                
                                You MUST NOT:
                                - guess missing facts
                                - extrapolate beyond CONTEXT
                                - generalize historically without textual support
                
                                ────────────────────────────────
                                INTERACTIVITY RULES
                                ────────────────────────────────
                                If the user's question or description is incomplete or ambiguous:
                
                                - Continue the narrative instead of stopping.
                                - Explain what can be understood from the CONTEXT.
                                - Clearly mention what information is missing.
                                - Ask 1 to 3 reflective clarifying questions, naturally woven into the story.
                                - When appropriate, introduce hypotheses using wording such as:
                                  "Based on the available context, one possible interpretation is..."
                
                                If the question is entirely outside CONTEXT, respond EXACTLY:
                                "This information is not available in the provided context."
                
                                ────────────────────────────────
                                SUGGESTION MODE
                                ────────────────────────────────
                                When the user vaguely describes a game:
                
                                You MAY suggest possible games ONLY if:
                                - each suggestion is explicitly justified by elements in CONTEXT
                
                                When suggesting:
                                - integrate suggestions naturally into the narrative
                                - explain gently why each game may correspond
                                - avoid certainty; use cautious, scholarly language
                
                                If CONTEXT does not support suggestions:
                                - say so clearly within the narrative
                                - ask clarifying questions instead
                                If the QUESTION is a greeting, casual message, or small talk
                                                (e.g., "hi", "hello", "how are you"):
                
                                                - Respond briefly and politely.
                                                - Do NOT introduce board games.
                                                - Do NOT use historical or narrative exposition.
                                                - Do NOT use the CONTEXT.
                
                
                                ────────────────────────────────
                                STYLE CONSTRAINT (VERY IMPORTANT)
                                ────────────────────────────────
                                - Do NOT use explicit section titles or headings in the final answer.
                                - Do NOT number sections.
                                - Do NOT expose any internal structure.
                                - The response must feel like a single, coherent story with natural transitions.
                
                                ────────────────────────────────
                                INPUTS
                                ────────────────────────────────
                                CONTEXT:
                                %s
                
                                QUESTION:
                                %s
                
                
""".formatted(fullContext, question);
    }

    private String stripThink(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("(?s)<think>.*?</think>", "").trim();
        cleaned = cleaned.replaceAll("(?is)^\\s*(reasoning|analysis)\\s*:\\s*.*$", "").trim();
        return cleaned.isBlank() ? text.trim() : cleaned;
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
    }

    private String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return "";
        return m.length() > 1200 ? m.substring(0, 1200) : m;
    }

    private record Retrieval(
            List<String> contexts,
            String fullContext,
            List<String> ids,
            List<BoardGame> games,
            List<Double> scores,
            Map<String, Object> meta
    ) {}
}
