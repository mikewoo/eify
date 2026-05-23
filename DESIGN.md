# Eify Design System

> AI 编码助手的视觉规范文档。使用 `--eify-*` CSS 变量和 `.eify-*` 组件类名。设计参照 Linear 风格——浅底科技风，深色侧边栏 + 浅色内容区，干净克制。

---

## Brand Colors

### Primary — Blue-Purple (蓝紫系)

科技感主色，用于主按钮、选中态、品牌标识。

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-primary-500` | `#6366f1` | 品牌主色 (Indigo-500) |
| `--eify-primary-600` | `#4f46e5` | hover 加深 |
| `--eify-primary-700` | `#4338ca` | active 按下 |
| `--eify-primary-400` | `#818cf8` | 浅化变体 |
| `--eify-primary-100` | `#e0e9ff` | 浅背景底色 |
| `--eify-primary-50` | `#f0f4ff` | 更浅背景底色 |

**快捷别名：**
- `--eify-primary` = `--eify-primary-500` （默认主色）
- `--eify-primary-hover` = `--eify-primary-600`
- `--eify-primary-active` = `--eify-primary-700`
- `--eify-primary-light` = `--eify-primary-100`
- `--eify-primary-lighter` = `--eify-primary-50`

**渐变：**
- `--eify-gradient-primary` = `linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)` — 用于主按钮、Logo、进度条

### Accent — Teal-Mint (青薄荷系)

辅色，用于数据/状态、强调装饰。

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-accent-400` | `#2dd4bf` | 辅助主色 |
| `--eify-accent-300` | `#5eead4` | 浅化 |
| `--eify-accent-100` | `#ccfbf1` | 浅背景 |

**快捷别名：** `--eify-accent`, `--eify-accent-hover`, `--eify-accent-light`

### Functional Colors

| Role | Base | Background | Text |
|:---|:---|:---|:---|
| **Success** | `#22c55e` | `#f0fdf4` | `#16a34a` |
| **Warning** | `#f59e0b` | `#fffbeb` | `#d97706` |
| **Error** | `#ef4444` | `#fef2f2` | `#dc2626` |
| **Info** | `#0ea5e9` | `#f0f9ff` | `#0284c7` |

---

## Typography

### Font Stack

```
-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
'Helvetica Neue', Arial, sans-serif
```

中文补充：`'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei'`

等宽字体（版本号/代码区域）：`'SF Mono', 'Monaco', 'Inconsolata', monospace`

### Base

- **基准字号**：`14px`
- **行高**：`1.5`
- **抗锯齿**：`antialiased` (webkit + moz)

### Size Scale

| Class | Size | Line Height |
|:---|:---|:---|
| `.text-xs` | 12px | 1.5 |
| `.text-sm` | 13px | 1.5 |
| `.text-base` | 14px | 1.5 |
| `.text-lg` | 16px | 1.5 |
| `.text-xl` | 18px | 1.5 |
| `.text-2xl` | 20px | 1.4 |
| `.text-3xl` | 24px | 1.3 |

### Weights

| Class | Weight |
|:---|:---|
| `.font-normal` | 400 |
| `.font-medium` | 500 |
| `.font-semibold` | 600 |
| `.font-bold` | 700 |

### Key Text Sizes by Context

| Context | Size | Weight | Color |
|:---|:---|:---|:---|
| Page title | 20px | 600 | `--eify-text-primary` |
| Page description | 13px | 400 | `--eify-text-secondary` |
| Card title | 16px | 600 | `--eify-text-primary` |
| Menu item | 14px | 500 | see sidebar |
| Logo | 28px | 800 | gradient |

---

## Spacing

4px 步进网格。

| Token | Value |
|:---|:---|
| `--eify-spacing-1` | 4px |
| `--eify-spacing-2` | 8px |
| `--eify-spacing-3` | 12px |
| `--eify-spacing-4` | 16px |
| `--eify-spacing-5` | 20px |
| `--eify-spacing-6` | 24px |
| `--eify-spacing-8` | 32px |
| `--eify-spacing-10` | 40px |
| `--eify-spacing-12` | 48px |
| `--eify-spacing-16` | 64px |

