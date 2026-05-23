<template>
  <EifyListPage
    ref="listPageRef"
    :title="t('agent.title')"
    :description="t('agent.description')"
    :search-placeholder="t('agent.searchPlaceholder')"
    :search-fields="searchFieldsConfig"
    :table-columns="columns"
    :fetch-data="fetchAgents"
    :show-pagination="true"
    :default-page-size="10"
    :page-sizes="[10, 20, 50, 100]"
    :action-width="320"
    :show-view-toggle="true"
    :card-page-size="8"
    :show-stats="true"
    :stats="statsConfig"
    @search="handleAdvancedSearch"
  >
    <!-- 操作按钮 -->
    <template #actions>
      <el-button type="primary" class="eify-btn-primary-gradient" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        {{ t('agent.addAgent') }}
      </el-button>
    </template>

    <!-- 表格列插槽 -->
    <template #table-name="{ row }">
      <div class="name-cell">
        <el-avatar v-if="row.avatar" :src="row.avatar" :size="32" class="agent-avatar" />
        <div v-else class="avatar-placeholder">
          <el-icon><User /></el-icon>
        </div>
        <div class="name-info">
          <div class="name-text">{{ row.name }}</div>
          <div class="model-text">{{ row.defaultModel }}</div>
        </div>
      </div>
    </template>

    <template #table-enabled="{ row }">
      <div class="status-cell">
        <span class="status-dot" :class="{ enabled: row.enabled === 1 }"></span>
        <span class="status-text">{{ row.enabled === 1 ? t('common.enabled') : t('common.disabled') }}</span>
      </div>
    </template>

    <template #table-temperature="{ row }">
      <el-tag size="small" effect="plain">{{ row.temperature }}</el-tag>
    </template>

    <template #table-actions="{ row }">
      <div class="table-action-buttons">
        <el-button size="small" type="success" @click="handleTestChat(row)" :loading="testingId === row.id">
          <el-icon><ChatDotRound /></el-icon>
          {{ t('agent.testChat') }}
        </el-button>
        <el-button size="small" @click="handleEdit(row)">
          <el-icon><Edit /></el-icon>
          {{ t('common.edit') }}
        </el-button>
        <el-button size="small" type="danger" @click="handleDelete(row)">
          <el-icon><Delete /></el-icon>
          {{ t('common.delete') }}
        </el-button>
      </div>
    </template>

    <!-- 卡片视图插槽 -->
    <template #card="{ items }">
      <div
        v-for="item in items"
        :key="item.id"
        class="agent-card"
        :class="{ disabled: item.enabled === 0 }"
      >
        <div class="card-header">
          <el-avatar v-if="item.avatar" :src="item.avatar" :size="42" />
          <div v-else class="card-avatar-placeholder">
            <el-icon><User /></el-icon>
          </div>
          <div class="card-title">
            <h3>{{ item.name }}</h3>
            <el-tag size="small" effect="plain">{{ item.defaultModel }}</el-tag>
          </div>
          <div class="card-status">
            <span class="status-indicator" :class="{ enabled: item.enabled === 1 }">
              <span class="indicator-dot"></span>
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="card-info-row">
            <span class="info-label">{{ t('agent.systemPrompt') }}</span>
            <el-tooltip :content="item.systemPrompt" placement="top" :show-after="300">
              <span class="info-value prompt-tooltip">{{ truncateText(item.systemPrompt, 40) }}</span>
            </el-tooltip>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('agent.temperature') }}</span>
            <el-tag size="small" effect="plain">{{ item.temperature }}</el-tag>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('agent.maxTokens') }}</span>
            <span class="info-value">{{ item.maxTokens || 2000 }}</span>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('common.createTime') }}</span>
            <span class="info-value">{{ formatDate(item.createdAt) }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button size="small" type="success" @click="handleTestChat(item)" :loading="testingId === item.id">
            <el-icon><ChatDotRound /></el-icon>
            {{ t('agent.testChat') }}
          </el-button>
          <div class="action-buttons">
            <el-button size="small" @click="handleEdit(item)">
              <el-icon><Edit /></el-icon>
              {{ t('common.edit') }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon>
              {{ t('common.delete') }}
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </EifyListPage>

  <!-- 表单对话框 -->
  <EifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    :rules="formRules"
    :default-data="defaultFormData"
    :dialog-props="{
      addTitle: t('agent.addAgent'),
      editTitle: t('agent.editAgent'),
      submitText: t('common.confirm'),
      width: '700px',
      labelWidth: '120px'
    }"
    @submit="handleSubmit"
  >
    <template #form="{ data }">
      <el-tabs v-model="activeTab" class="form-tabs" lazy>
        <el-tab-pane :label="t('agent.basicConfig')" name="basic">
          <el-form-item :label="t('common.name')" prop="name">
            <el-input
              v-model="data.name"
              :placeholder="t('agent.agentNamePlaceholder')"
              maxlength="100"
              show-word-limit
            />
          </el-form-item>

          <el-form-item :label="t('common.description')" prop="description">
            <el-input
              v-model="data.description"
              type="textarea"
              :rows="3"
              :placeholder="t('agent.description')"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item :label="t('agent.avatarUrl')" prop="avatar">
            <el-input
              v-model="data.avatar"
              :placeholder="t('agent.avatarUrlPlaceholder')"
              maxlength="500"
            />
          </el-form-item>

          <el-form-item :label="t('agent.selectProvider')" prop="defaultProviderId">
            <el-select
              v-model="data.defaultProviderId"
              :placeholder="t('agent.selectProvider')"
              style="width: 100%"
              @change="handleProviderChange"
            >
              <el-option
                v-for="provider in providers"
                :key="provider.id"
                :label="provider.name"
                :value="provider.id"
              >
                <span>{{ provider.name }}</span>
                <el-tag size="small" style="margin-left: 8px" effect="plain">{{ provider.type }}</el-tag>
              </el-option>
            </el-select>
            <div class="form-hint" v-if="providers.length === 0">{{ t('provider.unsyncedHint') }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.selectModel')" prop="defaultModel">
            <el-autocomplete
              v-model="data.defaultModel"
              :fetch-suggestions="searchModels"
              :placeholder="t('agent.selectModel')"
              style="width: 100%"
              clearable
            >
              <template #default="{ item }">
                <div class="model-suggestion">
                  <span class="model-name">{{ item.value }}</span>
                  <span class="model-type" v-if="item.type">{{ item.type }}</span>
                </div>
              </template>
            </el-autocomplete>
            <div class="model-hint" v-if="getCurrentRecommendedModels().length > 0">
              <el-text size="small" type="info">
                {{ t('agent.recommendedModels') }}
                <el-tag
                  v-for="model in getCurrentRecommendedModels()"
                  :key="model"
                  size="small"
                  @click="data.defaultModel = model"
                  style="cursor: pointer; margin-left: 4px"
                >
                  {{ model }}
                </el-tag>
              </el-text>
            </div>
          </el-form-item>

          <el-form-item :label="t('common.status')" prop="enabled">
            <el-radio-group v-model="data.enabled">
              <el-radio :value="1">{{ t('common.enabled') }}</el-radio>
              <el-radio :value="0">{{ t('common.disabled') }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane :label="t('agent.promptConfig')" name="prompt">
          <el-form-item :label="t('agent.systemPrompt')" prop="systemPrompt">
            <el-input
              v-model="data.systemPrompt"
              type="textarea"
              :rows="6"
              :placeholder="t('agent.systemPromptPlaceholder')"
              maxlength="5000"
              show-word-limit
            />
          </el-form-item>

          <el-form-item :label="t('agent.userMessagePrefix')" prop="userMessagePrefix">
            <el-input
              v-model="data.userMessagePrefix"
              type="textarea"
              :rows="2"
              :placeholder="t('agent.userMessagePrefixPlaceholder')"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>

          <el-form-item :label="t('agent.welcomeMessageField')" prop="welcomeMessage">
            <el-input
              v-model="data.welcomeMessage"
              type="textarea"
              :rows="2"
              :placeholder="t('agent.welcomeMessagePlaceholder')"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane :label="t('agent.modelParams')" name="params">
          <el-form-item :label="t('agent.temperature')" prop="temperature">
            <el-slider
              v-model="data.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :marks="temperatureMarks"
              show-stops
            />
            <div class="param-value-fixed">{{ data.temperature }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.maxTokens')" prop="maxTokens">
            <el-input-number
              v-model="data.maxTokens"
              :min="1"
              :max="128000"
              :step="100"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item :label="t('agent.topP')" prop="topP">
            <el-slider
              v-model="data.topP"
              :min="0"
              :max="1"
              :step="0.05"
              show-stops
            />
            <div class="param-value-fixed">{{ data.topP }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.frequencyPenalty')" prop="frequencyPenalty">
            <el-slider
              v-model="data.frequencyPenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              show-stops
            />
            <div class="param-value-fixed">{{ data.frequencyPenalty }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.presencePenalty')" prop="presencePenalty">
            <el-slider
              v-model="data.presencePenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              show-stops
            />
            <div class="param-value-fixed">{{ data.presencePenalty }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.maxHistoryRounds')" prop="maxHistoryRounds">
            <el-input-number
              v-model="data.maxHistoryRounds"
              :min="0"
              :max="100"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item :label="t('agent.streamEnabled')" prop="streamEnabled">
            <el-radio-group v-model="data.streamEnabled">
              <el-radio :value="1">{{ t('common.enabled') }}</el-radio>
              <el-radio :value="0">{{ t('common.disabled') }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane :label="t('agent.ragConfig')" name="rag">
          <el-form-item :label="t('knowledge.title')">
            <el-select
              v-model="data.knowledgeIds"
              multiple
              :placeholder="t('agent.knowledgeSelectPlaceholder')"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="kb in knowledgeList"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              >
                <div class="kb-option">
                  <span>{{ kb.name }}</span>
                  <el-tag size="small" effect="plain">{{ kb.embeddingModel }}</el-tag>
                </div>
              </el-option>
            </el-select>
            <div class="form-hint">{{ t('agent.knowledgeHint') }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.ragEnabled')">
            <el-switch
              v-model="data.ragEnabled"
              :active-value="1"
              :inactive-value="0"
              :active-text="t('common.enabled')"
              :inactive-text="t('common.disabled')"
              :disabled="!data.knowledgeIds || data.knowledgeIds.length === 0"
            />
          </el-form-item>

          <el-form-item :label="t('agent.ragTopK')">
            <el-slider
              v-model="data.ragTopK"
              :min="1"
              :max="20"
              :step="1"
              show-stops
              :disabled="!data.knowledgeIds || data.knowledgeIds.length === 0 || !data.ragEnabled"
            />
            <div class="param-value-fixed">{{ data.ragTopK }}</div>
          </el-form-item>

          <el-form-item :label="t('agent.retrievalStrategy')">
            <el-radio-group
              v-model="data.ragStrategy"
              :disabled="!data.knowledgeIds || data.knowledgeIds.length === 0 || !data.ragEnabled"
            >
              <el-radio value="hybrid">{{ t('agent.hybrid') }}</el-radio>
              <el-radio value="vector">{{ t('agent.vector') }}</el-radio>
              <el-radio value="keyword">{{ t('agent.keyword') }}</el-radio>
            </el-radio-group>
            <div class="form-hint">{{ t('agent.ragStrategyHint') }}</div>
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane :label="t('agent.mcpTools')" name="tools">
          <div v-if="mcpToolOptions.length === 0" class="empty-tools">
            <el-empty :description="t('agent.noToolsAvailable')" :image-size="80" />
          </div>
          <div v-else class="tools-wrapper">
            <div class="tools-hint">
              {{ t('agent.toolsHint') }}
            </div>
            <el-checkbox-group v-model="data.mcpToolIds" :max="10">
              <div
                v-for="(tools, serverName) in groupedMcpTools"
                :key="serverName"
                class="tool-group"
              >
                <div class="tool-group-header">{{ serverName }}</div>
                <div
                  v-for="tool in tools"
                  :key="tool.id"
                  class="tool-item"
                >
                  <el-checkbox :value="tool.id" :label="tool.id">
                    <span class="tool-name">{{ tool.name }}</span>
                  </el-checkbox>
                  <span class="tool-desc" :title="tool.description">{{ tool.description }}</span>
                </div>
              </div>
            </el-checkbox-group>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </EifyFormDialog>

  <!-- 测试对话对话框 -->
  <el-dialog
    v-model="testChatVisible"
    :title="`${t('agent.testChat')} - ${currentAgent?.name}`"
    width="900px"
    :close-on-click-modal="false"
  >
    <div class="test-chat-container">
      <!-- 工具栏 -->
      <div class="chat-toolbar">
        <div class="toolbar-left">
          <el-button size="small" @click="handleClearChat" :disabled="chatMessages.length === 0">
            <el-icon><Delete /></el-icon>
            {{ t('agent.clearChat') }}
          </el-button>
          <el-button size="small" @click="handleExportChat" :disabled="chatMessages.length === 0">
            <el-icon><Download /></el-icon>
            {{ t('common.export') }}
          </el-button>
        </div>
        <div class="toolbar-right">
          <span class="agent-info">
            <el-tag size="small" effect="plain">{{ currentAgent?.defaultModel }}</el-tag>
            <span class="agent-name">{{ currentAgent?.name }}</span>
          </span>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="chatMessagesRef">
        <!-- Agent 欢迎语 -->
        <div v-if="currentAgent?.welcomeMessage && chatMessages.length === 0" class="message assistant welcome">
          <div class="message-avatar assistant-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="message-bubble ai-bubble welcome-bubble">
            {{ currentAgent.welcomeMessage }}
            <div class="message-meta">
              <div class="message-actions">
                <button class="action-btn ai-action-btn" :title="t('chat.copyMessage')" @click.stop="copyMessage(currentAgent.welcomeMessage!)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                </button>
              </div>
              <div class="message-time">{{ formatTime(new Date()) }}</div>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, index) in chatMessages"
          :key="index"
          class="message"
          :class="msg.role"
        >
          <div class="message-avatar" :class="msg.role === 'user' ? 'user-avatar' : 'assistant-avatar'">
            <el-icon v-if="msg.role === 'user'"><User /></el-icon>
            <el-icon v-else><ChatDotRound /></el-icon>
          </div>
          <div class="message-bubble" :class="[msg.role === 'user' ? 'user-bubble' : 'ai-bubble', { 'error-bubble': msg.isError }]">
            <!-- 错误消息 -->
            <div v-if="msg.isError" class="error-content">
              <svg class="error-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>
              {{ msg.content }}
            </div>
            <!-- 打字机效果 -->
            <div v-else-if="msg.isTyping" class="typewriter-content">{{ msg.content }}<span class="cursor">|</span></div>
            <!-- Markdown 渲染 -->
            <div v-else-if="!msg.isError" class="markdown-body" v-html="formatMessage(msg.content)"></div>
            <!-- 用户消息：操作按钮 + 时间 同行 -->
            <div v-if="msg.role === 'user' && !msg.isTyping && msg.timestamp" class="message-meta user-meta">
              <div class="message-actions">
                <button class="action-btn" :title="t('chat.copyMessage')" @click.stop="copyMessage(msg.content)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                </button>
                <button class="action-btn" :title="t('chat.resend')" :disabled="loadingChat || typewriterActive" @click.stop="resendMessage(msg.content)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                  </svg>
                </button>
              </div>
              <div class="message-time">{{ formatTime(new Date(msg.timestamp || '')) }}</div>
            </div>
            <!-- AI 消息：复制按钮 + Token/延迟 同行 -->
            <div v-if="msg.role === 'assistant' && !msg.isTyping && !msg.isError && msg.content" class="message-meta ai-meta">
              <div class="message-actions">
                <button class="action-btn ai-action-btn" :title="t('chat.copyMessage')" @click.stop="copyMessage(msg.content)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                </button>
              </div>
              <div v-if="msg.tokens || msg.latency" class="message-stats">
                <span v-if="msg.tokens">{{ t('agent.tokens') }}: {{ msg.tokens }}</span>
                <span v-if="msg.latency"> | {{ t('agent.latency') }}: {{ msg.latency }}ms</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loadingChat" class="message assistant">
          <div class="message-avatar assistant-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="message-bubble ai-bubble loading-bubble">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <!-- 快捷提示 -->
        <div class="quick-prompts" v-if="currentAgent?.systemPrompt">
          <span class="prompt-label">{{ t('agent.quickPrompt') }}</span>
          <el-tag
            v-for="(prompt, index) in quickPrompts"
            :key="index"
            size="small"
            @click="handleQuickPrompt(prompt)"
            class="prompt-tag"
            effect="plain"
          >
            {{ prompt }}
          </el-tag>
        </div>

        <!-- 输入框 -->
        <div class="input-wrapper">
          <el-input
            ref="inputRef"
            v-model="testMessage"
            type="textarea"
            :rows="3"
            :placeholder="t('agent.testMessagePlaceholder')"
            :disabled="loadingChat || typewriterActive"
            @keydown.enter.ctrl="handleSendTestMessage"
            @keydown.meta.enter.ctrl="handleSendTestMessage"
          />
          <div class="input-actions">
            <el-button
              type="primary"
              :loading="loadingChat"
              :disabled="!testMessage.trim() || typewriterActive"
              @click="handleSendTestMessage"
              size="default"
            >
              {{ t('agent.sendMessage') }}
            </el-button>
            <el-button
              @click="handleClearChat"
              :disabled="loadingChat || chatMessages.length === 0"
              size="default"
              link
            >
              {{ t('agent.clearChat') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>

  <ConfirmDialog
    :show="showDeleteConfirm"
    :title="t('common.confirmDeleteTitle')"
    :message="deleteTarget ? t('common.confirmDelete', { name: deleteTarget.name }) : ''"
    type="danger"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import i18n from '@/i18n'
import { ElMessage } from 'element-plus'
import {
  Plus,
  User,
  Edit,
  Delete,
  ChatDotRound,
  Download
} from '@element-plus/icons-vue'
import { useLocaleStore } from '@/store/locale'
import EifyListPage from '@/components/EifyListPage.vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  agentApi,
  type AgentResponse,
  type AgentListParams,
  type AgentTestChatResponse
} from '@/api/agent'
import {
  providerApi,
  type ProviderResponse,
  type ModelConfigInfo
} from '@/api/provider'
import {
  knowledgeApi,
  type KnowledgeBaseResponse
} from '@/api/knowledge'
import {
  mcpApi
} from '@/api/mcp'
import type { TableColumn } from '@/components/EifyTable.vue'
import type { SearchCondition } from '@/components/EifySearch.vue'
import type { ListStat } from '@/types/api'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const { t } = useI18n()
const localeStore = useLocaleStore()

/* ========== 类型定义 ========== */

interface AgentFormData {
  id?: number
  name: string
  description: string
  avatar: string
  defaultProviderId: number
  defaultModel: string
  systemPrompt: string
  userMessagePrefix: string
  welcomeMessage: string
  temperature: number
  maxTokens: number
  topP: number
  frequencyPenalty: number
  presencePenalty: number
  maxHistoryRounds: number
  streamEnabled: number
  enabled: number
  knowledgeIds: number[]
  mcpToolIds: number[]
  ragEnabled: number
  ragTopK: number
  ragStrategy: string
}

/** MCP 工具选项（按 Server 分组） */
interface McpToolOption {
  id: number
  name: string
  description: string
  serverName: string
  serverId: number
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  tokens?: number
  latency?: number
  timestamp?: string
  isTyping?: boolean
  isError?: boolean
}

/* ========== 表格配置 ========== */

const columns = computed<TableColumn[]>(() => [
  { prop: 'name', label: t('common.name'), minWidth: 200, slot: 'name' },
  { prop: 'description', label: t('common.description'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'enabled', label: t('common.status'), minWidth: 90, slot: 'enabled' },
  { prop: 'temperature', label: t('agent.temperature'), minWidth: 110, slot: 'temperature' },
  { prop: 'maxTokens', label: t('agent.maxTokens'), minWidth: 110 },
  { prop: 'createdAt', label: t('common.createTime'), minWidth: 150 }
])

/* ========== 搜索配置 ========== */

const searchFieldsConfig = computed(() => [
  {
    key: 'name',
    label: t('common.name'),
    description: t('agent.agentNamePlaceholder'),
    inputType: 'text' as const,
    placeholder: t('agent.agentNamePlaceholder')
  },
  {
    key: 'enabled',
    label: t('common.status'),
    description: t('common.filterHint'),
    inputType: 'select' as const,
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 }
    ]
  }
])

/* ========== 响应式状态 ========== */

const listPageRef = ref()
const dialogRef = ref()
const dialogVisible = ref(false)
const testChatVisible = ref(false)
const testingId = ref<number | null>(null)
const searchConditions = ref<SearchCondition[]>([])
const activeTab = ref('basic')

// 删除确认
const showDeleteConfirm = ref(false)
const deleteTarget = ref<{ id: number; name: string } | null>(null)
const providers = ref<ProviderResponse[]>([])
const providerModelsMap = ref<Map<number, ModelConfigInfo[]>>(new Map())
const knowledgeList = ref<KnowledgeBaseResponse[]>([])
const mcpToolOptions = ref<McpToolOption[]>([])
const currentAgent = ref<AgentResponse | null>(null)
const chatMessages = ref<ChatMessage[]>([])
const testMessage = ref('')
const loadingChat = ref(false)
const chatMessagesRef = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()

const temperatureMarks = computed(() => ({
  0: t('agent.temperaturePrecise'),
  1: t('agent.temperatureBalanced'),
  2: t('agent.temperatureRandom')
}))

/* ========== 统计配置 ========== */

const statsData = ref({ total: 0, enabled: 0, disabled: 0 })
const statsConfig = computed<ListStat[]>(() => [
  { key: 'total', label: t('common.total'), value: statsData.value.total || '-' },
  { key: 'enabled', label: t('common.enabled'), value: statsData.value.enabled || '-', class: 'enabled' },
  { key: 'disabled', label: t('common.disabled'), value: statsData.value.disabled || '-', class: 'disabled' }
])

/* ========== 表单默认数据 ========== */

// 定义表单默认数据（供应商 ID 会在 loadProviders 后更新）
const defaultFormData: AgentFormData = {
  name: '',
  description: '',
  avatar: '',
  defaultProviderId: 0,  // 会在 loadProviders 完成后更新为第一个可用供应商
  defaultModel: '',
  systemPrompt: 'You are a helpful assistant.',
  userMessagePrefix: '',
  welcomeMessage: '',
  temperature: 0.7,
  maxTokens: 2000,
  topP: 1.0,
  frequencyPenalty: 0,
  presencePenalty: 0,
  maxHistoryRounds: 10,
  streamEnabled: 1,
  enabled: 1,
  knowledgeIds: [],
  mcpToolIds: [],
  ragEnabled: 0,
  ragTopK: 5,
  ragStrategy: 'hybrid'
}

/* ========== 表单验证规则 ========== */

const formRules = {
  name: [
    { required: true, message: () => t('agent.agentNameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: () => t('agent.agentNameRequired'), trigger: 'blur' }
  ],
  defaultProviderId: [
    {
      required: true,
      validator: (_rule: any, value: any, callback: any) => {
        if (!value || value === 0) {
          callback(new Error(t('agent.selectProvider')))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  defaultModel: [
    { required: true, message: () => t('agent.selectModel'), trigger: 'blur' },
    { min: 1, max: 100, message: () => t('agent.selectModel'), trigger: 'blur' }
  ],
  systemPrompt: [
    { required: true, message: () => t('agent.systemPromptPlaceholder'), trigger: 'blur' },
    { min: 1, max: 5000, message: () => t('agent.systemPromptPlaceholder'), trigger: 'blur' }
  ]
}

/* ========== 生命周期 ========== */

onMounted(async () => {
  await Promise.all([loadProviders(), loadKnowledgeList()])
})

/* ========== API 调用 ========== */

/**
 * 加载供应商列表（仅包含已同步模型的供应商）
 */
const loadProviders = async () => {
  try {
    const result = await providerApi.getProviderList({ enabled: 1, pageSize: 100 })
    const allProviders = (result as any).list || result.records || []

    // 并行获取每个供应商的模型列表，过滤未同步的供应商
    const modelsResults = await Promise.allSettled(
      allProviders.map((p: ProviderResponse) => providerApi.getProviderModels(p.id))
    )
    const map = new Map<number, ModelConfigInfo[]>()
    providers.value = allProviders.filter((p: ProviderResponse, i: number) => {
      const r = modelsResults[i]
      if (r.status === 'fulfilled' && r.value.length > 0) {
        map.set(p.id, r.value)
        return true
      }
      return false
    })
    providerModelsMap.value = map

    if (providers.value.length > 0) {
      defaultFormData.defaultProviderId = providers.value[0].id
    }
  } catch (error) {
    console.error('Failed to load providers:', error)
  }
}

const loadKnowledgeList = async () => {
  try {
    knowledgeList.value = await knowledgeApi.getKnowledgeList()
  } catch (error) {
    console.error('Failed to load knowledge list:', error)
  }
}

/**
 * 加载 MCP 工具选项（按 Server 分组）
 */
const loadMcpTools = async () => {
  try {
    const result = await mcpApi.getList({ pageSize: 100, enabled: 1 })
    const options: McpToolOption[] = []
    const servers = (result as any).list || result.records || []
    for (const server of servers) {
      if (server.enabled !== 1) continue
      try {
        const detail = await mcpApi.getById(server.id)
        if (detail.tools) {
          for (const tool of detail.tools) {
            options.push({
              id: tool.id,
              name: tool.name,
              description: tool.description,
              serverName: server.name,
              serverId: server.id
            })
          }
        }
      } catch {
        // 跳过无法加载的 Server
      }
    }
    mcpToolOptions.value = options
  } catch (error) {
    console.error('Failed to load MCP tools:', error)
  }
}

/**
 * 按 Server 分组工具选项
 */
const groupedMcpTools = computed(() => {
  const groups: Record<string, McpToolOption[]> = {}
  for (const tool of mcpToolOptions.value) {
    const key = tool.serverName || `Server #${tool.serverId}`
    if (!groups[key]) groups[key] = []
    groups[key].push(tool)
  }
  return groups
})

/**
 * 获取 Agent 列表
 */
const fetchAgents = async (params: {
  page: number
  size: number
  conditions?: SearchCondition[]
}) => {
  const queryParams: AgentListParams = {
    page: params.page,
    pageSize: params.size
  }

  // 处理搜索条件
  if (params.conditions && params.conditions.length > 0) {
    params.conditions.forEach(condition => {
      if (condition.field === 'name') {
        queryParams.name = condition.value as string
      } else if (condition.field === 'enabled') {
        queryParams.enabled = condition.value as number
      }
    })
  }

  const result = await agentApi.getAgentList(queryParams)
  const records = (result as any).list || result.records || []

  // 更新统计
  updateStats(records)

  return {
    records,
    total: result.total
  }
}

/**
 * 创建 Agent
 */
const createAgent = async (data: AgentFormData) => {
  const requestData: any = {
    name: data.name,
    description: data.description || undefined,
    avatar: data.avatar || undefined,
    defaultProviderId: data.defaultProviderId,
    defaultModel: data.defaultModel,
    systemPrompt: data.systemPrompt,
    userMessagePrefix: data.userMessagePrefix || undefined,
    welcomeMessage: data.welcomeMessage || undefined,
    temperature: data.temperature,
    maxTokens: data.maxTokens,
    topP: data.topP,
    frequencyPenalty: data.frequencyPenalty,
    presencePenalty: data.presencePenalty,
    maxHistoryRounds: data.maxHistoryRounds,
    streamEnabled: data.streamEnabled,
    enabled: data.enabled,
    knowledgeIds: data.knowledgeIds.length > 0 ? data.knowledgeIds : undefined,
    mcpToolIds: data.mcpToolIds.length > 0 ? data.mcpToolIds : undefined,
    ragEnabled: data.knowledgeIds.length > 0 ? data.ragEnabled : 0,
    ragTopK: data.ragTopK,
    ragStrategy: data.ragStrategy
  }
  return await agentApi.createAgent(requestData)
}

/**
 * 更新 Agent
 */
const updateAgent = async (data: AgentFormData) => {
  if (!data.id) return

  const requestData: any = {
    name: data.name,
    description: data.description || undefined,
    avatar: data.avatar || undefined,
    defaultProviderId: data.defaultProviderId,
    defaultModel: data.defaultModel,
    systemPrompt: data.systemPrompt,
    userMessagePrefix: data.userMessagePrefix || undefined,
    welcomeMessage: data.welcomeMessage || undefined,
    temperature: data.temperature,
    maxTokens: data.maxTokens,
    topP: data.topP,
    frequencyPenalty: data.frequencyPenalty,
    presencePenalty: data.presencePenalty,
    maxHistoryRounds: data.maxHistoryRounds,
    streamEnabled: data.streamEnabled,
    enabled: data.enabled,
    knowledgeIds: data.knowledgeIds,
    mcpToolIds: data.mcpToolIds.length > 0 ? data.mcpToolIds : [],
    ragEnabled: data.knowledgeIds.length > 0 ? data.ragEnabled : 0,
    ragTopK: data.ragTopK,
    ragStrategy: data.ragStrategy
  }

  return await agentApi.updateAgent(data.id, requestData)
}

/**
 * 删除 Agent
 */
const deleteAgent = async (id: number) => {
  await agentApi.deleteAgent(id)
  ElMessage.success(t('common.deleteSuccess'))
  listPageRef.value?.refresh()
}

/**
 * 测试对话
 */
const testChat = async (id: number, message: string): Promise<AgentTestChatResponse> => {
  return await agentApi.testChat(id, { message })
}

/* ========== 工具方法 ========== */

const truncateText = (text: string, maxLength: number) => {
  if (!text) return ''
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const localeTag = localeStore.current === 'en-US' ? 'en-US' : 'zh-CN'
  return date.toLocaleString(localeTag, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const updateStats = (list: AgentResponse[]) => {
  if (!Array.isArray(list)) list = []
  statsData.value = {
    total: list.length,
    enabled: list.filter(a => a.enabled === 1).length,
    disabled: list.filter(a => a.enabled === 0).length
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  })
}

/* ========== 事件处理 ========== */

const handleAdd = () => {
  activeTab.value = 'basic'
  loadMcpTools()
  dialogRef.value?.open()
}

const handleEdit = (row: Record<string, any>) => {
  const formData: AgentFormData = {
    id: row.id,
    name: row.name,
    description: row.description || '',
    avatar: row.avatar || '',
    defaultProviderId: row.defaultProviderId,
    defaultModel: row.defaultModel,
    systemPrompt: row.systemPrompt,
    userMessagePrefix: row.userMessagePrefix || '',
    welcomeMessage: row.welcomeMessage || '',
    temperature: row.temperature,
    maxTokens: row.maxTokens || 2000,
    topP: row.topP,
    frequencyPenalty: row.frequencyPenalty || 0,
    presencePenalty: row.presencePenalty || 0,
    maxHistoryRounds: row.maxHistoryRounds || 10,
    streamEnabled: row.streamEnabled || 1,
    enabled: row.enabled,
    knowledgeIds: row.knowledgeIds || [],
    mcpToolIds: row.mcpToolIds || [],
    ragEnabled: row.ragEnabled ?? 0,
    ragTopK: row.ragTopK ?? 5,
    ragStrategy: row.ragStrategy || 'hybrid'
  }
  activeTab.value = 'basic'
  loadMcpTools()
  dialogRef.value?.open(formData)
}

const handleDelete = (row: { id: number; name: string }) => {
  deleteTarget.value = row
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value) return
  try {
    await deleteAgent(deleteTarget.value.id)
  } finally {
    showDeleteConfirm.value = false
    deleteTarget.value = null
  }
}

const cancelDelete = () => {
  showDeleteConfirm.value = false
  deleteTarget.value = null
}

const handleProviderChange = (providerId: number) => {
  // 根据选择的供应商，从 API 获取的模型列表中自动推荐
  const models = providerModelsMap.value.get(providerId) || []
  if (dialogRef.value && dialogRef.value.formData) {
    const formData = dialogRef.value.formData as any
    if (models.length > 0 && !formData.defaultModel) {
      formData.defaultModel = models[0].modelName
    }
  }
}

/**
 * 获取当前选择供应商的模型列表
 */
const getCurrentRecommendedModels = () => {
  if (!dialogRef.value || !dialogRef.value.formData) {
    return []
  }
  const formData = dialogRef.value.formData as any
  if (!formData.defaultProviderId) {
    return []
  }
  const models = providerModelsMap.value.get(formData.defaultProviderId) || []
  return models.map(m => m.modelName)
}

/**
 * 搜索模型（用于 el-autocomplete）
 */
const searchModels = (queryString: string, callback: (data: any[]) => void) => {
  const modelNames = getCurrentRecommendedModels()
  const results = queryString
    ? modelNames.filter(name => name.toLowerCase().includes(queryString.toLowerCase()))
      .map(name => ({ value: name, type: t('agent.recommendationLabel') }))
    : modelNames.map(name => ({ value: name, type: t('agent.recommendationLabel') }))

  callback(results)
}

const handleTestChat = (row: { id: number; name: string }) => {
  currentAgent.value = row as any
  // 重新计算快速提示
  Object.assign(quickPrompts, getQuickPrompts())
  chatMessages.value = []
  testMessage.value = ''
  testChatVisible.value = true
}

const typewriterActive = ref(false)

async function runTypewriter(msg: ChatMessage, fullText: string) {
  typewriterActive.value = true
  msg.isTyping = true
  let current = ''
  const chars = [...fullText]
  const baseDelay = 20

  for (let i = 0; i < chars.length; i++) {
    current += chars[i]
    msg.content = current
    let delay = baseDelay
    if (/[，。！？\n]/.test(chars[i])) delay = baseDelay * 4
    else if (/[,!?.:;]/.test(chars[i])) delay = baseDelay * 3
    else if (chars[i] === ' ') delay = baseDelay * 0.5
    await new Promise(resolve => setTimeout(resolve, delay))
    nextTick(() => scrollToBottom())
  }

  msg.isTyping = false
  typewriterActive.value = false
}

const handleSendTestMessage = async () => {
  if (!currentAgent.value || !testMessage.value.trim() || typewriterActive.value) return

  const userMessage = testMessage.value.trim()
  chatMessages.value.push({
    role: 'user',
    content: userMessage,
    timestamp: new Date().toISOString()
  })

  testMessage.value = ''
  loadingChat.value = true
  scrollToBottom()

  try {
    const result = await testChat(currentAgent.value.id, userMessage)

    if (result.success && result.reply) {
      chatMessages.value.push({
        role: 'assistant',
        content: '',
        tokens: result.tokens?.totalTokens,
        latency: result.performance?.latencyMs,
        timestamp: new Date().toISOString()
      })
      const msg = chatMessages.value[chatMessages.value.length - 1]
      loadingChat.value = false
      await runTypewriter(msg, result.reply)
      ElMessage.success(t('agent.testSuccess'))
    } else {
      chatMessages.value.push({
        role: 'assistant',
        content: result.errorMessage || t('agent.chatFailed'),
        timestamp: new Date().toISOString(),
        isError: true
      })
      ElMessage.error(result.errorMessage || t('agent.testFailed'))
      loadingChat.value = false
    }
  } catch (error: any) {
    chatMessages.value.push({
      role: 'assistant',
      content: error.message || t('agent.testFailed'),
      timestamp: new Date().toISOString(),
      isError: true
    })
    ElMessage.error(error.message || t('agent.testFailed'))
    loadingChat.value = false
  } finally {
    scrollToBottom()
  }
}

const handleAdvancedSearch = (conditions: SearchCondition[]) => {
  searchConditions.value = [...conditions]
  listPageRef.value?.refresh()
}

const handleSubmit = async (data: any, mode: string) => {
  try {
    if (mode === 'add') {
      await createAgent(data)
      ElMessage.success(t('common.createSuccess'))
    } else {
      await updateAgent(data)
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogRef.value?.submitSuccess()
    listPageRef.value?.refresh()
  } catch (error: any) {
    dialogRef.value?.submitFail()
  }
}

/* ========== 测试对话功能 ========== */

/**
 * 清空对话历史
 */
const handleClearChat = () => {
  chatMessages.value = []
  ElMessage.success(t('agent.clearChat'))
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
  testMessage.value = content
  nextTick(() => {
    handleSendTestMessage()
  })
}

/**
 * 导出对话记录
 */
const handleExportChat = () => {
  if (chatMessages.value.length === 0) {
    ElMessage.warning(t('agent.noExportData'))
    return
  }

  // 格式化对话内容
  const exportContent = chatMessages.value.map(msg => {
    const time = formatTime(new Date(msg.timestamp || ''))
    const roleText = msg.role === 'user' ? t('agent.userRole') : t('agent.aiRole')
    return `[${time}] ${roleText}: ${msg.content}`
  }).join('\n\n')

  // 创建下载链接
  const blob = new Blob([`${currentAgent.value?.name || 'Agent'} - ${t('agent.testChat')}\n\n${exportContent}`], {
    type: 'text/plain;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `agent-test-${new Date().getTime()}.txt`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success(t('agent.chatExported'))
}

/**
 * 处理快速提示
 */
const handleQuickPrompt = (prompt: string) => {
  testMessage.value = prompt
  // 自动聚焦到输入框
  nextTick(() => {
    inputRef.value?.focus()
  })
}


/**
 * 格式化消息内容（支持 Markdown）
 */
const formatMessage = (content: string) => {
  try {
    // 简单的 Markdown 渲染
    return DOMPurify.sanitize(marked.parse(content) as string)
  } catch (error) {
    // 如果解析失败，转义 HTML
    return content.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

/**
 * 格式化时间
 */
const formatTime = (date: Date) => {
  const localeTag = localeStore.current === 'en-US' ? 'en-US' : 'zh-CN'
  return date.toLocaleTimeString(localeTag, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

/**
 * 获取快速提示列表（从系统提示词中提取关键问题）
 */
const getQuickPrompts = () => {
  if (!currentAgent.value?.systemPrompt) return []

  // 提取一些常见的问题类型
  const prompts = []

  const localeMsgs = (i18n.global.messages as any).value?.[i18n.global.locale.value]
      || (i18n.global.messages as any).value?.['zh-CN']
  const qp = localeMsgs?.agent?.quickPrompts as Record<string, string[]> | undefined
  if (!qp) return prompts
  // 如果是助手类
  if (currentAgent.value.systemPrompt.toLowerCase().includes('assistant') ||
      currentAgent.value.systemPrompt.toLowerCase().includes('help')) {
    prompts.push(...qp.assistant)
  }

  // 如果是写作类
  if (currentAgent.value.systemPrompt.toLowerCase().includes('write') ||
      currentAgent.value.systemPrompt.toLowerCase().includes('写作')) {
    prompts.push(...qp.writing)
  }

  // 如果是编程类
  if (currentAgent.value.systemPrompt.toLowerCase().includes('code') ||
      currentAgent.value.systemPrompt.toLowerCase().includes('programming')) {
    prompts.push(...qp.coding)
  }

  // 如果没有匹配的类型，使用通用提示
  if (prompts.length === 0) {
    prompts.push(...qp.general)
  }

  return prompts
}

// 直接使用函数而不是 computed
const quickPrompts = getQuickPrompts()
</script>

<style scoped>
/* 主按钮渐变样式 */
.eify-btn-primary-gradient {
  background: var(--eify-gradient-primary);
  border: none;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  transition: var(--eify-transition-base);
}

.eify-btn-primary-gradient:hover {
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
}

/* 操作按钮链接文字样式 */
.table-action-buttons {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-1);
}

.table-action-buttons .el-button {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
}

.table-action-buttons .el-button .el-icon {
  font-size: 13px;
  margin-right: 2px;
}

/* 表格视图增强 */
.name-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.agent-avatar {
  flex-shrink: 0;
}

.avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  flex-shrink: 0;
}

.name-info {
  flex: 1;
  min-width: 0;
}

.name-text {
  font-weight: 500;
  color: var(--eify-text-primary);
  margin-bottom: 2px;
}

.model-text {
  font-size: 12px;
  color: var(--eify-text-tertiary);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  position: relative;
}

.status-dot.enabled {
  background: var(--eify-success);
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
}

.status-dot:not(.enabled) {
  background: var(--eify-text-tertiary);
}

.status-dot.enabled::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 1px solid var(--eify-success);
  opacity: 0.5;
  animation: status-pulse 2s ease-in-out infinite;
}

@keyframes status-pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.status-text {
  font-size: 14px;
}

/* 表单样式调整 */
:deep(.el-form-item__label) {
  font-weight: 500;
}

.form-tabs {
  margin-top: -20px;
}

.form-tabs :deep(.el-tabs__content) {
  padding-top: 20px;
}

.param-value {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  margin-top: 8px;
  text-align: center;
}

/* 滑块参数容器 */
:deep(.el-tabs__content .el-form-item) {
  margin-bottom: 32px; /* 增加行间距，避免重叠 */
}

:deep(.el-form-item__content) {
  position: relative;
}

:deep(.el-slider) {
  width: calc(100% - 80px); /* 滑块宽度与输入框保持一致 */
}

:deep(.el-input-number) {
  width: calc(100% - 80px) !important; /* 输入框宽度与滑块保持一致 */
}

/* 当前值固定定位在进度条右侧 */
.param-value-fixed {
  position: absolute;
  right: 0;
  top: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--eify-primary);
  line-height: 32px;
  min-width: 40px;
  text-align: right;
}

/* ========== 模型建议 ========== */
.model-suggestion {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.model-name {
  flex: 1;
  font-size: 14px;
}

.model-type {
  font-size: 11px;
  color: var(--eify-text-tertiary);
  padding: 2px 6px;
  background: var(--eify-bg-surface);
  border-radius: 4px;
}

.model-hint {
  margin-top: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(139, 92, 246, 0.05) 100%);
  border-radius: var(--eify-radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.1);
}

.model-hint .el-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.model-hint .el-tag:hover {
  background: var(--eify-primary);
  color: white;
  border-color: var(--eify-primary);
}

/* ========== MCP 工具 ========== */
.empty-tools {
  padding: 40px 0;
}

.tools-wrapper {
  max-height: 360px;
  overflow-y: auto;
}

.tools-hint {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  margin-bottom: 16px;
  padding: 8px 12px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(139, 92, 246, 0.05) 100%);
  border-radius: var(--eify-radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.1);
}

.tool-group {
  margin-bottom: 16px;
}

.tool-group-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--eify-primary);
  padding: 6px 12px;
  background: rgba(99, 102, 241, 0.06);
  border-radius: var(--eify-radius-sm);
  margin-bottom: 8px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--eify-radius-sm);
  transition: background 0.2s;
}

.tool-item:hover {
  background: var(--eify-bg-surface);
}

.tool-item .el-checkbox {
  flex-shrink: 0;
  min-width: 0;
}

.tool-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--eify-text-primary);
  white-space: nowrap;
}

.tool-desc {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

/* ========== RAG 配置 ========== */
.kb-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.form-hint {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  margin-top: 4px;
  line-height: 1.5;
}

/* ========== 卡片视图 ========== */
.agent-card {
  background: var(--eify-bg-base);
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: hidden;
  transition: var(--eify-transition-base);
  border: 1px solid var(--eify-border-subtle);
}

.agent-card:hover {
  box-shadow: var(--eify-shadow-lg);
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.2);
}

.agent-card.disabled {
  opacity: 0.7;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: var(--eify-spacing-3);
  padding: var(--eify-spacing-4);
  border-bottom: 1px solid var(--eify-border-subtle);
  background: linear-gradient(180deg, var(--eify-bg-surface) 0%, var(--eify-bg-base) 100%);
  min-height: 64px;
}

.card-avatar-placeholder {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  flex-shrink: 0;
  font-size: 18px;
}

.card-title {
  flex: 1;
}

.card-title h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0 0 var(--eify-spacing-1) 0;
}

.card-status {
  flex-shrink: 0;
}

.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
}

.indicator-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  position: relative;
}

.status-indicator.enabled .indicator-dot {
  background: var(--eify-success);
  box-shadow: 0 0 12px rgba(34, 197, 94, 0.6);
}

.status-indicator:not(.enabled) .indicator-dot {
  background: var(--eify-text-tertiary);
}

.status-indicator.enabled .indicator-dot::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--eify-success);
  opacity: 0.3;
  animation: indicator-pulse 2s ease-in-out infinite;
}

@keyframes indicator-pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 0.3;
  }
  50% {
    transform: scale(1.6);
    opacity: 0;
  }
}

.card-body {
  padding: var(--eify-spacing-4);
}

.card-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-1) 0;
  font-size: 12px;
}

.info-label {
  color: var(--eify-text-tertiary);
  flex-shrink: 0;
}

.info-value {
  color: var(--eify-text-secondary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.info-value.prompt-tooltip {
  cursor: default;
  border-bottom: 1px dashed var(--eify-border-default);
  transition: var(--eify-transition-base);
}

.info-value.prompt-tooltip:hover {
  color: var(--eify-primary);
  border-bottom-color: var(--eify-primary);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
  border-top: 1px solid var(--eify-border-subtle);
  background: var(--eify-bg-surface);
  min-height: 48px;
}

.action-buttons {
  display: flex;
  gap: var(--eify-spacing-2);
}

.action-buttons .el-button {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
}

.action-buttons .el-button .el-icon {
  font-size: 14px;
}

/* ========== 测试对话对话框 ========== */
.test-chat-container {
  display: flex;
  flex-direction: column;
  height: 560px;
}

/* 工具栏 */
.chat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--eify-bg-subtle);
  border: 1px solid var(--eify-border-default);
  border-radius: var(--eify-radius-md) var(--eify-radius-md) 0 0;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  gap: 6px;
}

.toolbar-left .el-button {
  font-size: 12px;
  height: 30px;
  padding: 0 10px;
}

.toolbar-right .agent-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right .agent-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--eify-text-secondary);
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--eify-bg-subtle);
  border-left: 1px solid var(--eify-border-default);
  border-right: 1px solid var(--eify-border-default);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-messages::-webkit-scrollbar { width: 5px; }
