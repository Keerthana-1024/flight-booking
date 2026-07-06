import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  // For production: npm run build:spring → copies to Spring Boot static dir
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  }
})
