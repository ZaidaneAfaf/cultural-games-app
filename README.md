# GameBoard Genius – RAG Board Games Assistant

GameBoard Genius est une application intelligente basée sur **RAG (Retrieval Augmented Generation)** permettant de poser des questions sur les jeux de société et d'obtenir des réponses enrichies grâce à une base vectorielle.

Le système utilise plusieurs technologies modernes :

- **Qdrant** → Base de données vectorielle pour la recherche sémantique
- **Embeddings** → Transformation des textes en vecteurs
- **GROQ LLM** → Génération intelligente de réponses
- **Wikipedia API** → Enrichissement du contexte
- **Docker Compose** → Orchestration des services
# Etapes pour demarer le projet:

# 1. Récupérer une clé API GROQ

1. Aller sur le site : https://console.groq.com/keys
  
2. Créer une nouvelle **API Key**

3. Copier la clé générée.

---

#  2. Configuration des variables d'environnement

Dans le projet, copier le fichier : .env.example vers .env

Puis modifier les variables suivantes dans le fichier `.env` :

```env
GROQ_API_KEY=YOUR_KEY_HERE
WIKI_USER_AGENT=projetS5/1.0 (contact@example.com)
```
# 3. Lancer le projet
Depuis la racine du projet, exécuter la commande suivante :
````
docker compose up -d --build
````
# 4. Restaurer la base vectorielle (Snapshot Qdrant)

Le projet utilise un **snapshot Qdrant contenant les embeddings des jeux de société**.

### Télécharger le snapshot

Télécharger le snapshot compressé :

👉 [Download Snapshot](https://github.com/ZaidaneAfaf/cultural-games-app/archive/refs/tags/v1.0.zip)

Après téléchargement, **décompresser le fichier** pour obtenir :
boardgames.snapshot

---

##  Importer le snapshot dans Qdrant

Ouvrir le dashboard Qdrant :
http://localhost:6333/dashboard#/collections/

Puis suivre les étapes :

1. Aller dans **Collections**
2. Cliquer sur **Upload Snapshot**
3. Importer le fichier :
boardgames.snapshot

Une fois importé, la collection **boardgames** sera automatiquement restaurée.

---

##  Tester l'application

Une fois les services lancés, ouvrir le frontend : http://localhost:3000/

Vous pouvez maintenant poser des questions sur les jeux de société et tester les capacités du système RAG.


