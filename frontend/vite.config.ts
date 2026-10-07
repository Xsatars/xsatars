import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
// 文档：https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      // 用 @ 代表 src 目录，import 时不用写一长串 ../
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    // 显式指定监听地址，避免只监听 IPv6(::1) 导致 127.0.0.1 打不开
    host: '127.0.0.1',
    port: 5173,
    watch: {
      // 有些编辑器/工具保存文件时会先生成临时文件再改名，
      // Vite 去监听这些转瞬即逝的文件会报 EBUSY 把 dev server 搞崩，所以忽略掉
      ignored: ['**/.*.tmpdir/**', '**/.*.tmp', '**/.~*']
    },
    // 开发时把 /api 开头的请求转发到后端 8080，这样前端代码里直接写 /api/xxx 即可，
    // 不需要关心跨域问题。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 也要转发（后端 STOMP 端点是 /ws）
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
