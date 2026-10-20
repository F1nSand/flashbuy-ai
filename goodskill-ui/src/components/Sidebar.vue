<script setup>
import {computed} from 'vue'
import {useRouter} from 'vue-router'
import {navigationItems} from '../navigation.js'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:collapsed'])

const router = useRouter()
const currentRoute = computed(() => router.currentRoute.value.path)

const toggleSidebar = () => {
  emit('update:collapsed', !props.collapsed)
}

const isActive = (path) => {
  return currentRoute.value === path
}

</script>

<template>
  <aside class="sidebar" :class="{ collapsed: collapsed }">
    <div class="sidebar-header">
      <div class="sidebar-logo" v-if="!collapsed">
        <div class="logo-icon-wrapper">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
          </svg>
        </div>
        <span class="logo-text">秒杀管理</span>
      </div>
      <button class="collapse-btn" @click="toggleSidebar">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline v-if="collapsed" points="9 18 15 12 9 6"/>
          <polyline v-else points="15 18 9 12 15 6"/>
        </svg>
      </button>
    </div>
    <nav class="sidebar-nav">
      <div
        v-for="item in navigationItems"
        :key="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="router.push(item.path)"
      >
        <div class="nav-icon" :style="{ color: item.color, backgroundColor: isActive(item.path) ? item.color : item.color + '15' }">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ stroke: isActive(item.path) ? 'white' : 'currentColor' }">
            <path :d="item.icon"/>
          </svg>
        </div>
        <span class="nav-title">{{ item.title }}</span>
        <div class="active-indicator" v-if="isActive(item.path)" :style="{ backgroundColor: item.color }"></div>
      </div>
    </nav>
    <div class="sidebar-footer">
      <div class="footer-info" v-if="!collapsed">
        <div class="footer-label">© 2026</div>
        <div class="footer-version">v1.0.0</div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  height: 100vh;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 24px rgba(0,0,0,0.02);
}

.sidebar.collapsed {
  width: 80px;
}

.sidebar.collapsed .sidebar-nav {
  padding: 24px 0;
  align-items: center;
}

.sidebar.collapsed .nav-item {
  padding: 12px 0;
  justify-content: center;
  width: 48px;
}

.sidebar.collapsed .sidebar-header {
  padding: 24px 0;
  justify-content: center;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  height: 80px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
  overflow: hidden;
}

.logo-icon-wrapper {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--accent-primary) 0%, #ec4899 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(to right, var(--text-primary), var(--text-secondary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.5px;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: var(--bg-primary);
  color: var(--accent-primary);
  border-color: var(--border-color);
}

.sidebar-nav {
  flex: 1;
  padding: 24px 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}

.nav-item:hover {
  background: var(--bg-tertiary);
}

.nav-item.active {
  background: var(--bg-tertiary);
}

.nav-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.nav-item:hover .nav-icon {
  transform: scale(1.05);
}

.nav-item.active .nav-icon {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.nav-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: color 0.2s ease;
  white-space: nowrap;
}

.nav-item:hover .nav-title {
  color: var(--text-primary);
}

.nav-item.active .nav-title {
  color: var(--text-primary);
  font-weight: 600;
}

.sidebar.collapsed .nav-title {
  opacity: 0;
  width: 0;
  display: none;
}

.active-indicator {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 20px;
  border-radius: 4px 0 0 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.nav-item.active .active-indicator {
  opacity: 1;
}

.sidebar-footer {
  padding: 24px;
  border-top: 1px solid var(--border-color);
}

.footer-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.sidebar.collapsed .footer-info {
  display: none;
}

.sidebar-nav::-webkit-scrollbar {
  width: 0;
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
    box-shadow: none;
  }

  .sidebar.open {
    transform: translateX(0);
    box-shadow: 4px 0 24px rgba(0,0,0,0.1);
  }
}
</style>
