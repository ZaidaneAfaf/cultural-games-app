package com.example.projets5.dto;

import java.util.List;
import java.util.Map;

public record RetrieveResponseDTO(
        String question,
        List<String> contexts,
        List<String> retrievedIds,
        List<String> retrievedGames,
        List<Double> scores,
        Map<String, Object> metadata
) {}
