<script setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Client} from '@stomp/stompjs'
import {api, formatApiError, newIdempotencyKey} from '../../api/client.js'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(true)
const error = ref('')
const selected = ref(null)
const detailLoading = ref(false)
const actionLoading = ref(false)
const actionError = ref('')
const realtimeState = ref('正在连接订单通知…')
let realtimeClient

const stateLabels = {
  PENDING_PAYMENT: '待付款', PAYMENT_CONFIRMING: '付款确认中', PAID: '已付款',
  CLOSED: '已关闭', REFUNDING: '退款处理中', REFUNDED: '已退款'
}
const canPay = order => order?.state === 'PENDING_PAYMENT' && Date.parse(order.paymentDeadline) > Date.now()
const canCancel = order => order?.state === 'PENDING_PAYMENT' && Date.parse(order.paymentDeadline) > Date.now()
const money = minor => `¥${(Number(minor || 0) / 100).toFixed(2)}`
const dateTime = value => value ? new Intl.DateTimeFormat('zh-CN', {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value)) : '—'
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/order/me', {params: {page: page.value, size}})
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('订单列表暂时无法读取。')
    orders.value = response.data.data.orders || []
    total.value = Number(response.data.data.total || 0)
    if (route.query.order) await openOrder(String(route.query.order))
  } catch (cause) {
    error.value = formatApiError(cause)
  } finally {
    loading.value = false
  }
}

async function openOrder(orderNo) {
  detailLoading.value = true
  actionError.value = ''
  selected.value = null
  try {
    const response = await api.get(`/api/order/me/${encodeURIComponent(orderNo)}`)
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('订单详情暂时无法读取。')
    selected.value = response.data.data
  } catch (cause) {
    actionError.value = formatApiError(cause)
  } finally {
    detailLoading.value = false
  }
}

async function cancelOrder(order) {
  if (!window.confirm(`确认关闭订单 ${order.orderNo}？关闭后预占库存会释放。`)) return
  actionLoading.value = true
  actionError.value = ''
  const storageKey = `goodskill.cancel:${order.orderNo}`
  let key
  try {
    key = sessionStorage.getItem(storageKey)
    if (!key) {
      key = newIdempotencyKey()
      sessionStorage.setItem(storageKey, key)
    }
  } catch { key = newIdempotencyKey() }
  try {
    const response = await api.post(`/api/order/me/${encodeURIComponent(order.orderNo)}/cancel`, null, {headers: {'Idempotency-Key': key}})
    if (response.data?.code !== 0) throw new Error('订单关闭结果尚未确认。')
    try { sessionStorage.removeItem(storageKey) } catch { /* session storage may be disabled */ }
    await loadOrders()
    await openOrder(order.orderNo)
  } catch (cause) {
    if (cause?.response && cause.response.status < 500) {
      try { sessionStorage.removeItem(storageKey) } catch { /* session storage may be disabled */ }
    }
    actionError.value = formatApiError(cause)
  } finally { actionLoading.value = false }
}

function submitAlipayCheckout(formHtml, popup) {
  if (typeof formHtml !== 'string' || formHtml.length > 200_000) throw new Error('支付页面数据无效。')
  const parsed = new DOMParser().parseFromString(formHtml, 'text/html')
  const sourceForm = parsed.querySelector('form')
  if (!sourceForm || parsed.querySelectorAll('form').length !== 1 || sourceForm.method.toLowerCase() !== 'post') {
    throw new Error('支付机构返回了无法识别的付款表单。')
  }
  const action = new URL(sourceForm.getAttribute('action') || '', window.location.href)
  const allowedOrigins = new Set(['https://openapi.alipay.com', 'https://openapi-sandbox.dl.alipaydev.com'])
  if (!allowedOrigins.has(action.origin) || action.pathname !== '/gateway.do' || action.username || action.password) {
    throw new Error('支付地址未通过安全校验，请联系运营人员。')
  }
  const fields = Array.from(sourceForm.querySelectorAll('input[type="hidden"]'))
  if (fields.length < 3 || fields.length > 100 || parsed.querySelectorAll('input:not([type="hidden"])').length) {
    throw new Error('支付参数格式不正确。')
  }

  const form = popup.document.createElement('form')
  form.method = 'post'
  form.action = action.href
  form.acceptCharset = 'UTF-8'
  for (const field of fields) {
    const name = field.name
    if (!/^[A-Za-z0-9_.]{1,128}$/.test(name) || field.value.length > 20_000) throw new Error('支付参数格式不正确。')
    const input = popup.document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = field.value
    form.append(input)
  }
  popup.document.body.replaceChildren(form)
  popup.opener = null
  form.submit()
}

