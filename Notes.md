📋 Étapes de Développement du Système RAG Jeux de Société
🎯 Objectif du Projet
Créer un système RAG (Retrieval-Augmented Generation) permettant de découvrir les jeux de société à travers les époques et cultures, en utilisant BoardGameGeek, Ludii et Wikipedia comme sources de données.

🔧 Étapes Techniques Détaillées
1. Configuration de l'Environnement
Initialisation d'un projet Spring Boot avec les dépendances nécessaires

Configuration de MongoDB comme base de données principale

Configuration de Qdrant comme base de données vectorielle

Intégration d'Ollama pour le traitement du langage naturel

2. Modélisation des Données
Création d'un modèle unifié Game pour représenter tous les jeux

Définition des métadonnées spécifiques à chaque source (BGG, Ludii)

Ajout des règles de jeu (rulesets) et informations historiques

Gestion des catégories, périodes et origines culturelles

3. Import et Prétraitement des Données
Sources de données :
BoardGameGeek (BGG) : Jeux modernes avec notations et complexité

Ludii : Jeux traditionnels et historiques avec contexte culturel

Wikipedia : Informations historiques et culturelles enrichies

Processus d'import :
Développement de services d'import CSV pour chaque source

Nettoyage et normalisation des données

Fusion des données provenant de sources multiples

Gestion des doublons et incohérences

4. Système d'Embedding et Recherche Vectorielle
Intégration du service d'embedding avec le modèle all-minilm:l6

Création des embeddings pour chaque jeu basés sur :

Nom et description

Catégories et mécanismes

Contexte historique et culturel

Indexation des embeddings dans Qdrant pour une recherche rapide

5. Architecture du Système RAG
Composants principaux :
GameController : API REST pour la gestion des jeux

RagController : Point d'entrée pour les requêtes conversationnelles

RagService : Cœur du système RAG avec logique de recherche hybride

VectorStoreService : Gestion de la recherche vectorielle

6. Algorithme de Recherche Hybride
Étape 1 : Recherche Directe
Détection des recherches spécifiques par nom de jeu

Utilisation des index MongoDB pour une réponse ultra-rapide

Filtrage intelligent des résultats (meilleures notes, noms exacts)

Étape 2 : Recherche Vectorielle
Activation si la recherche directe ne donne pas de résultats

Recherche par similarité sémantique dans Qdrant

Limitation à 3 résultats maximum pour la performance

Étape 3 : Enrichissement Contextuel
Récupération asynchrone des informations Wikipedia

Agrégation des données de toutes les sources

Préparation du contexte pour la génération de réponse

7. Génération de Réponses Naturelles
Intégration avec Ollama et le modèle gemma2:2b

Création de prompts contextuels incluant :

Informations des jeux trouvés

Contexte historique et règles

Recommandations personnalisées

Formatage des réponses en style conversationnel

8. Optimisations des Performances
Cache et Mémoire :
Implémentation d'un cache en mémoire pour les réponses fréquentes

Gestion des timeouts pour les appels externes

Nettoyage automatique du cache

Recherche Intelligente :
Priorisation des recherches par nom exact

Limitation du nombre de résultats vectoriels

Timeout court pour les appels Wikipedia (2 secondes)
