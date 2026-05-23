# 架构设计

## 架构特点

- **多工作空间多租户**：JWT 无状态认证 + ThreadLocal 上下文传递，所有业务数据按 `workspace_id` 隔离
- SSE 长连接支持流式输出
- Redis 缓存 Agent 配置、对话上下文
- MySQL 持久化用户、Agent、对话、消息数据
- Nginx 反向代理，优化 SSE 传输

### 工作空间多租户架构

```
请求 → JwtAuthFilter → CurrentContext.set(userId, workspaceId)
                              │
                              ▼
                    Controller → Service
                              │
                  ┌───────────┼───────────┐
                  │           │           │
                  ▼           ▼           ▼
            list/create    update     checkName
                 │         /delete    Unique
                 │           │           │
                 ▼           ▼           ▼
          LambdaQuery   selectById   selectCount
          + workspace     │            │
              eq          ▼            ▼
                       WorkspaceGuard.requireInWorkspace(entity, ErrorCode)
                              │
                        ┌─────┴─────┐
                        │  null 或   │
                        │ workspace │
                        │  不匹配？  │
                        ├───────────┤
                        │   是 → throw BusinessException
                        │   否 → return entity (继续操作)
                        └───────────┘
                              │
                     finally: CurrentContext.clear()
```

核心链路：
1. **JwtAuthFilter** 从 JWT 提取 `sub`（userId）和 `wid`（workspaceId），注入 `CurrentContext`
2. **CurrentContext** 使用 ThreadLocal 保存请求级 userId/workspaceId
3. **Service 层**所有查询/更新/删除必须过滤 `workspace_id`
4. **WorkspaceGuard** 统一封装 bind/requireInWorkspace/checkNameUnique，消除重复样板代码
5. **Finally 块**清理 ThreadLocal，防止线程池复用导致上下文泄漏
6. **唯一索引**均为 workspace 作用域：`UNIQUE KEY (name, workspace_id)`

### WorkspaceGuard 守卫模式

```java
// 创建：绑定当前工作空间
WorkspaceGuard.bind(entity);

// 更新/删除：一行完成归属校验
Entity existing = WorkspaceGuard.requireInWorkspace(mapper.selectById(id), ErrorCode.NOT_FOUND);

// 名称唯一性检查
WorkspaceGuard.checkNameUnique(mapper,
    Entity::getName, Entity::getWorkspaceId, Entity::getId,
    name, excludeId, ErrorCode.XXX_NAME_DUPLICATE);
```

实体需实现 `WorkspaceAware` 接口（声明 `getWorkspaceId()`/`setWorkspaceId()`），Lombok `@Data` 自动满足。

详细说明：[AUTH-WORKSPACE.md](guides/AUTH-WORKSPACE.md)

---

## 模块结构（多模块 Maven 项目）

```text
eify/
├── eify-app/               # 启动模块，Spring Boot Application
├── eify-auth/               # 认证与工作空间（JWT、用户管理、多租户隔离）
├── eify-provider/           # 模型提供商管理
├── eify-agent/              # Agent 管理与配置
├── eify-chat/               # 对话引擎
├── eify-mcp/                # MCP 工具管理与调用
├── eify-workflow/           # 工作流编排与执行
├── eify-knowledge/          # 知识库与 RAG
├── eify-common/             # 公共模块（工具类、常量、异常、DTO）
├── eify-web/                # Vue 前端
└── deploy/                  # Docker Compose 配置
```

> **版本号管理**：全项目版本号收敛到根 `pom.xml` 的 `<revision>` 属性（Maven CI Friendly Versions），9 个子模块通过 `${revision}` 自动继承。`flatten-maven-plugin` 在构建时将 `${revision}` 解析为实际值写入 `.flattened-pom.xml`（供 CI/CD 使用，不入库）。改版本号只需改一处：`<revision>X.Y.Z-SNAPSHOT</revision>`。

### 模块内部结构（统一规范）

