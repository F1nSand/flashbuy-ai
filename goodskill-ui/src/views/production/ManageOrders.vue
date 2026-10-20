<script setup>
import {computed, onMounted, ref} from 'vue'
import {api, formatApiError, newIdempotencyKey} from '../../api/client.js'
import {useRoute, useRouter} from 'vue-router'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const stateFilter = ref('')
const loading = ref(true)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')
const selected = ref(null)
const detailLoading = ref(false)
const shipment = ref({carrierCode: '', trackingNo: ''})
const stateLabels = {PENDING_PAYMENT: '待付款', PAYMENT_CONFIRMING: '付款确认中', PAID: '已付款', CLOSED: '已关闭', REFUNDING: '退款处理中', REFUNDED: '已退款'}
const fulfillmentLabels = {UNFULFILLED: '待发货', PACKING: '备货中', SHIPPED: '已发货', DELIVERED: '已送达', CANCELLED: '已取消'}
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))
const money = minor => `¥${(Number(minor || 0) / 100).toFixed(2)}`
const dateTime = value => value ? new Intl.DateTimeFormat('zh-CN', {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value)) : '—'
const mayShip = order => order?.state === 'PAID' && order?.fulfillmentState === 'UNFULFILLED'
const mayRefund = order => order?.state === 'PAID' && order?.fulfillmentState === 'UNFULFILLED'

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {page: page.value, size}
    if (stateFilter.value) params.state = stateFilter.value
    const response = await api.get('/api/order/manage/orders', {params})
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('订单工作台暂时无法读取。')
    orders.value = response.data.data.orders || []
    total.value = Number(response.data.data.total || 0)
    if (route.query.order) await openOrder(String(route.query.order))
  } catch (cause) { error.value = formatApiError(cause) }
  finally { loading.value = false }
}

async function openOrder(orderNo) {
  detailLoading.value = true
  success.value = ''
  selected.value = null
  try {
    const response = await api.get(`/api/order/manage/orders/${encodeURIComponent(orderNo)}`)
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('订单详情暂时无法读取。')
    selected.value = response.data.data
    shipment.value = {carrierCode: selected.value.carrierCode || '', trackingNo: selected.value.trackingNo || ''}
  } catch (cause) { error.value = formatApiError(cause) }
  finally { detailLoading.value = false }
}

async function shipOrder() {
  if (!selected.value || !shipment.value.carrierCode.trim() || !shipment.value.trackingNo.trim()) return
  if (!window.confirm(`确认登记订单 ${selected.value.order.orderNo} 已发货？`)) return
  actionLoading.value = true
  error.value = ''
  try {
    const response = await api.post(`/api/order/manage/orders/${encodeURIComponent(selected.value.order.orderNo)}/shipment`, {
      carrierCode: shipment.value.carrierCode.trim(), trackingNo: shipment.value.trackingNo.trim()
    })
    if (response.data?.code !== 0) throw new Error(response.data?.msg || '发货登记失败。')
    success.value = '发货信息已保存并写入审计记录。'
    await refreshSelected()
  } catch (cause) { error.value = formatApiError(cause) }
  finally { actionLoading.value = false }
}

async function refundOrder() {
  const current = selected.value?.order
  if (!current) return
  const reason = window.prompt('请填写整单退款原因（最多 500 字）。')
  if (!reason?.trim()) return
  if (reason.trim().length > 500) { error.value = '退款原因不能超过 500 个字符。'; return }
  if (!window.confirm(`确认申请订单 ${current.orderNo} 整单退款 ${money(current.totalAmountMinor)}？`)) return
  const idempotencyStore = `goodskill.refund:${current.orderNo}`
  const fingerprint = `${current.orderNo}:${reason.trim()}`
  let key
  try {
    const saved = JSON.parse(sessionStorage.getItem(idempotencyStore) || 'null')
    key = saved?.fingerprint === fingerprint ? saved.key : newIdempotencyKey()
    sessionStorage.setItem(idempotencyStore, JSON.stringify({fingerprint, key}))
  } catch { key = newIdempotencyKey() }
  actionLoading.value = true
  error.value = ''
  try {
    const response = await api.post(`/api/order/manage/${encodeURIComponent(current.orderNo)}/refund`, {reason: reason.trim()}, {headers: {'Idempotency-Key': key}})
    if (response.data?.code !== 0) throw new Error(response.data?.msg || '退款申请未能完成。')
    try { sessionStorage.removeItem(idempotencyStore) } catch { /* storage may be disabled */ }
    success.value = `整单退款申请已受理：${response.data.data.refundNo}。退款结果以渠道回执为准。`
    await refreshSelected()
  } catch (cause) {
    if (cause?.response && cause.response.status < 500) {
      try { sessionStorage.removeItem(idempotencyStore) } catch { /* storage may be disabled */ }
    }
    error.value = formatApiError(cause)
  } finally { actionLoading.value = false }
}

