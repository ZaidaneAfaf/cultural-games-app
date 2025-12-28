import axios from 'axios';

const API_BASE_URL = 'http://localhost:2256/api';

export const api = {
  // Recherche RAG
  async searchRag(query) {
    const response = await axios.get(`${API_BASE_URL}/rag/search`, {
      params: { query }
    });
    return response.data;
  },

  // Obtenir les détails d'un jeu
  async getGameById(id) {
    const response = await axios.get(`${API_BASE_URL}/games/${id}`);
    return response.data;
  },

  // Recherche de jeux
  async searchGames(name) {
    const response = await axios.get(`${API_BASE_URL}/games/search`, {
      params: { name }
    });
    return response.data;
  }
};