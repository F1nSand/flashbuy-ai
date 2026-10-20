<script setup>
import {computed, onMounted, onUnmounted} from 'vue'
import {RouterLink, RouterView, useRoute, useRouter} from 'vue-router'
import {hasAnyRole, loadSession, logout, resetSession, session} from '../auth/session.js'

const route = useRoute()
const router = useRouter()
const isManager = computed(() => hasAnyRole(['seckill-manager', 'system-manager']))
const isSystemManager = computed(() => hasAnyRole(['system-manager']))
const onSessionExpired = () => {
  resetSession()
  router.replace({path: '/login', query: {redirect: route.fullPath}})
}

const signOut = async () => {
  try {
    await logout()
  } finally {
    await router.replace('/login')
  }
}

onMounted(() => {
  loadSession()
  window.addEventListener('goodskill:session-expired', onSessionExpired)
})
onUnmounted(() => window.removeEventListener('goodskill:session-expired', onSessionExpired))
</script>

<template>
  <div class="commerce-shell">
    <header class="commerce-topbar">
      <RouterLink class="commerce-brand" to="/activities" aria-label="GoodSkill 活动首页">
        <span class="brand-mark" aria-hidden="true">G</span>
        <span><strong>GOODSKILL</strong><small>限时好物 · 透明下单</small></span>
      </RouterLink>
      <nav class="commerce-nav" aria-label="主导航">
        <RouterLink to="/activities" :class="{active: route.path.startsWith('/activities')}">活动</RouterLink>
        <RouterLink to="/orders" :class="{active: route.path.startsWith('/orders')}">我的订单</RouterLink>
        <RouterLink v-if="isManager" to="/manage/activities" :class="{active: route.path.startsWith('/manage/activities')}">活动运营</RouterLink>
        <RouterLink v-if="isSystemManager" to="/manage/dashboard" :class="{active: route.path.startsWith('/manage/dashboard')}">经营概况</RouterLink>
        <RouterLink v-if="isSystemManager" to="/manage/orders" :class="{active: route.path.startsWith('/manage/orders')}">订单处理</RouterLink>
        <RouterLink v-if="isSystemManager" to="/manage/inventory" :class="{active: route.path.startsWith('/manage/inventory')}">库存流水</RouterLink>
        <RouterLink v-if="isSystemManager" to="/manage/audit" :class="{active: route.path.startsWith('/manage/audit')}">操作审计</RouterLink>
      </nav>
      <div class="commerce-account">
        <span class="account-dot" aria-hidden="true"></span>
        <span class="account-name">{{ session.user?.username || '游客浏览' }}</span>
        <RouterLink v-if="!session.user" class="signout-button" to="/login">登录</RouterLink>
        <button v-else type="button" class="signout-button" @click="signOut">退出</button>
      </div>
    </header>
    <main class="commerce-main">
      <RouterView />
    </main>
    <footer class="commerce-footer">
      <span>GOODSKILL COMMERCE</span>
      <span>订单状态以服务端记录为准</span>
    </footer>
  </div>
</template>

<style>
:root {
  --commerce-paper: #f5f3ed;
  --commerce-surface: #fffefa;
  --commerce-ink: #1d302b;
  --commerce-muted: #687671;
  --commerce-line: #dedfd7;
  --commerce-green: #17634b;
  --commerce-green-deep: #124b3b;
  --commerce-orange: #dc653f;
  --commerce-orange-soft: #fbe9df;
  --commerce-shadow: 0 18px 44px rgb(30 48 42 / 8%);
  --commerce-radius: 18px;
}

html[data-app-mode="production"], html[data-app-mode="production"] body {
  height: auto;
  min-height: 100%;
  min-width: 360px;
  overflow: auto;
  background: var(--commerce-paper);
  color: var(--commerce-ink);
  font-family: Inter, "Segoe UI", "Microsoft YaHei", sans-serif;
}

html[data-app-mode="production"] #app { height: auto; min-height: 100vh; }
.commerce-shell { min-height: 100vh; background: radial-gradient(ellipse at 83% 4%, rgb(232 226 208 / 48%), transparent 33%), var(--commerce-paper); }
.commerce-topbar { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: minmax(210px, 1fr) auto minmax(210px, 1fr); align-items: center; gap: 28px; padding: 0 clamp(20px, 5vw, 76px); min-height: 76px; color: #f6f4ed; background: var(--commerce-green-deep); box-shadow: 0 8px 24px rgb(18 75 59 / 14%); }
.commerce-brand { display: flex; align-items: center; gap: 12px; color: inherit; text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid rgb(255 255 255 / 34%); border-radius: 12px; color: #f5d08c; font-family: Georgia, serif; font-size: 23px; font-weight: 700; }
.commerce-brand span:last-child { display: grid; gap: 2px; }
.commerce-brand strong { font-size: 12px; letter-spacing: .16em; }
.commerce-brand small { color: #bac9c1; font-size: 10px; letter-spacing: .08em; }
.commerce-nav { display: flex; align-items: center; gap: clamp(14px, 2vw, 30px); }
.commerce-nav a { position: relative; padding: 28px 0 25px; color: #c4d1ca; font-size: 13px; text-decoration: none; white-space: nowrap; transition: color .18s ease; }
.commerce-nav a:hover, .commerce-nav a.active { color: #fffefa; }
.commerce-nav a.active::after { position: absolute; right: 0; bottom: 17px; left: 0; height: 2px; border-radius: 2px; background: #e6bd76; content: ""; }
.commerce-account { display: flex; justify-content: flex-end; align-items: center; gap: 10px; min-width: 0; }
.account-dot { width: 8px; height: 8px; border-radius: 50%; background: #8fd0a2; box-shadow: 0 0 0 4px rgb(143 208 162 / 14%); }
.account-name { overflow: hidden; max-width: 120px; color: #dce5df; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.signout-button { padding: 8px 12px; border: 1px solid rgb(255 255 255 / 22%); border-radius: 999px; color: #fffefa; background: transparent; font: inherit; font-size: 11px; text-decoration: none; cursor: pointer; }
.signout-button:hover { border-color: #e6bd76; color: #f5d08c; }
.commerce-main { width: min(1320px, 100% - 48px); min-height: calc(100vh - 130px); margin: 0 auto; padding: 44px 0 74px; }
.commerce-footer { display: flex; justify-content: space-between; gap: 18px; padding: 18px clamp(20px, 5vw, 76px); border-top: 1px solid var(--commerce-line); color: var(--commerce-muted); font-size: 10px; letter-spacing: .08em; }
@media (max-width: 900px) {
  .commerce-topbar { grid-template-columns: 1fr auto; gap: 10px 18px; padding-top: 12px; padding-bottom: 10px; }
  .commerce-nav { grid-column: 1 / -1; grid-row: 2; order: 3; overflow-x: auto; gap: 22px; }
  .commerce-nav a { padding: 10px 0 12px; }
  .commerce-nav a.active::after { bottom: 5px; }
  .commerce-account { grid-column: 2; grid-row: 1; }
  .commerce-main { width: min(100% - 32px, 720px); padding-top: 28px; }
}
@media (max-width: 480px) {
  .commerce-topbar { padding-right: 16px; padding-left: 16px; }
  .account-name { display: none; }
  .commerce-footer { flex-direction: column; padding-right: 16px; padding-left: 16px; }
}
</style>
