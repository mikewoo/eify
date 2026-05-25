# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范。

## [Unreleased] - 2026-05-25

### 新增
- 后端删除引用检查：Agent/知识库/MCP 服务器/工作流删除前检查是否被活跃对话或实体引用，被引用时拒绝删除并返回对应错误码
- 新增错误码：`AGENT_IN_USE(3004)`、`KNOWLEDGE_IN_USE(7008)`、`WORKFLOW_IN_USE(6009)`、`MCP_SERVER_IN_USE_BY_WORKFLOW(5006)`
- Agent 列表接口现在返回 `knowledgeBases` 字段，包含关联知识库的简要信息（已删除的 KB 名称返回 null）
- 新增 `GET /api/v1/mcp-servers/tools` 批量接口，一次返回所有 MCP Server 及工具（含 `online` 状态和 `inputSchema`），替代前端 N+1 查询

### 变更
- 后端 SSE 错误消息现在通过 `MessageUtil` 解析，根据请求的 `Accept-Language` 头返回对应语言的错误信息，替代原有的硬编码中文消息
- Provider 删除检查从 `PROVIDER_NOT_FOUND` 改为 `PROVIDER_IN_USE(2007)` / `PROVIDER_IN_USE_BY_WORKFLOW(2008)`
- Agent 编辑页 MCP Tools 标签页增强：展示 Server 在线状态、endpoint、工具数量，支持工具 `inputSchema` 参数预览展开/收起，离线 Server 工具自动禁用

### 修复
- 修复当 Agent 关联的 Provider 已被删除时，发送消息返回不友好的 `HTTP error! status: 500` 问题。现在 ChatServiceImpl 在 SseEmitter 创建后捕获 `BusinessException`，通过 SSE 事件发送错误消息，避免异常到达 `GlobalExceptionHandler` 导致 `HttpMediaTypeNotAcceptableException`（`Accept: text/event-stream` 与 JSON 响应体不兼容）
- 前端 ChatView/AgentList/WorkflowEdit 现在对已删除的引用实体（Agent、工作流、知识库）显示 `(不可用)` 标记，并禁用聊天输入框
- 前端 SSE 错误回退提示使用 i18n 键 `sendErrorHint` 代替原始 HTTP 状态码

## [1.0.0] - 2026-05-18

### 新增
- 初始开源版本：Spring Boot 4.0.6 + Vue 3 + TypeScript
- 多模型提供商管理（OpenAI 兼容协议，支持 DashScope、Ollama）
- Agent 创建与配置，可视化管理系统提示词和模型参数
- 对话引擎：SSE 流式响应、多轮对话、上下文管理、MCP Tool Call 递归
- 知识库 + RAG：文档上传解析、向量嵌入、多策略检索（向量/关键词/混合）
- MCP 工具协议支持：Model Context Protocol 动态工具注册和调用
- 工作流引擎：可视化编排，支持 LLM/API/ToolCall/条件分支/开始/结束节点
- 多工作空间多租户架构：JWT 认证 + 工作空间级数据隔离
- 完整日志系统：纯 JSON 格式（UTC）、ClickHouse + Vector 采集、OpenTelemetry 链路追踪
- Docker 全栈部署：MySQL + Redis + pgvector + ClickHouse + Vector
- CI/CD：GitHub Actions 自动化测试与构建 + Jenkins CD 部署流水线

---

## 类型说明

| 类型 | 说明 |
|:---|:---|
| **新增** | 新功能 |
| **变更** | 对现有功能的修改 |
| **修复** | Bug 修复 |
| **废弃** | 即将移除的功能 |
| **移除** | 已移除的功能 |
| **安全** | 安全漏洞修复 |
