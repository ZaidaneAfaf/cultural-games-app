import React, { useState } from 'react';
import './index.css';
import GameCard from './components/GameCard';
import GameModal from './components/GameModal';
import { api } from './services/api';

const SearchBar = ({ query, onQueryChange, onSearch, placeholder }) => (
  <div className="search-box">
    <input
      type="text"
      value={query}
      onChange={(e) => onQueryChange(e.target.value)}
      className="search-input"
      placeholder={placeholder}
      onKeyPress={(e) => e.key === 'Enter' && onSearch()}
    />
    <button className="search-btn" onClick={onSearch}>
      <span>🔍 Rechercher</span>
    </button>
  </div>
);

const LoadingSpinner = () => (
  <div className="loading" style={{ display: 'block' }}>
    <div className="loading-spinner"></div>
    <h3>Recherche en cours...</h3>
    <p style={{ color: 'var(--text-light)', marginTop: '1rem' }}>
      Consultation de la base de données...
    </p>
  </div>
);

function App() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [selectedGame, setSelectedGame] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [conversationHistory, setConversationHistory] = useState([]);

  const handleSearch = async () => {
    if (!query.trim()) {
      alert('Veuillez entrer une requête');
      return;
    }

    // Ajouter la question de l'utilisateur à l'historique
    const userMessage = { type: 'user', content: query };
    setConversationHistory(prev => [...prev, userMessage]);

    const currentQuery = query;
    setQuery(''); // Vider l'input

    setLoading(true);
    try {
      const data = await api.searchRag(currentQuery);
      setResults(data);

      // Ajouter la réponse à l'historique
      const botMessage = {
        type: 'bot',
        content: data.answer || "Voici les jeux qui correspondent à votre recherche...",
        games: data.relevantGames || []
      };
      setConversationHistory(prev => [...prev, botMessage]);
    } catch (error) {
      console.error('Erreur de recherche:', error);
      const errorData = {
        error: true,
        message: error.message,
        answer: `❌ Erreur de connexion au serveur: ${error.message}`
      };
      setResults(errorData);

      const botMessage = {
        type: 'bot',
        content: errorData.answer,
        games: []
      };
      setConversationHistory(prev => [...prev, botMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleGameClick = async (gameId) => {
    try {
      const mockGame = {
        id: gameId,
        name: "Jeu de simulation",
        yearPublished: 2020,
        averageRating: 7.5,
        complexityWeight: 2.8,
        minPlayers: 2,
        maxPlayers: 4,
        description: "Un jeu de stratégie passionnant...",
        source: "LUDII",
        categories: ["Stratégie", "Familial"],
        wikipediaSummary: "Un jeu populaire...",
        ludiiMetadata: {
          origin: "Rome antique",
          originPoint: "Italie"
        }
      };
      setSelectedGame(mockGame);
      setShowModal(true);
    } catch (error) {
      console.error('Erreur:', error);
    }
  };

  const examples = [
    "Règles du backgammon",
    "Jeux égyptiens anciens",
    "Stratégie pour 4 joueurs",
    "Dés à 6 faces en os",
    "Plateau en bois carré",
    "Jeux familiaux simples",
    "Pièces circulaires en argile"
  ];

  const demoGames = [
    {
      id: "1",
      name: "Backgammon",
      yearPublished: 3000,
      averageRating: 7.8,
      complexityWeight: 2.0,
      minPlayers: 2,
      maxPlayers: 2,
      description: "Jeu de table ancien avec dés et pions...",
      source: "LUDII",
      categories: ["Classique", "Stratégie"]
    },
    {
      id: "2",
      name: "Senet",
      yearPublished: -3100,
      averageRating: 6.5,
      complexityWeight: 1.5,
      minPlayers: 2,
      maxPlayers: 2,
      description: "Jeu égyptien antique sur plateau...",
      source: "LUDII",
      categories: ["Antique", "Égyptien"]
    },
    {
      id: "3",
      name: "Chess",
      yearPublished: 600,
      averageRating: 8.5,
      complexityWeight: 4.0,
      minPlayers: 2,
      maxPlayers: 2,
      description: "Jeu de stratégie abstrait...",
      source: "BOARDGAMEGEEK",
      categories: ["Stratégie", "Abstrait"]
    }
  ];

  return (
    <div className="container">
      <div className="header">
        <h1>⚱️ Ludothèque Archéologique</h1>
        <p>Explorez l'histoire ludique de l'humanité à travers les âges</p>
        <p style={{ fontSize: '0.95rem', marginTop: '0.5rem', opacity: 0.9 }}>
          Recherchez des jeux anciens ou identifiez des artefacts découverts
        </p>
      </div>

      {/* Historique de conversation */}
      {conversationHistory.length > 0 && (
        <div className="conversation-history">
          {conversationHistory.map((message, index) => (
            <div key={index} className={`message ${message.type}`}>
              {message.type === 'user' ? (
                <div className="message-bubble user-bubble">
                  <div className="message-icon">👤</div>
                  <div className="message-content">{message.content}</div>
                </div>
              ) : (
                <div className="message-bubble bot-bubble">
                  <div className="message-icon">🎲</div>
                  <div className="message-content">
                    <div className="bot-response">{message.content}</div>
                    {message.games && message.games.length > 0 && (
                      <div className="games-grid">
                        {message.games.map(game => (
                          <GameCard
                            key={game.id}
                            game={game}
                            onClick={handleGameClick}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {loading && <LoadingSpinner />}

      {/* Affichage initial ou résultats sans historique */}
      {!loading && conversationHistory.length === 0 && (
        <div className="card welcome-card">
          <div className="welcome-message">
            <h2>👋 Bienvenue !</h2>
            <p>Posez-moi une question sur les jeux de société ou décrivez un objet de jeu que vous avez trouvé.</p>
          </div>

          <div className="examples">
            {examples.map((example, index) => (
              <div
                key={index}
                className="example-tag"
                onClick={() => setQuery(example)}
              >
                {example}
              </div>
            ))}
          </div>

          <div className="info-box">
            <h4>💡 Conseils de recherche</h4>
            <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
              <div>
                <strong>📚 Jeux connus:</strong> nom, règles, stratégies
              </div>
              <div>
                <strong>🏛️ Objets trouvés:</strong> matériaux, formes, motifs, dimensions
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Barre de recherche fixe en bas */}
      <div className="search-container-fixed">
        <SearchBar
          query={query}
          onQueryChange={setQuery}
          onSearch={handleSearch}
          placeholder="Ex: Règles du backgammon, jeux égyptiens, dés en os..."
        />
      </div>

      {showModal && selectedGame && (
        <GameModal
          game={selectedGame}
          onClose={() => {
            setShowModal(false);
            setSelectedGame(null);
          }}
        />
      )}
    </div>
  );
}

export default App;