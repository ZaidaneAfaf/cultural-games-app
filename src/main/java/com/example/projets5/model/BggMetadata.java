package com.example.projets5.model;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BggMetadata {
    private Double avgRating, bayesAvgRating, stdDev, gameWeight;
    private Integer minPlayers, maxPlayers, communityAgeRec, languageEase;
    private Integer bestPlayers, goodPlayers;
    private Integer mfgPlaytime, comMinPlaytime, comMaxPlaytime;
    private Integer numOwned, numWant, numWish, numUserRatings;
    private Integer rankBoardgame, rankStrategyGames;
    private Boolean kickstarted, isReimplementation;
    private String family, imagePath;
}
