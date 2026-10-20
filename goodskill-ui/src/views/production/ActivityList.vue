<script setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {RouterLink} from 'vue-router'
import {api, formatApiError, serverNow} from '../../api/client.js'

const activities = ref([])
const loading = ref(true)
const error = ref('')
const clock = ref(serverNow())
let timer

const liveCount = computed(() => activities.value.filter(activity => activity.startsAt && activity.endsAt
  && Date.parse(activity.startsAt) <= clock.value && Date.parse(activity.endsAt) > clock.value).length)

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/seckill/activities', {params: {limit: 50}})
    if (response.data?.code !== 0 || !Array.isArray(response.data?.data)) throw new Error('活动暂时无法加载')
    activities.value = response.data.data
  } catch (cause) {
    error.value = formatApiError(cause)
  } finally {
    loading.value = false
  }
}

const price = minor => `¥${(Number(minor || 0) / 100).toFixed(2)}`
const dateLabel = value => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
}).format(new Date(value))
const statusLabel = activity => {
  if (Date.parse(activity.startsAt) > clock.value) return '即将开始'
  if (Date.parse(activity.endsAt) <= clock.value) return '已结束'
  return '正在进行'
}

onMounted(() => {
  load()
  timer = window.setInterval(() => { clock.value = serverNow() }, 1000)
})
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="activity-page">
    <section class="store-hero">
      <div class="hero-copy">
        <p class="eyebrow"><span class="eyebrow-dot"></span> GOODSKILL SELECT · 每日精选</p>
        <h1>好物有时，<em>准时开场。</em></h1>
        <p class="hero-intro">每场活动都有明确的开始时间、库存和购买规则。下单后，你可以在订单页查看最新进度。</p>
        <div class="hero-proof"><span>01 / 价格下单时锁定</span><span>02 / 订单实时可查</span></div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="art-orbit orbit-one"></div><div class="art-orbit orbit-two"></div>
        <div class="art-product"><span>LIMITED<br>DROP</span><b>G</b></div>
        <div class="art-note">活动场次 <strong>{{ activities.length.toString().padStart(2, '0') }}</strong></div>
      </div>
      <div class="hero-index"><span>进行中</span><strong>{{ liveCount.toString().padStart(2, '0') }}</strong><i>场</i></div>
    </section>

    <section class="activity-section" aria-labelledby="activity-heading">
      <div class="section-heading">
        <div><p class="eyebrow dark-eyebrow">THE LIVE CALENDAR</p><h2 id="activity-heading">正在发生的活动</h2></div>
        <span class="update-note">状态按服务端时间更新</span>
      </div>

      <div v-if="loading" class="state-panel" role="status"><span class="loading-mark"></span><p>正在读取活动…</p></div>
      <div v-else-if="error" class="state-panel state-error" role="alert"><p>{{ error }}</p><button class="commerce-button secondary" @click="load">重试加载</button></div>
      <div v-else-if="activities.length === 0" class="state-panel"><span class="empty-stamp">—</span><h3>新场次正在准备</h3><p>目前没有开放中的活动，稍后再来看看。</p></div>

      <div v-else class="activity-grid">
        <article v-for="(activity, index) in activities" :key="activity.activityId" class="activity-card" :class="{'activity-card-ended': statusLabel(activity) === '已结束'}">
          <div class="card-art" :class="`card-art-${index % 4}`">
            <span class="card-edition">DROP / {{ String(index + 1).padStart(2, '0') }}</span>
            <span class="card-glyph" aria-hidden="true">{{ ['◌', '✳', '⌁', '◇'][index % 4] }}</span>
            <span class="card-status" :class="{'status-live': statusLabel(activity) === '正在进行'}">{{ statusLabel(activity) }}</span>
          </div>
          <div class="card-content">
            <div class="card-meta"><span>{{ activity.activityCode }}</span><span>至 {{ dateLabel(activity.endsAt) }}</span></div>
            <h3>{{ activity.title }}</h3>
            <p class="sku-summary">{{ activity.skus?.[0]?.productName }} <span v-if="activity.skus?.length > 1">等 {{ activity.skus.length }} 款</span></p>
            <div class="card-footer">
              <div><small>活动价起</small><strong>{{ price(Math.min(...(activity.skus || []).map(sku => sku.unitPriceMinor))) }}</strong></div>
              <RouterLink class="commerce-button" :to="`/activities/${activity.activityId}`">查看活动 <span aria-hidden="true">↗</span></RouterLink>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="trust-band" aria-label="购买说明">
      <div><span>01</span><strong>确认商品与数量</strong><small>购买规则在活动中展示</small></div>
      <div><span>02</span><strong>提交生成订单</strong><small>重复提交会复用原订单</small></div>
      <div><span>03</span><strong>前往支付并跟踪</strong><small>订单状态以服务端为准</small></div>
    </section>
  </div>
