<script setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'
import {api, formatApiError, newIdempotencyKey, serverNow} from '../../api/client.js'
import {loadSession} from '../../auth/session.js'

const route = useRoute()
const router = useRouter()
const activity = ref(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const notice = ref('')
const selectedSkuId = ref('')
const quantity = ref(1)
const clock = ref(serverNow())
const address = ref({recipientName: '', recipientPhone: '', province: '', city: '', district: '', addressLine: ''})
let clockTimer

const selectedSku = computed(() => activity.value?.skus?.find(sku => String(sku.skuId) === String(selectedSkuId.value)))
const remaining = computed(() => Math.max(0, Math.floor((Date.parse(activity.value?.endsAt || 0) - clock.value) / 1000)))
const timeLabel = computed(() => {
  if (!activity.value) return ''
  const start = Date.parse(activity.value.startsAt)
  if (clock.value < start) return `距开始 ${Math.max(0, Math.floor((start - clock.value) / 1000))} 秒`
  if (remaining.value <= 0) return '本场活动已结束'
  return `距结束 ${remaining.value} 秒`
})
const price = minor => `¥${(Number(minor || 0) / 100).toFixed(2)}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get(`/api/seckill/activities/${encodeURIComponent(route.params.activityId)}`)
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('活动信息暂时无法读取。')
    activity.value = response.data.data
    selectedSkuId.value = String(activity.value.skus?.[0]?.skuId || '')
  } catch (cause) {
    error.value = formatApiError(cause)
  } finally {
    loading.value = false
  }
}

async function requestFingerprint(value) {
  const bytes = new TextEncoder().encode(JSON.stringify(value))
  if (globalThis.crypto?.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
  }
  return Array.from(bytes).reduce((hash, byte) => ((hash * 33) ^ byte) >>> 0, 5381).toString(16)
}

async function stableOrderKey(payload) {
  const storageKey = `goodskill.order:${activity.value.activityId}`
  const fingerprint = await requestFingerprint(payload)
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
    if (saved?.fingerprint === fingerprint && saved?.key) return {key: saved.key, storageKey}
    const key = newIdempotencyKey()
    sessionStorage.setItem(storageKey, JSON.stringify({fingerprint, key}))
    return {key, storageKey}
  } catch {
    return {key: newIdempotencyKey(), storageKey: null}
  }
}

async function createOrder() {
  if (!selectedSku.value || !Number.isInteger(Number(quantity.value)) || Number(quantity.value) < 1) {
    error.value = '请选择有效的商品和数量。'
    return
  }
  if (Number(quantity.value) > selectedSku.value.purchaseLimitPerUser) {
    error.value = `每位用户最多购买 ${selectedSku.value.purchaseLimitPerUser} 件。`
    return
  }
  if (Object.values(address.value).some(value => !String(value).trim())) {
    error.value = '请完整填写收货信息。'
    return
  }

  const user = await loadSession()
  if (!user) {
    await router.push({path: '/login', query: {redirect: route.fullPath}})
    return
  }

  const payload = {
    skuId: Number(selectedSkuId.value), quantity: Number(quantity.value),
    shippingAddress: Object.fromEntries(Object.entries(address.value).map(([key, value]) => [key, value.trim()]))
  }
  const {key, storageKey} = await stableOrderKey(payload)
  submitting.value = true
  error.value = ''
  notice.value = ''
  try {
    const response = await api.post(`/api/seckill/activities/${activity.value.activityId}/orders`, payload, {
      headers: {'Idempotency-Key': key}
    })
    if (response.data?.code !== 0 || !response.data?.data?.orderNo) throw new Error('下单结果暂时不可用。请到“我的订单”确认订单状态。')
    if (storageKey) sessionStorage.removeItem(storageKey)
    await router.push({path: '/orders', query: {order: response.data.data.orderNo}})
  } catch (cause) {
    const status = cause?.response?.status
    if (status && status < 500 && storageKey) sessionStorage.removeItem(storageKey)
    error.value = formatApiError(cause)
    if (!cause?.response || status >= 500) notice.value = '如果请求超时或网络中断，请用相同商品、数量和地址重试；系统会复用同一订单请求。'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  clockTimer = window.setInterval(() => { clock.value = serverNow() }, 1000)
})
onUnmounted(() => window.clearInterval(clockTimer))
</script>

<template>
  <div class="detail-page">
    <RouterLink class="back-link" to="/activities">← 返回活动列表</RouterLink>
    <div v-if="loading" class="detail-state" role="status">正在读取活动信息…</div>
    <div v-else-if="error && !activity" class="detail-state" role="alert"><p>{{ error }}</p><button class="shop-button" @click="load">重试</button></div>
    <template v-else-if="activity">
      <section class="detail-hero">
        <div class="detail-illustration" aria-hidden="true"><span>LIMITED<br>EDITION</span><b>G</b><i>GOODSKILL SELECT</i></div>
        <div class="detail-copy">
          <p class="detail-kicker">{{ activity.activityCode }} <span>·</span> {{ activity.state === 'PUBLISHED' ? timeLabel : '当前不可购买' }}</p>
          <h1>{{ activity.title }}</h1>
          <p>选定款式和数量后提交订单。库存和限购以服务端确认结果为准。</p>
          <div class="detail-facts"><span>限量场次</span><span>下单锁定价格</span><span>订单可追踪</span></div>
        </div>
      </section>

      <section class="purchase-grid">
        <div class="sku-panel">
          <div class="panel-heading"><div><small>THE EDIT</small><h2>选择商品</h2></div><span>{{ activity.skus?.length || 0 }} 款</span></div>
          <button v-for="sku in activity.skus" :key="sku.skuId" type="button" class="sku-choice" :class="{chosen: String(sku.skuId) === String(selectedSkuId)}" @click="selectedSkuId = String(sku.skuId)">
            <span class="sku-radio" aria-hidden="true"></span><span class="sku-name"><strong>{{ sku.productName }}</strong><small>{{ sku.skuName }} · 每人限购 {{ sku.purchaseLimitPerUser }} 件</small></span><span class="sku-price">{{ price(sku.unitPriceMinor) }}</span>
            <em :class="sku.inStock ? 'available' : 'soldout'">{{ sku.inStock ? '有库存' : '暂时售罄' }}</em>
          </button>
          <div class="purchase-quantity"><label for="quantity">购买数量</label><input id="quantity" v-model.number="quantity" type="number" min="1" :max="selectedSku?.purchaseLimitPerUser || 1" inputmode="numeric"></div>
          <div class="secure-note"><span>✓</span>重复提交会返回同一订单，库存由服务端最终确认。</div>
        </div>

        <form class="address-panel" @submit.prevent="createOrder">
          <div class="panel-heading"><div><small>DELIVERY</small><h2>收货信息</h2></div><span>加密会话</span></div>
          <div class="address-fields">
            <label>收件人<input v-model="address.recipientName" autocomplete="name" maxlength="100" required></label>
            <label>联系电话<input v-model="address.recipientPhone" autocomplete="tel" inputmode="tel" maxlength="24" required></label>
            <label>省份<input v-model="address.province" autocomplete="address-level1" maxlength="100" required></label>
            <label>城市<input v-model="address.city" autocomplete="address-level2" maxlength="100" required></label>
            <label>区/县<input v-model="address.district" autocomplete="address-level3" maxlength="100" required></label>
            <label class="wide-field">详细地址<input v-model="address.addressLine" autocomplete="street-address" maxlength="300" required></label>
          </div>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <p v-if="notice" class="form-notice">{{ notice }}</p>
          <div class="submit-row"><div><small>商品金额</small><strong>{{ price((selectedSku?.unitPriceMinor || 0) * Number(quantity || 0)) }}</strong></div><button class="shop-button" type="submit" :disabled="submitting || activity.state !== 'PUBLISHED' || !selectedSku?.inStock">{{ submitting ? '正在确认…' : activity.state !== 'PUBLISHED' ? '暂不可购买' : !selectedSku?.inStock ? '暂时售罄' : '提交订单' }} <span aria-hidden="true">→</span></button></div>
          <p class="payment-note">提交后订单会保留 15 分钟，请在“我的订单”中完成支付。</p>
        </form>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-page { color: var(--commerce-ink); }.back-link { display: inline-block; margin-bottom: 20px; color: var(--commerce-muted); font-size: 12px; text-decoration: none; }.back-link:hover { color: var(--commerce-green); }
.detail-hero { display: grid; grid-template-columns: minmax(260px, .72fr) 1fr; min-height: 300px; overflow: hidden; border-radius: 24px; color: #f8f5eb; background: #1b5140; box-shadow: var(--commerce-shadow); }.detail-illustration { position: relative; display: grid; place-items: center; min-height: 300px; overflow: hidden; background: radial-gradient(circle at center, #456e59, #1a493b 68%); }.detail-illustration::before,.detail-illustration::after { position: absolute; width: 240px; height: 240px; border: 1px solid rgb(242 216 173 / 25%); border-radius: 50%; content: ''; transform: scaleY(.4) rotate(-20deg); }.detail-illustration::after { width: 330px; height: 330px; transform: scaleY(.35) rotate(32deg); }.detail-illustration b { z-index: 1; color: #f0c98f; font: 140px Georgia,serif; text-shadow: 12px 20px 25px rgb(0 0 0 / 25%); }.detail-illustration span,.detail-illustration i { position: absolute; z-index: 2; color: #c7d7cb; font-size: 8px; letter-spacing: .18em; line-height: 1.7; }.detail-illustration span { top: 25px; left: 28px; }.detail-illustration i { right: 24px; bottom: 22px; font-style: normal; }
.detail-copy { align-self: center; padding: 42px clamp(28px, 5vw, 66px); }.detail-kicker { color: #c6d7cc; font-size: 10px; letter-spacing: .14em; }.detail-kicker span { margin: 0 8px; color: #e5bd80; }.detail-copy h1 { max-width: 640px; margin: 18px 0 14px; font: 500 clamp(32px, 4vw, 48px)/1.2 Georgia,"Microsoft YaHei",serif; }.detail-copy>p:not(.detail-kicker) { max-width: 480px; color: #d3e0d7; font-size: 13px; line-height: 1.9; }.detail-facts { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 28px; }.detail-facts span { padding: 8px 10px; border: 1px solid rgb(255 255 255 / 20%); border-radius: 999px; color: #d6e0d7; font-size: 9px; }
.purchase-grid { display: grid; grid-template-columns: 1.05fr .95fr; gap: 18px; margin-top: 22px; align-items: start; }.sku-panel,.address-panel { padding: clamp(22px, 3vw, 34px); border: 1px solid var(--commerce-line); border-radius: 20px; background: var(--commerce-surface); box-shadow: 0 8px 24px rgb(30 48 42 / 4%); }.panel-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 19px; }.panel-heading small { color: #a07655; font-size: 9px; letter-spacing: .17em; }.panel-heading h2 { margin: 4px 0 0; font: 500 23px Georgia,"Microsoft YaHei",serif; }.panel-heading>span { color: var(--commerce-muted); font-size: 10px; }
.sku-choice { position: relative; display: grid; grid-template-columns: 20px minmax(0,1fr) auto; gap: 12px; width: 100%; align-items: center; padding: 14px 13px; border: 1px solid #e5e4dc; border-radius: 12px; color: inherit; background: #fffefa; text-align: left; cursor: pointer; }.sku-choice+.sku-choice { margin-top: 9px; }.sku-choice:hover,.sku-choice.chosen { border-color: #628e77; background: #f4f8f2; }.sku-radio { width: 15px; height: 15px; border: 1px solid #bdc7be; border-radius: 50%; }.chosen .sku-radio { border: 4px solid var(--commerce-green); }.sku-name { display: grid; gap: 5px; }.sku-name strong { font-size: 12px; }.sku-name small { color: var(--commerce-muted); font-size: 9px; }.sku-price { grid-column: 3; grid-row: 1; color: #a65838; font: 17px Georgia,serif; }.sku-choice em { grid-column: 2 / 4; justify-self: start; padding: 4px 7px; border-radius: 999px; font-size: 9px; font-style: normal; }.available { color: #276247; background: #e6f1e8; }.soldout { color: #885d51; background: #f5e9e3; }
.purchase-quantity { display: flex; justify-content: space-between; align-items: center; margin-top: 18px; color: #58645e; font-size: 11px; }.purchase-quantity input { width: 84px; padding: 9px; border: 1px solid var(--commerce-line); border-radius: 9px; font: inherit; }.secure-note { margin-top: 17px; padding-top: 14px; border-top: 1px solid #eeede7; color: var(--commerce-muted); font-size: 9px; }.secure-note span { margin-right: 5px; color: var(--commerce-green); }
.address-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 13px 12px; }.address-fields label { display: grid; gap: 6px; color: #53615a; font-size: 10px; }.address-fields input { min-width: 0; height: 38px; padding: 0 10px; border: 1px solid #e1e2da; border-radius: 8px; outline: none; color: var(--commerce-ink); background: #fff; font: inherit; font-size: 12px; }.address-fields input:focus { border-color: #5c8b73; box-shadow: 0 0 0 3px rgb(92 139 115 / 13%); }.wide-field { grid-column: 1/-1; }.form-error,.form-notice { margin: 13px 0 0; color: #a74636; font-size: 11px; line-height: 1.6; }.form-notice { color: #735d3d; }
.submit-row { display: flex; justify-content: space-between; align-items: center; gap: 15px; margin-top: 20px; padding-top: 17px; border-top: 1px solid #eeede7; }.submit-row>div { display: grid; gap: 5px; }.submit-row small { color: var(--commerce-muted); font-size: 9px; }.submit-row strong { color: #a65838; font: 22px Georgia,serif; }.shop-button { display: inline-flex; min-height: 42px; justify-content: center; align-items: center; gap: 14px; padding: 0 18px; border: 0; border-radius: 999px; color: #fffefa; background: var(--commerce-green); font: inherit; font-size: 11px; font-weight: 600; cursor: pointer; }.shop-button:hover:not(:disabled) { background: var(--commerce-green-deep); }.shop-button:disabled { opacity: .48; cursor: not-allowed; }.payment-note { margin-top: 11px; color: #8a928c; font-size: 9px; text-align: right; }.detail-state { display: grid; min-height: 220px; place-content: center; justify-items: center; gap: 12px; color: var(--commerce-muted); }
@media(max-width:850px){.purchase-grid{grid-template-columns:1fr}.detail-hero{grid-template-columns:.7fr 1.3fr}.detail-illustration{min-height:270px}}@media(max-width:580px){.detail-hero{grid-template-columns:1fr}.detail-illustration{min-height:180px}.detail-illustration b{font-size:98px}.detail-copy{padding:26px}.detail-facts{margin-top:18px}.purchase-grid{gap:12px}.sku-panel,.address-panel{padding:20px}.address-fields{gap:11px 9px}}
</style>
