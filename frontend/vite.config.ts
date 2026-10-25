import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// 개발: npm run dev(5173)가 API·SSE·알상무 노트를 bootRun(8080)으로 넘긴다
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/views': 'http://localhost:8080',
    },
  },
  build: { outDir: 'dist' },
})
