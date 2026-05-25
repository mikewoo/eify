import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================
// 类型定义
// ============================================

/**
 * Agent 创建请求
 */
export interface AgentCreateRequest {
  name: string
  description?: string
  avatar?: string
  defaultProviderId: number
  defaultModel: string
  systemPrompt: string
  userMessagePrefix?: string
  welcomeMessage?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  frequencyPenalty?: number
  presencePenalty?: number
  maxHistoryRounds?: number
  streamEnabled?: number
  agentConfig?: Record<string, any>
  enabled?: number
  knowledgeIds?: number[]
  mcpToolIds?: number[]
  ragEnabled?: number
  ragTopK?: number
  ragStrategy?: string
}

/**
 * Agent 更新请求
 */
export interface AgentUpdateRequest {
  name?: string
  description?: string
  avatar?: string
  defaultProviderId?: number
  defaultModel?: string
  systemPrompt?: string
  userMessagePrefix?: string
  welcomeMessage?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  frequencyPenalty?: number
  presencePenalty?: number
  maxHistoryRounds?: number
  streamEnabled?: number
  agentConfig?: Record<string, any>
  enabled?: number
  knowledgeIds?: number[]
  mcpToolIds?: number[]
  ragEnabled?: number
  ragTopK?: number
  ragStrategy?: string
}

/**
 * Provider 信息（嵌套在 Agent 响应中）
 */
export interface ProviderInfo {
  id: number
  name: string
  type: string
  baseUrl: string
}

/**
 * MCP 工具简要信息（嵌套在 Agent 响应中）
 */
export interface McpToolBrief {
  id: number
  name: string
  serverName: string | null
}

/**
 * Agent 响应对象
 */
export interface AgentResponse {
  id: number
  name: string
  description: string | null
  avatar: string | null
  defaultProviderId: number
  defaultProviderName: string | null
  defaultProviderType: string | null
  defaultProviderAvailable?: boolean
  defaultModel: string
  systemPrompt: string
  userMessagePrefix: string | null
  welcomeMessage: string | null
  temperature: number
  maxTokens: number | null
  topP: number
  frequencyPenalty: number | null
  presencePenalty: number | null
  maxHistoryRounds: number | null
  streamEnabled: number | null
  agentConfig: Record<string, any> | null
  enabled: number
  knowledgeIds: number[] | null
  mcpToolIds: number[] | null
  mcpTools: McpToolBrief[] | null
  knowledgeBases?: { id: number; name: string }[] | null
  ragEnabled: number | null
  ragTopK: number | null
  ragStrategy: string | null
  createdAt: string
  updatedAt: string
  creatorId: number | null
  defaultProvider?: ProviderInfo | null
}

/**
 * 分页查询参数
 */
export interface AgentListParams {
  page?: number
  pageSize?: number
  name?: string
  enabled?: number
}

/**
 * 绑定 MCP 工具请求
 */
export interface BindToolsRequest {
  toolIds: number[]
}

/**
 * 测试对话请求
 */
export interface AgentTestChatRequest {
  message: string
  stream?: boolean
  overrideProviderId?: number
  overrideModel?: string
}

/**
 * Token 统计
 */
export interface TokenStats {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/**
 * 性能指标
 */
export interface PerformanceMetrics {
  latencyMs: number
  actualProviderId: number
  actualModel: string
}

/**
 * 测试对话响应
 */
export interface AgentTestChatResponse {
  reply: string | null
  tokens: TokenStats | null
  performance: PerformanceMetrics | null
  success: boolean
  errorMessage: string | null
}

// ============================================
// API 方法
// ============================================

/**
 * Agent API
 */
export const agentApi = {
  /**
   * 分页查询 Agent 列表
   */
  getAgentList: (params?: AgentListParams): Promise<PageResult<AgentResponse>> => {
    return get<PageResult<AgentResponse>>('/api/v1/agents', {
      params,
    })
  },

  /**
   * 获取 Agent 详情
   */
  getAgent: (id: number): Promise<AgentResponse> => {
    return get<AgentResponse>(`/api/v1/agents/${id}`, {
    })
  },

  /**
   * 创建 Agent
   */
  createAgent: (data: AgentCreateRequest): Promise<AgentResponse> => {
    return post<AgentResponse>('/api/v1/agents', data, {
    })
  },

  /**
   * 更新 Agent
   */
  updateAgent: (id: number, data: AgentUpdateRequest): Promise<AgentResponse> => {
    return put<AgentResponse>(`/api/v1/agents/${id}`, data, {
    })
  },

  /**
   * 删除 Agent
   */
  deleteAgent: (id: number): Promise<void> => {
    return del<void>(`/api/v1/agents/${id}`, {
    })
  },

  /**
   * 绑定 MCP 工具（全量替换）
   */
  bindTools: (id: number, data: BindToolsRequest): Promise<void> => {
    return put<void>(`/api/v1/agents/${id}/tools`, data, {
    })
  },

  /**
   * 测试 Agent 对话
   */
  testChat: (id: number, data: AgentTestChatRequest): Promise<AgentTestChatResponse> => {
    return post<AgentTestChatResponse>(`/api/v1/agents/${id}/test-chat`, data, {
    })
  }
}