.chat-messages::-webkit-scrollbar-track { background: transparent; }
.chat-messages::-webkit-scrollbar-thumb { background: var(--eify-gray-300); border-radius: 10px; }
.chat-messages::-webkit-scrollbar-thumb:hover { background: var(--eify-gray-400); }

/* ========== 消息气泡 ========== */
.message {
  display: flex;
  gap: 10px;
  max-width: 85%;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message.assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 13px;
}

.user-avatar {
  background: var(--eify-primary);
  color: var(--eify-text-inverse);
}

.assistant-avatar {
  background: var(--eify-gradient-primary);
  color: var(--eify-text-inverse);
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.6;
  word-break: break-word;
  min-width: 0;
}

.user-bubble {
  background: var(--eify-primary);
  color: var(--eify-text-inverse);
  border-bottom-right-radius: 4px;
}

.user-bubble .message-time {
  text-align: right;
  opacity: 0.7;
}

.ai-bubble {
  background: var(--eify-bg-base);
  color: var(--eify-text-primary);
  border: 1px solid var(--eify-border-default);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.error-bubble {
  background: var(--eify-error-light);
  border-color: var(--eify-error-200);
}

.welcome-bubble {
  background: var(--eify-bg-secondary);
  border-style: dashed;
  font-style: italic;
  color: var(--eify-text-secondary);
}

.loading-bubble {
  padding: 14px 18px;
}

.error-content {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--eify-error-600);
  font-size: 13px;
}

