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

CSS 变量替换本质上是**视觉层变更**，当前项目没有视觉回归测试（如 Storybook + Chromatic）。验证分两层：自动化保障代码不坏，人工保障视觉不错。

#### 自动验证（每批 + 全部）

每批文件改完后：
```bash
cd eify-web && npx vue-tsc --noEmit && cd ..   # TypeScript 类型检查
cd eify-web && npx vitest run && cd ..          # 前端单元测试（1 文件 11 用例）
```

全部改完后：
```bash
mvn test -q                                     # 后端全部测试
```

**自动化验证能发现的：**
- 类型错误（import 路径、props 类型不匹配）
- ConfirmDialog 组件逻辑被打破
- 后端编译/测试失败

**自动化验证检测不到的：**
- CSS 变量替换后颜色偏差（如目标 token 的色值与原硬编码不完全一致）
- font-size 工具类与原始 px 值的微小视觉差异（如 11px → `.text-xs`(12px)）
- CSS 优先级变化导致样式失效
- 替换后遗漏导致部分元素回退到浏览器默认样式

#### 视觉验证（全改完后手动执行）

```bash
./start.sh dev                                    # 启动应用
```

逐页目视检查清单（12 页）：

| # | 页面 | 路由 | 重点检查 |
|:---|:---|:---|:---|
| 1 | 登录页 | `/login` | 背景色、品牌渐变、按钮颜色、输入框聚焦环 |
| 2 | Agent 列表 | `/agents` | 卡片背景、表格行 hover、标签颜色、搜索框 |
| 3 | Chat 对话 | `/chat` | 聊天气泡渐变、错误提示色、加载动画 |
| 4 | Provider 列表 | `/providers` | 卡片、表格、状态标签 |
| 5 | MCP Server 列表 | `/mcp-servers` | 卡片、日志查看器颜色（Catppuccin 豁免区） |
| 6 | 知识库 | `/knowledge` | 卡片、搜索区、上传区域 |
| 7 | 文档 | `/documents` | 预览组件、分页 |
| 8 | 工作流列表 | `/workflows` | 卡片、状态色（running/stopped/error） |
| 9 | 工作流创建 | `/workflows/create` | 节点面板、表单输入框 |
| 10 | 工作流编辑 | `/workflows/:id/edit` | 画布连接线、节点颜色、变量表格 |
| 11 | 个人设置 | `/profile` | 头像渐变、表单、分页 |
| 12 | 侧边栏 + 顶栏 | 全局 | 菜单 hover/active 效果、Logo glow、折叠按钮 |

每页通过标准：颜色无异常变化、文字层级正确、交互反馈（hover/active/focus）正常。

### 完成标准

- [ ] 20 个文件硬编码色值全部替换
- [ ] font-size 替换为工具类
- [ ] 豁免项保持原样
- [ ] `vue-tsc --noEmit` 通过
- [ ] `vitest run` 通过
- [ ] `mvn test -q` 通过
- [ ] 12 页视觉检查全部通过
- [ ] git diff 逐文件自查确认

## Consequences

- AI 生成的前端代码将自动使用 Eify 设计令牌，视觉一致性大幅提升
- 后续新增页面只需参照 DESIGN.md，无需记忆色值
- LoginView 的 `#0f0f1a` 背景色需后续纳入设计令牌体系
