import React, { useState, useEffect, useRef, useMemo } from 'react';
import './index.css';
import GameCard from './components/GameCard';
import GameModal from './components/GameModal';
import { api } from './services/api';

// ✅ YOUR 3 IMAGES
import thinkingImg from './assets/thinking.png';
import findAnswerImg from './assets/findAnswer.png';
import presentingImg from './assets/presenting.png';

const SearchBar = ({ query, onQueryChange, onSearch, placeholder }) => (
  <div className="search-box">
    <input
      type="text"
      value={query}
      onChange={(e) => onQueryChange(e.target.value)}
      className="search-input"
      placeholder={placeholder}
      onKeyDown={(e) => e.key === 'Enter' && onSearch()}
    />
    <button className="search-btn" onClick={onSearch} aria-label="Search">
      <span className="play-icon">▶</span>
    </button>
  </div>
);

const LoadingSpinner = () => (
  <div className="loading" style={{ display: 'block' }}>
    <div className="loading-spinner"></div>
    <h3>Searching...</h3>
    <p style={{ color: 'var(--text-light)', marginTop: '1rem' }}>
      Consulting the knowledge base...
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

  // intro | thinking | answer
  const [sageMood, setSageMood] = useState('intro');

  const chatColumnRef = useRef(null);
  const conversationRef = useRef(null);
  const lastBotBubbleRef = useRef(null);
  const floatingSageRef = useRef(null);
  const endOfChatRef = useRef(null);

  const avatarSrc = useMemo(() => {
    if (sageMood === 'thinking') return thinkingImg;
    if (sageMood === 'answer') return findAnswerImg;
    return presentingImg;
  }, [sageMood]);

  const lastBotIndex = useMemo(() => {
    for (let i = conversationHistory.length - 1; i >= 0; i--) {
      if (conversationHistory[i].type === 'bot') return i;
    }
    return -1;
  }, [conversationHistory]);

  const shouldShowFloatingSage = lastBotIndex !== -1;

  useEffect(() => {
    endOfChatRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [conversationHistory, loading]);

  const repositionSage = () => {
    const chatEl = chatColumnRef.current;
    const bubbleEl = lastBotBubbleRef.current;
    const sageEl = floatingSageRef.current;
    const footerEl = document.querySelector('.search-container-fixed');

    if (!sageEl) return;

    if (!shouldShowFloatingSage || !chatEl || !bubbleEl) {
      sageEl.style.opacity = '0';
      return;
    }

    const chatRect = chatEl.getBoundingClientRect();
    const bubbleRect = bubbleEl.getBoundingClientRect();

    const OFFSET_Y = 35;
    const desiredTop = bubbleRect.top - chatRect.top + OFFSET_Y;

    const minTop = 0;

    const footerRect = footerEl?.getBoundingClientRect();
    const footerTopInChat = footerRect ? (footerRect.top - chatRect.top) : Infinity;

    const sageH = sageEl.offsetHeight || 140;
    const SAFE_GAP = 12;

    const maxTop = footerTopInChat - sageH - SAFE_GAP;

    const finalTop = Math.max(minTop, Math.min(desiredTop, maxTop));

    sageEl.style.top = `${finalTop}px`;
    sageEl.style.opacity = '1';
  };

  useEffect(() => {
    requestAnimationFrame(repositionSage);
    const t = setTimeout(() => requestAnimationFrame(repositionSage), 120);
    return () => clearTimeout(t);
  }, [conversationHistory, loading, sageMood, lastBotIndex, shouldShowFloatingSage]);

  useEffect(() => {
    const onScroll = () => requestAnimationFrame(repositionSage);
    const onResize = () => requestAnimationFrame(repositionSage);

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onResize);

    const conv = conversationRef.current;
    conv?.addEventListener('scroll', onScroll, { passive: true });

    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onResize);
      conv?.removeEventListener('scroll', onScroll);
    };
  }, [shouldShowFloatingSage]);

  const handleSearch = async () => {
    if (!query.trim()) {
      alert('Please enter a question.');
      return;
    }

    const userText = query.trim();

    setConversationHistory((prev) => [...prev, { type: 'user', content: userText }]);
    setQuery('');
    setLoading(true);
    setSageMood('thinking');

    try {
      const data = await api.searchRag(userText);
      setResults(data);

      setConversationHistory((prev) => [
        ...prev,
        {
          type: 'bot',
          content: data.answer || 'Here are the games that match your request...',
          games: data.relevantGames || [],
        },
      ]);

      setSageMood('answer');
      setTimeout(() => setSageMood('intro'), 2500);
    } catch (error) {
      const errorData = {
        error: true,
        message: error.message,
        answer: `❌ Server connection error: ${error.message}`,
      };
      setResults(errorData);

      setConversationHistory((prev) => [
        ...prev,
        { type: 'bot', content: errorData.answer, games: [] },
      ]);

      setSageMood('answer');
      setTimeout(() => setSageMood('intro'), 2500);
    } finally {
      setLoading(false);
    }
  };

  const handleGameClick = async (gameId) => {
    const mockGame = {
      id: gameId,
      name: 'Simulation Game',
      yearPublished: 2020,
      averageRating: 7.5,
      complexityWeight: 2.8,
      minPlayers: 2,
      maxPlayers: 4,
      description: 'A thrilling strategy game...',
      source: 'LUDII',
      categories: ['Strategy', 'Family'],
      wikipediaSummary: 'A popular game...',
      ludiiMetadata: { origin: 'Ancient Rome', originPoint: 'Italy' },
    };
    setSelectedGame(mockGame);
    setShowModal(true);
  };

  return (
    <div className="container">
      <div className="header">
        <h1>GameBoardGenius</h1>
        {/* ✅ subtitle in ENGLISH */}
        <p>Explore the playful history of humanity across the ages</p>
      </div>

      <div className="chat-layout">
        <div className="chat-column" ref={chatColumnRef}>
          {/* ✅ floating sage only when bot exists */}
          <div
            className="floating-sage"
            ref={floatingSageRef}
            style={{ opacity: 0, display: shouldShowFloatingSage ? 'block' : 'none' }}
          >
            <img src={avatarSrc} alt="Sage" />
          </div>

          {conversationHistory.length > 0 && (
            <div className="conversation-history" ref={conversationRef}>
              {conversationHistory.map((message, index) => {
                const isLastBot = message.type === 'bot' && index === lastBotIndex;

                return (
                  <div key={index} className={`message ${message.type}`}>
                    {message.type === 'user' ? (
                      <div className="message-bubble user-bubble">
                        <div className="message-icon">👤</div>
                        <div className="message-content">{message.content}</div>
                      </div>
                    ) : (
                      <div
                        className="message-bubble bot-bubble"
                        ref={isLastBot ? lastBotBubbleRef : null}
                      >
                        <div className="message-content">
                          <div className="bot-response">{message.content}</div>

                          {message.games && message.games.length > 0 && (
                            <div className="games-grid">
                              {message.games.map((game) => (
                                <GameCard key={game.id} game={game} onClick={handleGameClick} />
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}

              <div ref={endOfChatRef} />
            </div>
          )}

          {loading && <LoadingSpinner />}

          {/* ✅ WELCOME WITHOUT SUGGESTION BUTTONS */}
          {!loading && conversationHistory.length === 0 && (
            <div className="card welcome-card">
              <div className="welcome-sage-layout">
                <div className="welcome-text">
                  <h2>Welcome.</h2>
                  <p className="welcome-paragraph">
                    Board games are not mere amusements—they are living vessels of culture.
                    Across centuries, they have carried symbols, strategies, and stories from one
                    generation to the next. They mirror the rise of civilizations, the art of
                    thinking, and the rituals that weave people together. Ask your question, and I
                    shall answer as a keeper of playful history.
                  </p>
                </div>

                <div className="welcome-sage-image">
                  <img src={presentingImg} alt="Wise Sage" />
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="search-container-fixed">
        <SearchBar
          query={query}
          onQueryChange={setQuery}
          onSearch={handleSearch}
          placeholder="e.g., Backgammon rules, ancient Egyptian games..."
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
