# JWT Access Token 与 Refresh Token 安全设计
`ADR-0010 jwt-token-refresh-security`

# Status
Accepted

# Date
2026-05-22

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

当前 JWT 令牌设计存在以下安全隐患：

| 问题 | 现状 | 风险 |
|---|---|---|
| Access Token 有效期过长 | 2 小时 | 泄露后攻击窗口大，OWASP 建议 15-30min |
| Refresh Token 滚动无上限 | 30 天，每次刷新重置倒计时 | 长期不登录也能维持会话 |
| 无 Refresh Token 重用检测 | 旧 token 刷新后仍可使用 | 攻击者窃取后可与合法用户并行使用，永久持有访问权 |
| 无服务端撤销能力 | 纯无状态 JWT | 无法主动踢出被盗会话 |
| 无 iss/aud 校验 | 不校验签发者和受众 | Token 可跨环境混用（dev token 可用于 prod） |
| Refresh Token 存储在 localStorage | 前端 localStorage 明文存储 | XSS 可读取 token，CSRF 无防护 |
| 登出不清除服务端状态 | 仅前端清除 token | 已签发的 refresh token 仍可继续使用 |

OWASP 2025 和 NIST SP 800-63C 均要求 Refresh Token 必须具备重用检测（Reuse Detection）机制、HttpOnly Cookie 传输、以及服务端撤销能力。

### Decision drivers
1. 满足 OWASP / NIST 令牌安全标准
2. Access Token 泄露后最小化攻击窗口
3. Refresh Token 被窃取时能检测并自动撤销
4. 防止跨环境 token 混用（iss/aud 校验）
5. 防御 XSS 窃取和 CSRF 攻击（HttpOnly Cookie）
6. 登出时服务端撤销 token family
7. 不引入额外数据库表（利用已有 Redis）
8. 前端 401 自动刷新逻辑保持可用

# Considered Options

* **方案 A：仅缩短有效期** — Access Token 改 30min，Refresh Token 改 24h 不滚动。
  - 被拒绝原因：无重用检测，攻击者窃取 refresh token 后仍可正常使用直到过期，无法感知入侵。

* **方案 B：数据库黑名单** — 维护一张 `revoked_token` 表，每次刷新将旧 token 写入黑名单。
  - 被拒绝原因：表会持续膨胀，需要清理任务；每次刷新额外 DB 写入；增加故障点。

* **方案 C：Refresh Token Family + Count 机制（已采纳）** — 每次登录创建 token family，刷新时原子自增计数，旧 token 立即失效；若检测到过期计数被重用，撤销整个 family。

# Decision

采用 **四层防御** 机制：

1. **Refresh Token Family + Count** — Redis 存储 family 状态，原子操作防并发重放
2. **iss/aud 校验** — JWT 过滤器验证 `iss`（签发者）和 `aud`（受众）claims，防止跨环境 token 混用
3. **HttpOnly Cookie** — Refresh Token 通过 `Set-Cookie` 传输，浏览器自动携带，JavaScript 不可读
4. **Logout 撤销** — 登出时 `Redis DEL` 删除 family，立即撤销该链所有 token

### 令牌参数

| 参数 | 值 | 可配置 | 说明 |
|---|---|---|---|
| Access Token 有效期 | 30 分钟 | `auth.jwt.access-expire-seconds` | OWASP 建议 15-30min |
| Refresh Token 绝对超时 | 24 小时 | `auth.jwt.refresh-absolute-ttl-seconds` | 从首次签发算起，不滚动 |
| Refresh Token 单次使用 | 是 | 否 | 每个 token 仅能用一次 |
| 重用检测 | 是 | 否 | 检测到则撤销 family |
| JWT 签发者 | 环境相关 | `auth.jwt.issuer` | dev/staging/prod 各不同 |
| JWT 受众 | eify-api | `auth.jwt.audience` | 防止令牌用于非预期服务 |
| Refresh Token 传输 | HttpOnly Cookie | `auth.jwt.cookie-secure` | Secure + SameSite=Strict + Path=/api/v1/auth |

### Family + Count 流程

