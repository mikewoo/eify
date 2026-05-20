<template>
  <div class="wf-node" :class="{ selected: props.selected }" :style="{ '--wf-node-color': '#f97316' }">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node-icon">
      <el-icon><Connection /></el-icon>
    </div>
    <div class="wf-node-label">{{ data.name || t('workflow.nodeTypes.api') }}</div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Handle, Position } from '@vue-flow/core'
import { Connection } from '@element-plus/icons-vue'
import type { NodeProps } from '@vue-flow/core'

const { t } = useI18n()
const props = defineProps<NodeProps>()
const data = props.data as { name: string; type: string; config: Record<string, any> }
</script>

<style scoped>@import './node.css';</style>
