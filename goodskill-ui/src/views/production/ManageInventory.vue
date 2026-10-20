<script setup>
import {computed, ref} from 'vue'
import {api, formatApiError} from '../../api/client.js'

const filters = ref({activityId: '', skuId: '', orderNo: ''})
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 50
const loading = ref(false)
const queried = ref(false)
const error = ref('')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))
const time = value => value ? new Intl.DateTimeFormat('zh-CN', {dateStyle: 'medium', timeStyle: 'medium'}).format(new Date(value)) : '—'
const operationNames = {ORDER_RESERVATION: '下单预占', ORDER_RESERVATION_RELEASE: '释放预占', ORDER_PAYMENT: '支付结算'}

async function search(nextPage = 1) {
  const params = Object.fromEntries(Object.entries(filters.value).map(([key, value]) => [key, value.trim()]).filter(([, value]) => value))
  if (!Object.keys(params).length) { error.value = '请至少填写活动 ID、SKU ID 或订单编号之一。'; return }
  if ((params.activityId && !/^[1-9][0-9]*$/.test(params.activityId)) || (params.skuId && !/^[1-9][0-9]*$/.test(params.skuId)) || (params.orderNo && !/^GS[0-9A-F]{32}$/.test(params.orderNo))) {
    error.value = '筛选值格式不正确。'
    return
  }
  page.value = nextPage
  params.page = page.value
  params.size = size
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/order/manage/inventory', {params})
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('库存流水暂时无法读取。')
    records.value = response.data.data.records || []
    total.value = Number(response.data.data.total || 0)
    queried.value = true
  } catch (cause) { error.value = formatApiError(cause) }
  finally { loading.value = false }
}
</script>

<template>
  <section class="ledger-page"><header class="ledger-heading"><div><p class="ledger-eyebrow">INVENTORY JOURNAL / 库存流水</p><h1>每次库存变化，<em>都可追溯。</em></h1><p>只读查询交易流水，按活动、SKU 或订单缩小范围。</p></div><span class="ledger-symbol" aria-hidden="true">↗</span></header><form class="ledger-filter" @submit.prevent="search(1)"><label>活动 ID<input v-model="filters.activityId" inputmode="numeric"></label><label>SKU ID<input v-model="filters.skuId" inputmode="numeric"></label><label>订单编号<input v-model="filters.orderNo" maxlength="40" placeholder="GS + 32 位大写字母/数字"></label><button type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询流水 →' }}</button></form><p v-if="error" class="ledger-error" role="alert">{{ error }}</p><div v-if="loading" class="ledger-empty">正在读取交易流水…</div><div v-else-if="!queried" class="ledger-empty">请先设置至少一个筛选条件；为避免无界扫描，不提供全表导出。</div><div v-else-if="records.length === 0" class="ledger-empty">没有匹配的库存变更。</div><template v-else><div class="ledger-summary">{{ total }} 条流水 · 第 {{ page }} / {{ pageCount }} 页</div><div class="ledger-table"><div class="ledger-table-head"><span>时间 / 业务</span><span>活动 / SKU</span><span>可售变化</span><span>预占变化</span><span>已售变化</span><span>操作人</span></div><article v-for="record in records" :key="record.ledgerId" class="ledger-row"><span><time>{{ time(record.createdAt) }}</time><strong>{{ operationNames[record.operationType] || record.operationType }}</strong><small>{{ record.orderNo || record.reason }}</small></span><span>#{{ record.activityId }} / #{{ record.skuId }}</span><strong :class="{'positive':record.availableDelta>0,'negative':record.availableDelta<0}">{{ record.availableDelta > 0 ? '+' : '' }}{{ record.availableDelta }}</strong><strong :class="{'positive':record.reservedDelta<0,'negative':record.reservedDelta>0}">{{ record.reservedDelta > 0 ? '+' : '' }}{{ record.reservedDelta }}</strong><strong :class="{'positive':record.soldDelta>0,'negative':record.soldDelta<0}">{{ record.soldDelta > 0 ? '+' : '' }}{{ record.soldDelta }}</strong><span class="ledger-actor">{{ record.actorId }}</span></article></div><nav v-if="pageCount>1" class="ledger-pagination"><button :disabled="page<=1" @click="search(page-1)">← 上一页</button><span>{{ page }} / {{ pageCount }}</span><button :disabled="page>=pageCount" @click="search(page+1)">下一页 →</button></nav></template></section>
