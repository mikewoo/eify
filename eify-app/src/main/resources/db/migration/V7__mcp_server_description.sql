-- V7: mcp_server 表添加 description 字段
-- 为 MCP Server 增加描述信息，用于 Agent 编辑页展示 Server 简介

SELECT COUNT(*) INTO @col_exists
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'mcp_server'
  AND COLUMN_NAME = 'description';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `mcp_server` ADD COLUMN `description` VARCHAR(500) NULL COMMENT ''服务器描述'' AFTER `name`',
    'SELECT ''Column description already exists'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
