package com.boardgames.rag.service;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Service
public class CsvDataCleaner {

    /**
     * Nettoie les descriptions avec espaces multiples et texte collé
     */
    public String cleanDescription(String description) {
        if (description == null) return "";

        // Remplacer les espaces multiples par un seul espace
        String cleaned = description.replaceAll("\\s+", " ");

        // Ajouter des points si manquants (texte collé)
        if (!cleaned.matches(".*[.!?]$") && cleaned.length() > 50) {
            cleaned += ".";
        }

        return cleaned.trim();
    }

    /**
     * Parse les listes au format "['4', '5']" en List<Integer>
     */
    public List<Integer> parseBestPlayers(String bestPlayersStr) {
        List<Integer> result = new ArrayList<>();

        if (bestPlayersStr == null || bestPlayersStr.trim().isEmpty()) {
            return result;
        }

        try {
            // Enlever les crochets et guillemets
            String cleaned = bestPlayersStr.replace("[", "").replace("]", "").replace("'", "").replace("\"", "");
            String[] parts = cleaned.split(",");

            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(Integer.parseInt(trimmed));
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur parsing bestPlayers: " + bestPlayersStr);
        }

        return result;
    }

    /**
     * Parse les listes de catégories/aliases
     */
    public List<String> parseStringList(String listStr) {
        if (listStr == null || listStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Gérer différents formats : "['cat1', 'cat2']" ou "cat1,cat2"
            String cleaned = listStr.replace("[", "").replace("]", "").replace("'", "").replace("\"", "");
            String[] parts = cleaned.split(",");

            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("Erreur parsing string list: " + listStr);
            return new ArrayList<>();
        }
    }

    /**
     * Convertit les rankings (21926 = "non classé")
     */
    public Integer parseRanking(String rankingStr) {
        if (rankingStr == null || rankingStr.trim().isEmpty() || "21926".equals(rankingStr)) {
            return null; // Non classé
        }

        try {
            return Integer.parseInt(rankingStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse les coordonnées GPS "30°1'55.75"N, 31°4'31.13"E"
     */
    public String cleanGpsCoordinates(String coordinates) {
        if (coordinates == null || coordinates.trim().isEmpty()) {
            return null;
        }
        return coordinates.trim();
    }

    /**
     * Parse les périodes historiques "530,3199"
     */
    public String cleanEvidenceRange(String evidenceRange) {
        if (evidenceRange == null || evidenceRange.trim().isEmpty()) {
            return null;
        }
        return evidenceRange.trim();
    }
}