.error-icon {
  flex-shrink: 0;
  color: var(--eify-error-600);
}

/* 消息元信息行（操作按钮 + 时间/统计 同行） */
.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  margin-top: 6px;
}

.user-meta {
  color: rgba(255, 255, 255, 0.65);
}

.ai-meta {
  color: var(--eify-text-tertiary);
}

/* 消息统计（Token/延迟） */
.message-stats {
  white-space: nowrap;
  flex-shrink: 0;
  margin-left: auto;
}

/* ========== 消息时间 ========== */
.message-time {
  font-size: 11px;
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
  animation: cursor-blink 1s infinite;
}

@keyframes cursor-blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}

/* ========== Markdown 渲染 ========== */
.markdown-body {
  font-size: 14px;
  line-height: 1.65;
}

.markdown-body :deep(p) { margin: 0 0 0.35em 0; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 0.3em 0; padding-left: 1.5em; }
.markdown-body :deep(li) { margin-bottom: 0.1em; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--eify-primary);
  padding: 0.2em 0 0.2em 0.8em;
  margin: 0.4em 0;
  color: var(--eify-text-secondary);
}
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 0.1em 0.3em;
  border-radius: 3px;
  font-family: 'SF Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 0.88em;
}
.markdown-body :deep(pre) {
  background: var(--eify-gray-800);
  padding: 0.7em 0.9em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.4em 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--eify-gray-200);
  font-size: 0.85em;
}
.markdown-body :deep(a) { color: var(--eify-primary); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin: 0.5em 0 0.25em 0;
  line-height: 1.3;
}
.markdown-body :deep(*:last-child) { margin-bottom: 0 !important; }

