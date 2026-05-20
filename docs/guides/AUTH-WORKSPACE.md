# 用户认证与工作空间多租户系统

> 本文档描述 Eify 的用户认证体系、多工作空间（Multi-Workspace）架构设计、数据隔离机制及前端实现。

---

## 目录

- [系统概述](#系统概述)
- [数据模型](#数据模型)
- [认证流程](#认证流程)
- [工作空间隔离](#工作空间隔离)
- [API 接口](#api-接口)
- [前端架构](#前端架构)
- [约束与注意事项](#约束与注意事项)

---

## 系统概述

### 核心设计

Eify 采用**无状态 JWT 认证 + 工作空间多租户**架构：

```
┌──────────────────────────────────────────────────────────────┐
│                        用户 (User)                            │
│                            │                                  │
│              ┌─────────────┼─────────────┐                    │
│              │             │             │                    │
│       工作空间 A      工作空间 B      工作空间 C               │
│       (owner)        (admin)        (member)                 │
│          │              │              │                      │
│    ┌─────┼─────┐  ┌────┼────┐   ┌────┼────┐                 │
│   Agent  Chat  │ Agent Chat │  Agent Chat  │                 │
│  Provider MCP  │  ...       │   ...         │                │
└──────────────────────────────────────────────────────────────┘
```

- 一个用户可加入多个工作空间（通过 `ai_workspace_member` 表）
- 每个工作空间的数据完全隔离（所有业务表包含 `workspace_id` 字段）
- 用户通过切换工作空间来切换数据上下文
- JWT Token 中包含当前工作空间 ID（`wid` 声明），后端根据此值过滤数据

### 技术选型

| 组件 | 技术 | 说明 |
|:---|:---|:---|
| 认证方式 | JWT（Hutool JWT） | 无状态，token 中包含 userId + workspaceId |
| 密码加密 | BCrypt（Spring Security） | 单向哈希 |
| 上下文传递 | ThreadLocal（CurrentContext） | 请求级 userId/workspaceId 传递 |
| 过滤器 | Servlet Filter（JwtAuthFilter） | 解析 JWT，设置上下文 |
| 前端状态 | Pinia Store（authStore） | 响应式管理 token、用户、工作空间状态 |
| 前端持久化 | localStorage | 存储 accessToken、refreshToken |

---

## 数据模型

### 表结构

#### ai_user（用户表）

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | BIGINT UNSIGNED | 主键（自增） |
| `username` | VARCHAR(50) | 用户名（唯一） |
| `email` | VARCHAR(100) | 邮箱 |
| `password` | VARCHAR(255) | 密码（BCrypt 加密） |
| `display_name` | VARCHAR(50) | 显示名称 |
| `avatar_url` | VARCHAR(500) | 头像 URL |
| `status` | TINYINT | 状态：0=禁用，1=正常 |
| `last_login_at` | DATETIME | 最后登录时间 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `deleted` | TINYINT | 软删除：0=正常，1=删除 |

#### ai_workspace（工作空间表）

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | BIGINT UNSIGNED | 主键（自增） |
| `name` | VARCHAR(100) | 工作空间名称 |
| `description` | VARCHAR(500) | 描述 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `deleted` | TINYINT | 软删除：0=正常，1=删除 |

#### ai_workspace_member（工作空间成员表）

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | BIGINT UNSIGNED | 主键（自增） |
| `workspace_id` | BIGINT UNSIGNED | 工作空间 ID（FK → ai_workspace） |
| `user_id` | BIGINT UNSIGNED | 用户 ID（FK → ai_user） |
| `role` | VARCHAR(20) | 角色：`owner` / `admin` / `member` |
| `joined_at` | DATETIME | 加入时间 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `deleted` | TINYINT | 软删除：0=正常，1=删除 |

#### 所有业务表均含 workspace_id

业务数据（Agent、Provider、Chat、Workflow、MCP、Knowledge）的每张表都包含 `workspace_id BIGINT UNSIGNED` 字段，用于数据隔离。

### 实体关系

```
User (1) ──── (N) WorkspaceMember (N) ──── (1) Workspace
                           │
                      role: owner | admin | member

每个业务实体 (Agent/Provider/Conversation/...) (N) ──── (1) Workspace
```

### JWT 载荷结构

```json
{
  "sub": 3,         // 用户 ID (userId)
  "wid": 4,         // 当前工作空间 ID (workspaceId)
  "role": "admin",  // 当前工作空间角色
  "iat": 1778596609, // 签发时间（秒）
  "exp": 1778603809  // 过期时间（秒，默认 2 小时）
}
```

---

## 认证流程

### 登录流程

```
┌──────────┐     POST /api/auth/login      ┌─────────────┐
│  前端     │ ─────────────────────────▶  │  AuthService │
│ (Vue)    │                              │  (Spring)    │
│          │                              │              │
│          │                              │ 1. 验证用户名/密码   │
│          │                              │ 2. 查询用户第一个     │
│          │                              │    工作空间         │
│          │                              │ 3. 签发 JWT        │
│          │ ◀─────────────────────────   │    (sub + wid)    │
│          │    AuthResponse {            │              │
│          │      accessToken,            │              │
│          │      refreshToken,           │              │
│          │      user,                   │              │
│          │      workspace               │              │
│          │    }                         │              │
└──────────┘                              └─────────────┘
```

### 请求认证流程（JwtAuthFilter）

```
请求到达
  │
  ├─ 公开接口？(/api/auth/login, /register, /refresh)
  │    └─ 是 → 直接放行（无认证）
  │
  ├─ 携带 Authorization: Bearer <JWT>？
  │    ├─ 是 → 解析 JWT
  │    │       ├─ 验证签名
  │    │       ├─ 提取 sub → userId
  │    │       ├─ 提取 wid → workspaceId
  │    │       └─ CurrentContext.set(userId, workspaceId)
  │    │
  │    └─ 否 → 携带 X-User-Id？（兼容旧模式）
  │             ├─ 是 → CurrentContext.set(userId, 1L)
  │             └─ 否 → 无上下文（Service 层决定是否拒绝）
  │
  └─ chain.doFilter() → Controller → Service
       └─ finally: CurrentContext.clear()
```

### Token 刷新流程

```
AccessToken 过期（2 小时）
  │
  └─ POST /api/auth/refresh { refreshToken }
       │
       ├─ 验证 refreshToken（有效期 30 天）
       ├─ 签发新 accessToken + refreshToken
       └─ 返回 AuthResponse
```

### 注册流程

```
POST /api/auth/register { username, email, password }
  │
  ├─ 检查用户名/邮箱唯一性
  ├─ 创建用户（BCrypt 加密密码）
  ├─ 创建默认工作空间（"{username} 的工作空间"）
  ├─ 关联用户为 owner 角色
  └─ 签发 JWT → 返回 AuthResponse
```

---

## 工作空间隔离

### 后端隔离机制

#### 上下文注入

`JwtAuthFilter` 从 JWT 中提取 `wid` 声明，设置到 `CurrentContext`：

```java
Long userId = Long.valueOf(jwt.getPayload("sub").toString());
Long workspaceId = Long.valueOf(jwt.getPayload("wid").toString());
CurrentContext.set(userId, workspaceId);
```

#### Service 层过滤

所有 Service 方法必须使用 `CurrentContext.getWorkspaceId()` 过滤数据。自 `WorkspaceGuard` 引入后，推荐使用统一工具方法替代手动样板代码：

```java
// 列表查询 — workspace 过滤（仍可直接使用 LambdaQueryWrapper）
LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
    .eq(Provider::getWorkspaceId, CurrentContext.getWorkspaceId())
    .orderByDesc(Provider::getId);

// 详情查询 — workspace + id 双重验证（单次查询）
Provider provider = providerMapper.selectOne(new LambdaQueryWrapper<Provider>()
    .eq(Provider::getId, id)
    .eq(Provider::getWorkspaceId, CurrentContext.getWorkspaceId()));

// 更新/删除 — 使用 WorkspaceGuard 一行完成校验
Provider existing = WorkspaceGuard.requireInWorkspace(
    providerMapper.selectById(id), ErrorCode.NOT_FOUND);

// 创建 — 使用 WorkspaceGuard 绑定 workspace
WorkspaceGuard.bind(entity);

// 名称唯一性校验
WorkspaceGuard.checkNameUnique(mapper,
    Entity::getName, Entity::getWorkspaceId, Entity::getId,
    name, excludeId, ErrorCode.XXX_NAME_DUPLICATE);
```

详见 [工作空间守卫模式](#工作空间守卫模式-workspaceguard--workspaceaware)。

#### 唯一性约束（Workspace 作用域）

数据库唯一键必须是 workspace 作用域内的唯一：

```sql
-- ✅ 正确：workspace 内唯一
UNIQUE KEY `uk_name_workspace` (`name`, `workspace_id`)

-- ❌ 错误：全局唯一（不同 workspace 不能同名）
UNIQUE KEY `uk_name` (`name`)
```

影响的表：
- `provider.uk_name_workspace`
- `ai_provider.uk_code_workspace`
- `ai_agent.uk_name_workspace`
- `ai_workflow.uk_name_workspace`
- `knowledge_base.uk_name_workspace`

#### 默认值处理

`CurrentContext.getWorkspaceId()` 在未设置时返回 `1L`（兼容旧数据）。

```java
public static Long getWorkspaceId() {
    Long wid = WORKSPACE_ID.get();
    return wid != null ? wid : 1L;
}
```

### 前端隔离机制

#### 状态管理（authStore）

```
authStore (Pinia)
├── accessToken       ← localStorage
├── refreshToken      ← localStorage
├── user              ← API 响应
├── workspace         ← 当前工作空间（API 响应）
├── workspaces        ← 用户所有工作空间列表
├── refreshKey        ← 工作空间切换计数器（触发页面刷新）
│
├── hydrate()         → 从 token 恢复用户/工作空间信息
├── login()           → 登录 → saveAuth()
├── switchWorkspace() → 切换工作空间 → saveAuth() → window.location.reload()
└── fetchWorkspaces() → 获取用户的工作空间列表
```

#### 工作空间切换流程

```
用户点击切换工作空间
  │
  ├─ authStore.switchWorkspace(targetId)
  │    ├─ POST /api/auth/switch-workspace { workspaceId }
  │    ├─ 后端验证用户是该工作空间成员
  │    ├─ 签发新 JWT（wid = targetId）
  │    ├─ 返回 AuthResponse
  │    │
  │    ├─ saveAuth(resp)
  │    │    ├─ workspace.value = resp.workspace
  │    │    ├─ localStorage.setItem('accessToken', resp.accessToken)
  │    │    └─ localStorage.setItem('refreshToken', resp.refreshToken)
  │    │
  │    ├─ refreshKey.value++（触发组件更新）
  │    └─ setTimeout → window.location.reload()
  │
  └─ 页面重载
       ├─ hydrate() → GET /api/auth/me（使用新 token）
       ├─ 恢复用户信息 + 新工作空间
       └─ 所有组件重新挂载，加载新工作空间数据
```

#### 列表页刷新机制

所有列表页（Provider、Agent、Workflow、MCP、Knowledge、Chat）通过以下机制确保切换后数据更新：

1. **RouterView key 变更**：`App.vue` 中 `<RouterView :key="authStore.refreshKey" />`，工作空间切换时 key 变更导致组件销毁重建
2. **refreshKey watch**：`EifyListPage.vue` 监听 `authStore.refreshKey` 变化，自动清空条件并刷新列表
3. **页面重载**：`window.location.reload()` 确保所有 JS 状态完全重置

### 工作空间守卫模式 (WorkspaceGuard & WorkspaceAware)

为解决各 ServiceImpl 中重复的 workspace 校验样板代码，提炼了统一守卫工具。

#### WorkspaceAware 接口

所有参与工作空间多租户隔离的实体实现此接口，声明其具有 `workspaceId` 属性的读写能力：

```java
public interface WorkspaceAware {
    Long getWorkspaceId();
    void setWorkspaceId(Long workspaceId);
}
```

已实现的实体（9 个）：`Provider`、`ModelConfig`、`Agent`、`McpServer`、`Workflow`、`KnowledgeBase`、`Document`、`Conversation`、`Message`。

#### WorkspaceGuard 工具类

提供 4 个静态方法，从 `CurrentContext.getWorkspaceId()` 获取当前工作空间 ID：

| 方法 | 用途 | 示例 |
|:---|:---|:---|
| `bind(entity)` | 创建时将 workspaceId 绑定到实体 | `WorkspaceGuard.bind(provider)` |
| `requireInWorkspace(entity, errorCode)` | 校验实体非空且属于当前 workspace | `WorkspaceGuard.requireInWorkspace(mapper.selectById(id), ERR)` |
| `checkNameUnique(mapper, ...)` | 校验名称在 workspace 内唯一 | `WorkspaceGuard.checkNameUnique(mapper, ...)` |

**设计原则**：
- 所有方法零依赖（仅依赖 CurrentContext + MyBatis-Plus SFunction）
- 不引入新的抽象层，不改变既有查询结构
- 替换的目标是「重复的 if-null-check + workspace-compare」样板，而非 LambdaQueryWrapper 本身

**重构前后对比**：

```java
// 重构前（7 行样板，每个 Service 重复）
Provider existing = providerMapper.selectById(id);
if (existing == null) {
    throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
}
if (!existing.getWorkspaceId().equals(CurrentContext.getWorkspaceId())) {
    throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
}

// 重构后（1 行）
Provider existing = WorkspaceGuard.requireInWorkspace(
    providerMapper.selectById(id), ErrorCode.NOT_FOUND);
```

**适用范围**：
- ServiceImpl 的 update/delete 操作（替换 selectById + workspace compare）
- ServiceImpl 的 create 操作（替换 `entity.setWorkspaceId(CurrentContext.getWorkspaceId())`）
- ServiceImpl 的 checkNameUnique 方法（替换 LambdaQueryWrapper 构建）
- 不适用于 list 查询（LambdaQueryWrapper 直接使用更清晰）
- 不适用于特殊业务逻辑（如跨 workspace 的操作）

#### ConversationMapper.xml SQL 片段复用

所有 4 个查询共享 `<sql>` 片段，修改列或过滤条件时无需逐个修改：

```xml
<sql id="baseColumns">id, user_id, agent_id, title, status, created_at, updated_at</sql>
<sql id="workspaceFilter">workspace_id = #{workspaceId} AND deleted = 0</sql>
<sql id="cursorCondition">(updated_at &lt; #{lastTimestamp} OR (...))</sql>
<sql id="orderBy">ORDER BY updated_at DESC, id DESC</sql>
```

#### WorkflowServiceImpl 安全修复

`getWorkflowOrThrow()` 原使用 `selectById(id)` 不带 workspace 过滤，存在跨工作空间数据访问漏洞。已修复为：

```java
private Workflow getWorkflowOrThrow(Long id) {
    Workflow workflow = workflowMapper.selectOne(
        new LambdaQueryWrapper<Workflow>()
            .eq(Workflow::getId, id)
            .eq(Workflow::getWorkspaceId, CurrentContext.getWorkspaceId()));
    if (workflow == null) {
        throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
    }
    return workflow;
}
```

---

## API 接口

### 认证接口

| 方法 | 路径 | 说明 | 认证 |
|:---|:---|:---|:---|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录 | 否 |
| POST | `/api/auth/refresh` | 刷新 Token | 否 |
| GET | `/api/auth/me` | 获取当前用户信息 | 是 |
| POST | `/api/auth/logout` | 退出登录 | 是 |
| GET | `/api/auth/workspaces` | 获取用户的工作空间列表 | 是 |
| POST | `/api/auth/switch-workspace` | 切换工作空间 | 是 |

### 请求/响应格式

认证接口使用标准 `Result<T>` 包装：

**登录请求**：
```json
POST /api/auth/login
{
  "username": "demo",
  "password": "demo123"
}
```

**登录响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 7200,
    "user": {
      "id": 3,
      "username": "demo",
      "email": "demo@eify.dev",
      "displayName": "demo",
      "avatarUrl": null
    },
    "workspace": {
      "id": 3,
      "name": "demo 的工作空间",
      "role": "owner"
    }
  }
}
```

**切换工作空间请求**：
```json
POST /api/auth/switch-workspace
{
  "workspaceId": 4
}
```

**工作空间列表响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 3, "name": "demo 的工作空间", "role": "owner" },
    { "id": 4, "name": "测试项目空间", "role": "admin" }
  ]
}
```

### Auth 错误码 (8000-8999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 8000 | 用户不存在 | 404 |
| 8001 | 用户已被禁用 | 403 |
| 8002 | 用户名或邮箱已存在 | 409 |
| 8003 | 密码错误 | 401 |
| 8004 | 登录已过期（Token 过期） | 401 |
| 8005 | 令牌无效 | 401 |
| 8006 | 工作空间不存在 | 404 |
| 8007 | 不是该工作空间成员 | 403 |
| 8008 | 无权访问该工作空间 | 403 |

---

## 前端架构

### 组件层次

```
App.vue (:key="authStore.refreshKey")
├── EifySidebar.vue          (导航菜单，无工作空间数据)
├── EifyHeader.vue           (工作空间切换器 + 用户菜单)
│   ├── 工作空间下拉菜单
│   │   ├── 当前工作空间（高亮 + 角色标识）
│   │   └── 其他工作空间（切换按钮）
│   └── 用户下拉菜单（个人中心 + 退出）
├── RouterView
│   ├── ProviderList.vue     → EifyListPage (watch refreshKey)
│   ├── AgentList.vue        → EifyListPage (watch refreshKey)
│   ├── WorkflowList.vue     → EifyListPage (watch refreshKey)
│   ├── McpServerList.vue    → EifyListPage (watch refreshKey)
│   ├── KnowledgeView.vue    → EifyListPage (watch refreshKey)
│   ├── ChatView.vue         (独立 watch refreshKey)
│   └── ProfileView.vue      (工作空间管理 + 信息展示)
└──
```

### 工作空间切换 UI

#### Header 工作空间切换器

- 显示当前工作空间名称（带紫色渐变图标）
- 下拉菜单显示所有可用工作空间
- 当前工作空间带 `el-tag` 角色标识
- 其他工作空间点击即可切换
- 底部"管理工作空间"跳转至 Profile 页面

#### Profile 页工作空间管理

- 展示用户所有工作空间卡片
- 当前工作空间紫色边框高亮 + "当前"标签
- 其他工作空间显示"切换"按钮（带 loading 状态）

### 角色标识

| 角色 | 中文 | 说明 |
|:---|:---|:---|
| `owner` | 拥有者 | 注册时自动创建的工作空间，拥有完全权限 |
| `admin` | 管理员 | 被邀请加入的管理角色 |
| `member` | 成员 | 普通成员 |

---

## 约束与注意事项

### 🔴 核心约束

| 约束 | 说明 | 违反后果 |
|:---|:---|:---|
| **Service 层必须过滤 workspace_id** | 所有查询/更新/删除必须带上 `workspace_id = CurrentContext.getWorkspaceId()` | 跨工作空间数据泄露 |
| **更新/删除必须先验证 workspace 归属** | 不能直接用 `selectById(id)` 后修改，必须先验证 `workspace_id` 匹配 | 跨工作空间数据篡改 |
| **唯一索引必须是 workspace 作用域** | `UNIQUE KEY (name, workspace_id)`，不是全局唯一 | 不同工作空间不能使用同名资源 |
| **JWT 必须包含 wid** | `buildAuthResponse` 必须 `payload.put("wid", workspace.getId())` | 后端无法识别工作空间上下文 |
| **ThreadLocal 必须清理** | `CurrentContext.clear()` 必须在 `finally` 块中调用 | 线程池复用导致上下文泄漏 |
| **切换工作空间后必须全量刷新** | 使用 `window.location.reload()` 确保所有状态重置 | 前端展示上一工作空间的旧数据 |

### ✅ 开发规范

- 新建业务模块时，Service 的 list/getById/update/delete 方法必须包含 workspace 过滤
- 新建业务表时，必须包含 `workspace_id BIGINT UNSIGNED` 字段及对应索引
- 新建唯一索引时，必须与 `workspace_id` 组合
- 前端新建列表页时，优先使用 `EifyListPage` 组件（已内置工作空间切换监听）
- `CurrentContext.getWorkspaceId()` 有默认值 `1L`，测试环境注意此行为

### 常见场景

**Q: 如何添加用户到第二个工作空间？**

直接在 `ai_workspace_member` 表插入记录：
```sql
INSERT INTO ai_workspace_member (workspace_id, user_id, role, joined_at)
VALUES (4, 3, 'admin', NOW());
```

**Q: 调试时如何确认当前工作空间？**

检查请求头 `Authorization: Bearer <JWT>`，解码 JWT 查看 `wid` 字段。

**Q: 工作空间切换后数据未刷新？**

1. 确认浏览器控制台无 JS 错误
2. 确认 DevTools Network 中 API 请求携带了新的 Token
3. 确认后端日志中 `workspace_id` 过滤条件正确
4. 如果前端软刷新（RouterView key）未生效，`window.location.reload()` 作为兜底

### 相关文件清单

| 层 | 文件 | 作用 |
|:---|:---|:---|
| Entity | `eify-auth/.../auth/entity/User.java` | 用户实体 |
| Entity | `eify-auth/.../auth/entity/Workspace.java` | 工作空间实体 |
| Entity | `eify-auth/.../auth/entity/WorkspaceMember.java` | 成员关联实体 |
| Entity | `eify-auth/.../auth/entity/WorkspaceInvite.java` | 工作空间邀请码实体 |
| DTO | `eify-auth/.../auth/dto/AuthResponse.java` | 认证响应 DTO |
| Context | `eify-auth/.../auth/context/CurrentContext.java` | ThreadLocal 上下文 |
| Guard | `eify-auth/.../common/workspace/WorkspaceGuard.java` | 工作空间守卫工具（bind/requireInWorkspace/checkNameUnique） |
| Interface | `eify-auth/.../common/workspace/WorkspaceAware.java` | 实体 workspace 能力接口 |
| Filter | `eify-app/.../security/JwtAuthFilter.java` | JWT 认证过滤器 |
| Service | `eify-auth/.../auth/service/AuthService.java` | 认证业务逻辑 |
| Service | `eify-auth/.../auth/service/WorkspaceService.java` | 工作空间业务逻辑 |
| Controller | `eify-app/.../controller/AuthController.java` | 认证 REST 接口 |
| Controller | `eify-app/.../controller/WorkspaceController.java` | 工作空间 REST 接口 |
| Store | `eify-web/src/store/auth.ts` | 前端 Pinia 认证状态 |
| UI | `eify-web/src/components/EifyHeader.vue` | 顶栏工作空间切换器 |
| UI | `eify-web/src/views/ProfileView.vue` | 个人中心工作空间管理 |
| UI | `eify-web/src/components/EifyListPage.vue` | 列表页基类（工作空间监听） |
