import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [
    react(),
    // JCEF file:// 환경에서 type="module"이 CORS로 차단되므로 빌드 후 HTML에서 제거
    {
      name: 'jcef-script-compat',
      transformIndexHtml(html) {
        return html
          .replace(/ type="module"/g, '')
          .replace(/ crossorigin/g, '')
          // module script removed -> keep deferred execution semantics
          .replace(/<script(?![^>]*\bdefer\b)/g, '<script defer')
      }
    }
  ],
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // Single-file IIFE bundle is intentional for JCEF file:// loading
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // IIFE 포맷: import/export 없이 단일 스크립트로 번들링
        format: 'iife',
        manualChunks: undefined,
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },
  base: './',
})
