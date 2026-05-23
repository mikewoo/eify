-- H2 专用 schema（从 init.sql 转换，去掉 MySQL 特有语法）
-- 仅用于集成测试

-- 用户表
CREATE TABLE IF NOT EXISTS ai_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)     NOT NULL,
    email         VARCHAR(128)    NOT NULL DEFAULT '',
    password      VARCHAR(256)    NOT NULL,
    display_name  VARCHAR(128)    DEFAULT NULL,
    avatar_url    VARCHAR(512)    DEFAULT NULL,
    status        INT             NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP       DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    UNIQUE (username),
    UNIQUE (email)
);

-- 工作空间表
CREATE TABLE IF NOT EXISTS ai_workspace (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128)    NOT NULL,
    description   VARCHAR(512)    DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0
);

-- 工作空间成员表
CREATE TABLE IF NOT EXISTS ai_workspace_member (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id  BIGINT          NOT NULL,
    user_id       BIGINT          NOT NULL,
    role          VARCHAR(32)     NOT NULL DEFAULT 'member',
    joined_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_ws_member_user ON ai_workspace_member(user_id);

-- 用户会话表
CREATE TABLE IF NOT EXISTS ai_user_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT          NOT NULL,
    refresh_token VARCHAR(256)    NOT NULL,
    expires_at    TIMESTAMP       NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_user ON ai_user_session(user_id);

-- 供应商表
CREATE TABLE IF NOT EXISTS provider (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    name          VARCHAR(100)    NOT NULL,
    type          VARCHAR(50)     NOT NULL,
    base_url      VARCHAR(500)    NOT NULL,
    auth_config   CLOB            NOT NULL,
    enabled       INT             NOT NULL DEFAULT 1,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL DEFAULT 0,
    UNIQUE (name, workspace_id)
);

-- 模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id   BIGINT          NOT NULL,
    name          VARCHAR(100)    NOT NULL,
    model_id      VARCHAR(100)    NOT NULL,
    model_category INT            NOT NULL DEFAULT 0,
    context_size  INT             NOT NULL DEFAULT 0,
    extra_params  CLOB            NOT NULL,
    enabled       INT             NOT NULL DEFAULT 1,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL DEFAULT 0,
    workspace_id  BIGINT          NOT NULL DEFAULT 1
);

-- 供应商健康状态表
CREATE TABLE IF NOT EXISTS provider_health (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id     BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'UNKNOWN',
    last_check_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP       DEFAULT NULL,
    fail_count      INT             NOT NULL DEFAULT 0,
    latency_ms      INT             DEFAULT NULL,
    error_message   VARCHAR(500)    DEFAULT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INT             NOT NULL DEFAULT 0,
    creator_id      BIGINT          NOT NULL DEFAULT 0,
    UNIQUE (provider_id)
);

-- Agent 配置表
CREATE TABLE IF NOT EXISTS ai_agent (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id        BIGINT          NOT NULL DEFAULT 1,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500)    DEFAULT NULL,
    avatar              VARCHAR(500)    DEFAULT NULL,
    default_provider_id BIGINT          NOT NULL,
    default_model       VARCHAR(100)    NOT NULL,
    system_prompt       CLOB            NOT NULL,
    user_message_prefix VARCHAR(1000)   DEFAULT NULL,
    welcome_message     VARCHAR(500)    DEFAULT NULL,
    temperature         DECIMAL(3,2)    NOT NULL DEFAULT 0.70,
    max_tokens          INT             NOT NULL DEFAULT 2000,
    top_p               DECIMAL(3,2)    NOT NULL DEFAULT 1.00,
    frequency_penalty   DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    presence_penalty    DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    max_history_rounds  INT             NOT NULL DEFAULT 10,
    stream_enabled      INT             NOT NULL DEFAULT 1,
    workflow_id         BIGINT          DEFAULT NULL,
    rag_enabled         INT             NOT NULL DEFAULT 0,
    rag_top_k           INT             NOT NULL DEFAULT 5,
    rag_strategy        VARCHAR(20)     NOT NULL DEFAULT 'hybrid',
    agent_config        CLOB            DEFAULT NULL,
    enabled             INT             NOT NULL DEFAULT 1,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INT             NOT NULL DEFAULT 0,
    creator_id          BIGINT          DEFAULT NULL,
    UNIQUE (name, workspace_id)
);

-- 对话会话表
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    user_id       BIGINT          NOT NULL,
    agent_id      BIGINT          DEFAULT NULL,
    title         VARCHAR(200)    NOT NULL,
    status        INT             NOT NULL DEFAULT 1,
    workflow_id   BIGINT          DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL
);

-- 聊天消息表
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT          NOT NULL,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    role          VARCHAR(20)     NOT NULL,
    content       CLOB            NOT NULL,
    token_count   INT             NOT NULL DEFAULT 0,
    model_id      BIGINT          DEFAULT NULL,
    metadata      CLOB            DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON ai_chat_message(session_id, id);

