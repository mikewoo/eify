# Eify 侧边栏组件文档

## 组件特性

✅ **科技感深色侧边栏**：接近纯黑（#0f172a）但不是纯黑，使用渐变和网格装饰
✅ **品牌渐变 Logo**：主色渐变文字 + 发光动画效果
✅ **智能选中态**：主色竖线 + 背景微亮 + 模糊光晕
✅ **平滑过渡动效**：悬停缩放图标 + 文字渐入渐出
✅ **折叠/展开**：底部折叠按钮 + 状态指示灯

## 文件结构

```
eify-web/src/
├── components/
│   └── EifySidebar.vue          # 侧边栏组件
├── styles/
│   ├── design-tokens.css       # CSS 变量（含侧边栏变量）
│   └── sidebar.css              # 侧边栏样式
├── views/
│   └── SidebarPreview.vue       # 侧边栏预览页面
└── App.vue                      # 主应用（使用侧边栏）
```

## 使用方式

### 1. 基本使用

在 `App.vue` 中直接使用：

```vue
<script setup lang="ts">
import EifySidebar from './components/EifySidebar.vue'
</script>

<template>
  <el-container class="layout-container">
    <EifySidebar />
    <el-main class="main-content">
      <RouterView />
    </el-main>
  </el-container>
</template>
```

### 2. 添加菜单项

在 `EifySidebar.vue` 中的 `menuItems` 数组添加：

```typescript
const menuItems: MenuItem[] = [
  {
    path: '/your-path',
    title: '你的标题',
    icon: 'IconName'  // Element Plus 图标名称
  }
]
```

### 3. 可用图标

Element Plus 图标（常用）：

| 图标 | 用途 | 组件名 |
|:---|:---|:---|
| 💬 | 对话 | `ChatDotRound` |
| 👤 | 用户/Agent | `User` |
| ⚙️ | 设置 | `Setting` |
| 🎨 | 设计 | `Brush` |
| 👁️ | 预览 | `View` |
| 📊 | 数据 | `DataAnalysis` |
| 🔧 | 工具 | `Tools` |
| 📝 | 文档 | `Document` |
| 🏠 | 首页 | `HomeFilled` |

完整图标列表：https://element-plus.org/zh-CN/component/icon.html

### 4. 颜色变量

侧边栏使用的 CSS 变量：

```css
/* 深色背景 */
--eify-bg-sidebar: #0f172a              /* 主背景 */
--eify-bg-sidebar-hover: #1e293b         /* 悬停背景 */
--eify-bg-sidebar-active: #1e293b        /* 选中背景 */
--eify-bg-dark: #0a0f1c                   /* 更深黑色 */

/* 文字颜色 */
--eify-text-inverse: #ffffff             /* 白色文字 */
--eify-text-quaternary: #cbd5e1          /* 灰色文字 */

/* 主色 */
--eify-primary: #6366f1                    /* 主色 */
--eify-accent: #2dd4bf                     /* 青色辅色 */
```

## 预览方式

### 方式1：侧边栏预览页面
访问：http://localhost:5173/sidebar-preview

### 方式2：实际应用
访问：http://localhost:5173/
- 侧边栏已集成到主应用
- 可点击各菜单项体验效果

## 交互说明

### 默认状态
- 白色文字
- 透明背景
- 无左边竖线

### 悬停状态
- 文字变白
- 背景微亮（rgba 白色 10% 透明度）
- 图标放大 1.1 倍
- 图标变青色

### 选中状态
- 文字变白
- 背景微亮（主色 10% 透明度）
- 左边 3px 主色竖线
- 竖线模糊光晕效果

### 折叠状态
- 宽度变窄（240px → 64px）
- 隐藏文字
- Logo 缩小
- 隐藏副标题

## 动画效果

| 动画 | 时长 | 缓动 |
|:---|:---|:---|
| Logo 发光 | 3s | ease-in-out infinite |
| 悬停状态 | 200ms | ease-default |
| 图标缩放 | 200ms | ease-default |
| 文字渐入 | 200ms | ease-default |
| 折叠展开 | 200ms | ease-default |

## 响应式适配

侧边栏支持以下断点：

```css
/* 平板 */
@media (max-width: 1024px) {
  .eify-sidebar {
    width: 200px;
  }
}

/* 移动端 */
@media (max-width: 768px) {
  .eify-sidebar {
    position: fixed;
    left: -100%;
    z-index: var(--eify-z-modal);
    transition: var(--eify-transition-base);
  }

  .eify-sidebar.mobile-open {
    left: 0;
  }
}
```

## 自定义样式

### 修改侧边栏宽度

```css
.eify-sidebar {
  width: 280px !important; /* 默认 240px */
}
```

### 修改 Logo 样式

```css
.eify-sidebar-logo-title {
  font-size: 32px !important; /* 默认 28px */
  letter-spacing: 3px !important;
}
```

### 修改选中态竖线

```css
.eify-menu-item.active::before {
  width: 4px !important; /* 默认 3px */
}
```

## 设计细节

### 1. 渐变背景

侧边栏使用微妙的渐变背景：

```css
background: linear-gradient(180deg,
  var(--eify-bg-sidebar) 0%,
  #0a0f1c 100%
);
```

### 2. 网格装饰

悬停时显示科技感网格背景：

```css
background-image:
  linear-gradient(rgba(99, 102, 241, 0.02) 1px, transparent 1px),
  linear-gradient(90deg, rgba(99, 102, 241, 0.02) 1px, transparent 1px);
background-size: 20px 20px;
```

### 3. 状态指示灯

底部角落的青色闪烁指示灯：

```css
width: 6px;
height: 6px;
background: var(--eify-accent);
animation: eify-status-pulse 2s ease-in-out infinite;
```

### 4. 模糊光晕

选中项竖线的模糊光晕效果：

```css
filter: blur(8px);
opacity: 0.5;
```

## 浏览器兼容

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 性能优化

1. **GPU 加速**：使用 `transform` 和 `opacity` 进行动画
2. **will-change**：动态区域使用 `will-change` 优化
3. **防抖节流**：折叠切换使用 200ms 过渡

## 已知限制

1. 折叠状态下 Tooltip 可能显示位置不准确
2. 长菜单项需要滚动查看
3. 移动端需要额外的遮罩层

## 后续优化

- [ ] 移动端适配（遮罩层 + 滑动关闭）
- [ ] 多级菜单支持
- [ ] 菜单搜索功能
- [ ] 可配置主题色
- [ ] 国际化支持
