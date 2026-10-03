import test from 'node:test'
import assert from 'node:assert/strict'
import axios from 'axios'
import { api, setSessionToken, sessionToken, errorMessage } from '../src/api.js'

const storage = new Map()
globalThis.sessionStorage = { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
globalThis.window = new EventTarget()

test('请求统一携带当前Token；登录请求不会附带旧凭据', async () => {
  setSessionToken('test-session-a')
  let headers
  const adapter = async config => { headers = config.headers; return { config, data: { data: { ok: true } }, status: 200 } }
  assert.deepEqual(await api.get('/user/cart', { adapter }), { ok: true })
  assert.equal(headers.Authorization, 'Bearer test-session-a')
  await api.post('/user/auth/login', {}, { adapter })
  assert.equal(headers.Authorization, undefined)
})

test('退出/切换身份后，延迟成功响应不会交给页面', async () => {
  setSessionToken('test-session-a')
  let reply, started
  const ready = new Promise(resolve => { started = resolve })
  const response = api.get('/user/orders', { adapter: config => new Promise(resolve => { reply = () => resolve({ config, data: { data: ['private-a'] }, status: 200 }); started() }) }).catch(error => error)
  await ready
  setSessionToken('test-session-b'); reply()
  assert.equal(axios.isCancel(await response), true)
  assert.equal(sessionToken(), 'test-session-b')
})

test('旧请求延迟401不能清除新用户会话', async () => {
  setSessionToken('test-session-a')
  let rejectRequest, started, unauthorized = 0
  const onUnauthorized = () => unauthorized++
  window.addEventListener('sky-unauthorized', onUnauthorized)
  const ready = new Promise(resolve => { started = resolve })
  const response = api.get('/user/cart', { adapter: config => new Promise((resolve, reject) => { rejectRequest = () => reject(new axios.AxiosError('old401', 'ERR_BAD_REQUEST', config, null, { status: 401, data: { message: 'expired' } })); started() }) }).catch(error => error)
  await ready
  setSessionToken('test-session-b'); rejectRequest()
  assert.equal(axios.isCancel(await response), true)
  assert.equal(sessionToken(), 'test-session-b')
  assert.equal(unauthorized, 0)
  window.removeEventListener('sky-unauthorized', onUnauthorized)
})

test('当前身份401失效一次，错误对象不会成为object文本', async () => {
  setSessionToken('test-session-a')
  let unauthorized = 0
  const listener = () => unauthorized++
  window.addEventListener('sky-unauthorized', listener)
  const response = await api.get('/user/cart', { adapter: async config => { throw new axios.AxiosError('401', 'ERR_BAD_REQUEST', config, null, { status: 401, data: { message: { internal: 'must not render' } } }) } }).catch(error => error)
  assert.equal(sessionToken(), '')
  assert.equal(unauthorized, 1)
  assert.equal(errorMessage(response), '服务暂时不可用，请稍后重试')
  assert.equal(errorMessage(new axios.CanceledError()), '')
  window.removeEventListener('sky-unauthorized', listener)
})
