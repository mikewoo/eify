<template>
  <div class="node-panel">
    <div class="node-panel-header">
      <h3 class="text-base">{{ t('workflow.nodeTypesTitle') }}</h3>
      <span class="node-panel-hint text-xs">{{ t('workflow.dragHint') }}</span>
    </div>
    <div class="node-panel-list">
      <div
        v-for="item in nodeTypes"
        :key="item.type"
        class="node-panel-item"
        :style="{ '--item-color': item.color }"
        draggable="true"
        @dragstart="onDragStart($event, item)"
      >
        <div class="node-panel-item-icon">
          <component :is="item.icon" :size="18" />
        </div>
        <div class="node-panel-item-info">
          <span class="node-panel-item-name text-sm">{{ t(item.labelKey) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VideoPlay, SwitchFilled, Cpu, Connection, Guide, Edit, SetUp } from '@element-plus/icons-vue'

const { t } = useI18n()

interface NodeTypeItem {
  type: string
  labelKey: string
  color: string
  icon: any
}

const nodeTypeKeyMap: Record<string, string> = {
  start: 'workflow.nodeTypes.start',
  end: 'workflow.nodeTypes.end',
  llm: 'workflow.nodeTypes.llm',
  api_call: 'workflow.nodeTypes.api',
  condition: 'workflow.nodeTypes.condition',
  code: 'workflow.nodeTypes.code',
  tool_call: 'workflow.nodeTypes.tool',
}

const nodeTypes: NodeTypeItem[] = [
  { type: 'start', labelKey: 'workflow.nodeTypes.start', color: '#22c55e', icon: VideoPlay },
  { type: 'end', labelKey: 'workflow.nodeTypes.end', color: '#ef4444', icon: SwitchFilled },
  { type: 'llm', labelKey: 'workflow.nodeTypes.llm', color: '#8b5cf6', icon: Cpu },
  { type: 'api_call', labelKey: 'workflow.nodeTypes.api', color: '#f97316', icon: Connection },
  { type: 'condition', labelKey: 'workflow.nodeTypes.condition', color: '#eab308', icon: Guide },
  { type: 'code', labelKey: 'workflow.nodeTypes.code', color: '#3b82f6', icon: Edit },
  { type: 'tool_call', labelKey: 'workflow.nodeTypes.tool', color: '#06b6d4', icon: SetUp },
]

function getNodeLabel(type: string): string {
  const key = nodeTypeKeyMap[type]
  return key ? t(key) : type
}

function onDragStart(event: DragEvent, item: NodeTypeItem) {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('application/vueflow', item.type)
  event.dataTransfer.effectAllowed = 'move'
}

defineExpose({ getNodeLabel })
</script>

<style scoped>
.node-panel {
  width: 220px;
  height: 100%;
  background: var(--eify-bg-base);
  border-right: 1px solid var(--eify-border-default);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.node-panel-header {
  padding: 16px;
  border-bottom: 1px solid var(--eify-border-default);
}

.node-panel-header h3 {
  margin: 0 0 4px;
  font-weight: 600;
  color: var(--eify-text-primary);
}

.node-panel-hint {
  color: var(--eify-text-tertiary);
}

.node-panel-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.node-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.node-panel-item:hover {
  background: var(--eify-bg-surface);
}

.node-panel-item:active {
  cursor: grabbing;
}

.node-panel-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--item-color, var(--eify-primary));
  color: var(--eify-text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.node-panel-item-info {
  display: flex;
  flex-direction: column;
}

.node-panel-item-name {
  font-weight: 600;
  color: var(--eify-text-primary);
}

.node-panel-item-desc {
  color: var(--eify-text-tertiary);
}
</style>
