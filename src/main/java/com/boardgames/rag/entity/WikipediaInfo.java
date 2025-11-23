package com.boardgames.rag.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WikipediaInfo {
    private String pageId;
    private String title;
    private String extract;
    private String url;
    private Map<String, String> coordinates;
    private List<String> categories;
    private LocalDateTime lastUpdated;
}