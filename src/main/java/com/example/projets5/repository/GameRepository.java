package com.example.projets5.repository;

import com.example.projets5.model.BoardGame;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends MongoRepository<BoardGame, String> {

    // ===================== EXISTING METHODS =====================
    List<BoardGame> findByNameContainingIgnoreCase(String name);
    List<BoardGame> findByYearPublishedBetween(Integer start, Integer end);
    List<BoardGame> findByBgg_GameWeightBetween(Double min, Double max);
    List<BoardGame> findByBgg_MinPlayersLessThanEqualAndBgg_MaxPlayersGreaterThanEqual(Integer min, Integer max);
    List<BoardGame> findByCategoriesIn(List<String> cats);
    List<BoardGame> findBySource(String source);
    List<BoardGame> findByBggId(String bggId);
    List<BoardGame> findByLudiiId(String ludiiId);

    // ===================== NEW (RAG FALLBACK) =====================
    // ✅ exact name match (case-insensitive), returns first match
    Optional<BoardGame> findFirstByNameIgnoreCase(String name);

    // ✅ batch exact name match (case-insensitive)
    List<BoardGame> findByNameInIgnoreCase(List<String> names);
}
