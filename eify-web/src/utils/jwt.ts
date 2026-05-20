/**
 * JWT 工具函数 — 解码和过期检查
 */
export interface JwtPayload {
  sub?: string
  userId?: number
  exp?: number
  iat?: number
  [key: string]: any
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload))
  } catch {
    return null
  }
}

export function getTokenExpiry(token: string): number | null {
  const payload = decodeJwt(token)
  if (!payload || !payload.exp) return null
  return payload.exp * 1000 // 转为毫秒
}

/**
 * 检查 token 是否已过期（或将在 bufferSeconds 秒内过期）
 * 默认 60 秒缓冲，提前刷新避免发出即将过期的请求
 */
export function isTokenExpired(token: string, bufferSeconds: number = 60): boolean {
  const exp = getTokenExpiry(token)
  if (!exp) return true // 无法解析则视为过期
  return Date.now() >= exp - bufferSeconds * 1000
}

export function parseUserIdFromToken(): string | null {
  const token = localStorage.getItem('accessToken')
  if (!token) return null
  const payload = decodeJwt(token)
  if (!payload) return null
  return payload.sub || String(payload.userId || '') || null
}
