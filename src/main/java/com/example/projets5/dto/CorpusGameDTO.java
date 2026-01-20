package com.example.projets5.dto;

import java.util.List;

public record CorpusGameDTO(
        String id,
        String name,
        String description,
        List<String> categories
) {}
