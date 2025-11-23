package com.boardgames.rag.repository;

import com.boardgames.rag.entity.Game;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GameRepository extends MongoRepository<Game, String> {

    // === METHODES EXISTANTES ===

    // Recherche par nom (insensible à la casse) - version avec projection pour l'affichage rapide
    @Query(value = "{ 'name': { $regex: ?0, $options: 'i' } }",
            fields = "{ 'name': 1, 'description': 1, 'yearPublished': 1, 'averageRating': 1, 'minPlayers': 1, 'maxPlayers': 1, 'complexityWeight': 1, 'wikipediaSummary': 1 }")
    List<Map<String, Object>> findByNameContainingIgnoreCaseWithProjection(String name);

    // Recherche complète par nom - pour les détails complets
    List<Game> findByNameContainingIgnoreCase(String name);

    // Recherche exacte
    Game findByName(String name);

    // === METHODES MANQUANTES - AJOUTEZ CES 6 METHODES ===

    // Pour GameController.findByNameRegex
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Game> findByNameRegex(String name);

    // Pour GameController.findBySource
    List<Game> findBySource(Game.GameSource source);

    // Pour GameController.findByCategoriesContaining
    List<Game> findByCategoriesContaining(String category);

    // Pour TestService.findByOrigin
    @Query("{ 'ludiiMetadata.origin': { $regex: ?0, $options: 'i' } }")
    List<Game> findByOrigin(String origin);

    // Pour TestService.findByYearPublishedBetween
    List<Game> findByYearPublishedBetween(int startYear, int endYear);

    // Pour LudiiRulesetsImportService.findBySourceIdAndSource - CORRIGEZ CETTE LIGNE :
    Optional<Game> findBySourceIdAndSource(String sourceId, Game.GameSource source);

}