### Layout-specific Spacing

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-page-padding` | 24px | 页面内容区边距 |
| `--eify-card-padding` | 20px | 卡片内边距 |
| `--eify-element-gap` | 16px | 页面元素间距 |

### Utility Classes

Margin: `.m-{1..6}`, `.mx-auto`, `.my-{0,4,6}`
Padding: `.p-{0..8}`, `.px-{0..6}`, `.py-{0..6}`
Gap: `.gap-{1..6}`

---

## Border Radius

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-radius-xs` | 4px | 聊天气泡小角 |
| `--eify-radius-sm` | 6px | 按钮、输入框 |
| `--eify-radius-md` | 8px | 卡片、菜单项 |
| `--eify-radius-lg` | 12px | 聊天气泡、用户信息 hover 区 |
| `--eify-radius-xl` | 16px | 大卡片 |
| `--eify-radius-2xl` | 20px | — |
| `--eify-radius-full` | 9999px | 标签、徽章、头像 |

**快捷类名：** `.rounded-xs` ~ `.rounded-full`

卡片专用：`--eify-card-radius: 12px`

---

## Shadows

### Elevation Scale

| Token | Usage |
|:---|:---|
| `--eify-shadow-xs` | 微量提升 |
| `--eify-shadow-sm` | 默认卡片 |
| `--eify-shadow-md` | 卡片 hover、主按钮 hover |
| `--eify-shadow-lg` | 下拉面板 |
| `--eify-shadow-xl` | 模态框 |
| `--eify-shadow-inner` | 内阴影（已按下状态） |

### Card Shadow

`--eify-card-shadow` = `0 1px 3px 0 rgb(0 0 0 / 0.06), 0 1px 2px -1px rgb(0 0 0 / 0.06)` — 卡片默认使用，比 `shadow-sm` 更轻。

### Focus Rings

| Token | Color | Usage |
|:---|:---|:---|
| `--eify-shadow-primary` | `rgb(99 102 241 / 0.1)` | 输入框聚焦、主按钮焦点 |
| `--eify-shadow-accent` | `rgb(45 212 191 / 0.1)` | 辅色焦点 |
| `--eify-shadow-error` | `rgb(239 68 68 / 0.1)` | 错误态焦点 |

### Interaction Principle

- 默认态：无阴影或 `card-shadow`
- hover 态：提升一级阴影（如 card → `shadow-md`）
- 主按钮 hover：`0 4px 12px rgba(99, 102, 241, 0.3)` + `translateY(-1px)`
- active/按下：`translateY(0)` 回弹

---

## Background Colors

### Content Area (浅色主题)

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-bg-base` | `#ffffff` | 基础白底 |
| `--eify-bg-subtle` | `#fafafa` | body 背景 |
| `--eify-bg-surface` | `#f3f4f6` | 表格表头、hover 区域 |
| `--eify-bg-surface-raised` | `#ffffff` | 抬高面板 |
| `--eify-bg-secondary` | `#f8fafc` | 页面内容区 `.eify-page` |

### Sidebar (深色)

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-bg-sidebar` | `#161b2e` | 侧边栏背景 |
| `--eify-bg-sidebar-hover` | `#232940` | 菜单项 hover |
| `--eify-bg-sidebar-active` | `#232940` | 菜单项选中 |
| `--eify-bg-sidebar-deep` | `#0d111d` | 侧边栏底部渐变目标 |

侧边栏背景渐变：`linear-gradient(180deg, #161b2e 0%, #0d111d 100%)`

### Card Stacking

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-bg-card-1` | `#ffffff` | 一级卡片 |
| `--eify-bg-card-2` | `#fafafa` | 二级嵌套 |
| `--eify-bg-card-3` | `#f3f4f6` | 三级嵌套 |

### Input

- 正常态：`#ffffff`
- 禁用态：`#f3f4f6`

---

## Text Colors

### Hierarchy

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-text-primary` | `#0f172a` | 标题、正文 |
| `--eify-text-secondary` | `#475569` | 描述、辅助信息 |
| `--eify-text-tertiary` | `#94a3b8` | 占位符、禁用字 |
| `--eify-text-quaternary` | `#cbd5e1` | 更深淡化（侧边栏文字） |
| `--eify-text-inverse` | `#ffffff` | 深色背景上的文字 |