async function refreshSelected() {
  await load()
  if (selected.value) await openOrder(selected.value.order.orderNo)
}

async function applyFilter() {
  page.value = 1
  await load()
}

async function changePage(next) {
  page.value = next
  await load()
  window.scrollTo({top: 0, behavior: 'smooth'})
}

function closeDetail() {
  selected.value = null
  if (route.query.order) router.replace({path: '/manage/orders'})
}

onMounted(load)
</script>

<template>
  <section class="manage-orders">
    <header class="orders-title"><div><p class="manage-eyebrow">TRADE DESK / 订单处理</p><h1>订单交付，<em>每一步都留痕。</em></h1><p>订单状态与库存以交易数据库为准；运营动作会写入审计。</p></div><div class="total-count"><strong>{{ total }}</strong><small>笔记录</small></div></header>
    <div class="toolbar"><label>订单状态<select v-model="stateFilter" @change="applyFilter"><option value="">全部状态</option><option v-for="(label, state) in stateLabels" :key="state" :value="state">{{ label }}</option></select></label><button class="quiet-refresh" :disabled="loading" @click="load">↻ 刷新</button></div>
    <p v-if="success" class="banner success" role="status">{{ success }}</p><p v-if="error" class="banner error" role="alert">{{ error }}</p>
    <div v-if="loading" class="orders-empty">正在读取订单…</div>
    <div v-else-if="orders.length === 0" class="orders-empty"><span>—</span><h2>没有匹配订单</h2><p>新的支付订单会出现在这里。</p></div>
    <template v-else><div class="admin-order-head"><span>订单 / 买家</span><span>商品</span><span>状态</span><span>金额</span><span>创建时间</span></div><button v-for="order in orders" :key="order.orderNo" class="admin-order-row" type="button" @click="openOrder(order.orderNo)"><span><strong>{{ order.orderNo }}</strong><small>用户 {{ order.buyerId }}</small></span><span><strong>{{ order.productName }}</strong><small>{{ order.skuName }} × {{ order.quantity }}</small></span><span class="status-pill" :class="`state-${order.state.toLowerCase()}`">{{ stateLabels[order.state] || order.state }}<small>{{ fulfillmentLabels[order.fulfillmentState] || order.fulfillmentState }}</small></span><strong class="amount">{{ money(order.totalAmountMinor) }}</strong><small class="created-time">{{ dateTime(order.createdAt) }}</small></button>
      <nav v-if="pageCount > 1" class="pagination"><button :disabled="page <= 1" @click="changePage(page - 1)">← 上一页</button><span>第 {{ page }} / {{ pageCount }} 页</span><button :disabled="page >= pageCount" @click="changePage(page + 1)">下一页 →</button></nav>
    </template>
    <div v-if="selected || detailLoading" class="drawer-backdrop" @click.self="closeDetail"><section class="order-drawer" role="dialog" aria-modal="true" aria-labelledby="managed-order-title"><button class="drawer-close" aria-label="关闭详情" @click="closeDetail">×</button><div v-if="detailLoading" class="orders-empty">正在读取…</div><template v-else-if="selected"><p class="manage-eyebrow">ORDER / {{ selected.order.orderNo }}</p><h2 id="managed-order-title">订单处理</h2><div class="drawer-status"><span>{{ stateLabels[selected.order.state] || selected.order.state }}</span><strong>{{ fulfillmentLabels[selected.order.fulfillmentState] || selected.order.fulfillmentState }}</strong></div><div class="drawer-section"><div><span>活动与商品</span><strong>#{{ selected.order.activityId }} · {{ selected.order.productName }} / {{ selected.order.skuName }} × {{ selected.order.quantity }}</strong></div><div><span>订单金额</span><strong>{{ money(selected.order.totalAmountMinor) }}</strong></div><div><span>买家 ID</span><strong>{{ selected.order.buyerId }}</strong></div><div><span>付款截止</span><strong>{{ dateTime(selected.order.paymentDeadline) }}</strong></div></div><h3>收货地址</h3><address>{{ selected.shippingAddress.recipientName }} · {{ selected.shippingAddress.recipientPhone }}<br>{{ selected.shippingAddress.province }}{{ selected.shippingAddress.city }}{{ selected.shippingAddress.district }}{{ selected.shippingAddress.addressLine }}</address><p v-if="selected.refundState" class="refund-state">退款状态：{{ selected.refundState }}</p><template v-if="mayShip(selected.order)"><h3>登记发货</h3><div class="shipment-form"><label>承运商代码<input v-model="shipment.carrierCode" maxlength="40" placeholder="例如 SF / YTO" autocomplete="off"></label><label>物流单号<input v-model="shipment.trackingNo" maxlength="100" autocomplete="off"></label><button class="primary-action" :disabled="actionLoading || !shipment.carrierCode.trim() || !shipment.trackingNo.trim()" @click="shipOrder">{{ actionLoading ? '正在提交…' : '确认发货' }}</button></div></template><p v-else-if="selected.order.fulfillmentState === 'SHIPPED'" class="tracking-note">{{ selected.carrierCode }} · {{ selected.trackingNo }}</p><div class="drawer-actions"><button v-if="mayRefund(selected.order)" class="refund-action" :disabled="actionLoading" @click="refundOrder">申请整单退款</button><span v-if="selected.order.state === 'PENDING_PAYMENT'">待付款订单暂不能申请退款。</span><span v-else-if="selected.order.fulfillmentState !== 'UNFULFILLED'">当前履约状态不支持退款。</span></div></template></section></div>
  </section>
