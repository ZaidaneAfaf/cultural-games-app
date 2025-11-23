package com.boardgames.rag.service.csv;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.entity.BggMetadata;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.CsvDataCleaner;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service
public class BggCsvImportService {

    private final GameRepository gameRepository;
    private final CsvDataCleaner dataCleaner;

    @Value("${app.data.csv.bgg-path}")
    private String bggCsvPath;

    public BggCsvImportService(GameRepository gameRepository, CsvDataCleaner dataCleaner) {
        this.gameRepository = gameRepository;
        this.dataCleaner = dataCleaner;
    }

    public void importBggGames() {
        System.out.println("=== IMPORT BGG GAMES ===");

        try (CSVReader reader = new CSVReader(new FileReader(bggCsvPath))) {
            List<String[]> rows = reader.readAll();

            if (rows.isEmpty()) {
                System.out.println("Fichier BGG vide");
                return;
            }

            // Header row
            String[] headers = rows.get(0);
            System.out.println("Colonnes BGG: " + Arrays.toString(headers));

            // Data rows
            int importedCount = 0;
            int errorCount = 0;

            for (int i = 1; i < rows.size() && i <= 1000; i++) { // Limite pour test
                try {
                    String[] row = rows.get(i);
                    Game game = parseBggRow(headers, row);

                    if (game != null) {
                        gameRepository.save(game);
                        importedCount++;

                        if (importedCount % 100 == 0) {
                            System.out.println("✅ " + importedCount + " jeux BGG importés...");
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Erreur ligne " + i + ": " + e.getMessage());
                }
            }

            System.out.println("=== IMPORT BGG TERMINÉ ===");
            System.out.println("✅ " + importedCount + " jeux importés");
            System.out.println("❌ " + errorCount + " erreurs");

        } catch (IOException | CsvException e) {
            System.err.println("❌ Erreur lecture fichier BGG: " + e.getMessage());
        }
    }

    private Game parseBggRow(String[] headers, String[] row) {
        if (row.length < 5) return null; // Ligne trop courte

        Game game = new Game();
        game.setSource(Game.GameSource.BGG);

        // Mapping des colonnes principales
        Map<String, String> rowMap = createRowMap(headers, row);

        // Champs de base
        game.setName(getValue(rowMap, "Name"));
        game.setDescription(dataCleaner.cleanDescription(getValue(rowMap, "Description")));
        game.setYearPublished(parseInt(getValue(rowMap, "YearPublished")));

        // IDs
        game.setSourceId(getValue(rowMap, "BGGId"));
        game.setBggId(getValue(rowMap, "BGGId"));

        // Joueurs
        game.setMinPlayers(parseInt(getValue(rowMap, "MinPlayers")));
        game.setMaxPlayers(parseInt(getValue(rowMap, "MaxPlayers")));
        game.setBestPlayers(dataCleaner.parseBestPlayers(getValue(rowMap, "BestPlayers")));
        game.setRecommendedAge(getValue(rowMap, "ComAgeRec"));

        // Durée
        game.setMinPlaytime(parseInt(getValue(rowMap, "ComMinPlaytime")));
        game.setMaxPlaytime(parseInt(getValue(rowMap, "ComMaxPlaytime")));
        game.setEstimatedPlaytime(parseInt(getValue(rowMap, "MfgPlaytime")));

        // Ratings et complexité
        game.setAverageRating(parseDouble(getValue(rowMap, "AvgRating")));
        game.setBayesAverageRating(parseDouble(getValue(rowMap, "BayesAvgRating")));
        game.setComplexityWeight(parseDouble(getValue(rowMap, "GameWeight")));
        game.setNumRatings(parseInt(getValue(rowMap, "NumUserRatings")));
        game.setNumOwned(parseInt(getValue(rowMap, "NumOwned")));
        game.setNumWishlist(parseInt(getValue(rowMap, "NumWish")));

        // Catégories (à parser depuis les colonnes booléennes)
        game.setCategories(extractBggCategories(rowMap));

        // Métadonnées BGG spécifiques
        BggMetadata bggMetadata = new BggMetadata();
        bggMetadata.setBggId(parseInt(getValue(rowMap, "BGGId")));
        bggMetadata.setStdDevRating(parseDouble(getValue(rowMap, "StdDev")));
        bggMetadata.setNumWant(parseInt(getValue(rowMap, "NumWant")));
        bggMetadata.setNumWish(parseInt(getValue(rowMap, "NumWish")));
        bggMetadata.setNumUserRatings(parseInt(getValue(rowMap, "NumUserRatings")));

        // Flags booléens
        bggMetadata.setCatStrategy(parseBoolean(getValue(rowMap, "Cat:Strategy")));
        bggMetadata.setCatThematic(parseBoolean(getValue(rowMap, "Cat:Thematic")));
        bggMetadata.setCatWar(parseBoolean(getValue(rowMap, "Cat:War")));
        bggMetadata.setCatFamily(parseBoolean(getValue(rowMap, "Cat:Family")));

        bggMetadata.setKickstarted(parseBoolean(getValue(rowMap, "Kickstarted")));
        bggMetadata.setIsReimplementation(parseBoolean(getValue(rowMap, "IsReimplementation")));

        game.setBggMetadata(bggMetadata);

        return game;
    }

    private Map<String, String> createRowMap(String[] headers, String[] row) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < headers.length && i < row.length; i++) {
            map.put(headers[i], row[i]);
        }
        return map;
    }

    private String getValue(Map<String, String> rowMap, String key) {
        return rowMap.getOrDefault(key, "");
    }

    private Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    private List<String> extractBggCategories(Map<String, String> rowMap) {
        List<String> categories = new ArrayList<>();

        // Ajouter les catégories basées sur les flags booléens
        if (parseBoolean(getValue(rowMap, "Cat:Strategy"))) categories.add("Strategy");
        if (parseBoolean(getValue(rowMap, "Cat:Thematic"))) categories.add("Thematic");
        if (parseBoolean(getValue(rowMap, "Cat:War"))) categories.add("War");
        if (parseBoolean(getValue(rowMap, "Cat:Family"))) categories.add("Family");
        if (parseBoolean(getValue(rowMap, "Cat:Abstract"))) categories.add("Abstract");
        if (parseBoolean(getValue(rowMap, "Cat:Party"))) categories.add("Party");

        return categories;
    }
}