async function payOrder(order) {
  const popup = window.open('about:blank', '_blank')
  if (!popup) {
    actionError.value = '浏览器拦截了付款窗口，请允许此站点打开新窗口后重试。'
    return
  }
  popup.document.title = '正在连接安全支付…'
  popup.document.body.textContent = '正在连接安全支付…'
  actionLoading.value = true
  actionError.value = ''
  try {
    const response = await api.post(`/api/order/me/${encodeURIComponent(order.orderNo)}/payment?mode=page`)
    const checkout = response.data?.data
    if (response.data?.code !== 0 || !checkout?.formHtml) throw new Error(response.data?.msg || '支付暂不可用。')
    submitAlipayCheckout(checkout.formHtml, popup)
  } catch (cause) {
    popup.close()
    actionError.value = formatApiError(cause)
  } finally { actionLoading.value = false }
}

function closeDetail() {
  selected.value = null
  actionError.value = ''
  if (route.query.order) router.replace({path: '/orders'})
}

async function setPage(next) {
  page.value = next
  await loadOrders()
  window.scrollTo({top: 0, behavior: 'smooth'})
}

onMounted(() => {
  loadOrders()
  const endpoint = new URL('/api/web/ws/trades', window.location.href)
  endpoint.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  realtimeClient = new Client({
    brokerURL: endpoint.href,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
    onConnect: () => {
      realtimeState.value = '订单实时通知已连接'
      realtimeClient.subscribe('/user/queue/trade-events', message => {
        try {
          const event = JSON.parse(message.body)
          if (typeof event.orderNo !== 'string') return
          loadOrders()
          if (selected.value?.order?.orderNo === event.orderNo) openOrder(event.orderNo)
        } catch { /* Ignore malformed advisory events; the HTTP order API remains authoritative. */ }
      })
    },
    onWebSocketClose: () => { realtimeState.value = '实时通知断开，仍可手动刷新' },
    onStompError: () => { realtimeState.value = '实时通知暂不可用，订单查询仍可使用' }
  })
  realtimeClient.activate()
})
onUnmounted(() => realtimeClient?.deactivate())
</script>

