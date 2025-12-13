package com.example.projets5.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QueryDTO {
    private String question;
    private Map<String, Object> filters;   // optionnel (pays, période, type, etc.)
}
