# 部署与 CI/CD

## 目录

- [架构总览](#架构总览)
- [本地开发](#本地开发)
- [Docker Compose](#docker-compose)
- [K8s 生产部署](#k8s-生产部署)
- [CI/CD 流水线](#cicd-流水线)
- [日常运维](#日常运维)
- [故障排查](#故障排查)
- [环境变量参考](#环境变量参考)

---

## 架构总览

```
用户 (浏览器)
    │ HTTPS
    ▼
Nginx Ingress (K8s)
    ├── /api/*  → eify-backend (Spring Boot, 8080)
    └── /*      → eify-frontend (Vue, 80)
                      │
    ┌─────────────────┼─────────────────┐
    ▼                 ▼                  ▼
PostgreSQL 17 (业务+向量)   Redis (缓存)
```

应用是模块化单体：`eify-app` 包含 agent / auth / chat / provider / knowledge / workflow / mcp 所有模块。

**容器运行时**：CCE Turbo 集群使用 containerd（非 Docker），OCI 镜像由 Jenkins + `docker build` 构建后推送至 SWR。

---

## 本地开发

### 快速启动

```bash
./start.sh dev     # 启动开发环境
./stop.sh dev      # 停止
```

`start.sh dev` 等价于 `mvn spring-boot:run -pl eify-app -Dspring-boot.run.profiles=dev`。

### 环境 Profile

| Profile | 配置文件 | 用途 | 部署方式 |
|:---|:---|:---|:---|
| `dev` | `application-dev.yml` | 本地开发 | Docker Compose |
| `test` | `application-test.yml` | K8s 自动化测试 | K8s Deployment |
| `staging` | `application-staging.yml` | K8s 预发布 | K8s Deployment |
| `prod` | `application-prod.yml` | K8s 生产 | K8s Deployment |

Profile 差异（测试 vs 生产）：

| 维度 | dev | test | staging | prod |
|:---|:---|:---|:---|:---|
| DB 连接池 | 30 | 20 | 40 | 50 |
| HikariCP 连接池 | 30 | 20 | 40 | 50 |
| Flyway repair | 开启 | 开启 | 关闭 | 关闭 |
| 日志采样 | 100% | 100% | 50% | 1%（SQL）/ 正常（MSG） |
| 凭据来源 | `.env` 默认值 | K8s Secret | K8s Secret | K8s Secret |

### Maven 常用命令

```bash
mvn compile -q                    # 编译
mvn test -q                       # 运行全部测试
mvn test -pl eify-knowledge -am -q  # 运行单个模块测试
mvn clean package -DskipTests     # 打包
```

### 前端开发

```bash
cd eify-web
npm install && npm run dev        # 开发模式
npm run build                     # 生产构建
npx vue-tsc --noEmit              # 类型检查
npx vitest run                    # 单元测试
```

---

## Docker Compose - 本地环境

### 全栈开发环境

```bash
# 启动（PostgreSQL 17 + Redis + 后端 + 前端）
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d

# 查看日志
docker-compose -f deploy/infra/deploy/docker-compose.yml logs -f

# 重新构建
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d --build

# 停止
docker-compose -f deploy/infra/deploy/docker-compose.yml down
```

### 日志采集链路

```bash
# 启动（ClickHouse + Vector + Grafana + Prometheus）
docker-compose -f deploy/infra/deploy/docker-compose-logging.yml up -d
```

> 数据库迁移由 Flyway 在应用启动时自动执行，无需手动导入 SQL。所有表（业务 + 向量）由单 Flyway 实例管理。

---

## K8s 线上环境

### 集群环境

- **平台**：华为云 CCE Turbo（containerd 运行时）
- **镜像仓库**：华为云 SWR（`swr.cn-south-1.myhuaweicloud.com/eify`）
- **外部服务**：PostgreSQL / Redis 运行在集群外 VM，通过 ConfigMap 指定地址

### 部署清单

```
deploy/k8s/
├── namespace.yaml              # eify 命名空间
├── configmap.yaml              # 非敏感配置（DB 地址、Spring profile）
├── secret.yaml                 # 敏感配置（密码、JWT 密钥）
├── backend-deployment.yaml     # 后端 Deployment（replicas: 1, RollingUpdate）
├── backend-service.yaml        # 后端 ClusterIP Service
├── frontend-deployment.yaml    # 前端 Deployment
├── frontend-service.yaml       # 前端 ClusterIP Service
└── ingress.yaml                # Nginx Ingress（路由、SSE 代理配置）
```

### 首次部署

```bash
# 1. 创建命名空间
kubectl apply -f deploy/k8s/

# 2. 创建 Secret（替换为实际值）
kubectl create secret generic eify-secret -n eify \
  --from-literal=PG_USERNAME=postgres \
  --from-literal=PG_PASSWORD='...' \
  --from-literal=REDIS_PASSWORD='...' \
  --from-literal=JWT_SECRET='...' \
  --from-literal=CRYPTO_KEK='...' \
  --from-literal=EMBEDDING_API_KEY='...'

# 3. 验证
kubectl get pods -n eify
kubectl get ingress -n eify
```

### 配置 Ingress

生产环境关键注解（SSE 流式响应需要）：

```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "600"   # LLM 长连接
nginx.ingress.kubernetes.io/proxy-buffering: "off"      # SSE 必须关闭缓冲
nginx.ingress.kubernetes.io/proxy-body-size: "50m"      # 文件上传
```

### 资源配置

| 组件 | 副本 | CPU Request/Limit | Memory Request/Limit |
|:---|:---|:---|:---|
| eify-backend | 1 | 500m/2000m | 512Mi/4096Mi |
| eify-frontend | 1 | 100m/500m | 128Mi/256Mi |

### 健康检查

```bash
curl http://<ingress-ip>/api/v1/health
```

后端 Deployment 配置了 `readinessProbe` 和 `livenessProbe`，路径 `/actuator/health`。

---

## CI/CD 流水线

### 分支策略

| 分支 | 类型 | 对应环境 | 触发方式 | 镜像标签 |
|:---|:---|:---|:---|:---|
| `feature/*` | 临时 | — | 本地开发 | — |
| `hotfix/*` | 临时 | — | 本地开发 | — |
| `alpha` | 保护 | **dev** | 合并触发 | `alpha-{BUILD}-{commit}` |
| `beta` | 保护 | **test** | 合并触发 | `beta-{BUILD}-{commit}` |
| `rc` | 保护 | **staging** | 合并触发 | `rc-{BUILD}-{commit}` |
| `main` | 保护 | — | 合并触发（自动打 Git Tag，不部署） | — |
| Git Tag | — | **prod** | 手动触发（在 Jenkins 指定 Tag 运行流水线） | Tag 名（如 `v1.0.0.20260520103045.8`） |

**晋升流程**：`feature/hotfix → alpha → beta → rc → main`。各阶段通过 MR/PR 合并到下一级保护分支。

**向下传播**：代码合并到高阶分支后，GitHub Actions 自动向下传播（由 [`.github/workflows/forward-merge.yml`](../.github/workflows/forward-merge.yml) 定义，不属于 Jenkins 部署流水线）：

```
main → rc → beta → alpha
 rc  → beta → alpha
 beta → alpha
```

> 合并提交含 `[forward-merge]` 标记，防止级联循环。

> **Git Tag 自动创建**：`main` 分支合并后，GitHub Actions 从 `.flattened-pom.xml`（`flatten-maven-plugin` 生成）提取已解析的 `<version>` 作为版本号，自动创建 Tag，格式为 `v{版本号}.{BUILD_DATETIME}.{BUILD_NUMBER}`（`BUILD_DATETIME` = `yyyyMMddHHmmss` 时间戳，`BUILD_NUMBER` = GitHub Actions 运行编号）。**生产部署需手动在 Jenkins 指定 Git Tag 触发。**

### 流水线架构

```
Git Push (分支或 Tag)
       │
       ▼
  Jenkins（自动检测分支/Tag → 映射环境）
       │
       ├── main → 构建 + 测试（跳过部署，可通过 Git Tag 手动部署）
       │
       ├── feature / hotfix → 构建 + 测试（跳过部署）
       │
       └── alpha/beta/rc/Tag
              │
              ├── Build: mvn package + npm build
              ├── Test: mvn test
              ├── Docker Build & Push: 构建镜像 → 推送 SWR
              ├── Deploy to K8s: 更新 ConfigMap + Secret + 镜像版本
              └── Health Check: 验证 /api/v1/health
```

### Jenkinsfile

流水线定义在项目根目录 [`Jenkinsfile`](../Jenkinsfile)，包含 5 个 Stage：

| Stage | 条件 | 内容 |
|:---|:---|:---|
| **Build** | 始终 | `mvn clean package -DskipTests` + `npm ci && npm run build` |
| **Test** | `SKIP_TESTS=false` | `mvn test`，JUnit 报告归档 |
| **Docker Build & Push** | alpha/beta/rc/Tag | 构建 `eify-backend` 和 `eify-frontend` 镜像，推送 SWR |
| **Deploy to K8s** | alpha/beta/rc/Tag | 创建/更新 Namespace、ConfigMap、Secret，滚动更新镜像 |
| **Health Check** | alpha/beta/rc/Tag | `kubectl exec` + `wget` 验证 `/api/v1/health` |

**环境自动检测**（`ENVIRONMENT=auto` 时）：

| 触发源 | 映射环境 | 命名空间 |
|:---|:---|:---|
| Git Tag（手动或 Webhook） | `prod` | `eify` |
| `rc` 分支 | `staging` | `eify-staging` |
| `beta` 分支 | `test` | `eify-test` |
| `alpha` 分支 | `dev` | `eify-dev` |
| `main` 分支 | —（通过 Git Tag 手动部署） | — |
| 其他分支 | —（不部署） | — |

**手动部署**：设置 `ENVIRONMENT` 参数为非 `auto` 值可强制指定部署目标。

**凭据管理**：通过 Jenkins `withCredentials` 注入 SWR 登录凭证和 K8s Secret 值（PG/Redis/JWT/CRYPTO/Embedding 共 5 项），不在 Jenkinsfile 中硬编码。

> **构建环境**：Jenkins 运行在 Docker VM 上，通过 `docker.sock` 挂载使用宿主机 Docker daemon。若 Jenkins 运行在 containerd 环境（CCE 集群），需改用 Kaniko 构建镜像。

---

## 日常运维

### 更新镜像

```bash
kubectl set image deployment/eify-backend \
  eify-backend=swr.cn-south-1.myhuaweicloud.com/eify/eify-backend:<TAG> -n eify

kubectl rollout status deployment/eify-backend -n eify --timeout=180s
```

### 回滚

```bash
kubectl rollout undo deployment/eify-backend -n eify
kubectl rollout history deployment/eify-backend -n eify     # 查看历史
kubectl rollout undo deployment/eify-backend -n eify --to-revision=2
```

### 查看日志

```bash
kubectl logs -f deployment/eify-backend -n eify
kubectl logs --tail=100 deployment/eify-backend -n eify
kubectl logs --previous deployment/eify-backend -n eify     # 上一个崩溃容器
```

### 扩缩容

```bash
kubectl scale deployment/eify-backend --replicas=2 -n eify
kubectl get hpa -n eify                                     # HPA 状态
```

### 常用排查

```bash
kubectl get pods -n eify
kubectl describe pod <pod> -n eify                          # 启动失败详情
kubectl exec -it <pod> -n eify -- /bin/bash                  # 进入容器
kubectl get events -n eify --sort-by='.lastTimestamp'       # 集群事件
kubectl top pods -n eify                                     # 资源用量
```

### 重启/配置变更

```bash
kubectl rollout restart deployment/eify-backend -n eify     # ConfigMap 变更后重启
```

---

## 故障排查

### Pod 启动失败

```bash
kubectl describe pod <pod> -n eify | tail -20               # 查看 Events
kubectl logs --previous <pod> -n eify                        # 崩溃前日志
```

**常见原因**：
- `ImagePullBackOff`：SWR 凭证过期或镜像地址错误 → 检查 Secret、配置镜像免密拉取
- `CrashLoopBackOff`：数据库连接失败或 OOM → 检查 ConfigMap 地址、增加 memory limit
- `OOMKilled`：内存不足 → `kubectl describe pod` 查看 Exit Code 137

### Ingress 502/504

- 502：后端 Service 不可达 → `kubectl port-forward svc/eify-backend 8080:8080 -n eify` 测试
- 504：代理超时 → 检查 `proxy-read-timeout`，LLM 场景至少 600s

### Flyway 迁移失败

```bash
kubectl logs deployment/eify-backend -n eify | grep -i flyway
```

检查 Flyway 历史表是否冲突。dev 环境配置了 `repair-on-migrate: true` 可自动修复。

---

## 环境变量参考

完整列表见 [`.env.example`](../.env.example)。生产环境通过 K8s Secret 注入，禁止在 ConfigMap 或 deployment YAML 中硬编码。

| 变量 | 用途 | 必填 |
|:---|:---|:---|
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） | prod 必须 |
| `CRYPTO_KEK` | API Key 加密密钥（AES-256-GCM） | prod 必须 |
| `PG_URL` | PostgreSQL 17 JDBC URL | 是 |
| `PG_USERNAME` / `PG_PASSWORD` | PostgreSQL 认证 | 是 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | 是 |
| `EMBEDDING_API_KEY` | Embedding API 密钥 | 是 |
| `CLICKHOUSE_HOST` / `CLICKHOUSE_PASSWORD` | 日志存储 | 否（可选） |
