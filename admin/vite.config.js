import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Relative base so the bundled SPA works under any prefix
  // (e.g. /realms/{realm}/group-mgmt/ui/) without rebuilding.
  base: './',
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/realms': {
        target: 'http://keycloak:8080',
        changeOrigin: false,
        headers: {
          'Host': 'localhost:8080',
        },
      },
    },
  },
})