</template>

<style scoped>
.ledger-page{color:var(--commerce-ink)}.ledger-heading{display:flex;justify-content:space-between;align-items:center;padding:25px 30px;border:1px solid var(--commerce-line);border-radius:17px;background:linear-gradient(125deg,#fffefa,#edf1e9)}.ledger-eyebrow{margin-bottom:9px;color:#a07655;font-size:9px;font-weight:650;letter-spacing:.16em}.ledger-heading h1{margin:0;font:500 clamp(27px,3.7vw,40px)/1.2 Georgia,"Microsoft YaHei",serif}.ledger-heading h1 em{color:var(--commerce-green);font-style:normal}.ledger-heading p:last-child{margin-top:9px;color:var(--commerce-muted);font-size:10px}.ledger-symbol{color:#b17d55;font:62px Georgia,serif;transform:rotate(40deg)}.ledger-filter{display:grid;grid-template-columns:.7fr .7fr 1.5fr auto;gap:10px;align-items:end;margin:16px 0 20px;padding:16px;border:1px solid var(--commerce-line);border-radius:12px;background:var(--commerce-surface)}.ledger-filter label{display:grid;gap:6px;color:var(--commerce-muted);font-size:9px}.ledger-filter input{min-width:0;height:35px;padding:0 9px;border:1px solid #e1e2da;border-radius:7px;outline:none;font: inherit; font-size: 10px}.ledger-filter input:focus{border-color:#608d76}.ledger-filter button{height:35px;padding:0 15px;border:0;border-radius:999px;color:white;background:var(--commerce-green);font: inherit; font-size: 9px; font-weight: 600;white-space:nowrap;cursor:pointer}.ledger-filter button:disabled{opacity:.5}.ledger-error{margin:-11px 0 12px;color:#a74636;font-size:10px}.ledger-empty{display:grid;min-height:190px;place-items:center;color:var(--commerce-muted);font-size:11px;text-align:center}.ledger-summary{margin-bottom:8px;color:var(--commerce-muted);font-size:9px}.ledger-table{overflow:hidden;border:1px solid var(--commerce-line);border-radius:12px;background:var(--commerce-surface)}.ledger-table-head,.ledger-row{display:grid;grid-template-columns:minmax(165px,1.55fr) minmax(110px,1fr) repeat(3,minmax(65px,.55fr)) minmax(100px,.8fr);gap:10px;align-items:center;padding:10px 14px}.ledger-table-head{color:#87918a;background:#f7f7f1;font-size:9px}.ledger-row{min-height:58px;border-top:1px solid #efeee8;color:#5c685f;font-size:9px}.ledger-row>span:first-child{display:grid;gap:4px}.ledger-row time,.ledger-row small{color:#8a948d;font-size:8px}.ledger-row strong{font-size:10px}.positive{color:#27704b}.negative{color:#a55340}.ledger-actor{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.ledger-pagination{display:flex;justify-content:center;align-items:center;gap:17px;padding:18px;color:var(--commerce-muted);font-size:10px}.ledger-pagination button{border:0;color:#8d654e;background:none;font:inherit;cursor:pointer}.ledger-pagination button:disabled{opacity:.4;cursor:not-allowed}@media(max-width:850px){.ledger-table-head{display:none}.ledger-row{grid-template-columns:1.4fr .8fr .6fr .6fr .6fr;gap:7px;padding:11px}.ledger-row .ledger-actor{grid-column:1/-1}.ledger-filter{grid-template-columns:1fr 1fr}.ledger-filter label:nth-child(3){grid-column:1/-1}.ledger-filter button{grid-column:1/-1}}@media(max-width:520px){.ledger-heading{padding:20px}.ledger-symbol{font-size:42px}.ledger-row{grid-template-columns:1.2fr .8fr .55fr .55fr .55fr;font-size:8px}.ledger-row strong{font-size:9px}.ledger-row>span:first-child small{display:none}}
</style>