```text
eify-[module]/
├── src/main/java/com/eify/[module]/
│   ├── controller/       # REST 接口
│   ├── service/          # 业务逻辑接口
│   ├── service/impl/     # 业务逻辑实现
│   ├── mapper/           # MyBatis-Plus Mapper
│   ├── domain/           # 领域层
│   │   ├── entity/        # 数据库实体
│   │   ├── vo/            # 视图对象（返回前端）
│   │   └── dto/           # 数据传输对象（接收前端）
│   ├── route/            # 路由解析（可选，如 eify-knowledge 的 EmbeddingRouteResolver）
│   ├── strategy/         # 策略模式实现（可选，如嵌入策略、检索策略）
│   ├── adapter/          # 适配器实现（可选，如 eify-provider 的 ProviderAdapter）
│   ├── config/           # 模块配置
│   ├── client/           # 外部服务客户端（可选）
│   ├── exception/        # 模块异常
│   └── constant/         # 模块常量
│
├── src/main/resources/
│   ├── mapper/           # MyBatis XML（可选）
│   └── *.yml             # 配置文件（可选）
│
└── pom.xml              # 模块 POM
```

**eify-common 特殊结构**：
```text
eify-common/
├── src/main/java/com/eify/common/
│   ├── config/           # 全局配置（MyBatis、Redis、线程池、链路追踪）
│   ├── controller/       # HealthController
│   ├── dto/              # CursorPageRequest
│   ├── entity/           # BaseEntity
│   ├── enums/            # 全局枚举（ResponseCode 已废弃）
│   ├── error/            # ErrorCode 统一错误码
│   ├── exception/        # BusinessException
│   ├── handler/          # 全局异常处理 + MyBatis 类型处理器
│   ├── http/             # LlmHttpClient（OkHttp 封装）
│   ├── log/              # 统一日志系统（REQ/SQL/MSG/SIMPLE/SYS）
│   ├── result/           # Result / PageResult
│   └── util/             # PageHelper / RedisUtil
└── src/main/resources/
    └── logback-spring.xml
```

**eify-auth 模块结构**：
```text
eify-auth/
├── src/main/java/com/eify/auth/
│   ├── config/AuthConfig.java            # BCrypt PasswordEncoder Bean
│   ├── context/CurrentContext.java       # ThreadLocal userId/workspaceId
│   ├── dto/                              # LoginRequest / RegisterRequest / AuthResponse
│   ├── entity/                           # User / Workspace / WorkspaceMember
│   ├── mapper/                           # UserMapper / WorkspaceMapper / WorkspaceMemberMapper
│   └── service/AuthService.java          # 注册/登录/刷新/切换工作空间
└── src/main/java/com/eify/common/workspace/
    ├── WorkspaceAware.java               # 实体 workspace 能力接口
    └── WorkspaceGuard.java               # 守卫工具（bind/requireInWorkspace/checkNameUnique）
```

说明：`WorkspaceGuard`/`WorkspaceAware` 物理上在 eify-auth 模块，但包名为 `com.eify.common.workspace`（保持包路径稳定）。

### 模块技术栈

| 模块 | 关键技术 |
|:---|:---|
| eify-common | MyBatis-Plus、Redis、SLF4J/Logback、Micrometer Tracing、OkHttp |
| eify-auth | Hutool JWT、Spring Security Crypto (BCrypt)、MyBatis-Plus |
| eify-provider | Spring WebFlux (WebClient)、MyBatis-Plus、OkHttp（连接测试 + 模型同步） |
| eify-agent | MyBatis-Plus、Redis（Agent 配置缓存） |
| eify-chat | MyBatis-Plus、Redis（对话上下文）、SSE (SseEmitter) |
| eify-mcp | MCP SDK (`io.modelcontextprotocol.sdk:mcp:1.1.1`) |
| eify-knowledge | pgvector、PostgreSQL、PDFBox 2.0.27、Apache POI 5.2.3、OkHttp（嵌入 API 调用）、Caffeine（双存储设计见 [ADR](ADRs/ADR-0004-dual-storage-mysql-pgvector.md)）|
| eify-workflow | GraalVM Polyglot 24.1.0（JS 引擎）、MyBatis-Plus |
| eify-app | Spring Boot 4.0.6、JwtAuthFilter（iss/aud 校验 + HttpOnly Cookie）、Flyway（MySQL + pgvector）、所有 8 个业务模块 |

---

## 模块依赖关系

### 分层视图

