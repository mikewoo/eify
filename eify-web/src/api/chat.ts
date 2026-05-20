import { get, post, del } from '@/utils/request'

/**
 * 发送消息请求
 */
export interface SendChatRequest {
  sessionId?: number | null
  agentId?: number
  workflowId?: number
  content: string
  contextRounds?: number
}

/**
 * SSE 事件类型
 */
export type SseEventType = 'message' | 'complete' | 'error' | 'timeout'

/**
 * SSE 事件数据
 */
export interface SseEvent {
  event: SseEventType
  data: SseEventData
}

/**
 * SSE 事件数据内容
 */
export interface SseEventData {
  content?: string
  done?: boolean
  usage?: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  finishReason?: string
  error?: string
  timeout?: boolean
  message?: string
}

/**
 * 会话
 */
export interface Conversation {
  id: number
  userId: number
  agentId: number | null
  workflowId: number | null
  title: string
  status: number
  createdAt: string
  updatedAt: string
}

/**
 * 发送消息（SSE 流式响应）
 */
export async function sendChat(
  _userId: number,
  data: SendChatRequest,
  onMessage: (event: SseEvent) => void,
  onComplete?: () => void,
  onError?: (error: Error) => void
): Promise<() => void> {
  const controller = new AbortController()

  try {
    const response = await fetch('/api/v1/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('accessToken'),
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(data),
      signal: controller.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()

    if (!reader) {
      throw new Error('Response body is null')
    }

    let buffer = ''
    let currentEvent = ''
    let dataBuffer: string[] = []

    function flushEvent() {
      if (dataBuffer.length === 0) return
      const dataStr = dataBuffer.join('').trim()
      dataBuffer = []
      if (!dataStr) return

      try {
        const data = JSON.parse(dataStr)
        const event: SseEvent = {
          event: data.event || currentEvent,
          data: data.data || data
        }
        onMessage(event)

        if (event.event === 'complete') {
          onComplete?.()
        }
      } catch (e) {
        if (e instanceof SyntaxError) {
          console.error('Failed to parse SSE data:', dataStr, e)
        } else {
          console.error('SSE callback error:', e)
        }
      }
    }

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        flushEvent()
        onComplete?.()
        break
      }

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          // Flush previous event before starting a new one
          flushEvent()
          currentEvent = line.slice(6).trim()
          continue
        }

        if (line.startsWith('data:')) {
          dataBuffer.push(line.slice(5))
          continue
        }

        // Empty line or comment — end of an event
        if (line === '' || line.startsWith(':')) {
          flushEvent()
          currentEvent = ''
        }
      }
    }
  } catch (error) {
    if (error instanceof Error && error.name !== 'AbortError') {
      onError?.(error)
    }
  }

  // 返回取消函数
  return () => controller.abort()
}

/**
 * 获取用户对话列表
 */
export function getUserConversations(params: {
  userId: number
  status?: number
  lastId?: number
  lastTimestamp?: string
  pageSize?: number
}) {
  const { userId, ...queryParams } = params
  return get<any>(`/api/v1/conversations/user/${userId}`, {
    params: queryParams
  })
}

export function createConversation(data: { agentId?: number; workflowId?: number; title?: string }) {
  return post<any>('/api/v1/chat/conversations', data)
}

export function deleteConversation(id: number) {
  return del<void>(`/api/v1/conversations/${id}`)
}

export function getConversationMessages(conversationId: number, params?: {
  lastId?: number
  lastTimestamp?: string
  pageSize?: number
}) {
  return get<any>(`/api/v1/chat/conversations/${conversationId}/messages`, {
    params
  })
}
