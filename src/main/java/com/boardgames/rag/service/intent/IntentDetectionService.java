package com.boardgames.rag.service.intent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class IntentDetectionService {

    private final ChatClient chatClient;
    private final ExecutorService executorService;

    // 🔥 HALLUCINATIONS À BLOQUER (liste étendue)
    private static final String[] HALLUCINATIONS_A_BLOQUER = {
            "tiroch", "ninkasi game", "ludus mysticus", "jeu de la déesse",
            "ninkasi", "ludus mystic", "game of ninkasi", "tiroch game",
            "mystical ludus", "goddess game", "jeu mystique", "jeu de tiroch"
    };

    // Termes archéologiques FORTS
    private static final String[] TERMES_ARCHEO_FORCES = {
            "trouvé", "trouve", "découvert", "découverte", "excavation", "fouille",
            "plateau", "case", "cases", "grille", "ligne",
            "artefact", "vestige", "fragment", "fragmenté", "cassé", "brisé",
            "ancien", "antique", "archéologique", "archéologue",
            "site archéologique", "tombe", "sépulture", "nécropole", "ruines",
            "pompéi", "pompeii", "herculanum", "rome antique",
            "os", "ivoire", "pierre", "argile", "céramique", "poterie",
            "gravure", "inscription", "hiéroglyphe", "symbole", "motif",
            "dé en", "osselet", "tali", "tessera", "astragale",
            "sumérien", "babylonien", "égyptien", "romain", "grec",
            "mésopotamien", "pharaonique", "néolithique", "préhistorique",
            "cube", "sphérique", "cylindrique", "poli", "taillé",
            "points gravés", "faces numérotées", "dimensions", "cm", "mm"
    };

    // Termes de description matérielle
    private static final String[] TERMES_DESCRIPTION_MATERIELLE = {
            "cm", "mm", "centimètre", "millimètre", "mètre",
            "cube", "sphère", "cylindre", "rectangulaire", "carré", "circulaire",
            "matériau", "matière", "composé de", "fabriqué en",
            "pèse", "gramme", "kg", "poids",
            "couleur", "noir", "blanc", "rouge", "ocre", "marron", "beige",
            "texture", "lisse", "rugueux", "poli", "brut",
            "gravé", "sculpté", "incisé", "peint", "décoré",
            "points", "faces", "côtés", "surface"
    };

    public IntentDetectionService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.executorService = Executors.newCachedThreadPool();
    }

    public IntentResult detectIntent(String userMessage) {
        System.out.println("🎯 [INTENT] Analyse: \"" + userMessage.substring(0, Math.min(50, userMessage.length())) + "...\"");

        String lowerMessage = userMessage.toLowerCase().trim();

        // ===== PRIORITÉ 1 : HALLUCINATIONS (BLOQUAGE IMMÉDIAT) =====
        for (String hallucination : HALLUCINATIONS_A_BLOQUER) {
            if (lowerMessage.contains(hallucination)) {
                System.out.println("🚨 [INTENT] ⚠️ HALLUCINATION DÉTECTÉE ET BLOQUÉE: " + hallucination);
                String warningMessage = generateHallucinationWarning(hallucination);
                return new IntentResult(IntentType.HALLUCINATION_BLOCKED, warningMessage);
            }
        }

        // ===== PRIORITÉ 2 : SALUTATIONS =====
        if (isGreetingOrSmallTalk(userMessage)) {
            System.out.println("👋 [INTENT] Salutation détectée");
            return new IntentResult(IntentType.GREETING, generateConversationalResponse(lowerMessage));
        }

        // ===== PRIORITÉ 3 : DESCRIPTION ARCHÉOLOGIQUE =====
        boolean isArchaeological = isArchaeologicalDescription(lowerMessage);

        if (isArchaeological) {
            System.out.println("🏛️ [INTENT] 🔍 DESCRIPTION ARCHÉOLOGIQUE DÉTECTÉE");
            return new IntentResult(IntentType.ARCHAEOLOGICAL_IDENTIFICATION, null);
        }

        // ===== PRIORITÉ 4 : QUESTIONS THÉORIQUES =====
        if (isTheoreticalQuestion(userMessage)) {
            System.out.println("💭 [INTENT] Question théorique détectée");
            return new IntentResult(IntentType.THEORETICAL_QUESTION, null);
        }

        // ===== PRIORITÉ 5 : RECHERCHE DE JEUX =====
        System.out.println("🎮 [INTENT] Recherche de jeu");
        return new IntentResult(IntentType.GAME_SEARCH, null);
    }

    // ===== GÉNÉRATION AVERTISSEMENT HALLUCINATION =====
    private String generateHallucinationWarning(String term) {
        return String.format("""
            🚫 **Terme non documenté détecté** : "%s"
            
            ⚠️ **Attention** : Ce terme ne correspond à aucun jeu historique documenté dans les sources archéologiques reconnues.
            
            **Jeux antiques authentifiés :**
            
            📚 **Égypte ancienne** (-3000 à -332)
            • Senet : Jeu de parcours à 30 cases
            • Mehen : Plateau en spirale représentant un serpent
            • Chiens et Chacals : Jeu de course à 58 trous
            
            📚 **Mésopotamie** (-2600 à -539)
            • Royal Game of Ur : 20 cases, 2 joueurs
            • Jeu des Vingt Cases
            
            📚 **Rome antique** (-753 à 476)
            • Ludus latrunculorum : Jeu de stratégie (ancêtre des échecs)
            • Tesserae : Dés en os/ivoire à 6 faces
            • Duodecim Scripta : Jeu de course à 3 lignes
            
            📚 **Grèce antique** (-800 à -146)
            • Petteia : Jeu de stratégie abstrait
            • Astragales : Osselets (ancêtres des dés)
            
            📚 **Civilisation Viking** (793-1066)
            • Hnefatafl : Jeu de stratégie asymétrique
            
            💡 **Recommandations :**
            • Consultez un archéologue professionnel pour authentification
            • Vérifiez les sources académiques (British Museum, Louvre, publications scientifiques)
            • Recherchez dans les bases de données archéologiques certifiées
            
            ℹ️ Si vous cherchez un jeu historique réel, reformulez votre recherche avec un nom documenté.
            """, term);
    }

    // ===== DÉTECTION DESCRIPTION ARCHÉOLOGIQUE (AMÉLIORÉE) =====
    private boolean isArchaeologicalDescription(String message) {
        String lowerMessage = message.toLowerCase();

        // Critère 1 : Description matérielle détaillée (NOUVEAU)
        int descCount = 0;
        for (String terme : TERMES_DESCRIPTION_MATERIELLE) {
            if (lowerMessage.contains(terme)) {
                descCount++;
            }
        }

        if (descCount >= 3) {
            System.out.println("🔍 [INTENT-ARCH] Description matérielle détectée (" + descCount + " termes)");
            return true;
        }

        // Critère 2 : Termes archéologiques forts
        int forceCount = 0;
        for (String terme : TERMES_ARCHEO_FORCES) {
            if (lowerMessage.contains(terme)) {
                forceCount++;
            }
        }

        if (forceCount >= 2) {
            System.out.println("🎯 [INTENT-ARCH] " + forceCount + " termes archéo forts détectés");
            return true;
        }

        // Critère 3 : Patterns archéologiques spécifiques
        String[] archaeologicalPatterns = {
                "j'ai trouvé.*(os|pierre|argile|bois|ivoire)",
                "nous avons découvert.*(jeu|artefact|objet)",
                "découvert à.*(pompéi|rome|égypte|grèce)",
                "trouvé dans.*(tombe|sépulture|fouille|site)",
                "description.*(ancien|antique|archéologique)",
                "ressemble à un.*(dé|jeu|plateau)",
                "pourrait être.*(jeu|artefact)",
                "matériau.*(os|ivoire|pierre|argile)",
                "forme.*(carré|rond|cube|cylindrique)",
                "dimension.*(cm|mm|centimètre)",
                "gravé.*(motif|inscription|symbole|points)",
                "objet.*(antique|ancien|archéologique)",
                "cube en (pierre|os|argile|ivoire)",
                "points gravés", "faces numérotées"
        };

        for (String pattern : archaeologicalPatterns) {
            if (lowerMessage.matches(".*" + pattern + ".*")) {
                System.out.println("🎯 [INTENT-ARCH] Pattern archéo détecté: " + pattern);
                return true;
            }
        }

        // Critère 4 : Description longue avec contexte archéologique
        if (message.length() > 80) {
            String[] contextTerms = {
                    "site", "fouille", "excavation", "archéologue",
                    "musée", "collection", "découverte", "vestige"
            };

            int contextCount = 0;
            for (String term : contextTerms) {
                if (lowerMessage.contains(term)) contextCount++;
            }

            if (contextCount >= 2 && descCount >= 2) {
                System.out.println("🎯 [INTENT-ARCH] Description longue avec contexte archéologique");
                return true;
            }
        }

        return false;
    }

    // ===== DÉTECTION QUESTIONS THÉORIQUES =====
    private boolean isTheoreticalQuestion(String message) {
        String lower = message.toLowerCase().trim();

        // Vérification : Si la question mentionne des jeux spécifiques, ce n'est PAS théorique
        if (containsMultipleKnownGames(lower)) {
            System.out.println("🎮 [INTENT] Question comparative entre jeux spécifiques → GAME_SEARCH");
            return false;
        }

        // Questions théoriques/conceptuelles
        String[] theoreticalPatterns = {
                "c'est quoi la relation entre", "qu'est-ce que la relation entre",
                "relation entre", "lien entre", "différence entre",
                "histoire des jeux", "évolution des jeux", "origine des jeux",
                "classification des jeux", "types de jeux", "catégories de jeux",
                "comment sont créés", "l'importance des jeux", "rôle des jeux",
                "impact des jeux", "pourquoi les jeux", "comment les jeux",
                "qu'est-ce qu'un jeu", "définition de jeu", "qu'est ce que un jeu",
                "comment.*évolué", "évolution.*histoire", "évolution.*à travers",
                "transformation.*jeux", "développement.*historique",
                "comment.*changé", "comment.*devenu", "changement.*historique"
        };

        for (String pattern : theoreticalPatterns) {
            if (lower.contains(pattern) || lower.matches(".*" + pattern + ".*")) {
                return true;
            }
        }

        // Questions "c'est quoi" sur sujets généraux
        if (lower.matches("^c'est quoi (l[ae]s? |d[eu]s? |un |une )?.*") ||
                lower.matches("^qu'est-ce que (l[ae]s? |d[eu]s? |un |une )?.*")) {

            String afterStarter = lower
                    .replaceFirst("^c'est quoi (l[ae]s? |d[eu]s? |un |une )?", "")
                    .replaceFirst("^qu'est-ce que (l[ae]s? |d[eu]s? |un |une )?", "");

            String[] generalTopics = {
                    "histoire", "évolution", "origine", "classification",
                    "importance", "rôle", "impact", "définition", "concept",
                    "relation", "lien", "différence"
            };

            for (String topic : generalTopics) {
                if (afterStarter.contains(topic)) {
                    System.out.println("💡 [INTENT] Question théorique (sujet général: " + topic + ")");
                    return true;
                }
            }

            if (!containsKnownGameName(afterStarter)) {
                System.out.println("💡 [INTENT] Question théorique (pas de jeu connu)");
                return true;
            }
        }

        // Questions larges sans jeu spécifique
        String[] broadQuestions = {
                "meilleurs jeux", "top jeux", "jeux recommand",
                "quels jeux", "quelles sont", "liste des jeux",
                "exemples de jeux", "types de stratégie"
        };

        for (String pattern : broadQuestions) {
            if (lower.contains(pattern)) {
                System.out.println("💡 [INTENT] Question théorique (question large)");
                return true;
            }
        }

        return false;
    }

    // ===== DÉTECTION NOM DE JEU CONNU =====
    private boolean containsKnownGameName(String text) {
        String[] knownGames = {
                "catan", "pandemic", "chess", "échecs", "go", "senet",
                "monopoly", "scrabble", "cluedo", "risk", "azul",
                "ticket to ride", "carcassonne", "7 wonders", "dominion",
                "puerto rico", "agricola", "terra mystica", "twilight struggle",
                "war of the ring", "gloomhaven", "spirit island", "wingspan",
                "root", "scythe", "brass birmingham", "ark nova", "mehen",
                "royal game of ur", "petteia", "ludus latrunculorum",
                "hnefatafl", "duodecim scripta", "tesserae"
        };

        for (String game : knownGames) {
            if (text.contains(" " + game + " ") ||
                    text.startsWith(game + " ") ||
                    text.endsWith(" " + game) ||
                    text.equals(game)) {
                return true;
            }
        }

        return false;
    }

    // ===== DÉTECTION MULTIPLES JEUX CONNUS =====
    private boolean containsMultipleKnownGames(String text) {
        String[] knownGames = {
                "catan", "pandemic", "chess", "échecs", "go", "senet",
                "monopoly", "scrabble", "cluedo", "risk", "azul",
                "ticket to ride", "carcassonne", "7 wonders", "dominion",
                "puerto rico", "agricola", "terra mystica", "twilight struggle",
                "war of the ring", "gloomhaven", "spirit island", "wingspan",
                "root", "scythe", "brass birmingham", "ark nova", "mehen",
                "royal game of ur", "petteia", "ludus latrunculorum"
        };

        int gameCount = 0;

        for (String game : knownGames) {
            if (text.contains(" " + game + " ") ||
                    text.startsWith(game + " ") ||
                    text.endsWith(" " + game) ||
                    text.equals(game)) {
                gameCount++;
                if (gameCount >= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    // ===== SALUTATIONS =====
    private boolean isGreetingOrSmallTalk(String message) {
        String trimmed = message.trim().toLowerCase();

        String[] exactGreetings = {
                "bonjour", "bonsoir", "salut", "hello", "hi", "hey",
                "bonjour!", "salut!", "hello!", "hi!", "ça va", "cava", "cv",
                "tu vas bien", "vas bien", "comment ça va", "comment ca va",
                "comment vas-tu", "comment allez-vous", "comment tu vas",
                "bien ou bien", "sava", "ça va ?", "cava ?"
        };

        for (String greeting : exactGreetings) {
            if (trimmed.equals(greeting) || trimmed.equals(greeting + "?")) {
                return true;
            }
        }

        String[] smallTalkPatterns = {
                "tu vas", "vas-tu", "allez-vous", "comment tu", "comment ça",
                "quoi de neuf", "quoi de neuf ?", "quoi de nouveau",
                "ça roule", "ca roule", "comment c'est", "comment c est"
        };

        for (String pattern : smallTalkPatterns) {
            if (trimmed.contains(pattern) && trimmed.length() < 25) {
                return true;
            }
        }

        if (trimmed.contains("merci") && trimmed.length() < 30) {
            return true;
        }

        return false;
    }

    private String generateConversationalResponse(String message) {
        if (message.contains("merci")) {
            return "Avec plaisir ! 😊 N'hésitez pas si vous avez d'autres questions sur les jeux.";
        }
        if (message.contains("bonsoir")) {
            return "Bonsoir ! 👋 Comment puis-je vous aider concernant les jeux de société ?";
        }
        return "Bonjour ! 👋 Je suis spécialiste des jeux de société. Comment puis-je vous aider ?";
    }

    // ===== GÉNÉRATION RÉPONSE THÉORIQUE =====
    public String generateTheoreticalResponse(String question) {
        System.out.println("💭 [QUESTION THÉORIQUE] Génération pour: " + question);

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return askOllamaTheoretical(question);
            }, executorService);

            String response = future.get(20, TimeUnit.SECONDS);

            if (response == null || response.trim().isEmpty()) {
                return generateTheoreticalFallback(question);
            }

            System.out.println("✅ [QUESTION THÉORIQUE] Réponse générée (" + response.length() + " caractères)");
            return response;

        } catch (TimeoutException e) {
            System.err.println("⏰ [QUESTION THÉORIQUE] Timeout");
            return generateTheoreticalFallback(question);
        } catch (Exception e) {
            System.err.println("❌ [QUESTION THÉORIQUE] Erreur: " + e.getMessage());
            return generateTheoreticalFallback(question);
        }
    }

    private String askOllamaTheoretical(String question) {
        String prompt = String.format("""
            Tu es un expert en histoire et théorie des jeux de société.
            
            Question de l'utilisateur: %s
            
            Réponds de manière:
            1. Conceptuelle et pédagogique
            2. Avec exemples concrets de jeux historiques
            3. Avec contexte historique et évolution
            4. En citant des catégories de jeux si pertinent
            
            Ton ton est naturel, amical et professionnel, comme ChatGPT.
            Réponse en français, 200-400 mots maximum.
            """, question);

        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            throw new RuntimeException("Erreur Ollama: " + e.getMessage());
        }
    }

    private String generateTheoreticalFallback(String question) {
        String lowerQuestion = question.toLowerCase();

        if (lowerQuestion.contains("meilleurs") || lowerQuestion.contains("top") ||
                lowerQuestion.contains("recommand")) {

            return """
                🏆 **Recommandations de jeux de société**
                
                **Par complexité:**
                
                📘 **Simples** (1-2/5)
                • Carcassonne - Placement de tuiles, 2-5 joueurs, 30-45min
                • Azul - Collection de tuiles, 2-4 joueurs, 30-45min
                • Ticket to Ride - Trains, 2-5 joueurs, 30-60min
                
                📙 **Moyens** (3/5)
                • Catan - Colonisation, 3-4 joueurs, 60-120min
                • 7 Wonders - Civilisation, 2-7 joueurs, 30min
                • Dominion - Deck-building, 2-4 joueurs, 30min
                
                📕 **Complexes** (4-5/5)
                • Twilight Struggle - Guerre froide, 2 joueurs, 180min
                • Terra Mystica - Fantastique, 2-5 joueurs, 60-150min
                • Gloomhaven - Tactique/RPG, 1-4 joueurs, 60-120min
                
                **Par catégorie:**
                • **Stratégie**: Chess, Go, Terra Mystica
                • **Familial**: Carcassonne, Ticket to Ride, Azul
                • **Coopératif**: Pandemic, Forbidden Island, Spirit Island
                • **Party**: Codenames, Dixit, Decrypto
                
                💡 **Conseil**: Cherchez un jeu spécifique pour plus de détails !
                """;
        }

        if (lowerQuestion.contains("histoire") || lowerQuestion.contains("évolution") ||
                lowerQuestion.contains("origine")) {

            return """
                📚 **Histoire des jeux de société**
                
                **🏺 Antiquité** (-3000 à 500)
                • **Senet** (Égypte, -3000): Jeu de parcours religieux, 30 cases
                • **Royal Game of Ur** (Mésopotamie, -2600): Jeu de course
                • **Mehen** (Égypte, -3000): Plateau en spirale
                • **Ludus latrunculorum** (Rome): Stratégie militaire
                • **Petteia** (Grèce): Stratégie abstraite
                
                **⚔️ Moyen Âge** (500-1500)
                • **Chess** (Inde/Perse, ~600): Stratégie abstraite
                • **Go** (Chine, -2000): Contrôle territorial
                • **Hnefatafl** (Vikings, 400-1000): Stratégie asymétrique
                
                **🎭 Ère moderne** (1900-1980)
                • **Monopoly** (1935): Économie et propriété
                • **Risk** (1957): Conquête mondiale
                • **Diplomacy** (1959): Négociation stratégique
                • **Scrabble** (1948): Jeu de lettres
                
                **🎲 Ère contemporaine** (1980-aujourd'hui)
                • **Catan** (1995): Nouvelle vague de jeux allemands
                • **Pandemic** (2008): Coopératif moderne
                • **Gloomhaven** (2017): Campagne tactique immersive
                • **Wingspan** (2019): Jeu naturaliste
                
                **📊 Évolution majeure:**
                Passage de jeux abstraits anciens (Chess, Go) aux jeux thématiques modernes avec mécaniques complexes.
                """;
        }

        return String.format("""
            📖 **Réponse théorique**
            
            **Question:** "%s"
            
            Les jeux de société ont évolué depuis l'Antiquité avec plusieurs grandes familles:
            
            **🏺 Jeux antiques:**
            • Senet, Mehen (Égypte)
            • Royal Game of Ur (Mésopotamie)
            • Chess, Go (Asie)
            • Ludus latrunculorum (Rome)
            
            **🎯 Classification moderne:**
            • **Abstraits**: Chess, Go, Othello
            • **Stratégie**: Catan, Twilight Struggle, Terra Mystica
            • **Familiaux**: Carcassonne, Azul, Ticket to Ride
            • **Coopératifs**: Pandemic, Spirit Island
            • **Party**: Codenames, Dixit, Decrypto
            
            **📈 Tendances actuelles:**
            • Mécaniques innovantes (deck-building, placement d'ouvriers)
            • Thèmes immersifs et narratifs
            • Jeux solo et coopératifs
            • Campagnes longues (legacy games)
            
            💡 **Pour plus d'informations:** Posez une question plus spécifique ou cherchez un jeu précis !
            """, question);
    }

    // ===== ANALYSE ARCHÉOLOGIQUE POUR IDENTIFIER LE JEU =====
    public String generateArchaeologicalAnalysis(String description) {
        System.out.println("🏛️ [ARCHÉOLOGIE] Identification du jeu antique");

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return identifyAncientGame(description);
            }, executorService);

            String response = future.get(25, TimeUnit.SECONDS);

            if (response == null || response.trim().isEmpty() || response.length() < 100) {
                System.err.println("⚠️ [ARCHÉOLOGIE] Réponse insuffisante → Fallback");
                return generateArchaeologicalFallback(description);
            }

            System.out.println("✅ [ARCHÉOLOGIE] Identification générée");
            return response;

        } catch (TimeoutException e) {
            System.err.println("⏰ [ARCHÉOLOGIE] Timeout après 25s");
            return generateArchaeologicalFallback(description);
        } catch (Exception e) {
            System.err.println("❌ [ARCHÉOLOGIE] Erreur: " + e.getMessage());
            return generateArchaeologicalFallback(description);
        }
    }

    // 🔥 NOUVEAU : IDENTIFICATION DU JEU ANTIQUE
    private String identifyAncientGame(String description) {
        String shortDesc = description.length() > 400
                ? description.substring(0, 400) + "..."
                : description;

        String prompt = String.format("""
            TU ES ARCHÉOLOGUE SPÉCIALISTE DES JEUX ANTIQUES.
            
            UN ARCHÉOLOGUE TE DÉCRIT UN OBJET TROUVÉ :
            "%s"
            
            TA MISSION : L'aider à identifier à quel JEU ANTIQUE cet objet pourrait appartenir.
            
            === JEUX ANTIQUES CONNUS (pour référence) ===
            
            🏺 **ÉGYPTE ANCIENNE** :
            • Senet : 30 cases, bâtonnets, pions coniques, -3000 av. J.-C.
            • Mehen : plateau spiralé, pions lions, -3000 av. J.-C.
            • Chiens et Chacals : 58 trous, bâtonnets tête de chien
            
            🏛️ **ROME ANTIQUE** :
            • Tesserae : dés cubiques 6 faces (os/ivoire), points 1-6
            • Duodecim Scripta : 3 lignes × 12 cases, ancêtre backgammon
            • Ludus latrunculorum : grille stratégique, pions ronds
            • Tali : dés allongés 4 faces (osselets)
            
            🏺 **MÉSOPOTAMIE** :
            • Royal Game of Ur : 20 cases, dés tétraédriques, -2600 av. J.-C.
            
            ⚔️ **VIKINGS** :
            • Hnefatafl : plateau 11×11 ou 9×9, stratégie asymétrique
            
            🇬🇷 **GRÈCE ANTIQUE** :
            • Petteia : stratégie sur grille, pions similaires aux dames
            • Astragales : osselets comme dés
            
            === TON ANALYSE DOIT SUIVRE CE PLAN ===
            
            🎯 **1. HYPOTHÈSE D'IDENTIFICATION**
            - Jeu antique le plus probable : [NOM]
            - Niveau de confiance : Élevé/Moyen/Faible
            - Raison principale : [pourquoi ce jeu ?]
            
            🔍 **2. COMPARAISON AVEC LA DESCRIPTION**
            ✅ **Ce qui correspond :**
            • [élément 1 de la description] → [fait connu du jeu]
            • [élément 2] → [fait connu]
            
            ⚠️ **Ce qui ne correspond pas/manuelquant :**
            • [différence ou information absente]
            • [ce qu'il faudrait vérifier]
            
            📖 **3. CE QU'ON SAIT DE CE JEU**
            - Origine : [civilisation, période]
            - Règles reconstituées : [2-3 phrases]
            - Où le voir : [musée célèbre]
            - Particularités : [ce qui le rend unique]
            
            🔬 **4. COMMENT ÊTRE SÛR À 100%%**
            - Vérification 1 : [action concrète]
            - Vérification 2 : [action concrète]
            - Expert à consulter : [spécialiste]
            - Analyse recommandée : [technique scientifique]
            
            💡 **5. SI CE N'EST PAS CE JEU...**
            - Autre possibilité : [nom du jeu]
            - Pourquoi moins probable : [raison]
            - Différence clé : [élément distinctif]
            
            === TON STYLE ===
            • Professionnel mais accessible
            • Précis : donne des faits vérifiables
            • Honnête : indique les incertitudes
            • Utile : conseils pratiques
            
            === IMPORTANT ===
            • Base-toi uniquement sur l'archéologie documentée
            • Ne parle PAS de jeux modernes
            • Ne donne PAS de certitude absolue sans preuve
            
            GÉNÈRE TA RÉPONSE MAINTENANT :
            """, shortDesc);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // Vérification que la réponse mentionne un jeu antique connu
            String lowerResponse = response.toLowerCase();
            if (lowerResponse.contains("senet") || lowerResponse.contains("ur") ||
                    lowerResponse.contains("mehen") || lowerResponse.contains("tessera") ||
                    lowerResponse.contains("hnefatafl") || lowerResponse.contains("latrunculorum") ||
                    lowerResponse.contains("duodecim") || lowerResponse.contains("petteia")) {
                return response;
            } else {
                System.err.println("⚠️ [ARCHÉOLOGIE] Aucun jeu antique identifié");
                return generateArchaeologicalFallback(description);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur identification: " + e.getMessage());
        }
    }

    // 🔥 FALLBACK AMÉLIORÉ POUR ARCHÉOLOGIE
    private String generateArchaeologicalFallback(String description) {
        String shortDesc = description.length() > 200
                ? description.substring(0, 200) + "..."
                : description;

        return String.format("""
            🔍 **ANALYSE ARCHÉOLOGIQUE PRÉLIMINAIRE**
            
            **Description reçue :**
            "%s"
            
            **STATUT :** Identification incertaine
            
            **PROBLÈME :** La description ne permet pas d'identifier un jeu antique spécifique avec certitude.
            
            **JEUX ANTIQUES À CONSIDÉRER :**
            
            1. 🎲 **SI C'EST UN DÉ/CUBE :**
               • **Tesserae romaines** : dés cubiques 6 faces, points 1-6
               • **Tali** : dés allongés 4 faces, utilisés pour paris
            
            2. 🏺 **SI C'EST UN PLATEAU :**
               • **Senet** (Égypte) : 30 cases rectangulaires
               • **Royal Game of Ur** (Mésopotamie) : 20 cases avec décoration
               • **Hnefatafl** (Vikings) : plateau carré 11×11 ou 9×9
            
            3. ♟️ **SI CE SONT DES PIONS :**
               • Chercher le plateau correspondant
               • Les pions seuls sont difficiles à identifier
            
            **INFORMATIONS CRITIQUES MANQUANTES :**
            1. 📏 **Dimensions exactes** (en mm, pas en cm)
            2. ⚒️ **Matériau précis** (type de pierre, os de quel animal ?)
            3. 🎨 **Marquages/décors** (points, lignes, symboles)
            4. 🏺 **Contexte archéologique** (site, strate, objets autour)
            
            **ACTIONS IMMÉDIATES :**
            1. 📸 Prendre 5 photos sous différents angles avec une règle
            2. 🏛️ Contacter le musée d'archéologie le plus proche
            3. 📚 Consulter : "Board Games in Antiquity" (Finkel)
            
            **EXPERTISE REQUISE :**
            • Archéologue spécialisé en jeux antiques
            • Laboratoire de datation (C14 si matière organique)
            • Musée avec collection de jeux antiques
            
            **NOTE :** Seulement ~20 jeux antiques sont bien documentés.
            Votre découverte pourrait être très importante !
            """, shortDesc);
    }

    // ===== ENUM ET CLASSES =====
    public enum IntentType {
        GREETING,
        THEORETICAL_QUESTION,
        GAME_SEARCH,
        ARCHAEOLOGICAL_IDENTIFICATION,
        HALLUCINATION_BLOCKED
    }

    public static class IntentResult {
        private final IntentType type;
        private final String directResponse;

        public IntentResult(IntentType type, String directResponse) {
            this.type = type;
            this.directResponse = directResponse;
        }

        public IntentType getType() { return type; }
        public String getDirectResponse() { return directResponse; }
        public boolean hasDirectResponse() { return directResponse != null; }
    }
}