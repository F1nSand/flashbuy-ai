<script setup>
import {computed, onMounted, ref} from 'vue'
import {api, formatApiError} from '../../api/client.js'

const activities = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const formOpen = ref(false)
const form = ref({activityCode: '', title: '', startsAt: '', endsAt: '', skuCode: '', productName: '', skuName: '', unitPrice: '', purchaseLimitPerUser: 1, allocatedQuantity: 100})
const sortedActivities = computed(() => [...activities.value].sort((a, b) => Date.parse(b.startsAt) - Date.parse(a.startsAt)))
const money = minor => `¥${(Number(minor || 0) / 100).toFixed(2)}`
const dateTime = value => value ? new Intl.DateTimeFormat('zh-CN', {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value)) : '—'
const statusName = state => ({DRAFT: '草稿', PUBLISHED: '已发布', PAUSED: '已暂停'}[state] || state)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/seckill/activities/manage', {params: {limit: 100}})
    if (response.data?.code !== 0 || !Array.isArray(response.data?.data)) throw new Error('活动列表暂时无法读取。')
    activities.value = response.data.data
  } catch (cause) { error.value = formatApiError(cause) }
  finally { loading.value = false }
}

function openCreate() {
  const now = new Date(Date.now() + 60 * 60_000)
  const later = new Date(now.getTime() + 24 * 60 * 60_000)
  const localInput = date => new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
  form.value = {activityCode: `SALE-${Date.now().toString(36).toUpperCase()}`, title: '', startsAt: localInput(now), endsAt: localInput(later), skuCode: `SKU-${Date.now().toString(36).toUpperCase()}`, productName: '', skuName: '标准款', unitPrice: '', purchaseLimitPerUser: 1, allocatedQuantity: 100}
  success.value = ''
  error.value = ''
  formOpen.value = true
}

async function createActivity() {
  if (new Date(form.value.endsAt) <= new Date(form.value.startsAt)) { error.value = '结束时间必须晚于开始时间。'; return }
  const priceMinor = Math.round(Number(form.value.unitPrice) * 100)
  if (!Number.isSafeInteger(priceMinor) || priceMinor <= 0) { error.value = '请填写大于零且最多两位小数的价格。'; return }
  saving.value = true
  error.value = ''
  try {
    const response = await api.post('/api/seckill/activities', {
      activityCode: form.value.activityCode.trim(), title: form.value.title.trim(),
      startsAt: new Date(form.value.startsAt).toISOString(), endsAt: new Date(form.value.endsAt).toISOString(),
      skus: [{skuCode: form.value.skuCode.trim(), productName: form.value.productName.trim(), skuName: form.value.skuName.trim(), unitPriceMinor: priceMinor, purchaseLimitPerUser: Number(form.value.purchaseLimitPerUser), allocatedQuantity: Number(form.value.allocatedQuantity)}]
    })
    if (response.data?.code !== 0) throw new Error(response.data?.msg || '活动创建失败。')
    formOpen.value = false
    success.value = '草稿已保存。发布前请核对活动时段、价格和库存。'
    await load()
  } catch (cause) { error.value = formatApiError(cause) }
  finally { saving.value = false }
}

async function transition(activity, action) {
  const label = action === 'publish' ? '发布' : '暂停'
  if (!window.confirm(`确认${label}“${activity.title}”？`)) return
  saving.value = true
  error.value = ''
  try {
    const response = await api.post(`/api/seckill/activities/${activity.activityId}/${action}`, {expectedVersion: activity.rowVersion})
    if (response.data?.code !== 0) throw new Error(response.data?.msg || `${label}操作失败。`)
    success.value = `活动已${label}，版本 ${response.data.data.publishedVersion}。`
    await load()
  } catch (cause) { error.value = formatApiError(cause) }
  finally { saving.value = false }
}
</script>

