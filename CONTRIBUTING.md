# 贡献指南

感谢你对 Eify 的关注！这份指南将帮助你参与项目开发。

## 环境搭建

```bash
# 1. Fork 并克隆项目
git clone https://github.com/YOUR_USERNAME/eify.git
cd eify

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入本地配置

# 3. 启动依赖服务
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d pgvector redis

# 4. 启动后端
mvn spring-boot:run -pl eify-app -Dspring-boot.run.profiles=dev

# 5. 启动前端
cd eify-web
npm install
npm run dev
```

## 开发规范

提交代码前请阅读项目规范文档：

- [架构与编码规范](docs/ARCHITECTURE.md) — 模块结构、命名规范、分层职责
- [API 接口规范](docs/API-SPEC.md) — 接口设计约定
- [数据库规范](docs/guides/DATABASE.md) — 建表模板、索引策略、分页限制
- [日志规范](docs/guides/LOGGING.md) — 日志格式、类型、链路追踪

### 关键约束

| 约束 | 说明 |
|:---|:---|
| 异常处理 | 必须使用 `ErrorCode` 枚举，禁止 `RuntimeException` |
| 数据库索引 | 查询必须有索引覆盖，不允许全表扫描 |
| 工作空间隔离 | Service 层所有查询/更新/删除必须过滤 `workspace_id` |
| 日志格式 | 纯 JSON 格式，UTC 时区 |
| 配置安全 | 禁止硬编码密码和 API Key，使用 `${ENV_VAR}` 占位符 |

## 提交流程

1. **创建分支** — `git checkout -b feature/your-feature`（`feature/`、`hotfix/`、`docs/`、`refactor/`）
2. **编写代码** — 遵守项目编码规范
3. **本地测试** — `mvn test` 确保所有测试通过
4. **提交** — 使用简洁的提交信息描述变更
5. **推送** — 推送到你的 Fork
6. **创建 PR** — 描述变更内容、测试情况

## 提交信息格式

```
feature: 添加 Ollama 模型适配器
hotfix: 修复 SSE 连接超时未清理问题
docs: 补充部署文档环境变量说明
refactor: 提取 WorkspaceGuard 工具类
```

## 测试

```bash
# 运行全部测试
mvn test

# 运行指定模块测试
mvn test -pl eify-workflow
```

CI 通过 GitHub Actions Service Container 提供 PostgreSQL 17 测试数据库，CD 部署由 Jenkins 流水线完成。本地测试也可通过 `application-test.yml` 配置连接本地数据库。

## Issue 规范

- Bug 报告：描述复现步骤、期望行为、实际行为、环境信息
- 功能建议：描述使用场景、期望效果
- 先搜索已有 Issue 避免重复

---

如有疑问，欢迎在 Issue 中讨论。
