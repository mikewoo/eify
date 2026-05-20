<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="t('document.preview')"
    width="90vw"
    :close-on-click-modal="false"
    draggable
    destroy-on-close
    top="5vh"
    class="document-preview-dialog"
    @opened="loadData"
    @closed="handleClose"
  >
    <div class="preview-container" v-loading="loading">
      <!-- 左侧：原始文档（内部滚动） -->
      <div class="preview-left">
        <div class="panel-header">
          <span class="panel-title">{{ t('document.rawDocument') }}</span>
          <span class="panel-subtitle">{{ documentName }}</span>
        </div>
        <div class="panel-body" ref="documentContentRef">
          <!-- 富文本渲染（MD/DOCX） -->
          <div
            v-if="isStyledDoc && renderedHtml"
            class="rich-content"
            v-html="renderedHtml"
          />
          <!-- 富文本渲染失败时回退显示原始文本 -->
          <div
            v-else-if="isStyledDoc && !renderedHtml && documentContent"
            class="raw-text"
          >{{ documentContent }}</div>
          <!-- 纯文本渲染（TXT/PDF） -->
          <template v-else-if="!isStyledDoc">
            <div
              v-for="(segment, index) in textSegments"
              :key="index"
              class="text-segment"
              :class="{ 'text-highlight': segment.chunkIndex === activeChunkIndex }"
            >
              {{ segment.text }}
            </div>
          </template>
          <el-empty v-if="!loading && !documentContent" :description="t('document.noContent')" />
        </div>
      </div>

      <!-- 右侧：分块列表（分页） -->
      <div class="preview-right">
        <div class="panel-header">
          <span class="panel-title">{{ t('document.chunkList') }}</span>
          <span class="panel-subtitle">{{ t('document.chunks', { count: chunks.length }) }}</span>
        </div>
        <div class="panel-body">
          <div
            v-for="chunk in pagedChunks"
            :key="chunk.id"
            class="chunk-card"
            :class="{ 'chunk-active': activeChunkId === chunk.id }"
            @click="handleChunkClick(chunk)"
          >
            <div class="chunk-header">
              <span class="chunk-index">#{{ chunk.chunkIndex }}</span>
              <span class="chunk-length">{{ t('document.chars', { count: chunk.content.length }) }}</span>
            </div>
            <div class="chunk-text">{{ chunk.content }}</div>
          </div>
          <el-empty v-if="!loading && chunks.length === 0" :description="t('document.noChunks')" />
        </div>
        <div class="panel-footer" v-if="chunks.length > pageSize">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="chunks.length"
            layout="prev, pager, next"
            small
            :pager-count="5"
          />
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { knowledgeApi, type DocumentChunkResponse } from '@/api/knowledge'
import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

interface TextSegment {
  text: string
  chunkIndex: number
  startOffset: number
}

const props = defineProps<{
  visible: boolean
  documentId: number | null
  documentName: string
  fileType: string
}>()

defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()

const loading = ref(false)
const documentContent = ref('')
const chunks = ref<DocumentChunkResponse[]>([])
const activeChunkId = ref<number | null>(null)
const activeChunkIndex = ref<number>(-1)
const documentContentRef = ref<HTMLElement | null>(null)
const textSegments = ref<TextSegment[]>([])

// 分块分页
const currentPage = ref(1)
const pageSize = 10
const pagedChunks = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return chunks.value.slice(start, start + pageSize)
})

// 是否为富文本文档
const isStyledDoc = computed(() => {
  const ft = props.fileType?.toLowerCase() || ''
  return ft === 'md' || ft === 'doc' || ft === 'docx'
})

// Markdown 渲染器
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

// 渲染后的 HTML（MD/DOCX 用）
const renderedHtml = computed(() => {
  if (!documentContent.value) return ''
  const ft = props.fileType?.toLowerCase() || ''
  if (ft === 'md') {
    try {
      return DOMPurify.sanitize(markedInstance.parse(documentContent.value) as string)
    } catch (e) {
      console.error('Markdown 渲染失败:', e)
      return ''
    }
  }
  if (ft === 'doc' || ft === 'docx') {
    return DOMPurify.sanitize(documentContent.value) // 后端已返回 HTML
  }
  return ''
})

const loadData = async () => {
  if (!props.documentId) return
  loading.value = true
  try {
    const [content, chunkList] = await Promise.all([
      knowledgeApi.getDocumentContent(props.documentId),
      knowledgeApi.getDocumentChunks(props.documentId)
    ])
    documentContent.value = content
    chunks.value = chunkList
    if (!isStyledDoc.value) {
      buildSegments(content, chunkList)
    }
    currentPage.value = 1
    activeChunkId.value = null
    activeChunkIndex.value = -1
  } catch {
    ElMessage.error(t('document.loadFailed'))
  } finally {
    loading.value = false
  }
}

