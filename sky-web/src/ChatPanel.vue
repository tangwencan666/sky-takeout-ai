<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { api, sessionToken, currentSession, errorMessage } from './api'
import { money, text, list } from './format'
import FoodImage from './FoodImage.vue'
const emit = defineEmits(['select-dish'])
const messages = ref([]), message = ref(''), conversation = ref(null), mode = ref('unknown')
const running = ref(false), error = ref(''), plan = ref(null), budget = ref(80), purpose = ref('ordering')
const messagesElement = ref(null), rules = ref(null)
const model = ref(''), provider = ref(''), keyConfigured = ref(false), providerVerified = ref(false), statusLoading = ref(false)
const modeLabel = computed(() => {
  if (mode.value === 'live') return providerVerified.value ? '真实模型 · 已验证' : '真实模型 · 待验证'
  if (mode.value === 'mock') return 'Mock 演示'
  return mode.value === 'unavailable' ? '状态获取失败' : '正在读取模型状态'
})
const statusType = computed(() => mode.value === 'live' ? providerVerified.value ? 'success' : 'warning' : mode.value === 'unavailable' ? 'danger' : 'info')
const canRequest = computed(() => mode.value === 'mock' || (mode.value === 'live' && keyConfigured.value))
const modeDescription = computed(() => {
  if (mode.value === 'mock') return '不调用真实模型。聊天演示协议，搭配使用真实菜单与预算；口味理解尚未经过真实模型验证。'
  if (mode.value === 'live') {
    if (!keyConfigured.value) return 'API Key 未配置，真实模型请求暂不可用。配置完成后可刷新状态。'
    if (providerVerified.value) return '本次服务启动后已通过基础连通验证。工具、记忆与RAG的真实模型效果仍需分别验证；聊天建议不等于交易承诺。'
    return '已选择真实模型，但尚未完成基础连通验证。发送、生成预算搭配或查询规则将使用当前模型；不会自动发起验证请求。'
  }
  return mode.value === 'unavailable' ? '暂时无法确认当前模型模式，请刷新状态后重试。菜单、购物车和订单功能仍可使用。' : '正在读取服务配置与验证结果，此操作不调用模型。'
})
const ruleTitles = { 'delivery-policy.md': '配送规则', 'refund-policy.md': '退款规则', 'coupon-policy.md': '优惠券规则', 'membership-policy.md': '会员规则', 'food-safety.md': '食品安全' }
let abort, alive = true, timedOut = false
async function refreshStatus() {
  if (statusLoading.value || running.value) return
  statusLoading.value = true
  try {
    // 状态查询只读取后端记录，不触发probe或任何收费模型调用。
    const result = await api.get('/user/ai/status')
    if (!alive) return
    if (!['mock', 'live'].includes(result?.mode)) throw new Error('模型状态格式异常，请稍后刷新')
    mode.value = result.mode
    model.value = text(result.model)
    provider.value = text(result.provider)
    keyConfigured.value = result.keyConfigured === true
    providerVerified.value = result.providerVerified === true && result.mode === 'live'
    error.value = ''
  } catch (e) {
    if (alive) {
      mode.value = 'unavailable'; model.value = ''; provider.value = ''
      keyConfigured.value = false; providerVerified.value = false; error.value = errorMessage(e)
    }
  } finally { if (alive) statusLoading.value = false }
}
onMounted(refreshStatus)
onUnmounted(() => { alive = false; abort?.abort() })

async function scrollMessages() {
  await nextTick()
  messagesElement.value?.scrollTo({ top: messagesElement.value.scrollHeight, behavior: 'instant' })
}
function stop() { abort?.abort() }
function reset() { messages.value = []; conversation.value = null; plan.value = null; rules.value = null; error.value = '' }
async function execute(action) {
  if (running.value || !canRequest.value) return
  running.value = true; error.value = ''; timedOut = false; abort = new AbortController()
  const revision = currentSession()
  const timer = setTimeout(() => { timedOut = true; abort?.abort() }, 55000)
  try { await action(abort.signal, revision) }
  catch (e) {
    if (alive && revision === currentSession()) error.value = timedOut ? 'AI回复超时，请稍后重试；菜单和订单功能仍可使用' : e.name === 'AbortError' || e.code === 'ERR_CANCELED' ? '已停止本次请求' : errorMessage(e, 'AI服务暂时不可用，请稍后重试')
  } finally { clearTimeout(timer); if (alive) running.value = false; abort = null }
}