### Link

| Token | Value |
|:---|:---|
| `--eify-text-link` | `var(--eify-primary)` |
| `--eify-text-link-hover` | `var(--eify-primary-hover)` |

### Semantic Text Colors

Status text uses the darker 600 shade of each functional color:
- `.text-success` = `#16a34a`
- `.text-warning` = `#d97706`
- `.text-error` = `#dc2626`
- `.text-info` = `#0284c7`

---

## Border Colors

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-border-default` | `#e2e8f0` | 默认边框 |
| `--eify-border-subtle` | `#f1f5f9` | 轻边框（卡片分割） |
| `--eify-border-strong` | `#cbd5e1` | 强调边框（hover 态） |
| `--eify-border-focus` | `var(--eify-primary)` | 聚焦边框 |

### Divider

`--eify-divider-default` = `#e2e8f0` — HR 分隔线、表格分隔线

---

## Gradients

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-gradient-primary` | `135deg, #6366f1 → #8b5cf6` | 主按钮、Logo、进度条 |
| `--eify-gradient-accent` | `135deg, #2dd4bf → #14b8a6` | 辅色渐变 |
| `--eify-gradient-subtle` | `135deg, #f8fafc → #f1f5f9` | 淡背景渐变 |
| `--eify-bg-sidebar` gradient | `180deg, #161b2e → #0d111d` | 侧边栏背景 |

**重要：** 不要在浅色卡片上使用渐变背景替代纯色背景。渐变仅用于按钮、Logo 和侧边栏。

---

## Components

### Button `.eify-btn`

**Variants：**

| Class | Background | Text | Border | Hover |
|:---|:---|:---|:---|:---|
| `.eify-btn-primary` | `gradient-primary` | white | transparent | shadow + translateY(-1px) |
| `.eify-btn-secondary` | white | `text-primary` | `border-default` | border → primary, bg → primary-50 |
| `.eify-btn-danger` | `error` | white | transparent | error-600 + shadow |
| `.eify-btn-ghost` | transparent | `text-primary` | none | bg → primary-light, text → primary |
| `.eify-btn-text` | transparent | `text-secondary` | none | text → primary, bg → primary-50 |

**Sizes：**

| Class | Height | Padding | Font |
|:---|:---|:---|:---|
| `.eify-btn-sm` | 32px | 0 12px | 13px |
| `.eify-btn-md` (default) | 36px | 0 16px | 14px |
| `.eify-btn-lg` | 40px | 0 20px | 15px |

**Rules：**
- 主按钮一个页面最多出现一次（主要 CTA）
- 次要按钮用于取消/返回
- 危险按钮仅用于不可逆操作（删除），需二次确认
- 禁用态：`opacity: 0.5; cursor: not-allowed`

### Input `.eify-input`

| Size | Height | Font |
|:---|:---|:---|
| `.eify-input-sm` | 32px | 13px |
| `.eify-input-md` (default) | 36px | 14px |
| `.eify-input-lg` | 40px | 15px |

- 默认边框：`border-default`
- 聚焦：边框变 primary + `shadow-primary`
- 禁用：bg 变 `bg-input-disabled`，文字变 quaternary
- Placeholder：`text-quaternary`

### Card `.eify-card`

```
white bg + 12px radius + card-shadow + 1px border-default
hover → shadow-md
```

**子区域：**
- `.eify-card-header` — 顶部区域，底部有分割线
- `.eify-card-body` — 内容区
- `.eify-card-footer` — 底部区域，顶部有分割线

卡片内边距：20px (`--eify-card-padding`)

### Table `.eify-table`

- 表头：bg `surface`, 字号 13px, weight 600, 文字 `text-secondary`
- 数据行：bg white, 字号 14px, 行高 56px
- 行 hover：bg → `primary-light`
- 底部行无 border-bottom

### Tag `.eify-tag`

| Class | Background | Text |
|:---|:---|:---|
| `.eify-tag-primary` | `primary-light` | `primary` |
| `.eify-tag-success` | `success-light` | `success` |
| `.eify-tag-warning` | `warning-light` | `warning` |
| `.eify-tag-error` | `error-light` | `error` |
| `.eify-tag-accent` | `accent-light` | `accent` |
| `.eify-tag-gray` | `gray-100` | `gray-600` |

