import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El panel se sirve en /admin/ desde el mismo origen que el sitio público.
// En desarrollo, Vite reenvía /api al servicio de dominio para no necesitar CORS.
export default defineConfig({
  base: '/admin/',
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
})
