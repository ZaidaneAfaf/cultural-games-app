package com.example.projets5.service;

import com.example.projets5.model.*;
import com.example.projets5.repository.GameRepository;
import com.example.projets5.util.CSVUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final GameRepository gameRepository;

    private Reader openReader(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("CSV path is null or blank");
        }

        if (path.startsWith("classpath:")) {
            String cp = path.substring("classpath:".length()); // ex: "data/games.csv"
            ClassPathResource res = new ClassPathResource(cp);
            if (!res.exists()) {
                throw new FileNotFoundException("Classpath resource not found: " + cp);
            }
            return new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
        }

        // Chemin fichier classique
        return Files.newBufferedReader(Path.of(path));
    }

    // ------------------ BGG ------------------
    public void importBGG(String path) throws Exception {
        try (Reader rd = openReader(path);
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(rd)) {

            java.util.List<BoardGame> batch = new java.util.ArrayList<>();

            for (CSVRecord r : p) {
                BoardGame g = BoardGame.builder()
                        .source("BGG")
                        .bggId(r.get("BGGId"))
                        .name(r.get("Name"))
                        .yearPublished(CSVUtils.toInt(r.get("YearPublished")))
                        .description(CSVUtils.cleanDescription(r.get("Description")))
                        .bgg(BggMetadata.builder()
                                .avgRating(CSVUtils.toDouble(r.get("AvgRating")))
                                .bayesAvgRating(CSVUtils.toDouble(r.get("BayesAvgRating")))
                                .stdDev(CSVUtils.toDouble(r.get("StdDev")))
                                .gameWeight(CSVUtils.toDouble(r.get("GameWeight")))
                                .minPlayers(CSVUtils.toInt(r.get("MinPlayers")))
                                .maxPlayers(CSVUtils.toInt(r.get("MaxPlayers")))
                                .communityAgeRec(CSVUtils.toInt(r.get("ComAgeRec")))
                                .languageEase(CSVUtils.toInt(r.get("LanguageEase")))
                                .bestPlayers(CSVUtils.parseIntListFromStringList(r.get("BestPlayers")).stream().findFirst().orElse(null))
                                .goodPlayers(CSVUtils.parseIntListFromStringList(r.get("GoodPlayers")).stream().findFirst().orElse(null))
                                .mfgPlaytime(CSVUtils.toInt(r.get("MfgPlaytime")))
                                .comMinPlaytime(CSVUtils.toInt(r.get("ComMinPlaytime")))
                                .comMaxPlaytime(CSVUtils.toInt(r.get("ComMaxPlaytime")))
                                .numOwned(CSVUtils.toInt(r.get("NumOwned")))
                                .numWant(CSVUtils.toInt(r.get("NumWant")))
                                .numWish(CSVUtils.toInt(r.get("NumWish")))
                                .numUserRatings(CSVUtils.toInt(r.get("NumUserRatings")))
                                .rankBoardgame(CSVUtils.normalizeRanking(CSVUtils.toInt(safe(r, "Rank:boardgame"))))
                                .rankStrategyGames(CSVUtils.normalizeRanking(CSVUtils.toInt(safe(r, "Rank:strategygames"))))
                                .kickstarted(CSVUtils.toBool(safe(r, "Kickstarted")))
                                .isReimplementation(CSVUtils.toBool(safe(r, "IsReimplementation")))
                                .build())
                        .build();

                // catégories Cat:xxx
                java.util.List<String> cats = new java.util.ArrayList<>();
                r.toMap().forEach((k, v) -> {
                    if (k != null && k.startsWith("Cat:")
                            && ("1".equals(v) || "true".equalsIgnoreCase(v))) {
                        cats.add(k.substring(4));
                    }
                });
                g.setCategories(cats);

                batch.add(g);
                if (batch.size() >= 1000) {
                    gameRepository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                gameRepository.saveAll(batch);
            }
        }
    }

    // ------------------ Ludii Games ------------------
    public void importLudiiGames(String path) throws Exception {
        try (Reader rd = openReader(path);
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(rd)) {

            java.util.List<BoardGame> batch = new java.util.ArrayList<>();

            for (CSVRecord r : p) {
                LudiiMetadata lm = LudiiMetadata.builder()
                        .nativeName(safe(r, "NativeName"))
                        .origin(safe(r, "Origin"))
                        .originPoint(CSVUtils.parseDmsLatLon(safe(r, "OriginPoint")))
                        .evidenceRange(CSVUtils.parseYearRange(safe(r, "EvidenceRange")))
                        .dlpGame(CSVUtils.toBool(safe(r, "DLPGame")))
                        .publicGame(CSVUtils.toBool(safe(r, "PublicGame")))
                        .knownAliases(CSVUtils.parseCsvList(safe(r, "knownAliases")))
                        .author(safe(r, "Author"))
                        .publisher(safe(r, "Publisher"))
                        .date(safe(r, "Date"))
                        .proprietaryGame(CSVUtils.toBool(safe(r, "ProprietaryGame")))
                        .wishlistGame(CSVUtils.toBool(safe(r, "WishlistGame")))
                        .helpUs(CSVUtils.toBool(safe(r, "HelpUs")))
                        .disableWebApp(CSVUtils.toBool(safe(r, "DisableWebApp")))
                        .build();

                BoardGame g = BoardGame.builder()
                        .source("LUDII")
                        .ludiiId(r.get("Id"))
                        .name(r.get("Name"))
                        .description(CSVUtils.cleanDescription(safe(r, "Description")))
                        .bggId(safe(r, "BGGId"))
                        .ludii(lm)
                        .build();

                batch.add(g);
                if (batch.size() >= 1000) {
                    gameRepository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                gameRepository.saveAll(batch);
            }
        }
    }

    // ------------------ Ludii Rulesets ------------------
    public void importLudiiRulesets(String path) throws Exception {
        java.util.Map<String, BoardGame> byLudiiId = gameRepository.findBySource("LUDII")
                .stream()
                .collect(java.util.stream.Collectors.toMap(BoardGame::getLudiiId, g -> g));

        try (Reader rd = openReader(path);
             CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(rd)) {

            for (CSVRecord r : p) {
                String gameId = r.get("GameId");
                BoardGame g = byLudiiId.get(gameId);
                if (g == null) continue;

                Ruleset rs = Ruleset.builder()
                        .rulesetId(safe(r, "Id"))
                        .name(safe(r, "Name"))
                        .nativeName(safe(r, "NativeName"))
                        .summary(safe(r, "Summary"))
                        .type(CSVUtils.toInt(safe(r, "Type")))
                        .description(CSVUtils.cleanDescription(safe(r, "Description")))
                        .rules(safe(r, "Rules"))
                        .reference(safe(r, "Reference"))
                        .origin(safe(r, "Origin"))
                        .author(safe(r, "Author"))
                        .publisher(safe(r, "Publisher"))
                        .date(safe(r, "Date"))
                        .originPoint(CSVUtils.parseDmsLatLon(safe(r, "OriginPoint")))
                        .evidenceRange(CSVUtils.parseYearRange(safe(r, "EvidenceRange")))
                        .selfContained(CSVUtils.toBool(safe(r, "SelfContained")))
                        .notes(safe(r, "Notes"))
                        .disableWebApp(CSVUtils.toBool(safe(r, "DisableWebApp")))
                        .wishlistRuleset(CSVUtils.toBool(safe(r, "WishlistRuleset")))
                        .build();

                if (g.getRulesets() == null) {
                    g.setRulesets(new java.util.ArrayList<>());
                }
                g.getRulesets().add(rs);
            }
        }

        gameRepository.saveAll(byLudiiId.values());
    }

    // ------------------ Utils ------------------
    private static String safe(CSVRecord r, String col) {
        return r.isMapped(col) ? r.get(col) : null;
    }
}
