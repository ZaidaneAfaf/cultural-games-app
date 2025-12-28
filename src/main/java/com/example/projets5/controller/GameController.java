package com.example.projets5.controller;

import com.example.projets5.dto.GameResponseDTO;
import com.example.projets5.dto.QueryDTO;
import com.example.projets5.repository.GameRepository;
import com.example.projets5.service.*;
import lombok.RequiredArgsConstructor;
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
    public GameResponseDTO ask(@RequestBody QueryDTO query) {

        Map<String, Object> res = ragService.ask(
                query.getQuestion(),
                query.getFilters() != null ? query.getFilters() : Map.of()
        );

        // On retransforme proprement la Map renvoyée par RagService
        String question   = (String) res.get("question");
        String answer     = (String) res.get("answer");
        List<String> ctxs = (List<String>) res.getOrDefault("contexts", List.of());
        Map<String, Object> meta =
                (Map<String, Object>) res.getOrDefault("metadata", Map.of());

        return new GameResponseDTO(question, answer, ctxs, meta);
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
