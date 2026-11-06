import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      // В dev-режиме /api уходит в локальный бэкенд — как nginx в Docker и на сервере
      proxy: {
        '/api': env.VITE_DEV_API_PROXY || 'http://localhost:8000',
      },
    },
  }
})
