# Eify Web

Eify 前端应用，基于 Vue 3 + TypeScript + Vite 构建。

## 技术栈

| 类别 | 技术 |
|:---|:---|
| **框架** | Vue 3 (Composition API) |
| **语言** | TypeScript |
| **构建** | Vite |
| **UI 框架** | Element Plus |
| **状态管理** | Pinia |
| **路由** | Vue Router |
| **工作流可视化** | Vue Flow |
| **HTTP 客户端** | Axios |
| **Markdown** | Marked + Highlight.js |
| **XSS 防护** | DOMPurify |

## 本地开发

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 类型检查
npx vue-tsc --noEmit

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

## 项目结构

```
eify-web/src/
├── api/                    # API 模块（agent、auth、chat、knowledge、mcp、provider、workflow、workspace）
├── assets/                 # 静态资源
├── components/             # 通用组件
│   ├── EifySidebar.vue     # 侧边栏
│   ├── EifyTable.vue       # 通用表格
│   ├── EifySearch.vue      # 搜索组件
│   ├── EifyFormDialog.vue  # 表单对话框
│   └── ...
├── views/                  # 页面视图
│   ├── AgentList.vue       # Agent 列表
│   ├── ChatView.vue        # 对话界面
│   ├── WorkflowEdit.vue    # 工作流编辑器
│   ├── KnowledgeView.vue   # 知识库管理
│   ├── ProviderList.vue    # 模型提供商
│   └── ...
├── stores/                 # Pinia 状态管理
├── router/                 # 路由配置
├── styles/                 # 样式文件
├── types/                  # TypeScript 类型定义
└── utils/                  # 工具函数
```

## 测试

```bash
# 运行所有测试
npm test

# 监听模式（开发时使用）
npm run test:watch
```

测试框架：Vitest + Vue Test Utils + jsdom

## 代理配置

开发环境 API 请求代理到 `http://localhost:8080`，配置在 `vite.config.ts`。

## 相关文档

- [后端项目文档](../docs/README.md)
- [API 接口规范](../docs/API-SPEC.md)
