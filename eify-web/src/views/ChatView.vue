<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { sendChat, getUserConversations, createConversation, deleteConversation, getConversationMessages, type SendChatRequest, type SseEvent, type Conversation } from '@/api/chat'
import { agentApi, type AgentResponse } from '@/api/agent'
import { workflowApi, type WorkflowResponse } from '@/api/workflow'
import { useAuthStore } from '@/store/auth'
import { useLocaleStore } from '@/store/locale'
import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { ElMessage } from 'element-plus'

// 配置 marked 渲染器
const markedInstance = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      try {
        const language = hljs.getLanguage(lang) ? lang : 'plaintext'
        return hljs.highlight(code, { language }).value
      } catch {
        return code
      }
    }
  })
)
markedInstance.setOptions({ gfm: true })

const { t } = useI18n()
const localeStore = useLocaleStore()
const authStore = useAuthStore()
const userId = computed(() => authStore.user?.id?.toString() || '1')

// ========== 状态 ==========
const conversations = ref<Conversation[]>([])
const currentConversationId = ref<number | null>(null)
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string; isStreaming?: boolean; isError?: boolean; isTyping?: boolean; timestamp?: string }>>([])
const inputContent = ref('')
const isSending = ref(false)
const currentAgentId = ref<number | null>(null) // 当前选中的 Agent ID
const currentAgent = ref<AgentResponse | null>(null) // 当前选中的 Agent
const isLoadingMessages = ref(false) // 加载消息历史状态


// 打字机效果状态
const typewriterQueue = ref<Array<{ index: number, content: string }>>([])
const isTypewriterRunning = ref(false)
const typewriterProcessed = new Set<number>() // 防止重复处理

// 确认对话框状态
const showDeleteConfirm = ref(false)
const conversationToDelete = ref<number | null>(null)

// 错误提示状态
const showErrorToast = ref(false)
const errorMessage = ref('')

// Agent/工作流 选择相关状态
const showAgentSelector = ref(false)
const selectorMode = ref<'agent' | 'workflow'>('agent')
const availableAgents = ref<AgentResponse[]>([])
const isLoadingAgents = ref(false)
const availableWorkflows = ref<WorkflowResponse[]>([])
const isLoadingWorkflows = ref(false)
const newConversationTitle = ref('') // 新对话名称
const pendingMessage = ref('') // 待发送的消息内容

// ========== Ref ==========
const messagesContainer = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()

// ========== 计算属性 ==========
const currentConversation = computed(() => {
  return conversations.value.find(c => c.id === currentConversationId.value)
})

// ========== 初始化 ==========
onMounted(async () => {
  await loadConversations()
  // 自动选择最近一次对话
  if (conversations.value.length > 0 && !currentConversationId.value) {
    const sortedConvos = [...conversations.value].sort((a, b) => {
      const timeA = new Date(a.updatedAt).getTime()
      const timeB = new Date(b.updatedAt).getTime()
      return timeB - timeA
    })
    await selectConversation(sortedConvos[0].id)
  } else {
    // 没有对话时才滚动到底部
    scrollToBottom()
  }
})

// ========== 会话管理 ==========
async function loadConversations() {
  try {
    const result = await getUserConversations({
      userId: Number(userId.value),
      status: 1, // 进行中的对话
      pageSize: 50
    })
    conversations.value = result?.list || []
  } catch (error) {
    console.error('加载对话列表失败:', error)
    conversations.value = []
  }
}

async function createNewConversation() {
  // 打开选择器
  showAgentSelector.value = true
  selectorMode.value = 'agent'
  await Promise.all([loadAvailableAgents(), loadAvailableWorkflows()])
}

async function loadAvailableAgents() {
  isLoadingAgents.value = true
  try {
    const result = await agentApi.getAgentList({ page: 1, pageSize: 50, enabled: 1 })
    availableAgents.value = (result as any).list || result.records || []
  } catch (error) {
    console.error('加载 Agent 列表失败:', error)
    availableAgents.value = []
  } finally {
    isLoadingAgents.value = false
  }
}

async function selectAgent(agent: AgentResponse) {
  try {
    currentAgent.value = agent
    currentAgentId.value = agent.id

    // 创建新对话（带自定义名称）
    const title = newConversationTitle.value.trim() || t('chat.conversationWithAgent', { name: agent.name })
    const result = await createConversation({
      agentId: agent.id,
      title: title
    })
    const newConv = result as Conversation
    conversations.value.unshift(newConv)
    currentConversationId.value = newConv.id
    messages.value = [] // 新建对话时清空消息

    // 清空输入框并关闭选择器
    newConversationTitle.value = ''
    showAgentSelector.value = false

    // 自动发送待发消息
    if (pendingMessage.value) {
      const msg = pendingMessage.value
      pendingMessage.value = ''
      inputContent.value = msg
      await sendMessage()
    }
  } catch (error) {
    console.error('创建对话失败:', error)
    currentConversationId.value = null
    messages.value = []
  }
}

