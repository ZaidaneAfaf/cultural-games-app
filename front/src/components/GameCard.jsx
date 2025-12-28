import React from 'react';

const GameCard = ({ game, onClick, mode = 'normal' }) => {
  const formatYear = (year) => {
    return year < 0 ? `${Math.abs(year)} av. JC` : year;
  };

  return (
    <div
      className={`game-card ${mode === 'archaeo' ? 'similar' : ''}`}
      onClick={() => onClick(game.id)}
    >
      <div className="game-header">
        <div>
          <div className="game-title">{game.name}</div>
          {game.yearPublished && (
            <div className={`game-year ${game.yearPublished < 1500 ? 'archaeo' : ''}`}>
              {formatYear(game.yearPublished)}
            </div>
          )}
        </div>
        {mode === 'archaeo' && (
          <span className="archaeology-match">
            {Math.floor(Math.random() * 40 + 50)}% match
          </span>
        )}
      </div>

      <div className="game-meta">
        {game.averageRating && (
          <div className="meta-item">⭐ {game.averageRating.toFixed(1)}</div>
        )}
        {game.complexityWeight && (
          <div className="meta-item">🧠 {game.complexityWeight.toFixed(1)}</div>
        )}
        {game.minPlayers && game.maxPlayers && (
          <div className="meta-item">👥 {game.minPlayers}-{game.maxPlayers}</div>
        )}
        {game.wikipediaSummary && (
          <div className="meta-item">🌐 Wikipedia</div>
        )}
      </div>

      {game.description && (
        <div className="game-description">
          {game.description.length > 200
            ? `${game.description.substring(0, 200)}...`
            : game.description}
        </div>
      )}

      <div className="game-footer">
        <span className={`source-badge ${game.source === 'LUDII' ? 'archaeo' : ''}`}>
          {game.source}
        </span>
        <div className={`view-details ${game.source === 'LUDII' ? 'archaeo' : ''}`}>
          {mode === 'archaeo' ? 'Comparer →' : 'Voir les détails →'}
        </div>
      </div>
    </div>
  );
};

export default GameCard;