<template>
  <section class="manage-page">
    <header class="manage-heading"><div><p class="manage-eyebrow">COMMERCE OPERATIONS / 活动</p><h1>把每一场活动，<em>运营清楚。</em></h1><p>草稿可直接发布或暂停；库存和价格来自交易数据库。</p></div><button class="primary-action" @click="openCreate">＋ 新建活动草稿</button></header>
    <p v-if="success" class="notice success" role="status">{{ success }}</p><p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div v-if="loading" class="manage-empty">正在读取运营数据…</div>
    <div v-else-if="sortedActivities.length === 0" class="manage-empty"><span>—</span><h2>还没有活动</h2><p>创建草稿后录入首个商品和库存。</p><button class="primary-action" @click="openCreate">新建活动</button></div>
    <div v-else class="activity-ops-list">
      <article v-for="activity in sortedActivities" :key="activity.activityId" class="activity-ops-card">
        <div class="ops-card-head"><div><p class="activity-code">{{ activity.activityCode }} · #{{ activity.activityId }}</p><h2>{{ activity.title }}</h2><p class="activity-times">{{ dateTime(activity.startsAt) }} — {{ dateTime(activity.endsAt) }}</p></div><span class="ops-status" :class="`ops-${activity.state.toLowerCase()}`">{{ statusName(activity.state) }}</span></div>
        <div class="sku-table"><div class="sku-table-head"><span>商品 / SKU</span><span>活动价</span><span>可售库存</span><span>已分配</span><span>每人限购</span></div><div v-for="sku in activity.skus" :key="sku.skuId" class="sku-table-row"><span><strong>{{ sku.productName }}</strong><small>{{ sku.skuName }} · {{ sku.skuCode }}</small></span><span>{{ money(sku.unitPriceMinor) }}</span><span>{{ sku.availableQuantity }}</span><span>{{ sku.allocatedQuantity }}</span><span>{{ sku.purchaseLimitPerUser }}</span></div></div>
        <footer class="ops-card-footer"><span>活动版本 {{ activity.publishedVersion }} · 行版本 {{ activity.rowVersion }}</span><div><button v-if="activity.state === 'DRAFT'" class="primary-action small" :disabled="saving" @click="transition(activity, 'publish')">发布活动</button><button v-if="activity.state === 'PUBLISHED'" class="secondary-action small" :disabled="saving" @click="transition(activity, 'pause')">暂停活动</button><RouterLink class="quiet-link" :to="`/activities/${activity.activityId}`" target="_blank">预览公开页 ↗</RouterLink></div></footer>
      </article>
    </div>

    <div v-if="formOpen" class="modal-backdrop" @click.self="formOpen = false"><form class="activity-form" @submit.prevent="createActivity"><button type="button" class="modal-close" aria-label="关闭" @click="formOpen = false">×</button><p class="manage-eyebrow">NEW ACTIVITY / 新建</p><h2>创建活动草稿</h2><p class="form-intro">首版从草稿创建时录入一个 SKU 和初始库存，发布后价格与库存快照固定。</p><div class="form-grid"><label>活动编码<input v-model="form.activityCode" maxlength="64" required></label><label class="span-two">活动标题<input v-model="form.title" maxlength="200" required></label><label>开始时间<input v-model="form.startsAt" type="datetime-local" required></label><label>结束时间<input v-model="form.endsAt" type="datetime-local" required></label><label>商品名称<input v-model="form.productName" maxlength="200" required></label><label>SKU 名称<input v-model="form.skuName" maxlength="200" required></label><label>SKU 编码<input v-model="form.skuCode" maxlength="64" required></label><label>活动价格（元）<input v-model="form.unitPrice" type="number" min="0.01" step="0.01" inputmode="decimal" required></label><label>初始库存<input v-model.number="form.allocatedQuantity" type="number" min="1" max="100000000" required></label><label>每人限购<input v-model.number="form.purchaseLimitPerUser" type="number" min="1" max="100000" required></label></div><p v-if="error" class="notice error" role="alert">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-action" @click="formOpen = false">取消</button><button type="submit" class="primary-action" :disabled="saving">{{ saving ? '正在保存…' : '保存草稿' }}</button></div></form></div>
  </section>
</template>

