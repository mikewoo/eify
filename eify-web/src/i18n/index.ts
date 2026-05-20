import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'
import type { LocaleMessages } from './locales/types'

const savedLocale = localStorage.getItem('eify_lang') || 'zh-CN'

const i18n = createI18n<[LocaleMessages], 'zh-CN' | 'en-US', false>({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export default i18n
