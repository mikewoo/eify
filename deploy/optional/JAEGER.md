# Jaeger 可选工具指南

> **注意**：Jaeger 是**可选工具**。Eify 的 TraceId/SpanId 功能独立运行，不需要 Jaeger。

---

## 概述

Jaeger 提供**分布式追踪可视化**，用于查看调用链和服务依赖图。

**架构关系**：
```
Micrometer Tracing + Brave  →  生成 TraceId/SpanId  →  日志系统（必需）
                                                              │
                                                              ▼
                                                     OTLP Exporter（可选）
                                                              │
                                                              ▼
                                                        Jaeger（可视化）
```

**何时使用**：
- ✅ 多服务架构调试
- ✅ 性能瓶颈分析
- ❌ 单服务 MVP（可选）

---

## 快速启动

```bash
# 启动 Jaeger
./deploy/optional/start-jaeger.sh start

# 打开 UI
./deploy/optional/start-jaeger.sh ui
# 或访问 http://localhost:16686
```

---

## 管理命令

```bash
./deploy/optional/start-jaeger.sh start    # 启动服务
./deploy/optional/start-jaeger.sh stop     # 停止服务
./deploy/optional/start-jaeger.sh status   # 查看状态
./deploy/optional/start-jaeger.sh logs     # 查看日志
./deploy/optional/start-jaeger.sh ui       # 打开 UI
```

---

## 服务端点

| 端口 | 说明 |
|:---|:---|
| 16686 | Jaeger UI |
| 4317 | OTLP gRPC |
| 4318 | OTLP HTTP |

---

## 参见

- [../docs/LOGGING.md](../docs/guides/LOGGING.md) - TraceId/SpanId 核心功能
- [../infra/deploy/README.md](../infra/deploy/README.md) - 日志系统部署

---

**更新日志**:
- **2026-04-23**: 移至 `deploy/optional/`，明确标记为可选工具
