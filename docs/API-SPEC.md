# API 接口规范

## 路径规范

RESTful 风格：/api/v1/{资源复数名}

```
GET    /api/v1/providers                    # 列表（分页）
POST   /api/v1/providers                    # 创建
GET    /api/v1/providers/{id}               # 详情
PUT    /api/v1/providers/{id}               # 更新
DELETE /api/v1/providers/{id}               # 删除
POST   /api/v1/providers/{id}/test-connection  # 非 CRUD 操作用动词
```

## 分页规范

### 概述

项目支持两种分页模式，根据数据量选择合适的分页方式：

| 分页模式 | 适用场景 | 请求参数 | 响应字段 | 性能 |
|:---|:---|:---|:---|:---|
| **传统分页** | 小表（< 10 万行） | `page`、`pageSize` | `total`、`page`、`pageSize` | 较慢 |
| **游标分页** | 大表（≥ 10 万行） | `lastId`、`pageSize` | `hasMore`、`pageSize` | 快 6-50 倍 |

### 传统分页（小表）

**请求示例**：
```
GET /api/v1/providers?page=1&pageSize=20
```

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| `page` | Integer | 否 | 1 | 当前页码（从 1 开始） |
| `pageSize` | Integer | 否 | 20 | 每页大小（1-100） |

**响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [...],
    "total": 1000,
    "page": 1,
    "pageSize": 20
  }
}
```

### 游标分页（大表，如 ai_message）

**请求示例**：
```
# 第一页
GET /api/v1/messages/cursor?pageSize=20

# 下一页
GET /api/v1/messages/cursor?pageSize=20&lastId=12345
```

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| `lastId` | Long | 否 | null | 上一页最后一条记录的 ID（首页为 null） |
| `pageSize` | Integer | 否 | 20 | 每页大小（1-100） |

**响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [...],
    "pageSize": 20,
    "hasMore": true
  }
}
```

**字段说明**：
- `list`: 当前页数据列表
- `pageSize`: 每页大小
- `hasMore`: 是否有更多数据（用于前端判断是否显示"加载更多"按钮）

**分页策略**：
1. 查询时多查一条：`LIMIT pageSize + 1`
2. 如果返回数量 > pageSize，说明有更多数据
3. 前端使用上一页返回的最后一条记录的 ID 作为下一页的 `lastId`

### 游标分页优势

| 优势 | 说明 |
|:---|:---|
| **避免 COUNT(*)** | 不执行全表扫描，响应速度快 6-50 倍 |
| **索引优化** | 利用主键索引 `WHERE id < lastId` 快速定位 |
| **无深分页问题** | 不受 OFFSET 累积影响，翻页性能稳定 |
| **带宽节省** | 不返回 total 字段，减少响应体积 |

### 游标分页限制

| 限制 | 说明 |
|:---|:---|
| **不支持跳页** | 只能顺序翻页，不能跳到指定页 |
| **无总页数** | 无法显示总页数和总记录数 |
| **需要排序字段** | 必须有唯一、有序的字段（如主键 ID） |

## SSE 流式响应

流式接口不返回 `Result<T>`，直接返回 `text/event-stream`：

```
POST /api/v1/chat/stream
Content-Type: application/json

# 响应格式：
event: message
data: {"agentId":"123"}

event: message
data: {"content":"你好"}

event: complete
data: {"status":"completed"}

event: error
data: {"message":"调用失败"}

event: timeout
data: {"message":"请求超时"}
```

## 统一响应格式

所有 RESTful 接口返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {...}
}
```

SSE 流式接口直接返回 `text/event-stream`，不包装 `Result<T>`。

## 空值规范
- 列表字段空时返回 `[]`，不返回 `null`
- 字符串字段空时返回 `""`，不返回 `null`
- 对象不存在时返回 `null`

## 错误码规范

四位数字，按模块分段：

```
1000-1999 通用    | 2000-2999 Provider | 3000-3999 Agent
4000-4999 Chat    | 5000-5999 MCP      | 6000-6999 Workflow | 7000-7999 Knowledge
8000-8999 Auth    |
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
| 8009 | 邀请码无效或已过期 | 400 |
| 8010 | 邀请码已达最大使用次数 | 400 |
| 8011 | 不能移除自己 | 400 |
| 8012 | 不能移除工作空间拥有者 | 400 |
| 8013 | 工作空间至少需要一个拥有者 | 400 |
| 8014 | 已经是工作空间成员 | 409 |
| 8015 | 检测到令牌重用，已撤销所有会话 | 401 |

