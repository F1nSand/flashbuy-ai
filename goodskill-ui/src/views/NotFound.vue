<script setup>
import {useRouter} from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="not-found">
    <div class="not-found-card">
      <p class="code">404</p>
      <h1>页面不存在</h1>
      <p class="description">这个地址没有对应的功能页面，请返回控制台继续操作。</p>
      <button type="button" @click="router.push('/dashboard')">返回控制台</button>
    </div>
  </main>
</template>

<style scoped>
.not-found { display: grid; min-height: 70vh; place-items: center; padding: 24px; background: var(--bg-primary); }
.not-found-card { max-width: 480px; padding: 44px; border: 1px solid var(--border-light); border-radius: 22px; text-align: center; background: var(--bg-secondary); box-shadow: var(--shadow-md); }
.code { margin: 0; color: #2563eb; font-size: 52px; font-weight: 800; letter-spacing: .06em; }
h1 { margin: 8px 0 12px; color: var(--text-primary); }
.description { margin: 0 0 24px; color: var(--text-secondary); line-height: 1.6; }
button { padding: 11px 18px; border: 0; border-radius: 10px; color: white; background: #2563eb; font: inherit; cursor: pointer; }
</style>
