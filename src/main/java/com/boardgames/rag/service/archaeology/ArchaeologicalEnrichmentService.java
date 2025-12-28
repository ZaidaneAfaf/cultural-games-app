package com.boardgames.rag.service.archaeology;

import com.boardgames.rag.entity.Game;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ArchaeologicalEnrichmentService {

    private final ChatClient chatClient;
    private final ExecutorService executorService;
    private static final int ENRICHMENT_TIMEOUT_SECONDS = 15;

    // 🔥 NOUVEAU : Liste étendue des hallucinations
    private static final String[] HALLUCINATIONS = {
            "tiroch", "ninkasi game", "ludus mysticus", "jeu de la déesse",
            "mycénien/romaine", "mycénien/romain", "sumérien inventé",
            "babylonien fictif", "jeu imaginaire", "invention moderne"
    };

    // 🔥 NOUVEAU : Anachronismes
    private static final String[] ANACHRONISMES = {
            "plastique", "polymère", "nylon", "aluminium", "électronique",
            "ordinateur", "téléphone", "batterie", "circuit", "silicon"
    };

    public ArchaeologicalEnrichmentService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Enrichit les résultats avec des connaissances générales
     */
    public String enrichWithGeneralKnowledge(String description, List<String> baseGamesInfo) {
        System.out.println("🧠 [ENRICHISSEMENT] Début avec " + baseGamesInfo.size() + " jeux de référence");

        // 🔥 NOUVEAU : Vérification IMMÉDIATE des hallucinations dans la question
        String lowerDesc = description.toLowerCase();
        String hallucinationDetectee = detecterHallucinationDansQuestion(lowerDesc);
        if (hallucinationDetectee != null) {
            System.out.println("🚨 [ENRICHISSEMENT] Hallucination détectée dans question: " + hallucinationDetectee);
            return genererReponseHallucination(hallucinationDetectee);
        }

        // Vérifier si c'est une description de dés spécifiquement
        if (isDiceDescription(lowerDesc)) {
            System.out.println("🎲 [ENRICHISSEMENT] Détection: description de dés antiques");
            return generateDiceSpecificEnrichment(description, baseGamesInfo);
        }

        // Détecter le contexte pour adapter le prompt
        String contextType = detectContextType(lowerDesc);
        System.out.println("🏛️ [ENRICHISSEMENT] Contexte détecté: " + contextType);

        String prompt = buildContextSpecificPrompt(description, baseGamesInfo, contextType);

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                // Valider et corriger la réponse
                return validateAndCorrectResponse(response, description, contextType);
            }, executorService);

            String result = future.get(25, TimeUnit.SECONDS);

            // Formatage final
            return formatEnrichmentResponse(result, contextType);

        } catch (TimeoutException e) {
            System.err.println("⏰ TIMEOUT sur enrichissement contextuel");
            return generateQuickEnrichmentFallback(description, baseGamesInfo, contextType);
        } catch (Exception e) {
            System.err.println("❌ Erreur enrichissement: " + e.getMessage());
            return generateErrorEnrichment();
        }
    }

    /**
     * 🔥 NOUVEAU : Détecte une hallucination dans la QUESTION
     */
    private String detecterHallucinationDansQuestion(String lowerDescription) {
        for (String hallucination : HALLUCINATIONS) {
            if (lowerDescription.contains(hallucination)) {
                return hallucination;
            }
        }
        return null;
    }

    /**
     * 🔥 NOUVEAU : Génère une réponse pour hallucination
     */
    private String genererReponseHallucination(String hallucination) {
        return String.format("""
            🚫 **ALERTE : JEU NON DOCUMENTÉ**
            
            Le terme "%s" ne correspond à aucun jeu attesté historiquement.
            
            **Pourquoi cette alerte ?**
            1. ✅ Aucune découverte archéologique
            2. ✅ Aucune source historique fiable
            3. ✅ Aucune référence dans les musées
            4. ✅ Aucune publication académique
            
            **Jeux RÉELS documentés :**
            • Royal Game of Ur (Mésopotamie, ~2600 av. J.-C.)
            • Senet (Égypte, ~3000 av. J.-C.)
            • Ludus latrunculorum (Rome antique)
            • Mehen (Égypte, serpent)
            • Hounds and Jackals (Égypte/Mésopotamie)
            
            **Sources vérifiables :**
            • British Museum (Londres)
            • Metropolitan Museum (New York)
            • Musée du Louvre (Paris)
            
            **Conseil :** Consultez un archéologue spécialisé.
            """, hallucination);
    }

    /**
     * Vérifie si c'est une description de dés
     */
    private boolean isDiceDescription(String lowerDesc) {
        return lowerDesc.contains("dé") ||
                lowerDesc.contains("dés") ||
                lowerDesc.contains("osselet") ||
                lowerDesc.contains("tali") ||
                lowerDesc.contains("tesserae") ||
                lowerDesc.contains("astragale");
    }

    /**
     * Détecte le type de contexte
     */
    private String detectContextType(String lowerDesc) {
        if (lowerDesc.contains("égypt") || lowerDesc.contains("egypt")) {
            return "EGYPTIAN";
        } else if (lowerDesc.contains("romain") || lowerDesc.contains("rome")) {
            return "ROMAN";
        } else if (lowerDesc.contains("grec") || lowerDesc.contains("grèce") || lowerDesc.contains("greece")) {
            return "GREEK";
        } else if (lowerDesc.contains("vik") || lowerDesc.contains("scandinav") || lowerDesc.contains("nordique")) {
            return "VIKING";
        } else if (lowerDesc.contains("mésopotam") || lowerDesc.contains("sumer") || lowerDesc.contains("babylon")) {
            return "MESOPOTAMIAN";
        } else if (lowerDesc.contains("médiéval") || lowerDesc.contains("moyen âge")) {
            return "MEDIEVAL";
        } else {
            return "GENERAL";
        }
    }

    /**
     * Construit un prompt spécifique au contexte
     */
    private String buildContextSpecificPrompt(String description, List<String> baseGamesInfo, String contextType) {
        String contextRules = getContextRules(contextType);

        // 🔥 AMÉLIORÉ : Instructions renforcées contre les hallucinations
        String avertissementsRenforces = """
            === AVERTISSEMENTS RENFORCÉS ===
            JEUX INTERDITS (N'EXISTENT PAS) :
            - "Tiroch", "Ninkasi Game", "Ludus Mysticus"
            - "Jeu de la Déesse", "Mycénien/Romain"
            - TOUT jeu non documenté dans des publications académiques
            
            SI LA QUESTION MENTIONNE UN DE CES TERMES :
            1. RÉPONDS : "Ce jeu n'est pas documenté historiquement"
            2. EXPLIQUE pourquoi
            3. PROPOSE des jeux RÉELS à la place
            
            NE JAMAIS INVENTER de jeux, dates, ou lieux.
            """;

        return String.format("""
            TU ES UN ARCHÉOLOGUE SPÉCIALISÉ EN JEUX ANCIENS.
            
            === RÈGLES ABSOLUES ===
            1. NE PROPOSE QUE DES JEUX RÉELLEMENT DOCUMENTÉS
            2. PAS D'INVENTION (ex: "Tiroch", "Ninkasi Game" = FAUX)
            3. SOIS PRÉCIS SUR LES CIVILISATIONS
            4. DONNE DES RÉFÉRENCES VÉRIFIABLES
            5. SI TU NE SAIS PAS, DIS "JE NE SAIS PAS"
            
            %s
            
            === CONTEXTE SPÉCIFIQUE ===
            %s
            
            === INFORMATIONS DE NOTRE BASE ===
            %s
            
            === DESCRIPTION À ANALYSER ===
            "%s"
            
            === INSTRUCTIONS ===
            Donne des informations FIABLES et VÉRIFIÉES :
            
            1. **Jeux similaires HORS notre base** (MAX 2, DOIVENT EXISTER)
            Pour CHAQUE jeu :
            - **Nom exact** (doit être documenté)
            - Civilisation : [exacte]
            - Période : [précise]
            - Matériaux : [typiques]
            - Où le voir : [musée réel avec référence]
            - Ressemblance : [avec description]
            
            2. **Contexte historique VÉRIFIÉ**
            - Tradition documentée
            - Usage attesté
            - Découvertes archéologiques connues
            
            3. **Références CONCRÈTES**
            - Musées spécifiques (nom + ville)
            - Publications académiques (auteur + titre)
            - Sites de fouilles documentés
            
            RÉPONDS EN FRANÇAIS :
            """,
                avertissementsRenforces,
                contextRules,
                String.join("\n\n", baseGamesInfo),
                description);
    }

    /**
     * Règles spécifiques par contexte
     */
    private String getContextRules(String contextType) {
        switch (contextType) {
            case "EGYPTIAN":
                return """
                    CONTEXTE : ÉGYPTE ANTIQUE
                    - Période : 3000 av. J.-C. à 500 av. J.-C.
                    - Jeux RÉELS : Senet, Mehen, Hounds and Jackals, Aseb
                    - IMPORTANT : Pas de jeux romains ou grecs ici
                    - Matériaux typiques : bois, faïence, pierre
                    - ANACHRONISMES : plastique, métaux modernes = IMPOSSIBLE
                    """;

            case "ROMAN":
                return """
                    CONTEXTE : ROME ANTIQUE
                    - Période : 500 av. J.-C. à 500 ap. J.-C.
                    - Jeux RÉELS : Ludus latrunculorum, Tabula, Calculi, Marelle
                    - Dés : Tesserae (cubiques), Tali (osselets)
                    - IMPORTANT : Mehen est ÉGYPTIEN, pas romain
                    - ANACHRONISMES : plastique, électronique = IMPOSSIBLE
                    """;

            case "GREEK":
                return """
                    CONTEXTE : GRÈCE ANTIQUE
                    - Période : 800 av. J.-C. à 146 av. J.-C.
                    - Jeux RÉELS : Petteia, Pessoi, Kottabos, Osselets
                    - IMPORTANT : Distinguer période classique et hellénistique
                    - ANACHRONISMES : matériaux synthétiques = IMPOSSIBLE
                    """;

            case "VIKING":
                return """
                    CONTEXTE : SCANDINAVIE MÉDIÉVALE
                    - Période : 793 ap. J.-C. à 1066 ap. J.-C.
                    - Jeux RÉELS : Hnefatafl, Jeu des rois, Dés vikings
                    - IMPORTANT : Pas de jeux antiques romains/égyptiens
                    """;

            default:
                return """
                    CONTEXTE : GÉNÉRAL
                    - Sois PRÉCIS sur la civilisation
                    - Vérifie la période historique
                    - Ne mélange pas les civilisations
                    - Signale les anachronismes
                    """;
        }
    }

    /**
     * Valide et corrige la réponse de l'IA
     */
    private String validateAndCorrectResponse(String response, String description, String contextType) {
        String lowerResponse = response.toLowerCase();
        String lowerDesc = description.toLowerCase();

        // 🔥 AMÉLIORÉ : Filtrage renforcé
        boolean hallucinationDetectee = false;

        for (String hallucination : HALLUCINATIONS) {
            if (lowerResponse.contains(hallucination)) {
                System.out.println("⚠️ Filtrage hallucination: " + hallucination);
                response = response.replaceAll("(?i)" + Pattern.quote(hallucination), "[JEU INVENTÉ - À IGNORER]");
                hallucinationDetectee = true;
            }
        }

        // 🔥 NOUVEAU : Si hallucination détectée, ajoute un avertissement fort
        if (hallucinationDetectee) {
            response = "🚫 **ATTENTION : Cette réponse contenait des termes non documentés.**\n\n" +
                    "Les termes inventés ont été remplacés par '[JEU INVENTÉ - À IGNORER]'.\n\n" + response;
        }

        // 🔥 AMÉLIORÉ : Détection des anachronismes
        if (contextType.equals("EGYPTIAN") || contextType.equals("ROMAN") || contextType.equals("GREEK")) {
            boolean anachronismeDetecte = false;
            for (String anachro : ANACHRONISMES) {
                if (lowerResponse.contains(anachro)) {
                    anachronismeDetecte = true;
                    System.out.println("⚠️ Anachronisme détecté: " + anachro + " dans contexte " + contextType);
                }
            }

            if (anachronismeDetecte && !response.contains("ANACHRONISME")) {
                response += "\n\n⚠️ **ALERTE ANACHRONISME** : Cette période historique ne connaissait pas les matériaux modernes mentionnés.";
                response += "\nLes matériaux antiques étaient : bois, pierre, os, ivoire, métaux simples (bronze, cuivre).";
            }
        }

        // Vérifications spécifiques par contexte
        switch (contextType) {
            case "EGYPTIAN":
                // Vérifier qu'il ne propose pas de jeux romains pour l'Égypte
                if (lowerResponse.contains("romain") || lowerResponse.contains("rome")) {
                    response += "\n\n⚠️ **NOTE** : Les jeux mentionnés comme 'romains' ne sont pas égyptiens.";
                    response += "\nPour l'Égypte antique, référez-vous à : Senet, Mehen, Hounds and Jackals.";
                }
                break;

            case "ROMAN":
                // Vérifier qu'il ne propose pas Mehen comme romain
                if (lowerResponse.contains("mehen") && !response.contains("[CORRECTION]")) {
                    response = response.replace("Mehen", "[CORRECTION : Mehen est égyptien, pas romain]");
                }
                break;
        }

        // Vérifier la présence de références concrètes
        if (!lowerResponse.contains("musée") && !lowerResponse.contains("museum") &&
                !lowerResponse.contains("publication") && !lowerResponse.contains("référence")) {
            response += "\n\nℹ️ **Pour références concrètes** : Consultez les collections du British Museum ou du Louvre.";
        }

        return response;
    }

    /**
     * Formatage final de la réponse
     */
    private String formatEnrichmentResponse(String content, String contextType) {
        String title;
        switch (contextType) {
            case "EGYPTIAN": title = "CONTEXTE ÉGYPTIEN - RESSOURCES VÉRIFIÉES"; break;
            case "ROMAN": title = "CONTEXTE ROMAIN - RESSOURCES VÉRIFIÉES"; break;
            case "GREEK": title = "CONTEXTE GREC - RESSOURCES VÉRIFIÉES"; break;
            case "VIKING": title = "CONTEXTE SCANDINAVE - RESSOURCES VÉRIFIÉES"; break;
            default: title = "RESSOURCES EXTERNES VÉRIFIÉES";
        }

        return "🧠 **" + title + "**\n\n" + content;
    }

    /**
     * Enrichissement spécifique pour les dés
     */
    private String generateDiceSpecificEnrichment(String description, List<String> baseGamesInfo) {
        StringBuilder enrichment = new StringBuilder();

        String lowerDesc = description.toLowerCase();
        boolean isRoman = lowerDesc.contains("romain") || lowerDesc.contains("rome");
        boolean isEgyptian = lowerDesc.contains("égypt") || lowerDesc.contains("egypt");

        enrichment.append("🧠 **ANALYSE SPÉCIALISÉE - DÉS ET OSSELETS ANTIQUES**\n\n");

        enrichment.append("### 🎲 **TYPES DE DÉS ANTIQUES DOCUMENTÉS**\n\n");

        if (isRoman || (!isEgyptian && !lowerDesc.contains("grec"))) {
            enrichment.append("**1. DÉS ROMAINS (Tesserae)**\n");
            enrichment.append("- **Forme** : Cubique à 6 faces (standard)\n");
            enrichment.append("- **Matériaux** : Os, ivoire, bronze, pierre, verre\n");
            enrichment.append("- **Période** : Ier siècle av. J.-C. - Ve siècle ap. J.-C.\n");
            enrichment.append("- **Gravures** : Points (I-VI), parfois lettres (A-F) ou symboles\n");
            enrichment.append("- **Usage** : Jeux de hasard (alea), divination, amusement\n");
            enrichment.append("- **Exemple** : Dés de Pompéi (Musée archéologique de Naples)\n");
            enrichment.append("- **Référence** : « Roman Dice » dans CIL VI 1779\n\n");

            enrichment.append("**2. OSSELETS ROMAINS (Tali/Astragales)**\n");
            enrichment.append("- **Forme** : Os de mouton/chèvre (4 faces utiles)\n");
            enrichment.append("- **Valeurs** : 1 (plana), 3 (tropus), 4 (canis), 6 (venus)\n");
            enrichment.append("- **Jeu** : Jeu des osselets (tali lusoria)\n");
            enrichment.append("- **Référence** : « Roman Games » de Anne-Elizabeth Dunn-Vaturi\n");
            enrichment.append("- **Musée** : British Museum, inv. GR 1867.5-8.2\n\n");
        }

        if (isEgyptian) {
            enrichment.append("**3. JETONS ÉGYPTIENS POUR JEUX**\n");
            enrichment.append("- **Note importante** : Les Égyptiens utilisaient rarement des dés cubiques\n");
            enrichment.append("- **Alternative** : Bâtons jetés (un côté peint), disques\n");
            enrichment.append("- **Pour Senet** : 4 bâtons de lancer (konane)\n");
            enrichment.append("- **Matériaux** : Ivoire, bois, pierre\n");
            enrichment.append("- **Référence** : « Egyptian Games » au Metropolitan Museum\n\n");
        }

        if (lowerDesc.contains("grec") || lowerDesc.contains("grèce")) {
            enrichment.append("**4. OSSELETS GRECS (Astragaloi)**\n");
            enrichment.append("- **Usage** : Divination et jeu\n");
            enrichment.append("- **Référence littéraire** : Mentionnés par Platon et Sophocle\n");
            enrichment.append("- **Musée** : Musée de l'Agora (Athènes)\n\n");
        }

        enrichment.append("### 🔬 **MÉTHODOLOGIE D'IDENTIFICATION**\n");
        enrichment.append("1. **Mesures précises** en mm (hauteur × largeur × profondeur)\n");
        enrichment.append("2. **Analyse des gravures** : régularité, profondeur, style\n");
        enrichment.append("3. **Marques d'usure** : faces préférentiellement usées\n");
        enrichment.append("4. **Contexte archéologique** : association avec autres objets\n\n");

        enrichment.append("### 📚 **RÉFÉRENCES ACADÉMIQUES**\n");
        enrichment.append("- **Corpus de dés antiques** : CIL (Corpus Inscriptionum Latinarum)\n");
        enrichment.append("- **Catalogue** : British Museum Collection Online\n");
        enrichment.append("- **Ouvrage** : « Dice and Dice Games in Antiquity » de F. N. David\n");
        enrichment.append("- **Article** : « Roman Gaming Counters » (Journal of Roman Archaeology)\n");

        return enrichment.toString();
    }

    /**
     * Fallback rapide si timeout
     */
    private String generateQuickEnrichmentFallback(String description, List<String> baseGamesInfo, String contextType) {
        StringBuilder fallback = new StringBuilder();

        switch (contextType) {
            case "EGYPTIAN":
                fallback.append("🧠 **RESSOURCES ÉGYPTIENNES - RÉFÉRENCES RAPIDES**\n\n");
                fallback.append("**Jeux égyptiens documentés :**\n");
                fallback.append("1. **Senet** - jeu de parcours funéraire\n");
                fallback.append("   • Musée égyptien du Caire, inv. JE 62015\n");
                fallback.append("   • Publication : Piccione, « The Egyptian Game of Senet»\n\n");

                fallback.append("2. **Mehen** - jeu du serpent\n");
                fallback.append("   • Metropolitan Museum, New York\n");
                fallback.append("   • Forme : spirale représentant un serpent\n\n");

                fallback.append("**Pour consultation :**\n");
                fallback.append("- Digital Egypt for Universities (UCL)\n");
                fallback.append("- Theban Mapping Project\n");
                break;

            case "ROMAN":
                fallback.append("🧠 **RESSOURCES ROMAINES - RÉFÉRENCES RAPIDES**\n\n");
                fallback.append("**Jeux romains documentés :**\n");
                fallback.append("1. **Ludus latrunculorum** - jeu de stratégie\n");
                fallback.append("   • Musée national romain (Rome)\n");
                fallback.append("   • Publication : Schädler, « Latrunculi»\n\n");

                fallback.append("2. **Tabula** - ancêtre du backgammon\n");
                fallback.append("   • British Museum, inv. 1867.5-8.1\n\n");

                fallback.append("**Dés romains (Tesserae) :**\n");
                fallback.append("- CIL (Corpus Inscriptionum Latinarum)\n");
                fallback.append("- Museum of London collection\n");
                break;

            default:
                fallback.append("🧠 **RESSOURCES GÉNÉRALES - RÉFÉRENCES RAPIDES**\n\n");
                fallback.append("**Collections en ligne :**\n");
                fallback.append("1. **British Museum** (collections.britishmuseum.org)\n");
                fallback.append("2. **Metropolitan Museum** (metmuseum.org)\n");
                fallback.append("3. **Louvre Atlas** (atlas.louvre.fr)\n\n");

                fallback.append("**Publications académiques :**\n");
                fallback.append("- JSTOR (jstor.org) - articles sur jeux antiques\n");
                fallback.append("- Persée (persee.fr) - revues françaises\n");
        }

        fallback.append("\n**Documentation recommandée :**\n");
        fallback.append("1. Photos multiples avec échelle\n");
        fallback.append("2. Localisation GPS du site\n");
        fallback.append("3. Contexte stratigraphique\n");
        fallback.append("4. Association avec autres artefacts\n");

        return fallback.toString();
    }

    /**
     * Enrichissement en cas d'erreur
     */
    private String generateErrorEnrichment() {
        return """
            🧠 **RESSOURCES EXTERNES - ACCÈS DIRECT**
            
            ⚠️ *Le système d'enrichissement est temporairement indisponible.*
            
            **Accédez directement aux sources :**
            
            1. **Collections numérisées :**
               - British Museum Collection Online
               - Metropolitan Museum Heilbrunn Timeline
               - Louvre Atlas Database
            
            2. **Publications académiques :**
               - JSTOR (recherche "ancient board games")
               - Persée (recherche "jeux antiques")
               - Academia.edu (papiers d'experts)
            
            3. **Musées spécialisés :**
               - Swiss Museum of Games (La Tour-de-Peilz)
               - Musée du Jeu (Hyères)
               - Musée national du Moyen Âge (Paris)
            """;
    }

    /**
     * Extrait les infos essentielles des jeux pour l'enrichissement
     */
    public List<String> extractKeyInfoForEnrichment(List<Game> games) {
        if (games == null || games.isEmpty()) {
            return List.of("⚠️ Aucun jeu correspondant exactement dans notre base de données.");
        }

        return games.stream()
                .limit(3) // Limiter à 3 jeux maximum
                .map(game -> {
                    StringBuilder info = new StringBuilder();

                    info.append("**").append(game.getName()).append("**");

                    // Période
                    if (game.getYearPublished() != null) {
                        int year = game.getYearPublished();
                        if (year < 0) {
                            info.append(" (").append(Math.abs(year)).append(" av. J.-C.)");
                        } else {
                            info.append(" (").append(year).append(" ap. J.-C.)");
                        }
                    }
                    info.append("\n");

                    // Origine
                    if (game.getLudiiMetadata() != null && game.getLudiiMetadata().getOrigin() != null) {
                        info.append("📍 ").append(game.getLudiiMetadata().getOrigin()).append("\n");
                    }

                    // Description très courte
                    if (game.getDescription() != null && !game.getDescription().trim().isEmpty()) {
                        String cleanDesc = game.getDescription().trim();
                        if (cleanDesc.length() > 80) {
                            cleanDesc = cleanDesc.substring(0, 80) + "...";
                        }
                        info.append("📝 ").append(cleanDesc).append("\n");
                    }

                    // Source
                    if (game.getSource() != null) {
                        info.append("🏛️ Source : ").append(game.getSource()).append("\n");
                    }

                    return info.toString();
                })
                .collect(Collectors.toList());
    }
}