<style scoped>
.manage-page{color:var(--commerce-ink)}.manage-heading{display:flex;justify-content:space-between;align-items:flex-end;gap:22px;margin:16px 0 26px}.manage-eyebrow{margin-bottom:9px;color:#a07655;font-size:9px;font-weight:650;letter-spacing:.16em}.manage-heading h1{margin:0;font:500 clamp(28px,3.8vw,43px)/1.2 Georgia,"Microsoft YaHei",serif}.manage-heading h1 em{color:var(--commerce-green);font-style:normal}.manage-heading>div>p:last-child{margin-top:10px;color:var(--commerce-muted);font-size:11px}.primary-action,.secondary-action{display:inline-flex;min-height:38px;align-items:center;justify-content:center;padding:0 15px;border:0;border-radius:999px;color:white;background:var(--commerce-green);font: inherit; font-size: 10px; font-weight: 600;cursor:pointer}.primary-action:hover:not(:disabled){background:var(--commerce-green-deep)}.primary-action:disabled,.secondary-action:disabled{opacity:.5;cursor:not-allowed}.secondary-action{border:1px solid #d6ddd6;color:#4e6056;background:transparent}.primary-action.small,.secondary-action.small{min-height:30px;padding:0 12px}.notice{margin:0 0 13px;padding:11px 14px;border-radius:10px;font-size:10px;line-height:1.6}.notice.success{color:#276247;background:#e7f1e8}.notice.error{color:#9c4938;background:#f8eae3}.manage-empty{display:grid;min-height:260px;place-content:center;justify-items:center;gap:10px;color:var(--commerce-muted);font-size:11px;text-align:center}.manage-empty>span{font:28px Georgia,serif}.manage-empty h2{color:var(--commerce-ink);font:500 23px Georgia,serif}.activity-ops-list{display:grid;gap:13px}.activity-ops-card{overflow:hidden;border:1px solid var(--commerce-line);border-radius:15px;background:var(--commerce-surface);box-shadow:0 6px 22px rgb(30 48 42 / 3%)}.ops-card-head{display:flex;justify-content:space-between;align-items:flex-start;gap:15px;padding:20px 22px 16px}.activity-code{color:#909991;font:9px ui-monospace,Consolas,monospace;letter-spacing:.06em}.ops-card-head h2{margin:7px 0 5px;font:500 20px Georgia,"Microsoft YaHei",serif}.activity-times{color:var(--commerce-muted);font-size:10px}.ops-status{padding:6px 10px;border-radius:999px;color:#82643c;background:#f4ecd9;font-size:9px;white-space:nowrap}.ops-published{color:#276247;background:#e5f0e7}.ops-paused{color:#737a72;background:#eceee9}.sku-table{border-top:1px solid #eeede7}.sku-table-head,.sku-table-row{display:grid;grid-template-columns:minmax(170px,2fr) repeat(4,minmax(75px,.7fr));gap:12px;align-items:center;padding:10px 22px}.sku-table-head{color:#89928c;background:#f7f7f1;font-size:9px}.sku-table-row{border-top:1px solid #f0efe9;color:#4e5c53;font-size:10px}.sku-table-row>span:first-child{display:grid;gap:4px}.sku-table-row strong{color:var(--commerce-ink);font-size:11px}.sku-table-row small{color:var(--commerce-muted);font-size:9px}.ops-card-footer{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 22px;border-top:1px solid #eeede7;color:var(--commerce-muted);font-size:9px}.ops-card-footer>div{display:flex;align-items:center;gap:15px}.quiet-link{color:#8d654e;font-size:9px;text-decoration:none}.quiet-link:hover{text-decoration:underline}.modal-backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;overflow:auto;padding:20px;background:rgb(19 37 30 / 44%);backdrop-filter:blur(3px)}.activity-form{position:relative;width:min(690px,100%);max-height:min(92vh,850px);overflow:auto;padding:32px;border:1px solid #e3e2db;border-radius:18px;background:#fffefa;box-shadow:0 24px 70px rgb(0 0 0 / 19%)}.modal-close{position:absolute;top:18px;right:20px;width:30px;height:30px;border:1px solid var(--commerce-line);border-radius:50%;color:var(--commerce-muted);background:white;font-size:21px;cursor:pointer}.activity-form h2{margin:0;font:500 25px Georgia,serif}.form-intro{margin:9px 0 20px;color:var(--commerce-muted);font-size:10px;line-height:1.7}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px}.form-grid label{display:grid;gap:6px;color:#59645d;font-size:9px}.form-grid input{min-width:0;height:38px;padding:0 10px;border:1px solid #dfe2dc;border-radius:8px;outline:none;color:var(--commerce-ink);background:white;font: inherit; font-size: 11px}.form-grid input:focus{border-color:#608d76;box-shadow:0 0 0 3px rgb(96 141 118 / 12%)}.span-two{grid-column:1/-1}.modal-actions{display:flex;justify-content:flex-end;gap:9px;margin-top:18px}@media(max-width:700px){.manage-heading{align-items:flex-start;flex-direction:column}.sku-table-head,.sku-table-row{grid-template-columns:minmax(135px,1.7fr) repeat(2,minmax(60px,.6fr));gap:8px;padding-right:13px;padding-left:13px}.sku-table-head span:nth-child(4),.sku-table-head span:nth-child(5),.sku-table-row span:nth-child(4),.sku-table-row span:nth-child(5){display:none}.ops-card-head{padding:16px}.ops-card-footer{align-items:flex-start;flex-direction:column;padding:12px 16px}.ops-card-footer>div{flex-wrap:wrap}.activity-form{padding:25px 19px}.form-grid{gap:10px}}
</style>
