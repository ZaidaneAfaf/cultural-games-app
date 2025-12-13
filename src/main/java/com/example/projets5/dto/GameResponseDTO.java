package com.example.projets5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class GameResponseDTO {

    private String question;
    private String answer;
    private List<String> contexts;      // les morceaux de contexte utilisés
    private Map<String, Object> metadata;  // latence, topK, source, etc.
}
