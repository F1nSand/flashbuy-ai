import {createApp} from 'vue'
import './style.css'
import '@fortawesome/fontawesome-free/css/all.min.css'
import App from './App.production.vue'
import router from './router/production.js'

document.documentElement.dataset.appMode = 'production'
createApp(App).use(router).mount('#app')