const buildSegments = (content: string, chunkList: DocumentChunkResponse[]) => {
  if (!content || chunkList.length === 0) {
    textSegments.value = content ? [{ text: content, chunkIndex: -1, startOffset: 0 }] : []
    return
  }

  const segments: TextSegment[] = []
  let searchStart = 0

  for (const chunk of chunkList) {
    const anchor = chunk.content.substring(0, Math.min(80, chunk.content.length))
    const idx = content.indexOf(anchor, searchStart)

    if (idx === -1) {
      const shortAnchor = chunk.content.substring(0, Math.min(30, chunk.content.length))
      const shortIdx = content.indexOf(shortAnchor, searchStart)
      if (shortIdx !== -1) {
        if (shortIdx > searchStart) {
          segments.push({ text: content.substring(searchStart, shortIdx), chunkIndex: -1, startOffset: searchStart })
        }
        const end = Math.min(shortIdx + chunk.content.length, content.length)
        segments.push({ text: content.substring(shortIdx, end), chunkIndex: chunk.chunkIndex, startOffset: shortIdx })
        searchStart = end
      }
    } else {
      if (idx > searchStart) {
        segments.push({ text: content.substring(searchStart, idx), chunkIndex: -1, startOffset: searchStart })
      }
      const end = Math.min(idx + chunk.content.length, content.length)
      segments.push({ text: content.substring(idx, end), chunkIndex: chunk.chunkIndex, startOffset: idx })
      searchStart = end
    }
  }

  if (searchStart < content.length) {
    segments.push({ text: content.substring(searchStart), chunkIndex: -1, startOffset: searchStart })
  }

  textSegments.value = segments
}

