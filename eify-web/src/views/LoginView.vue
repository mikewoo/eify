<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { useLocaleStore } from '@/store/locale'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const localeStore = useLocaleStore()
const { t } = useI18n()

const activeTab = ref<'login' | 'register'>('login')
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()
const loading = ref(false)

const appVersion = computed(() => __APP_VERSION__.replace(/^v/, ''))
const versionBadge = computed(() => {
  const v = __APP_VERSION__.toLowerCase()
  if (v.includes('-dev') || v.includes('-snapshot') || v === 'dev') return 'DEV'
  if (v.includes('-test')) return 'TEST'
  if (v.includes('-staging') || v.includes('-rc')) return 'STAGING'
  return ''
})
const currentYear = computed(() => new Date().getFullYear())

const localeOptions = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' }
]

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loginRules: FormRules = {
  username: [{ required: true, message: () => t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: () => t('login.passwordRequired'), trigger: 'blur' }]
}

const validateConfirmPassword = (_rule: any, value: string, callback: Function) => {
  if (value !== registerForm.password) {
    callback(new Error(t('login.passwordMismatch')))
  } else {
    callback()
  }
}

const registerRules: FormRules = {
  username: [
    { required: true, message: () => t('login.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 64, message: () => t('login.usernameLength'), trigger: 'blur' }
  ],
  email: [
    { required: true, message: () => t('login.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('login.emailInvalid'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: () => t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 128, message: () => t('login.passwordLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: () => t('login.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await authStore.login(loginForm.username, loginForm.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    console.error('[Login] Login failed:', e)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerFormRef.value) return
  try {
    await registerFormRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await authStore.register(registerForm.username, registerForm.email, registerForm.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    // error handled by request interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- Background decoration -->
    <div class="login-bg">
      <div class="login-bg-grid"></div>
      <div class="login-bg-glow login-bg-glow-1"></div>
      <div class="login-bg-glow login-bg-glow-2"></div>
    </div>

    <!-- Login card -->
    <div class="login-card">
      <!-- 语言切换 -->
      <div class="login-locale text-sm">
        <el-select
          :model-value="localeStore.current"
          :placeholder="t('login.localeLabel')"
          size="small"
          class="locale-select"
          popper-class="locale-popper"
          @update:model-value="localeStore.setLocale($event as string)"
        >
          <el-option
            v-for="opt in localeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>

      <div class="login-brand">
        <div class="login-logo">EIFY</div>
        <div class="login-subtitle text-sm">AI Agent Platform</div>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs text-lg" :stretch="true">
        <el-tab-pane :label="t('login.loginTab')" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            @keyup.enter="handleLogin"
          >
            <el-form-item :label="t('login.username')" prop="username">
              <el-input v-model="loginForm.username" :placeholder="t('login.usernamePlaceholder')" prefix-icon="User" />
            </el-form-item>
            <el-form-item :label="t('login.password')" prop="password">
              <el-input v-model="loginForm.password" type="password" :placeholder="t('login.passwordPlaceholder')" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn text-lg" :loading="loading" @click="handleLogin">
                {{ t('login.loginBtn') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="t('login.registerTab')" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            @keyup.enter="handleRegister"
          >
            <el-form-item :label="t('login.username')" prop="username">
              <el-input v-model="registerForm.username" :placeholder="t('login.usernameHint')" prefix-icon="User" />
            </el-form-item>
            <el-form-item :label="t('login.email')" prop="email">
              <el-input v-model="registerForm.email" :placeholder="t('login.emailPlaceholder')" prefix-icon="Message" />
            </el-form-item>
            <el-form-item :label="t('login.password')" prop="password">
              <el-input v-model="registerForm.password" type="password" :placeholder="t('login.passwordHint')" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item :label="t('login.confirmPassword')" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" :placeholder="t('login.confirmPasswordPlaceholder')" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn text-lg" :loading="loading" @click="handleRegister">
                {{ t('login.registerBtn') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="login-footer">
        <div class="login-version">
          <span class="login-version-text text-xs">v{{ appVersion }}</span>
          <span v-if="versionBadge" class="login-version-badge text-xs">{{ versionBadge }}</span>
        </div>
        <div class="login-copyright text-xs">
          {{ t('login.copyright', { year: currentYear }) }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #0f0f1a;
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(99, 102, 241, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

.login-bg-glow {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.12;
}

.login-bg-glow-1 {
  top: -200px;
  right: -100px;
  background: var(--eify-primary);
}

.login-bg-glow-2 {
  bottom: -200px;
  left: -100px;
  background: var(--eify-primary-400);
}

.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.login-locale {
  position: absolute;
  top: 16px;
  right: 20px;
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}

.locale-select {
  width: 110px;
}

.locale-select :deep(.el-input) {
  background: transparent !important;
}

.locale-select :deep(.el-select__wrapper) {
  background: transparent !important;
  box-shadow: none !important;
}

.locale-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: none !important;
  border-radius: 6px;
}

.locale-select :deep(.el-input__wrapper:hover) {
  border-color: rgba(99, 102, 241, 0.4);
}

.locale-select :deep(.el-input.is-focus .el-input__wrapper) {
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.2) !important;
}

.locale-select :deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.85);
  font-weight: 600;
}

.locale-select :deep(.el-select__selected-item),
.locale-select :deep(.el-select__placeholder) {
  font-weight: 400;
  color: rgba(255, 255, 255, 0.55);
}

.locale-select :deep(.el-select__caret) {
  color: rgba(255, 255, 255, 0.35);
}

.login-brand {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: 8px;
  background: var(--eify-gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-subtitle {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.35);
  letter-spacing: 2px;
}

.login-tabs {
  --el-text-color-primary: rgba(255, 255, 255, 0.65);
  --el-text-color-secondary: rgba(255, 255, 255, 0.35);
}

.login-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.login-tabs :deep(.el-tabs__item) {
  color: rgba(255, 255, 255, 0.35);
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: var(--eify-primary);
}

.login-tabs :deep(.el-tabs__active-bar) {
  background: var(--eify-primary);
}

.login-tabs :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.55);
}

.login-tabs :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: none;
}

.login-tabs :deep(.el-input__wrapper:hover) {
  border-color: rgba(99, 102, 241, 0.4);
}

.login-tabs :deep(.el-input__wrapper.is-focus) {
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.2);
}

.login-tabs :deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.85);
}

.login-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  background: var(--eify-gradient-primary);
  border: none;
}

.login-btn:hover {
  background: var(--eify-gradient-primary);
}

.login-footer {
  text-align: center;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.login-version {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 10px;
}

.login-version-text {
  color: rgba(255, 255, 255, 0.25);
  font-family: monospace;
}

.login-version-badge {
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(99, 102, 241, 0.2);
  color: var(--eify-primary-300);
  letter-spacing: 1px;
}

.login-copyright {
  color: rgba(255, 255, 255, 0.2);
}
</style>

<style>
/* el-select 下拉面板（全局，因为 popper 渲染在 body） */
.locale-popper {
  background: rgba(20, 20, 40, 0.95) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  backdrop-filter: blur(12px);
}

.locale-popper .el-select-dropdown__item {
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  background: transparent !important;
}

.locale-popper .el-select-dropdown__item.is-hovered,
.locale-popper .el-select-dropdown__item:hover {
  color: rgba(255, 255, 255, 0.85) !important;
  background: rgba(99, 102, 241, 0.15) !important;
}

.locale-popper .el-select-dropdown__item.is-selected {
  color: var(--eify-primary) !important;
  font-weight: 600;
  background: transparent !important;
}
</style>
