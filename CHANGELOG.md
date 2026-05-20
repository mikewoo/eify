# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范。

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