const handleChunkClick = (chunk: DocumentChunkResponse) => {
  if (activeChunkId.value === chunk.id) {
    activeChunkId.value = null
    activeChunkIndex.value = -1
    return
  }

  activeChunkId.value = chunk.id
  activeChunkIndex.value = chunk.chunkIndex

  if (!isStyledDoc.value) {
    nextTick(() => {
      const el = documentContentRef.value?.querySelector('.text-highlight')
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  }
}

const handleClose = () => {
  documentContent.value = ''
  chunks.value = []
  textSegments.value = []
  activeChunkId.value = null
  activeChunkIndex.value = -1
  currentPage.value = 1
}
</script>

<style>
/* 弹窗：固定 90% 屏幕，用非 scoped 确保覆盖 el-dialog */
.document-preview-dialog {
  width: 90vw !important;
  height: 90vh !important;
  max-width: 90vw !important;
  max-height: 90vh !important;
  border-radius: var(--eify-card-radius, 12px) !important;
  box-shadow: var(--eify-shadow-lg, 0 10px 15px -3px rgb(0 0 0 / 0.1)) !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
  position: relative !important;
}

.document-preview-dialog .el-dialog__header {
  padding: 16px 24px !important;
  border-bottom: 1px solid var(--eify-border-subtle, #f1f5f9) !important;
  margin-right: 0 !important;
  flex-shrink: 0 !important;
}

.document-preview-dialog .el-dialog__title {
  font-size: 16px !important;
  font-weight: 600 !important;
  color: var(--eify-text-primary, #0f172a) !important;
}

.document-preview-dialog .el-dialog__body {
  padding: 0 !important;
  flex: 1 !important;
  min-height: 0 !important;
  overflow: hidden !important;
  display: flex !important;
  flex-direction: column !important;
}
</style>

<style scoped>

/* 双栏容器 */
.preview-container {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 1px;
  background: var(--eify-border-subtle);
  overflow: hidden;
}

.preview-left,
.preview-right {
  display: flex;
  flex-direction: column;
  background: var(--eify-bg-surface);
  overflow: hidden;
}

.preview-left {
  flex: 6;
  min-width: 0;
}

.preview-right {
  flex: 4;
  min-width: 0;
}

/* 面板头部 */
.panel-header {
  display: flex;
  align-items: baseline;
  gap: var(--eify-spacing-3);
  padding: var(--eify-spacing-3) var(--eify-spacing-5);
  border-bottom: 1px solid var(--eify-border-subtle);
  flex-shrink: 0;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--eify-text-primary);
}

.panel-subtitle {
  font-size: 12px;
  color: var(--eify-text-tertiary);
}

/* 面板内容区：固定窗格，内部滚动 */
.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.panel-body::-webkit-scrollbar {
  width: 6px;
}

.panel-body::-webkit-scrollbar-thumb {
  background: var(--eify-gray-300, #cbd5e1);
  border-radius: 3px;
}

/* 左侧文档 — 纯文本 */
.preview-left .panel-body {
  padding: var(--eify-spacing-4) var(--eify-spacing-5);
  font-size: 13px;
  line-height: 1.8;
  color: var(--eify-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.text-segment {
  padding: 2px 4px;
  border-radius: 4px;
  transition: background-color 0.3s ease;
}

.text-highlight {
  background-color: rgba(99, 102, 241, 0.12);
  border-left: 3px solid var(--eify-primary);
  padding-left: 12px;
}

/* ========================================
   富文本渲染内容样式（MD/DOCX）
   ======================================== */

.rich-content {
  font-size: 15px;
  line-height: 1.85;
  color: var(--eify-text-primary, #1e293b);
  white-space: normal;
  word-break: break-word;
}

.raw-text {
  font-size: 13px;
  line-height: 1.8;
  color: var(--eify-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.rich-content :deep(h1) {
  font-size: 1.75em;
  font-weight: 700;
  margin: 1.2em 0 0.6em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--eify-border-subtle, #e2e8f0);
  color: var(--eify-text-primary, #0f172a);
}

.rich-content :deep(h2) {
  font-size: 1.4em;
  font-weight: 700;
  margin: 1.1em 0 0.5em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid var(--eify-border-subtle, #e2e8f0);
  color: var(--eify-text-primary, #0f172a);
}

.rich-content :deep(h3) {
  font-size: 1.2em;
  font-weight: 600;
  margin: 1em 0 0.4em;
  color: var(--eify-text-primary, #1e293b);
}

.rich-content :deep(h4),
.rich-content :deep(h5),
.rich-content :deep(h6) {
  font-size: 1.05em;
  font-weight: 600;
  margin: 0.8em 0 0.3em;
  color: var(--eify-text-primary, #1e293b);
}

.rich-content :deep(p) {
  margin: 0.6em 0;
}

.rich-content :deep(a) {
  color: var(--eify-primary, #6366f1);
  text-decoration: none;
}

.rich-content :deep(a:hover) {
  text-decoration: underline;
}

.rich-content :deep(strong) {
  font-weight: 600;
  color: var(--eify-text-primary, #0f172a);
}

.rich-content :deep(em) {
  font-style: italic;
}

.rich-content :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.5em 1em;
  border-left: 4px solid var(--eify-primary, #6366f1);
  background: rgba(99, 102, 241, 0.04);
  color: var(--eify-text-secondary, #475569);
}

.rich-content :deep(blockquote p) {
  margin: 0.3em 0;
}

/* 列表 */
.rich-content :deep(ul),
.rich-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.8em;
}

.rich-content :deep(li) {
  margin: 0.2em 0;
}

.rich-content :deep(ul ul),
.rich-content :deep(ol ol),
.rich-content :deep(ul ol),
.rich-content :deep(ol ul) {
  margin: 0.2em 0;
}

/* 任务列表 */
.rich-content :deep(input[type="checkbox"]) {
  margin-right: 0.4em;
}

/* 代码 */
.rich-content :deep(code) {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, monospace;
  font-size: 0.88em;
  padding: 0.15em 0.4em;
  border-radius: 4px;
  background: rgba(99, 102, 241, 0.08);
  color: #e11d48;
}

.rich-content :deep(pre) {
  margin: 0.8em 0;
  padding: 1em 1.2em;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--eify-border-subtle, #e2e8f0);
  overflow-x: auto;
}

.rich-content :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
  font-size: 0.85em;
  line-height: 1.6;
}

/* 表格 */
.rich-content :deep(table) {
  width: 100%;
  margin: 0.8em 0;
  border-collapse: collapse;
  font-size: 0.92em;
}

.rich-content :deep(th),
.rich-content :deep(td) {
  padding: 0.5em 0.8em;
  border: 1px solid var(--eify-border-subtle, #e2e8f0);
  text-align: left;
}

.rich-content :deep(th) {
  background: rgba(99, 102, 241, 0.06);
  font-weight: 600;
  color: var(--eify-text-primary, #0f172a);
}

.rich-content :deep(tr:nth-child(even) td) {
  background: rgba(241, 245, 249, 0.5);
}

/* 水平线 */
.rich-content :deep(hr) {
  margin: 1.2em 0;
  border: none;
  border-top: 1px solid var(--eify-border-subtle, #e2e8f0);
}

/* 图片 */
.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 0.6em 0;
}

/* 首个和末个元素去掉多余 margin */
.rich-content :deep(*:first-child) {
  margin-top: 0;
}

.rich-content :deep(*:last-child) {
  margin-bottom: 0;
}

/* 右侧分块列表 */
.preview-right .panel-body {
  padding: var(--eify-spacing-3);
  display: flex;
  flex-direction: column;
  gap: var(--eify-spacing-2);
}

.chunk-card {
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
  border: 1px solid var(--eify-border-subtle);
  border-radius: 8px;
  cursor: pointer;
  transition: var(--eify-transition-base);
}

.chunk-card:hover {
  border-color: var(--eify-primary);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08);
}

.chunk-active {
  border-color: var(--eify-primary);
  background: rgba(99, 102, 241, 0.04);
}

.chunk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--eify-spacing-1);
}

.chunk-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--eify-primary);
}

.chunk-length {
  font-size: 11px;
  color: var(--eify-text-tertiary);
}

.chunk-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--eify-text-secondary);
  max-height: 96px;
  overflow: hidden;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 面板底部分页 */
.panel-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: var(--eify-spacing-2) 0;
  border-top: 1px solid var(--eify-border-subtle);
}

:deep(.panel-footer .el-pagination) {
  --el-pagination-font-size: 12px;
}

:deep(.panel-footer .el-pager li) {
  min-width: 28px;
  height: 28px;
}
</style>
