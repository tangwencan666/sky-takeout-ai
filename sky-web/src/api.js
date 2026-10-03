import axios from 'axios'

export const TOKEN_KEY = 'sky-takeout-token'
export const api = axios.create({ baseURL: '/api', timeout: 15000 })
let sessionRevision = 0
const pending = new Set()

export function currentSession() { return sessionRevision }
export function sessionToken() { return sessionStorage.getItem(TOKEN_KEY) || '' }

// 身份切换即取消在途请求；旧用户的成功响应或401都不能影响新用户页面。
export function setSessionToken(token) {
  sessionRevision += 1
  for (const controller of pending) controller.abort()
  pending.clear()
  if (token) sessionStorage.setItem(TOKEN_KEY, token)
  else sessionStorage.removeItem(TOKEN_KEY)
}

export function errorMessage(error, fallback = '请求失败，请稍后重试') {
  if (error?.silent || axios.isCancel(error)) return ''
  const value = error?.message
  return typeof value === 'string' && value.trim() ? value.slice(0, 300) : fallback
}

function release(config) { if (config?.skyController) pending.delete(config.skyController) }
function stale(config) { return config && config.skyRevision !== sessionRevision }
function cancelled() { return new axios.CanceledError('会话已改变') }

api.interceptors.request.use(config => {
  const controller = new AbortController()
  pending.add(controller)
  config.skyController = controller
  config.skyRevision = sessionRevision
  config.skyToken = sessionToken()
  config.signal = config.signal ? AbortSignal.any([config.signal, controller.signal]) : controller.signal
  if (config.skyToken && !config.url?.includes('/auth/')) config.headers.Authorization = `Bearer ${config.skyToken}`
  return config
})

api.interceptors.response.use(response => {
  release(response.config)
  if (stale(response.config)) throw cancelled()
  return response.data.data
}, error => {
  const config = error.config
  release(config)
  if (stale(config) || axios.isCancel(error)) return Promise.reject(cancelled())
  if (error.response?.status === 401 && config?.skyToken && !config.url?.includes('/auth/')) {
    setSessionToken('')
    window.dispatchEvent(new Event('sky-unauthorized'))
  }
  const serverMessage = error.response?.data?.message
  const message = typeof serverMessage === 'string' && serverMessage.trim()
    ? serverMessage.slice(0, 300)
    : error.code === 'ECONNABORTED' ? '请求超时，请稍后重试'
      : error.response ? '服务暂时不可用，请稍后重试' : '网络连接失败，请检查网络后重试'
  return Promise.reject(new Error(message))
})