### 通用错误码 (1000-1999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 1000 | 系统错误 | 500 |
| 1001 | 参数校验失败 | 400 |
| 1002 | 未登录 | 401 |
| 1003 | 无权限 | 403 |
| 1004 | 资源不存在 | 404 |
| 1005 | 请求超时 | 408 |
| 1006 | 请求过于频繁 | 429 |
| 1007 | 重复请求 | 409 |

### Provider 错误码 (2000-2999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 2000 | LLM 提供商不存在 | 404 |
| 2001 | LLM 调用失败 | 500 |
| 2002 | LLM 超时 | 504 |
| 2003 | LLM 限流 | 429 |
| 2004 | LLM 熔断器打开 | 503 |
| 2005 | API Key 无效 | 401 |
| 2006 | 不支持的模型 | 400 |
| 2007 | Provider 已被 Agent 使用，无法删除 | 409 |
| 2008 | Provider 已被工作流使用，无法删除 | 409 |

### Agent 错误码 (3000-3999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 3000 | Agent 不存在 | 404 |
| 3001 | Agent 已禁用 | 403 |
| 3002 | Agent 配置无效 | 400 |
| 3003 | Agent 名称已存在 | 409 |
| 3004 | Agent 已被对话使用，无法删除 | 409 |

### Chat 错误码 (4000-4999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 4000 | 对话不存在 | 404 |
| 4001 | 消息不存在 | 404 |
| 4002 | 上下文过长 | 400 |
| 4003 | SSE 连接已关闭 | 500 |
| 4004 | SSE 连接超时 | 504 |

### MCP 错误码 (5000-5999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 5000 | MCP 服务器不存在 | 404 |
| 5001 | MCP 服务器离线 | 503 |
| 5002 | MCP 工具不存在 | 404 |
| 5003 | MCP 工具调用失败 | 500 |
| 5004 | MCP 服务器有 Agent 绑定，无法删除 | 409 |
| 5005 | 单个 Agent 最多绑定 10 个 MCP 工具 | 400 |
| 5006 | MCP 服务器已被工作流使用，无法删除 | 409 |

### Workflow 错误码 (6000-6999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 6000 | 工作流不存在 | 404 |
| 6001 | 工作流执行失败 | 500 |
| 6002 | 工作流配置无效 | 400 |
| 6003 | 工作流已禁用 | 403 |
| 6004 | 工作流名称已存在 | 409 |
| 6005 | 执行记录不存在 | 404 |
| 6006 | 节点执行失败 | 500 |
| 6007 | 执行已取消 | 409 |
| 6008 | 条件表达式求值失败 | 500 |
| 6009 | 工作流已被对话使用，无法删除 | 409 |

### Knowledge 错误码 (7000-7999)

| 错误码 | 说明 | HTTP 状态码 |
|:---|:---|:---|
| 7000 | 知识库不存在 | 404 |
| 7001 | 知识库已禁用 | 403 |
| 7002 | 文档上传失败 | 500 |
| 7003 | 文档解析失败 | 500 |
| 7004 | 向量化失败 | 500 |
| 7005 | 知识库名称已存在 | 409 |
| 7006 | 文档不存在 | 404 |
| 7007 | 选定的嵌入模型不可用，请检查供应商配置 | 400 |
| 7008 | 知识库已被 Agent 使用，无法删除 | 409 |

## Auth API 接口

认证接口使用 `/api/v1/auth` 路径前缀，响应格式为标准 `Result<T>` 包装。JWT 验证失败时，JwtAuthFilter 直接返回 `{"success":false,"error":"..."}` 非标准格式。

| 方法 | 路径 | 说明 | 认证 |
|:---|:---|:---|:---|
| POST | `/api/v1/auth/register` | 用户注册（自动创建默认工作空间） | 否 |
| POST | `/api/v1/auth/login` | 用户登录 | 否 |
| POST | `/api/v1/auth/refresh` | 刷新 Token（优先从 HttpOnly Cookie 读取 refresh token） | 否 |
| GET | `/api/v1/auth/me` | 获取当前用户信息 | 是 |
| POST | `/api/v1/auth/logout` | 退出登录（从 Cookie 提取 token，服务端撤销 family） | 是 |
| GET | `/api/v1/auth/workspaces` | 获取用户的工作空间列表 | 是 |
| POST | `/api/v1/auth/switch-workspace` | 切换工作空间 | 是 |

**登录请求**：
```json
POST /api/v1/auth/login
{ "username": "demo", "password": "demo123" }
```

