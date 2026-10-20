<script setup>
import {onMounted, ref} from 'vue'
import {RouterLink} from 'vue-router'
import {api, formatApiError} from '../../api/client.js'

const metrics = ref(null)
const activities = ref([])
const loading = ref(true)
const error = ref('')
const updatedAt = ref('')
const number = value => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
const dateTime = value => value ? new Intl.DateTimeFormat('zh-CN', {month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'}).format(new Date(value)) : '—'

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [metricResponse, activityResponse] = await Promise.all([
      api.get('/api/order/manage/metrics'),
      api.get('/api/seckill/activities/manage', {params: {limit: 5}})
    ])
    if (metricResponse.data?.code !== 0 || !metricResponse.data?.data) throw new Error('业务指标暂时无法读取。')
    if (activityResponse.data?.code !== 0 || !Array.isArray(activityResponse.data?.data)) throw new Error('活动数据暂时无法读取。')
    metrics.value = metricResponse.data.data
    activities.value = activityResponse.data.data
    updatedAt.value = new Intl.DateTimeFormat('zh-CN', {hour: '2-digit', minute: '2-digit', second: '2-digit'}).format(new Date())
  } catch (cause) { error.value = formatApiError(cause) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <section class="dashboard-page"><header class="dashboard-hero"><div><p class="dashboard-eyebrow">GOODSKILL COMMERCE / OPERATIONS</p><h1>经营概况，<em>来自真实交易。</em></h1><p>库存与订单指标由交易数据库即时汇总，不包含模拟数据。</p></div><button type="button" class="refresh-button" :disabled="loading" @click="load">↻ <span>{{ loading ? '读取中' : '刷新数据' }}</span></button><small v-if="updatedAt" class="updated-at">读取于 {{ updatedAt }}</small></header>
    <p v-if="error" class="dashboard-error" role="alert">{{ error }}</p>
    <div v-if="loading && !metrics" class="dashboard-state" role="status">正在汇总当前业务状态…</div>
    <div v-else-if="metrics" class="metric-grid"><article class="metric-card metric-green"><span>当前进行活动</span><strong>{{ number(metrics.activeActivities) }}</strong><small>已发布且处于活动时段</small><i>01</i></article><article class="metric-card metric-amber"><span>等待付款订单</span><strong>{{ number(metrics.pendingPaymentOrders) }}</strong><small>等待买家完成付款</small><i>02</i></article><article class="metric-card metric-blue"><span>已付款订单</span><strong>{{ number(metrics.paidOrders) }}</strong><small>按交易状态统计</small><i>03</i></article><article class="metric-card metric-rust"><span>等待履约</span><strong>{{ number(metrics.waitingFulfillmentOrders) }}</strong><small>已付款且尚未发货</small><i>04</i></article></div>
    <section v-if="metrics" class="stock-strip"><div><span>活动中可售库存</span><strong>{{ number(metrics.availableUnits) }} <small>件</small></strong></div><div><span>当前预占库存</span><strong>{{ number(metrics.reservedUnits) }} <small>件</small></strong></div><p>只显示交易数据库现有数量，不推算成交金额或未来销量。</p></section>
    <div class="dashboard-lower"><section class="recent-activities"><div class="lower-heading"><div><p class="dashboard-eyebrow">ACTIVITY PIPELINE</p><h2>活动动态</h2></div><RouterLink to="/manage/activities">全部活动 ↗</RouterLink></div><div v-if="activities.length === 0" class="recent-empty">还没有运营活动。</div><article v-for="activity in activities" :key="activity.activityId" class="recent-activity"><div class="activity-initial">{{ activity.title?.slice(0, 1) || 'G' }}</div><div><strong>{{ activity.title }}</strong><small>{{ activity.activityCode }} · {{ dateTime(activity.startsAt) }} 开始</small></div><span :class="`recent-state recent-${activity.state.toLowerCase()}`">{{ {DRAFT: '草稿', PUBLISHED: '已发布', PAUSED: '已暂停'}[activity.state] || activity.state }}</span></article></section><aside class="quick-actions"><p class="dashboard-eyebrow">WORKSPACE</p><h2>快速前往</h2><RouterLink to="/manage/activities"><span>01</span><strong>活动与库存</strong><i>→</i></RouterLink><RouterLink to="/manage/orders"><span>02</span><strong>订单与履约</strong><i>→</i></RouterLink><RouterLink to="/manage/inventory"><span>03</span><strong>库存变化记录</strong><i>→</i></RouterLink><RouterLink to="/manage/audit"><span>04</span><strong>操作审计</strong><i>→</i></RouterLink></aside></div>
  </section>
</template>

<style scoped>
.dashboard-page{color:var(--commerce-ink)}.dashboard-hero{position:relative;display:flex;justify-content:space-between;align-items:center;gap:22px;padding:32px 36px;border-radius:19px;color:#f5f3ed;background:linear-gradient(112deg,#164a3a,#28654e);box-shadow:var(--commerce-shadow)}.dashboard-eyebrow{margin-bottom:9px;color:#a67b57;font-size:9px;font-weight:650;letter-spacing:.16em}.dashboard-hero .dashboard-eyebrow{color:#b5cabe}.dashboard-hero h1{margin:0;font:500 clamp(28px,3.8vw,41px)/1.2 Georgia,"Microsoft YaHei",serif}.dashboard-hero h1 em{color:#f0c78c;font-style:normal}.dashboard-hero>div>p:last-child{margin-top:10px;color:#d0ded5;font-size:10px}.refresh-button{display:flex;min-width:104px;align-items:center;justify-content:center;gap:7px;height:34px;padding:0 12px;border:1px solid rgb(255 255 255 / 25%);border-radius:999px;color:#f5f3ed;background:transparent;font:inherit;font-size:10px;cursor:pointer}.refresh-button:disabled{opacity:.5}.updated-at{position:absolute;right:39px;bottom:13px;color:#b9cec1;font-size:8px}.dashboard-error{margin:13px 0;padding:10px 13px;border-radius:8px;color:#9c4938;background:#f8eae3;font-size:10px}.dashboard-state{display:grid;min-height:210px;place-items:center;color:var(--commerce-muted);font-size:11px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-top:17px}.metric-card{position:relative;display:grid;min-height:144px;align-content:start;gap:10px;overflow:hidden;padding:19px;border:1px solid var(--commerce-line);border-radius:14px;background:var(--commerce-surface)}.metric-card>span{color:#6b776f;font-size:10px}.metric-card>strong{color:#205c45;font:31px Georgia,serif}.metric-card>small{color:#89928c;font-size:9px}.metric-card>i{position:absolute;right:13px;bottom:-11px;color:rgb(32 92 69 / 6%);font:58px Georgia,serif;font-style:normal}.metric-amber>strong{color:#a77638}.metric-blue>strong{color:#55758b}.metric-rust>strong{color:#a35843}.stock-strip{display:grid;grid-template-columns:1fr 1fr auto;gap:22px;align-items:center;margin-top:12px;padding:15px 20px;border:1px solid #d8dfd5;border-radius:12px;background:#edf3eb}.stock-strip>div{display:grid;gap:4px}.stock-strip span{color:#66756a;font-size:9px}.stock-strip strong{color:#276247;font:19px Georgia,serif}.stock-strip strong small{font: inherit; font-size: 9px}.stock-strip p{max-width:250px;color:#7b877d;font-size:8px;line-height:1.6}.dashboard-lower{display:grid;grid-template-columns:1.5fr .8fr;gap:13px;margin-top:18px}.recent-activities,.quick-actions{padding:20px;border:1px solid var(--commerce-line);border-radius:14px;background:var(--commerce-surface)}.lower-heading{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:12px}.lower-heading h2,.quick-actions h2{margin:0;font:500 21px Georgia,serif}.lower-heading>a{color:#8d654e;font-size:9px;text-decoration:none}.recent-activity{display:grid;grid-template-columns:31px 1fr auto;gap:10px;align-items:center;padding:10px 0;border-top:1px solid #efeee8}.activity-initial{display:grid;width:29px;height:29px;place-items:center;border-radius:9px;color:#9a704a;background:#f1e7d8;font:15px Georgia,serif}.recent-activity>div:nth-child(2){display:grid;gap:4px;min-width:0}.recent-activity strong{overflow:hidden;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.recent-activity small{color:var(--commerce-muted);font-size:8px}.recent-state{padding:5px 7px;border-radius:999px;color:#81643d;background:#f4ecd9;font-size:8px}.recent-published{color:#276247;background:#e5f0e7}.recent-paused{color:#737a72;background:#eceee9}.recent-empty{padding:22px 0;color:var(--commerce-muted);font-size:10px}.quick-actions h2{margin-bottom:11px}.quick-actions>a{display:grid;grid-template-columns:24px 1fr 15px;gap:8px;align-items:center;padding:12px 0;border-top:1px solid #efeee8;color:var(--commerce-ink);text-decoration:none}.quick-actions>a span{color:#a07655;font-size:8px}.quick-actions>a strong{font-size:10px;font-weight:550}.quick-actions>a i{color:#8d654e;font-style:normal}.quick-actions>a:hover strong{color:var(--commerce-green)}@media(max-width:850px){.metric-grid{grid-template-columns:repeat(2,1fr)}.dashboard-lower{grid-template-columns:1fr}.stock-strip{grid-template-columns:1fr 1fr}.stock-strip p{grid-column:1/-1;max-width:none}}@media(max-width:500px){.dashboard-hero{align-items:flex-start;flex-direction:column;padding:24px}.updated-at{right:22px;bottom:17px}.refresh-button{align-self:flex-start}.metric-card{min-height:125px;padding:15px}.stock-strip{gap:12px;padding:13px}.recent-activities,.quick-actions{padding:16px}}
</style>