/* ========== 加载动画 ========== */
.typing-indicator {
  display: flex;
  gap: 4px;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--eify-text-tertiary);
  animation: typing-dot 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing-dot {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* ========== 输入区域 ========== */
.chat-input-area {
  border: 1px solid var(--eify-border-default);
  border-top: none;
  border-radius: 0 0 var(--eify-radius-md) var(--eify-radius-md);
  padding: 14px 16px 16px;
  background: var(--eify-bg-base);
  flex-shrink: 0;
}

.quick-prompts {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.prompt-label {
  font-size: 11px;
  color: var(--eify-text-quaternary);
  flex-shrink: 0;
}

.prompt-tag {
  cursor: pointer;
  transition: all 0.15s;
  font-size: 11px;
}

.prompt-tag:hover {
  background: var(--eify-primary);
  color: var(--eify-text-inverse);
  border-color: var(--eify-primary);
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-wrapper .el-textarea {
  flex: 1;
}

.input-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-shrink: 0;
}

/* 快速提示 */
.quick-prompts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--eify-spacing-2);
  padding: var(--eify-spacing-2);
  background: var(--eify-bg-surface);
  border-radius: var(--eify-radius-md);
  border: 1px solid var(--eify-border-subtle);
}

.prompt-label {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  margin-right: var(--eify-spacing-1);
}

.prompt-tag {
  cursor: pointer;
  transition: var(--eify-transition-base);
  border: 1px solid var(--eify-border-subtle);
}

.prompt-tag:hover {
  background: var(--eify-primary);
  color: white;
  border-color: var(--eify-primary);
}

/* 输入区域包装器 */
.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--eify-spacing-2);
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--eify-spacing-2);
}

/* Markdown 渲染样式 */
:deep(.message-content) h1,
:deep(.message-content) h2,
:deep(.message-content) h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

:deep(.message-content) p {
  margin: 0 0 8px 0;
}

:deep(.message-content) code {
  background: var(--eify-bg-surface);
  padding: 2px 4px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

:deep(.message-content) pre {
  background: var(--eify-bg-surface);
  padding: 12px;
  border-radius: var(--eify-radius-md);
  margin: 8px 0;
  overflow-x: auto;
}

:deep(.message-content) blockquote {
  border-left: 3px solid var(--eify-primary);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--eify-text-secondary);
}
</style>
