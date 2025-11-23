package com.boardgames.rag.controller;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.rag.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;
    private final GameRepository gameRepository;

    public RagController(RagService ragService, GameRepository gameRepository) {
        this.ragService = ragService;
        this.gameRepository = gameRepository;
    }

    @GetMapping("/search")
    public RagService.RagResponse searchGames(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults) {

        System.out.println("🔍 Requête RAG reçue: \"" + query + "\"");
        return ragService.searchWithRag(query, maxResults);
    }

    @GetMapping("/games/{gameId}")
    public Game getGameDetails(@PathVariable String gameId) {
        System.out.println("🔍 Détails jeu demandé: " + gameId);
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Jeu non trouvé avec l'ID: " + gameId));
    }

    @GetMapping("/games/search")
    public List<Game> searchGamesByName(@RequestParam String name) {
        System.out.println("🔍 Recherche par nom: " + name);
        return gameRepository.findByNameContainingIgnoreCase(name);
    }

    @PostMapping("/cache/clear")
    public String clearCache() {
        ragService.clearCache();
        return "✅ Cache vidé avec succès";
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "✅ Système RAG opérationnel - " + java.time.LocalDateTime.now();
    }
}