function closeAgentSelector() {
  showAgentSelector.value = false
  newConversationTitle.value = '' // 清空输入
  pendingMessage.value = ''
}

async function loadAvailableWorkflows() {
  isLoadingWorkflows.value = true
  try {
    const result = await workflowApi.getList({ page: 1, pageSize: 50 })
    // 只显示已发布的工作流
    availableWorkflows.value = ((result as any).list || result.records || []).filter((w: any) => w.status === 1)
  } catch (error) {
    console.error('加载工作流列表失败:', error)
    availableWorkflows.value = []
  } finally {
    isLoadingWorkflows.value = false
  }
}

async function selectWorkflow(workflow: WorkflowResponse) {
  try {
    const title = newConversationTitle.value.trim() || workflow.name
    const result = await createConversation({
      workflowId: workflow.id,
      title: title
    })
    const newConv = result as Conversation
    conversations.value.unshift(newConv)
    currentConversationId.value = newConv.id
    messages.value = []
    currentAgent.value = null
    currentAgentId.value = null

    newConversationTitle.value = ''
    showAgentSelector.value = false

    if (pendingMessage.value) {
      const msg = pendingMessage.value
      pendingMessage.value = ''
      inputContent.value = msg
      await sendMessage()
    }
  } catch (error) {
    console.error('创建工作流对话失败:', error)
    currentConversationId.value = null
    messages.value = []
  }
}

async function selectConversation(id: number) {
  if (currentConversationId.value === id) return // 避免重复加载

  currentConversationId.value = id
  const conversation = conversations.value.find(c => c.id === id)

  // 加载 Agent/工作流 信息
  if (conversation?.agentId) {
    try {
      const agent = await agentApi.getAgent(conversation.agentId)
      currentAgent.value = agent
      currentAgentId.value = agent.id
    } catch (error) {
      console.error('加载 Agent 信息失败:', error)
      currentAgent.value = null
      currentAgentId.value = null
    }
  } else {
    currentAgent.value = null
    currentAgentId.value = null
  }

  await loadConversationMessages(id)
}

async function loadConversationMessages(conversationId: number) {
  if (!conversationId) {
    messages.value = []
    return
  }

  isLoadingMessages.value = true
  messages.value = []

  try {
    const result = await getConversationMessages(conversationId, { pageSize: 100 })

    const records = result ? ((result as any).list || result.records) : null
    if (records) {
      const messagesList = records.map((msg: any) => ({
        role: msg.role,
        content: msg.content,
        timestamp: msg.createTime || msg.createdAt || msg.timestamp || new Date().toISOString()
      }))

      // 按时间顺序排序（旧的在前，新的在后）
      messagesList.sort((a: any, b: any) => {
        const timeA = new Date(a.timestamp).getTime()
        const timeB = new Date(b.timestamp).getTime()
        return timeA - timeB
      })

      messages.value = messagesList
    }

    // 自动滚动到底部
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('加载消息历史失败:', error)
    // 即使加载失败，也清空消息区域，让用户可以开始新对话
    messages.value = []
  } finally {
    isLoadingMessages.value = false
  }
}

async function deleteConversationItem(id: number, event: Event) {
  event.stopPropagation() // 阻止触发选择对话

  // 显示确认对话框
  conversationToDelete.value = id
  showDeleteConfirm.value = true
}

async function confirmDeleteConversation() {
  const id = conversationToDelete.value
  if (id === null) return

  try {
    await deleteConversation(id)

    // 从列表中移除
    conversations.value = conversations.value.filter(c => c.id !== id)

    // 如果删除的是当前对话，清空消息区域
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      messages.value = []
    }

    // 关闭对话框
    showDeleteConfirm.value = false
    conversationToDelete.value = null
  } catch (error: any) {
    console.error('删除对话失败:', error)

    // 根据错误类型显示不同的提示
    let errorMsg = t('chat.deleteFailedRetry')

    if (error.response?.data?.message) {
      errorMsg = error.response.data.message
    } else if (error.message) {
      errorMsg = error.message
    }

    // 显示错误提示
    errorMessage.value = errorMsg
    showErrorToast.value = true

    // 3秒后自动隐藏
    setTimeout(() => {
      showErrorToast.value = false
    }, 3000)
  }
}

function cancelDeleteConversation() {
  showDeleteConfirm.value = false
  conversationToDelete.value = null
}