<template>
  <section class="orders-page">
    <header class="orders-heading"><div><p class="orders-eyebrow">YOUR PURCHASES / 订单中心</p><h1>每一笔订单，<em>都能查得到。</em></h1><p>支付、取消和配送进展均以服务端记录为准。</p><small class="realtime-label"><i :class="{'realtime-online':realtimeState.includes('已连接')}"></i>{{ realtimeState }}</small></div><div class="order-count"><strong>{{ total.toString().padStart(2, '0') }}</strong><span>笔订单</span></div></header>
    <div v-if="loading" class="orders-state" role="status">正在读取订单…</div>
    <div v-else-if="error" class="orders-state orders-error" role="alert"><p>{{ error }}</p><button class="order-button secondary" @click="loadOrders">重新加载</button></div>
    <div v-else-if="orders.length === 0" class="orders-state"><span class="empty-mark">G</span><h2>还没有订单</h2><p>活动开始后，你的订单会显示在这里。</p><RouterLink to="/activities" class="order-button">去看看活动</RouterLink></div>
    <template v-else>
      <div class="order-table-head"><span>订单编号</span><span>当前状态</span><span>待付金额</span><span>付款截止</span><span></span></div>
      <article v-for="order in orders" :key="order.orderNo" class="order-row">
        <button class="order-main" type="button" @click="openOrder(order.orderNo)"><span class="order-number">{{ order.orderNo }}<small>创建于本活动场次</small></span><span class="order-state" :class="`state-${order.state?.toLowerCase()}`">{{ stateLabels[order.state] || order.state }}</span><strong class="order-amount">{{ money(order.totalAmountMinor) }}</strong><span class="order-deadline">{{ dateTime(order.paymentDeadline) }}</span><span class="row-arrow" aria-hidden="true">↗</span></button>
        <div class="order-actions"><button v-if="canPay(order)" class="order-button compact" :disabled="actionLoading" @click="payOrder(order)">立即支付</button><button v-if="canCancel(order)" class="text-action" :disabled="actionLoading" @click="cancelOrder(order)">关闭订单</button><button v-if="!canPay(order) && order.state === 'PENDING_PAYMENT'" class="text-action" @click="loadOrders">刷新状态</button></div>
      </article>
      <nav v-if="pageCount > 1" class="pagination" aria-label="订单列表分页"><button class="text-action" :disabled="page <= 1" @click="setPage(page - 1)">← 上一页</button><span>第 {{ page }} / {{ pageCount }} 页</span><button class="text-action" :disabled="page >= pageCount" @click="setPage(page + 1)">下一页 →</button></nav>
    </template>

    <div v-if="selected || detailLoading" class="detail-backdrop" @click.self="closeDetail" @keydown.esc="closeDetail">
      <section class="order-detail" role="dialog" aria-modal="true" aria-labelledby="detail-title"><button class="detail-close" aria-label="关闭订单详情" @click="closeDetail">×</button>
        <div v-if="detailLoading" class="orders-state">正在读取订单详情…</div>
        <template v-else-if="selected"><p class="orders-eyebrow">ORDER DETAILS</p><h2 id="detail-title">订单详情</h2><p class="detail-number">{{ selected.order.orderNo }}</p><div class="detail-status"><span>当前状态</span><strong>{{ stateLabels[selected.order.state] || selected.order.state }}</strong></div><div class="detail-lines"><div><span>商品</span><strong>{{ selected.productName }} · {{ selected.skuName }} × {{ selected.quantity }}</strong></div><div><span>订单金额</span><strong>{{ money(selected.order.totalAmountMinor) }}</strong></div><div><span>付款截止</span><strong>{{ dateTime(selected.order.paymentDeadline) }}</strong></div><div><span>履约状态</span><strong>{{ selected.fulfillmentState || '待处理' }}</strong></div></div><h3>收货信息</h3><address v-if="selected.shippingAddress">{{ selected.shippingAddress.recipientName }} · {{ selected.shippingAddress.recipientPhone }}<br>{{ selected.shippingAddress.province }}{{ selected.shippingAddress.city }}{{ selected.shippingAddress.district }}{{ selected.shippingAddress.addressLine }}</address><p v-else class="detail-muted">暂无收货信息</p><p v-if="actionError" class="detail-error" role="alert">{{ actionError }}</p><div class="detail-buttons"><button v-if="canPay(selected.order)" class="order-button" :disabled="actionLoading" @click="payOrder(selected.order)">前往支付宝</button><button v-if="canCancel(selected.order)" class="order-button secondary" :disabled="actionLoading" @click="cancelOrder(selected.order)">关闭订单</button></div></template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.realtime-label{display:flex;align-items:center;gap:6px;margin-top:11px;color:#78857c;font-size:9px}.realtime-label i{width:6px;height:6px;border-radius:50%;background:#c1b6a0}.realtime-label i.realtime-online{background:#59a477;box-shadow:0 0 0 3px rgb(89 164 119 / 14%)}
.orders-page{color:var(--commerce-ink)}.orders-heading{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;margin:18px 0 34px}.orders-eyebrow{margin-bottom:10px;color:#a07655;font-size:9px;font-weight:650;letter-spacing:.17em}.orders-heading h1{margin:0;font:500 clamp(29px,4vw,45px)/1.2 Georgia,"Microsoft YaHei",serif}.orders-heading h1 em{color:var(--commerce-green);font-style:normal}.orders-heading p:not(.orders-eyebrow){margin-top:11px;color:var(--commerce-muted);font-size:12px}.order-count{display:grid;justify-items:end;gap:2px;color:var(--commerce-muted);font-size:10px}.order-count strong{color:var(--commerce-green);font:30px Georgia,serif}.orders-state{display:grid;min-height:270px;place-content:center;justify-items:center;gap:12px;color:var(--commerce-muted);font-size:12px;text-align:center}.orders-error{color:#a74636}.empty-mark{display:grid;width:52px;height:52px;place-items:center;border:1px solid #d7dfd6;border-radius:50%;color:#a07655;font:28px Georgia,serif}.orders-state h2{margin-top:4px;color:var(--commerce-ink);font:500 23px Georgia,serif}.orders-state p{line-height:1.7}.order-table-head,.order-main{display:grid;grid-template-columns:minmax(220px,1.5fr) minmax(110px,.8fr) minmax(110px,.7fr) minmax(140px,.9fr) 24px;gap:12px;align-items:center}.order-table-head{padding:0 19px 10px;color:#87918a;font-size:9px;letter-spacing:.06em}.order-row{margin-bottom:9px;padding:15px 18px 13px;border:1px solid var(--commerce-line);border-radius:13px;background:var(--commerce-surface);transition:border-color .16s ease}.order-row:hover{border-color:#a7bca9}.order-main{width:100%;padding:0;border:0;color:inherit;background:transparent;text-align:left;cursor:pointer}.order-number{display:grid;gap:5px;font:11px ui-monospace,Consolas,monospace}.order-number small{color:#89928c;font: inherit; font-size: 9px}.order-state{justify-self:start;padding:6px 9px;border-radius:999px;color:#82643c;background:#f4ecd9;font-size:9px;white-space:nowrap}.state-paid{color:#276247;background:#e5f0e7}.state-closed,.state-refunded{color:#6f7670;background:#eceee9}.state-refunding,.state-payment_confirming{color:#855a37;background:#f8e9db}.order-amount{color:#a65838;font:17px Georgia,serif}.order-deadline{color:#69756d;font-size:10px}.row-arrow{color:#9b7861}.order-actions{display:flex;justify-content:flex-end;align-items:center;gap:14px;margin-top:10px}.order-button{display:inline-flex;min-height:40px;justify-content:center;align-items:center;padding:0 16px;border:0;border-radius:999px;color:white;background:var(--commerce-green);font: inherit; font-size: 10px; font-weight: 600;text-decoration:none;cursor:pointer}.order-button:hover:not(:disabled){background:var(--commerce-green-deep)}.order-button:disabled{opacity:.5;cursor:not-allowed}.order-button.secondary{border:1px solid #d8ddd7;color:#4e6056;background:transparent}.order-button.compact{min-height:30px;padding:0 13px}.text-action{padding:5px 3px;border:0;color:#8d654e;background:none;font: inherit; font-size: 10px;cursor:pointer}.text-action:disabled{opacity:.4;cursor:not-allowed}.pagination{display:flex;justify-content:center;align-items:center;gap:22px;padding:24px 0;color:var(--commerce-muted);font-size:10px}.detail-backdrop{position:fixed;inset:0;z-index:50;display:flex;justify-content:flex-end;background:rgb(19 37 30 / 38%);backdrop-filter:blur(3px)}.order-detail{position:relative;width:min(490px,100%);height:100%;overflow:auto;padding:38px clamp(24px,5vw,44px);background:#fffefa;box-shadow:-18px 0 50px rgb(0 0 0 / 12%);animation:slidein .2s ease-out}.detail-close{position:absolute;top:20px;right:22px;width:34px;height:34px;border:1px solid var(--commerce-line);border-radius:50%;color:var(--commerce-muted);background:white;font-size:22px;cursor:pointer}.order-detail h2{margin:0;font:500 28px Georgia,serif}.detail-number{margin:8px 0 24px;color:#7d8981;font:10px ui-monospace,Consolas,monospace}.detail-status{display:flex;justify-content:space-between;align-items:center;padding:14px 0;border-top:1px solid var(--commerce-line);border-bottom:1px solid var(--commerce-line);font-size:11px}.detail-status strong{color:var(--commerce-green)}.detail-lines{display:grid;gap:13px;padding:18px 0}.detail-lines>div{display:flex;justify-content:space-between;gap:18px;font-size:10px}.detail-lines span,.detail-muted{color:var(--commerce-muted)}.detail-lines strong{font-weight:550;text-align:right}.order-detail h3{margin:15px 0 9px;font-size:12px}.order-detail address{padding:13px;border-radius:10px;color:#56635b;background:#f5f5ef;font-size:10px;font-style:normal;line-height:1.9}.detail-error{margin-top:14px;color:#a74636;font-size:10px;line-height:1.6}.detail-buttons{display:flex;gap:10px;margin-top:22px}@keyframes slidein{from{transform:translateX(20px);opacity:.5}to{transform:none;opacity:1}}@media(max-width:760px){.order-table-head{display:none}.order-main{grid-template-columns:1fr auto;gap:10px}.order-number{grid-column:1;grid-row:1}.order-state{grid-column:2;grid-row:1}.order-amount{grid-column:1;grid-row:2}.order-deadline{grid-column:2;grid-row:2;text-align:right}.row-arrow{display:none}.order-actions{margin-top:8px}.orders-heading{align-items:flex-start}.order-count{padding-top:6px}}@media(max-width:480px){.orders-heading{margin-top:4px}.order-number{font-size:9px}.order-deadline{font-size:9px}.order-row{padding:13px}.order-actions{gap:9px}}
</style>
