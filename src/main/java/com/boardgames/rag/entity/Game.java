package com.boardgames.rag.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "games")
public class Game {

    @Id
    private String id;

    // === CHAMPS COMMUNS ===
    private String name;
    private String description;
    private Integer yearPublished;
    private List<String> categories;
    private List<String> mechanics;
    private String imageUrl;

    // === IDENTIFICATION SOURCE ===
    private GameSource source;
    private String sourceId;
    private String bggId; // Pour lier BGG et Ludii

    // === INFORMATIONS JOUEURS ===
    private Integer minPlayers;
    private Integer maxPlayers;
    private List<Integer> bestPlayers;
    private String recommendedAge;

    // === DURÉE ===
    private Integer minPlaytime;
    private Integer maxPlaytime;
    private Integer estimatedPlaytime;

    // === NOTES ET COMPLEXITÉ ===
    private Double averageRating;
    private Double bayesAverageRating;
    private Double complexityWeight;
    private Integer numRatings;
    private Integer numOwned;
    private Integer numWishlist;
    private String wikipediaSummary;
    private String wikipediaUrl;

    // === CLASSEMENTS ===
    private Map<String, Integer> rankings;

    // === MÉTADONNÉES SPÉCIFIQUES ===
    private BggMetadata bggMetadata;
    private LudiiMetadata ludiiMetadata;

    // === RÈGLES ET VARIANTES ===
    private List<Ruleset> rulesets;

    // === ENRICHISSEMENT WIKIPEDIA ===
    private WikipediaInfo wikipediaInfo;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    public enum GameSource {
        BGG, LUDII, MERGED
    }

    // Constructeur pour tests
    public Game(String name, String description, Integer yearPublished, GameSource source) {
        this.name = name;
        this.description = description;
        this.yearPublished = yearPublished;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}