<template>
  <div class="document-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button class="back-btn" @click="router.push('/knowledge')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.back') }}
        </el-button>
        <div class="header-title">
          <h2 class="text-2xl">{{ knowledgeName }}</h2>
          <span class="header-desc text-sm">{{ t('document.title') }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-button type="primary" class="eify-btn-primary-gradient" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          {{ t('knowledge.uploadDocument') }}
        </el-button>
      </div>
    </div>

    <!-- 统计信息 -->
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-value text-2xl">{{ stats.total }}</span>
        <span class="stat-label text-xs">{{ t('document.totalDocuments') }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-value completed">{{ stats.completed }}</span>
        <span class="stat-label text-xs">{{ t('document.completed') }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-value processing">{{ stats.processing }}</span>
        <span class="stat-label text-xs">{{ t('document.processing') }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-value failed">{{ stats.failed }}</span>
        <span class="stat-label text-xs">{{ t('document.failed') }}</span>
      </div>
    </div>

    <!-- 文档列表 -->
    <div class="document-table-wrapper">
      <el-table
        :data="documents"
        v-loading="loading"
        stripe
        style="width: 100%"
        :header-cell-style="{ background: 'var(--eify-bg-surface)', color: 'var(--eify-text-primary)', fontWeight: 600 }"
      >
        <el-table-column prop="originalName" :label="t('document.fileName')" min-width="200">
          <template #default="{ row }">
            <div class="file-name-cell">
              <div class="file-icon text-xl" :class="getFileTypeClass(row.fileType)">
                <el-icon><Document /></el-icon>
              </div>
              <div class="file-info">
                <div class="file-name">{{ row.originalName }}</div>
                <div class="file-meta text-xs">{{ row.fileType }} · {{ formatFileSize(row.fileSize) }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="charCount" :label="t('document.charCount')" width="100">
          <template #default="{ row }">
            <span>{{ row.charCount != null ? row.charCount.toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="chunkCount" :label="t('document.chunkCount')" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="plain">{{ row.chunkCount || 0 }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="processStatus" :label="t('document.processStatus')" width="120">
          <template #default="{ row }">
            <el-tag :type="ProcessStatusType[row.processStatus]" size="small" effect="plain">
              {{ t(ProcessStatusLabel[row.processStatus]) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="errorMessage" :label="t('document.errorMessage')" min-width="180">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
              <span class="error-message text-xs">{{ row.errorMessage }}</span>
            </el-tooltip>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" :label="t('document.uploadTime')" width="170" />

        <el-table-column :label="t('common.actions')" width="340" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-action-buttons">
              <el-button
                size="small"
                type="primary"
                class="text-xs"
                @click="handlePreview(row)"
                :disabled="row.processStatus !== 2"
              >
                <el-icon><View /></el-icon>
                {{ t('document.preview') }}
              </el-button>
              <el-button
                size="small"
                type="warning"
                class="text-xs"
                @click="handleReprocess(row)"
                :loading="reprocessingId === row.id"
                :disabled="row.processStatus === 1"
              >
                <el-icon><Refresh /></el-icon>
                {{ t('document.reprocess') }}
              </el-button>
              <el-button size="small" type="danger" class="text-xs" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      :title="t('knowledge.uploadDocument')"
      width="560px"
      :close-on-click-modal="false"
      @close="handleUploadDialogClose"
    >
      <div class="upload-content">
        <el-upload
          drag
          :auto-upload="false"
          :file-list="uploadFileList"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :before-upload="beforeUpload"
          multiple
          accept=".txt,.md,.pdf,.doc,.docx,.csv,.json"
        >
          <div class="upload-area">
            <el-icon class="upload-icon"><Upload /></el-icon>
            <div class="upload-text text-base">{{ t('document.uploadDragText') }}<span class="upload-link">{{ t('document.uploadClickText') }}</span></div>
            <div class="upload-hint text-xs">{{ t('document.uploadHint') }}</div>
          </div>
        </el-upload>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showUploadDialog = false">{{ t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            class="eify-btn-primary-gradient"
            @click="handleUpload"
            :loading="uploading"
            :disabled="uploadFileList.length === 0"
          >
            {{ t('document.uploadBtn', { count: uploadFileList.length }) }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 文档预览 -->
    <DocumentPreview
      v-model:visible="showPreview"
      :document-id="previewDocumentId"
      :document-name="previewDocumentName"
      :file-type="previewDocumentFileType"
    />
  </div>

  <ConfirmDialog
    :show="showDeleteConfirm"
    :title="t('common.confirmDeleteTitle')"
    :message="deleteTarget ? t('common.confirmDelete') : ''"
    type="danger"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Upload,
  Document,
  Refresh,
  Delete,
  View
} from '@element-plus/icons-vue'
import {
  knowledgeApi,
  ProcessStatusLabel,
  ProcessStatusType,
  type DocumentResponse
} from '@/api/knowledge'
import DocumentPreview from '@/components/DocumentPreview.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import type { UploadFile, UploadFiles } from 'element-plus'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const knowledgeId = computed(() => Number(route.params.id))
const knowledgeName = ref('')
const loading = ref(false)
const documents = ref<DocumentResponse[]>([])

// 上传相关
const showUploadDialog = ref(false)
const uploadFileList = ref<UploadFile[]>([])
const uploading = ref(false)

// 操作状态
const reprocessingId = ref<number | null>(null)

const showDeleteConfirm = ref(false)
const deleteTarget = ref<DocumentResponse | null>(null)

// 预览相关
const showPreview = ref(false)
const previewDocumentId = ref<number | null>(null)
const previewDocumentName = ref('')
const previewDocumentFileType = ref('')

/* ========== 统计 ========== */

const stats = computed(() => {
  const total = documents.value.length
  const completed = documents.value.filter(d => d.processStatus === 2).length
  const processing = documents.value.filter(d => d.processStatus === 1).length
  const failed = documents.value.filter(d => d.processStatus === 3).length
  return { total, completed, processing, failed }
})

/* ========== 工具方法 ========== */

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const getFileTypeClass = (fileType: string): string => {
  const type = fileType?.toLowerCase() || ''
  if (type.includes('pdf')) return 'file-pdf'
  if (type.includes('doc')) return 'file-word'
  if (type.includes('txt') || type.includes('md')) return 'file-text'
  if (type.includes('csv') || type.includes('json')) return 'file-data'
  return 'file-default'
}

/* ========== 数据加载 ========== */

const loadDocuments = async () => {
  loading.value = true
  try {
    const result = await knowledgeApi.getDocuments(knowledgeId.value)
    documents.value = ((result as any).list || (result as any).records || result || [])
  } catch (error: any) {
    ElMessage.error(t('document.loadListFailed'))
  } finally {
    loading.value = false
  }
}

const loadKnowledgeName = async () => {
  try {
    const kb = await knowledgeApi.getKnowledge(knowledgeId.value)
    knowledgeName.value = kb.name
  } catch {
    // ignore
  }
}

/* ========== 上传处理 ========== */

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  uploadFileList.value = [...fileList]
}

const handleFileRemove = (_file: UploadFile, fileList: UploadFiles) => {
  uploadFileList.value = [...fileList]
}

const beforeUpload = (file: File) => {
  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    ElMessage.warning(t('document.fileTooLarge', { name: file.name }))
    return false
  }
  return true
}

const handleUpload = async () => {
  if (uploadFileList.value.length === 0) return

  uploading.value = true
  let successCount = 0
  let failCount = 0

  for (const fileItem of uploadFileList.value) {
    try {
      await knowledgeApi.uploadDocument(knowledgeId.value, fileItem.raw!)
      successCount++
    } catch {
      failCount++
    }
  }

  uploading.value = false
  showUploadDialog.value = false

  if (successCount > 0) {
    ElMessage.success(t('document.uploadSuccess', { count: successCount }))
  }
  if (failCount > 0) {
    ElMessage.warning(t('document.uploadPartial', { count: failCount }))
  }

  loadDocuments()
}

const handleUploadDialogClose = () => {
  uploadFileList.value = []
}

/* ========== 操作处理 ========== */

const handlePreview = (row: DocumentResponse) => {
  previewDocumentId.value = row.id
  previewDocumentName.value = row.originalName
  previewDocumentFileType.value = row.fileType || ''
  showPreview.value = true
}

const handleReprocess = async (row: DocumentResponse) => {
  try {
    reprocessingId.value = row.id
    await knowledgeApi.reprocessDocument(row.id)
    ElMessage.success(t('document.reprocessSubmitted'))
    loadDocuments()
  } catch {
    ElMessage.error(t('document.reprocessFailed'))
  } finally {
    reprocessingId.value = null
  }
}

const handleDelete = (row: DocumentResponse) => {
  deleteTarget.value = row
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value) return
  try {
    await knowledgeApi.deleteDocument(deleteTarget.value.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadDocuments()
  } finally {
    showDeleteConfirm.value = false
    deleteTarget.value = null
  }
}

const cancelDelete = () => {
  showDeleteConfirm.value = false
  deleteTarget.value = null
}

/* ========== 初始化 ========== */

onMounted(() => {
  loadKnowledgeName()
  loadDocuments()
})
</script>

<style scoped>
.document-page {
  padding: var(--eify-spacing-6);
  max-width: 1680px;
  margin: 0 auto;
}

@media (min-width: 1920px) {
  .document-page {
    max-width: 1800px;
  }
}

@media (min-width: 1200px) and (max-width: 1439px) {
  .document-page {
    max-width: 1400px;
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .document-page {
    max-width: 1100px;
  }
}

@media (max-width: 768px) {
  .document-page {
    padding: var(--eify-spacing-4);
    max-width: 100%;
  }
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--eify-spacing-6);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-4);
}

.back-btn {
  height: 36px;
}

.header-title h2 {
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0;
}

.header-desc {
  color: var(--eify-text-tertiary);
}

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

/* 统计栏 */
.stats-bar {
  display: flex;
  gap: var(--eify-spacing-6);
  margin-bottom: var(--eify-spacing-6);
  padding: var(--eify-spacing-4) var(--eify-spacing-5);
  background: var(--eify-bg-surface);
  border-radius: var(--eify-card-radius);
  border: 1px solid var(--eify-border-subtle);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-weight: 700;
  color: var(--eify-text-primary);
}

.stat-value.completed {
  color: var(--eify-success);
}

.stat-value.processing {
  color: var(--eify-warning);
}

.stat-value.failed {
  color: var(--eify-danger);
}

.stat-label {
  color: var(--eify-text-tertiary);
}

/* 表格 */
.document-table-wrapper {
  background: var(--eify-bg-surface);
  border-radius: var(--eify-card-radius);
  border: 1px solid var(--eify-border-subtle);
  overflow: hidden;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.file-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon.file-pdf {
  background: rgba(239, 68, 68, 0.1);
  color: var(--eify-error);
}

.file-icon.file-word {
  background: rgba(59, 130, 246, 0.1);
  color: var(--eify-info-500);
}

.file-icon.file-text {
  background: rgba(34, 197, 94, 0.1);
  color: var(--eify-success);
}

.file-icon.file-data {
  background: rgba(168, 85, 247, 0.1);
  color: #a855f7;
}

.file-icon.file-default {
  background: rgba(99, 102, 241, 0.1);
  color: var(--eify-primary);
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-weight: 500;
  color: var(--eify-text-primary);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  color: var(--eify-text-tertiary);
}

.error-message {
  color: var(--eify-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 180px;
}

.text-muted {
  color: var(--eify-text-tertiary);
}

.table-action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--eify-spacing-1);
}

.table-action-buttons .el-button {
  height: 28px;
  padding: 0 10px;
}

.table-action-buttons .el-button .el-icon {
  font-size: 13px;
  margin-right: 2px;
}

/* 上传对话框 */
.upload-content {
  padding: var(--eify-spacing-2) 0;
}

.upload-content :deep(.el-upload) {
  width: 100%;
}

.upload-content :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: var(--eify-card-radius);
  border: 2px dashed var(--eify-border-subtle);
  transition: var(--eify-transition-base);
}

.upload-content :deep(.el-upload-dragger:hover) {
  border-color: var(--eify-primary);
}

.upload-area {
  padding: var(--eify-spacing-8) 0;
  text-align: center;
}

.upload-icon {
  font-size: 48px;
  color: var(--eify-primary);
  margin-bottom: var(--eify-spacing-3);
}

.upload-text {
  color: var(--eify-text-secondary);
  margin-bottom: var(--eify-spacing-2);
}

.upload-link {
  color: var(--eify-primary);
  font-style: normal;
  cursor: pointer;
}

.upload-hint {
  color: var(--eify-text-tertiary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--eify-spacing-2);
}
</style>
