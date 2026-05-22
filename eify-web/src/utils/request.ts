import axios from 'axios'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { parseUserIdFromToken } from '@/utils/jwt'

const { t } = i18n.global

const instance = axios.create({
  baseURL: '/',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

instance.interceptors.request.use(
  (config: any) => {
    const token = localStorage.getItem('accessToken')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const userId = parseUserIdFromToken()
    if (userId && config.headers) {
      config.headers['X-User-Id'] = userId
    }
    const lang = localStorage.getItem('eify_lang') || 'zh-CN'
    if (config.headers) {
      config.headers['Accept-Language'] = lang
    }
    return config
  },
  (error: any) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response: any) => {
    const { code, message, data, success } = response.data

    if (code === 200 || success === true) {
      if (data === null || data === undefined) {
        return Promise.reject(new Error(message || t('common.noData')))
      }
      return data
    }

    ElMessage.error(message || response.data?.error || t('error.requestFailed'))
    return Promise.reject(new Error(message || t('error.requestFailed')))
  },
  async (error: any) => {
    const { response } = error

    if (response) {
      const { status } = response
      switch (status) {
        case 401: {
          if (!error.config._retry) {
            error.config._retry = true
            try {
              const resp = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                credentials: 'include'
              })
              if (resp.ok) {
                const data = await resp.json()
                if (data.data?.accessToken) {
                  localStorage.setItem('accessToken', data.data.accessToken)
                  error.config.headers.Authorization = `Bearer ${data.data.accessToken}`
                  return instance(error.config)
                }
              }
            } catch {
              // refresh failed, fall through to login redirect
            }
          }
          ElMessage.error(t('auth.sessionExpired'))
          localStorage.removeItem('accessToken')
          window.location.href = '/login'
          break
        }
        case 403:
          ElMessage.error(t('error.unauthorized'))
          break
        case 404:
          ElMessage.error(t('error.notFound'))
          break
        case 500:
          ElMessage.error(t('error.serverError'))
          break
        default:
          ElMessage.error(response.data?.message || t('error.requestFailed'))
      }
    } else {
      ElMessage.error(t('error.networkError'))
    }

    return Promise.reject(error)
  }
)

/**
 * GET 请求
 */
export function get<T = any>(url: string, config?: any): Promise<T> {
  return instance.get(url, config) as any
}

/**
 * POST 请求
 */
export function post<T = any>(url: string, data?: any, config?: any): Promise<T> {
  return instance.post(url, data, config) as any
}

/**
 * PUT 请求
 */
export function put<T = any>(url: string, data?: any, config?: any): Promise<T> {
  return instance.put(url, data, config) as any
}

/**
 * DELETE 请求
 */
export function del<T = any>(url: string, config?: any): Promise<T> {
  return instance.delete(url, config) as any
}
