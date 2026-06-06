-- H2 测试初始数据（幂等：使用 MERGE INTO 避免主键冲突）

-- 管理员用户（密码：admin123）
MERGE INTO ai_user (id, username, email, password, display_name, status) KEY(id)
VALUES (1, 'admin', 'admin@eify.local', '$2a$10$N.ZOn9MHQb28sYJLBCBP3.Ot1CvnOWJpIbPLmAlMNd2V6H6J3K6Oy', '管理员', 1);

-- 默认工作空间
MERGE INTO ai_workspace (id, name, description) KEY(id)
VALUES (1, '默认工作空间', '系统默认工作空间');

-- 管理员加入默认工作空间
MERGE INTO ai_workspace_member (workspace_id, user_id, role) KEY(workspace_id, user_id)
VALUES (1, 1, 'owner');

-- Second workspace for cross-workspace testing
MERGE INTO ai_workspace (id, name, description) KEY(id)
VALUES (2, 'Personal Workspace', 'Cross-workspace test workspace');

-- Admin also belongs to workspace 2
MERGE INTO ai_workspace_member (workspace_id, user_id, role) KEY(workspace_id, user_id)
VALUES (2, 1, 'owner');

-- 默认供应商
MERGE INTO provider (id, workspace_id, name, type, base_url, auth_config, enabled, creator_id) KEY(id)
VALUES (1, 1, 'OpenAI', 'OPENAI', 'https://api.openai.com', '{"api_key": "sk-test"}', 1, 1);

-- 同步 IDENTITY 序列：上面用显式 id 插入了种子数据，
-- H2 PostgreSQL 模式下显式插入不会推进自增序列，需手动重启到安全高位，
-- 避免应用层自增插入与种子主键冲突（与生产 V1__init.sql 的 setval 等价）
ALTER TABLE ai_user ALTER COLUMN id RESTART WITH 100;
ALTER TABLE ai_workspace ALTER COLUMN id RESTART WITH 100;
ALTER TABLE provider ALTER COLUMN id RESTART WITH 100;
