-- ============================================================
-- Eify 数据库初始化脚本（统一入口）
-- 执行方式：mysql -u root -p < deploy/sql/init_eify_mysql.sql
-- 适用环境：dev / prod
-- 最后更新：2026-05-22
-- ============================================================
--
-- 表清单（按模块分组）：
--   用户与工作空间：ai_user, ai_workspace, ai_workspace_member, ai_workspace_invite, ai_user_session
--   Provider：       provider, model_config, provider_health
--   Agent：          ai_agent
--   Chat：           ai_chat_session, ai_chat_message
--   Knowledge：      knowledge_base, document, agent_knowledge
--   MCP：            mcp_server, mcp_tool, agent_mcp_tool
--   Workflow：       ai_workflow, ai_workflow_node, ai_workflow_edge, ai_workflow_execution
--
-- 规范来源：docs/guides/DATABASE.md, docs/guides/AUTH-WORKSPACE.md
-- ============================================================

CREATE DATABASE IF NOT EXISTS `eify` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `eify`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ############################################################
-- 1. 用户与工作空间模块
-- ############################################################

-- 用户表
CREATE TABLE IF NOT EXISTS `ai_user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`      VARCHAR(64)     NOT NULL               COMMENT '用户名（唯一）',
    `email`         VARCHAR(128)    NOT NULL DEFAULT ''     COMMENT '邮箱',
    `password`      VARCHAR(256)    NOT NULL               COMMENT 'BCrypt 密码哈希',
    `display_name`  VARCHAR(128)    DEFAULT NULL           COMMENT '显示名称',
    `avatar_url`    VARCHAR(512)    DEFAULT NULL           COMMENT '头像URL',
    `status`        TINYINT         NOT NULL DEFAULT 1     COMMENT '状态：0=禁用，1=正常',
    `last_login_at` DATETIME        DEFAULT NULL           COMMENT '最后登录时间',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED DEFAULT NULL           COMMENT '创建人ID（注册时为空）',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 工作空间表
CREATE TABLE IF NOT EXISTS `ai_workspace` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作空间ID',
    `name`          VARCHAR(128)    NOT NULL               COMMENT '工作空间名称',
    `description`   VARCHAR(512)    DEFAULT NULL           COMMENT '描述',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间表';

-- 工作空间成员表
CREATE TABLE IF NOT EXISTS `ai_workspace_member` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id`  BIGINT UNSIGNED NOT NULL               COMMENT '工作空间ID',
    `user_id`       BIGINT UNSIGNED NOT NULL               COMMENT '用户ID',
    `role`          VARCHAR(32)     NOT NULL DEFAULT 'member' COMMENT '角色：owner/admin/member',
    `joined_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workspace_user` (`workspace_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间成员表';

