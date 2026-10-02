<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api, TOKEN_KEY } from './api'
const messages = ref([]), message = ref(''), conversation = ref(null), mode = ref('mock'), running = ref(false), error = ref(''), plan = ref(null), budget = ref(80)
let abort
onMounted(async () => { try { mode.value = (await api.get('/user/ai/status')).mode } catch (e) { error.value = e.message } })
onUnmounted(() => abort?.abort())
// fetch支持POST和Authorization；处理跨数据包的事件边界及UTF-8，不假设一次read是一条消息。
async function chat() {
  const text = message.value.trim(); if (!text || running.value) return
  running.value = true; error.value = ''; message.value = ''; abort = new AbortController()
  messages.value.push({ role: 'user', text }); const answer = reactive({ role: 'assistant', text: '' }); messages.value.push(answer)
  try {
    const response = await fetch('/api/user/ai/chat/stream', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sessionStorage.getItem(TOKEN_KEY)}` }, body: JSON.stringify({ message: text, conversationId: conversation.value }), signal: abort.signal })
    if (!response.ok) { if (response.status === 401) window.dispatchEvent(new Event('sky-unauthorized')); throw new Error('AI连接失败，请检查登录状态后重试') }
    const reader = response.body.getReader(), decoder = new TextDecoder(); let buffer = '', completed = false
    try {
      while (true) {
        const { value, done } = await reader.read(); if (done) break
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2)
          const lines = block.split('\n'); const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
          const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
          if (!data) continue
          const payload = JSON.parse(data)
          if (event === 'delta') answer.text += payload.text
          if (event === 'done') { conversation.value = payload.conversationId; mode.value = payload.mode; completed = true }
          if (event === 'error') throw new Error(payload.message)
        }
      }
      if (!completed) throw new Error('回复未完成，请重试')
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
  } catch (e) { error.value = e.name === 'AbortError' ? '已停止生成' : e.message }
  finally { running.value = false; abort = null }
}
async function makePlan() {
  if (running.value || !message.value.trim()) return
  running.value = true; error.value = ''; plan.value = null
  try { plan.value = await api.post('/user/ai/meal-plan', { message: message.value, budget: budget.value }) } catch (e) { error.value = e.message } finally { running.value = false }
}
async function addDish(id) {
  try { await api.post('/user/cart', { itemType: 'DISH', itemId: id, quantity: 1, flavor: '' }); ElMessage.success('已加入购物车') } catch (e) { error.value = e.message }
}
</script>
<template>
  <section class="chat-panel">
    <h2>AI点餐助手 <el-tag>{{ mode === 'live' ? '模型模式' : 'Mock演示' }}</el-tag></h2>
    <p>告诉我人数、预算和口味。推荐卡片经过后端核验；聊天文字仍需结合实际菜单确认。</p>
    <el-alert v-if="mode !== 'live'" title="当前未调用真实模型；聊天为协议演示，套餐按真实菜单与预算生成演示结果。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div class="messages" aria-live="polite"><article v-for="(item, index) in messages" :key="index" :class="['bubble', item.role]"><b>{{ item.role === 'user' ? '我' : '智膳助手' }}</b><p>{{ item.text || '正在生成…' }}</p></article></div>
    <el-input v-model="message" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="例如：两个人，预算80，想吃辣一点" :disabled="running" />
    <div class="chat-actions"><el-button type="primary" :disabled="running || !message.trim()" @click="chat">发送</el-button><el-button v-if="running" @click="abort?.abort()">停止生成</el-button><el-input-number v-model="budget" :min="1" :max="10000" :precision="2" aria-label="套餐预算" /><el-button :disabled="running || !message.trim()" @click="makePlan">生成预算套餐</el-button></div>
    <div v-if="plan"><h3>推荐方案 · ¥{{ plan.totalPrice }} / 预算 ¥{{ plan.budget }}</h3><p>{{ plan.reason }}</p><div class="grid"><article v-for="dish in plan.dishes" :key="dish.id" class="card"><img v-if="dish.image" :src="dish.image" :alt="dish.name"><h3>{{ dish.name }}</h3><p>{{ dish.description }}</p><b>¥{{ dish.price }}</b><el-button @click="addDish(dish.id)">加入购物车</el-button></article></div></div>
  </section>
</template>
<style scoped>
.messages{display:flex;flex-direction:column;gap:16px;max-height:450px;overflow:auto;padding:20px 0}.bubble{max-width:85%;padding:16px 20px;border-radius:14px;background:#fff;border:1px solid #e1e7dd}.bubble.user{align-self:flex-end;background:#e6f1e9}.bubble.assistant{align-self:flex-start}.bubble p{white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 0}.chat-actions{display:flex;flex-wrap:wrap;gap:10px;margin:16px 0}.el-alert{margin:12px 0}
</style>
