// src/components/WiseSageAvatar.jsx
import React from 'react';

import sageIntro from '../assets/presenting.png';
import sageThinking from '../assets/thinking.png';
import sageAnswer from '../assets/findAnswer.png';

const WiseSageAvatar = ({ mood }) => {
  let imgSrc = sageIntro;
  let alt = "Sage qui se présente";

  if (mood === 'thinking') {
    imgSrc = sageThinking;
    alt = "Sage en train de réfléchir";
  } else if (mood === 'answer') {
    imgSrc = sageAnswer;
    alt = "Sage qui a trouvé la réponse";
  }

  return (
    <div className={`sage-avatar sage-${mood}`}>
      <img src={imgSrc} alt={alt} />
      {mood === 'answer' && <div className="sage-sparkles" />}
    </div>
  );
};

export default WiseSageAvatar;