统一样式：`padding: 4px 10px; font-size: 12px; font-weight: 500; border-radius: full`

### Badge `.eify-badge`

红色实心小圆/数字徽章（用于未读计数等）：
- min-width: 18px, height: 18px
- bg: error red, text: white
- `border-radius: full`

### Progress `.eify-progress`

高度 6px，圆角 full，背景 gray-200，填充使用 `gradient-primary`。

### Switch `.eify-switch`

40×22px 开关，unchecked 态 gray-300，checked 态 primary，滑块白色圆点。

### Spinner `.eify-spinner`

16×16px，border 2px `border-default`，顶部 primary，`0.6s linear infinite`。

### Skeleton `.eify-skeleton`

灰色渐变扫光动画（1.5s），用于加载占位。

### Chat Bubble `.eify-chat-bubble`

- 用户：`gradient-primary` 白字，右下角 4px 小圆角
- 助手：`gray-100` 深色字，左下角 4px 小圆角
- 最大宽度 70%

### Alert `.eify-alert`

4 种变体（`.eify-alert-info/.success/.warning/.error`），各带对应 bg+border+text。

### Divider `.eify-divider`

1px 高，bg `divider-default`。`.eify-divider-vertical` 为竖版。

### Status Dot `.eify-status-dot`

8×8px 圆点，4 种状态色：online(teal)、busy(amber)、offline(gray)、error(red)。`.pulse` 变体有呼吸动画。

---

## Layout

### Shell Structure

```
┌─ Sidebar (200px, deep dark) ─┬─ Header (56px, white, sticky) ─┐
│                              │                                  │
│  Logo (gradient text)       │  Breadcrumb + User Info          │
│  Menu Items                  │                                  │
│  Footer + Version            │  Page Content                    │
│                              │  (max-width: 1680px, mx-auto)    │
└──────────────────────────────┴──────────────────────────────────┘
```

### Sidebar `.eify-sidebar`

- 宽度：200px (可拉伸 180–280px)
- 折叠宽度：64px
- 背景：深蓝紫渐变 `#161b2e → #0d111d`
- 右侧边框：`1px rgba(255,255,255,0.05)`
- 菜单项：文字 quaternary，hover 变 white，active 有左侧紫渐变指示条 + glow
- 图标容器：32×32px 圆角 10px，hover 有紫渐变背景 + box-shadow
- Logo：28px/800 渐变文字 + 3s 呼吸 glow 动画

### Header `.eify-header`

- 高度：56px
- 背景：white + 底部 1px 边框
- 定位：sticky top
- 左区：面包屑导航
- 右区：操作按钮 + 用户头像（32px 渐变圆形）

### Page `.eify-page`

- 背景：`bg-secondary` (`#f8fafc`)
- 内容区 max-width：1680px，居中，padding 24px
- 标题：20px/600

### Responsive Breakpoints

| Breakpoint | Max Width | 其他调整 |
|:---|:---|:---|
| ≥1920px | 1800px | 标题 22px |
| 1440–1919px | 1680px | 默认 |
| 1200–1439px | 1400px | — |
| 768–1199px | 1100px | 卡片圆角改 md |
| ≤768px | 100% | padding 改 16px；header 用户名称隐藏；页面标题栏纵向排列 |

---

## Sidebar Deep Design

侧边栏是 Eify 最具辨识度的视觉元素。

### Menu Item States

| State | Background | Text Color | Icon | Left Bar |
|:---|:---|:---|:---|:---|
| Default | transparent | quaternary | default | hidden |
| Hover | `rgba(255,255,255,0.05)` | white | glow + scale 1.1 | hidden |
| Active | `rgba(99,102,241,0.1)` | white | glow + scale 1.05 | purple gradient + blur glow |

### Eye Candy

- 网格式背景：20px 间距紫线，hover 时浮现（opacity 过渡）
- 顶部发光边框：紫色渐变 1px 线
- Logo 文字：呼吸发光动画 (3s infinite)
- 折叠按钮：紫边框 + hover 时 scale 1.05 + 外发光
- 底部版本标签：等宽字体 + accent 色徽章