// ========== 消息发送 ==========
async function sendMessage() {
  const content = inputContent.value.trim()
  if (!content || isSending.value) return

  // 如果没有对话/执行对象，先让用户选择
  if (!currentConversationId.value && !currentAgentId.value) {
    pendingMessage.value = content
    showAgentSelector.value = true
    await loadAvailableAgents()
    await loadAvailableWorkflows()
    return
  }

  // 1. 立即清空输入框
  inputContent.value = ''

  // 2. 添加用户消息
  messages.value.push({
    role: 'user',
    content,
    timestamp: new Date().toISOString()
  })

  // 3. 自动滚动到底部
  await nextTick()
  scrollToBottom()

  // 4. 添加空的 AI 气泡（带加载状态）
  const aiMessageIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    isStreaming: true,
    timestamp: new Date().toISOString()
  })

  await nextTick()
  scrollToBottom()

  // 5. 开始发送
  isSending.value = true

  const request: SendChatRequest = {
    sessionId: currentConversationId.value,
    agentId: currentAgentId.value ?? undefined,
    content
  }

  // 用于收集完整响应内容
  let fullResponse = ''

  try {
    await sendChat(
      Number(userId.value),
      request,
      // onMessage
      (event: SseEvent) => {
        if (event.event === 'message') {
          // 收集内容（不立即显示）
          const content = event.data.content || ''
          fullResponse += content
        } else if (event.event === 'complete') {
          // 完成时启动打字机效果（防止同一消息多次入队）
          if (typewriterProcessed.has(aiMessageIndex)) return
          typewriterProcessed.add(aiMessageIndex)
          if (!messages.value[aiMessageIndex]) return
          messages.value[aiMessageIndex].isStreaming = false

          typewriterQueue.value.push({
            index: aiMessageIndex,
            content: fullResponse
          })

          processTypewriterQueue()
        } else if (event.event === 'error') {
          // 错误 — 只在消息尚未完成时覆盖（避免 later error 覆盖已显示的回复）
          if (!messages.value[aiMessageIndex]) return
          if (messages.value[aiMessageIndex].isStreaming) {
            messages.value[aiMessageIndex].content = event.data.error || t('chat.requestFailedRetry')
            messages.value[aiMessageIndex].isStreaming = false
            messages.value[aiMessageIndex].isError = true
          }
        } else if (event.event === 'timeout') {
          // 超时
          if (!messages.value[aiMessageIndex]) return
          if (messages.value[aiMessageIndex].isStreaming) {
            messages.value[aiMessageIndex].content = event.data.message || t('chat.requestTimeoutRetry')
            messages.value[aiMessageIndex].isStreaming = false
            messages.value[aiMessageIndex].isError = true
          }
        }
      },
      // onComplete
      () => {
        isSending.value = false
      },
      // onError
      (error: Error) => {
        console.error('发送消息失败:', error)
        if (!messages.value[aiMessageIndex]) return
        if (messages.value[aiMessageIndex].isStreaming) {
          messages.value[aiMessageIndex].content = error.message || t('chat.sendFailedRetry')
          messages.value[aiMessageIndex].isStreaming = false
          messages.value[aiMessageIndex].isError = true
        }
        isSending.value = false
      }
    )
  } catch (error) {
    console.error('发送消息失败:', error)
    if (!messages.value[aiMessageIndex]) return
    if (messages.value[aiMessageIndex].isStreaming) {
      messages.value[aiMessageIndex].content = error instanceof Error ? error.message : t('chat.sendFailedRetry')
      messages.value[aiMessageIndex].isStreaming = false
      messages.value[aiMessageIndex].isError = true
    }
    isSending.value = false
  }
}

// ========== 工具函数 ==========

/**
 * 打字机效果 - 逐字符显示文本
 */
async function typeWriterEffect(messageIndex: number, fullContent: string) {
  const message = messages.value[messageIndex]
  if (!message) return

  message.isTyping = true

  let currentText = ''
  const chars = fullContent.split('')

  const baseDelay = 25

  for (let i = 0; i < chars.length; i++) {
    currentText += chars[i]
    message.content = currentText

    let delay = baseDelay
    if (chars[i] === '，' || chars[i] === '。' || chars[i] === '！' || chars[i] === '？') {
      delay = baseDelay * 3
    } else if (chars[i] === '\n') {
      delay = baseDelay * 5
    } else if (chars[i] === ' ') {
      delay = baseDelay * 0.5
    }

    await new Promise(resolve => setTimeout(resolve, delay))
    nextTick(() => scrollToBottom())
  }

  message.isTyping = false
}

/**
 * 处理打字机队列
 */
