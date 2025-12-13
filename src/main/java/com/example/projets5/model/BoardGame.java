package com.example.projets5.model;
import lombok.*; import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List; import java.util.Map;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Document("boardgames")
public class BoardGame {
    @Id private String id;
    private String source;              // BGG | LUDII | MERGED
    private String name, description;
    private String bggId, ludiiId;
    private Integer yearPublished;
    private java.util.List<String> categories, families;
    private BggMetadata bgg; private LudiiMetadata ludii;
    private java.util.List<Ruleset> rulesets;
    private java.util.Map<String,Object> extra;
}