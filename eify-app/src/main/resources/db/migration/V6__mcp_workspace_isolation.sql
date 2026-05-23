-- ============================================================
-- V6: MCP 工作空间隔离 — 添加缺失的 workspace_id 索引和唯一约束
-- mcp_tool: 加 idx_workspace_id、idx_name_workspace
-- agent_mcp_tool: 加 idx_workspace_id、重建 uk_agent_tool 含 workspace_id
-- ============================================================

-- mcp_tool: idx_workspace_id
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mcp_tool' AND INDEX_NAME = 'idx_workspace_id') = 0,
    'ALTER TABLE `mcp_tool` ADD INDEX `idx_workspace_id` (`workspace_id`)',
    'SELECT ''Index idx_workspace_id already exists on mcp_tool, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mcp_tool: idx_name_workspace（findServerIdForTool 按 name + workspace_id 查询用）
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mcp_tool' AND INDEX_NAME = 'idx_name_workspace') = 0,
    'ALTER TABLE `mcp_tool` ADD INDEX `idx_name_workspace` (`name`, `workspace_id`)',
    'SELECT ''Index idx_name_workspace already exists on mcp_tool, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- agent_mcp_tool: idx_workspace_id
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_mcp_tool' AND INDEX_NAME = 'idx_workspace_id') = 0,
    'ALTER TABLE `agent_mcp_tool` ADD INDEX `idx_workspace_id` (`workspace_id`)',
    'SELECT ''Index idx_workspace_id already exists on agent_mcp_tool, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- agent_mcp_tool: 重建唯一约束，从 (agent_id, tool_id) 扩到 (agent_id, tool_id, workspace_id)
-- 使用新名称 uk_agent_tool_workspace 确保二阶段幂等（与 V5 模式一致）
-- 安全：旧约束是 (agent_id, tool_id)，新约束是超集。agent_id 全局唯一，
-- (agent_id, tool_id) 不会跨 workspace 重复，因此 ADD UNIQUE KEY 不会冲突
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_mcp_tool' AND INDEX_NAME = 'uk_agent_tool') > 0,
    'ALTER TABLE `agent_mcp_tool` DROP INDEX `uk_agent_tool`',
    'SELECT ''Index uk_agent_tool does not exist on agent_mcp_tool, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_mcp_tool' AND INDEX_NAME = 'uk_agent_tool_workspace') = 0,
    'ALTER TABLE `agent_mcp_tool` ADD UNIQUE KEY `uk_agent_tool_workspace` (`agent_id`, `tool_id`, `workspace_id`)',
    'SELECT ''Unique key uk_agent_tool_workspace already exists on agent_mcp_tool, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
