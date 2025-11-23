package com.boardgames.rag.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Ruleset {
    private String id;
    private String gameId;
    private String name;
    private String nativeName;
    private String summary;
    private String description;
    private String rules; // Texte détaillé des règles
    private Integer type;
    private String reference;
    private String origin;
    private String author;
    private String publisher;
    private String date;
    private String originPoint;
    private String evidenceRange;
    private Boolean selfContained;
    private Boolean wishlistRuleset;
    private Boolean disableWebApp;
}