# i18n 国际化：JSON 交换格式 + 构建时嵌入
`ADR-0002 i18n-json-exchange-format`

# Status
Accepted

# Date
2026-05-18

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

Eify 即将开源，需要规范国际化（i18n）管理方式。当前翻译文本仅以 TypeScript 文件（`src/i18n/locales/*.ts`）存在，编译进前端 JS bundle。需要对贡献者友好的翻译交换格式，同时保持开发时的类型安全。

### Decision drivers
- 翻译文件需对开源贡献者友好（非 TS 开发者也能编辑）
- 开发时需保持类型安全（key 遗漏编译期报错）
- 为未来翻译平台（Crowdin/Weblate）集成做好准备
- 不增加运行时开销和运维复杂度

# Considered Options
* **方案 A：保持现状** — TS 文件作为唯一格式，无 JSON 输出。对非 TS 贡献者不友好。
* **方案 B：运行时动态加载** — 后端提供 i18n API，前端 fetch 翻译，K8s ConfigMap 热更新。增加后端 API、WatchService、缓存失效等基础设施，每个都是潜在故障点；破坏 GitOps 原则（ConfigMap 绕过 Git 修改翻译）。
* **方案 C：JSON 交换格式 + 构建时嵌入** — TS 文件保持为代码级真相来源，构建脚本输出 JSON 到 `deploy/i18n/`，翻译嵌入构建产物不变。参照 GitLab、Grafana、VS Code 等大型开源项目实践。

# Decision

**选择方案 C：JSON 交换格式 + 构建时嵌入。**

| 维度 | 说明 |
|:---|:---|
| **开发体验** | TS 文件 + `satisfies LocaleMessages` 编译期类型检查，key 遗漏立即报错 |
| **贡献者友好** | `deploy/i18n/*.json` 是语言无关的纯 JSON，任何译者都能理解和编辑 |
| **翻译平台对接** | JSON 是 Crowdin/Weblate 的标准输入格式，未来可直接集成 |
| **零运行时变更** | 不新增 API、不修改现有组件、不引入新依赖 |
| **CI 可验证** | `git diff --exit-code deploy/i18n/` 确保 JSON 与 TS 同步 |

为什么不用方案 B（运行时动态加载）：
1. "紧急文案修复"场景极罕见——成熟 CI 流水线中，改字符串的 PR 从合并到部署只需数分钟
2. 运行时加载需要后端 API、WatchService 监听、缓存失效、前端加载链、K8s ConfigMap 同步——1 人项目维护负担过重
3. ConfigMap 允许绕过 Git 直接修改运行中翻译，导致"跑着的"和"Git 里的"不一致

## Consequences

### 优势
- JSON 格式对译者友好，降低贡献门槛
- 为翻译平台集成做好准备
- 零运行时开销，无新增故障点

### 权衡
- 维护两套格式（TS + JSON），需 CI 检查同步
- 新增语言时需同时创建 TS 和 JSON 文件
- 构建脚本自动同步，CI 检查防止漂移；JSON 生成是确定性的

# Details

## 翻译格式双轨制

| 格式 | 位置 | 用途 | 谁编辑 |
|:---|:---|:---|:---|
| `.ts` | `src/i18n/locales/` | 代码级真相来源，类型安全 | 开发者 |
| `.json` | `deploy/i18n/` | 交换格式，翻译平台对接 | 译者 / 翻译平台 |

TS 文件是主来源。开发者添加新 key 时编辑 TS 文件，构建脚本自动生成 JSON。JSON 文件同时提交到 Git，供译者参考。

## 数据流

```
src/i18n/locales/zh-CN.ts ──(satisfies LocaleMessages)──→ Vue 前端 JS bundle
       │                                                    (现有流程，不变)
       │ npm run generate:i18n  (prebuild)
       ▼
deploy/i18n/zh-CN.json ──→ 翻译平台 (Crowdin/Weblate, 未来)
       │                    译者直接查看/编辑
       │
       └── Git 提交（与 TS 文件同步，CI 检查无漂移）
```

## 新增/修改文件

| 文件 | 操作 | 用途 |
|:---|:---|:---|
| `deploy/i18n/zh-CN.json` | 新建 | 中文翻译 JSON |
| `deploy/i18n/en-US.json` | 新建 | 英文翻译 JSON |
| `eify-web/scripts/generate-i18n-json.ts` | 新建 | 构建脚本：TS → JSON |
| `eify-web/package.json` | 修改 | `build` 脚本加 `generate:i18n` 前置步骤 |

## 不动文件

`src/i18n/locales/*.ts`、`src/i18n/index.ts`、`src/store/locale.ts`、所有 Vue 组件中的 `t('key')` 调用、后端所有文件、Dockerfile、nginx.conf、K8s 清单均不变。

## 参考
- GitLab i18n: https://docs.gitlab.com/ee/development/i18n/externalization.html
- Grafana i18n: https://grafana.com/docs/grafana/latest/developers/internationalization/
