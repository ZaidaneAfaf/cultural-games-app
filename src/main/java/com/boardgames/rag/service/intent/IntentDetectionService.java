package com.boardgames.rag.service.intent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class IntentDetectionService {

    private final ChatClient chatClient;
    private final ExecutorService executorService;

    public IntentDetectionService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Détecte l'intention de l'utilisateur
     */
    public IntentResult detectIntent(String userMessage) {
        String lowerMessage = userMessage.toLowerCase().trim();

        // 1. SALUTATIONS ET CONVERSATIONS COURANTES
        if (isGreetingOrSmallTalk(lowerMessage)) {
            return new IntentResult(
                    IntentType.GREETING,
                    generateConversationalResponse(lowerMessage)
            );
        }

        // 2. QUESTIONS GÉNÉRALES (pas de jeu spécifique)
        if (isGeneralQuestion(lowerMessage)) {
            return new IntentResult(
                    IntentType.GENERAL_QUESTION,
                    null
            );
        }

        // 3. RECHERCHE DE JEU (par défaut)
        return new IntentResult(IntentType.GAME_SEARCH, null);
    }

    /**
     * Détecte si c'est une salutation ou petite conversation
     */
    private boolean isGreetingOrSmallTalk(String message) {
        // Salutations simples
        String[] greetings = {
                "bonjour", "bonsoir", "salut", "hello", "hi", "hey",
                "coucou", "bonne journée", "bonne soirée", "yo"
        };

        for (String greeting : greetings) {
            if (message.equals(greeting) ||
                    message.startsWith(greeting + " ") ||
                    message.startsWith(greeting + "!") ||
                    message.startsWith(greeting + ".") ||
                    message.startsWith(greeting + ",")) {
                return true;
            }
        }

        // Small talk (conversations courantes)
        String[] smallTalkPatterns = {
                "tu vas bien", "ça va", "ca va", "comment vas-tu", "comment allez-vous",
                "tu vas bien?", "ça va?", "ca va?", "comment ça va", "comment ca va",
                "quoi de neuf", "comment tu vas", "tu fais quoi",
                "merci", "merci beaucoup", "super", "cool", "ok", "d'accord",
                "au revoir", "à bientôt", "bye", "ciao", "à plus"
        };

        for (String pattern : smallTalkPatterns) {
            if (message.equals(pattern) ||
                    message.contains(pattern + "?") ||
                    message.contains(pattern + " ")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Détecte si c'est une question générale
     */
    private boolean isGeneralQuestion(String message) {
        // Pattern 1: Questions commençant par des mots interrogatifs
        String[] questionStarters = {
                "comment", "pourquoi", "quelle", "quel", "quels", "quelles",
                "qu'est-ce", "qu'est ce", "qui", "où", "quand", "combien"
        };

        for (String starter : questionStarters) {
            if (message.startsWith(starter + " ")) {
                // Vérifier qu'il n'y a PAS de nom de jeu spécifique juste après
                if (!containsSpecificGameNameNearStart(message)) {
                    return true;
                }
            }
        }

        // Pattern 2: Phrases contenant des mots-clés de questions générales
        String[] generalKeywords = {
                "relation entre", "différence entre", "lien entre",
                "histoire des jeux", "origine des jeux", "évolution des jeux",
                "types de jeux", "catégories de jeux", "classification",
                "influence", "impact", "rôle", "importance",
                "peux-tu", "peux tu", "pourrais-tu", "peux-tu me",
                "explique-moi", "dis-moi", "parle-moi", "raconte-moi"
        };

        for (String keyword : generalKeywords) {
            if (message.contains(keyword)) {
                // Si la question parle de jeux en général (pas d'un jeu spécifique)
                if (!containsSpecificGameName(message) ||
                        isAboutGamesInGeneral(message)) {
                    return true;
                }
            }
        }

        // Pattern 3: Questions avec "?" à la fin et pas de nom de jeu spécifique
        if (message.endsWith("?") && message.length() > 20) {
            if (!containsSpecificGameName(message)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si la question parle des jeux en général
     */
    private boolean isAboutGamesInGeneral(String message) {
        String[] generalGameTerms = {
                "jeux de société", "jeux de plateau", "jeux de cartes",
                "jeux vidéo", "les jeux", "ces jeux", "certains jeux",
                "jeux modernes", "jeux anciens", "jeux classiques",
                "histoire du jeu", "origine du jeu", "évolution du jeu"
        };

        for (String term : generalGameTerms) {
            if (message.contains(term)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si un nom de jeu spécifique est près du début (20 premiers chars)
     */
    private boolean containsSpecificGameNameNearStart(String message) {
        if (message.length() < 20) {
            return containsSpecificGameName(message);
        }

        String start = message.substring(0, Math.min(30, message.length()));
        return containsSpecificGameName(start);
    }

    /**
     * Vérifie si le message contient un nom de jeu spécifique
     */
    private boolean containsSpecificGameName(String message) {
        String[] commonGames = {
                " chess ", " échecs ", " go ", " senet ", " monopoly ", " scrabble ",
                " catan ", " risk ", " cluedo ", " azul ", " ticket to ride ",
                " pandemic ", " 7 wonders ", " splendor ", " dominion ",
                " uno ", " poker ", " bridge ", " tarot ", " belote "
        };

        // Ajouter des espaces pour éviter les faux positifs
        String spacedMessage = " " + message + " ";

        for (String game : commonGames) {
            if (spacedMessage.contains(game)) {
                // Vérifier si c'est utilisé dans un contexte général
                // Ex: "l'histoire du jeu d'échecs" vs "les règles des échecs"
                if (isUsedInGeneralContext(message, game.trim())) {
                    return false; // Pas considéré comme recherche spécifique
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si le jeu est mentionné dans un contexte général/théorique
     */
    private boolean isUsedInGeneralContext(String message, String gameName) {
        // Si la phrase parle de l'histoire, de l'origine, de l'influence du jeu
        // C'est une question théorique, pas une recherche de jeu
        String[] theoreticalContexts = {
                "histoire de", "histoire du", "origine de", "origine du",
                "évolution de", "évolution du", "influence de", "influence du",
                "impact de", "impact du", "rôle de", "rôle du",
                "comment l'", "comment le", "pourquoi le", "pourquoi l'"
        };

        for (String context : theoreticalContexts) {
            if (message.contains(context)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Génère une réponse conversationnelle adaptée
     */
    private String generateConversationalResponse(String message) {
        message = message.toLowerCase().trim();

        // Réponses pour "tu vas bien?" et variantes
        if (message.contains("tu vas bien") || message.contains("ça va") || message.contains("ca va") ||
                message.contains("comment vas-tu") || message.contains("comment allez-vous")) {
            String[] responses = {
                    "Je vais très bien, merci ! 😊 Prêt à vous aider à découvrir des jeux passionnants. Et vous ?",
                    "Ça va super bien ! 🎲 J'ai hâte de vous parler de jeux de société. Que puis-je faire pour vous ?",
                    "Parfaitement bien, merci de demander ! 🎯 Vous cherchez des infos sur un jeu en particulier ?"
            };
            return responses[ThreadLocalRandom.current().nextInt(responses.length)];
        }

        // Réponses pour "merci"
        if (message.contains("merci")) {
            String[] responses = {
                    "Avec plaisir ! 😊 N'hésitez pas si vous avez d'autres questions.",
                    "De rien ! 🎲 Je suis là pour ça.",
                    "Content d'avoir pu vous aider ! 🎯 À votre service pour toute autre question."
            };
            return responses[ThreadLocalRandom.current().nextInt(responses.length)];
        }

        // Réponses pour "au revoir"
        if (message.contains("au revoir") || message.contains("bye") || message.contains("à bientôt") ||
                message.contains("ciao") || message.contains("à plus")) {
            String[] responses = {
                    "Au revoir ! 👋 À très bientôt pour de nouvelles découvertes ludiques !",
                    "À bientôt ! 🎲 N'hésitez pas à revenir quand vous voulez.",
                    "Bonne journée ! 🎯 À la prochaine pour parler jeux de société !"
            };
            return responses[ThreadLocalRandom.current().nextInt(responses.length)];
        }

        // Salutations par défaut
        String[] defaultResponses = {
                "Bonjour ! 👋 Je suis votre assistant spécialisé en jeux de société. Comment puis-je vous aider aujourd'hui ?",
                "Salut ! 🎲 Ravi de vous voir. Vous cherchez des informations sur un jeu en particulier ?",
                "Hello ! 🎯 Prêt à découvrir de nouveaux jeux de société ? Que puis-je faire pour vous ?"
        };

        return defaultResponses[ThreadLocalRandom.current().nextInt(defaultResponses.length)];
    }

    /**
     * Génère une réponse pour une question générale
     */
    public String generateGeneralResponse(String question) {
        System.out.println("💬 [QUESTION GÉNÉRALE] Génération réponse...");

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return askOllamaGeneral(question);
            }, executorService);

            String response = future.get(10, TimeUnit.SECONDS);
            System.out.println("✅ [QUESTION GÉNÉRALE] Réponse générée");
            return response;

        } catch (TimeoutException e) {
            System.err.println("⏰ TIMEOUT sur question générale");
            return "Je réfléchis trop longtemps... Pouvez-vous reformuler votre question ?";
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            return "Désolé, j'ai du mal à répondre à cette question. Pouvez-vous la reformuler ?";
        }
    }

    /**
     * Demande à Ollama de répondre à une question générale
     */
    private String askOllamaGeneral(String question) {
        String prompt = String.format("""
            Tu es un expert en jeux de société qui répond de manière conversationnelle.
            
            Question de l'utilisateur : "%s"
            
            INSTRUCTIONS :
            - Réponds EN FRANÇAIS de manière naturelle et engageante
            - Si c'est une question sur les jeux en général, donne une réponse informative
            - Sois conversationnel, amical et précis
            - 2-3 paragraphes maximum
            - Si tu ne sais pas, sois honnête
            
            RÉPONDS DIRECTEMENT EN FRANÇAIS :
            """, question);

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new RuntimeException("Ollama error: " + e.getMessage());
        }
    }

    /**
     * Type d'intention
     */
    public enum IntentType {
        GREETING,
        GENERAL_QUESTION,
        GAME_SEARCH
    }

    /**
     * Résultat de la détection d'intention
     */
    public static class IntentResult {
        private final IntentType type;
        private final String directResponse;

        public IntentResult(IntentType type, String directResponse) {
            this.type = type;
            this.directResponse = directResponse;
        }

        public IntentType getType() {
            return type;
        }

        public String getDirectResponse() {
            return directResponse;
        }

        public boolean hasDirectResponse() {
            return directResponse != null;
        }
    }
}