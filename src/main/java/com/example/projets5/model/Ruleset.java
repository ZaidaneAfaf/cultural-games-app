package com.example.projets5.model;

import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Ruleset {
    private String rulesetId, name, nativeName, summary, description, rules, reference;
    private Integer type; private String origin, author, publisher, date, notes;
    private GeoPoint originPoint; private YearRange evidenceRange;
    private Boolean selfContained, disableWebApp, wishlistRuleset;
}