```
登录/注册
  │
  ├─ family = UUID.randomUUID()
  ├─ count  = 1
  ├─ Redis: SET refresh_family:{family} 1 EX 86400
  └─ 签发 refresh_token { family, count:1, exp:now+24h }

第 N 次刷新
  │
  ├─ 解析 refresh_token → family, count
  ├─ Redis: GET refresh_family:{family}
  │   ├─ key 不存在 → 拒绝（过期或已撤销）
  │   └─ stored_count == count → INCR → 签发新的 refresh_token { family, count:new, exp不变 }
  │
  └─ 签发新 access_token (30min) + 新 refresh_token (同 family, count+1, 同绝对过期时间)

重用检测
  │
  ├─ 攻击者使用已失效的 refresh_token (count=N)
  ├─ Redis: stored_count=M, M > N
  ├─ 不匹配 → DEL refresh_family:{family}
  └─ 整个 family 撤销 → 所有设备强制重新登录
```

### Redis 原子性保证

使用 `INCR` 命令天然原子，配合 fallback 检查防止竞态：

```
INCR refresh_family:{family} → newCount
if newCount != expectedCount + 1:
    DEL refresh_family:{family}  // 撤销 family
    return REUSE_DETECTED
```

Redis 不可用时降级为 `ConcurrentHashMap` 内存存储（仅 dev/test 环境）。

### 不滚动设计

Refresh Token 的 `exp` 始终指向首次签发的绝对时间（`iat + 24h`），刷新时 `iat` 和 `exp` 均不变。这确保：
- 24 小时后无论多活跃都必须重新登录
- 减少 refresh token 长期流转的泄露面

### iss/aud 校验

JWT 的 `iss`（签发者）和 `aud`（受众）claims 在 `JwtAuthFilter` 中校验：

```
验证流程:
  exp 校验 (已有)
    ├─ 过期 → 401
    └─ 通过 → iss 校验
                ├─ 不匹配 → 401 "令牌签发者无效"
                └─ 通过 → aud 校验
                            ├─ 不匹配 → 401 "令牌受众无效"
                            └─ 通过 → 设置 CurrentContext
```

每个环境使用不同 issuer，防止 dev 环境签发的 token 被用于 prod：

| 环境 | issuer | audience |
|---|---|---|
| dev | eify-dev | eify-api |
| test | eify-test | eify-api |
| staging | eify-staging | eify-api |
| prod | eify-prod | 通过环境变量注入 |

### HttpOnly Cookie 传输 Refresh Token

Refresh Token 不再通过响应 body 返回，改为 `Set-Cookie` header：

```
Set-Cookie: refresh_token=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=86400
```

| 属性 | 值 | 安全作用 |
|---|---|---|
| `HttpOnly` | true | JavaScript 不可读，防 XSS 窃取 |
| `Secure` | 生产 true / 开发 false | 仅 HTTPS 传输 |
| `SameSite` | Strict | 防 CSRF，跨站请求不携带 cookie |
| `Path` | `/api/v1/auth` | 仅 auth 接口携带，减少暴露面 |
| `Max-Age` | 与 refresh token TTL 一致 | 浏览器自动过期清理 |

前端适配：
- `auth.store` 移除 `refreshToken` 相关 localStorage 读写
- `refresh()` API 调用不再传参，cookie 由浏览器自动携带（`credentials: 'include'`）
- `logout()` 清空 HttpOnly cookie（服务端 `Set-Cookie` 设 `Max-Age=0`）
- `/refresh` 端点优先从 cookie 读取 token，fallback 到 body（兼容过渡期）

### Logout 撤销

`POST /logout` 从 cookie 提取 refresh token，解析 `family` 字段后执行 `Redis DEL refresh_family:{family}`，立即撤销该链所有 token：

```java
void logout(String refreshToken) {
    String family = extractFamily(refreshToken);
    if (family != null) {
        revokeFamily(family);  // Redis DEL
    }
}
```

撤销后：
- 该 family 的所有 refresh token（包括未使用的）全部失效
- 已签发的 access token 在 30min 内仍然有效（无状态 JWT 无法撤销，符合 OWASP 接受范围）
- Cookie 同时被清空（`Set-Cookie` 设 `Max-Age=0`）

## Consequences

### 优势
- **攻击窗口缩小**：Access Token 从 2h 缩短到 30min
- **入侵可感知**：重用检测触发后合法用户下次操作被拒绝，意识到 token 被盗
- **攻击者无法持久化**：即使窃取 refresh token，也只能用一次，且与合法用户"竞速"
- **服务端可撤销**：登出或检测到重用时 Redis DEL 立即失效整个 family
- **跨环境隔离**：iss/aud 校验防止 dev token 误用于 prod
- **XSS 防护**：Refresh token 在 HttpOnly Cookie 中，JavaScript 不可读
- **CSRF 防护**：SameSite=Strict 禁止跨站请求携带 cookie
- **无需额外存储**：利用已有 Redis，不引入新数据库表
- **前端适配量小**：401 自动刷新逻辑保持，仅移除 localStorage 中的 refresh token

