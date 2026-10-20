<script setup>
import {computed, ref} from 'vue'
import {api, formatApiError} from '../../api/client.js'

const form = ref({action: '', actorId: '', targetType: '', targetId: ''})
const records = ref([])
const page = ref(1)
const size = 50
const total = ref(0)
const loading = ref(false)
const queried = ref(false)
const error = ref('')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))
const actionNames = {ACTIVITY_CREATE: '创建活动', ACTIVITY_UPDATE: '编辑活动', ACTIVITY_PUBLISH: '发布活动', ACTIVITY_PAUSE: '暂停活动', ORDER_CREATE: '创建订单', ORDER_CANCEL: '关闭订单', PAYMENT_CREATE: '发起支付', PAYMENT_SUCCEEDED: '确认支付', REFUND_REQUEST: '申请退款', REFUND_SUCCEEDED: '退款成功', FULFILLMENT_SHIP: '登记发货', FILE_UPLOAD: '上传文件', FILE_DELETE: '删除文件'}
const time = value => value ? new Intl.DateTimeFormat('zh-CN', {dateStyle: 'medium', timeStyle: 'medium'}).format(new Date(value)) : '—'

async function search(nextPage = 1) {
  const params = Object.fromEntries(Object.entries(form.value).map(([key, value]) => [key, value.trim()]).filter(([, value]) => value))
  if (!Object.keys(params).length) { error.value = '请至少指定一个筛选条件。'; return }
  page.value = nextPage
  params.page = page.value
  params.size = size
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/order/manage/audit', {params})
    if (response.data?.code !== 0 || !response.data?.data) throw new Error('审计记录暂时无法读取。')
    records.value = response.data.data.records || []
    total.value = Number(response.data.data.total || 0)
    queried.value = true
  } catch (cause) { error.value = formatApiError(cause) }
  finally { loading.value = false }
}
</script>

<template>
  <section class="audit-page"><header class="audit-heading"><div><p class="audit-eyebrow">ACCOUNTABILITY / 操作审计</p><h1>关键操作，<em>有迹可循。</em></h1><p>审计记录只读展示，包含操作者、目标对象、结果与变更摘要。</p></div><div class="audit-seal">AUDIT<br><strong>LOG</strong></div></header>
    <form class="audit-filter" @submit.prevent="search(1)"><label>操作类型<input v-model="form.action" maxlength="80" placeholder="例如 ACTIVITY_PUBLISH"></label><label>操作者 ID<input v-model="form.actorId" maxlength="64"></label><label>对象类型<input v-model="form.targetType" maxlength="80" placeholder="ORDER / ACTIVITY"></label><label>对象 ID<input v-model="form.targetId" maxlength="80"></label><button class="audit-button" type="submit" :disabled="loading">{{ loading ? '正在查询…' : '查询审计记录' }} →</button></form>
    <p v-if="error" class="audit-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="audit-state">正在查询不可变更的审计记录…</div><div v-else-if="queried && records.length === 0" class="audit-state">没有匹配的操作记录。</div><div v-else-if="!queried" class="audit-state">为控制查询范围，请先填写一个或多个筛选条件。</div>
    <template v-else><div class="audit-summary"><span>匹配记录 <strong>{{ total }}</strong></span><span>第 {{ page }} / {{ pageCount }} 页</span></div><article v-for="record in records" :key="record.auditId" class="audit-record"><div class="record-marker" aria-hidden="true">{{ record.result === 'SUCCESS' ? '✓' : '!' }}</div><div class="record-main"><div class="record-title"><strong>{{ actionNames[record.action] || record.action }}</strong><span :class="record.result === 'SUCCESS' ? 'result-ok' : 'result-fail'">{{ record.result }}</span></div><div class="record-meta"><span>{{ record.actorId }}</span><span>·</span><span>{{ record.targetType }} / {{ record.targetId }}</span><span>·</span><time>{{ time(record.createdAt) }}</time></div><p v-if="record.reason" class="record-reason">{{ record.reason }}</p><details v-if="record.changesJson"><summary>查看变更摘要</summary><pre>{{ JSON.stringify(JSON.parse(record.changesJson), null, 2) }}</pre></details></div><code>{{ record.requestId }}</code></article>
      <nav v-if="pageCount > 1" class="audit-pagination"><button :disabled="page <= 1" @click="search(page - 1)">← 上一页</button><span>{{ page }} / {{ pageCount }}</span><button :disabled="page >= pageCount" @click="search(page + 1)">下一页 →</button></nav></template>
  </section>