-- 工作空间邀请码表
CREATE TABLE IF NOT EXISTS `ai_workspace_invite` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id`  BIGINT UNSIGNED NOT NULL               COMMENT '工作空间ID',
    `code`          VARCHAR(16)     NOT NULL               COMMENT '邀请码',
    `expires_at`    DATETIME        DEFAULT NULL           COMMENT '过期时间（NULL=永不过期）',
    `max_uses`      INT             NOT NULL DEFAULT 0     COMMENT '最大使用次数（0=无限制）',
    `use_count`     INT             NOT NULL DEFAULT 0     COMMENT '已使用次数',
    `enabled`       TINYINT         NOT NULL DEFAULT 1     COMMENT '是否启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间邀请码表';

-- 用户会话表（refresh token 持久化）
CREATE TABLE IF NOT EXISTS `ai_user_session` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL               COMMENT '用户ID',
    `refresh_token` VARCHAR(256)    NOT NULL               COMMENT '刷新令牌',
    `expires_at`    DATETIME        NOT NULL               COMMENT '过期时间',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_refresh_token` (`refresh_token`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';

-- ############################################################
-- 2. Provider 模块
-- ############################################################

-- 模型供应商表
CREATE TABLE IF NOT EXISTS `provider` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',
    `name`          VARCHAR(100)    NOT NULL               COMMENT '供应商名称',
    `type`          VARCHAR(50)     NOT NULL               COMMENT '类型：OPENAI/ANTHROPIC/OLLAMA/OPENAI_COMPATIBLE',
    `base_url`      VARCHAR(500)    NOT NULL               COMMENT 'API 基础地址',
    `auth_config`   JSON            NOT NULL               COMMENT '鉴权配置（JSON）',
    `enabled`       TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
    KEY `idx_type_enabled` (`type`, `enabled`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商表';

-- 模型配置表
CREATE TABLE IF NOT EXISTS `model_config` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`   BIGINT UNSIGNED NOT NULL               COMMENT '所属供应商ID',
    `name`          VARCHAR(100)    NOT NULL               COMMENT '展示名，如 GPT-4o',
    `model_id`      VARCHAR(100)    NOT NULL               COMMENT '调用时传给 API 的值',
    `model_category` TINYINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '模型主类别：0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL',
    `context_size`  INT UNSIGNED    NOT NULL DEFAULT 0     COMMENT '上下文窗口大小（token 数）',
    `extra_params`  JSON            NOT NULL               COMMENT '模型级别扩展参数（JSON）',
    `enabled`       TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',

    PRIMARY KEY (`id`),
    KEY `idx_provider_id` (`provider_id`),
    KEY `idx_model_id` (`model_id`),
    KEY `idx_model_category` (`model_category`),
    KEY `idx_enabled_deleted` (`enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- 供应商健康状态表
CREATE TABLE IF NOT EXISTS `provider_health` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`     BIGINT UNSIGNED NOT NULL               COMMENT '供应商ID（唯一索引）',
    `status`          VARCHAR(20)     NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态：UP/DOWN/DEGRADED/UNKNOWN',
    `last_check_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后探测时间',
    `last_success_at` DATETIME        DEFAULT NULL           COMMENT '最后成功时间',
    `fail_count`      INT             NOT NULL DEFAULT 0     COMMENT '连续失败次数',
    `latency_ms`      INT             DEFAULT NULL           COMMENT '最近一次延迟（毫秒）',
    `error_message`   VARCHAR(500)    DEFAULT NULL           COMMENT '最近失败原因',

    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商健康状态表';

-- ############################################################
-- 3. Agent 模块
-- ############################################################

-- Agent 配置表
CREATE TABLE IF NOT EXISTS `ai_agent` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workspace_id`        BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',
    `name`                VARCHAR(100)    NOT NULL               COMMENT 'Agent名称',
    `description`         VARCHAR(500)    DEFAULT NULL           COMMENT '描述',
    `avatar`              VARCHAR(500)    DEFAULT NULL           COMMENT '头像URL',

    -- 模型配置
    `default_provider_id` BIGINT UNSIGNED NOT NULL               COMMENT '默认供应商ID',
    `default_model`       VARCHAR(100)    NOT NULL               COMMENT '默认模型名称',

    -- 提示词配置
    `system_prompt`       TEXT            NOT NULL               COMMENT '系统提示词',
    `user_message_prefix` VARCHAR(1000)   DEFAULT NULL           COMMENT '用户消息前缀',
    `welcome_message`     VARCHAR(500)    DEFAULT NULL           COMMENT '欢迎语',

    -- 模型参数
    `temperature`         DECIMAL(3,2)    NOT NULL DEFAULT 0.70  COMMENT '温度：0.00-2.00',
    `max_tokens`          INT UNSIGNED    NOT NULL DEFAULT 2000  COMMENT '最大生成tokens',
    `top_p`               DECIMAL(3,2)    NOT NULL DEFAULT 1.00  COMMENT 'Top-p采样',
    `frequency_penalty`   DECIMAL(3,2)    NOT NULL DEFAULT 0.00  COMMENT '频率惩罚：-2 到 2',
    `presence_penalty`    DECIMAL(3,2)    NOT NULL DEFAULT 0.00  COMMENT '存在惩罚：-2 到 2',

    -- 对话配置
    `max_history_rounds`  INT UNSIGNED    NOT NULL DEFAULT 10    COMMENT '最大历史轮数',
    `stream_enabled`      TINYINT         NOT NULL DEFAULT 1     COMMENT '是否启用流式输出：0=否，1=是',

    -- 工作流绑定
    `workflow_id`         BIGINT UNSIGNED DEFAULT NULL           COMMENT '绑定工作流ID，不为空时走工作流编排',

    -- RAG 配置
    `rag_enabled`         TINYINT         NOT NULL DEFAULT 0     COMMENT '是否启用RAG：0=禁用，1=启用',
    `rag_top_k`           INT             NOT NULL DEFAULT 5     COMMENT 'RAG检索返回的片段数量',
    `rag_strategy`        VARCHAR(20)     NOT NULL DEFAULT 'hybrid' COMMENT '检索策略：vector/keyword/hybrid',

    -- 扩展配置
    `agent_config`        JSON            DEFAULT NULL           COMMENT '扩展配置：{"fallbackModels":[...],"toolsEnabled":false}',
    `enabled`             TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
    KEY `idx_default_provider_id` (`default_provider_id`),
    KEY `idx_enabled_deleted` (`enabled`, `deleted`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置表';

-- ############################################################
-- 4. Chat 模块
-- ############################################################

-- 对话会话表
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',
    `user_id`       BIGINT UNSIGNED NOT NULL               COMMENT '用户ID',
    `agent_id`      BIGINT UNSIGNED DEFAULT NULL           COMMENT '使用的Agent ID',
    `title`         VARCHAR(200)    NOT NULL               COMMENT '对话标题',
    `status`        TINYINT         NOT NULL DEFAULT 1     COMMENT '状态：0=已归档，1=进行中',
    `workflow_id`   BIGINT UNSIGNED DEFAULT NULL           COMMENT '关联工作流ID',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL               COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_user_status_updated_id` (`user_id`, `status`, `updated_at`, `id`) COMMENT '用户对话查询索引（支持按更新时间排序）',
    KEY `idx_agent_updated_id` (`agent_id`, `updated_at`, `id`) COMMENT 'Agent 对话查询索引（支持按更新时间排序）',
    KEY `idx_created_at` (`created_at`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- 聊天消息表（大表，使用游标分页）
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     BIGINT UNSIGNED NOT NULL               COMMENT '所属会话ID',
    `workspace_id`   BIGINT UNSIGNED NOT NULL               COMMENT '所属工作空间ID',
    `role`           VARCHAR(20)     NOT NULL               COMMENT '角色：user/assistant/system',
    `content`        TEXT            NOT NULL               COMMENT '消息内容',
    `token_count`    INT UNSIGNED    NOT NULL DEFAULT 0     COMMENT 'token数',
    `model_id`       BIGINT UNSIGNED DEFAULT NULL           COMMENT '使用的模型ID',
    `metadata`       JSON            DEFAULT NULL           COMMENT '元数据：{"model":"gpt-4","latency_ms":1234}',

    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_session_id_id` (`session_id`, `id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_session_id_id_role_time` (`session_id`, `id`, `role`, `created_at`) COMMENT '会话消息查询覆盖索引',
    KEY `idx_created_at_id` (`created_at`, `id`) COMMENT '时间范围查询索引',
    KEY `idx_session_workspace_id` (`session_id`, `workspace_id`, `id`) COMMENT '会话内 workspace 隔离查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ############################################################
-- 5. Knowledge 模块
-- ############################################################

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workspace_id`     BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',
    `name`             VARCHAR(100)    NOT NULL               COMMENT '知识库名称',
    `description`      VARCHAR(500)    DEFAULT NULL           COMMENT '描述',
    `embedding_model`    VARCHAR(100)    NOT NULL DEFAULT ''         COMMENT '嵌入模型名称',
    `embedding_model_id` BIGINT UNSIGNED DEFAULT NULL               COMMENT '嵌入模型 FK -> model_config.id，NULL 时降级到全局配置',
    `vector_dimension`   INT             NOT NULL DEFAULT 0         COMMENT '向量维度（由所选模型决定）',
    `chunk_size`       INT             NOT NULL DEFAULT 0     COMMENT '分块大小（字符数）',
    `chunk_overlap`    INT             NOT NULL DEFAULT 0     COMMENT '分块重叠（字符数）',
    `document_count`   INT             NOT NULL DEFAULT 0     COMMENT '文档数',
    `chunk_count`      INT             NOT NULL DEFAULT 0     COMMENT '分块数',
    `retrieval_count`  INT             NOT NULL DEFAULT 0     COMMENT '检索次数',
    `enabled`          TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_workspace_id` (`workspace_id`),
    KEY `idx_embedding_model_id` (`embedding_model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 文档表
CREATE TABLE IF NOT EXISTS `document` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workspace_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '工作空间ID',
    `knowledge_id`    BIGINT UNSIGNED NOT NULL               COMMENT '所属知识库ID',
    `file_name`       VARCHAR(255)    NOT NULL               COMMENT '存储文件名',
    `original_name`   VARCHAR(255)    NOT NULL               COMMENT '原始文件名',
    `file_type`       VARCHAR(20)     NOT NULL               COMMENT '文件类型：pdf/docx/txt/md',
    `file_size`       BIGINT UNSIGNED NOT NULL               COMMENT '文件大小（字节）',
    `file_path`       VARCHAR(500)    NOT NULL               COMMENT '文件存储路径',
    `char_count`      INT UNSIGNED    NOT NULL DEFAULT 0     COMMENT '字符数',
    `chunk_count`     INT UNSIGNED    NOT NULL DEFAULT 0     COMMENT '分块数',
    `process_status`  TINYINT         NOT NULL DEFAULT 0     COMMENT '处理状态：0=待处理，1=处理中，2=已完成，3=失败',
    `error_message`   VARCHAR(500)    DEFAULT NULL           COMMENT '失败原因',
    `enabled`         TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_workspace` (`workspace_id`),
    KEY `idx_knowledge` (`knowledge_id`),
    KEY `idx_status` (`process_status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- Agent 与知识库关联表
CREATE TABLE IF NOT EXISTS `agent_knowledge` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id`      BIGINT UNSIGNED NOT NULL               COMMENT 'Agent ID',
    `knowledge_id`  BIGINT UNSIGNED NOT NULL               COMMENT '知识库 ID',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED DEFAULT NULL           COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_knowledge` (`agent_id`, `knowledge_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent与知识库关联表';

-- ############################################################
-- 6. MCP 模块
-- ############################################################

-- MCP 服务器表
CREATE TABLE IF NOT EXISTS `mcp_server` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`          VARCHAR(100)    NOT NULL               COMMENT '服务器名称',
    `endpoint`      VARCHAR(500)    NOT NULL               COMMENT 'MCP Server URL (Streamable HTTP)',
    `enabled`       TINYINT         NOT NULL DEFAULT 1     COMMENT '启用状态：0=禁用，1=启用',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务器';

-- MCP 工具表
CREATE TABLE IF NOT EXISTS `mcp_tool` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `server_id`     BIGINT UNSIGNED NOT NULL               COMMENT '所属 MCP Server ID',
    `name`          VARCHAR(200)    NOT NULL               COMMENT '工具名称',
    `description`   TEXT            DEFAULT NULL           COMMENT '工具描述',
    `input_schema`  JSON            DEFAULT NULL           COMMENT '输入参数 JSON Schema',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',

    PRIMARY KEY (`id`),
    KEY `idx_server_id` (`server_id`),
    KEY `idx_workspace_id` (`workspace_id`),
    KEY `idx_name_workspace` (`name`, `workspace_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具';

-- Agent 绑定的 MCP 工具表
CREATE TABLE IF NOT EXISTS `agent_mcp_tool` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id`      BIGINT UNSIGNED NOT NULL               COMMENT 'Agent ID',
    `tool_id`       BIGINT UNSIGNED NOT NULL               COMMENT 'MCP 工具 ID',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '删除标识：0=正常，1=删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_tool_workspace` (`agent_id`, `tool_id`, `workspace_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_tool_id` (`tool_id`),
    KEY `idx_workspace_id` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 绑定的 MCP 工具';

-- ############################################################
-- 7. Workflow 模块
-- ############################################################

-- 工作流主表
CREATE TABLE IF NOT EXISTS `ai_workflow` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workspace_id`  BIGINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '工作空间 ID',
    `name`          VARCHAR(100)    NOT NULL               COMMENT '工作流名称',
    `description`   VARCHAR(500)    DEFAULT NULL           COMMENT '工作流描述',
    `status`        TINYINT         NOT NULL DEFAULT 0     COMMENT '状态：0=草稿，1=已发布，2=已禁用',
    `version`       INT             NOT NULL DEFAULT 1     COMMENT '版本号，每次发布 +1',
    `variables`     JSON            DEFAULT NULL           COMMENT '全局变量定义：[{key,type,required,defaultVal}]',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=已删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
    KEY `idx_workspace_id` (`workspace_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流主表';

-- 工作流节点表
CREATE TABLE IF NOT EXISTS `ai_workflow_node` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id`   BIGINT UNSIGNED NOT NULL               COMMENT '所属工作流 ID',
    `node_key`      VARCHAR(50)     NOT NULL               COMMENT '节点标识（工作流内唯一）',
    `type`          VARCHAR(30)     NOT NULL               COMMENT '节点类型：start/end/llm/api_call/condition/code',
    `label`         VARCHAR(100)    DEFAULT NULL           COMMENT '节点显示名称',
    `position_x`    DOUBLE          DEFAULT NULL           COMMENT '画布 X 坐标',
    `position_y`    DOUBLE          DEFAULT NULL           COMMENT '画布 Y 坐标',
    `config`        JSON            DEFAULT NULL           COMMENT '节点配置（不同 type 结构不同）',

    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=已删除',
    `creator_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点表';

-- 工作流连线表
CREATE TABLE IF NOT EXISTS `ai_workflow_edge` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id`     BIGINT UNSIGNED NOT NULL               COMMENT '所属工作流 ID',
    `source_node_id`  BIGINT UNSIGNED NOT NULL               COMMENT '源节点 ID',
    `target_node_id`  BIGINT UNSIGNED NOT NULL               COMMENT '目标节点 ID',
    `source_handle`   VARCHAR(50)     NOT NULL DEFAULT 'default' COMMENT '源节点出口：default/true/false/分支名',
    `label`           VARCHAR(50)     DEFAULT NULL           COMMENT '连线显示文字',

    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=已删除',
    `creator_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`),
    KEY `idx_source` (`source_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流连线表';

-- 工作流执行记录表
CREATE TABLE IF NOT EXISTS `ai_workflow_execution` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id`      BIGINT UNSIGNED NOT NULL               COMMENT '工作流 ID',
    `workflow_version` INT             DEFAULT NULL           COMMENT '执行时工作流版本快照',
    `status`           VARCHAR(20)     NOT NULL DEFAULT 'running' COMMENT '状态：running/completed/failed/cancelled',
    `variables`        JSON            DEFAULT NULL           COMMENT '运行时变量快照',
    `current_node_id`  BIGINT UNSIGNED DEFAULT NULL           COMMENT '当前执行到的节点 ID',
    `error_message`    TEXT            DEFAULT NULL           COMMENT '失败原因',
    `started_at`       DATETIME        DEFAULT NULL           COMMENT '开始执行时间',
    `completed_at`     DATETIME        DEFAULT NULL           COMMENT '执行结束时间',

    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT         NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=正常，1=已删除',
    `creator_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '创建人ID',

    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- ############################################################
-- 8. 开发环境初始数据（仅 dev 环境使用）
-- ############################################################

-- 管理员用户（密码：admin123，BCrypt 加密）
INSERT INTO `ai_user` (`id`, `username`, `email`, `password`, `display_name`, `status`) VALUES
(1, 'admin', 'admin@eify.local', '$2a$10$CfJnrJ65v1Oyt91xEG/tB.DzgQCLk6gX0reH9LpgHgB6boCapKH3C', 'Admin', 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`);

-- 默认工作空间
INSERT INTO `ai_workspace` (`id`, `name`, `description`) VALUES
(1, 'System Workspace', 'System default workspace')
ON DUPLICATE KEY UPDATE `name` = `name`;

-- 管理员加入默认工作空间
INSERT INTO `ai_workspace_member` (`workspace_id`, `user_id`, `role`) VALUES
(1, 1, 'owner')
ON DUPLICATE KEY UPDATE `role` = `role`;

-- ############################################################
-- 9. 已有部署迁移 SQL（V2/V3 变更已纳入新部署 DDL）
-- ############################################################
-- V1→V2: ai_chat_message 添加 workspace_id 列 + 索引（已纳入上方 DDL）
-- V2→V3: mcp_server 添加 uk_name_workspace 唯一约束 + idx_workspace_id（已纳入上方 DDL）
--
-- 以下为早期部署可能需要的 ALTER：
-- ALTER TABLE `document` ADD COLUMN `workspace_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '工作空间ID' AFTER `id`;
-- UPDATE `document` d JOIN `knowledge_base` kb ON kb.id = d.knowledge_id SET d.workspace_id = kb.workspace_id WHERE d.workspace_id = 0;
-- ALTER TABLE `document` ADD KEY `idx_workspace` (`workspace_id`);


