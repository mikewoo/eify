import { get, post, put, del } from '@/utils/request'

// ============================================
// 类型定义
// ============================================

/**
 * 知识库响应
 */
export interface KnowledgeBaseResponse {
  id: number
  name: string
  description: string | null
  embeddingModel: string
  vectorDimension: number
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  chunkCount: number
  enabled: number
  creatorId: number | null
  createdAt: string
  updatedAt: string
}

/**
 * 知识库创建请求
 */
export interface KnowledgeCreateRequest {
  name: string
  description?: string
  embeddingModel?: string
  vectorDimension?: number
  chunkSize?: number
  chunkOverlap?: number
}

/**
 * 知识库更新请求
 */
export interface KnowledgeUpdateRequest {
  name?: string
  description?: string
  embeddingModel?: string
  vectorDimension?: number
  chunkSize?: number
  chunkOverlap?: number
}

/**
 * 文档响应
 */
export interface DocumentResponse {
  id: number
  knowledgeId: number
  fileName: string
  originalName: string
  fileType: string
  fileSize: number
  filePath: string
  charCount: number | null
  chunkCount: number | null
  processStatus: number
  errorMessage: string | null
  enabled: number
  creatorId: number | null
  createdAt: string
  updatedAt: string
}

/**
 * 文档分块响应
 */
export interface DocumentChunkResponse {
  id: number
  knowledgeId: number
  documentId: number
  chunkIndex: number
  content: string
  chunkHash: string
  enabled: number
  createdAt: string
}

export const ProcessStatusLabel: Record<number, string> = {
  0: 'document.pending',
  1: 'document.processing',
  2: 'document.completed',
  3: 'document.failed'
}

export const ProcessStatusType: Record<number, string> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger'
}

// ============================================
// API 方法
// ============================================

export const knowledgeApi = {
  /**
   * 获取所有知识库
   */
  getKnowledgeList: async (): Promise<KnowledgeBaseResponse[]> => {
    const result = await get<{ list: KnowledgeBaseResponse[] }>('/api/v1/knowledge')
    return result.list || []
  },

  /**
   * 获取知识库详情
   */
  getKnowledge: (id: number): Promise<KnowledgeBaseResponse> => {
    return get<KnowledgeBaseResponse>(`/api/v1/knowledge/${id}`)
  },

  /**
   * 创建知识库
   */
  createKnowledge: (data: KnowledgeCreateRequest): Promise<KnowledgeBaseResponse> => {
    return post<KnowledgeBaseResponse>('/api/v1/knowledge', data)
  },

  /**
   * 更新知识库
   */
  updateKnowledge: (id: number, data: KnowledgeUpdateRequest): Promise<KnowledgeBaseResponse> => {
    return put<KnowledgeBaseResponse>(`/api/v1/knowledge/${id}`, data)
  },

  /**
   * 删除知识库
   */
  deleteKnowledge: (id: number): Promise<void> => {
    return del<void>(`/api/v1/knowledge/${id}`)
  },

  /**
   * 切换知识库启用状态
   */
  toggleStatus: (id: number, status: number): Promise<void> => {
    return put<void>(`/api/v1/knowledge/${id}/status?status=${status}`)
  },

  /**
   * 按知识库查询文档列表
   */
  getDocuments: (knowledgeId: number): Promise<DocumentResponse[]> => {
    return get<DocumentResponse[]>(`/api/v1/documents/knowledge/${knowledgeId}`)
  },

  /**
   * 上传文档
   */
  uploadDocument: (knowledgeId: number, file: File): Promise<DocumentResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    return post<DocumentResponse>(`/api/v1/documents/${knowledgeId}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000
    })
  },

  /**
   * 获取文档详情
   */
  getDocument: (documentId: number): Promise<DocumentResponse> => {
    return get<DocumentResponse>(`/api/v1/documents/${documentId}`)
  },

  /**
   * 重新处理文档
   */
  reprocessDocument: (documentId: number): Promise<void> => {
    return post<void>(`/api/v1/documents/${documentId}/reprocess`, undefined, { timeout: 300000 })
  },

  /**
   * 删除文档
   */
  deleteDocument: (documentId: number): Promise<void> => {
    return del<void>(`/api/v1/documents/${documentId}`)
  },

  /**
   * 获取支持的文件类型
   */
  getSupportedFileTypes: (): Promise<string[]> => {
    return get<string[]>('/api/v1/documents/supported-types')
  },

  /**
   * 获取文档原始文本内容
   */
  getDocumentContent: (documentId: number): Promise<string> => {
    return get<string>(`/api/v1/documents/${documentId}/content`)
  },

  /**
   * 获取文档分块列表
   */
  getDocumentChunks: (documentId: number): Promise<DocumentChunkResponse[]> => {
    return get<DocumentChunkResponse[]>(`/api/v1/documents/${documentId}/chunks`)
  }
}