async function processTypewriterQueue() {
  if (isTypewriterRunning.value || typewriterQueue.value.length === 0) {
    return
  }

  isTypewriterRunning.value = true

  while (typewriterQueue.value.length > 0) {
    const item = typewriterQueue.value.shift()
    if (item) {
      await typeWriterEffect(item.index, item.content)
    }
  }

  isTypewriterRunning.value = false
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function renderMarkdown(content: string) {
  if (!content) return ''
  try {
    return DOMPurify.sanitize(markedInstance.parse(content) as string)
  } catch {
    return content.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

function formatMessageTime(timestamp: string): string {
  try {
    // 兼容后端返回的时间格式："2026-04-29 20:35:39"
    let normalizedTimestamp = timestamp
    if (timestamp && timestamp.includes(' ')) {
      normalizedTimestamp = timestamp.replace(' ', 'T')
    }

    const date = new Date(normalizedTimestamp)

    // 检查日期是否有效
    if (isNaN(date.getTime())) {
  
      return ''
    }

    // 显示年月日+时间格式：2026-05-20 15:30
    const localeTag = localeStore.current === 'en-US' ? 'en-US' : 'zh-CN'
    const dateOnly = date.toLocaleDateString(localeTag, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
    const timeOnly = date.toLocaleTimeString(localeTag, {
      hour: '2-digit',
      minute: '2-digit'
    })

    return `${dateOnly} ${timeOnly}`
  } catch (error) {

    return ''
  }
}

function handleKeyDown(event: KeyboardEvent) {
  // Enter 发送，Shift+Enter 换行
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

async function copyMessage(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success(t('chat.messageCopied'))
  } catch {
    ElMessage.error(t('common.operationFailed'))
  }
}

function resendMessage(content: string) {
  inputContent.value = content
  nextTick(() => {
    adjustTextareaHeight()
    sendMessage()
  })
}

function adjustTextareaHeight() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 200) + 'px'
  }
}

// ========== 监听工作空间切换 ==========
watch(() => authStore.refreshKey, () => {

  messages.value = []
  currentConversationId.value = null
  loadConversations().then(() => {
    if (conversations.value.length > 0) {
      const sorted = [...conversations.value].sort((a, b) =>
        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      )
      selectConversation(sorted[0].id)
    }
  })
})

// ========== 监听输入框 ==========
watch(inputContent, () => {
  nextTick(() => adjustTextareaHeight())
})
</script>

<template>
  <div class="chat-container">
    <!-- 左侧：会话列表 -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <h2 class="sidebar-title text-xl">{{ t('sidebar.chat') }}</h2>
        <button class="new-chat-btn-header text-sm" @click="createNewConversation" :disabled="isSending">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          <span>{{ t('chat.newChat') }}</span>
        </button>
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conversation-item', { active: conv.id === currentConversationId }]"
          @click="selectConversation(conv.id)"
        >
          <div class="conversation-content">
            <div class="conversation-title">{{ conv.title }}</div>
            <div class="conversation-time">{{ new Date(conv.updatedAt).toLocaleDateString() }}</div>
          </div>
          <button
            class="delete-conversation-btn"
            @click="deleteConversationItem(conv.id, $event)"
            :title="t('chat.deleteChat')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <div v-if="conversations.length === 0 && !isLoadingMessages" class="empty-state">
          <p>{{ t('chat.noConversations') }}</p>
          <p class="hint">{{ t('chat.noConversationsHint') }}</p>
        </div>

        <!-- 加载状态 -->
        <div v-if="isLoadingMessages" class="loading-state">
          <div class="loading-spinner"></div>
          <p>{{ t('common.loading') }}</p>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <div class="chat-title">
          <div v-if="currentAgent" class="agent-info">
            <img v-if="currentAgent.avatar" :src="currentAgent.avatar" class="agent-avatar" alt="">
            <div v-else class="agent-avatar-placeholder">{{ currentAgent.name.charAt(0) }}</div>
            <div>
              <div class="agent-name">{{ currentAgent.name }}</div>
              <div class="conversation-title">{{ currentConversation?.title || t('chat.newChat') }}</div>
            </div>
          </div>
          <div v-else-if="currentConversation?.workflowId" class="agent-info">
            <div class="agent-avatar-placeholder workflow-placeholder">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 9l5-5 5 5M12 4v12"/>
              </svg>
            </div>
            <div>
              <div class="agent-name">{{ currentConversation.title }}</div>
              <div class="conversation-title">{{ t('chat.workflowMode') }}</div>
            </div>
          </div>
          <div v-else>
            {{ currentConversation?.title || t('chat.newChat') }}
          </div>
        </div>
      </header>

      <!-- 消息区域 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="messages-empty">
          <div class="empty-icon">💬</div>
          <p>{{ t('chat.startChat') }}</p>
          <p class="hint">{{ t('chat.startChatHint') }}</p>
        </div>

        <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
          <div class="message-avatar">
            <div v-if="msg.role === 'user'" class="avatar-user">U</div>
            <div v-else class="avatar-ai">AI</div>
          </div>

          <div class="message-content">
            <!-- 用户消息：纯文本 -->
            <div v-if="msg.role === 'user'" class="message-bubble user-bubble">
              {{ msg.content }}
              <!-- 操作按钮 + 时间（同一行） -->
              <div v-if="msg.timestamp && !msg.isStreaming" class="message-meta user-meta">
                <div class="message-actions user-actions">
                  <button
                    class="action-btn"
                    :title="t('chat.copyMessage')"
                    @click.stop="copyMessage(msg.content)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                  </button>
                  <button
                    class="action-btn"
                    :title="t('chat.resend')"
                    :disabled="isSending"
                    @click.stop="resendMessage(msg.content)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="23 4 23 10 17 10"/>
                      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                    </svg>
                  </button>
                </div>
                <div class="message-time">{{ formatMessageTime(msg.timestamp) }}</div>
              </div>
            </div>

            <!-- AI 消息：Markdown 渲染 + 加载动画 -->
            <div v-else :class="['message-bubble', 'ai-bubble', { 'error-bubble': msg.isError }]">
              <!-- 加载动画 -->
              <div v-if="msg.isStreaming && !msg.isError && !msg.content" class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <!-- 错误信息 -->
              <div v-else-if="msg.isError" class="error-content">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="error-icon">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M12 8v4M12 16h.01"/>
                </svg>
                <span>{{ msg.content }}</span>
              </div>
              <!-- 打字机效果：纯文本 + 光标 -->
              <div v-else-if="msg.isTyping" class="typewriter-content">{{ msg.content }}<span class="cursor">|</span></div>
              <!-- 完成：Markdown 渲染 -->
              <div v-else-if="!msg.isStreaming && msg.content && !msg.isTyping" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
              <!-- 复制按钮 + 时间（同一行） -->
              <div v-if="msg.timestamp && !msg.isStreaming && !msg.isTyping" class="message-meta ai-meta">
                <div v-if="!msg.isError && msg.content" class="message-actions ai-actions">
                  <button
                    class="action-btn ai-action-btn"
                    :title="t('chat.copyMessage')"
                    @click.stop="copyMessage(msg.content)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                  </button>
                </div>
                <div class="message-time">{{ formatMessageTime(msg.timestamp) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-container">
        <div class="input-wrapper">
          <textarea
            ref="inputRef"
            v-model="inputContent"
            class="message-input"
            :placeholder="t('chat.inputPlaceholder')"
            rows="1"
            :disabled="isSending"
            @keydown="handleKeyDown"
          ></textarea>
          <button
            class="send-button"
            @click="sendMessage"
            :disabled="!inputContent.trim() || isSending"
            :class="{ sending: isSending }"
          >
            <svg v-if="!isSending" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
            </svg>
            <div v-else class="spinner"></div>
          </button>
        </div>
        <div class="input-hint">
          {{ t('chat.aiDisclaimer') }}
        </div>
      </div>
    </main>

    <!-- Agent 选择器弹窗 -->
    <div v-if="showAgentSelector" class="agent-selector-overlay" @click="closeAgentSelector">
      <div class="agent-selector-modal" @click.stop>
        <div class="agent-selector-header">
          <div class="selector-tabs">
            <button
              :class="['selector-tab', { active: selectorMode === 'agent' }]"
              @click="selectorMode = 'agent'"
            >Agent</button>
            <button
              :class="['selector-tab', { active: selectorMode === 'workflow' }]"
              @click="selectorMode = 'workflow'"
            >{{ t('chat.workflow') }}</button>
          </div>
          <button class="close-btn" @click="closeAgentSelector">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <div class="agent-selector-body">
          <!-- 对话名称输入 -->
          <div class="conversation-name-input">
            <label>{{ t('chat.conversationName') }}</label>
            <input
              v-model="newConversationTitle"
              type="text"
              class="name-input"
              :placeholder="selectorMode === 'agent' ? t('chat.conversationNamePlaceholderAgent') : t('chat.conversationNamePlaceholderWorkflow')"
              maxlength="50"
            />
            <span class="input-counter">{{ newConversationTitle.length }}/50</span>
          </div>

          <!-- Agent 模式 -->
          <template v-if="selectorMode === 'agent'">
            <div v-if="isLoadingAgents" class="agent-loading">
              <div class="loading-spinner"></div>
              <p>{{ t('common.loading') }}</p>
            </div>

            <div v-else-if="availableAgents.length === 0" class="agent-empty">
              <div class="empty-icon">🤖</div>
              <p>{{ t('chat.noAgentsAvailable') }}</p>
              <p class="hint">{{ t('chat.noAgentsHint') }}</p>
            </div>

            <div v-else class="agent-list">
              <div
                v-for="agent in availableAgents"
                :key="agent.id"
                class="agent-item"
                @click="selectAgent(agent)"
              >
                <div class="agent-item-avatar">
                  <img v-if="agent.avatar" :src="agent.avatar" alt="">
                  <div v-else class="avatar-placeholder">{{ agent.name.charAt(0) }}</div>
                </div>
                <div class="agent-item-info">
                  <div class="agent-item-name">{{ agent.name }}</div>
                  <div class="agent-item-description">{{ agent.description || t('chat.noDescription') }}</div>
                  <div class="agent-item-model">
                    <span class="model-tag">{{ agent.defaultModel }}</span>
                    <span class="provider-tag">{{ agent.defaultProviderName || t('chat.unknownProvider') }}</span>
                  </div>
                </div>
                <svg class="agent-item-arrow" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6"/>
                </svg>
              </div>
            </div>
          </template>

          <!-- 工作流模式 -->
          <template v-if="selectorMode === 'workflow'">
            <div v-if="isLoadingWorkflows" class="agent-loading">
              <div class="loading-spinner"></div>
              <p>{{ t('common.loading') }}</p>
            </div>

            <div v-else-if="availableWorkflows.length === 0" class="agent-empty">
              <div class="empty-icon">🔗</div>
              <p>{{ t('chat.noWorkflowsAvailable') }}</p>
              <p class="hint">{{ t('chat.noWorkflowsHint') }}</p>
            </div>

            <div v-else class="agent-list">
              <div
                v-for="workflow in availableWorkflows"
                :key="workflow.id"
                class="agent-item"
                @click="selectWorkflow(workflow)"
              >
                <div class="agent-item-avatar">
                  <div class="avatar-placeholder workflow-avatar-icon">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 9l5-5 5 5M12 4v12"/>
                    </svg>
                  </div>
                </div>
                <div class="agent-item-info">
                  <div class="agent-item-name">{{ workflow.name }}</div>
                  <div class="agent-item-description">{{ workflow.description || t('chat.noDescription') }}</div>
                  <div class="agent-item-model">
                    <span class="model-tag">{{ t('chat.nodesCount', { count: workflow.nodeCount }) }}</span>
                    <span class="provider-tag">v{{ workflow.version }}</span>
                  </div>
                </div>
                <svg class="agent-item-arrow" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6"/>
                </svg>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <ConfirmDialog
      :show="showDeleteConfirm"
      :title="t('chat.deleteConfirmTitle')"
      :message="t('chat.deleteConfirmMessage')"
      :confirm-text="t('chat.deleteConfirmText')"
      :cancel-text="t('common.cancel')"
      type="danger"
      @confirm="confirmDeleteConversation"
      @cancel="cancelDeleteConversation"
    />

    <!-- 错误提示 Toast -->
    <Transition name="toast">
      <div v-if="showErrorToast" class="error-toast">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 8v4M12 16h.01"/>
        </svg>
        <span>{{ errorMessage }}</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ========== 容器布局 ========== */
.chat-container {
  display: flex;
  height: calc(100vh - 56px);
  background: var(--eify-bg-subtle);
}

/* ========== 左侧边栏 ========== */
.chat-sidebar {
  width: 240px;
  background: var(--eify-bg-sidebar);
  border-right: 1px solid var(--eify-border-default);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px 16px;
  border-bottom: 1px solid var(--eify-border-default);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-title {
  font-weight: 600;
  color: var(--eify-text-inverse);
}

/* 新建对话按钮（头部位置） */
.new-chat-btn-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  background: var(--eify-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  width: 100%;
}

.new-chat-btn-header:hover:not(:disabled) {
  background: var(--eify-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.new-chat-btn-header:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.new-chat-btn-header svg {
  flex-shrink: 0;
}

.loading-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--eify-text-quaternary);
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--eify-text-quaternary);
  border-top-color: var(--eify-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
  gap: 8px;
}

.conversation-item:hover {
  background: var(--eify-bg-sidebar-hover);
}

.conversation-item:hover .delete-conversation-btn {
  opacity: 1;
}

.conversation-content {
  flex: 1;
  min-width: 0;
}

.delete-conversation-btn {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--eify-text-quaternary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
  flex-shrink: 0;
}

.delete-conversation-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--eify-error);
}

.conversation-item.active {
  background: var(--eify-bg-sidebar-active);
}

.conversation-title {
  color: var(--eify-text-inverse);
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  color: var(--eify-text-quaternary);
  font-size: 12px;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--eify-text-quaternary);
}

.empty-state .hint {
  font-size: 12px;
  margin-top: 8px;
}

/* ========== 右侧聊天窗口 ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--eify-bg-base);
  min-width: 0;
}

.chat-header {
  height: 60px;
  border-bottom: 1px solid var(--eify-border-default);
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: var(--eify-bg-base);
  flex-shrink: 0;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--eify-text-primary);
}

.agent-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.agent-avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--eify-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 16px;
}

.agent-avatar-placeholder.workflow-placeholder {
  background: var(--eify-gradient-primary);
}

.agent-item-avatar .workflow-avatar-icon {
  background: linear-gradient(135deg, var(--eify-primary-50), var(--eify-primary-100));
  color: var(--eify-primary);
}

.agent-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin-bottom: 2px;
}

.agent-info .conversation-title {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  font-weight: 400;
}

/* ========== 消息区域 ========== */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 0;
}

