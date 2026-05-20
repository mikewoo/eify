import { post, get, del, put } from '@/utils/request'

export interface WorkspaceInfo {
  id: number
  name: string
  role: string
}

export interface MemberInfo {
  userId: number
  username: string
  displayName: string
  email: string
  role: string
  joinedAt: string
}

export const workspaceApi = {
  create(data: { name: string; description?: string }): Promise<WorkspaceInfo> {
    return post('/api/v1/workspaces', data)
  },

  generateInviteCode(workspaceId: number): Promise<string> {
    return post(`/api/v1/workspaces/${workspaceId}/invite-code`)
  },

  join(code: string): Promise<WorkspaceInfo> {
    return post('/api/v1/workspaces/join', { code })
  },

  listMembers(workspaceId: number): Promise<MemberInfo[]> {
    return get(`/api/v1/workspaces/${workspaceId}/members`)
  },

  removeMember(workspaceId: number, userId: number): Promise<void> {
    return del(`/api/v1/workspaces/${workspaceId}/members/${userId}`)
  },

  updateRole(workspaceId: number, userId: number, role: string): Promise<void> {
    return put(`/api/v1/workspaces/${workspaceId}/members/${userId}`, { role })
  },

  leave(workspaceId: number): Promise<void> {
    return del(`/api/v1/workspaces/${workspaceId}/leave`)
  }
}
