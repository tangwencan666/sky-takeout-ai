import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, TOKEN_KEY } from '../api'
// Pinia管理登录显示状态；后端始终独立验签，前端状态不承担权限判断。
export const useSession = defineStore('session', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null)
  async function login(input) {
    const result = await api.post('/user/auth/login', input)
    token.value = result.token
    user.value = result.user
    sessionStorage.setItem(TOKEN_KEY, result.token)
  }
  function logout() { token.value = ''; user.value = null; sessionStorage.removeItem(TOKEN_KEY) }
  return { token, user, login, logout }
})
