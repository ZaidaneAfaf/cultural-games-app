package com.boardgames.rag.service.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OllamaResponseService {

    private final ChatClient chatClient;

    // 🔥 Configuration des timeouts
    @Value("${app.prompting.timeout.zero-shot:20}")
    private int zeroShotTimeout;

    @Value("${app.prompting.timeout.few-shot:25}")
    private int fewShotTimeout;

    @Value("${app.prompting.timeout.chain-of-thought:30}")
    private int chainOfThoughtTimeout;

    @Value("${app.prompting.timeout.self-consistency:35}")
    private int selfConsistencyTimeout;

    @Value("${app.prompting.timeout.meta-prompting:30}")
    private int metaPromptingTimeout;

    @Value("${app.prompting.templates.few-shot-examples:3}")
    private int fewShotExamples;

    @Value("${app.prompting.strategies.enabled:true}")
    private boolean strategiesEnabled;

    public OllamaResponseService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostConstruct
    public void init() {
        System.out.println("🤖 OllamaResponseService initialisé");
        System.out.println("⏱️  Timeouts configurés:");
        System.out.println("   - Zero-Shot: " + zeroShotTimeout + "s");
        System.out.println("   - Few-Shot: " + fewShotTimeout + "s");
        System.out.println("   - Chain-of-Thought: " + chainOfThoughtTimeout + "s");
        System.out.println("   - Self-Consistency: " + selfConsistencyTimeout + "s");
        System.out.println("   - Meta-Prompting: " + metaPromptingTimeout + "s");
        System.out.println("   - Stratégies activées: " + strategiesEnabled);
    }

    /**
     * 🔥 GÉNÉRATION RAPIDE AVEC CONTEXTE JEUX
     */
    public String generateQuickResponse(String question, List<Map<String, Object>> gamesContext) {
        System.out.println("🤖 [OLLAMA] Génération Zero-Shot avec " + gamesContext.size() + " jeux");

        List<Map<String, Object>> limitedGames = gamesContext.stream()
                .limit(3)
                .collect(Collectors.toList());

        String contextText = limitedGames.stream()
                .map(this::formatGameForPrompt)
                .collect(Collectors.joining("\n\n"));

        String questionType = analyzeQuestionType(question);
        String prompt = buildAdaptivePrompt(question, contextText, questionType, limitedGames.size());

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                System.err.println("⚠️ [OLLAMA] Réponse vide reçue");
                return null;
            }

            System.out.println("✅ [OLLAMA] Réponse générée (" + response.length() + " caractères)");
            return response;

        } catch (Exception e) {
            System.err.println("❌ [OLLAMA] Erreur : " + e.getMessage());
            throw new RuntimeException("Erreur génération Ollama", e);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC FEW-SHOT PROMPTING
     */
    public String generateWithFewShotPrompting(String question, List<Map<String, Object>> gamesContext) {
        System.out.println("🎯 [OLLAMA-FEW-SHOT] Génération avec " + fewShotExamples + " exemples");

        String contextText = gamesContext.stream()
                .limit(2)
                .map(this::formatGameForPrompt)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildFewShotPrompt(question, contextText);

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("❌ [OLLAMA-FEW-SHOT] Erreur: " + e.getMessage());
            return generateQuickResponse(question, gamesContext);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC CHAIN-OF-THOUGHT
     */
    public String generateWithChainOfThought(String question, List<Map<String, Object>> gamesContext) {
        System.out.println("🧠 [OLLAMA-COT] Génération avec raisonnement pas-à-pas");

        String contextText = gamesContext.stream()
                .limit(2)
                .map(this::formatGameForPrompt)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildChainOfThoughtPrompt(question, contextText);

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("❌ [OLLAMA-COT] Erreur: " + e.getMessage());
            return generateQuickResponse(question, gamesContext);
        }
    }

    /**
     * 🔥 GÉNÉRATION AVEC SELF-CONSISTENCY
     */
    public String generateWithSelfConsistency(String question, List<Map<String, Object>> gamesContext) {
        System.out.println("🔄 [OLLAMA-SC] Génération avec auto-cohérence");

        String contextText = gamesContext.stream()
                .limit(2)
                .map(this::formatGameForPrompt)
                .collect(Collectors.joining("\n\n"));

        String prompt = buildSelfConsistencyPrompt(question, contextText);

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("❌ [OLLAMA-SC] Erreur: " + e.getMessage());
            return generateQuickResponse(question, gamesContext);
        }
    }

    /**
     * 🔥 ANALYSE TYPE DE QUESTION
     */
    private String analyzeQuestionType(String question) {
        String lower = question.toLowerCase();

        if (lower.contains("règles") || lower.contains("comment jouer") || lower.contains("comment se joue")) {
            return "RULES";
        }
        if (lower.contains("meilleurs") || lower.contains("top") || lower.contains("recommand")) {
            return "RECOMMENDATION";
        }
        if (lower.contains("différence") || lower.contains("compar") || lower.contains("vs") || lower.contains("entre")) {
            return "COMPARISON";
        }
        if (lower.contains("histoire") || lower.contains("origine") || lower.contains("quand") || lower.contains("où")) {
            return "HISTORY";
        }
        if (lower.contains("antique") || lower.contains("ancien") || lower.contains("romain") ||
                lower.contains("égypt") || lower.contains("grec") || lower.contains("viking")) {
            return "ARCHAEOLOGICAL";
        }

        return "GENERAL";
    }

    /**
     * 🔥 CONSTRUCTION PROMPT ZERO-SHOT ADAPTATIF
     */
    private String buildAdaptivePrompt(String question, String contextText, String questionType, int gameCount) {
        String baseInstruction = """
            Tu es un expert en jeux de société, avec un style naturel et conversationnel.
            Ton ton est amical, enthousiaste et professionnel, comme ChatGPT.
            """;

        String specificInstruction = switch (questionType) {
            case "RULES" -> """
                L'utilisateur demande les règles d'un jeu.
                Explique de manière claire et structurée.
                """;

            case "RECOMMENDATION" -> String.format("""
                L'utilisateur cherche des recommandations.
                %d jeu(x) trouvé(s). Présente-les avec enthousiasme.
                """, gameCount);

            case "COMPARISON" -> """
                L'utilisateur compare des jeux.
                Sois objectif et aide à choisir selon les préférences.
                """;

            case "HISTORY" -> """
                L'utilisateur s'intéresse à l'histoire du jeu.
                Adopte un ton narratif captivant.
                """;

            case "ARCHAEOLOGICAL" -> """
                L'utilisateur s'intéresse aux jeux antiques.
                Sois précis et éducatif.
                """;

            default -> String.format("""
                L'utilisateur pose une question générale.
                %d jeu(x) trouvé(s) dans la base.
                """, gameCount);
        };

        return String.format("""
            %s
            
            %s
            
            Question: "%s"
            
            Jeux trouvés:
            %s
            
            Réponds EN FRANÇAIS, de manière naturelle (150-250 mots).
            Base ta réponse UNIQUEMENT sur les jeux fournis.
            """, baseInstruction, specificInstruction, question, contextText);
    }

    /**
     * 🔥 CONSTRUCTION FEW-SHOT PROMPT
     */
    private String buildFewShotPrompt(String question, String contextText) {
        return String.format("""
            Tu es un expert en jeux de société. Voici des exemples de réponses de qualité:
            
            --- EXEMPLE 1 ---
            Q: "Quels sont les meilleurs jeux de stratégie ?"
            C: Catan (1995), 3-4 joueurs, complexité 2.3/5, note 7.2/10
            R: "Pour les jeux de stratégie, je recommande **Catan** (1995). C'est un jeu de colonisation et d'échange qui a révolutionné les jeux modernes."
            
            --- EXEMPLE 2 ---
            Q: "Comment jouer à Carcassonne ?"
            C: Carcassonne (2000), 2-5 joueurs, complexité 1.9/5
            R: "Carcassonne est un jeu de placement de tuiles très accessible ! Le but est de construire des villes, routes et monastères pour marquer des points."
            
            --- EXEMPLE 3 ---
            Q: "Quelle est la différence entre Pandemic et Spirit Island ?"
            C: Pandemic (2008), coopératif, 2.4/5 | Spirit Island (2017), coopératif, 4.0/5
            R: "Les deux sont excellents mais différents ! **Pandemic** est plus accessible (2.4/5). **Spirit Island** est plus complexe (4.0/5)."
            
            --- TA TÂCHE ---
            Question: "%s"
            
            Contexte:
            %s
            
            Génère une réponse de qualité similaire aux exemples.
            Réponds EN FRANÇAIS, en 150-250 mots.
            """, question, contextText);
    }

    /**
     * 🔥 CONSTRUCTION CHAIN-OF-THOUGHT PROMPT
     */
    private String buildChainOfThoughtPrompt(String question, String contextText) {
        return String.format("""
            Question: "%s"
            
            Contexte:
            %s
            
            **ÉTAPE 1 - COMPRÉHENSION** : Analyse la question.
            - Que cherche l'utilisateur ?
            - Quels critères sont importants ?
            
            **ÉTAPE 2 - CONTEXTE** : Examine les jeux disponibles.
            - Quels jeux correspondent ?
            - Quelles sont leurs caractéristiques ?
            
            **ÉTAPE 3 - STRUCTURATION** : Organise la réponse.
            - Comment présenter l'information ?
            - Quels points mettre en avant ?
            
            **ÉTAPE 4 - FORMULATION** : Rédige la réponse.
            - Sois naturel et conversationnel
            - Utilise les détails du contexte
            
            **RÉPONSE FINALE (en français) :**
            """, question, contextText);
    }

    /**
     * 🔥 CONSTRUCTION SELF-CONSISTENCY PROMPT
     */
    private String buildSelfConsistencyPrompt(String question, String contextText) {
        return String.format("""
            Question: "%s"
            
            Contexte:
            %s
            
            **INSTRUCTIONS** :
            1. Génère 2 versions différentes de la réponse
            2. Évalue chaque version (Exactitude/Clarté/Utilité)
            3. Sélectionne la meilleure
            4. Améliore-la si nécessaire
            
            **RÉPONSE FINALE (en français, 150-250 mots) :**
            """, question, contextText);
    }

    /**
     * 🔥 GÉNÉRATION DEPUIS PROMPT LIBRE
     */
    public String generateFromPrompt(String prompt) {
        System.out.println("🤖 [OLLAMA] Génération depuis prompt libre");

        String fullPrompt = """
            Tu es un expert en jeux de société.
            Réponds TOUJOURS en français, de manière claire et engageante.
            Ton ton est amical, enthousiaste mais professionnel.
            
            """ + prompt;

        try {
            String response = chatClient.prompt()
                    .user(fullPrompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                System.err.println("⚠️ [OLLAMA] Réponse vide reçue");
                return null;
            }

            System.out.println("✅ [OLLAMA] Réponse générée (" + response.length() + " caractères)");
            return response;

        } catch (Exception e) {
            System.err.println("❌ [OLLAMA] Erreur : " + e.getMessage());
            throw new RuntimeException("Erreur génération Ollama", e);
        }
    }

    /**
     * 🔥 FORMATAGE JEU POUR PROMPT
     */
    private String formatGameForPrompt(Map<String, Object> game) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎮 ").append(game.get("name"));

        if (game.get("yearPublished") != null) {
            sb.append(" (").append(game.get("yearPublished")).append(")");
        }
        sb.append("\n");

        if (game.get("averageRating") != null) {
            Double rating = (Double) game.get("averageRating");
            sb.append("  ⭐ Note: ").append(String.format("%.1f/10", rating)).append("\n");
        }

        if (game.get("complexityWeight") != null) {
            Double complexity = (Double) game.get("complexityWeight");
            String level = complexity <= 2.0 ? "Simple" :
                    complexity <= 3.5 ? "Moyen" : "Complexe";
            sb.append("  🎲 Complexité: ").append(String.format("%.1f/5", complexity))
                    .append(" (").append(level).append(")\n");
        }

        if (game.get("minPlayers") != null && game.get("maxPlayers") != null) {
            sb.append("  👥 Joueurs: ").append(game.get("minPlayers"))
                    .append("-").append(game.get("maxPlayers")).append("\n");
        }

        if (game.get("origin") != null) {
            sb.append("  🏛️ Origine: ").append(game.get("origin")).append("\n");
        }

        if (game.get("description") != null) {
            String desc = game.get("description").toString();
            if (desc.length() > 200) {
                desc = desc.substring(0, 197) + "...";
            }
            sb.append("  📝 ").append(desc).append("\n");
        }

        return sb.toString();
    }

    /**
     * 🔥 MÉTHODE UNIFIÉE AVEC STRATÉGIE
     */
    public String generateResponseWithStrategy(String question, List<Map<String, Object>> gamesContext,
                                               String promptingStrategy) {
        if (!strategiesEnabled) {
            System.out.println("⚠️ Stratégies désactivées → Zero-Shot");
            return generateQuickResponse(question, gamesContext);
        }

        System.out.println("🎯 Utilisation de la stratégie: " + promptingStrategy);

        switch (promptingStrategy.toUpperCase()) {
            case "FEW_SHOT":
                return generateWithFewShotPrompting(question, gamesContext);
            case "CHAIN_OF_THOUGHT":
            case "COT":
                return generateWithChainOfThought(question, gamesContext);
            case "SELF_CONSISTENCY":
            case "SC":
                return generateWithSelfConsistency(question, gamesContext);
            default:
                return generateQuickResponse(question, gamesContext);
        }
    }
}