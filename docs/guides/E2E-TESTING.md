# 端到端测试指南

> Playwright 端到端测试模式，适用于 Eify 核心链路的 UI 验证。
> 参考：ECC e2e-testing skill。最后更新：2026-05-23。

---

## 目录结构

```
eify-web/
├── tests/
│   ├── e2e/
│   │   ├── auth/              # 登录/注册/工作空间切换
│   │   ├── features/          # Agent 对话、工作流执行、知识库
│   │   └── api/               # 直接 HTTP 接口验证
│   ├── fixtures/              # 共享的 test fixtures
│   └── playwright.config.ts
```

---

## Playwright 配置

```typescript
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  fullyParallel: true,
  workers: process.env.CI ? 1 : undefined,
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'junit.xml' }],
    ['json', { outputFile: 'results.json' }],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    actionTimeout: 10000,
    navigationTimeout: 30000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
```

---

## Page Object Model

每个页面封装为一个 Page 类，使用 `data-testid` 定位器：

```typescript
// tests/e2e/features/AgentChatPage.ts
import { Page, Locator } from '@playwright/test';

export class AgentChatPage {
  readonly page: Page;
  readonly chatInput: Locator;
  readonly sendButton: Locator;
  readonly messageList: Locator;
  readonly loadingIndicator: Locator;

  constructor(page: Page) {
    this.page = page;
    this.chatInput = page.locator('[data-testid="chat-input"]');
    this.sendButton = page.locator('[data-testid="send-button"]');
    this.messageList = page.locator('[data-testid="message-list"]');
    this.loadingIndicator = page.locator('[data-testid="loading-indicator"]');
  }

  async goto() {
    await this.page.goto('/chat');
    await this.page.waitForLoadState('networkidle');
  }

  async sendMessage(text: string) {
    await this.chatInput.fill(text);
    await this.sendButton.click();
  }

  async getMessageCount() {
    return this.messageList.locator('[data-testid="message-item"]').count();
  }
}
```

---

## 测试结构

```typescript
// tests/e2e/features/agent-chat.spec.ts
import { test, expect } from '@playwright/test';
import { AgentChatPage } from './AgentChatPage';

test.describe('Agent Chat', () => {
  let chatPage: AgentChatPage;

  test.beforeEach(async ({ page }) => {
    chatPage = new AgentChatPage(page);
    await chatPage.goto();
  });

  test('send message and receive response', async ({ page }) => {
    await chatPage.sendMessage('你好');

    // 等待 SSE 响应到达
    await expect(chatPage.messageList).toContainText('你好');
    await expect(chatPage.loadingIndicator).not.toBeVisible({ timeout: 30000 });
    await expect(chatPage.getMessageCount()).toBeGreaterThan(1);
  });

  test('empty message should show validation', async () => {
    await chatPage.sendButton.click();
    await expect(page.locator('[data-testid="input-error"]')).toBeVisible();
  });
});
```

---

## SSE 流式响应测试

Eify 核心链路（Agent 对话、工作流执行）都涉及 SSE。测试模式：

```typescript
test('SSE streaming response', async ({ page }) => {
  // 拦截 SSE 请求，等待响应完成
  const responsePromise = page.waitForResponse(
    resp => resp.url().includes('/api/v1/chat/stream') && resp.status() === 200,
    { timeout: 60000 }
  );

  await chatPage.sendMessage('写一段代码');

  const response = await responsePromise;
  expect(response.status()).toBe(200);
  // 验证流式内容已渲染
  await expect(chatPage.messageList.locator('[data-testid="message-item"]').last())
    .toContainText('```', { timeout: 30000 });
});
```

---

## 防抖处理

### 跳过已知不稳定的测试

```typescript
test.fixme('flaky - SSE timeout on slow CI', async ({ page }) => {
  // ...
});
```

### 复现偶发失败

```bash
npx playwright test --repeat-each=10 --retries=3 agent-chat.spec.ts
```

### 常见原因及修复

| 原因 | 错误做法 | 正确做法 |
|:---|:---|:---|
| 竞态条件 | `page.click()` 后立即断言 | 使用 `expect(locator).toBeVisible()` 等自动等待断言 |
| 网络时序 | `page.waitForTimeout(5000)` 盲等 | `page.waitForResponse(urlPattern)` 等具体响应 |
| 动画时序 | 动画中点击按钮 | `waitFor({ state: 'visible' })` 或 `waitForLoadState('networkidle')` |

---

## 产物管理

```typescript
// 截图
await page.screenshot({ path: 'artifacts/screenshots/full.png', fullPage: true });

// Trace（手动控制）
await context.tracing.start({ screenshots: true, snapshots: true });
// ... 测试步骤 ...
await context.tracing.stop({ path: 'artifacts/traces/test.zip' });
```

---

## Eify 优先测试目标

按系统风险清单的 P1 缺口，e2e 应优先覆盖：

| 测试对象 | 覆盖场景 | 优先级 |
|:---|:---|:---|
| `ChatServiceImpl` SSE 生命周期 | 发送消息 → 流式响应 → 完成/超时/报错 → UI 渲染 | P1 |
| `WorkflowEngine` 端到端 | 创建 → DAG 配置 → 执行 → 查看结果 | P1 |
| `McpClientServiceImpl` 生命周期 | 连接 → 工具列表 → 调用 → 缓存命中 → 断连降级 | P1 |

---

## 相关文档

- [SECURITY.md](SECURITY.md) — 系统风险清单（P0/P1/P2 测试优先级）
- [CLAUDE.md](../../CLAUDE.md) — 代码提交检查清单
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 架构设计 + 模块依赖
