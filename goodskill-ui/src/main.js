import {createApp} from 'vue'
import './style.css'
import '@fortawesome/fontawesome-free/css/all.min.css'
import App from './App.vue'
import router from './router'

if (import.meta.env.VITE_APP_MODE === 'production') {
  document.documentElement.dataset.appMode = 'production'
}

createApp(App).use(router).mount('#app')