</template>

<style scoped>
.manage-orders{color:var(--commerce-ink)}.orders-title{display:flex;justify-content:space-between;align-items:flex-end;gap:22px;margin:16px 0 24px}.manage-eyebrow{margin-bottom:9px;color:#a07655;font-size:9px;font-weight:650;letter-spacing:.16em}.orders-title h1{margin:0;font:500 clamp(28px,3.8vw,43px)/1.2 Georgia,"Microsoft YaHei",serif}.orders-title h1 em{color:var(--commerce-green);font-style:normal}.orders-title>div>p:last-child{margin-top:10px;color:var(--commerce-muted);font-size:11px}.total-count{display:grid;justify-items:end;color:var(--commerce-muted);font-size:10px}.total-count strong{color:var(--commerce-green);font:30px Georgia,serif}.toolbar{display:flex;justify-content:space-between;align-items:center;margin:22px 0 14px}.toolbar label{display:flex;align-items:center;gap:10px;color:var(--commerce-muted);font-size:10px}.toolbar select{height:34px;padding:0 28px 0 10px;border:1px solid var(--commerce-line);border-radius:8px;color:var(--commerce-ink);background:white;font: inherit; font-size: 10px}.quiet-refresh{padding:7px 10px;border:1px solid var(--commerce-line);border-radius:999px;color:#536258;background:transparent;font: inherit; font-size: 10px;cursor:pointer}.quiet-refresh:disabled{opacity:.5}.banner{margin-bottom:10px;padding:10px 13px;border-radius:9px;font-size:10px;line-height:1.6}.banner.success{color:#276247;background:#e7f1e8}.banner.error{color:#9c4938;background:#f8eae3}.orders-empty{display:grid;min-height:230px;place-content:center;justify-items:center;gap:10px;color:var(--commerce-muted);font-size:11px}.orders-empty h2{color:var(--commerce-ink);font:500 22px Georgia,serif}.admin-order-head,.admin-order-row{display:grid;grid-template-columns:minmax(190px,1.35fr) minmax(150px,1.2fr) minmax(105px,.8fr) minmax(90px,.65fr) minmax(115px,.8fr);gap:12px;align-items:center}.admin-order-head{padding:0 14px 9px;color:#87918a;font-size:9px}.admin-order-row{width:100%;min-height:65px;margin-bottom:7px;padding:12px 14px;border:1px solid var(--commerce-line);border-radius:11px;color:var(--commerce-ink);background:var(--commerce-surface);text-align:left;cursor:pointer}.admin-order-row:hover{border-color:#a7bca9;background:#fff}.admin-order-row>span:first-child,.admin-order-row>span:nth-child(2){display:grid;gap:5px;min-width:0}.admin-order-row>span:first-child strong{font:10px ui-monospace,Consolas,monospace}.admin-order-row>span:nth-child(2) strong{overflow:hidden;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.admin-order-row small{overflow:hidden;color:var(--commerce-muted);font-size:9px;text-overflow:ellipsis;white-space:nowrap}.status-pill{justify-self:start;padding:6px 8px;border-radius:8px;color:#82643c;background:#f4ecd9;font-size:9px}.status-pill small{display:block;margin-top:4px;color:inherit}.state-paid{color:#276247;background:#e5f0e7}.state-closed,.state-refunded{color:#6f7670;background:#eceee9}.state-refunding,.state-payment_confirming{color:#855a37;background:#f8e9db}.amount{color:#a65838;font:16px Georgia,serif}.created-time{color:#6f7a72}.pagination{display:flex;justify-content:center;align-items:center;gap:18px;padding:20px;color:var(--commerce-muted);font-size:10px}.pagination button{border:0;color:#8d654e;background:none;font:inherit;cursor:pointer}.pagination button:disabled{opacity:.4;cursor:not-allowed}.drawer-backdrop{position:fixed;inset:0;z-index:50;display:flex;justify-content:flex-end;background:rgb(19 37 30 / 40%);backdrop-filter:blur(3px)}.order-drawer{position:relative;width:min(540px,100%);height:100%;overflow:auto;padding:40px clamp(23px,4vw,42px);background:#fffefa;box-shadow:-18px 0 50px rgb(0 0 0 / 12%)}.drawer-close{position:absolute;top:18px;right:22px;width:34px;height:34px;border:1px solid var(--commerce-line);border-radius:50%;color:var(--commerce-muted);background:white;font-size:22px;cursor:pointer}.order-drawer h2{margin:0;font:500 27px Georgia,serif}.drawer-status{display:flex;justify-content:space-between;margin-top:18px;padding:13px 0;border-top:1px solid var(--commerce-line);border-bottom:1px solid var(--commerce-line);font-size:10px}.drawer-status strong{color:var(--commerce-green)}.drawer-section{display:grid;gap:13px;padding:18px 0}.drawer-section>div{display:grid;gap:5px}.drawer-section span{color:var(--commerce-muted);font-size:9px}.drawer-section strong{font-size:10px;font-weight:550}.order-drawer h3{margin:18px 0 9px;font-size:11px}.order-drawer address{padding:12px;border-radius:9px;color:#56635b;background:#f5f5ef;font-size:10px;font-style:normal;line-height:1.9}.shipment-form{display:grid;grid-template-columns:1fr 1fr;gap:10px}.shipment-form label{display:grid;gap:6px;color:var(--commerce-muted);font-size:9px}.shipment-form input{height:36px;min-width:0;padding:0 9px;border:1px solid #dfe2dc;border-radius:7px;outline:none;font: inherit; font-size: 10px}.shipment-form input:focus{border-color:#608d76;box-shadow:0 0 0 3px rgb(96 141 118 / 12%)}.shipment-form button{grid-column:1/-1;justify-self:start}.drawer-actions{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:22px}.drawer-actions span,.refund-state,.tracking-note{color:var(--commerce-muted);font-size:9px;line-height:1.7}.refund-action{min-height:34px;padding:0 14px;border:1px solid #e0b6a6;border-radius:999px;color:#a34f3b;background:#fff8f4;font: inherit; font-size: 9px; font-weight: 600;cursor:pointer}.refund-action:disabled{opacity:.5;cursor:not-allowed}.tracking-note{margin-top:13px}@media(max-width:850px){.admin-order-head{display:none}.admin-order-row{grid-template-columns:1fr auto;gap:9px}.admin-order-row>span:first-child{grid-column:1;grid-row:1}.admin-order-row>span:nth-child(2){grid-column:1;grid-row:2}.admin-order-row .status-pill{grid-column:2;grid-row:1}.admin-order-row .amount{grid-column:2;grid-row:2}.created-time{grid-column:1/-1}.orders-title{align-items:flex-start}.total-count{padding-top:3px}}@media(max-width:480px){.orders-title{flex-direction:column}.total-count{display:flex;align-items:baseline;gap:5px}.drawer-actions{align-items:flex-start;flex-direction:column}}
</style>
