<template>
  <div class="wf-node" :class="{ selected: props.selected }" :style="{ '--wf-node-color': '#ef4444' }">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node-icon">
      <el-icon><SwitchFilled /></el-icon>
    </div>
    <div class="wf-node-label">{{ data.name || t('workflow.nodeTypes.end') }}</div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Handle, Position } from '@vue-flow/core'
import { SwitchFilled } from '@element-plus/icons-vue'
import type { NodeProps } from '@vue-flow/core'

const { t } = useI18n()
const props = defineProps<NodeProps>()
const data = props.data as { name: string; type: string; config: Record<string, any> }
</script>

<style scoped>@import './node.css';</style>
