import axios from 'axios'
// Token仅存当前标签页会话；不记录请求体/Authorization，避免调试日志泄露凭据。
export const TOKEN_KEY = 'sky-takeout-token'
export const api = axios.create({ baseURL: '/api', timeout: 15000 })
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(response => response.data.data, error => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem(TOKEN_KEY)
    window.dispatchEvent(new Event('sky-unauthorized'))
  }
  return Promise.reject(new Error(error.response?.data?.message || '请求失败，请稍后重试'))
})