</template>

<style scoped>
.activity-page { color: var(--commerce-ink); }
.store-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr); min-height: 362px; overflow: hidden; padding: clamp(30px, 5vw, 66px); border-radius: 26px; color: #f5f3ed; background: #1a4e3e; box-shadow: var(--commerce-shadow); }
.store-hero::after { position: absolute; right: 0; bottom: 0; left: 0; height: 1px; background: linear-gradient(90deg, transparent, rgb(238 219 178 / 70%), transparent); content: ""; }
.hero-copy { position: relative; z-index: 1; align-self: center; max-width: 640px; }
.eyebrow { display: flex; align-items: center; gap: 9px; margin-bottom: 22px; color: #b8cec1; font-size: 10px; font-weight: 650; letter-spacing: .17em; }
.eyebrow-dot { width: 7px; height: 7px; border-radius: 50%; background: #efa776; box-shadow: 0 0 0 5px rgb(239 167 118 / 13%); }
.store-hero h1 { max-width: 570px; margin: 0; font-family: Georgia, "Noto Serif SC", serif; font-size: clamp(36px, 5vw, 62px); font-weight: 500; letter-spacing: .02em; line-height: 1.15; }
.store-hero h1 em { color: #f0c78c; font-style: normal; }
.hero-intro { max-width: 430px; margin: 18px 0 0; color: #d1ded6; font-size: 14px; line-height: 1.9; }
.hero-proof { display: flex; flex-wrap: wrap; gap: 10px 24px; margin-top: 30px; color: #c4d4ca; font-size: 10px; letter-spacing: .04em; }
.hero-art { position: relative; display: grid; place-items: center; min-height: 240px; }
.art-orbit { position: absolute; width: 230px; height: 230px; border: 1px solid rgb(229 216 186 / 24%); border-radius: 50%; transform: rotate(-24deg) scaleY(.42); }
.orbit-two { width: 295px; height: 295px; transform: rotate(48deg) scaleY(.37); }
.art-product { position: relative; display: grid; place-items: center; width: 152px; height: 182px; border: 1px solid rgb(240 199 140 / 56%); border-radius: 46% 46% 18px 18px; color: #f7dfb0; background: linear-gradient(145deg, rgb(255 255 255 / 15%), rgb(255 255 255 / 4%)); box-shadow: 16px 25px 36px rgb(7 37 29 / 25%); transform: rotate(8deg); }
.art-product span { position: absolute; top: 18px; left: 18px; color: #c5d5ca; font-size: 8px; letter-spacing: .16em; line-height: 1.5; }
.art-product b { font-family: Georgia, serif; font-size: 88px; font-weight: 500; }
.art-note { position: absolute; right: 0; bottom: 16px; display: flex; align-items: baseline; gap: 8px; color: #c2d5ca; font-size: 10px; letter-spacing: .1em; }
.art-note strong { color: #efc891; font-family: Georgia, serif; font-size: 22px; font-weight: 500; }
.hero-index { position: absolute; right: 28px; bottom: 24px; display: flex; align-items: baseline; gap: 7px; color: #b9cec1; font-size: 10px; letter-spacing: .12em; }
.hero-index strong { color: #f2cb8e; font-family: Georgia, serif; font-size: 21px; font-weight: 500; }
.activity-section { padding-top: 58px; }
.section-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin-bottom: 22px; }
.dark-eyebrow { margin-bottom: 8px; color: #a07655; }
.section-heading h2 { margin: 0; font-family: Georgia, "Noto Serif SC", serif; font-size: clamp(24px, 3vw, 31px); font-weight: 500; }
.update-note { padding-bottom: 4px; color: var(--commerce-muted); font-size: 11px; }
.activity-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.activity-card { overflow: hidden; border: 1px solid #e3e2db; border-radius: var(--commerce-radius); background: var(--commerce-surface); box-shadow: 0 8px 24px rgb(30 48 42 / 4%); transition: transform .2s ease, box-shadow .2s ease; }
.activity-card:hover { transform: translateY(-4px); box-shadow: var(--commerce-shadow); }
.card-art { position: relative; display: grid; place-items: center; height: 174px; overflow: hidden; background: #d9e1d5; }
.card-art::before, .card-art::after { position: absolute; border: 1px solid rgb(29 48 43 / 16%); border-radius: 50%; content: ""; }
.card-art::before { width: 180px; height: 180px; transform: scaleY(.48) rotate(-20deg); }
.card-art::after { width: 260px; height: 260px; transform: scaleY(.38) rotate(35deg); }
.card-art-1 { background: #e9dcc8; }.card-art-2 { background: #d9e1e4; }.card-art-3 { background: #e8d9d0; }
.card-edition { position: absolute; top: 15px; left: 17px; z-index: 1; color: rgb(29 48 43 / 60%); font-size: 8px; letter-spacing: .15em; }
.card-glyph { color: rgb(26 78 62 / 70%); font-family: Georgia, serif; font-size: 102px; line-height: 1; }
.card-art-1 .card-glyph { color: #b4784c; }.card-art-2 .card-glyph { color: #476779; }.card-art-3 .card-glyph { color: #9e6659; }
.card-status { position: absolute; right: 15px; bottom: 14px; z-index: 1; padding: 6px 9px; border-radius: 999px; color: #5b655e; background: rgb(255 254 250 / 86%); font-size: 9px; }
.card-status.status-live { color: #1c6249; background: #e4f0e7; }
.activity-card-ended .card-art { filter: saturate(.4); }
.card-content { padding: 19px 20px 18px; }
.card-meta { display: flex; justify-content: space-between; gap: 8px; color: #89918b; font-size: 9px; }
.card-content h3 { overflow: hidden; margin: 12px 0 6px; font-size: 17px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.sku-summary { min-height: 18px; margin: 0; color: var(--commerce-muted); font-size: 11px; }
.card-footer { display: flex; justify-content: space-between; align-items: flex-end; gap: 10px; margin-top: 20px; padding-top: 15px; border-top: 1px solid #ecece5; }
.card-footer > div { display: grid; gap: 3px; }.card-footer small { color: #929b94; font-size: 9px; }.card-footer strong { color: #b75439; font-family: Georgia, serif; font-size: 22px; font-weight: 500; }
.commerce-button { display: inline-flex; justify-content: center; align-items: center; gap: 9px; min-height: 36px; padding: 0 15px; border: 1px solid var(--commerce-green); border-radius: 999px; color: #fffefa; background: var(--commerce-green); font: inherit; font-size: 10px; text-decoration: none; cursor: pointer; transition: background .16s ease, transform .16s ease; }
.commerce-button:hover { background: var(--commerce-green-deep); transform: translateY(-1px); }
.commerce-button.secondary { color: var(--commerce-green); background: transparent; }
.state-panel { display: grid; justify-items: center; gap: 10px; min-height: 230px; padding: 36px; border: 1px dashed #c9cec5; border-radius: var(--commerce-radius); color: var(--commerce-muted); text-align: center; background: rgb(255 254 250 / 65%); }
.state-panel p { margin: 0; font-size: 13px; }.state-panel h3 { margin: 0; color: var(--commerce-ink); font-family: Georgia, serif; font-size: 22px; font-weight: 500; }.state-error { border-color: #e4b6a6; color: #a34832; }
.empty-stamp { color: #bd875e; font-family: Georgia, serif; font-size: 37px; }
.loading-mark { width: 24px; height: 24px; border: 2px solid #cbd5cb; border-top-color: var(--commerce-green); border-radius: 50%; animation: spin .8s linear infinite; }
.trust-band { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0; margin-top: 38px; padding: 23px 12px; border-top: 1px solid var(--commerce-line); border-bottom: 1px solid var(--commerce-line); }
.trust-band > div { display: grid; grid-template-columns: 35px 1fr; gap: 2px 10px; padding: 0 18px; border-right: 1px solid var(--commerce-line); }.trust-band > div:last-child { border: 0; }
.trust-band span { grid-row: span 2; color: #bd8058; font-family: Georgia, serif; font-size: 19px; }.trust-band strong { font-size: 11px; }.trust-band small { color: var(--commerce-muted); font-size: 9px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) { .store-hero { grid-template-columns: 1fr .7fr; min-height: 330px; }.activity-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }.hero-index { right: 18px; bottom: 14px; } }
@media (max-width: 640px) { .store-hero { grid-template-columns: 1fr; min-height: 470px; padding: 28px 24px; }.hero-art { min-height: 200px; }.art-product { width: 116px; height: 140px; }.art-product b { font-size: 68px; }.hero-index { bottom: 20px; }.activity-section { padding-top: 42px; }.activity-grid { grid-template-columns: 1fr; }.card-art { height: 160px; }.trust-band { grid-template-columns: 1fr; gap: 15px; }.trust-band > div { border-right: 0; }.section-heading { align-items: flex-start; flex-direction: column; gap: 8px; } }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; transition-duration: .01ms !important; } }
</style>