```
┌─────────────────────────────────────────────────────────────┐
│                         eify-app                            │
│                     （启动模块，聚合全部）                      │
└──────────┬──────────────────────────────────────────────────┘
           │ 依赖全部 8 个模块
┌──────────▼──────────────────────────────────────────────────┐
│                        eify-chat                            │
│               （对话引擎，顶层业务模块）                        │
└──┬───┬───┬───┬───┬───┬─────────────────────────────────────┘
   │   │   │   │   │   │
   │   │   │   │   │   └→ eify-workflow  ──→ provider, mcp
   │   │   │   │   └────→ eify-knowledge ──→ provider
   │   │   │   └────────→ eify-mcp
   │   │   └────────────→ eify-provider
   │   └────────────────→ eify-agent    ──→ provider, mcp
   └────────────────────→ eify-auth
           │
┌──────────▼──────────────────────────────────────────────────┐
│                       eify-common                           │
│                  （基础层，无内部依赖）                         │
└─────────────────────────────────────────────────────────────┘
```

### 依赖明细

| 模块 | 直接依赖 | 依赖原因 |
|:---|:---|:---|
| eify-common | — | 基础层，无内部依赖 |
| eify-auth | common | 使用 BaseEntity、ErrorCode、Result |
| eify-provider | common, auth | 使用 CurrentContext 做工作空间隔离 |
| eify-mcp | common, auth | 使用 CurrentContext 做工作空间隔离 |
| eify-agent | common, auth, provider, mcp | 调用提供商测试 Agent、绑定 MCP 工具 |
| eify-knowledge | common, auth, provider | 调用嵌入模型做向量化，EmbeddingRouteResolver 通过 ProviderAdapterFactory 获取嵌入端点 |
| eify-workflow | common, auth, provider, mcp | 执行节点调用 LLM 和 MCP 工具 |
| eify-chat | common, auth, agent, provider, knowledge, mcp, workflow | 对话引擎串联全部能力 |
| eify-app | 全部 8 个模块 | 启动模块，聚合所有依赖 |

### 禁止的依赖

- `eify-common` 不得依赖任何业务模块
- `eify-auth` 不得依赖任何业务模块（最底层业务模块）
- `eify-agent` 与 `eify-workflow` 不得互相依赖（禁止循环）
- 低层模块不得反向依赖高层模块

### 跨模块调用方式

**当前实现**：跨模块通过直接 Maven 依赖调用（如 eify-agent 直接依赖 eify-provider，直接注入 `ProviderService`）。

**未来规划（common/api/）**：
```text
common/api/
├── provider/ProviderClient.java      # 提供商客户端接口
├── provider/LlmProvider.java         # LLM 调用接口
├── provider/EmbeddingProvider.java   # 嵌入调用接口
├── knowledge/KnowledgeService.java   # 知识库服务接口
└── mcp/McpToolService.java          # MCP 工具服务接口
```

目标：解耦业务模块间的直接依赖，通过接口隔离实现类。

---

## 编码规范

### 各层职责边界

| 层 | 职责 | 禁止 |
|:---|:---|:---|
| **Controller** | 接收 HTTP 请求，参数校验，调用 Service，返回响应 | 业务逻辑、直接访问数据库、直接调用外部 API |
| **Service** | 业务逻辑处理、事务控制（`@Transactional`）、跨模块协调 | HTTP 请求处理、直接调用外部 API（使用 client 层） |
| **Mapper** | 数据访问，继承 `BaseMapper<T>` | 业务逻辑、调用其他 Service、事务控制 |
| **Entity** | 与数据库表一一对应，继承 `BaseEntity`，使用 `@TableName`/`@TableId`/`@TableField` | 业务逻辑 |
| **DTO** | 请求/响应对象，使用 JSR-303 注解校验 | - |

### 命名规范

**包命名**：`com.eify.[module].[layer]`

| 层级 | 命名规则 | 示例 |
|:---|:---|:---|
| Controller | `[Module]Controller` | `AgentController` |
| Service 接口 | `[Module]Service` | `AgentService` |
| Service 实现 | `[Module]ServiceImpl` | `AgentServiceImpl` |
| Mapper | `[Module]Mapper` | `AgentMapper` |
| Entity | `[Module]` | `Agent` |
| CreateRequest | `[Module]CreateRequest` | `AgentCreateRequest` |
| UpdateRequest | `[Module]UpdateRequest` | `AgentUpdateRequest` |
| Response | `[Module]Response` | `AgentResponse` |
| Exception | `[Module]Exception` | `AgentNotFoundException` |
| Constant | `[Module]Constant` | `AgentConstant` |

**方法命名**：

