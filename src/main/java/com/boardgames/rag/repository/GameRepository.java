package com.boardgames.rag.repository;

import com.boardgames.rag.entity.Game;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GameRepository extends MongoRepository<Game, String> {

    // ============================================
    // 🔍 MÉTHODES EXISTANTES (pour compatibilité)
    // ============================================

    @Query(value = "{ 'name': { $regex: ?0, $options: 'i' } }",
            fields = "{ 'name': 1, 'description': 1, 'yearPublished': 1, 'averageRating': 1, 'minPlayers': 1, 'maxPlayers': 1, 'complexityWeight': 1, 'wikipediaSummary': 1 }")
    List<Map<String, Object>> findByNameContainingIgnoreCaseWithProjection(String name);

    List<Game> findByNameContainingIgnoreCase(String name);
    Game findByName(String name);

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Game> findByNameRegex(String name);

    List<Game> findBySource(Game.GameSource source);
    List<Game> findByCategoriesContaining(String category);

    @Query("{ 'ludiiMetadata.origin': { $regex: ?0, $options: 'i' } }")
    List<Game> findByOrigin(String origin);

    List<Game> findByYearPublishedBetween(int startYear, int endYear);

    Optional<Game> findBySourceIdAndSource(String sourceId, Game.GameSource source);

    // ============================================
    // 🆕 NOUVELLES MÉTHODES POUR LA RECHERCHE ARCHÉO
    // ============================================

    @Query("{ 'description': { $regex: ?0, $options: 'i' } }")
    List<Game> findByDescriptionContainingIgnoreCase(String keyword);

    @Query("{$or: [{'name': {$regex: ?0, $options: 'i'}}, {'description': {$regex: ?0, $options: 'i'}}]}")
    List<Game> findByNameOrDescriptionContainingIgnoreCase(String keyword);

    @Query("{$or: ["
            + "{'description': {$regex: '.*os.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*pierre.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*argile.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*bois.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*ivoire.*', $options: 'i'}}"
            + "]}")
    List<Game> findByAncientMaterials();

    @Query("{$or: ["
            + "{'description': {$regex: '.*dé.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*dés.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*dice.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*tessera.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*astragale.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*dé.*', $options: 'i'}}"
            + "]}")
    List<Game> findByDiceOrBoneGames();

    @Query("{ 'yearPublished': { $lt: 1500 } }")
    List<Game> findAncientGames();

    @Query("{ 'yearPublished': { $lt: 500 } }")
    List<Game> findVeryAncientGames();

    @Query("{ 'yearPublished': { $lt: 0 } }")
    List<Game> findBCGames();

    @Query("{$or: ["
            + "{'ludiiMetadata.origin': {$regex: '.*rome.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*roman.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*romain.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*latrunculorum.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*tabula.*', $options: 'i'}}"
            + "]}")
    List<Game> findRomanGames();

    @Query("{$or: ["
            + "{'ludiiMetadata.origin': {$regex: '.*egypt.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*égypt.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*égypt.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*senet.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*mehen.*', $options: 'i'}}"
            + "]}")
    List<Game> findEgyptianGames();

    @Query("{$or: ["
            + "{'ludiiMetadata.origin': {$regex: '.*greece.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*grec.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*grec.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*petteia.*', $options: 'i'}}"
            + "]}")
    List<Game> findGreekGames();

    @Query("{$or: ["
            + "{'ludiiMetadata.origin': {$regex: '.*mesopotamia.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*sumer.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*babylon.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*ur.*', $options: 'i'}}"
            + "]}")
    List<Game> findMesopotamianGames();

    // 🔥 NOUVELLE : Jeux vikings
    @Query("{$or: ["
            + "{'ludiiMetadata.origin': {$regex: '.*viking.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*norse.*', $options: 'i'}}, "
            + "{'ludiiMetadata.origin': {$regex: '.*scandinav.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*viking.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*hnefatafl.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*tafl.*', $options: 'i'}}"
            + "], 'yearPublished': {$gte: 700, $lte: 1100}}")
    List<Game> findVikingGames();

    // 🔥 NOUVELLE : Dés antiques spécifiquement
    @Query("{$and: ["
            + "{'yearPublished': {$lt: 500}}, "
            + "{$or: ["
            + "{'description': {$regex: '.*dé.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*dice.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*tessera.*', $options: 'i'}}, "
            + "{'name': {$regex: '.*astragale.*', $options: 'i'}}"
            + "]}]}")
    List<Game> findAncientDiceGames();

    @Query("{'name': {$regex: ?0, $options: 'i'}, 'description': {$regex: ?1, $options: 'i'}}")
    List<Game> findByNameAndMaterial(String nameKeyword, String materialKeyword);

    @Query("{ 'source': 'LUDII' }")
    List<Game> findAllLudiiGames();

    @Query("{ 'source': 'LUDII', 'yearPublished': { $gte: ?0, $lte: ?1 } }")
    List<Game> findLudiiGamesByPeriod(int startYear, int endYear);

    @Query("{ 'description': { $regex: '.*\\bos\\b.*', $options: 'i' } }")
    List<Game> findByBoneMaterial();

    @Query("{ 'description': { $regex: '.*\\bpierre\\b.*', $options: 'i' } }")
    List<Game> findByStoneMaterial();

    @Query("{$and: ["
            + "{'description': {$regex: ?0, $options: 'i'}}, "
            + "{'description': {$regex: ?1, $options: 'i'}}"
            + "]}")
    List<Game> findByMultipleKeywords(String keyword1, String keyword2);

    @Query("{$and: ["
            + "{'yearPublished': {$lt: 1500}}, "
            + "{$or: ["
            + "{'description': {$regex: '.*os.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*pierre.*', $options: 'i'}}, "
            + "{'description': {$regex: '.*argile.*', $options: 'i'}}"
            + "]}]}")
    List<Game> findAncientGamesWithMaterials();

    @Query(value = "{}", fields = "{'source': 1}")
    List<Game> countBySource();

    @Query("{'yearPublished': {$not: {$gt: 1500}}}")
    List<Game> findNonModernGames();
}