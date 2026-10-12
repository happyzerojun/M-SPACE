import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 브라우저는 5173에만 요청하고, Vite가 Spring(8080)으로 대신 전달해요 → CORS가 생기지 않아요
    proxy: {
      '/api': process.env.API_TARGET ?? 'http://localhost:8080',
    },
  },
})