| 操作 | 命名 | 示例 |
|:---|:---|:---|
| 创建 | `create` | `create(AgentCreateRequest)` |
| 更新 | `update` | `update(Long id, AgentUpdateRequest)` |
| 删除 | `delete` | `delete(Long id)` |
| 查询单个 | `getById` | `getById(Long id)` |
| 分页查询 | `page` / `pageByCursor` | `page(Integer page, Integer size)` |
| 条件查询 | `selectBy[Condition]` | `selectByName(String name)` |
| 校验 | `validate[Item]` | `validateNameUnique(String name)` |

### DTO 规范

- **CreateRequest**：必填字段使用 `@NotBlank`/`@NotNull`，可设默认值
- **UpdateRequest**：所有字段非必填（部分更新），不设默认值
- **Response**：只包含前端需要的字段，不含内部实现细节

### 异常处理规范

所有错误码统一定义在 `eify-common` 的 `ErrorCode` 枚举中（80+ 错误码），按模块分类：
- **通用**：`SUCCESS(0)`、`BAD_REQUEST(400)`、`UNAUTHORIZED(401)`、`FORBIDDEN(403)`、`NOT_FOUND(404)`、`INTERNAL_ERROR(500)`
- **Provider**：`PROVIDER_NOT_FOUND(2001)`、`PROVIDER_CIRCUIT_OPEN(2004)` 等
- **Workflow**：`WORKFLOW_NODE_TIMEOUT(3001)`、`WORKFLOW_EXECUTION_FAILED(3002)` 等
- **Auth**：`INVALID_TOKEN(1001)`、`WORKSPACE_NOT_FOUND(1002)` 等

> 完整列表见 `eify-common/src/main/java/com/eify/common/error/ErrorCode.java`。

```java
// 业务异常（可预期的错误）
throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");

// 跨模块调用失败
try {
    return providerClient.chat(request);
} catch (Exception e) {
    log.error("调用 LLM 失败", e);
    throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用 LLM 失败");
}
```

### 事务管理规范

- 在 Service 层控制事务，不在 Controller 或 Mapper 层
- 写操作：`@Transactional(rollbackFor = Exception.class)`
- 只读操作：`@Transactional(readOnly = true)`
- 每个事务方法只做一件事

### 日志规范

| 级别 | 使用场景 |
|:---|:---|
| ERROR | 系统错误、不可恢复的异常（会触发告警） |
| WARN | 业务异常、慢查询、潜在问题 |
| INFO | 关键业务操作（创建、更新、删除） |
| DEBUG | 调试信息（仅开发环境） |

```java
// 正确：使用 SLF4J 占位符
log.info("创建 Agent 成功，id={}, name={}", agent.getId(), agent.getName());
log.error("调用 LLM API 失败，agentId={}", agentId, e);

// 错误：字符串拼接（性能差）
log.info("创建 Agent 成功，id：" + agent.getId());  // ❌
```

### 分页规范

项目支持传统分页和游标分页两种模式，使用 `PageHelper` 工具类转换：

```java
// 传统分页（小表 < 10万）
Page<Agent> pageObj = PageHelper.toPage(page, pageSize);
IPage<Agent> result = agentMapper.selectPage(pageObj, null);
return PageHelper.toPageResult(result);

// 游标分页（大表，避免深分页）
LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
        .orderByDesc(Agent::getId);
if (request.getLastId() != null) {
    wrapper.lt(Agent::getId, request.getLastId());
}
wrapper.last("LIMIT " + (request.getPageSize() + 1));
List<Agent> list = agentMapper.selectList(wrapper);
boolean hasMore = list.size() > request.getPageSize();
```

### 线程池隔离规范

所有外部调用必须使用独立线程池隔离，共 5 个线程池，由 `ThreadPoolConfig` 管理：

| 线程池 | Core | Max | Queue | 拒绝策略 | 用途 |
|:---|:---|:---|:---|:---|:---|
| `llmExecutor` | 10 | 50 | 100 | AbortPolicy | 外部 LLM API 调用 |
| `sseExecutor` | 10 | 50 | 100 | AbortPolicy | SSE 流式响应处理 |
| `workflowExecutor` | 4 | 10 | 100 | AbortPolicy | 工作流执行隔离 |
| `mcpExecutor` | 5 | 20 | 50 | AbortPolicy | MCP 外部工具调用 |
| `asyncExecutor` | 5 | 20 | 200 | **CallerRunsPolicy** | 日志异步写入、消息发送等非关键任务 |

> `asyncExecutor` 使用 `CallerRunsPolicy`（非关键任务允许回退到调用线程执行），其余 4 个池均使用 `AbortPolicy`（外部调用拒绝后抛出异常，由上层 `ErrorCode` 统一处理）。