### 权衡
- **Redis 强依赖**：Redis 不可用时刷新失败（dev/test 有内存降级，生产 Redis 高可用）
- **旧的 refresh token 失效**：部署后所有旧格式 token（无 family/count）将被拒绝，用户需重新登录
- **时钟依赖**：依赖服务器时间戳判断过期，时钟偏差可能导致提前/延迟过期
- **Cookie 限制**：SameSite=Strict 导致跨站跳转时需重新登录；Path 限制使非 auth 接口无法访问 cookie
- **Access Token 仍无状态**：logout 后 access token 在 30min 内仍有效（JWT 固有局限，OWASP 接受范围）

# Details

### 变更文件

| 文件 | 变更 |
|---|---|
| `eify-auth/.../AuthService.java` | 重写 `refresh()`（family/count 机制），新增 `logout()`（family 撤销），`iss`/`aud` 注入到 payload |
| `eify-common/.../ErrorCode.java` | 新增 `TOKEN_REUSE_DETECTED(8015)` |
| `eify-app/.../controller/AuthController.java` | 所有端点注入 `HttpServletResponse`；refresh token 通过 `Set-Cookie` 设置/清除；`/refresh` 从 cookie 读取优先 |
| `eify-app/.../security/JwtAuthFilter.java` | 新增 `iss`/`aud` claims 校验 |
| `eify-app/.../security/JwtSecretValidator.java` | 新增 `issuer` 和 `audience` 非空校验（非 dev 环境） |
| `application-dev.yml` | 新增 `issuer`、`audience`、`access-expire-seconds`、`refresh-absolute-ttl-seconds`、`cookie-secure` |
| `application-staging.yml` | 新增 `issuer: eify-staging`、`cookie-secure: true` |
| `application-prod.yml` | 新增 `issuer: eify-prod`、`cookie-secure: true` |
| `application-test.yml` (test resources) | 新增 `issuer: eify-test`、`audience: eify-api`、`cookie-secure: false` |
| `eify-web/src/store/auth.ts` | 移除 `refreshToken` localStorage 逻辑；`tryRefreshToken()` 不传参 |
| `eify-web/src/api/auth.ts` | `refresh()` 无参调用；`AuthResponse.refreshToken` 标记 deprecated |
| `eify-web/src/utils/request.ts` | 401 拦截器用 `fetch` + `credentials: 'include'` 刷新 |
| `eify-auth/.../AuthServiceTest.java` | 适配新 token 格式（iss/aud/family/count），更新 expiresIn 断言，更新 logout 测试 |
| `eify-app/.../JwtAuthFilterTest.java` | 适配 iss/aud 注入和校验 |
| `eify-app/.../AuthControllerTest.java` | 适配 `HttpServletResponse` 参数和 cookie 读取逻辑 |
| `eify-app/.../ProviderControllerIntegrationTest.java` | `createTestToken()` 添加 iss/aud claims |
| `eify-app/.../ChatControllerIntegrationTest.java` | `createTestToken()` 添加 iss/aud claims |

### Token Payload

Access Token:
```json
{
  "sub": 1,
  "wid": 10,
  "role": "owner",
  "iss": "eify-dev",
  "aud": "eify-api",
  "iat": 1716400000,
  "exp": 1716401800
}
```

Refresh Token:
```json
{
  "sub": 1,
  "wid": 10,
  "role": "owner",
  "iss": "eify-dev",
  "aud": "eify-api",
  "iat": 1716400000,
  "exp": 1716486400,
  "family": "a1b2c3d4-...",
  "count": 3
}
```

### 配置项

```yaml
auth:
  jwt:
    secret: ${JWT_SECRET}
    issuer: eify-dev                          # JWT iss claim，每环境不同
    audience: eify-api                        # JWT aud claim
    access-expire-seconds: 1800               # Access Token 30 分钟
    refresh-absolute-ttl-seconds: 86400       # Refresh Token 24 小时
    cookie-secure: false                      # dev=false, staging/prod=true
```

## 参考
- [OWASP - JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [NIST SP 800-63C - Federation and Assertions](https://pages.nist.gov/800-63-3/sp800-63c.html)
- [Auth0 - Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation)
