import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================
// 类型定义
// ============================================

export interface McpServerCreateRequest {
  name: string
  description?: string
  endpoint: string
  enabled: number
}

export interface McpServerUpdateRequest {
  name?: string
  description?: string
  endpoint?: string
  enabled?: number
}

export interface McpToolResponse {
  id: number
  name: string
  description: string
  inputSchema: Record<string, any> | null
}

export interface McpServerResponse {
  id: number
  name: string
  description: string | null
  endpoint: string
  enabled: number
  toolCount: number | null
  tools: McpToolResponse[] | null
  createdAt: string
  updatedAt: string
}

/** 批量获取 Server + 工具完整信息（含 online 状态和 inputSchema） */
export interface McpServerToolsResponse {
  id: number
  name: string
  description: string | null
  endpoint: string
  enabled: number
  online: boolean
  toolCount: number
  tools: McpToolOption[]
  createdAt: string
  updatedAt: string
}

/** 工具选项（含所属 Server 信息和 inputSchema） */
export interface McpToolOption {
  id: number
  name: string
  description: string
  serverName: string
  serverId: number
  inputSchema: Record<string, any> | null
}

export interface McpServerListParams {
  page?: number
  pageSize?: number
  name?: string
  endpoint?: string
  enabled?: number
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  toolCount: number
  toolNames: string[]
  errorMessage: string | null
}

export interface DebugToolRequest {
  toolName: string
  arguments: Record<string, any>
}

export interface DebugToolResponse {
  result: string
  elapsedMs: number
}

// ============================================
// API 方法
// ============================================

export const mcpApi = {
  /**
   * 分页查询 MCP Server 列表
   */
  getList: (params?: McpServerListParams): Promise<PageResult<McpServerResponse>> => {
    return get<PageResult<McpServerResponse>>('/api/v1/mcp-servers', { params })
  },

  /**
   * 获取 MCP Server 详情（含工具列表）
   */
  getById: (id: number): Promise<McpServerResponse> => {
    return get<McpServerResponse>(`/api/v1/mcp-servers/${id}`)
  },

  /**
   * 批量获取所有 Server 及其工具（含 online 状态、endpoint、inputSchema）
   */
  getToolsByServer: (params?: { enabled?: number }): Promise<McpServerToolsResponse[]> => {
    return get<McpServerToolsResponse[]>('/api/v1/mcp-servers/tools', { params })
  },

  /**
   * 创建 MCP Server
   */
  create: (data: McpServerCreateRequest): Promise<McpServerResponse> => {
    return post<McpServerResponse>('/api/v1/mcp-servers', data)
  },

  /**
   * 更新 MCP Server
   */
  update: (id: number, data: McpServerUpdateRequest): Promise<McpServerResponse> => {
    return put<McpServerResponse>(`/api/v1/mcp-servers/${id}`, data)
  },

  /**
   * 删除 MCP Server
   */
  delete: (id: number): Promise<void> => {
    return del<void>(`/api/v1/mcp-servers/${id}`)
  },

  /**
   * 测试连通性（listTools + 保存工具列表）
   */
  testConnection: (id: number): Promise<ConnectionTestResult> => {
    return post<ConnectionTestResult>(`/api/v1/mcp-servers/${id}/test`)
  },

  /**
   * 调试工具（调用指定工具并返回结果与耗时）
   */
  debugTool: (id: number, data: DebugToolRequest): Promise<DebugToolResponse> => {
    return post<DebugToolResponse>(`/api/v1/mcp-servers/${id}/debug`, data)
  }
}
