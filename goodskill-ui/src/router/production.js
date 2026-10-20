import {createRouter, createWebHistory} from 'vue-router'
import Login from '../views/Login.vue'
import ProductionLayout from '../layouts/ProductionLayout.vue'
import NotFound from '../views/NotFound.vue'
import {hasAnyRole, loadSession} from '../auth/session.js'

const routes = [
  {path: '/login', name: 'Login', component: Login, meta: {title: '登录', public: true}},
  {
    path: '/', component: ProductionLayout,
    children: [
      {path: '', redirect: '/activities'},
      {path: 'activities', name: 'ActivityList', component: () => import('../views/production/ActivityList.vue'), meta: {title: '限时活动', public: true}},
      {path: 'activities/:activityId', name: 'ActivityDetail', component: () => import('../views/production/ActivityDetail.vue'), meta: {title: '活动详情', public: true}},
      {path: 'orders', name: 'BuyerOrders', component: () => import('../views/production/BuyerOrders.vue'), meta: {title: '我的订单', roles: ['buyer']}},
      {path: 'manage/dashboard', name: 'ManageDashboard', component: () => import('../views/production/ManageDashboard.vue'), meta: {title: '经营概况', roles: ['system-manager']}},
      {path: 'manage/activities', name: 'ManageActivities', component: () => import('../views/production/ManageActivities.vue'), meta: {title: '活动运营', roles: ['system-manager', 'seckill-manager']}},
      {path: 'manage/orders', name: 'ManageOrders', component: () => import('../views/production/ManageOrders.vue'), meta: {title: '订单处理', roles: ['system-manager']}},
      {path: 'manage/inventory', name: 'ManageInventory', component: () => import('../views/production/ManageInventory.vue'), meta: {title: '库存流水', roles: ['system-manager']}},
      {path: 'manage/audit', name: 'ManageAudit', component: () => import('../views/production/ManageAudit.vue'), meta: {title: '操作审计', roles: ['system-manager']}},
      {path: ':pathMatch(.*)*', name: 'NotFound', component: NotFound, meta: {title: '页面不存在', public: true}}
    ]
  }
]

const router = createRouter({history: createWebHistory(), routes})

router.beforeEach(async (to) => {
  document.title = `${to.meta.title || 'GOODSKILL'} · GOODSKILL`
  if (to.path === '/login') {
    const user = await loadSession()
    if (!user) return true
    return typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/')
      ? to.query.redirect : '/activities'
  }
  if (to.meta.public) return true
  const user = await loadSession()
  if (!user) return {path: '/login', query: {redirect: to.fullPath}}
  if (to.meta.roles?.length && !hasAnyRole(to.meta.roles)) return '/activities'
  return true
})

export default router
