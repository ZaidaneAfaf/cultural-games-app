package com.boardgames.rag.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LudiiMetadata {
    private Integer ludiiId;
    private String nativeName;
    private String origin;
    private String originPoint; // Coordonnées GPS "30°1'55.75"N, 31°4'31.13"E"
    private String evidenceRange; // Période historique "530,3199"
    private String mainRuleset;
    private String ludiiRuleset;
    private String reference;
    private Boolean dlpGame;
    private Boolean publicGame;
    private String author;
    private String publisher;
    private String dateAdded;
    private List<String> knownAliases;
    private Boolean proprietaryGame;
    private Boolean wishlistGame;
    private Boolean helpUs;
    private Boolean disableWebApp;
}