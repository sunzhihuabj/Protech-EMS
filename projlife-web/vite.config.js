import { defineConfig } from 'vite'
import vue2 from '@vitejs/plugin-vue2'
import path from 'path'

// S0 前端：Vue 2.7 + Element UI 2.15；/api 代理到主应用 8080
export default defineConfig({
  plugins: [vue2()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 8088,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/fs': { target: 'http://localhost:8091', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