</template>

<style scoped>
.audit-page{color:var(--commerce-ink)}.audit-heading{display:flex;justify-content:space-between;align-items:center;gap:24px;padding:28px 32px;border:1px solid #dedfd7;border-radius:18px;background:linear-gradient(120deg,#fffefa,#f0eee5)}.audit-eyebrow{margin-bottom:10px;color:#a07655;font-size:9px;font-weight:650;letter-spacing:.16em}.audit-heading h1{margin:0;font:500 clamp(28px,3.8vw,41px)/1.2 Georgia,"Microsoft YaHei",serif}.audit-heading h1 em{color:var(--commerce-green);font-style:normal}.audit-heading p:last-child{margin-top:10px;color:var(--commerce-muted);font-size:10px}.audit-seal{display:grid;width:70px;height:70px;place-content:center;border:1px solid #c9b592;border-radius:50%;color:#907958;font:8px Georgia,serif;letter-spacing:.2em;text-align:center;transform:rotate(8deg)}.audit-seal strong{font-size:13px}.audit-filter{display:grid;grid-template-columns:repeat(4,minmax(120px,1fr)) auto;gap:10px;align-items:end;margin:17px 0 23px;padding:17px;border:1px solid var(--commerce-line);border-radius:13px;background:var(--commerce-surface)}.audit-filter label{display:grid;gap:6px;color:var(--commerce-muted);font-size:9px}.audit-filter input{width:100%;height:34px;padding:0 9px;border:1px solid #e1e2da;border-radius:7px;outline:none;font: inherit; font-size: 10px}.audit-filter input:focus{border-color:#608d76;box-shadow:0 0 0 3px rgb(96 141 118 / 12%)}.audit-button{height:34px;padding:0 13px;border:0;border-radius:999px;color:white;background:var(--commerce-green);font: inherit; font-size: 9px; font-weight: 600;white-space:nowrap;cursor:pointer}.audit-button:disabled{opacity:.5}.audit-error{margin:-11px 0 12px;color:#a74636;font-size:10px}.audit-state{display:grid;min-height:180px;place-items:center;color:var(--commerce-muted);font-size:11px;text-align:center}.audit-summary{display:flex;justify-content:space-between;padding:0 4px 10px;color:var(--commerce-muted);font-size:9px}.audit-summary strong{color:var(--commerce-ink)}.audit-record{display:grid;grid-template-columns:27px 1fr minmax(100px,.45fr);gap:12px;align-items:start;margin-bottom:8px;padding:14px 15px;border:1px solid var(--commerce-line);border-radius:11px;background:var(--commerce-surface)}.record-marker{display:grid;width:23px;height:23px;place-items:center;border-radius:50%;color:#2d6548;background:#e6f1e8;font-size:10px}.record-title{display:flex;align-items:center;gap:10px;font-size:11px}.record-title span{padding:3px 6px;border-radius:999px;font-size:8px}.result-ok{color:#276247;background:#e5f0e7}.result-fail{color:#9c4938;background:#f8eae3}.record-meta{display:flex;flex-wrap:wrap;gap:7px;margin-top:7px;color:var(--commerce-muted);font-size:9px}.record-reason{margin-top:7px;color:#68756c;font-size:9px;line-height:1.6}.record-main details{margin-top:8px;color:#866449;font-size:9px}.record-main summary{cursor:pointer}.record-main pre{overflow:auto;max-height:180px;margin-top:7px;padding:9px;border-radius:7px;color:#536158;background:#f4f4ee;font:9px ui-monospace,Consolas,monospace}.audit-record>code{overflow:hidden;color:#949b94;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.audit-pagination{display:flex;justify-content:center;align-items:center;gap:17px;padding:18px;color:var(--commerce-muted);font-size:10px}.audit-pagination button{border:0;color:#8d654e;background:transparent;font:inherit;cursor:pointer}.audit-pagination button:disabled{opacity:.4;cursor:not-allowed}@media(max-width:900px){.audit-filter{grid-template-columns:repeat(2,minmax(0,1fr))}.audit-button{grid-column:1/-1}.audit-record{grid-template-columns:25px 1fr}.audit-record>code{grid-column:2}}@media(max-width:560px){.audit-heading{padding:22px}.audit-seal{width:54px;height:54px;font-size:7px}.audit-filter{padding:13px}.record-meta{gap:5px}}
</style>
