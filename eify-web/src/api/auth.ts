import { post, get } from '@/utils/request'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: {
    id: number
    username: string
    email: string
    displayName: string
    avatarUrl: string | null
  }
  workspace: {
    id: number
    name: string
    role: string
  }
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  displayName?: string
}

export const authApi = {
  login(data: LoginRequest): Promise<AuthResponse> {
    return post('/api/v1/auth/login', data)
  },
  register(data: RegisterRequest): Promise<AuthResponse> {
    return post('/api/v1/auth/register', data)
  },
  refresh(refreshToken: string): Promise<AuthResponse> {
    return post('/api/v1/auth/refresh', { refreshToken })
  },
  logout(): Promise<any> {
    return post('/api/v1/auth/logout')
  },
  listWorkspaces(): Promise<AuthResponse['workspace'][]> {
    return get('/api/v1/auth/workspaces')
  },
  switchWorkspace(workspaceId: number): Promise<AuthResponse> {
    return post('/api/v1/auth/switch-workspace', { workspaceId })
  },
  me(): Promise<AuthResponse> {
    return get('/api/v1/auth/me')
  }
}
