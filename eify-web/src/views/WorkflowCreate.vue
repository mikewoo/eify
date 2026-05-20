<template>
  <div class="workflow-create">
    <div class="create-header">
      <el-button :icon="ArrowLeft" @click="handleCancel">{{ t('common.back') }}</el-button>
      <h2 class="create-title">{{ t('workflow.addWorkflow') }}</h2>
    </div>

    <div class="create-body">
      <div class="create-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="create-form">
          <el-form-item :label="t('workflow.workflowName')" prop="name">
            <el-input
              v-model="form.name"
              :placeholder="t('workflow.workflowNamePlaceholder')"
              maxlength="100"
              show-word-limit
              size="large"
            />
          </el-form-item>

          <el-form-item :label="t('common.status')">
            <el-select v-model="form.status" style="width: 200px">
              <el-option :label="t('workflow.statusDraft')" :value="0" />
              <el-option :label="t('workflow.statusPublished')" :value="1" />
            </el-select>
          </el-form-item>

          <el-form-item :label="t('workflow.workflowDesc')">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              :placeholder="t('workflow.descPlaceholder')"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item :label="t('workflow.variableDefinition')">
            <el-table :data="form.variables" border size="small" class="kv-table">
              <el-table-column :label="t('workflow.variableName')" width="140">
                <template #default="{ row, $index }">
                  <el-input v-model="row.key" :placeholder="t('workflow.variableName')" size="small" @blur="onVarKeyBlur($index)" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.type')" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small" style="width: 100%">
                    <el-option label="string" value="string" />
                    <el-option label="number" value="number" />
                    <el-option label="boolean" value="boolean" />
                    <el-option label="object" value="object" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('workflow.varRequired')" width="65" align="center">
                <template #default="{ row }">
                  <el-switch v-model="row.required" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('workflow.defaultValue')" min-width="130">
                <template #default="{ row }">
                  <el-input v-model="row.defaultVal" :placeholder="t('workflow.defaultValueOptional')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="60" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" :icon="Delete" circle size="small" @click="form.variables.splice($index, 1)" />
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" :icon="Plus" style="margin-top: 8px" @click="addVariable">
              {{ t('workflow.addVariable') }}
            </el-button>
          </el-form-item>

          <div class="create-actions">
            <el-button size="large" @click="handleCancel">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" size="large" @click="handleNext">{{ t('workflow.nextStep') }}</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Plus, Delete } from '@element-plus/icons-vue'

const router = useRouter()
const { t } = useI18n()
const formRef = ref<FormInstance>()

interface VariableItem {
  key: string
  type: string
  required: boolean
  defaultVal: string
}

const form = reactive({
  name: '',
  status: 0,
  description: '',
  variables: [] as VariableItem[]
})

const rules: FormRules = {
  name: [
    { required: true, message: () => t('workflow.workflowNameRequired'), trigger: 'blur' },
    { max: 100, message: () => t('workflow.nameMaxLength'), trigger: 'blur' }
  ]
}

function addVariable() {
  form.variables.push({ key: '', type: 'string', required: false, defaultVal: '' })
}

function onVarKeyBlur(index: number) {
  const item = form.variables[index]
  if (item && !item.key) {
    item.key = `var${index + 1}`
  }
}

function handleCancel() {
  router.push('/workflows')
}

function handleNext() {
  if (!formRef.value) return
  formRef.value.validate().then(valid => {
    if (!valid) return
    sessionStorage.setItem('workflowDraft', JSON.stringify({
      name: form.name,
      status: form.status,
      description: form.description || '',
      variables: form.variables.filter(v => v.key)
    }))
    router.push('/workflows/new')
  })
}
</script>

<style scoped>
.workflow-create {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.create-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--eify-bg-surface, #fff);
  border-bottom: 1px solid var(--eify-border-subtle, #e5e7eb);
  flex-shrink: 0;
}

.create-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--eify-text-primary, #1f2937);
}

.create-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  justify-content: center;
}

.create-card {
  width: 100%;
  max-width: 720px;
  background: var(--eify-bg-surface, #fff);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  padding: 32px;
  align-self: flex-start;
}

.create-form {
  width: 100%;
}

.create-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--eify-border-subtle, #e5e7eb);
}

.kv-table {
  width: 100%;
}
</style>
