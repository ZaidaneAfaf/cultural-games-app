import React from 'react';

const GameModal = ({ game, isArchaeology = false, onClose }) => {
  const formatYear = (year) => {
    return year < 0 ? `${Math.abs(year)} av. JC` : year;
  };

  return (
    <div className="modal active" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className={`modal-header ${isArchaeology ? 'archaeo' : ''}`}>
          <span className="close" onClick={onClose}>&times;</span>
          <h2>{game.name}</h2>
        </div>

        <div className="modal-body">
          <div className="metadata-grid">
            {game.yearPublished && (
              <div className="metadata-item">
                <strong>Année:</strong> {formatYear(game.yearPublished)}
              </div>
            )}

            {game.averageRating && (
              <div className="metadata-item">
                <strong>Note:</strong> ⭐ {game.averageRating.toFixed(1)}/10
              </div>
            )}

            {game.complexityWeight && (
              <div className="metadata-item">
                <strong>Complexité:</strong> 🧠 {game.complexityWeight.toFixed(1)}/5
              </div>
            )}

            {game.minPlayers && game.maxPlayers && (
              <div className="metadata-item">
                <strong>Joueurs:</strong> 👥 {game.minPlayers}-{game.maxPlayers}
              </div>
            )}

            <div className="metadata-item">
              <strong>Source:</strong> {game.source}
            </div>
          </div>

          {game.description && (
            <div className={`detail-section ${isArchaeology ? 'archaeo' : ''}`}>
              <h3>📖 Description</h3>
              <p>{game.description}</p>
            </div>
          )}

          {game.categories?.length > 0 && (
            <div className={`detail-section ${isArchaeology ? 'archaeo' : ''}`}>
              <h3>🏷️ Catégories</h3>
              <p>{game.categories.join(', ')}</p>
            </div>
          )}

          {game.ludiiMetadata && (
            <div className="detail-section archaeo">
              <h3>🏛️ Histoire et Origine</h3>
              <div className="metadata-grid">
                {game.ludiiMetadata.origin && (
                  <div className="metadata-item">
                    <strong>Origine:</strong> {game.ludiiMetadata.origin}
                  </div>
                )}
                {game.ludiiMetadata.originPoint && (
                  <div className="metadata-item">
                    <strong>Localisation:</strong> {game.ludiiMetadata.originPoint}
                  </div>
                )}
              </div>
            </div>
          )}

          {isArchaeology && game.yearPublished && game.yearPublished < 500 && (
            <div className="detail-section archaeo">
              <h3>🔍 Analyse archéologique</h3>
              <p><strong>Jeu antique.</strong> Pour identification précise :</p>
              <ul style={{ marginLeft: '1.5rem', marginTop: '0.5rem' }}>
                <li>Comparez les matériaux</li>
                <li>Vérifiez dimensions et formes</li>
                <li>Examinez motifs et gravures</li>
                <li>Consultez un spécialiste</li>
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default GameModal;