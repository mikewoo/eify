-- ============================================
-- Eify pgvector 初始化脚本（参考副本）
-- 用途：document_chunk 表（向量数据）
-- 注意：表结构由 Flyway 自动管理，此文件仅供手动导入备用
-- 迁移文件：eify-app/src/main/resources/db/migration-pg/V1__init_pgvector.sql
--          eify-app/src/main/resources/db/migration-pg/V2__flexible_vector_dimension.sql
-- ============================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 向量分块表
CREATE TABLE IF NOT EXISTS document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT  NOT NULL DEFAULT 0,
    knowledge_id    BIGINT  NOT NULL,
    document_id     BIGINT  NOT NULL,
    chunk_index     INT     NOT NULL,
    content         TEXT    NOT NULL,
    embedding       VECTOR,
    chunk_hash      CHAR(64) NOT NULL,
    enabled         SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW 向量索引（余弦距离）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops);

-- 业务索引
CREATE INDEX IF NOT EXISTS idx_chunk_workspace ON document_chunk(workspace_id);
CREATE INDEX IF NOT EXISTS idx_chunk_knowledge ON document_chunk(knowledge_id);
CREATE INDEX IF NOT EXISTS idx_chunk_document ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_hash ON document_chunk(chunk_hash);

COMMENT ON TABLE document_chunk IS '文档分块向量表，存储切分后的文本及其 Embedding 向量';
COMMENT ON COLUMN document_chunk.embedding IS '向量嵌入，维度由对应 knowledge_base.vector_dimension 决定';
COMMENT ON COLUMN document_chunk.chunk_hash IS '内容 SHA-256 哈希，用于去重和增量更新';
