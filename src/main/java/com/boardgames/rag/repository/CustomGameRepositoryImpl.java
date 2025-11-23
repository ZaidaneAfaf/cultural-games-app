package com.boardgames.rag.repository;

import com.boardgames.rag.entity.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class CustomGameRepositoryImpl implements CustomGameRepository {

    private final MongoTemplate mongoTemplate;

    public CustomGameRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Game> searchByCriteria(Map<String, Object> criteria, Pageable pageable) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.containsKey("categories") && criteria.get("categories") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) criteria.get("categories");
            criteriaList.add(Criteria.where("categories").in(categories));
        }

        if (criteria.containsKey("minPlayers")) {
            criteriaList.add(Criteria.where("minPlayers").gte(criteria.get("minPlayers")));
        }

        if (criteria.containsKey("maxPlayers")) {
            criteriaList.add(Criteria.where("maxPlayers").lte(criteria.get("maxPlayers")));
        }

        if (criteria.containsKey("minRating")) {
            criteriaList.add(Criteria.where("averageRating").gte(criteria.get("minRating")));
        }

        if (criteria.containsKey("maxComplexity")) {
            criteriaList.add(Criteria.where("complexityWeight").lte(criteria.get("maxComplexity")));
        }

        if (criteria.containsKey("origin")) {
            criteriaList.add(Criteria.where("ludiiMetadata.origin").regex(
                    criteria.get("origin").toString(), "i"));
        }

        if (criteria.containsKey("source")) {
            criteriaList.add(Criteria.where("source").is(
                    Game.GameSource.valueOf(criteria.get("source").toString().toUpperCase())));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, Game.class);
        query.with(pageable);

        List<Game> games = mongoTemplate.find(query, Game.class);
        return new org.springframework.data.domain.PageImpl<>(games, pageable, total);
    }

    @Override
    public List<Game> findSimilarGames(String gameId, List<String> categories,
                                       Double minRating, Double maxComplexity) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(Criteria.where("id").ne(gameId));

        if (categories != null && !categories.isEmpty()) {
            criteriaList.add(Criteria.where("categories").in(categories));
        }

        if (minRating != null) {
            criteriaList.add(Criteria.where("averageRating").gte(minRating));
        }

        if (maxComplexity != null) {
            criteriaList.add(Criteria.where("complexityWeight").lte(maxComplexity));
        }

        query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.limit(10);

        return mongoTemplate.find(query, Game.class);
    }

    @Override
    public List<Game> findHistoricalGamesByRegion(String region, String timePeriod) {
        Query query = new Query();

        Criteria regionCriteria = Criteria.where("ludiiMetadata.origin").regex(region, "i");
        Criteria timeCriteria = Criteria.where("ludiiMetadata.evidenceRange").regex(timePeriod);
        Criteria sourceCriteria = Criteria.where("source").is(Game.GameSource.LUDII);

        query.addCriteria(new Criteria().andOperator(regionCriteria, timeCriteria, sourceCriteria));

        return mongoTemplate.find(query, Game.class);
    }

    @Override
    public List<Game> findGamesByPlayerCount(Integer playerCount) {
        Query query = new Query(Criteria.where("minPlayers").lte(playerCount)
                .and("maxPlayers").gte(playerCount));
        query.limit(20);

        return mongoTemplate.find(query, Game.class);
    }
}