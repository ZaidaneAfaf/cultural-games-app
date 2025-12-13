package com.example.projets5.repository;


import com.example.projets5.model.BoardGame;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameRepository extends MongoRepository<BoardGame, String> {
    java.util.List<BoardGame> findByNameContainingIgnoreCase(String name);
    java.util.List<BoardGame> findByYearPublishedBetween(Integer start, Integer end);
    java.util.List<BoardGame> findByBgg_GameWeightBetween(Double min, Double max);
    java.util.List<BoardGame> findByBgg_MinPlayersLessThanEqualAndBgg_MaxPlayersGreaterThanEqual(Integer min, Integer max);
    java.util.List<BoardGame> findByCategoriesIn(java.util.List<String> cats);
    java.util.List<BoardGame> findBySource(String source);
    java.util.List<BoardGame> findByBggId(String bggId);
    java.util.List<BoardGame> findByLudiiId(String ludiiId);
}