**登录响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJ...",
    "expiresIn": 1800,
    "user": { "id": 3, "username": "demo", "email": "demo@eify.dev", "displayName": "demo" },
    "workspace": { "id": 3, "name": "demo 的工作空间", "role": "owner" }
  }
}
```

> **注意**：Refresh Token 不再在响应 body 中返回，而是通过 `Set-Cookie` header 设置为 HttpOnly Cookie（`refresh_token=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`）。前端通过 `credentials: 'include'` 自动携带 cookie。

**JWT 载荷结构**：
```json
{
  "sub": 3,          // 用户 ID
  "wid": 4,          // 当前工作空间 ID
  "role": "admin",   // 当前工作空间角色
  "iss": "eify-dev", // 签发者（每环境不同，防止跨环境 token 混用）
  "aud": "eify-api", // 受众
  "iat": 1778596609, // 签发时间（秒）
  "exp": 1778603809  // 过期时间（Access Token 30 分钟，Refresh Token 24 小时）
}
```

**切换工作空间请求**：
```json
POST /api/v1/auth/switch-workspace
{ "workspaceId": 4 }
```

详细说明：[AUTH-WORKSPACE.md](guides/AUTH-WORKSPACE.md)

---

## 完整 API 接口参考

共 93 个接口（16 个 Controller），其中 80 个业务接口 + 12 个测试接口 + 1 个健康检查。

### Provider API（8 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/providers` | 分页查询（支持 name/type/enabled 过滤） |
| GET | `/api/v1/providers/{id}` | 查询详情（含模型配置和健康状态） |
| POST | `/api/v1/providers` | 创建提供商 |
| PUT | `/api/v1/providers/{id}` | 更新提供商 |
| DELETE | `/api/v1/providers/{id}` | 删除提供商（逻辑删除） |
| POST | `/api/v1/providers/{id}/test-connection` | 测试 API 连通性（自动同步模型） |
| GET | `/api/v1/providers/{id}/models` | 查询模型列表（支持 `?category=` 过滤） |
| POST | `/api/v1/providers/{id}/models` | 手动添加模型（适用于无公开模型 API 的供应商） |

### Agent API（7 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/agents` | 分页查询（支持 name/enabled 过滤） |
| GET | `/api/v1/agents/{id}` | 查询详情（含提供商信息） |
| POST | `/api/v1/agents` | 创建 Agent |
| PUT | `/api/v1/agents/{id}` | 更新 Agent |
| DELETE | `/api/v1/agents/{id}` | 删除 Agent（逻辑删除） |
| PUT | `/api/v1/agents/{id}/tools` | 绑定 MCP 工具（全量替换，最多 10 个） |
| POST | `/api/v1/agents/{id}/test-chat` | 发送测试消息验证 Agent 配置 |

### Chat API（13 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/v1/chat/send` | 发送消息，SSE 流式返回 AI 响应 |
| GET | `/api/v1/chat/context/default-rounds` | 获取默认上下文轮数 |
| GET | `/api/v1/chat/health` | Chat 健康检查 |
| POST | `/api/v1/chat/conversations` | 创建对话 |
| GET | `/api/v1/chat/conversations/{id}/messages` | 获取对话消息历史（游标分页） |
| POST | `/api/v1/chat/fix-database` | 临时开发端点 |
| GET | `/api/v1/conversations/user/{userId}` | 用户对话列表（游标分页） |
| GET | `/api/v1/conversations/agent/{agentId}` | Agent 对话列表（游标分页） |
| GET | `/api/v1/conversations/{id}` | 查询对话详情 |
| DELETE | `/api/v1/conversations/{id}` | 删除对话（软删除） |
| GET | `/api/v1/messages/cursor/session/{sessionId}` | 按会话查询消息（游标分页） |
| GET | `/api/v1/messages/cursor/time-range` | 按时间范围查询消息（游标分页） |
| GET | `/api/v1/messages/{id}` | 查询消息详情 |

### MCP Server API（7 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/mcp-servers` | 分页查询 |
| GET | `/api/v1/mcp-servers/{id}` | 查询详情（含工具列表） |
| POST | `/api/v1/mcp-servers` | 创建 MCP 服务器 |
| PUT | `/api/v1/mcp-servers/{id}` | 更新 MCP 服务器 |
| DELETE | `/api/v1/mcp-servers/{id}` | 删除（有 Agent 绑定时拒绝） |
| POST | `/api/v1/mcp-servers/{id}/test` | 测试连通性，自动保存工具列表 |
| POST | `/api/v1/mcp-servers/{id}/debug` | 调试工具调用，返回结果和延迟 |

