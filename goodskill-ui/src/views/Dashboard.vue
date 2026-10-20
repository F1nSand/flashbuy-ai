<script setup>
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import axios from 'axios'
import PageHeader from '../components/PageHeader.vue'
import {navigationItems} from '../navigation.js'

const router = useRouter()
const user = ref({username: 'Admin'})
const totals = ref({users: null, goods: null})
const states = ref({users: 'loading', goods: 'loading'})
const errors = ref({users: '', goods: ''})

const api = axios.create({timeout: 10000})
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.access_token = token
  return config
})

const expireSession = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('currentUser')
  router.push('/login')
}

api.interceptors.response.use((response) => response, (error) => {
  if (error.response?.status === 401) expireSession()
  return Promise.reject(error)
})

const countRequests = {
  users: {url: '/api/auth/user/list?page=1&size=1', label: '用户总数'},
  goods: {url: '/api/seckill/goods/list?page=1&size=1', label: '商品总数'}
}

const loadTotal = async (key) => {
  states.value[key] = 'loading'
  errors.value[key] = ''
  try {
    const response = await api.get(countRequests[key].url)
    const result = response.data
    if (result?.code === 401) {
      expireSession()
      return
    }
    if (result?.code !== 0 || result.data?.total == null || !Number.isFinite(Number(result.data.total))) {
      throw new Error(result?.msg || '服务返回了无效的统计数据')
    }
    totals.value[key] = Number(result.data.total)
    states.value[key] = 'success'
  } catch (error) {
    if (error.response?.status === 401 || error.response?.data?.code === 401) return
    states.value[key] = 'error'
    errors.value[key] = error.response?.data?.msg || error.message || '请求失败，请稍后重试'
  }
}

const navigate = (path) => router.push(path)
onMounted(() => {
  try {
    const savedUser = localStorage.getItem('currentUser')
    if (savedUser) user.value = JSON.parse(savedUser)
  } catch {
    user.value = {username: 'Admin'}
  }
  Promise.all([loadTotal('users'), loadTotal('goods')])
})
</script>

<template>
  <div class="dashboard-container">
    <PageHeader title="控制台" :show-back="false" />
    <main class="dashboard-content">
      <section class="welcome-card">
        <div>
          <h1>欢迎回来，{{ user.username || 'Admin' }} 👋</h1>
          <p>管理用户、商品并运行秒杀模拟。</p>
        </div>
      </section>

      <section class="stats-section" aria-label="业务总数">
        <article v-for="key in ['users', 'goods']" :key="key" class="stat-card">
          <div class="stat-icon" :class="`stat-icon-${key}`">
            <svg v-if="key === 'users'" viewBox="0 0 24 24" aria-hidden="true"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m7-10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm13 10v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="m12 2 9 5v10l-9 5-9-5V7l9-5zm-9 5 9 5 9-5m-9 5v10"/></svg>
          </div>
          <div class="stat-info">
            <h2>{{ countRequests[key].label }}</h2>
            <p v-if="states[key] === 'loading'" class="stat-state" role="status">正在加载…</p>
            <p v-else-if="states[key] === 'success'" class="stat-value">{{ totals[key].toLocaleString() }}</p>
            <div v-else class="stat-error" role="alert">
              <span>{{ errors[key] }}</span>
              <button type="button" @click="loadTotal(key)">重试</button>
            </div>
          </div>
        </article>
      </section>

      <section class="quick-links">
        <h2>快捷入口</h2>
        <div class="menu-grid">
          <button v-for="item in navigationItems" :key="item.path" type="button" class="menu-card" @click="navigate(item.path)">
            <span class="menu-icon" :style="{'--icon-color': item.color}">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="item.icon"/></svg>
            </span>
            <span class="menu-info"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
            <span class="menu-arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.dashboard-container { min-height: calc(100vh - 80px); background: var(--bg-primary); }
.dashboard-content { max-width: 1240px; margin: 0 auto; padding: 32px 40px 48px; }
.welcome-card { padding: 30px 34px; border-radius: 22px; color: white; background: linear-gradient(135deg,#0f172a,#1e3a5f); box-shadow: var(--shadow-md); }
.welcome-card h1 { margin: 0 0 8px; font-size: 26px; }
.welcome-card p { margin: 0; color: #cbd5e1; }
.stats-section { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 22px; margin: 26px 0 36px; }
.stat-card { display: flex; align-items: center; gap: 18px; min-height: 132px; padding: 24px; border: 1px solid var(--border-light); border-radius: 18px; background: var(--bg-secondary); box-shadow: var(--shadow-sm); }
.stat-icon { display: grid; place-items: center; width: 52px; height: 52px; flex: 0 0 52px; border-radius: 15px; color: #2563eb; background: #eff6ff; }
.stat-icon-goods { color: #ea580c; background: #fff7ed; }
.stat-icon svg { width: 27px; height: 27px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.stat-info { min-width: 0; }
.stat-info h2 { margin: 0 0 8px; color: var(--text-secondary); font-size: 14px; font-weight: 600; }
.stat-value { margin: 0; color: var(--text-primary); font-size: 29px; font-weight: 700; }
.stat-state { margin: 0; color: var(--text-secondary); }
.stat-error { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; color: var(--danger-color); font-size: 13px; }
.stat-error button { padding: 5px 10px; border: 0; border-radius: 7px; color: white; background: #2563eb; cursor: pointer; }
.quick-links h2 { margin: 0 0 18px; color: var(--text-primary); font-size: 19px; }
.menu-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; }
.menu-card { display: flex; align-items: center; gap: 16px; min-height: 112px; padding: 20px; border: 1px solid var(--border-light); border-radius: 17px; color: inherit; text-align: left; background: var(--bg-secondary); cursor: pointer; transition: transform .18s ease, box-shadow .18s ease; }
.menu-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }
.menu-icon { display: grid; place-items: center; width: 48px; height: 48px; flex: 0 0 48px; border-radius: 14px; color: white; background: var(--icon-color); }
.menu-icon svg { width: 25px; height: 25px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.menu-info { display: grid; gap: 6px; flex: 1; }
.menu-info strong { color: var(--text-primary); font-size: 15px; }
.menu-info small { color: var(--text-secondary); font-size: 12px; }
.menu-arrow { color: var(--text-secondary); font-size: 22px; }
@media (max-width: 720px) { .dashboard-content { padding: 22px 18px 36px; } .stats-section,.menu-grid { grid-template-columns: 1fr; } .welcome-card { padding: 24px; } }
</style>
