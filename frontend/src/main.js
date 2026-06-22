import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createHead } from '@vueuse/head'
import App from './App.vue'
import router from './router'
import './assets/material-symbols.css'
import './style.css'

const head = createHead()

createApp(App).use(head).use(createPinia()).use(router).mount('#app')