### Knowledge API（20 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| **知识库管理** | | |
| POST | `/api/v1/knowledge` | 创建知识库 |
| GET | `/api/v1/knowledge/{id}` | 查询知识库详情 |
| PUT | `/api/v1/knowledge/{id}` | 更新知识库 |
| DELETE | `/api/v1/knowledge/{id}` | 删除知识库 |
| GET | `/api/v1/knowledge` | 分页查询知识库列表 |
| PUT | `/api/v1/knowledge/{id}/status` | 切换知识库状态 |
| **文档管理** | | |
| POST | `/api/v1/documents/{knowledgeId}/upload` | 上传文档（触发异步处理） |
| POST | `/api/v1/documents/{knowledgeId}/batch-upload` | 批量上传文档 |
| GET | `/api/v1/documents/knowledge/{knowledgeId}` | 按知识库分页查询文档 |
| GET | `/api/v1/documents/{documentId}` | 查询文档详情/状态 |
| POST | `/api/v1/documents/{documentId}/reprocess` | 重新处理文档 |
| DELETE | `/api/v1/documents/{documentId}` | 删除文档（级联删除 pgvector） |
| GET | `/api/v1/documents/{documentId}/content` | 获取文档原始文本（预览） |
| GET | `/api/v1/documents/{documentId}/chunks` | 获取文档分块列表（预览） |
| GET | `/api/v1/documents/supported-types` | 获取支持的文件类型 |
| **检索增强** | | |
| POST | `/api/v1/retrieval/search` | 语义搜索相关文档分块 |
| POST | `/api/v1/retrieval/chat` | RAG 对话式问答 |
| POST | `/api/v1/retrieval/batch-search` | 批量语义搜索 |
| GET | `/api/v1/retrieval/suggestions` | 获取检索建议 |
| POST | `/api/v1/retrieval/analyze` | 分析检索质量 |

### Workflow API（6 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/workflows` | 分页查询工作流列表 |
| GET | `/api/v1/workflows/{id}` | 查询工作流详情（含节点和边） |
| POST | `/api/v1/workflows` | 创建工作流 |
| PUT | `/api/v1/workflows/{id}` | 更新工作流 |
| DELETE | `/api/v1/workflows/{id}` | 删除工作流 |
| POST | `/api/v1/workflows/{id}/execute` | 执行工作流，返回 SSE 流（5 分钟超时） |

### Workspace API（7 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/v1/workspaces` | 创建新工作空间 |
| POST | `/api/v1/workspaces/{id}/invite-code` | 生成邀请码 |
| POST | `/api/v1/workspaces/join` | 通过邀请码加入工作空间 |
| GET | `/api/v1/workspaces/{id}/members` | 查询工作空间成员列表 |
| DELETE | `/api/v1/workspaces/{id}/members/{userId}` | 移除成员 |
| PUT | `/api/v1/workspaces/{id}/members/{userId}` | 更新成员角色 |
| DELETE | `/api/v1/workspaces/{id}/leave` | 离开工作空间 |

### System API（1 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/health` | 应用健康检查（返回 status、timestamp、version） |

### Locale API（2 个）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/locale` | 获取当前语言设置 |
| PUT | `/api/v1/locale` | 切换语言（en-US / zh-CN） |

### Test Log API（13 个）

仅在 dev 环境可用，用于测试请求日志记录：

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/test/req-log/success` | 正常响应测试 |
| GET | `/api/v1/test/req-log/error` | 错误响应测试 |
| GET | `/api/v1/test/req-log/large` | 大响应体测试 |
| GET | `/api/v1/test/req-log/slow` | 慢响应测试 |
| POST | `/api/v1/test/req-log/post` | POST 请求日志测试 |
| GET | `/api/v1/test/req-log/future` | 异步响应测试 |
| GET | `/api/v1/test/req-log/nested` | 嵌套属性测试 |
| GET | `/api/v1/test/req-log/empty` | 空响应测试 |
| GET | `/api/v1/test/req-log/special` | 特殊字符测试 |
| GET | `/api/v1/test/req-log/business` | 业务异常测试 |
| GET | `/api/v1/test/req-log/random-delay` | 随机延迟测试 |
| GET | `/api/v1/test/req-log/not-found` | 404 测试 |

---

## 代码示例

### Result 类

```java
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
```

### PageResult 类

```java
public class PageResult<T> {
    private List<T> list;
    private Long total;       // 传统分页使用
    private Integer page;     // 传统分页使用
    private Integer pageSize; // 两种模式通用
    private Boolean hasMore;  // 游标分页使用

