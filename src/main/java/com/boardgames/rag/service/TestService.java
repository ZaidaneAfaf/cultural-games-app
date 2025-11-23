package com.boardgames.rag.service;

import com.boardgames.rag.entity.*;
import com.boardgames.rag.repository.GameRepository;
import com.boardgames.rag.service.csv.BggCsvImportService;
import com.boardgames.rag.service.csv.LudiiGamesImportService;
import com.boardgames.rag.service.csv.LudiiRulesetsImportService;
import com.boardgames.rag.service.embedding.VectorStoreService;
import com.boardgames.rag.service.rag.RagService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class TestService {

    private final GameRepository gameRepository;
    private final BggCsvImportService bggImportService;
    private final LudiiGamesImportService ludiiImportService;
    private final LudiiRulesetsImportService ludiiRulesetsImportService;
    private final VectorStoreService vectorStoreService;
    private final RagService ragService;

    public TestService(GameRepository gameRepository,
                       BggCsvImportService bggImportService,
                       LudiiGamesImportService ludiiImportService,
                       LudiiRulesetsImportService ludiiRulesetsImportService,
                       VectorStoreService vectorStoreService,
                       RagService ragService) {
        this.gameRepository = gameRepository;
        this.bggImportService = bggImportService;
        this.ludiiImportService = ludiiImportService;
        this.ludiiRulesetsImportService = ludiiRulesetsImportService;
        this.vectorStoreService = vectorStoreService;
        this.ragService = ragService;
    }

    @PostConstruct
    public void testEnhancedModels() {
        System.out.println("=== DÉMARRAGE APPLICATION ===");

        // Nettoyer la base pour les tests
        gameRepository.deleteAll();
        System.out.println("🗑️ Base de données nettoyée");

        // Créer des jeux de test
        createTestGames();

        // Tester l'import CSV
        testCsvImport();

        // Tester les recherches
        testSearches();

        // Tester les rulesets
        testRulesets();

        // Tester le système RAG
        testRagSystem();

        System.out.println("=== DÉMARRAGE TERMINÉ ===");
    }

    private void createTestGames() {
        System.out.println("=== CRÉATION JEUX DE TEST ===");

        // Créer un jeu BGG complet
        Game bggGame = createSampleBggGame();
        Game savedBggGame = gameRepository.save(bggGame);
        System.out.println("✅ Jeu BGG sauvegardé: " + savedBggGame.getName());

        // Créer un jeu Ludii complet
        Game ludiiGame = createSampleLudiiGame();
        Game savedLudiiGame = gameRepository.save(ludiiGame);
        System.out.println("✅ Jeu Ludii sauvegardé: " + savedLudiiGame.getName());
    }

    private Game createSampleBggGame() {
        Game game = new Game();
        game.setName("Catan");
        game.setDescription("Jeu de stratégie et de commerce dans un monde médiéval");
        game.setYearPublished(1995);
        game.setSource(Game.GameSource.BGG);
        game.setSourceId("BGG-123");

        game.setCategories(Arrays.asList("Strategy", "Trading", "Medieval"));
        game.setMinPlayers(3);
        game.setMaxPlayers(4);
        game.setBestPlayers(Arrays.asList(4));
        game.setEstimatedPlaytime(90);

        game.setAverageRating(7.5);
        game.setComplexityWeight(2.3);
        game.setNumRatings(150000);

        // Métadonnées BGG
        BggMetadata bggMetadata = new BggMetadata();
        bggMetadata.setBggId(123);
        bggMetadata.setCatStrategy(true);
        bggMetadata.setCatFamily(true);
        bggMetadata.setDesigners("Klaus Teuber");
        bggMetadata.setStdDevRating(1.2);
        game.setBggMetadata(bggMetadata);

        return game;
    }

    private Game createSampleLudiiGame() {
        Game game = new Game();
        game.setName("Senet");
        game.setDescription("Jeu de plateau égyptien antique");
        game.setYearPublished(-3000); // Année négative pour les jeux anciens
        game.setSource(Game.GameSource.LUDII);
        game.setSourceId("LUDII-456");

        game.setCategories(Arrays.asList("Ancient", "Race", "Strategy"));
        game.setMinPlayers(2);
        game.setMaxPlayers(2);
        game.setEstimatedPlaytime(30);

        // Métadonnées Ludii
        LudiiMetadata ludiiMetadata = new LudiiMetadata();
        ludiiMetadata.setLudiiId(456);
        ludiiMetadata.setOrigin("Egypt");
        ludiiMetadata.setOriginPoint("30°1'55.75\"N, 31°4'31.13\"E");
        ludiiMetadata.setEvidenceRange("-3000,-1000");
        ludiiMetadata.setKnownAliases(Arrays.asList("Game of Passing", "Znt"));
        game.setLudiiMetadata(ludiiMetadata);

        return game;
    }

    private void testCsvImport() {
        System.out.println("=== TEST IMPORT CSV ===");

        // Import BGG
        try {
            bggImportService.importBggGames();
        } catch (Exception e) {
            System.err.println("❌ Erreur import BGG: " + e.getMessage());
            System.out.println("💡 Assurez-vous que le fichier BGG est dans le dossier 'data/'");
        }

        // Import Ludii Games
        try {
            ludiiImportService.importLudiiGames();
        } catch (Exception e) {
            System.err.println("❌ Erreur import Ludii Games: " + e.getMessage());
            System.out.println("💡 Assurez-vous que le fichier Ludii Games est dans le dossier 'data/'");
        }

        // Import Ludii Rulesets
        try {
            ludiiRulesetsImportService.importLudiiRulesets();
        } catch (Exception e) {
            System.err.println("❌ Erreur import Ludii Rulesets: " + e.getMessage());
            System.out.println("💡 Assurez-vous que le fichier Ludii Rulesets est dans le dossier 'data/'");
        }
    }

    private void testSearches() {
        System.out.println("=== TEST RECHERCHES ===");

        // Test recherche par catégorie
        List<Game> strategyGames = gameRepository.findByCategoriesContaining("Strategy");
        System.out.println("🔍 Jeux de stratégie trouvés: " + strategyGames.size());

        // Test recherche par origine
        List<Game> egyptGames = gameRepository.findByOrigin("Egypt");
        System.out.println("🔍 Jeux égyptiens trouvés: " + egyptGames.size());

        // Test recherche par source
        List<Game> bggGames = gameRepository.findBySource(Game.GameSource.BGG);
        System.out.println("🔍 Jeux BGG trouvés: " + bggGames.size());

        List<Game> ludiiGames = gameRepository.findBySource(Game.GameSource.LUDII);
        System.out.println("🔍 Jeux Ludii trouvés: " + ludiiGames.size());

        // Test recherche par année
        List<Game> ancientGames = gameRepository.findByYearPublishedBetween(-5000, 0);
        System.out.println("🔍 Jeux anciens (avant JC) trouvés: " + ancientGames.size());

        // Compter le total
        long total = gameRepository.count();
        System.out.println("📊 Total des jeux en base: " + total);

        // Afficher quelques exemples
        if (total > 0) {
            System.out.println("🎲 Exemples de jeux importés:");
            List<Game> sampleGames = gameRepository.findAll().subList(0, Math.min(5, (int) total));
            for (Game game : sampleGames) {
                System.out.println("   - " + game.getName() + " (" + game.getSource() + ")");
            }
        }
    }

    private void testRulesets() {
        System.out.println("=== TEST RULESETS ===");

        // Trouver des jeux avec rulesets
        List<Game> allGames = gameRepository.findAll();
        int gamesWithRulesets = 0;
        int totalRulesets = 0;

        for (Game game : allGames) {
            if (game.getRulesets() != null && !game.getRulesets().isEmpty()) {
                gamesWithRulesets++;
                totalRulesets += game.getRulesets().size();

                // Afficher les 3 premiers jeux avec rulesets
                if (gamesWithRulesets <= 3) {
                    System.out.println("📚 " + game.getName() + " - " + game.getRulesets().size() + " variantes");
                    for (Ruleset ruleset : game.getRulesets().subList(0, Math.min(2, game.getRulesets().size()))) {
                        System.out.println("   └─ " + ruleset.getName() +
                                (ruleset.getType() != null ? " (Type: " + ruleset.getType() + ")" : ""));
                    }
                }
            }
        }

        System.out.println("📊 " + gamesWithRulesets + " jeux avec rulesets");
        System.out.println("📚 " + totalRulesets + " rulesets au total");

        if (gamesWithRulesets == 0) {
            System.out.println("💡 Aucun ruleset trouvé - vérifiez l'import du fichier rulesets");
        }
    }

    private void testRagSystem() {
        System.out.println("=== TEST SYSTÈME RAG ===");

        // 1. Indexation vectorielle avec gestion d'erreur détaillée
        try {
            System.out.println("🚀 Début de l'indexation...");
            vectorStoreService.indexAllGames();
            System.out.println("✅ Indexation terminée avec succès !");
        } catch (Exception e) {
            System.err.println("❌ ERREUR INDEXATION VECTORIELLE:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Type: " + e.getClass().getName());
            System.err.println("\n📋 Stack trace complète:");
            e.printStackTrace();
            System.err.println("\n💡 Vérifications:");
            System.err.println("   - Qdrant est-il démarré ? (docker ps | grep qdrant)");
            System.err.println("   - Ollama est-il démarré ? (ollama list)");
            System.err.println("   - Port Qdrant correct ? (6334 pour gRPC)");
            return;
        }

        // 2. Tests de recherche RAG (seulement si indexation OK)
        testRagSearches();
    }

    private void testRagSearches() {
        System.out.println("\n🧪 Tests de recherche RAG:");

        String[] testQuestions = {
                "jeux de stratégie pour 4 joueurs",
                "jeux anciens égyptiens",
                "meilleurs jeux complexes",
                "jeux familiaux simples",
                "règles du senet"
        };

        for (String question : testQuestions) {
            try {
                System.out.println("\n🔍 Recherche RAG: \"" + question + "\"");
                RagService.RagResponse response = ragService.searchWithRag(question, 3);
                System.out.println("   📝 " + response.getAnswer().split("\n")[0]);
                System.out.println("   🎯 " + response.getRelevantGames().size() + " jeux trouvés");
            } catch (Exception e) {
                System.err.println("   ❌ Erreur: " + e.getMessage());
            }
        }
    }
}