.messages-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--eify-text-tertiary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.messages-empty .hint {
  font-size: 12px;
  margin-top: 8px;
  color: var(--eify-text-quaternary);
}

/* ========== 消息气泡 ========== */
.message {
  display: flex;
  gap: 12px;
  max-width: 800px;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message.assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.avatar-user {
  background: var(--eify-primary);
  color: white;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-ai {
  background: var(--eify-accent);
  color: white;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
  position: relative;
}

/* ========== 消息元信息行（操作按钮 + 时间） ========== */
.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  gap: 8px;
}

/* ========== 消息时间 ========== */
.message-time {
  font-size: 11px;
  line-height: 1.2;
  white-space: nowrap;
  flex-shrink: 0;
  margin-left: auto;
}

.user-bubble .message-time {
  color: rgba(255, 255, 255, 0.65);
}

.ai-bubble .message-time {
  color: var(--eify-text-tertiary);
}

/* ========== 消息操作按钮 ========== */
.message-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.action-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.user-bubble .action-btn {
  color: rgba(255, 255, 255, 0.65);
}

.user-bubble .action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.95);
}

.ai-action-btn {
  color: var(--eify-text-tertiary);
}

.ai-action-btn:hover:not(:disabled) {
  background: var(--eify-bg-subtle);
  color: var(--eify-text-secondary);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai-bubble .message-time {
  margin-top: 6px;
}

.user-bubble {
  background: var(--eify-primary);
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-bubble {
  background: var(--eify-bg-surface);
  color: var(--eify-text-primary);
  border-bottom-left-radius: 4px;
  border: 1px solid var(--eify-border-default);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-bubble > * {
  margin-bottom: 0 !important;
}

.ai-bubble > .markdown-body {
  flex: 1;
}

.ai-bubble > .message-time {
  margin-top: 2px;
}

.ai-bubble.error-bubble {
  background: var(--eify-error-light);
  border-color: var(--eify-error-200);
}

.error-content {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--eify-error-600);
  font-size: 14px;
}

.error-icon {
  flex-shrink: 0;
}

/* ========== 加载动画 ========== */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--eify-text-tertiary);
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) {
  animation-delay: 0s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* ========== Markdown 样式 ========== */
.markdown-body {
  font-size: 14px;
  line-height: 1.65;
  white-space: normal;
  word-break: break-word;
}

.markdown-body :deep(p) {
  margin: 0 0 0.4em 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(br) {
  display: none;
}

.markdown-body :deep(p br) {
  display: inline;
}

/* 列表和引用块间距收紧 */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.3em 0;
  padding-left: 1.6em;
}

.markdown-body :deep(ul:last-child),
.markdown-body :deep(ol:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(li) {
  margin-bottom: 0.15em;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--eify-primary);
  padding: 0.3em 0 0.3em 0.8em;
  margin: 0.4em 0;
  color: var(--eify-text-secondary);
}

.markdown-body :deep(blockquote:last-child) {
  margin-bottom: 0;
}

/* 代码块 */
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 0.1em 0.35em;
  border-radius: 3px;
  font-family: 'SF Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 0.88em;
}