    // 传统分页构造
    public static <T> PageResult<T> of(List<T> list, Long total, Integer page, Integer pageSize) {
        return new PageResult<>(list, total, page, pageSize, null);
    }

    // 游标分页构造
    public static <T> PageResult<T> ofCursor(List<T> list, Integer pageSize, Boolean hasMore) {
        return new PageResult<>(list, null, null, pageSize, hasMore);
    }
}
```

### CursorPageRequest 类

```java
public class CursorPageRequest {
    private Long lastId;      // 上一页最后一条 ID
    private Integer pageSize;  // 每页大小（默认 20）

    public Long getLastId() {
        return lastId;
    }

    public Integer getPageSize() {
        return pageSize != null ? pageSize : 20;
    }
}
```

### Controller 示例

```java
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    // 传统分页（小表）
    @GetMapping
    public Result<PageResult<AgentResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<AgentResponse> result = agentService.list(page, pageSize);
        return Result.success(result);
    }

    // 游标分页（大表，如消息列表）
    @GetMapping("/cursor")
    public Result<PageResult<MessageResponse>> listByCursor(
            @Valid CursorPageRequest request) {
        PageResult<MessageResponse> result = messageService.listByCursor(request);
        return Result.success(result);
    }
}
```

### Service 示例

```java
@Service
public class AgentServiceImpl {

    // 传统分页实现
    public PageResult<AgentResponse> list(Integer page, Integer pageSize) {
        // 参数校验
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询数据（包含 COUNT）
        Page<Agent> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId());
        Page<Agent> result = agentMapper.selectPage(pageParam, wrapper);

        return PageResult.of(convertToResponse(result.getRecords()),
                result.getTotal(), page, pageSize);
    }

    // 游标分页实现（适用于消息等大表）
    public PageResult<MessageResponse> listByCursor(CursorPageRequest request) {
        // 参数校验
        if (request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 构建查询条件
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .orderByDesc(Message::getId);

        // 游标条件
        if (request.getLastId() != null) {
            wrapper.lt(Message::getId, request.getLastId());
        }

        // 多查一条判断 hasMore
        wrapper.last("LIMIT " + (request.getPageSize() + 1));

        // 执行查询
        List<Message> list = messageMapper.selectList(wrapper);

        // 判断是否有更多数据
        boolean hasMore = list.size() > request.getPageSize();
        if (hasMore) {
            list = list.subList(0, request.getPageSize());
        }

        return PageResult.ofCursor(list, request.getPageSize(), hasMore);
    }
}
```

### SSE Controller 示例

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        return chatService.stream(request);
    }
```

## 接口版本管理

### 当前版本
- API 基础路径：`/api/v1/`
- 已实现版本化的模块：
  - `/api/v1/providers` - 模型提供商管理
  - `/api/v1/agents` - Agent 管理
  - `/api/v1/chat` - 对话引擎
  - `/api/v1/conversations` - 对话管理
  - `/api/v1/messages` - 消息管理
  - `/api/v1/mcp-servers` - MCP 服务器管理
  - `/api/v1/knowledge` - 知识库管理
  - `/api/v1/documents` - 文档管理
  - `/api/v1/retrieval` - 检索增强
  - `/api/v1/workflows` - 工作流管理
  - `/api/v1/auth` - 用户认证
  - `/api/v1/workspaces` - 工作空间管理

### 版本管理原则
- 新接口必须使用 `/api/v1/` 前缀
- 接口变更（不兼容）时，创建新版本 `/api/v2/`
- 废弃接口保留至少 2 个大版本周期

## 分页接口选择指南

| 场景 | 推荐方式 | 判断标准 |
|:---|:---|:---|
| **新模块** | 同时实现两种 | 未来数据量不确定 |
| **小表** | 仅传统分页 | 数据量 < 10 万，增长缓慢 |
| **大表** | 仅游标分页 | 数据量 ≥ 10 万，或高频写入 |
| **历史数据** | 游标分页 | 历史数据不变化，适合缓存 |

## 注意事项

1. **游标分页不支持跳页**：前端只能顺序翻页，不能直接跳到指定页
2. **需要唯一排序字段**：通常使用主键 ID，确保数据顺序唯一
3. **hasMore 准确性**：通过多查一条数据确保判断准确
4. **参数校验统一**：pageSize 限制为 1-100，所有分页模式一致
5. **日志记录**：游标分页日志应记录 lastId、hasMore 等关键信息
