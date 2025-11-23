package com.boardgames.rag.repository;

import com.boardgames.rag.entity.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface CustomGameRepository {

    Page<Game> searchByCriteria(Map<String, Object> criteria, Pageable pageable);

    List<Game> findSimilarGames(String gameId, List<String> categories,
                                Double minRating, Double maxComplexity);

    List<Game> findHistoricalGamesByRegion(String region, String timePeriod);

    List<Game> findGamesByPlayerCount(Integer playerCount);
}