---

## Transitions & Animation

### Duration

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-duration-fast` | 150ms | hover 反馈、标签切换 |
| `--eify-duration-base` | 200ms | 默认过渡 |
| `--eify-duration-slow` | 300ms | 展开/折叠、页面切换 |

### Easing

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-ease-default` | `cubic-bezier(0.4, 0, 0.2, 1)` | 通用 |
| `--eify-ease-in` | `cubic-bezier(0.4, 0, 1, 1)` | 入场 |
| `--eify-ease-out` | `cubic-bezier(0, 0, 0.2, 1)` | 退场 |
| `--eify-ease-bounce` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 弹跳效果 |

### Shorthand

- `.transition` → `all 200ms ease-default`
- `.transition-fast` → `all 150ms ease-default`
- `.transition-slow` → `all 300ms ease-default`

---

## Z-Index Scale

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-z-dropdown` | 1000 | 下拉菜单 |
| `--eify-z-sticky` | 1020 | 顶栏 (sticky header) |
| `--eify-z-fixed` | 1030 | 固定元素 |
| `--eify-z-modal-backdrop` | 1040 | 模态背景遮罩 |
| `--eify-z-modal` | 1050 | 模态框 |
| `--eify-z-popover` | 1060 | 弹出层 |
| `--eify-z-tooltip` | 1070 | 工具提示 |
| `--eify-z-notification` | 1080 | 通知 |

---

## Component Sizes

| Component | sm | md (default) | lg |
|:---|:---|:---|:---|
| Button height | 32px | 36px | 40px |
| Input height | 32px | 36px | 40px |
| Table row | — | 56px | — |
| Sidebar width | — | 200px | (max 280px) |
| Sidebar collapsed | — | 64px | — |
| Header height | — | 56px | — |

---

## AI Status Colors

| Token | Value | Usage |
|:---|:---|:---|
| `--eify-status-agent-online` | `#2dd4bf` | Agent 在线 |
| `--eify-status-agent-busy` | `#fbbf24` | Agent 忙碌 |
| `--eify-status-agent-offline` | `#94a3b8` | Agent 离线 |
| `--eify-status-agent-error` | `#ef4444` | Agent 异常 |

---

## Dark Mode

已通过 `prefers-color-scheme: dark` 媒体查询预留暗色变量覆盖：

- 页面背景 → `#0f172a`
- 表面背景 → `#1e293b`
- 主文字 → `#f1f5f9`
- 边框 → `#334155`
- 侧边栏 → `#0d111d`

**当前状态：** 仅 CSS 变量层面映射，未做组件级适配。浅色为主，暗色预留。

---

## Design Principles

1. **浅底科技风：** 白色/浅灰内容区承载信息密度，深色侧边栏提供视觉锚点
2. **克制装饰：** 渐变仅用于按钮/Logo/进度条，卡片纯白 + 轻阴影
3. **紫蓝主色 + 青薄荷辅色：** 暖紫冷青互补，功能色独立不混用
4. **交互反馈有层级：** hover 轻提(translateY -1px) + 阴影，active 回弹
5. **侧边栏是品牌载体：** glow 动画、渐变指示条、网格背景——细节堆积科技感
6. **4px 节奏：** 所有间距、圆角、尺寸对齐 4px 网格
7. **信息层级用颜色区分：** 4 级文字色 + 4 级背景色 + 5 级阴影

## Anti-Patterns (Do NOT Do)

- ❌ 不要在浅色卡片上使用渐变背景
- ❌ 不要自己写颜色值——必须使用 `--eify-*` CSS 变量
- ❌ 不要创建新的组件类名——使用已有的 `.eify-*` 体系
- ❌ 不要修改侧边栏的颜色方案（深蓝紫是刻意设计）
- ❌ 不要在非按钮元素上使用 `gradient-primary`
- ❌ 不要使用 `#000` 或 `#333` 作为文字颜色——用 `text-primary` / `text-secondary`
- ❌ 不要在 `prefers-color-scheme: dark` 之外自行实现暗色模式