.markdown-body :deep(pre) {
  background: var(--eify-gray-800);
  padding: 0.75em 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.markdown-body :deep(pre:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--eify-gray-200);
  font-size: 0.85em;
  line-height: 1.5;
}

/* 表格 */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--eify-border-default);
  padding: 0.35em 0.6em;
  text-align: left;
  font-size: 0.92em;
}

.markdown-body :deep(th) {
  background: var(--eify-bg-surface);
  font-weight: 600;
}

/* 标题 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 0.6em 0 0.3em 0;
  line-height: 1.3;
}

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child) {
  margin-top: 0;
}

/* 链接 */
.markdown-body :deep(a) {
  color: var(--eify-primary);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 水平线 */
.markdown-body :deep(hr) {
  margin: 0.6em 0;
  border: none;
  border-top: 1px solid var(--eify-border-default);
}

/* 图片 */
.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 0.3em 0;
}

/* 最后一个元素去掉底部 margin */
.markdown-body :deep(*:last-child) {
  margin-bottom: 0 !important;
}

.streaming-content {
  opacity: 0.9;
}

/* ========== 打字机效果 ========== */
.typewriter-content {
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.typewriter-content .cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--eify-primary);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 49% {
    opacity: 1;
  }
  50%, 100% {
    opacity: 0;
  }
}

