<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from './api'
import { useSession } from './stores/session'
import ChatPanel from './ChatPanel.vue'

const session = useSession()
const tab = ref('menu'), error = ref(''), busy = ref(false), registration = ref(false)
const credentials = ref({ username: '', password: '' })
const categories = ref([]), category = ref(null), dishes = ref([]), meals = ref([])
const page = ref(1), total = ref(0), cart = ref({ items: [], totalPrice: 0, checkoutAllowed: false })
const orders = ref([]), orderPage = ref(1), orderTotal = ref(0), selectedOrder = ref(null)
const delivery = ref({ address: '', contact: '' }), requestKey = ref(crypto.randomUUID())
// 所有动作显示统一错误；取消确认不是异常，不向服务端发送写请求。
async function run(action) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function confirmed(message) {
  try { await ElMessageBox.confirm(message, '确认操作', { confirmButtonText: '确认', cancelButtonText: '取消' }); return true }
  catch { return false }
}
async function menu() {
  categories.value = await api.get('/user/categories')
  const params = { categoryId: category.value || undefined, page: page.value, size: 20 }
  const result = await api.get('/user/dishes', { params }); dishes.value = result.records; total.value = result.total
  meals.value = (await api.get('/user/setmeals', { params: { page: 1, size: 100 } })).records
}
async function loadCart() { cart.value = await api.get('/user/cart') }
async function loadOrders() { const data = await api.get('/user/orders', { params: { page: orderPage.value, size: 10 } }); orders.value = data.records; orderTotal.value = data.total }
async function authenticate() {
  if (registration.value) await api.post('/user/auth/register', credentials.value)
  await session.login(credentials.value); credentials.value.password = ''; await menu()
}
async function add(type, id) {
  await api.post('/user/cart', { itemType: type, itemId: id, flavor: '', quantity: 1 })
  ElMessage.success('已加入购物车')
}
async function changeTab() { await run(async () => { if (tab.value === 'cart') await loadCart(); else if (tab.value === 'orders') await loadOrders(); else await menu() }) }
async function checkout() {
  if (!delivery.value.address.trim() || !delivery.value.contact.trim()) throw new Error('请填写收货地址和联系人')
  if (!(await confirmed(`确认提交订单？当前估算 ¥${cart.value.totalPrice}，最终金额由后端核算。`))) return
  await api.post('/user/orders', { ...delivery.value, requestKey: requestKey.value })
  requestKey.value = crypto.randomUUID(); await loadCart(); tab.value = 'orders'; await loadOrders()
}
async function cancelOrder(id) { if (await confirmed('确认取消这笔订单？')) { await api.post(`/user/orders/${id}/cancel`); await loadOrders() } }
async function reorder(id) { if (await confirmed('按当前价格与可售状态重新加入购物车？')) { await api.post(`/user/orders/${id}/again`); tab.value = 'cart'; await loadCart() } }
const statusNames = { PENDING_PAYMENT: '待付款', WAITING_ACCEPT: '待接单', PREPARING: '制作中', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消' }
onMounted(() => { window.addEventListener('sky-unauthorized', session.logout); if (session.token) run(menu) })
onUnmounted(() => window.removeEventListener('sky-unauthorized', session.logout))
</script>

<template>
  <main>
    <header><div><span class="eyebrow">SKY TAKEOUT</span><h1>苍穹智膳</h1><p>认真选餐，安心享用。</p></div><el-button v-if="session.token" @click="session.logout">退出登录</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
    <section v-if="!session.token" class="login">
      <h2>{{ registration ? '创建账号' : '欢迎回来' }}</h2>
      <el-form label-position="top" @submit.prevent="run(authenticate)">
        <el-form-item label="用户名"><el-input v-model="credentials.username" autocomplete="username" maxlength="32" placeholder="3–32位字母、数字或下划线" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="credentials.password" type="password" show-password :autocomplete="registration ? 'new-password' : 'current-password'" maxlength="64" placeholder="至少8位" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy">{{ registration ? '注册并登录' : '登录' }}</el-button>
        <el-button text @click="registration = !registration">{{ registration ? '已有账号' : '注册新账号' }}</el-button>
      </el-form>
    </section>
    <template v-else>
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane label="今日菜单" name="menu" /><el-tab-pane label="购物车" name="cart" /><el-tab-pane label="我的订单" name="orders" /><el-tab-pane label="AI点餐助手" name="ai" />
      </el-tabs>
      <section v-if="tab === 'menu'">
        <div class="section-head"><h2>选择你的好味道</h2><el-select v-model="category" clearable placeholder="全部菜品分类" style="width:220px" @change="page = 1; run(menu)"><el-option v-for="c in categories.filter(c => c.type === 1)" :key="c.id" :label="c.name" :value="c.id" /></el-select></div>
        <el-empty v-if="!dishes.length" description="当前分类暂无可售菜品" />
        <div class="grid"><article v-for="dish in dishes" :key="dish.id" class="card"><img v-if="dish.image" :src="dish.image" :alt="dish.name" loading="lazy"><h3>{{ dish.name }}</h3><p>{{ dish.description }}</p><div class="card-bottom"><b>¥{{ dish.price }}</b><el-button type="primary" :disabled="busy" @click="run(() => add('DISH', dish.id))">加入购物车</el-button></div></article></div>
        <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" @current-change="run(menu)" />
        <h2>精选套餐</h2><el-empty v-if="!meals.length" description="暂无可售套餐" /><div class="grid"><article v-for="meal in meals" :key="meal.id" class="card"><h3>{{ meal.name }}</h3><p>{{ meal.description }}</p><div class="card-bottom"><b>¥{{ meal.price }}</b><el-button :disabled="busy" @click="run(() => add('SETMEAL', meal.id))">加入购物车</el-button></div></article></div>
      </section>
      <section v-else-if="tab === 'cart'">
        <h2>购物车</h2><el-empty v-if="!cart.items.length" description="先挑选几道喜欢的菜吧" />
        <article v-for="item in cart.items" :key="item.id" class="cart-row"><div><b>{{ item.name }}</b><p>{{ item.available ? `单价 ¥${item.unitPrice}` : '暂不可售，请移除' }} · {{ item.quantity }} 份</p></div><div><el-button :disabled="busy" @click="run(async () => { await api.patch(`/user/cart/${item.id}/reduce`); await loadCart() })">减一份</el-button><el-button :disabled="busy" @click="run(async () => { await api.delete(`/user/cart/${item.id}`); await loadCart() })">移除</el-button></div></article>
        <template v-if="cart.items.length"><h3>当前可售商品合计 ¥{{ cart.totalPrice }}</h3><el-form label-position="top"><el-form-item label="收货地址"><el-input v-model="delivery.address" maxlength="300" /></el-form-item><el-form-item label="联系人及电话"><el-input v-model="delivery.contact" maxlength="100" /></el-form-item></el-form><el-button type="primary" :disabled="!cart.checkoutAllowed" :loading="busy" @click="run(checkout)">确认下单</el-button><el-button :disabled="busy" @click="run(async () => { if (await confirmed('清空当前购物车？')) { await api.delete('/user/cart'); await loadCart() } })">清空购物车</el-button><p class="muted">当前为学习项目，未接入真实支付。</p></template>
      </section>
      <section v-else-if="tab === 'orders'">
        <h2>我的订单</h2><el-empty v-if="!orders.length" description="还没有订单" /><article v-for="order in orders" :key="order.id" class="card order"><div><b>{{ statusNames[order.status] }}</b><p>{{ order.number }}</p><p>{{ order.createdAt }} · ¥{{ order.totalAmount }}</p></div><div><el-button @click="run(async () => selectedOrder = await api.get(`/user/orders/${order.id}`))">详情</el-button><el-button @click="run(() => reorder(order.id))">再次下单</el-button><el-button v-if="['PENDING_PAYMENT','WAITING_ACCEPT'].includes(order.status)" type="danger" plain @click="run(() => cancelOrder(order.id))">取消</el-button></div></article>
        <el-pagination v-model:current-page="orderPage" :page-size="10" :total="orderTotal" layout="prev, pager, next" @current-change="run(loadOrders)" />
      </section>
      <ChatPanel v-else-if="tab === 'ai'" />
    </template>
    <el-dialog :model-value="!!selectedOrder" title="订单详情" @close="selectedOrder = null"><template v-if="selectedOrder"><p>{{ selectedOrder.order.address }}</p><p>{{ selectedOrder.order.contact }}</p><p v-for="line in selectedOrder.details" :key="line.id">{{ line.name }} × {{ line.quantity }} · 单价 ¥{{ line.unitPrice }}</p></template></el-dialog>
    <footer>苍穹智膳 · Java 后端与大模型应用学习项目</footer>
  </main>
</template>