通过 `ObjectProvider<TaskDecorator>` 注入 `ContextPropagatingTaskDecorator`，自动将 `CurrentContext`（ThreadLocal）传播到所有线程池的工作线程。

### 注解使用速查

**Spring 注解**：`@RestController`、`@RequestMapping`、`@Service`、`@Mapper`、`@Component`、`@Configuration`、`@RequiredArgsConstructor`（构造器注入）、`@Transactional`、`@Cacheable`/`@CacheEvict`

**校验注解**：`@NotNull`、`@NotBlank`、`@NotEmpty`、`@Size`、`@Min`/`@Max`、`@Validated`

**Lombok 注解**：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@RequiredArgsConstructor`、`@Slf4j`

**Swagger 注解**：`@Tag`（Controller 分组）、`@Operation`（方法描述）、`@Schema`（模型描述）

---

## 单元测试

### 框架与模式

- **框架**：JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`）
- **模式**：纯单元测试，Mock 所有依赖，不启动 Spring 容器
- **单个测试 < 50ms**

### 测试结构

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentServiceImpl")
class AgentServiceImplTest {

    @Mock AgentMapper agentMapper;
    @InjectMocks AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {
        @Test
        @DisplayName("P0 - 名称重复应抛异常")
        void shouldThrowWhenNameDuplicate() { ... }
    }
}
```

### 命名规范

| 项目 | 规范 |
|:---|:---|
| 测试类 | `{ServiceImpl}Test` |
| 内部类 | `@Nested` 按方法分组 |
| 方法名 | `should[期望行为]When[条件]` |
| 显示名 | `@DisplayName("P0/P1 - 中文描述")` |

### 优先级标注

- **P0**：异常路径（资源不存在、跨 workspace 访问、名称重复、参数校验）
- **P1**：正常流程（CRUD 操作、分页查询、字段绑定）

### 覆盖率（860+ 个测试，67 个测试文件）

| 模块 | 测试数 | 测试文件数 | 主要测试文件 |
|:---|---:|---:|:---|
| eify-common | 153 | 14 | `ResultTest`, `PageResultTest`, `ErrorCodeTest`, `BaseEntityTest`, `LogLevelTest`, `LogTypeTest`, `BusinessExceptionTest`, `RedisUtilTest` 等 |
| eify-provider | 70 | 7 | `ProviderServiceImplTest`, `ProviderAdapterFactoryTest`, `OpenAiCompatibleAdapterTest` 等 |
| eify-agent | 81 | 2 | `AgentServiceImplTest`, `AgentControllerTest` |
| eify-chat | 71 | 7 | `ConversationServiceImplTest`, `MessageServiceImplTest`, `SseEventTest` 等 |
| eify-auth | 77 | 4 | `AuthServiceTest`, `WorkspaceServiceTest`, `WorkspaceGuardTest`, `CurrentContextTest` 等 |
| eify-mcp | 51 | 2 | `McpServerServiceImplTest`, `McpClientServiceImplTest` |
| eify-knowledge | 109 | 9 | `KnowledgeServiceImplTest`, `RetrievalStrategyImplTest` 等 |
| eify-workflow | 169 | 14 | `WorkflowServiceImplTest`, `CodeNodeExecutorTest`, `ConditionNodeExecutorTest` 等 |
| eify-app | 40 | 4 | `JwtAuthFilterTest`, `AuthControllerTest`, `WorkspaceControllerTest` |
| **合计** | **821** | **63** | |

---

## 代码检查清单

- [ ] 包结构符合模块规范，类/方法命名符合规范
- [ ] Controller 只做参数校验和返回封装，业务逻辑在 Service 层
- [ ] Entity 继承 `BaseEntity`，使用 Lombok + MyBatis-Plus 注解
- [ ] DTO 使用 JSR-303 校验注解，Response 只包含必要字段
- [ ] 跨模块调用通过 Maven pom.xml 声明，不引入循环依赖
- [ ] 异常处理使用 `ErrorCode` 枚举
- [ ] 事务在 Service 层控制，外部调用使用线程池隔离
- [ ] 日志使用 SLF4J 占位符，级别正确（ERROR/WARN/INFO/DEBUG）
- [ ] 新 Entity 实现 `WorkspaceAware`，Service 层查询过滤 `workspace_id`
- [ ] 新模块有对应的单元测试（`src/test/java/.../{Service}Test.java`）