-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id     BIGINT          NOT NULL DEFAULT 1,
    name             VARCHAR(100)    NOT NULL,
    description      VARCHAR(500)    DEFAULT NULL,
    embedding_model  VARCHAR(100)    NOT NULL DEFAULT 'text-embedding-3-small',
    embedding_model_id BIGINT        DEFAULT NULL,
    vector_dimension INT             NOT NULL DEFAULT 1536,
    chunk_size       INT             NOT NULL DEFAULT 500,
    chunk_overlap    INT             NOT NULL DEFAULT 50,
    document_count   INT             NOT NULL DEFAULT 0,
    chunk_count      INT             NOT NULL DEFAULT 0,
    retrieval_count  INT             NOT NULL DEFAULT 0,
    enabled          INT             NOT NULL DEFAULT 1,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT             NOT NULL DEFAULT 0,
    creator_id       BIGINT          NOT NULL DEFAULT 0,
    UNIQUE (name, workspace_id)
);

-- 文档表
CREATE TABLE IF NOT EXISTS document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id    BIGINT          NOT NULL,
    file_name       VARCHAR(255)    NOT NULL,
    original_name   VARCHAR(255)    NOT NULL,
    file_type       VARCHAR(20)     NOT NULL,
    file_size       BIGINT          NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    char_count      INT             NOT NULL DEFAULT 0,
    chunk_count     INT             NOT NULL DEFAULT 0,
    process_status  INT             NOT NULL DEFAULT 0,
    error_message   VARCHAR(500)    DEFAULT NULL,
    enabled         INT             NOT NULL DEFAULT 1,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INT             NOT NULL DEFAULT 0,
    creator_id      BIGINT          NOT NULL DEFAULT 0
);

-- Agent 与知识库关联表
CREATE TABLE IF NOT EXISTS agent_knowledge (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT          NOT NULL,
    knowledge_id  BIGINT          NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          DEFAULT NULL,
    UNIQUE (agent_id, knowledge_id)
);

-- MCP 服务器表
CREATE TABLE IF NOT EXISTS mcp_server (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)    NOT NULL,
    endpoint      VARCHAR(500)    NOT NULL,
    enabled       INT             NOT NULL DEFAULT 1,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    workspace_id  BIGINT          NOT NULL DEFAULT 1
);

-- MCP 工具表
CREATE TABLE IF NOT EXISTS mcp_tool (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id     BIGINT          NOT NULL,
    name          VARCHAR(200)    NOT NULL,
    description   CLOB            DEFAULT NULL,
    input_schema  CLOB            DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    workspace_id  BIGINT          NOT NULL DEFAULT 1
);

-- V6: workspace isolation indexes for mcp_tool
CREATE INDEX IF NOT EXISTS idx_mcp_tool_wsid ON mcp_tool(workspace_id);
CREATE INDEX IF NOT EXISTS idx_mcp_tool_name_ws ON mcp_tool(name, workspace_id);

-- Agent 绑定的 MCP 工具表 (fixed: added updated_at/deleted/creator_id, uk_agent_tool_workspace)
CREATE TABLE IF NOT EXISTS agent_mcp_tool (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT          NOT NULL,
    tool_id       BIGINT          NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL DEFAULT 0,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    CONSTRAINT uk_agent_tool_workspace UNIQUE (agent_id, tool_id, workspace_id)
);

-- V6: workspace isolation index for agent_mcp_tool
CREATE INDEX IF NOT EXISTS idx_amct_wsid ON agent_mcp_tool(workspace_id);

-- 工作流主表
CREATE TABLE IF NOT EXISTS ai_workflow (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    name          VARCHAR(100)    NOT NULL,
    description   VARCHAR(500)    DEFAULT NULL,
    status        INT             NOT NULL DEFAULT 0,
    version       INT             NOT NULL DEFAULT 1,
    variables     CLOB            DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    UNIQUE (name, workspace_id)
);

-- 工作流节点表
CREATE TABLE IF NOT EXISTS ai_workflow_node (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id   BIGINT          NOT NULL,
    node_key      VARCHAR(50)     NOT NULL,
    type          VARCHAR(30)     NOT NULL,
    label         VARCHAR(100)    DEFAULT NULL,
    position_x    DOUBLE          DEFAULT NULL,
    position_y    DOUBLE          DEFAULT NULL,
    config        CLOB            DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0
);

-- 工作流连线表
CREATE TABLE IF NOT EXISTS ai_workflow_edge (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT          NOT NULL,
    source_node_id  BIGINT          NOT NULL,
    target_node_id  BIGINT          NOT NULL,
    source_handle   VARCHAR(50)     NOT NULL DEFAULT 'default',
    label           VARCHAR(50)     DEFAULT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INT             NOT NULL DEFAULT 0
);

-- 工作流执行记录表
CREATE TABLE IF NOT EXISTS ai_workflow_execution (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id      BIGINT          NOT NULL,
    workflow_version INT             DEFAULT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'running',
    variables        CLOB            DEFAULT NULL,
    current_node_id  BIGINT          DEFAULT NULL,
    error_message    CLOB            DEFAULT NULL,
    started_at       TIMESTAMP       DEFAULT NULL,
    completed_at     TIMESTAMP       DEFAULT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT             NOT NULL DEFAULT 0
);
