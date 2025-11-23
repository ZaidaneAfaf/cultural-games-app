package com.boardgames.rag.service.csv;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.entity.LudiiMetadata;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.CsvDataCleaner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service
public class LudiiGamesImportService {

    private final GameRepository gameRepository;
    private final CsvDataCleaner dataCleaner;

    @Value("${app.data.csv.ludii-games-path}")
    private String ludiiGamesCsvPath;

    public LudiiGamesImportService(GameRepository gameRepository, CsvDataCleaner dataCleaner) {
        this.gameRepository = gameRepository;
        this.dataCleaner = dataCleaner;
    }

    public void importLudiiGames() {
        System.out.println("=== IMPORT LUDII GAMES ===");

        try (BufferedReader reader = new BufferedReader(new FileReader(ludiiGamesCsvPath))) {
            String line;
            List<String> headers = new ArrayList<>();
            int lineNumber = 0;
            int importedCount = 0;
            int errorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                try {
                    if (lineNumber == 1) {
                        // Header row
                        headers = parseCsvLine(line);
                        System.out.println("Colonnes Ludii Games: " + headers);
                        continue;
                    }

                    // Data row
                    List<String> row = parseCsvLine(line);
                    Game game = parseLudiiRow(headers, row, lineNumber);

                    if (game != null) {
                        gameRepository.save(game);
                        importedCount++;

                        if (importedCount % 50 == 0) {
                            System.out.println("🏺 " + importedCount + " jeux Ludii importés...");
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Erreur ligne " + lineNumber + ": " + e.getMessage());
                    // Continue avec les lignes suivantes
                }
            }

            System.out.println("=== IMPORT LUDII TERMINÉ ===");
            System.out.println("🏺 " + importedCount + " jeux historiques importés");
            System.out.println("❌ " + errorCount + " erreurs");

        } catch (IOException e) {
            System.err.println("❌ Erreur lecture fichier Ludii: " + e.getMessage());
            System.out.println("💡 Vérifiez que le fichier existe: " + ludiiGamesCsvPath);
        }
    }

    /**
     * Parse une ligne CSV manuellement pour gérer les formats problématiques
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        // Ajouter le dernier champ
        result.add(currentField.toString().trim());

        return result;
    }

    private Game parseLudiiRow(List<String> headers, List<String> row, int lineNumber) {
        if (row.size() < 5) {
            System.err.println("Ligne " + lineNumber + " trop courte: " + row.size() + " colonnes");
            return null;
        }

        try {
            Game game = new Game();
            game.setSource(Game.GameSource.LUDII);

            Map<String, String> rowMap = createRowMap(headers, row);

            // Champs de base
            game.setName(getValue(rowMap, "Name"));
            game.setDescription(dataCleaner.cleanDescription(getValue(rowMap, "Description")));
            game.setSourceId(getValue(rowMap, "Id"));
            game.setBggId(getValue(rowMap, "BGGId")); // Lien vers BGG si existe

            // Métadonnées Ludii spécifiques
            LudiiMetadata ludiiMetadata = new LudiiMetadata();
            ludiiMetadata.setLudiiId(parseInt(getValue(rowMap, "Id")));
            ludiiMetadata.setNativeName(getValue(rowMap, "NativeName"));
            ludiiMetadata.setOrigin(getValue(rowMap, "Origin"));
            ludiiMetadata.setOriginPoint(dataCleaner.cleanGpsCoordinates(getValue(rowMap, "OriginPoint")));
            ludiiMetadata.setEvidenceRange(dataCleaner.cleanEvidenceRange(getValue(rowMap, "EvidenceRange")));
            ludiiMetadata.setMainRuleset(getValue(rowMap, "MainRuleset"));
            ludiiMetadata.setLudiiRuleset(getValue(rowMap, "LudiiRuleset"));
            ludiiMetadata.setReference(getValue(rowMap, "Reference"));
            ludiiMetadata.setDlpGame(parseBoolean(getValue(rowMap, "DLPGame")));
            ludiiMetadata.setPublicGame(parseBoolean(getValue(rowMap, "PublicGame")));
            ludiiMetadata.setAuthor(getValue(rowMap, "Author"));
            ludiiMetadata.setPublisher(getValue(rowMap, "Publisher"));
            ludiiMetadata.setDateAdded(getValue(rowMap, "Date"));
            ludiiMetadata.setKnownAliases(dataCleaner.parseStringList(getValue(rowMap, "knownAliases")));
            ludiiMetadata.setProprietaryGame(parseBoolean(getValue(rowMap, "ProprietaryGame")));
            ludiiMetadata.setWishlistGame(parseBoolean(getValue(rowMap, "WishlistGame")));
            ludiiMetadata.setHelpUs(parseBoolean(getValue(rowMap, "HelpUs")));
            ludiiMetadata.setDisableWebApp(parseBoolean(getValue(rowMap, "DisableWebApp")));

            game.setLudiiMetadata(ludiiMetadata);

            return game;
        } catch (Exception e) {
            System.err.println("Erreur parsing ligne " + lineNumber + ": " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> createRowMap(List<String> headers, List<String> row) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < headers.size() && i < row.size(); i++) {
            map.put(headers.get(i), row.get(i));
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

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }
}