/* ========== 输入区域 ========== */
.input-container {
  border-top: 1px solid var(--eify-border-default);
  padding: 16px 24px 20px;
  background: var(--eify-bg-base);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  max-width: 900px;
  margin: 0 auto;
}

.message-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--eify-border-default);
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  outline: none;
  transition: all 0.2s;
  min-height: 44px;
  max-height: 200px;
  overflow-y: auto;
}

.message-input:focus {
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.message-input:disabled {
  background: var(--eify-bg-input-disabled);
  cursor: not-allowed;
}

.message-input::placeholder {
  color: var(--eify-text-tertiary);
}

.send-button {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  border: none;
  background: var(--eify-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-button:hover:not(:disabled) {
  background: var(--eify-primary-hover);
  transform: scale(1.05);
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.send-button.sending {
  background: var(--eify-text-tertiary);
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.input-hint {
  text-align: center;
  font-size: 12px;
  color: var(--eify-text-tertiary);
  margin-top: 8px;
}

/* ========== 滚动条样式 ========== */
.messages-container::-webkit-scrollbar,
.conversation-list::-webkit-scrollbar,
.message-input::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track,
.conversation-list::-webkit-scrollbar-track,
.message-input::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb,
.conversation-list::-webkit-scrollbar-thumb,
.message-input::-webkit-scrollbar-thumb {
  background: var(--eify-border-default);
  border-radius: 3px;
}

.messages-container::-webkit-scrollbar-thumb:hover,
.conversation-list::-webkit-scrollbar-thumb:hover,
.message-input::-webkit-scrollbar-thumb:hover {
  background: var(--eify-border-strong);
}

/* ========== Agent 选择器弹窗 ========== */
.agent-selector-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.agent-selector-modal {
  background: var(--eify-bg-base);
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.agent-selector-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--eify-border-default);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.selector-tabs {
  display: flex;
  gap: 4px;
  background: var(--eify-bg-subtle);
  border-radius: 8px;
  padding: 3px;
}

.selector-tab {
  padding: 6px 16px;
  border: none;
  background: transparent;
  color: var(--eify-text-secondary);
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.selector-tab.active {
  background: var(--eify-bg-base);
  color: var(--eify-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.selector-tab:hover:not(.active) {
  color: var(--eify-text-primary);
}

.agent-selector-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--eify-text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--eify-bg-surface);
  color: var(--eify-text-primary);
}

.agent-selector-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

/* ========== 对话名称输入 ========== */
.conversation-name-input {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--eify-border-default);
}

.conversation-name-input label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--eify-text-primary);
  margin-bottom: 8px;
}

.name-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--eify-border-default);
  border-radius: 8px;
  font-size: 14px;
  color: var(--eify-text-primary);
  background: var(--eify-bg-base);
  outline: none;
  transition: all 0.2s;
  margin-bottom: 6px;
}

.name-input:focus {
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.name-input::placeholder {
  color: var(--eify-text-tertiary);
}

.input-counter {
  font-size: 12px;
  color: var(--eify-text-quaternary);
  text-align: right;
  display: block;
}

.agent-loading {
  text-align: center;
  padding: 40px 20px;
  color: var(--eify-text-tertiary);
}

.agent-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--eify-text-tertiary);
}

