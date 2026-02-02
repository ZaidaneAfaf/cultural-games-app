import axios from "axios";

const apiClient = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

export const api = {
  async searchRag(question, filters = {}) {
    const response = await apiClient.post("/games/ask", { question, filters });
    return response.data;
  },
};
