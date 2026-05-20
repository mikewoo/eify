## 变更类型

- [ ] Bug 修复（fix）
- [ ] 新功能（feat）
- [ ] 文档更新（docs）
- [ ] 重构（refactor）
- [ ] 其他

## 变更说明

简要描述本次变更的内容和动机。

## 测试

- [ ] 本地 `mvn test` 全部通过
- [ ] 手动测试核心功能正常
- [ ] 新增功能已添加测试

## 检查清单

- [ ] 数据库索引：新增查询有索引覆盖
- [ ] 工作空间隔离：Service 层查询过滤 workspace_id
- [ ] 异常处理：使用 ErrorCode 枚举
- [ ] 日志格式：纯 JSON 格式，UTC 时区
- [ ] 配置安全：无硬编码密码 / API Key
- [ ] XSS 防护：v-html 使用 DOMPurify.sanitize

## 相关 Issue

Closes #

## 截图（如适用）

<!-- 粘贴 UI 变更的截图 -->
