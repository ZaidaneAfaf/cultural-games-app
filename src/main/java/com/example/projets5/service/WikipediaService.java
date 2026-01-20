package com.example.projets5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WikipediaService {

    private final RestTemplate restTemplate;

    @Value("${wikipedia.base-url:https://fr.wikipedia.org/w/api.php}")
    private String baseUrl;

    @Value("${wikipedia.user-agent:BoardGamesRAG/1.0 (contact@example.com)}")
    private String userAgent;

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", userAgent);
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    public String searchTitle(String query) {
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?action=query&list=search&srsearch=%s&format=json&srlimit=1",
                    baseUrl, q
            );
            ResponseEntity<Map> resp = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class
            );

            Object queryObj = resp.getBody().get("query");
            if (!(queryObj instanceof Map)) return null;
            Object search = ((Map<?, ?>) queryObj).get("search");

            if (search instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> m && m.get("title") != null) {
                    return String.valueOf(m.get("title"));
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getExtract(String title) {
        if (title == null || title.isBlank()) return null;
        try {
            String t = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?action=query&prop=extracts&exintro=true&explaintext=true&titles=%s&format=json",
                    baseUrl, t
            );
            ResponseEntity<Map> resp = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class
            );

            Object queryObj = resp.getBody().get("query");
            if (!(queryObj instanceof Map)) return null;
            Map<?, ?> pages = (Map<?, ?>) ((Map<?, ?>) queryObj).get("pages");

            for (Object k : pages.keySet()) {
                Map<?, ?> page = (Map<?, ?>) pages.get(k);
                Object extract = page.get("extract");
                if (extract != null) return String.valueOf(extract);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
