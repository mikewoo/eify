import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import i18n from '@/i18n'
import enUS from 'element-plus/es/locale/lang/en'
import zhCN from 'element-plus/es/locale/lang/zh-cn'

export const useLocaleStore = defineStore('locale', () => {
  const current = ref<'zh-CN' | 'en-US'>((localStorage.getItem('eify_lang') as 'zh-CN' | 'en-US') || 'zh-CN')

  const elementLocale = computed(() => current.value === 'en-US' ? enUS : zhCN)

  function setLocale(locale: 'zh-CN' | 'en-US') {
    current.value = locale
    i18n.global.locale.value = locale
    localStorage.setItem('eify_lang', locale)
    document.cookie = `eify_lang=${locale};path=/;max-age=${365 * 86400};SameSite=Lax`
  }

  function toggle() {
    setLocale(current.value === 'zh-CN' ? 'en-US' : 'zh-CN')
  }

  return { current, elementLocale, setLocale, toggle }
})
