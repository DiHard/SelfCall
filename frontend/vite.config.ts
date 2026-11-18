import { randomUUID } from 'node:crypto'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const release = randomUUID()
  return {
    plugins: [react(), {
      name: 'selfcall-release',
      apply: 'build',
      transformIndexHtml() {
        return [{ tag: 'meta', attrs: { name: 'selfcall-release', content: release } }]
      },
      generateBundle() {
        this.emitFile({ type: 'asset', fileName: 'version.json', source: JSON.stringify({ release }) })
      },
    }],
    server: {
      // В dev-режиме /api уходит в локальный бэкенд — как nginx в Docker и на сервере
      proxy: {
        '/api': env.VITE_DEV_API_PROXY || 'http://localhost:8000',
      },
    },
  }
})