.agent-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.agent-empty .hint {
  font-size: 12px;
  margin-top: 8px;
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--eify-border-default);
  cursor: pointer;
  transition: all 0.2s;
  background: var(--eify-bg-surface);
}

.agent-item:hover {
  border-color: var(--eify-primary);
  background: var(--eify-bg-subtle);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.agent-item-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.agent-item-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.agent-item-avatar .avatar-placeholder {
  width: 100%;
  height: 100%;
  background: var(--eify-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
}

.agent-item-info {
  flex: 1;
  min-width: 0;
}

.agent-item-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin-bottom: 4px;
}

.agent-item-description {
  font-size: 13px;
  color: var(--eify-text-secondary);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-item-model {
  display: flex;
  gap: 8px;
}

.model-tag,
.provider-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.model-tag {
  background: rgba(99, 102, 241, 0.1);
  color: var(--eify-primary);
}

.provider-tag {
  background: var(--eify-bg-subtle);
  color: var(--eify-text-tertiary);
}

.agent-item-arrow {
  color: var(--eify-text-quaternary);
  flex-shrink: 0;
}

.agent-item:hover .agent-item-arrow {
  color: var(--eify-primary);
}

/* ========== 错误提示 Toast ========== */
.error-toast {
  position: fixed;
  top: 80px;
  right: 24px;
  background: var(--eify-error-light);
  border: 1px solid var(--eify-error-200);
  color: var(--eify-error-600);
  padding: 14px 20px;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  z-index: 3000;
  min-width: 280px;
}

.error-toast svg {
  flex-shrink: 0;
  color: var(--eify-error);
}

/* Toast 动画 */
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.toast-enter-to,
.toast-leave-from {
  opacity: 1;
  transform: translateX(0);
}
</style>
