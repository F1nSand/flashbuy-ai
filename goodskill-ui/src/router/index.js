import {createRouter, createWebHistory} from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import UserManagement from '../views/UserManagement.vue'
import ProductsManagement from '../views/ProductsManagement.vue'
import SeckillSimulation from '../views/SeckillSimulation.vue'
import NotFound from '../views/NotFound.vue'

const routes = [
  {path: '/', redirect: '/dashboard'},
  {path: '/login', name: 'Login', component: Login, meta: {title: '登录'}},
  {path: '/dashboard', name: 'Dashboard', component: Dashboard, meta: {title: '控制台'}},
  {path: '/users', name: 'UserManagement', component: UserManagement, meta: {title: '用户管理'}},
  {path: '/products', name: 'ProductsManagement', component: ProductsManagement, meta: {title: '商品管理'}},
  {path: '/seckill-simulation', name: 'SeckillSimulation', component: SeckillSimulation, meta: {title: '秒杀模拟'}},
  {path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound, meta: {title: '页面不存在'}}
]

const router = createRouter({history: createWebHistory(), routes})

router.beforeEach(async (to) => {
  document.title = `${to.meta.title || '秒杀后台管理'} · 秒杀后台管理`
  if (to.path !== '/login' && !localStorage.getItem('token')) return '/login'
  return true
})

export default router
