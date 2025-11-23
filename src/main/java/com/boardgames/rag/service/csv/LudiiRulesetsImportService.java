package com.boardgames.rag.service.csv;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.entity.Ruleset;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.CsvDataCleaner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service
public class LudiiRulesetsImportService {

    private final GameRepository gameRepository;
    private final CsvDataCleaner dataCleaner;

    @Value("${app.data.csv.ludii-rulesets-path}")
    private String ludiiRulesetsCsvPath;

    public LudiiRulesetsImportService(GameRepository gameRepository, CsvDataCleaner dataCleaner) {
        this.gameRepository = gameRepository;
        this.dataCleaner = dataCleaner;
    }

    public void importLudiiRulesets() {
        System.out.println("=== IMPORT LUDII RULESETS ===");

        try (BufferedReader reader = new BufferedReader(new FileReader(ludiiRulesetsCsvPath))) {
            String line;
            List<String> headers = new ArrayList<>();
            int lineNumber = 0;
            int importedCount = 0;
            int errorCount = 0;
            int gamesUpdated = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                try {
                    if (lineNumber == 1) {
                        // Header row
                        headers = parseCsvLine(line);
                        System.out.println("Colonnes Ludii Rulesets: " + headers);
                        continue;
                    }

                    // Data row
                    List<String> row = parseCsvLine(line);
                    Ruleset ruleset = parseRulesetRow(headers, row, lineNumber);

                    if (ruleset != null) {
                        // Trouver le jeu correspondant et ajouter le ruleset
                        boolean updated = addRulesetToGame(ruleset);
                        if (updated) {
                            importedCount++;
                            gamesUpdated++;
                        }

                        if (importedCount % 25 == 0) {
                            System.out.println("📚 " + importedCount + " rulesets importés...");
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Erreur ligne " + lineNumber + ": " + e.getMessage());
                }
            }

            System.out.println("=== IMPORT RULESETS TERMINÉ ===");
            System.out.println("📚 " + importedCount + " rulesets importés");
            System.out.println("🎲 " + gamesUpdated + " jeux mis à jour avec rulesets");
            System.out.println("❌ " + errorCount + " erreurs");

        } catch (IOException e) {
            System.err.println("❌ Erreur lecture fichier Rulesets: " + e.getMessage());
            System.out.println("💡 Vérifiez que le fichier existe: " + ludiiRulesetsCsvPath);
        }
    }

    private Ruleset parseRulesetRow(List<String> headers, List<String> row, int lineNumber) {
        if (row.size() < 5) {
            System.err.println("Ligne " + lineNumber + " trop courte: " + row.size() + " colonnes");
            return null;
        }

        try {
            Ruleset ruleset = new Ruleset();

            Map<String, String> rowMap = createRowMap(headers, row);

            // Champs de base
            ruleset.setId(getValue(rowMap, "Id"));
            ruleset.setGameId(getValue(rowMap, "GameId"));
            ruleset.setName(getValue(rowMap, "Name"));
            ruleset.setNativeName(getValue(rowMap, "NativeName"));
            ruleset.setSummary(getValue(rowMap, "Summary"));
            ruleset.setDescription(dataCleaner.cleanDescription(getValue(rowMap, "Description")));
            ruleset.setRules(dataCleaner.cleanDescription(getValue(rowMap, "Rules")));

            // Métadonnées
            ruleset.setType(parseInt(getValue(rowMap, "Type")));
            ruleset.setReference(getValue(rowMap, "Reference"));
            ruleset.setOrigin(getValue(rowMap, "Origin"));
            ruleset.setAuthor(getValue(rowMap, "Author"));
            ruleset.setPublisher(getValue(rowMap, "Publisher"));
            ruleset.setDate(getValue(rowMap, "Date"));
            ruleset.setOriginPoint(dataCleaner.cleanGpsCoordinates(getValue(rowMap, "OriginPoint")));
            ruleset.setEvidenceRange(dataCleaner.cleanEvidenceRange(getValue(rowMap, "EvidenceRange")));

            // Flags
            ruleset.setSelfContained(parseBoolean(getValue(rowMap, "SelfContained")));
            ruleset.setWishlistRuleset(parseBoolean(getValue(rowMap, "WishlistRuleset")));
            ruleset.setDisableWebApp(parseBoolean(getValue(rowMap, "DisableWebApp")));

            return ruleset;
        } catch (Exception e) {
            System.err.println("Erreur parsing ligne " + lineNumber + ": " + e.getMessage());
            return null;
        }
    }

    private boolean addRulesetToGame(Ruleset ruleset) {
        // Chercher le jeu par GameId
        Optional<Game> gameOpt = gameRepository.findBySourceIdAndSource(ruleset.getGameId(), Game.GameSource.LUDII);

        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();

            // Initialiser la liste des rulesets si nécessaire
            if (game.getRulesets() == null) {
                game.setRulesets(new ArrayList<>());
            }

            // Ajouter le ruleset
            game.getRulesets().add(ruleset);
            gameRepository.save(game);
            return true;
        } else {
            System.err.println("Jeu non trouvé pour ruleset: GameId=" + ruleset.getGameId());
            return false;
        }
    }

    // Méthodes utilitaires (identique à LudiiGamesImportService)
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

        result.add(currentField.toString().trim());
        return result;
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