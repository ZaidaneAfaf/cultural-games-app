package com.boardgames.rag.service.wikipedia;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;

@Service
public class WikipediaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, WikipediaResult> cache;
    private final ExecutorService executorService;

    public WikipediaService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.cache = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();

        // Configurer RestTemplate avec timeout
        restTemplate.setRequestFactory(getRequestFactoryWithTimeout());
    }

    /**
     * Récupère le résumé Wikipedia avec TIMEOUT
     */
    public WikipediaResult getGameSummary(String gameName) {
        return getGameSummaryWithTimeout(gameName, 2000); // 2 secondes par défaut
    }

    /**
     * Version avec timeout configurable
     */
    public WikipediaResult getGameSummaryWithTimeout(String gameName, long timeoutMs) {
        if (gameName == null || gameName.trim().isEmpty()) {
            return null;
        }

        // Vérifier le cache d'abord
        String cacheKey = gameName.toLowerCase().trim();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        CompletableFuture<WikipediaResult> future = CompletableFuture.supplyAsync(() -> {
            return fetchWikipediaSummary(gameName);
        }, executorService);

        try {
            WikipediaResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result != null && result.getSummary() != null) {
                cache.put(cacheKey, result);
            } else {
                // Cache les échecs pour éviter de retenter
                cache.put(cacheKey, new WikipediaResult(null, null));
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            System.err.println("⏰ Timeout Wikipedia pour: " + gameName);
            cache.put(cacheKey, new WikipediaResult(null, null));
            return null;
        } catch (Exception e) {
            System.err.println("❌ Erreur Wikipedia pour: " + gameName);
            cache.put(cacheKey, new WikipediaResult(null, null));
            return null;
        }
    }

    /**
     * Récupération effective du résumé Wikipedia
     */
    private WikipediaResult fetchWikipediaSummary(String gameName) {
        try {
            // Nettoyer le nom du jeu pour Wikipedia
            String cleanedName = cleanGameNameForWikipedia(gameName);

            String url = "https://fr.wikipedia.org/api/rest_v1/page/summary/" +
                    java.net.URLEncoder.encode(cleanedName, "UTF-8");

            // Configurer les headers avec User-Agent
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "BoardGamesRAG/1.0 (contact@example.com)");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.has("extract") && !root.get("extract").asText().isEmpty()) {
                    String summary = root.get("extract").asText();
                    String urlContent = root.has("content_urls") ?
                            root.get("content_urls").get("desktop").get("page").asText() :
                            getGameWikipediaUrl(cleanedName);

                    // Nettoyer et formater le résumé
                    String formattedSummary = formatSummary(summary);

                    WikipediaResult result = new WikipediaResult(formattedSummary, urlContent);
                    return result;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur Wikipedia fetch pour: " + gameName + " - " + e.getMessage());

            // Essayer avec le nom anglais si l'échec est dû à la langue
            if (e.getMessage().contains("404") || e.getMessage().contains("403")) {
                return tryEnglishWikipedia(gameName);
            }
        }

        return null;
    }

    /**
     * Essaie avec Wikipedia anglais en cas d'échec
     */
    private WikipediaResult tryEnglishWikipedia(String gameName) {
        try {
            String cleanedName = cleanGameNameForWikipedia(gameName);
            String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                    java.net.URLEncoder.encode(cleanedName, "UTF-8");

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "BoardGamesRAG/1.0 (contact@example.com)");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.has("extract") && !root.get("extract").asText().isEmpty()) {
                    String summary = root.get("extract").asText();
                    String urlContent = root.has("content_urls") ?
                            root.get("content_urls").get("desktop").get("page").asText() :
                            "https://en.wikipedia.org/wiki/" + cleanedName.replace(" ", "_");

                    String formattedSummary = formatSummary(summary) + " (Source: English Wikipedia)";
                    return new WikipediaResult(formattedSummary, urlContent);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur Wikipedia anglais pour: " + gameName);
        }

        return null;
    }

    /**
     * Nettoie le nom du jeu pour Wikipedia
     */
    private String cleanGameNameForWikipedia(String gameName) {
        if (gameName == null) return "";

        // Supprimer les caractères spéciaux et normaliser
        return gameName.replaceAll("[^a-zA-Z0-9\\sàâäéèêëïîôöùûüÿçÀÂÄÉÈÊËÏÎÔÖÙÛÜŸÇ-]", "")
                .replace(" - ", " ")
                .trim();
    }

    /**
     * Formate le résumé pour l'affichage
     */
    private String formatSummary(String summary) {
        if (summary == null) return "";

        if (summary.length() > 400) {
            // Trouver la fin de la phrase la plus proche
            int lastPeriod = summary.lastIndexOf('.', 400);
            if (lastPeriod > 300) {
                return summary.substring(0, lastPeriod + 1) + "..";
            }
        }
        return summary.length() > 500 ? summary.substring(0, 500) + "..." : summary;
    }

    /**
     * Récupère l'URL de l'article Wikipedia
     */
    public String getGameWikipediaUrl(String gameName) {
        try {
            String cleanedName = cleanGameNameForWikipedia(gameName);
            String encodedName = java.net.URLEncoder.encode(cleanedName.replace(" ", "_"), "UTF-8");
            return "https://fr.wikipedia.org/wiki/" + encodedName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Configuration des timeouts HTTP
     */
    private org.springframework.http.client.SimpleClientHttpRequestFactory getRequestFactoryWithTimeout() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2 secondes connection timeout
        factory.setReadTimeout(2000);    // 2 secondes read timeout
        return factory;
    }

    /**
     * Vide le cache (utile pour les tests)
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Arrête l'executor service
     */
    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Classe pour stocker le résultat Wikipedia
     */
    public static class WikipediaResult {
        private final String summary;
        private final String url;

        public WikipediaResult(String summary, String url) {
            this.summary = summary;
            this.url = url;
        }

        public String getSummary() { return summary; }
        public String getUrl() { return url; }
    }
}