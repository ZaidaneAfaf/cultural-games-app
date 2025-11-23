package com.boardgames.rag.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BggMetadata {
    private Integer bggId;
    private Boolean isReimplementation;
    private Boolean kickstarted;
    private String family;
    private String designers;
    private String artists;
    private String publishers;
    private Double stdDevRating;
    private Integer numWant;
    private Integer numWish;
    private Integer numUserRatings;

    // Catégories booléennes
    private Boolean catStrategy;
    private Boolean catThematic;
    private Boolean catWar;
    private Boolean catFamily;
    private Boolean catAbstract;
    private Boolean catParty;
}