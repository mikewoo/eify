<p align="center">
  <img src="eify-web/src/assets/hero.png" alt="Eify Logo" width="120">
</p>

<h1 align="center">Eify</h1>

<p align="center">
  轻量级 AI Agent 平台 —— 可视化编排、多模型支持、RAG 知识库、MCP 工具扩展。发起这个开源项目的初衷是学习Vibe Coding

</p>

<p align="center">
  <a href="README.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-blue" alt="Version">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java">
  <img src="https://img.shields.io/badge/Vue-3.x-brightgreen" alt="Vue">
  <img src="https://img.shields.io/badge/CI-passing-brightgreen" alt="CI">
</p>

---

## 特性

| 模块 | 说明 |
|:---|:---|
| **多模型提供商** | 支持 OpenAI 兼容协议，可接入 DashScope、Ollama 等 |
| **Agent 管理** | 可视化创建和配置 Agent，自定义系统提示词和模型参数 |
| **流式对话** | SSE 流式响应，多轮对话，上下文窗口管理 |
| **知识库 + RAG** | 文档上传、向量化检索、多策略检索增强生成 |
| **MCP 工具** | 支持 Model Context Protocol（MCP），扩展 Agent 能力边界 |
| **工作流引擎** | 可视化拖拽编排，条件分支、LLM 节点、代码执行节点 |
| **多租户工作空间** | JWT 认证 + 工作空间级数据隔离，适合团队使用 |
| **完整日志链路** | Micrometer Tracing + Brave 链路追踪，ClickHouse + Vector 日志采集 |

## 快速开始

### 前置条件

- [Docker](https://docs.docker.com/get-docker/) & Docker Compose
- JDK 21+ & Maven 3.8+（仅本地开发需要）

### Docker 部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/mikewoo/eify.git
cd eify

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，设置必要的密码和 API Key

# 3. 构建后端 JAR
mvn clean package -DskipTests

# 4. 一键启动
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d

# 5. 访问
# 前端: http://localhost
# API 文档: http://localhost:8080/doc.html
```

### 本地开发

```bash
# 1. 启动依赖服务（MySQL + Redis）
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d mysql redis

# 2. 启动后端
./start.sh dev
# 或者: mvn spring-boot:run -pl eify-app -Dspring-boot.run.profiles=dev

# 3. 启动前端
cd eify-web
npm install
npm run dev

# 4. 访问
# 前端: http://localhost:5173
# API 文档: http://localhost:8080/doc.html
```

### 可选组件

```bash
# 日志采集栈（ClickHouse + Vector + Grafana）
docker-compose -f deploy/infra/deploy/docker-compose-logging.yml up -d

# 分布式追踪可视化（Jaeger）
docker-compose -f deploy/optional/docker-compose-jaeger.yml up -d

# 向量数据库（pgvector）— 已包含在主 Compose 中，无需单独启动
```

## 技术栈

| 层级 | 技术 |
|:---|:---|
| **后端框架** | Spring Boot 4.0.6 |
| **ORM** | MyBatis-Plus 3.5.15 |
| **数据库** | MySQL 8.0 |
| **缓存** | Redis 7 |
| **向量数据库** | PostgreSQL 16 + pgvector |
| **前端** | Vue 3.5.17 + TypeScript + Vite |
| **UI 框架** | Element Plus 2.10.2 |
| **状态管理** | Pinia 2.3.1 |
| **工作流可视化** | Vue Flow |
| **链路追踪** | Micrometer Tracing + Brave |
| **日志存储** | ClickHouse 25 + Vector 0.54 |
| **容器化** | Docker + Docker Compose |

## 项目结构

```
eify/
├── eify-app/              # 应用启动模块
├── eify-auth/             # 认证与工作空间（JWT、用户管理、多租户）
├── eify-provider/         # LLM 提供商管理
├── eify-agent/            # Agent 创建与配置
├── eify-chat/             # 对话引擎（SSE 流式响应）
├── eify-knowledge/        # 知识库 + RAG 检索
├── eify-mcp/              # MCP 工具协议
├── eify-workflow/         # 工作流引擎
├── eify-common/           # 公共模块
├── eify-web/              # Vue 3 前端
├── docs/                  # 项目文档
├── deploy/                # 部署配置（Dockerfile、K8s、SQL）
├── scripts/               # 工具脚本
├── start.sh               # 启动脚本
├── stop.sh                # 停止脚本
└── .env.example           # 环境变量模板
```

## 架构概览

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   Vue 3     │────▶│  Spring Boot│────▶│   MySQL 8    │
│   Frontend  │     │  REST API   │     │   持久化      │
└─────────────┘     └──────┬──────┘     └──────────────┘
                           │
               ┌───────────┼───────────┐
               │           │           │
               ▼           ▼           ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  Redis   │ │ pgvector │ │ 外部 LLM │
        │  缓存    │ │ 向量检索  │ │   API    │
        └──────────┘ └──────────┘ └──────────┘
```

- **认证**：JWT 无状态认证 + ThreadLocal 上下文传递
- **多租户**：所有业务数据按 `workspace_id` 隔离，WorkspaceGuard 统一校验
- **流式**：SSE 长连接，支持 LLM 逐 token 推送
- **日志**：Micrometer Tracing + Brave 自动生成 TraceId/SpanId，ClickHouse 存储

## 📖 文档

| 文档 | 说明 |
|:---|:---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计 + 编码规范 |
| [API-SPEC.md](docs/API-SPEC.md) | API 接口规范 |
| [AUTH-WORKSPACE.md](docs/guides/AUTH-WORKSPACE.md) | 认证与多租户 |
| [DATABASE.md](docs/guides/DATABASE.md) | 数据库设计（MySQL + ClickHouse） |
| [LOGGING.md](docs/guides/LOGGING.md) | 日志系统指南 |
| [WORKFLOW.md](docs/guides/WORKFLOW.md) | 工作流引擎设计 |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | 部署与 CI/CD |

## Roadmap

- [x] Ollama 本地模型适配器
- [x] Anthropic Claude 适配器
- [ ] 对话历史导出
- [ ] Agent 模板市场
- [x] 前端国际化（i18n）
- [ ] 前端测试覆盖
- [ ] 英文文档

## 贡献

欢迎提交 Issue 和 Pull Request。参与前请阅读：

- [贡献指南](CONTRIBUTING.md)
- [行为准则](CODE_OF_CONDUCT.md)

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

<p align="center">
  <sub>Made by mikewoo | MIT License</sub>
</p>