// POST流使用fetch，按SSE事件边界解析UTF-8；服务端done才代表本轮完成。
async function chat() {
  const input = message.value.trim()
  if (!input) return
  await execute(async (signal, revision) => {
    message.value = ''; messages.value.push({ role: 'user', text: input })
    const answer = reactive({ role: 'assistant', text: '', state: '正在生成' })
    messages.value.push(answer); scrollMessages()
    let reader, completed = false
    try {
      const response = await fetch('/api/user/ai/chat/stream', {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sessionToken()}` },
        body: JSON.stringify({ message: input, conversationId: conversation.value }), signal
      })
      if (revision !== currentSession()) throw new DOMException('会话已改变', 'AbortError')
      if (!response.ok) {
        if (response.status === 401) window.dispatchEvent(new Event('sky-unauthorized'))
        const body = await response.json().catch(() => ({}))
        throw new Error(text(body.message, 'AI连接失败，请稍后重试'))
      }
      if (!response.body) throw new Error('浏览器无法读取流式响应，请更新浏览器后重试')
      reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        if (revision !== currentSession()) throw new DOMException('会话已改变', 'AbortError')
        if (done) break
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
        if (buffer.length > 100000) throw new Error('回复格式异常，请重试')
        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2)
          const lines = block.split('\n')
          const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
          const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
          if (!data) continue
          let payload
          try { payload = JSON.parse(data) } catch { throw new Error('AI回复格式异常，请稍后重试') }
          if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('AI回复格式异常，请稍后重试')
          if (event === 'delta') { answer.text += text(payload.text); if (answer.text.length > 20000) throw new Error('回复过长，请缩小问题范围') }
          if (event === 'done') { conversation.value = text(payload.conversationId) || null; if (['mock', 'live'].includes(payload.mode)) mode.value = payload.mode; completed = true }
          if (event === 'error') throw new Error(text(payload.message, 'AI暂时不可用，请稍后重试'))
        }
        scrollMessages()
      }
      if (!completed) throw new Error('回复中断，请重新发送本轮问题')
      answer.state = '已完成'
    } finally {
      if (!completed) answer.state = '回复未完成'
      if (reader) { await reader.cancel().catch(() => {}); reader.releaseLock() }
    }
  })
}
async function makePlan() {
  if (!message.value.trim()) return
  if (!Number.isFinite(budget.value) || budget.value < 1) { error.value = '请输入有效预算'; return }
  await execute(async signal => {
    plan.value = null
    const result = await api.post('/user/ai/meal-plan', { message: message.value.trim(), budget: budget.value }, { signal, timeout: 55000 })
    if (alive) plan.value = { ...result, dishes: list(result?.dishes) }
  })
}
async function askRules() {
  if (!message.value.trim()) return
  await execute(async signal => {
    rules.value = null
    const result = await api.post('/user/ai/rules', { message: message.value.trim() }, { signal, timeout: 55000 })
    if (alive) rules.value = { ...result, sources: list(result?.sources) }
  })
}
function suggestion(value) { message.value = value }
</script>

<template>
  <section class="chat-panel">
    <div class="section-head"><div><span class="eyebrow">YOUR SMART FOOD COMPANION</span><h1>把选择，交给智膳助手</h1><p>说说人数、预算和口味，一起找到适合这一餐的搭配。</p></div><div class="provider-status"><el-tag :type="statusType" effect="plain">{{ modeLabel }}</el-tag><p v-if="model">模型：{{ model }}<span v-if="provider"> · {{ provider }}</span></p><el-button text :loading="statusLoading" :disabled="running" @click="refreshStatus">刷新状态</el-button><small>刷新状态不会调用模型</small></div></div>
    <div class="assistant-layout"><aside class="surface assistant-sidebar"><div class="assistant-symbol" aria-hidden="true">AI</div><h2>今天，想吃点什么？</h2><p>餐品以当前菜单为准，推荐方案的价格由后端重新核算。</p><div class="assistant-mode"><button :class="{active:purpose === 'ordering'}" :disabled="running" @click="purpose = 'ordering'; error = ''">点餐与搭配 <span>聊口味，定预算</span></button><button :class="{active:purpose === 'rules'}" :disabled="running" @click="purpose = 'rules'; error = ''">商家规则问答 <span>配送、退款与食品安全</span></button></div><div class="assistant-note"><strong>{{ modeLabel }}</strong><p>{{ modeDescription }}</p></div></aside>
      <div class="surface assistant-main">
        <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon class="notice" />
        <template v-if="purpose === 'ordering'">
          <div class="conversation-head"><strong>点餐对话</strong><el-button text :disabled="running" @click="reset">新对话</el-button></div>
          <div ref="messagesElement" class="messages" aria-live="polite" aria-label="AI对话记录">
            <div v-if="!messages.length" class="chat-welcome"><span class="eyebrow">LET'S FIND YOUR FAVORITE</span><h2>一餐刚刚好，<br>从一句话开始。</h2><p>可以从下面的问题试起，也可以直接说出你的想法。</p><div class="suggestion-list"><button :disabled="running" @click="suggestion('我30块钱以内，想吃辣的。')">30元以内，想吃辣的</button><button :disabled="running" @click="suggestion('两个人，预算80，想吃辣一点。')">两个人，预算80元</button><button :disabled="running" @click="suggestion('看看我的购物车，帮我确认一下价格。')">看看购物车里的搭配</button></div></div>
            <article v-for="(item, index) in messages" :key="index" :class="['bubble', item.role]"><b>{{ item.role === 'user' ? '我' : '智膳助手' }}</b><p>{{ item.text || (running ? '正在生成…' : '本轮未返回内容') }}</p><small v-if="item.role === 'assistant' && item.state !== '已完成'">{{ item.state }}</small></article>
          </div>
        </template>
        <template v-else><div class="rules-intro"><span class="eyebrow">KNOW BEFORE YOU ORDER</span><h2>关于这一餐的疑问</h2><p>从项目规则文档中检索答案，并展示来源。这里的规则是学习示例，具体处理以商家确认为准。</p><div class="suggestion-list"><button :disabled="running" v-for="question in ['配送超时怎么办？','优惠券为什么不能用？','退款多久？','食品有问题怎么办？']" :key="question" @click="suggestion(question)">{{ question }}</button></div></div><div v-if="rules" class="rule-answer"><h3>规则检索结果</h3><p>{{ text(rules.answer, '暂未找到相关依据') }}</p><div class="source-list"><span>依据：</span><el-tag v-for="source in rules.sources" :key="String(source)" type="info">{{ ruleTitles[source] || text(source, '规则文档') }}</el-tag><span v-if="!rules.sources.length">未找到足够依据</span></div></div></template>
        <div class="chat-composer"><el-input v-model="message" type="textarea" :rows="3" maxlength="2000" show-word-limit :placeholder="purpose === 'rules' ? '输入配送、退款或食品安全问题…' : '例如：两个人，预算80，想吃辣一点'" :disabled="running" aria-label="输入给AI助手的问题" /><div class="chat-actions"><div v-if="purpose === 'ordering'" class="budget-input"><label for="meal-budget">预算 ¥</label><el-input-number id="meal-budget" v-model="budget" :min="1" :max="10000" :precision="2" :disabled="running" aria-label="搭配预算" /></div><div class="chat-submit"><el-button v-if="running" @click="stop">停止生成</el-button><template v-else><el-button v-if="purpose === 'ordering'" :disabled="!canRequest || !message.trim()" @click="makePlan">生成预算搭配</el-button><el-button type="primary" :disabled="!canRequest || !message.trim()" @click="purpose === 'ordering' ? chat() : askRules()">{{ purpose === 'ordering' ? '发送' : '查询规则' }}</el-button></template></div></div></div>
      </div>
    </div>
    <section v-if="plan && purpose === 'ordering'" class="plan-section"><div class="section-head"><div><span class="eyebrow">A MEAL THAT FITS</span><h2>为这一餐推荐的搭配</h2><p>{{ text(plan.reason) }}</p></div><div class="plan-total"><strong>¥{{ money(plan.totalPrice) }}</strong><span>预算 ¥{{ money(plan.budget) }} · 每道一份</span></div></div><div class="product-grid"><article v-for="dish in plan.dishes" :key="dish.id" class="product-card"><FoodImage :src="dish.image" :name="dish.name" /><div class="product-body"><h3>{{ dish.name }}</h3><p>{{ text(dish.description) }}</p><div class="product-bottom"><span class="price"><small>¥</small>{{ money(dish.price) }}</span><el-button type="primary" @click="emit('select-dish', dish.id)">选规格</el-button></div></div></article></div></section>
  </section>
</template>

<style scoped>
.provider-status { max-width: 310px; text-align: right; }
.provider-status p { margin-top: 9px; font-size: 12px; overflow-wrap: anywhere; }
.provider-status small { display: block; font-size: 10px; color: #8b987e; }
.provider-status .el-button { margin-top: 5px; }
@media (max-width: 580px) { .provider-status { text-align: left; max-width: 100%; } }
</style>