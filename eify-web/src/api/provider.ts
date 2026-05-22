import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================
// 类型定义
// ============================================

/**
 * 供应商类型
 */
export type ProviderType = 'OPENAI' | 'ANTHROPIC' | 'OLLAMA' | 'OPENAI_COMPATIBLE'

/**
 * 供应商创建请求
 */
export interface ProviderCreateRequest {
  name: string
  type: ProviderType
  baseUrl: string
  authConfig: Record<string, any>
  enabled: number
}

/**
 * 供应商更新请求
 */
export interface ProviderUpdateRequest {
  name?: string
  type?: ProviderType
  baseUrl?: string
  authConfig?: Record<string, any>
  enabled?: number
}

/**
 * 模型配置信息
 */
export interface ModelConfigInfo {
  id: number
  modelName: string
  displayName: string
  /** 0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL */
  category: number
  /** 扩展参数（含 dimension、supports_streaming 等） */
  extraParams?: Record<string, any>
}

/**
 * 健康状态信息
 */
export interface ProviderHealthInfo {
  status: string
  lastCheckAt: string
  lastSuccessAt: string
  failCount: number
  latencyMs: number
  errorMessage: string
}

/**
 * 供应商响应对象
 */
export interface ProviderResponse {
  id: number
  name: string
  type: ProviderType
  baseUrl: string
  authConfig: Record<string, any> | null
  enabled: number
  modelConfigs: ModelConfigInfo[] | null
  health: ProviderHealthInfo | null
  createdAt: string
  updatedAt: string
}

/**
 * 分页查询参数
 */
export interface ProviderListParams {
  page?: number
  pageSize?: number
  name?: string
  type?: ProviderType
  enabled?: number
}

/**
 * 连通性测试结果
 */
export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number | null
  modelNames: string[] | null
  errorMessage: string | null
}

// ============================================
// API 方法
// ============================================

/**
 * Provider API
 */
export const providerApi = {
  /**
   * 分页查询供应商列表
   */
  getProviderList: (params?: ProviderListParams): Promise<PageResult<ProviderResponse>> => {
    return get<PageResult<ProviderResponse>>('/api/v1/providers', { params })
  },

  /**
   * 获取供应商详情
   */
  getProvider: (id: number): Promise<ProviderResponse> => {
    return get<ProviderResponse>(`/api/v1/providers/${id}`)
  },

  /**
   * 创建供应商
   */
  createProvider: (data: ProviderCreateRequest): Promise<ProviderResponse> => {
    return post<ProviderResponse>('/api/v1/providers', data)
  },

  /**
   * 更新供应商
   */
  updateProvider: (id: number, data: ProviderUpdateRequest): Promise<ProviderResponse> => {
    return put<ProviderResponse>(`/api/v1/providers/${id}`, data)
  },

  /**
   * 删除供应商
   */
  deleteProvider: (id: number): Promise<void> => {
    return del<void>(`/api/v1/providers/${id}`)
  },

  /**
   * 同步模型（测试连通性并自动同步可用模型列表）
   */
  testConnection: (id: number): Promise<ConnectionTestResult> => {
    return post<ConnectionTestResult>(`/api/v1/providers/${id}/test-connection`)
  },

  /**
   * 获取供应商下的模型列表，支持按 category 过滤
   */
  getProviderModels: (id: number, params?: { category?: number; enabled?: number }): Promise<ModelConfigInfo[]> => {
    return get<ModelConfigInfo[]>(`/api/v1/providers/${id}/models`, { params })
  },

  /**
   * 手动添加模型配置
   */
  createProviderModel: (id: number, data: Record<string, any>): Promise<ModelConfigInfo> => {
    return post<ModelConfigInfo>(`/api/v1/providers/${id}/models`, data)
  }
}
