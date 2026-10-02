import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
// 开发代理统一/api前缀，浏览器不直接跨域访问数据库或模型服务。
export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true, rewrite: path => path.replace(/^\/api/, '') } } }
})
