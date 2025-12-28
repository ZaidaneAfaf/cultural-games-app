import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:2256', // IMPORTANT: ton port Spring
        changeOrigin: true,
        secure: false,
      }
    }
  }
});