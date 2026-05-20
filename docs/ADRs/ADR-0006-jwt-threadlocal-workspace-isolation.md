# JWT 无状态认证 + ThreadLocal 工作空间隔离
`ADR-0006 jwt-threadlocal-workspace-isolation`

# Status
Accepted

# Date
2025-Q4

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

Eify 是一个多租户 SaaS 平台，核心需求：
- 一个用户可加入多个工作空间（Workspace）
- 每个工作空间的数据完全隔离（Agent、Chat、Workflow、Knowledge 等）
- 用户可随时切换工作空间，切换后所有数据视图更新
- 认证方案需适配 K8s 多副本部署（无状态）

### Decision drivers
- 必须无状态（K8s 多副本水平扩展）
- Service 层代码简洁（不污染方法签名）
- 安全红线：生产环境不得使用开发密钥
- 1 人团队维护成本可控

# Considered Options
* **方案 A：Session + Redis** — 服务端 session，Redis 存储登录态。K8s 多副本下需 Redis 同步，增加网络调用和故障点。
* **方案 B：Spring Security + OAuth2** — 标准安全框架。对 MVP 阶段过度复杂（1 人团队），配置量大。
* **方案 C：数据库行级安全（RLS）** — PostgreSQL RLS / MySQL 视图。pgvector 已是辅助存储，无法统一使用 RLS；MySQL 无原生 RLS。
* **方案 D：JWT + ThreadLocal + Guard** — 自研轻量方案。零外部依赖，无状态可水平扩展，实现简单。

# Decision

**选择方案 D：Hutool JWT 无状态认证 + ThreadLocal 请求上下文 + ContextPropagatingTaskDecorator 异步传播 + WorkspaceGuard 数据隔离守卫。**

JWT payload 中包含 `wid`（当前工作空间 ID），用户切换工作空间时后端签发新 JWT。`CurrentContext` 使用 ThreadLocal 保存请求级 userId/workspaceId，Service 层通过 `CurrentContext.getWorkspaceId()` 获取上下文，`WorkspaceGuard` 统一校验数据归属。

## Consequences

### 优势
- K8s 多副本零依赖，无需 Redis/DB 查会话
- ThreadLocal 请求级上下文，Service 层无需传参
- `WorkspaceGuard.requireInWorkspace()` 将 7 行重复校验压缩为 1 行
- `JwtSecretValidator` 启动时校验密钥，防止生产环境使用开发密钥

### 权衡
- ThreadLocal 必须在 finally 中清理，否则导致内存泄漏和跨请求数据污染
- 异步场景必须通过 TaskDecorator 传播上下文（新增线程池时容易遗漏）
- JWT 无状态意味着无法主动失效单个 token（需等待自然过期）

# Details

## 请求链路

```
用户请求
  │
  ▼
JwtAuthFilter (Servlet Filter)
  ├─ 解析 Authorization: Bearer <JWT>
  ├─ 提取 sub → userId, wid → workspaceId
  ├─ CurrentContext.set(userId, workspaceId)
  ├─ chain.doFilter() → Controller → Service
  │    └─ Service 层通过 CurrentContext.getWorkspaceId() 获取上下文
  │         └─ WorkspaceGuard 校验数据归属
  └─ finally: CurrentContext.clear()
```

## 异步上下文传播

5 个业务线程池（llm / workflow / mcp / sse / async）均通过 `ContextPropagatingTaskDecorator` 自动传播上下文：

```java
public class ContextPropagatingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Long userId = CurrentContext.getUserId();
        Long workspaceId = CurrentContext.getWorkspaceId();
        return () -> {
            try {
                CurrentContext.set(userId, workspaceId);
                runnable.run();
            } finally {
                CurrentContext.clear();
            }
        };
    }
}
```

## WorkspaceGuard 消除样板代码

```java
// 重构前（每个 Service 重复 7 行）
Provider existing = mapper.selectById(id);
if (existing == null || !existing.getWorkspaceId().equals(CurrentContext.getWorkspaceId())) {
    throw new BusinessException(ErrorCode.NOT_FOUND);
}

// 重构后
Provider existing = WorkspaceGuard.requireInWorkspace(mapper.selectById(id), ErrorCode.NOT_FOUND);
```

## 安全红线：JWT 密钥启动校验

`JwtSecretValidator` 在应用启动时（`ApplicationReadyEvent`）校验密钥：非 dev 环境拒绝空白、已知默认值、长度 < 16 字符的密钥。

## 参考
- [AUTH-WORKSPACE.md](../guides/AUTH-WORKSPACE.md) — 认证流程、API 接口、前端实现
- [系统风险清单](../../CLAUDE.md#系统风险清单) — 安全红线
