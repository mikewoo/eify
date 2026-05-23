# DESIGN.md Compliance Fix — Specification

## Status

Approved

## Date

2026-05-23

## Owner

Claude Code

## Deciders

User

## Context

项目已创建 `DESIGN.md` 设计系统规范，但现有前端代码中存在大量硬编码颜色值、font-size、间距和圆角，未使用 `--eify-*` CSS 变量和 `.eify-*` 组件类名。通过全局扫描发现约 20 个文件需要修复，按严重程度分为 HIGH/MEDIUM/LOW 三级。

## Decision

采用 **Approach A：按文件优先级逐个修**，每修完一个批次跑 `vue-tsc --noEmit` + `vitest run` 验证。

### Scope

全修：硬编码颜色、font-size、padding/margin、border-radius。

### 豁免项

- SVG `<stop>` 标签中的颜色（不支持 CSS 变量）
- 工作流节点类型色（`--wf-node-color`，属于领域设计令牌）
- MCP Server 日志查看器的 Catppuccin 主题色
- Element Plus `:deep()` 覆盖中的 CSS 变量 fallback 值
- `LoginView.vue` 的 `#0f0f1a` 背景色（设计令牌无对应值，保留原样）
- 48px 以上大字号（Logo 专用，不适用工具类）

### 修复顺序（4 批 20 个文件）

**Batch 1 — 品牌色重灾区：** `LoginView.vue`
**Batch 2 — 共享组件：** `EifySearch.vue`, `EifyHeader.vue`, `ConfirmDialog.vue`, `EifyListPage.vue`, `EifyFormDialog.vue`, `EifyTable.vue`, `DocumentPreview.vue`
**Batch 3 — 业务页面：** `ChatView.vue`, `AgentList.vue`, `ProviderList.vue`, `McpServerList.vue`, `KnowledgeView.vue`, `DocumentView.vue`, `WorkflowList.vue`, `ProfileView.vue`
**Batch 4 — 工作流相关：** `WorkflowEdit.vue`, `WorkflowCreate.vue`, `NodePanel.vue`

### 颜色替换映射

| 硬编码 | 替换为 |
|:---|:---|
| `#ffffff` (bg) | `var(--eify-bg-base)` |
| `#fff` (text) | `var(--eify-text-inverse)` |
| `#f8fafc` | `var(--eify-bg-secondary)` |
| `#f1f5f9` | `var(--eify-bg-surface)` |
| `#fef2f2` | `var(--eify-error-light)` |
| `#fecaca` | `var(--eify-error-200)` |
| `#6366f1` | `var(--eify-primary)` |
| `#8b5cf6` | `var(--eify-primary-400)` |
| `#4f46e5` | `var(--eify-primary-600)` |
| `gradient(#6366f1, #8b5cf6)` | `var(--eify-gradient-primary)` |
| `#ef4444` | `var(--eify-error)` |
| `#dc2626` | `var(--eify-error-600)` |
| `#f59e0b` | `var(--eify-warning)` |
| `#fbbf24` | `var(--eify-warning-400)` |
| `#d97706` | `var(--eify-warning-600)` |
| `#22c55e` | `var(--eify-success)` |
| `#059669` | `var(--eify-success-600)` |
| `#3b82f6` | `var(--eify-info-500)` |
| `#e2e8f0` / `#e5e7eb` | `var(--eify-border-default)` |
| `#0f172a` | `var(--eify-text-primary)` |
| `#1e293b` | `var(--eify-gray-800)` |
| `#334155` | `var(--eify-gray-700)` |
| `#475569` | `var(--eify-text-secondary)` |
| `#64748b` | `var(--eify-text-secondary)` |
| `#94a3b8` | `var(--eify-text-tertiary)` |
| `#a5b4fc` | `var(--eify-primary-300)` |

### font-size 替换

| px | 工具类 |
|:---|:---|
| 10-12px | `.text-xs` |
| 13px | `.text-sm` |
| 14px | `.text-base` |
| 15-16px | `.text-lg` |
| 18px | `.text-xl` |
| 20-22px | `.text-2xl` |
| 24px | `.text-3xl` |
| 28px+ | 保留 |

### 验证策略

每批完成后：`vue-tsc --noEmit` + `vitest run`
全部完成后：`mvn test -q`

### 完成标准

- 20 个文件硬编码色值全部替换
- font-size 替换为工具类
- 豁免项保持原样
- TypeScript 类型检查通过
- 前后端测试通过
- git diff 逐文件自查确认

## Consequences

- AI 生成的前端代码将自动使用 Eify 设计令牌，视觉一致性大幅提升
- 后续新增页面只需参照 DESIGN.md，无需记忆色值
- LoginView 的 `#0f0f1a` 背景色需后续纳入设计令牌体系
