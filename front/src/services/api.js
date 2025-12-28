import axios from 'axios';

// Base = le @RequestMapping du contrôleur
const API_BASE_URL = 'http://localhost:8081/api/games';

export const api = {
  // Appel au système RAG (endpoint /ask)
  async searchRag(question, filters = {}) {
    const body = {
      question,  // doit correspondre à QueryDTO.question
      filters,   // doit correspondre à QueryDTO.filters (peut être {})
    };

    const response = await axios.post(`${API_BASE_URL}/ask`, body);
    return response.data; // GameResponseDTO
  },
};
