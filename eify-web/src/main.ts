import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import './styles/design-tokens.css'
import './styles/utilities.css'
import './styles/components.css'
import './styles/sidebar.css'
import './styles/page.css'

import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import i18n from './i18n'
import router from './router'
import { createPinia } from 'pinia'
import { useLocaleStore } from './store/locale'
import App from './App.vue'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(i18n)
app.use(ElementPlus)
app.use(router)
const pinia = createPinia()
app.use(pinia)

// Initialize locale store before mount so Element Plus locale is ready
useLocaleStore()

router.isReady().then(() => {
  app.mount('#app')
})
