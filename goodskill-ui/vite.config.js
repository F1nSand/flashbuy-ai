import {defineConfig, loadEnv} from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({command, mode}) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = process.env.VITE_API_TARGET || env.VITE_API_TARGET || 'http://127.0.0.1:18080'
  const productionCommerceBuild = command === 'build' && mode !== 'demo'
  return {
  plugins: [vue(), {
    name: 'goodskill-production-entry',
    enforce: 'pre',
    transformIndexHtml: {
      order: 'pre',
      handler(html) {
        return productionCommerceBuild ? html.replace('/src/main.js', '/src/main.production.js') : html
      }
    }
  }],
  server: {
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        ws: true
      },
      '/storage': {
        target: 'http://127.0.0.1:19000',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/storage/, ''),
        configure: proxy => proxy.on('proxyReq', proxyReq => {
          proxyReq.setHeader('host', 'minio:9000')
        })
      }
    }
  },
  define: {
    global: 'window',
    'import.meta.env.VITE_APP_MODE': JSON.stringify(productionCommerceBuild ? 'production' : 'demo')
  }
  }
})
