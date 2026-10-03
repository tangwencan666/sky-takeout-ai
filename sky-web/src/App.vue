<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorMessage } from './api'
import { useSession } from './stores/session'
import { money, dateTime, list, text, flavorOptions } from './format'
import FoodImage from './FoodImage.vue'
import ChatPanel from './ChatPanel.vue'

const session = useSession()
const tab = ref('menu'), error = ref(''), busy = ref(false), loading = ref(false)
const registration = ref(false), credentials = ref({ username: '', password: '' })
const categories = ref([]), category = ref(null), dishes = ref([]), meals = ref([]), keyword = ref('')
const page = ref(1), total = ref(0), cart = ref(emptyCart())
const orders = ref([]), orderPage = ref(1), orderTotal = ref(0), selectedOrder = ref(null)
const selection = ref(null), choices = ref({})
const delivery = ref({ address: '', contact: '' }), requestKey = ref(crypto.randomUUID())
const cartCount = computed(() => cart.value.items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0))
const selectedCategory = computed(() => categories.value.find(item => item.id === category.value))
const statusNames = { PENDING_PAYMENT: '待付款', WAITING_ACCEPT: '待接单', PREPARING: '制作中', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消' }
let menuRequest = 0

function emptyCart() { return { items: [], totalPrice: 0, checkoutAllowed: false } }
// 旧账号的私有界面立即清空；API层同时中止旧身份请求，不让延迟响应回填。
watch(() => session.token, () => {
  menuRequest += 1
  tab.value = 'menu'; selectedOrder.value = null; selection.value = null
  orders.value = []; orderTotal.value = 0; cart.value = emptyCart()
  categories.value = []; dishes.value = []; meals.value = []
  delivery.value = { address: '', contact: '' }; category.value = null; page.value = 1
  orderPage.value = 1; keyword.value = ''; requestKey.value = crypto.randomUUID(); error.value = ''
})

async function run(action) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function confirmed(message) {
  try {
    await ElMessageBox.confirm(message, '确认操作', { confirmButtonText: '确认', cancelButtonText: '取消', closeOnClickModal: false })
    return true
  } catch { return false }
}
async function menu() {
  const request = ++menuRequest
  loading.value = true; error.value = ''
  try {
    const params = { categoryId: selectedCategory.value?.type === 1 ? category.value : undefined, keyword: keyword.value.trim() || undefined, page: page.value, size: 12 }
    const [categoryData, dishData, mealData] = await Promise.all([
      api.get('/user/categories'), api.get('/user/dishes', { params }),
      api.get('/user/setmeals', { params: { page: 1, size: 100, categoryId: selectedCategory.value?.type === 2 ? category.value : undefined } })
    ])
    if (request !== menuRequest) return
    categories.value = list(categoryData)
    dishes.value = list(dishData?.records); total.value = Number(dishData?.total) || 0
    meals.value = list(mealData?.records).filter(item => !keyword.value.trim() || text(item.name).includes(keyword.value.trim()))
  } catch (e) {
    if (request === menuRequest) {
      error.value = errorMessage(e)
      dishes.value = []; meals.value = []; total.value = 0
    }
  }
  finally { if (request === menuRequest) loading.value = false }
}
async function loadCart() {
  const data = await api.get('/user/cart')
  cart.value = { ...emptyCart(), ...data, items: list(data?.items) }
}
async function loadOrders() {
  const data = await api.get('/user/orders', { params: { page: orderPage.value, size: 10 } })
  orders.value = list(data?.records); orderTotal.value = Number(data?.total) || 0
}
async function authenticate() {
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(credentials.value.username)) throw new Error('用户名需为3–32位字母、数字或下划线')
  if (credentials.value.password.length < 8) throw new Error('密码至少需要8位')
  if (registration.value) await api.post('/user/auth/register', credentials.value)
  await session.login(credentials.value)
  credentials.value.password = ''
  await Promise.all([menu(), loadCart()])
}
async function navigate(next) {
  if (busy.value) return
  tab.value = next; error.value = ''
  if (next === 'menu') await menu()
  else if (next === 'cart') await run(loadCart)
  else if (next === 'orders') await run(loadOrders)
}
async function chooseCategory(id) { category.value = id; page.value = 1; await menu() }
async function openItem(type, id) {
  const detail = await api.get(`/user/${type === 'DISH' ? 'dishes' : 'setmeals'}/${id}`)
  const product = type === 'DISH' ? detail.dish : detail.setmeal
  const flavors = type === 'DISH' ? list(detail.flavors).map(item => ({ ...item, options: flavorOptions(item.options) })) : []
  choices.value = Object.fromEntries(flavors.map(item => [item.id, item.options[0] || '']))
  selection.value = { type, product, flavors, dishes: list(detail.dishes) }
}
async function addSelected() {
  const item = selection.value
  if (!item) return
  const flavor = item.flavors.map(group => `${group.name}：${choices.value[group.id]}`).join('；')
  if (flavor.length > 200) throw new Error('口味备注过长，请联系商家确认')
  await api.post('/user/cart', { itemType: item.type, itemId: item.product.id, flavor, quantity: 1 })
  selection.value = null
  await loadCart(); ElMessage.success('已加入购物车')
}
async function changeQuantity(item, delta) {
  if (delta > 0) await api.post('/user/cart', { itemType: item.itemType, itemId: item.itemId, flavor: item.flavor || '', quantity: 1 })
  else await api.patch(`/user/cart/${item.id}/reduce`)
  await loadCart()
}
async function checkout() {
  if (!delivery.value.address.trim() || !delivery.value.contact.trim()) throw new Error('请填写收货地址和联系人')
  if (!(await confirmed(`确认提交订单？当前估算 ¥${money(cart.value.totalPrice)}，最终金额由后端核算。`))) return
  await api.post('/user/orders', { ...delivery.value, requestKey: requestKey.value })
  requestKey.value = crypto.randomUUID()
  await loadCart(); tab.value = 'orders'; orderPage.value = 1; await loadOrders()
  ElMessage.success('订单已提交，可在订单详情中查看')
}
async function cancelOrder(id) {
  if (await confirmed('确认取消这笔订单？')) { await api.post(`/user/orders/${id}/cancel`); await loadOrders() }
}
async function reorder(id) {
  if (await confirmed('按当前价格与可售状态重新加入购物车？')) {
    await api.post(`/user/orders/${id}/again`); tab.value = 'cart'; await loadCart()
  }
}
function unauthorized() {
  session.logout(); ElMessage.warning('登录已过期，请重新登录')
}
onMounted(() => {
  window.addEventListener('sky-unauthorized', unauthorized)
  if (session.token) run(async () => { await session.restore(); await Promise.all([menu(), loadCart()]) })
})
onUnmounted(() => window.removeEventListener('sky-unauthorized', unauthorized))
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <div class="header-inner">
        <a class="brand" href="#" aria-label="苍穹智膳首页" @click.prevent="session.token && navigate('menu')"><span class="brand-mark" aria-hidden="true">膳</span><span><strong>苍穹智膳</strong><small>SKY TAKEOUT AI</small></span></a>
        <nav v-if="session.token" class="main-nav" aria-label="主导航">
          <button v-for="item in [{key:'menu',label:'今日菜单'},{key:'orders',label:'我的订单'},{key:'ai',label:'AI点餐助手'}]" :key="item.key" :class="{active:tab === item.key}" :aria-current="tab === item.key ? 'page' : undefined" :disabled="busy" @click="navigate(item.key)">{{ item.label }}</button>
        </nav>
        <div v-if="session.token" class="header-actions"><el-button class="cart-link" :type="tab === 'cart' ? 'primary' : 'default'" :disabled="busy" @click="navigate('cart')">购物车 <span class="count-pill">{{ cartCount }}</span></el-button><span class="user-name">{{ text(session.user?.username, '已登录') }}</span><el-button text :disabled="busy" @click="session.logout">退出</el-button></div>
        <span v-else class="header-caption">一餐好味，一天好心情</span>
      </div>
    </header>

    <main class="page-container">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
      <section v-if="!session.token" class="login-layout">
        <div class="login-intro"><span class="eyebrow">一日三餐，认真对待</span><h1>今天的好味道，<br>从这里开始。</h1><p>选一道合心意的菜，搭一份刚刚好的套餐。<br>让智膳助手帮你打理选择，把时间留给生活。</p><img src="/images/meal-placeholder.svg" alt="餐品搭配示意图" class="login-art"><span class="art-caption">餐品搭配示意</span></div>
        <div class="login-card"><span class="eyebrow">WELCOME TO SKY</span><h2>{{ registration ? '创建你的账号' : '欢迎回来' }}</h2><p>登录后开始选餐，查看你的专属订单。</p>
          <el-form label-position="top" @submit.prevent="run(authenticate)">
            <el-form-item label="用户名"><el-input v-model="credentials.username" autocomplete="username" maxlength="32" placeholder="3–32位字母、数字或下划线" :disabled="busy" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="credentials.password" type="password" show-password :autocomplete="registration ? 'new-password' : 'current-password'" maxlength="64" placeholder="请输入至少8位密码" :disabled="busy" /></el-form-item>
            <el-button type="primary" class="full-width" native-type="submit" :loading="busy">{{ registration ? '注册并登录' : '登录' }}</el-button>
            <p class="login-switch">{{ registration ? '已经有账号？' : '第一次来到智膳？' }} <el-button text :disabled="busy" @click="registration = !registration; error = ''">{{ registration ? '前往登录' : '注册新账号' }}</el-button></p>
          </el-form><div class="login-footnote">当前为开发演示环境 · 用户端入口</div>
        </div>
      </section>

      <template v-else>
        <section v-if="tab === 'menu'">
          <div class="menu-hero"><div><span class="eyebrow">YOUR DAILY GOOD FOOD</span><h1>好好吃饭，<br>就从这一餐开始。</h1><p>热菜、主食与精选搭配，总有一份合你心意。</p><el-button type="primary" plain @click="navigate('ai')">让 AI 帮我选一餐 <span aria-hidden="true">↗</span></el-button></div><div class="hero-art"><img src="/images/meal-placeholder.svg" alt="精选搭配示意图"><span class="hero-badge">按预算，选好餐</span></div></div>
          <div class="section-head"><div><span class="eyebrow">EXPLORE THE MENU</span><h2>今日菜单</h2></div><el-input v-model="keyword" class="menu-search" clearable placeholder="搜索菜品或套餐" maxlength="100" aria-label="搜索菜品或套餐" @keyup.enter="page = 1; menu()" @clear="page = 1; menu()"><template #append><el-button @click="page = 1; menu()">搜索</el-button></template></el-input></div>
          <div class="category-bar" role="group" aria-label="餐品分类"><button :class="{active:category === null}" @click="chooseCategory(null)">全部餐品</button><button v-for="item in categories" :key="item.id" :class="{active:category === item.id}" @click="chooseCategory(item.id)">{{ item.name }}</button></div>
          <div v-if="loading" class="product-grid" aria-label="正在加载菜单"><div v-for="n in 6" :key="n" class="product-card skeleton-card"><el-skeleton :rows="4" animated /></div></div>
          <template v-else>
            <template v-if="selectedCategory?.type !== 2"><div class="subsection-heading"><h3>{{ selectedCategory?.name || '新鲜好味' }}</h3><span>{{ total }} 道可售菜品</span></div><el-empty v-if="!dishes.length" description="没有找到相关菜品，换个分类或关键词试试" /><div class="product-grid"><article v-for="dish in dishes" :key="dish.id" class="product-card"><FoodImage :src="dish.image" :name="dish.name" /><div class="product-body"><h3>{{ dish.name }}</h3><p>{{ text(dish.description, '现做美味，随心选择') }}</p><div class="product-bottom"><span class="price"><small>¥</small>{{ money(dish.price) }}</span><el-button type="primary" :disabled="busy" @click="run(() => openItem('DISH', dish.id))">选规格</el-button></div></div></article></div><el-pagination v-if="total > 12" v-model:current-page="page" :page-size="12" :total="total" layout="prev, pager, next" @current-change="menu" /></template>
            <template v-if="!category || selectedCategory?.type === 2"><div class="subsection-heading meal-heading"><div><span class="eyebrow">BETTER TOGETHER</span><h3>精选套餐</h3></div><span>搭配好，更省心</span></div><el-empty v-if="!meals.length" description="暂无相关可售套餐" /><div class="product-grid"><article v-for="meal in meals" :key="meal.id" class="product-card"><FoodImage :name="meal.name" meal /><div class="product-body"><h3>{{ meal.name }}</h3><p>{{ text(meal.description) }}</p><div class="product-bottom"><span class="price"><small>¥</small>{{ money(meal.price) }}</span><el-button :disabled="busy" @click="run(() => openItem('SETMEAL', meal.id))">查看搭配</el-button></div></div></article></div></template>
          </template>
        </section>

        <section v-else-if="tab === 'cart'">
          <div class="section-head"><div><span class="eyebrow">YOUR SELECTION</span><h1>我的购物车</h1><p>认真挑选的好味道，都在这里。</p></div><el-button :disabled="busy" @click="navigate('menu')">继续选餐</el-button></div>
          <el-skeleton v-if="busy && !cart.items.length" :rows="4" animated />
          <el-empty v-else-if="!cart.items.length" description="购物车还是空的，先选一道喜欢的菜吧"><el-button type="primary" @click="navigate('menu')">去逛菜单</el-button></el-empty>
          <div v-else class="checkout-layout"><div class="surface cart-list"><article v-for="item in cart.items" :key="item.id" class="cart-row"><div class="cart-item-copy"><span class="item-type">{{ item.itemType === 'SETMEAL' ? '套餐' : '菜品' }}</span><h3>{{ item.name }}</h3><p v-if="item.flavor" class="flavor-copy">{{ item.flavor }}</p><span :class="{'unavailable':!item.available}">{{ item.available ? `单价 ¥${money(item.unitPrice)}` : '暂不可售，请移除后再下单' }}</span></div><div class="cart-item-controls"><div class="quantity-control"><button aria-label="减少一份" :disabled="busy" @click="run(() => changeQuantity(item, -1))">−</button><span>{{ item.quantity }}</span><button aria-label="增加一份" :disabled="busy || !item.available || item.quantity >= 99" @click="run(() => changeQuantity(item, 1))">+</button></div><strong class="line-price">¥{{ money(item.subtotal) }}</strong><el-button text :disabled="busy" @click="run(async () => { await api.delete(`/user/cart/${item.id}`); await loadCart() })">移除</el-button></div></article><div class="cart-clear"><el-button text :disabled="busy" @click="run(async () => { if (await confirmed('清空当前购物车？')) { await api.delete('/user/cart'); await loadCart() } })">清空购物车</el-button></div></div><aside class="surface checkout-card"><h2>配送信息</h2><el-form label-position="top"><el-form-item label="收货地址"><el-input v-model="delivery.address" type="textarea" :rows="2" maxlength="300" placeholder="请输入详细地址、楼栋及门牌号" /></el-form-item><el-form-item label="联系人及电话"><el-input v-model="delivery.contact" maxlength="100" placeholder="姓名与联系电话" /></el-form-item></el-form><div class="checkout-total"><span>可售商品合计</span><strong>¥{{ money(cart.totalPrice) }}</strong></div><p v-if="!cart.checkoutAllowed" class="unavailable">购物车有不可售商品，请先移除。</p><el-button type="primary" class="full-width" :disabled="!cart.checkoutAllowed" :loading="busy" @click="run(checkout)">确认下单</el-button><p class="muted">最终金额由服务端核算。当前为学习项目，未接入真实支付。</p></aside></div>
        </section>

        <section v-else-if="tab === 'orders'">
          <div class="section-head"><div><span class="eyebrow">YOUR FOOD JOURNEY</span><h1>我的订单</h1><p>每一餐的记录，都值得认真对待。</p></div><el-button :loading="busy" @click="run(loadOrders)">刷新订单</el-button></div>
          <el-skeleton v-if="busy && !orders.length" :rows="5" animated /><el-empty v-else-if="!orders.length" description="还没有订单，今天想吃点什么？"><el-button type="primary" @click="navigate('menu')">去选餐</el-button></el-empty>
          <article v-for="order in orders" :key="order.id" class="surface order-card"><div class="order-top"><span class="order-status" :class="order.status?.toLowerCase()">{{ statusNames[order.status] || '状态待更新' }}</span><span>{{ dateTime(order.createdAt) }}</span></div><div class="order-main"><div><p class="order-number">订单号 {{ order.number }}</p><strong class="order-amount">¥{{ money(order.totalAmount) }}</strong><p class="muted" v-if="order.status === 'PENDING_PAYMENT'">开发演示订单 · 当前未接入支付</p></div><div class="order-actions"><el-button :disabled="busy" @click="run(async () => { selectedOrder = await api.get(`/user/orders/${order.id}`) })">订单详情</el-button><el-button type="primary" plain :disabled="busy" @click="run(() => reorder(order.id))">再来一单</el-button><el-button v-if="['PENDING_PAYMENT','WAITING_ACCEPT'].includes(order.status)" text :disabled="busy" @click="run(() => cancelOrder(order.id))">取消订单</el-button></div></div></article>
          <el-pagination v-if="orderTotal > 10" v-model:current-page="orderPage" :page-size="10" :total="orderTotal" layout="prev, pager, next" @current-change="run(loadOrders)" />
        </section>
        <ChatPanel v-else-if="tab === 'ai'" :key="session.token" @select-dish="id => run(() => openItem('DISH', id))" />
      </template>

      <el-dialog :model-value="!!selection" :title="selection?.product?.name || '餐品详情'" width="540px" class="responsive-dialog" @close="selection = null"><template v-if="selection"><FoodImage :src="selection.product.image" :name="selection.product.name" :meal="selection.type === 'SETMEAL'" /><p>{{ text(selection.product.description) }}</p><template v-if="selection.type === 'DISH'"><div v-for="group in selection.flavors" :key="group.id" class="flavor-group"><h3>{{ group.name }}</h3><el-radio-group v-model="choices[group.id]"><el-radio-button v-for="option in group.options" :key="option" :value="option">{{ option }}</el-radio-button></el-radio-group></div><p v-if="!selection.flavors.length" class="muted">这道菜没有可选规格。</p></template><ul v-else class="meal-lines"><li v-for="line in selection.dishes" :key="line.dishId"><span>{{ line.name }}</span><b>× {{ line.quantity }}</b></li></ul><div class="dialog-checkout"><span class="price"><small>¥</small>{{ money(selection.product.price) }}</span><el-button type="primary" :loading="busy" @click="run(addSelected)">加入购物车</el-button></div></template></el-dialog>
      <el-dialog :model-value="!!selectedOrder" title="订单详情" width="620px" class="responsive-dialog" @close="selectedOrder = null"><template v-if="selectedOrder?.order"><div class="detail-header"><el-tag>{{ statusNames[selectedOrder.order.status] || '状态待更新' }}</el-tag><span>{{ dateTime(selectedOrder.order.createdAt) }}</span></div><p class="order-number">订单号 {{ selectedOrder.order.number }}</p><h3>配送信息</h3><p>{{ selectedOrder.order.address }}<br>{{ selectedOrder.order.contact }}</p><h3>餐品明细</h3><ul class="meal-lines"><li v-for="line in list(selectedOrder.details)" :key="line.id"><span>{{ line.name }} × {{ line.quantity }}<small v-if="line.flavor">{{ line.flavor }}</small></span><b>¥{{ money(line.unitPrice) }} / 份</b></li></ul><div class="checkout-total"><span>订单总额</span><strong>¥{{ money(selectedOrder.order.totalAmount) }}</strong></div></template></el-dialog>
    </main>
    <footer class="site-footer"><div><strong>苍穹智膳</strong><span>认真选餐，安心享用。</span></div><p>Java 后端与大模型应用学习项目 · 开发演示环境</p></footer>
  </div>
</template>
