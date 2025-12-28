package com.boardgames.rag.service.embedding;

import com.boardgames.rag.entity.Game;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.intent.IntentDetectionService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final GameRepository gameRepository;

    // Constante pour limiter la longueur totale du contenu
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_DESCRIPTION_LENGTH = 800;

    public VectorStoreService(VectorStore vectorStore,
                              EmbeddingModel embeddingModel,
                              GameRepository gameRepository) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.gameRepository = gameRepository;
    }

    /**
     * 🔥 RECHERCHE HYBRIDE INTELLIGENTE (VERSION CORRIGÉE)
     */
    public List<Document> searchGames(String query, int maxResults, IntentDetectionService.IntentType intentType) {
        System.out.println("🔍 [HYBRID] Recherche: \"" + query + "\"");
        System.out.println("🎯 [INTENT] Type reçu: " + intentType);

        // ÉTAPE 0 : Analyse de la requête avec intention
        QueryType queryType = analyzeQueryTypeWithIntent(query, intentType);
        System.out.println("📊 [HYBRID] Type final: " + queryType);

        // COUCHE 1 : Recherche directe en BDD (confiance maximale)
        List<Document> directResults = searchDirectInDatabase(query, queryType);

        if (!directResults.isEmpty()) {
            System.out.println("✅ [COUCHE 1] " + directResults.size() + " jeu(x) trouvé(s) (origine documentée)");
            return directResults.stream().limit(maxResults).collect(Collectors.toList());
        }

        // COUCHE 2 : Inférence PURE
        List<Document> inferredResults = searchByInference(query, queryType);

        if (!inferredResults.isEmpty()) {
            System.out.println("✅ [COUCHE 2] " + inferredResults.size() + " jeu(x) trouvé(s) (inférence)");
            return inferredResults.stream().limit(maxResults).collect(Collectors.toList());
        }

        // COUCHE 3 : Recherche vectorielle intelligente
        System.out.println("🔍 [COUCHE 3] Recherche vectorielle contextuelle");
        return searchVectorStoreIntelligently(query, queryType, maxResults);
    }

    /**
     * 🔥 SURCHARGE POUR COMPATIBILITÉ
     */
    public List<Document> searchGames(String query, int maxResults) {
        System.out.println("⚠️ [HYBRID] Appel sans intentType → utilisant GAME_SEARCH par défaut");
        return searchGames(query, maxResults, IntentDetectionService.IntentType.GAME_SEARCH);
    }

    /**
     * 🔥 ANALYSE DU TYPE DE REQUÊTE AVEC INTENTION
     */
    private QueryType analyzeQueryTypeWithIntent(String query, IntentDetectionService.IntentType intentType) {
        String lowerQuery = query.toLowerCase().trim();

        // PRIORITÉ 1 : Si intent est ARCHAEOLOGICAL, forcer archéologique
        if (intentType == IntentDetectionService.IntentType.ARCHAEOLOGICAL_IDENTIFICATION) {
            System.out.println("🏛️ [ANALYSE] Intent ARCHAEOLOGICAL → forçage type archéologique");
            return QueryType.ARCHAEOLOGICAL;
        }

        // PRIORITÉ 2 : Si intent est THEORETICAL mais contient termes archéo
        if (intentType == IntentDetectionService.IntentType.THEORETICAL_QUESTION) {
            if (containsArchaeologicalTerms(lowerQuery)) {
                System.out.println("🏛️ [ANALYSE] Question théorique avec termes archéo → priorité archéologique");
                return QueryType.ARCHAEOLOGICAL;
            }
        }

        // Sinon, analyse normale
        return analyzeQueryTypeNormal(lowerQuery);
    }

    /**
     * 🔥 VÉRIFICATION DES TERMES ARCHÉOLOGIQUES
     */
    private boolean containsArchaeologicalTerms(String query) {
        String[] archaeologicalTerms = {
                "romain", "rome", "égypt", "egypt", "grec", "grèce", "greece",
                "viking", "scandinave", "antique", "ancien", "archéolog",
                "mésopotam", "sumer", "babylon", "pharaon", "néolithique",
                "pompéi", "herculanum", "byzantin", "médiéval", "moyen âge",
                "latrunculorum", "tesserae", "petteia", "hnefatafl"
        };

        for (String term : archaeologicalTerms) {
            if (query.contains(term)) {
                System.out.println("🔍 [ANALYSE] Terme archéo détecté: " + term);
                return true;
            }
        }
        return false;
    }

    /**
     * 🔥 ANALYSE NORMALE DU TYPE DE REQUÊTE
     */
    private QueryType analyzeQueryTypeNormal(String lowerQuery) {
        if (lowerQuery.contains("règles") || lowerQuery.contains("comment jouer") ||
                lowerQuery.contains("comment se joue")) {
            return QueryType.SPECIFIC_GAME_RULES;
        }

        if (lowerQuery.matches(".*meilleurs?.*jeux.*") ||
                lowerQuery.matches(".*top.*jeux.*") ||
                lowerQuery.matches(".*recommand.*jeux.*")) {
            return QueryType.GENERAL_RECOMMENDATION;
        }

        if (lowerQuery.matches(".*jeux.*pour.*joueurs.*") ||
                lowerQuery.matches(".*jeux.*de.*stratégie.*") ||
                lowerQuery.matches(".*jeux.*famil.*")) {
            return QueryType.THEMATIC_SEARCH;
        }

        if (lowerQuery.contains("antique") || lowerQuery.contains("ancien") ||
                lowerQuery.contains("égypt") || lowerQuery.contains("romain") ||
                lowerQuery.contains("grec") || lowerQuery.contains("viking") ||
                lowerQuery.contains("mésopotam")) {
            return QueryType.ARCHAEOLOGICAL;
        }

        return QueryType.GENERAL_SEARCH;
    }

    enum QueryType {
        SPECIFIC_GAME_RULES,
        GENERAL_RECOMMENDATION,
        THEMATIC_SEARCH,
        ARCHAEOLOGICAL,
        GENERAL_SEARCH
    }

    /**
     * 🔥 COUCHE 1 : RECHERCHE DIRECTE CONTEXTUELLE
     */
    private List<Document> searchDirectInDatabase(String query, QueryType queryType) {
        String lowerQuery = query.toLowerCase().trim();
        List<Game> foundGames = new ArrayList<>();

        System.out.println("🔍 [COUCHE 1] Recherche contextuelle (type: " + queryType + ")");

        // CAS 1 : Mots-clés EXACTS pour jeux connus
        Map<String, String> exactKeywords = new HashMap<>();
        exactKeywords.put("chess", "Chess");
        exactKeywords.put("échecs", "Chess");
        exactKeywords.put("échec", "Chess");
        exactKeywords.put("catan", "Catan");
        exactKeywords.put("monopoly", "Monopoly");
        exactKeywords.put("go", "Go");
        exactKeywords.put("scrabble", "Scrabble");
        exactKeywords.put("risk", "Risk");
        exactKeywords.put("senet", "Senet");
        exactKeywords.put("azul", "Azul");
        exactKeywords.put("pandemic", "Pandemic");
        exactKeywords.put("carcassonne", "Carcassonne");
        exactKeywords.put("mehen", "Mehen");
        exactKeywords.put("royal game of ur", "Royal Game of Ur");
        exactKeywords.put("petteia", "Petteia");
        exactKeywords.put("latrunculorum", "Ludus latrunculorum");
        exactKeywords.put("hnefatafl", "Hnefatafl");

        for (Map.Entry<String, String> entry : exactKeywords.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                List<Game> matches = gameRepository.findByNameContainingIgnoreCase(entry.getValue());
                if (!matches.isEmpty()) {
                    System.out.println("✅ [COUCHE 1] Trouvé par mot-clé: " + entry.getValue());
                    foundGames.addAll(matches);
                    break;
                }
            }
        }

        // CAS 2 : Recherche par CIVILISATION (avec filtrage intelligent)
        if (foundGames.isEmpty() && queryType == QueryType.ARCHAEOLOGICAL) {
            System.out.println("🏛️ [COUCHE 1] Mode archéologique activé");

            if (lowerQuery.contains("romain") || lowerQuery.contains("rome")) {
                System.out.println("🏛️ [COUCHE 1] Recherche jeux romains documentés");
                foundGames = gameRepository.findRomanGames().stream()
                        .filter(g -> g.getYearPublished() != null && g.getYearPublished() < 500)
                        .collect(Collectors.toList());
            }
            else if (lowerQuery.contains("égypt") || lowerQuery.contains("egypt")) {
                System.out.println("🏛️ [COUCHE 1] Recherche jeux égyptiens documentés");
                foundGames = gameRepository.findEgyptianGames().stream()
                        .filter(g -> g.getYearPublished() != null && g.getYearPublished() < 0)
                        .collect(Collectors.toList());
            }
            else if (lowerQuery.contains("grec") || lowerQuery.contains("grèce") || lowerQuery.contains("greece")) {
                System.out.println("🏛️ [COUCHE 1] Recherche jeux grecs documentés");
                foundGames = gameRepository.findGreekGames().stream()
                        .filter(g -> g.getYearPublished() != null && g.getYearPublished() < 400)
                        .collect(Collectors.toList());
            }
            else if (lowerQuery.contains("viking") || lowerQuery.contains("scandinave") || lowerQuery.contains("norse")) {
                System.out.println("🏛️ [COUCHE 1] Recherche jeux vikings documentés");
                foundGames = gameRepository.findVikingGames();
            }
            else if (lowerQuery.contains("mésopotam") || lowerQuery.contains("sumer") || lowerQuery.contains("babylon")) {
                System.out.println("🏛️ [COUCHE 1] Recherche jeux mésopotamiens documentés");
                foundGames = gameRepository.findMesopotamianGames().stream()
                        .filter(g -> g.getYearPublished() != null && g.getYearPublished() < 0)
                        .collect(Collectors.toList());
            }
            else {
                // Recherche archéologique générale
                System.out.println("🔍 [COUCHE 1] Recherche archéologique générale");
                foundGames = gameRepository.findAncientGames();
            }
        }

        if (!foundGames.isEmpty()) {
            System.out.println("📊 [COUCHE 1] " + foundGames.size() + " jeu(x) avec origine documentée");
        } else {
            System.out.println("📊 [COUCHE 1] Aucun jeu trouvé en recherche directe");
        }

        return foundGames.stream()
                .map(this::gameToDocumentEnhanced)
                .collect(Collectors.toList());
    }

    /**
     * 🔥 COUCHE 2 : INFÉRENCE INTELLIGENTE
     */
    private List<Document> searchByInference(String query, QueryType queryType) {
        String lowerQuery = query.toLowerCase().trim();
        List<Game> foundGames = new ArrayList<>();

        System.out.println("🔍 [COUCHE 2] Inférence contextuelle (type: " + queryType + ")");

        List<Game> allGames = gameRepository.findAll();

        // ARCHÉOLOGIQUE : Priorité absolue
        if (queryType == QueryType.ARCHAEOLOGICAL) {
            System.out.println("🏛️ [COUCHE 2] Mode archéologique - inférence étendue");

            foundGames = allGames.stream()
                    .filter(g -> {
                        // Filtrer par année
                        if (g.getYearPublished() != null && g.getYearPublished() < 1500) {
                            return true;
                        }

                        // Filtrer par nom contenant des termes antiques
                        String name = g.getName().toLowerCase();
                        if (name.contains("senet") || name.contains("mehen") ||
                                name.contains("ur") || name.contains("latrunculorum") ||
                                name.contains("hnefatafl") || name.contains("tafl") ||
                                name.contains("petteia") || name.contains("tesserae")) {
                            return true;
                        }

                        // Filtrer par description
                        if (g.getDescription() != null) {
                            String desc = g.getDescription().toLowerCase();
                            if (desc.contains("antique") || desc.contains("ancien") ||
                                    desc.contains("égypt") || desc.contains("romain") ||
                                    desc.contains("grec") || desc.contains("viking") ||
                                    desc.contains("mésopotam")) {
                                return true;
                            }
                        }

                        return false;
                    })
                    .distinct()
                    .sorted((g1, g2) -> {
                        // Priorité aux plus anciens
                        if (g1.getYearPublished() != null && g2.getYearPublished() != null) {
                            return Integer.compare(g1.getYearPublished(), g2.getYearPublished());
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            System.out.println("📊 [COUCHE 2] " + foundGames.size() + " jeux antiques inférés");
        }
        // QUESTIONS DE RECOMMANDATION
        else if (queryType == QueryType.GENERAL_RECOMMENDATION) {
            System.out.println("🏆 [COUCHE 2] Mode recommandation");

            if (lowerQuery.contains("complexe") || lowerQuery.contains("complexité") || lowerQuery.contains("compliqué")) {
                foundGames = allGames.stream()
                        .filter(g -> g.getComplexityWeight() != null && g.getComplexityWeight() >= 3.5)
                        .sorted((g1, g2) -> {
                            if (g1.getAverageRating() != null && g2.getAverageRating() != null) {
                                return Double.compare(g2.getAverageRating(), g1.getAverageRating());
                            }
                            return 0;
                        })
                        .limit(15)
                        .collect(Collectors.toList());
                System.out.println("🧠 [COUCHE 2] " + foundGames.size() + " jeux complexes trouvés");
            }
            else if (lowerQuery.contains("simple") || lowerQuery.contains("facile") || lowerQuery.contains("famil")) {
                foundGames = allGames.stream()
                        .filter(g -> {
                            boolean lowComplexity = g.getComplexityWeight() != null && g.getComplexityWeight() <= 2.5;
                            boolean isFamily = g.getCategories() != null &&
                                    g.getCategories().stream().anyMatch(c ->
                                            c.toLowerCase().contains("family") ||
                                                    c.toLowerCase().contains("children"));
                            return lowComplexity || isFamily;
                        })
                        .sorted((g1, g2) -> {
                            if (g1.getAverageRating() != null && g2.getAverageRating() != null) {
                                return Double.compare(g2.getAverageRating(), g1.getAverageRating());
                            }
                            return 0;
                        })
                        .limit(15)
                        .collect(Collectors.toList());
                System.out.println("👪 [COUCHE 2] " + foundGames.size() + " jeux simples/familiaux trouvés");
            }
            else if (lowerQuery.contains("stratégie")) {
                foundGames = allGames.stream()
                        .filter(g -> g.getCategories() != null &&
                                g.getCategories().stream().anyMatch(c ->
                                        c.toLowerCase().contains("strategy") ||
                                                c.toLowerCase().contains("wargame")))
                        .sorted((g1, g2) -> {
                            if (g1.getAverageRating() != null && g2.getAverageRating() != null) {
                                return Double.compare(g2.getAverageRating(), g1.getAverageRating());
                            }
                            return 0;
                        })
                        .limit(15)
                        .collect(Collectors.toList());
                System.out.println("♟️ [COUCHE 2] " + foundGames.size() + " jeux de stratégie trouvés");
            }
            else {
                foundGames = allGames.stream()
                        .filter(g -> g.getAverageRating() != null && g.getAverageRating() >= 7.5)
                        .sorted((g1, g2) -> Double.compare(g2.getAverageRating(), g1.getAverageRating()))
                        .limit(15)
                        .collect(Collectors.toList());
                System.out.println("⭐ [COUCHE 2] " + foundGames.size() + " jeux bien notés trouvés");
            }

            foundGames = filterByPlayerCount(foundGames, lowerQuery);
        }
        // RECHERCHE THÉMATIQUE
        else if (queryType == QueryType.THEMATIC_SEARCH) {
            System.out.println("🎯 [COUCHE 2] Mode recherche thématique");

            foundGames = filterByPlayerCount(allGames, lowerQuery);

            if (lowerQuery.contains("stratégie")) {
                foundGames = foundGames.stream()
                        .filter(g -> g.getCategories() != null &&
                                g.getCategories().stream().anyMatch(c ->
                                        c.toLowerCase().contains("strategy") ||
                                                c.toLowerCase().contains("wargame")))
                        .collect(Collectors.toList());
            }

            if (lowerQuery.contains("coopératif") || lowerQuery.contains("cooperative")) {
                foundGames = foundGames.stream()
                        .filter(g -> g.getCategories() != null &&
                                g.getCategories().stream().anyMatch(c ->
                                        c.toLowerCase().contains("cooperative")))
                        .collect(Collectors.toList());
            }

            if (lowerQuery.contains("famil")) {
                foundGames = foundGames.stream()
                        .filter(g -> g.getCategories() != null &&
                                g.getCategories().stream().anyMatch(c ->
                                        c.toLowerCase().contains("family") ||
                                                c.toLowerCase().contains("children")))
                        .collect(Collectors.toList());
            }

            foundGames = foundGames.stream()
                    .sorted((g1, g2) -> {
                        if (g1.getAverageRating() == null) return 1;
                        if (g2.getAverageRating() == null) return -1;
                        return Double.compare(g2.getAverageRating(), g1.getAverageRating());
                    })
                    .limit(15)
                    .collect(Collectors.toList());
        }
        // RECHERCHE GÉNÉRALE
        else if (queryType == QueryType.GENERAL_SEARCH) {
            System.out.println("🔍 [COUCHE 2] Mode recherche générale");

            String[] keywords = lowerQuery.split("\\s+");
            for (String keyword : keywords) {
                if (keyword.length() > 3) {
                    List<Game> matches = gameRepository.findByNameContainingIgnoreCase(keyword);
                    foundGames.addAll(matches);
                }
            }

            foundGames = foundGames.stream()
                    .distinct()
                    .sorted((g1, g2) -> {
                        if (g1.getAverageRating() == null) return 1;
                        if (g2.getAverageRating() == null) return -1;
                        return Double.compare(g2.getAverageRating(), g1.getAverageRating());
                    })
                    .limit(15)
                    .collect(Collectors.toList());
        }

        if (!foundGames.isEmpty()) {
            System.out.println("📊 [COUCHE 2] " + foundGames.size() + " jeu(x) inféré(s)");
        } else {
            System.out.println("📊 [COUCHE 2] Aucun jeu inféré");
        }

        return foundGames.stream()
                .map(this::gameToDocumentEnhanced)
                .collect(Collectors.toList());
    }

    /**
     * 🔥 FILTRAGE PAR NOMBRE DE JOUEURS
     */
    private List<Game> filterByPlayerCount(List<Game> games, String query) {
        if (query.contains("4 joueurs") || query.contains("4 personnes")) {
            return games.stream()
                    .filter(g -> g.getMinPlayers() != null && g.getMinPlayers() <= 4 &&
                            g.getMaxPlayers() != null && g.getMaxPlayers() >= 4)
                    .collect(Collectors.toList());
        }
        if (query.contains("2 joueurs") || query.contains("2 personnes")) {
            return games.stream()
                    .filter(g -> g.getMinPlayers() != null && g.getMinPlayers() <= 2 &&
                            g.getMaxPlayers() != null && g.getMaxPlayers() >= 2)
                    .collect(Collectors.toList());
        }
        if (query.contains("5 joueurs") || query.contains("5 personnes")) {
            return games.stream()
                    .filter(g -> g.getMinPlayers() != null && g.getMinPlayers() <= 5 &&
                            g.getMaxPlayers() != null && g.getMaxPlayers() >= 5)
                    .collect(Collectors.toList());
        }
        if (query.contains("6 joueurs") || query.contains("6 personnes")) {
            return games.stream()
                    .filter(g -> g.getMinPlayers() != null && g.getMinPlayers() <= 6 &&
                            g.getMaxPlayers() != null && g.getMaxPlayers() >= 6)
                    .collect(Collectors.toList());
        }
        return games;
    }

    /**
     * 🔥 COUCHE 3 : RECHERCHE VECTORIELLE INTELLIGENTE
     */
    private List<Document> searchVectorStoreIntelligently(String query, QueryType queryType, int maxResults) {
        try {
            SearchRequest searchRequest;

            if (queryType == QueryType.GENERAL_RECOMMENDATION) {
                searchRequest = SearchRequest.query(query)
                        .withTopK(15)
                        .withSimilarityThreshold(0.3);
            } else if (queryType == QueryType.ARCHAEOLOGICAL) {
                System.out.println("🏛️ [COUCHE 3] Recherche vectorielle archéologique");
                searchRequest = SearchRequest.query(query)
                        .withTopK(12)
                        .withSimilarityThreshold(0.35);
            } else {
                searchRequest = SearchRequest.query(query)
                        .withTopK(12)
                        .withSimilarityThreshold(0.35);
            }

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            if (!results.isEmpty()) {
                System.out.println("📊 [COUCHE 3] " + results.size() + " résultat(s) vectoriel(s)");
            } else {
                System.out.println("📊 [COUCHE 3] Aucun résultat vectoriel");
            }

            return results.stream().limit(maxResults).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("❌ [COUCHE 3] Erreur: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 🔥 CONVERSION JEU → DOCUMENT AMÉLIORÉE (VERSION CORRIGÉE)
     * Limite la longueur du contenu pour éviter l'erreur de contexte
     */
    private Document gameToDocumentEnhanced(Game game) {
        StringBuilder content = new StringBuilder();

        // ÉTAPE 1 : NOM ET ANNÉE
        content.append("JEU: ").append(game.getName());
        if (game.getYearPublished() != null) {
            content.append(" (").append(game.getYearPublished()).append(")");
        }
        content.append(". ");

        // ÉTAPE 2 : CIVILISATION (CRITIQUE POUR ANTIQUE)
        String civilization = extractCivilizationEnhanced(game);
        if (civilization != null) {
            content.append("ORIGINE: ").append(civilization).append(". ");
        }

        // ÉTAPE 3 : CARACTÉRISTIQUES CLÉS
        content.append("CARACTÉRISTIQUES: ");

        String gameType = extractGameTypeEnhanced(game);
        if (gameType != null) {
            content.append(gameType).append(", ");
        }

        if (game.getMinPlayers() != null && game.getMaxPlayers() != null) {
            content.append("pour ").append(game.getMinPlayers())
                    .append("-").append(game.getMaxPlayers()).append(" joueurs, ");
        }

        if (game.getComplexityWeight() != null) {
            content.append("complexité ").append(String.format("%.1f", game.getComplexityWeight())).append("/5, ");
        }

        if (game.getAverageRating() != null) {
            content.append("note ").append(String.format("%.1f", game.getAverageRating())).append("/10. ");
        }

        // ÉTAPE 4 : DESCRIPTION ENRICHIE ET LIMITÉE
        if (game.getDescription() != null) {
            String enhancedDesc = enhanceDescription(game.getDescription(), game);
            // Limiter la description pour éviter l'erreur de contexte
            String limitedDesc = truncateToLength(enhancedDesc, MAX_DESCRIPTION_LENGTH);
            content.append(limitedDesc).append(" ");
        }

        // ÉTAPE 5 : CATÉGORIES ET THÈMES (limité)
        if (game.getCategories() != null && !game.getCategories().isEmpty()) {
            List<String> limitedCategories = game.getCategories().stream()
                    .limit(5) // Limiter à 5 catégories max
                    .collect(Collectors.toList());
            content.append("THÈMES: ").append(String.join(", ", limitedCategories)).append(". ");
        }

        // Limiter la longueur totale du contenu
        String finalContent = content.toString();
        if (finalContent.length() > MAX_CONTENT_LENGTH) {
            finalContent = truncateToLength(finalContent, MAX_CONTENT_LENGTH);
            System.out.println("📏 [DOCUMENT] Contenu tronqué pour: " + game.getName() +
                    " (" + finalContent.length() + " caractères)");
        }

        // MÉTADONNÉES ENRICHIES
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gameId", game.getId());
        metadata.put("name", game.getName());
        metadata.put("source", game.getSource().toString());

        if (game.getYearPublished() != null) {
            metadata.put("year", game.getYearPublished());
        }
        if (game.getAverageRating() != null) {
            metadata.put("rating", game.getAverageRating());
        }
        if (game.getComplexityWeight() != null) {
            metadata.put("complexity", game.getComplexityWeight());
        }
        if (game.getMinPlayers() != null) {
            metadata.put("minPlayers", game.getMinPlayers());
        }
        if (game.getMaxPlayers() != null) {
            metadata.put("maxPlayers", game.getMaxPlayers());
        }

        if (civilization != null) {
            metadata.put("civilization", civilization);
        }
        if (gameType != null) {
            metadata.put("gameType", gameType);
        }

        return new Document(finalContent.trim(), metadata);
    }

    /**
     * 🔥 TRONCATURE INTELLIGENTE DU TEXTE
     */
    private String truncateToLength(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        // Chercher le dernier point avant la limite
        int lastPeriod = text.lastIndexOf('.', maxLength - 3);
        if (lastPeriod > maxLength * 0.5) {
            return text.substring(0, lastPeriod + 1) + "..";
        }

        // Sinon tronquer proprement
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 🔥 EXTRACTION CIVILISATION AMÉLIORÉE
     */
    private String extractCivilizationEnhanced(Game game) {
        String name = game.getName().toLowerCase();
        String desc = game.getDescription() != null ? game.getDescription().toLowerCase() : "";
        String origin = "";

        if (game.getLudiiMetadata() != null && game.getLudiiMetadata().getOrigin() != null) {
            origin = game.getLudiiMetadata().getOrigin().toLowerCase();
        }

        if (origin.contains("egypt") || origin.contains("égypt") ||
                name.contains("senet") || name.contains("mehen") ||
                desc.contains("pharaon") || desc.contains("nil") || desc.contains("pyramid")) {
            return "ÉGYPTE ANCIENNE";
        }

        if (origin.contains("mesopotamia") || origin.contains("sumer") ||
                origin.contains("babylon") || name.contains("ur") ||
                desc.contains("mésopotam") || desc.contains("sumérien")) {
            return "MÉSOPOTAMIE";
        }

        if (origin.contains("rome") || origin.contains("roman") ||
                name.contains("latrunculorum") || name.contains("duodecim") ||
                desc.contains("romain") || desc.contains("pompéi") || desc.contains("caesar")) {
            return "ROME ANTIQUE";
        }

        if (origin.contains("greece") || origin.contains("grec") ||
                name.contains("petteia") || desc.contains("grec") ||
                desc.contains("hellén") || desc.contains("athens")) {
            return "GRÈCE ANTIQUE";
        }

        if (origin.contains("viking") || origin.contains("norse") ||
                origin.contains("scandinav") || name.contains("hnefatafl") ||
                desc.contains("viking") || desc.contains("scandinave") || desc.contains("nordic")) {
            return "CIVILISATION VIKING";
        }

        if (game.getYearPublished() != null) {
            if (game.getYearPublished() < 0) {
                return "ANTIQUITÉ";
            } else if (game.getYearPublished() < 1500) {
                return "MÉDIÉVAL";
            } else if (game.getYearPublished() < 1800) {
                return "RENAISSANCE/MODERNE";
            }
        }

        return null;
    }

    /**
     * 🔥 EXTRACTION TYPE DE JEU AMÉLIORÉE
     */
    private String extractGameTypeEnhanced(Game game) {
        String name = game.getName().toLowerCase();
        String desc = game.getDescription() != null ? game.getDescription().toLowerCase() : "";

        List<String> types = new ArrayList<>();

        if (desc.contains("board game") || desc.contains("jeu de plateau") ||
                desc.contains("plateau") || desc.contains("grid")) {
            types.add("PLATEAU");
        }

        if (desc.contains("card game") || desc.contains("jeu de cartes") ||
                name.contains("cards") || desc.contains("deck")) {
            types.add("CARTES");
        }

        if (desc.contains("dice") || desc.contains("dé") || desc.contains("dés") ||
                name.contains("dice") || desc.contains("tesserae")) {
            types.add("DÉS");
        }

        if (desc.contains("tile") || desc.contains("tuile") ||
                desc.contains("placement")) {
            types.add("TUILES");
        }

        if (desc.contains("abstract") || desc.contains("abstrait") ||
                name.contains("chess") || name.contains("go")) {
            types.add("ABSTRAIT");
        }

        if (desc.contains("strategy") || desc.contains("stratégie") ||
                desc.contains("wargame") || desc.contains("tactic")) {
            types.add("STRATÉGIE");
        }

        if (desc.contains("family") || desc.contains("familial") ||
                desc.contains("children") || desc.contains("kid")) {
            types.add("FAMILIAL");
        }

        if (desc.contains("cooperative") || desc.contains("coopératif")) {
            types.add("COOPÉRATIF");
        }

        return types.isEmpty() ? null : String.join("/", types);
    }

    /**
     * 🔥 ENRICHISSEMENT DESCRIPTION
     */
    private String enhanceDescription(String description, Game game) {
        StringBuilder enhanced = new StringBuilder();

        if (game.getYearPublished() != null && game.getYearPublished() < 1500) {
            enhanced.append("Jeu antique. ");
        }

        if (game.getAverageRating() != null && game.getAverageRating() > 7.5) {
            enhanced.append("Très bien noté. ");
        }

        if (game.getComplexityWeight() != null) {
            if (game.getComplexityWeight() > 3.5) {
                enhanced.append("Jeu complexe nécessitant de la stratégie. ");
            } else if (game.getComplexityWeight() < 2.0) {
                enhanced.append("Jeu accessible, règles simples. ");
            }
        }

        enhanced.append(description);
        return enhanced.toString();
    }

    /**
     * Indexe tous les jeux dans Qdrant
     */
    public void indexAllGames() {
        System.out.println("=== INDEXATION VECTORIELLE DES JEUX ===");

        List<Game> allGames = gameRepository.findAll();
        System.out.println("📊 " + allGames.size() + " jeux à indexer");

        if (allGames.isEmpty()) {
            System.out.println("⚠️ Aucun jeu trouvé en base de données");
            return;
        }

        if (!testConnections()) {
            throw new RuntimeException("Connexions aux services non disponibles");
        }

        int successCount = 0;
        int batchSize = 3; // Réduit la taille des lots pour éviter les erreurs
        int totalBatches = (int) Math.ceil((double) allGames.size() / batchSize);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int start = batchNum * batchSize;
            int end = Math.min(start + batchSize, allGames.size());
            List<Game> batch = allGames.subList(start, end);

            System.out.printf("📦 Lot %d/%d (%d jeux)%n",
                    batchNum + 1, totalBatches, batch.size());

            boolean success = processBatch(batch, batchNum + 1);

            if (success) {
                successCount += batch.size();
                System.out.println("✅ Lot " + (batchNum + 1) + " indexé");

                if (batchNum < totalBatches - 1) {
                    try {
                        Thread.sleep(500); // Augmenter le délai entre les lots
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                System.err.println("❌ Échec lot " + (batchNum + 1) + ", tentative individuelle...");
                for (Game game : batch) {
                    if (processSingleGame(game)) {
                        successCount++;
                    }
                }
            }
        }

        System.out.println("📊 Indexation terminée: " + successCount + "/" + allGames.size() + " jeux");
    }

    private boolean processBatch(List<Game> batch, int batchNum) {
        try {
            List<Document> documents = batch.stream()
                    .map(this::gameToDocumentEnhanced)
                    .collect(Collectors.toList());

            // Vérifier la taille totale des documents
            long totalChars = documents.stream()
                    .mapToLong(doc -> doc.getContent().length())
                    .sum();

            if (totalChars > 10000) { // Limite empirique
                System.out.println("⚠️ Lot " + batchNum + " trop volumineux (" + totalChars + " caractères) - Envoi individuel");
                return false; // Forcer le traitement individuel
            }

            vectorStore.add(documents);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erreur lot " + batchNum + ": " + e.getMessage());
            if (e.getMessage().contains("context length") || e.getMessage().contains("Bad Request")) {
                System.err.println("⚠️ Erreur de contexte - Tentative de traitement individuel");
            }
            return false;
        }
    }

    private boolean processSingleGame(Game game) {
        try {
            Document doc = gameToDocumentEnhanced(game);
            vectorStore.add(List.of(doc));
            System.out.println("  ✅ " + game.getName() + " indexé");
            return true;
        } catch (Exception e) {
            System.err.println("  ❌ " + game.getName() + ": " + e.getMessage());

            // Tentative avec une version encore plus réduite
            if (e.getMessage().contains("context length") || e.getMessage().contains("Bad Request")) {
                System.err.println("  ⚠️ Tentative avec version ultra-réduite...");
                try {
                    Document reducedDoc = createMinimalDocument(game);
                    vectorStore.add(List.of(reducedDoc));
                    System.out.println("  ✅ " + game.getName() + " indexé (version réduite)");
                    return true;
                } catch (Exception e2) {
                    System.err.println("  ❌ Échec même avec version réduite");
                }
            }
            return false;
        }
    }

    /**
     * 🔥 CRÉATION DOCUMENT MINIMAL POUR LES JEUX TRÈS VOLUMINEUX
     */
    private Document createMinimalDocument(Game game) {
        StringBuilder content = new StringBuilder();

        content.append("JEU: ").append(game.getName());
        if (game.getYearPublished() != null) {
            content.append(" (").append(game.getYearPublished()).append(")");
        }
        content.append(". ");

        // Informations essentielles seulement
        if (game.getMinPlayers() != null && game.getMaxPlayers() != null) {
            content.append("Joueurs: ").append(game.getMinPlayers())
                    .append("-").append(game.getMaxPlayers()).append(". ");
        }

        if (game.getAverageRating() != null) {
            content.append("Note: ").append(String.format("%.1f", game.getAverageRating())).append("/10. ");
        }

        if (game.getDescription() != null) {
            // Description très courte
            String shortDesc = truncateToLength(game.getDescription(), 200);
            content.append(shortDesc);
        }

        // Métadonnées minimales
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gameId", game.getId());
        metadata.put("name", game.getName());
        metadata.put("source", game.getSource().toString());

        if (game.getYearPublished() != null) {
            metadata.put("year", game.getYearPublished());
        }

        System.out.println("  📏 Document minimal créé pour: " + game.getName() +
                " (" + content.length() + " caractères)");

        return new Document(content.toString().trim(), metadata);
    }

    private boolean testConnections() {
        return testOllamaConnection() && testQdrantConnection();
    }

    private boolean testQdrantConnection() {
        System.out.println("🔌 Test Qdrant...");
        try {
            vectorStore.similaritySearch(SearchRequest.query("test").withTopK(1));
            System.out.println("✅ Qdrant OK");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Qdrant: " + e.getMessage());
            return false;
        }
    }

    private boolean testOllamaConnection() {
        System.out.println("🔌 Test Ollama...");
        try {
            embeddingModel.embed("test");
            System.out.println("✅ Ollama OK");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Ollama: " + e.getMessage());
            return false;
        }
    }
}