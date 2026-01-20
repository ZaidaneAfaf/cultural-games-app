package com.example.projets5.controller;

import com.example.projets5.dto.*;
import com.example.projets5.model.BoardGame;
import com.example.projets5.repository.GameRepository;
import com.example.projets5.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameController {

    private final RagService ragService;
    private final IndexService indexService;
    private final ImportService importService;
    private final GameRepository gameRepository;

    // ================== QUESTION ANSWERING ==================

    @PostMapping("/ask")
    public ResponseEntity<GameResponseDTO> ask(@RequestBody QueryDTO query) {
        try {
            Map<String, Object> res = ragService.ask(
                    query.getQuestion(),
                    query.getFilters() != null ? query.getFilters() : Map.of()
            );

            String question = (String) res.get("question");
            String answer   = (String) res.get("answer");

            @SuppressWarnings("unchecked")
            List<String> ctxs = (List<String>) res.getOrDefault("contexts", List.of());

            @SuppressWarnings("unchecked")
            Map<String, Object> meta =
                    (Map<String, Object>) res.getOrDefault("metadata", Map.of());

            return ResponseEntity.ok(new GameResponseDTO(question, answer, ctxs, meta));

        } catch (Exception e) {
            // ✅ ne pas renvoyer 500 brut
            String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();

            int status = (msg.contains("429") || msg.contains("rate limit"))
                    ? 429 : 503;

            var dto = new GameResponseDTO(
                    query.getQuestion(),
                    "LLM_ERROR: " + (status == 429 ? "RATE_LIMIT" : "TEMPORARY_FAILURE"),
                    List.of(),
                    Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage())
            );

            return ResponseEntity.status(status).body(dto);
        }
    }


    // ✅ DEBUG: retrieval only (sans LLM) -> indispensable pour comprendre RAGAS
    @PostMapping("/retrieve")
    public RetrieveResponseDTO retrieve(@RequestBody QueryDTO query) {
        return ragService.retrieve(
                query.getQuestion(),
                query.getFilters() != null ? query.getFilters() : Map.of()
        );
    }

    // ✅ EXPORT CORPUS: pour générer automatiquement des questions d’évaluation côté Python
    // Exemple: /api/games/admin/corpus?page=0&size=200
    @GetMapping("/admin/corpus")
    public List<CorpusGameDTO> exportCorpus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        // tri stable : évite que l’ordre change entre runs
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return gameRepository.findAll(pageable).getContent().stream()
                .map(g -> new CorpusGameDTO(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getCategories()
                ))
                .toList();
    }

    // ================== ADMIN : INDEX / IMPORT ==================

    @PostMapping("/admin/reindex")
    public ResponseEntity<String> reindex() {
        indexService.rebuildIndex();
        return ResponseEntity.ok("Reindex lancé (subset depuis la page 0).");
    }

    @PostMapping("/admin/reindex/from")
    public ResponseEntity<String> reindexFrom(
            @RequestParam(defaultValue = "0") int startPage) {

        indexService.rebuildIndexFromPage(startPage);
        return ResponseEntity.ok("Reindex lancé depuis la page " + startPage + ".");
    }

    @PostMapping("/admin/import")
    public Map<String,String> doImport(@RequestParam String bggCsv,
                                       @RequestParam String ludiiGamesCsv,
                                       @RequestParam String rulesetsCsv) throws Exception {
        importService.importBGG(bggCsv);
        importService.importLudiiGames(ludiiGamesCsv);
        importService.importLudiiRulesets(rulesetsCsv);
        return Map.of("status","imported");
    }

    @GetMapping("/admin/count")
    public Map<String, Object> count() {
        long c = gameRepository.count();
        return Map.of("count", c);
    }
}
