import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================
// 类型定义
// ============================================

/** 工作流状态：0=草稿，1=已发布，2=已禁用 */
export type WorkflowStatus = 0 | 1 | 2

/** 节点类型 */
export type NodeType = 'start' | 'end' | 'llm' | 'api_call' | 'condition' | 'code' | 'tool_call'

/** 工作流列表项 */
export interface WorkflowResponse {
  id: number
  name: string
  description: string | null
  status: WorkflowStatus
  version: number
  nodeCount: number
  edgeCount: number
  createdAt: string
  updatedAt: string
}

/** 节点详情 */
export interface NodeDetail {
  id: number
  workflowId: number
  nodeKey: string
  type: NodeType
  name: string
  positionX: number
  positionY: number
  config: Record<string, any> | null
}

/** 连线详情 */
export interface EdgeDetail {
  id: number
  workflowId: number
  sourceNodeId: number
  targetNodeId: number
  condition: string | null
  label: string | null
}

/** 工作流详情（含节点和连线） */
export interface WorkflowDetailResponse extends WorkflowResponse {
  variables: any[] | null
  nodes: NodeDetail[]
  edges: EdgeDetail[]
}

/** 创建/更新节点项 */
export interface NodeItem {
  nodeKey: string
  type: NodeType
  name?: string
  positionX?: number
  positionY?: number
  config?: Record<string, any>
}

/** 创建/更新连线项 */
export interface EdgeItem {
  sourceNodeKey: string
  targetNodeKey: string
  condition?: string
  label?: string
}

/** 创建工作流请求 */
export interface WorkflowCreateRequest {
  name: string
  description?: string
  status?: WorkflowStatus
  variables?: any[]
  nodes?: NodeItem[]
  edges?: EdgeItem[]
}

/** 更新工作流请求 */
export interface WorkflowUpdateRequest {
  name?: string
  description?: string
  status?: WorkflowStatus
  variables?: any[]
  nodes?: NodeItem[]
  edges?: EdgeItem[]
}

/** 列表查询参数 */
export interface WorkflowListParams {
  page?: number
  pageSize?: number
}

// ============================================
// API 方法
// ============================================

export const workflowApi = {
  /** 分页查询工作流列表 */
  getList: (params?: WorkflowListParams): Promise<PageResult<WorkflowResponse>> =>
    get<PageResult<WorkflowResponse>>('/api/v1/workflows', { params }),

  /** 获取工作流详情（含节点和连线） */
  getById: (id: number): Promise<WorkflowDetailResponse> =>
    get<WorkflowDetailResponse>(`/api/v1/workflows/${id}`),

  /** 创建工作流 */
  create: (data: WorkflowCreateRequest): Promise<WorkflowDetailResponse> =>
    post<WorkflowDetailResponse>('/api/v1/workflows', data),

  /** 更新工作流 */
  update: (id: number, data: WorkflowUpdateRequest): Promise<WorkflowDetailResponse> =>
    put<WorkflowDetailResponse>(`/api/v1/workflows/${id}`, data),

  /** 删除工作流 */
  delete: (id: number): Promise<void> =>
    del<void>(`/api/v1/workflows/${id}`)
}
