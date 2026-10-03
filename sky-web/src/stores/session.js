import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, sessionToken, setSessionToken } from '../api'
// Pinia管理登录显示状态；后端始终独立验签，前端状态不承担权限判断。
export const useSession = defineStore('session', () => {
  const token = ref(sessionToken())
  const user = ref(null)
  async function login(input) {
    const result = await api.post('/user/auth/login', input)
    setSessionToken(result.token)
    token.value = result.token
    user.value = result.user
  }
  async function restore() { user.value = await api.get('/user/me') }
  function logout() { setSessionToken(''); token.value = ''; user.value = null }
  return { token, user, login, logout, restore }
})
