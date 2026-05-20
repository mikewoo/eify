<template>
  <div class="wf-node wf-node-diamond" :class="{ selected: props.selected }" :style="{ '--wf-node-color': '#eab308' }">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node-icon">
      <el-icon><Guide /></el-icon>
    </div>
    <div class="wf-node-label">{{ data.name || t('workflow.nodeTypes.condition') }}</div>
    <Handle id="true" type="source" :position="Position.Bottom" style="left:30%" />
    <Handle id="false" type="source" :position="Position.Bottom" style="left:70%" />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Handle, Position } from '@vue-flow/core'
import { Guide } from '@element-plus/icons-vue'
import type { NodeProps } from '@vue-flow/core'

const { t } = useI18n()
const props = defineProps<NodeProps>()
const data = props.data as { name: string; type: string; config: Record<string, any> }
</script>

<style scoped>
@import './node.css';

.wf-node-diamond :deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
}
</style>
