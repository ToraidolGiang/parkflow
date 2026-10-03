import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { resolveApiProxyTarget } from './src/shared/config/viteProxy'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': resolveApiProxyTarget(env),
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  }
})
