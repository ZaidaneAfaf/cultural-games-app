package com.example.projets5.model;

import lombok.*; import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LudiiMetadata {
    private String nativeName, origin, author, publisher, date;
    private GeoPoint originPoint; private YearRange evidenceRange;
    private Boolean dlpGame, publicGame, proprietaryGame, wishlistGame, helpUs, disableWebApp;
    private java.util.List<String> knownAliases;
}