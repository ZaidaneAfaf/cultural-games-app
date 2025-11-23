package com.boardgames.rag.controller;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameController {

    private final GameRepository gameRepository;

    public GameController(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @GetMapping("/{id}")
    public Game getGameById(@PathVariable String id) {
        Optional<Game> game = gameRepository.findById(id);
        return game.orElseThrow(() -> new RuntimeException("Jeu non trouvé avec l'id: " + id));
    }

    @GetMapping("/search")
    public List<Game> searchGames(@RequestParam String name) {
        // Utilisez la méthode existante findByNameRegex au lieu de findByNameContainingIgnoreCase
        return gameRepository.findByNameRegex(".*" + name + ".*");
    }

    @GetMapping("/by-source/{source}")
    public List<Game> getGamesBySource(@PathVariable String source) {
        return gameRepository.findBySource(Game.GameSource.valueOf(source.toUpperCase()));
    }

    @GetMapping("/by-category/{category}")
    public List<Game> getGamesByCategory(@PathVariable String category) {
        return gameRepository.findByCategoriesContaining(category);
    }

    // Ajoutez un endpoint pour la santé de l'API
    @GetMapping("/health")
    public String health() {
        long count = gameRepository.count();
        return "✅ API Games opérationnelle - " + count + " jeux en base";
    }
}