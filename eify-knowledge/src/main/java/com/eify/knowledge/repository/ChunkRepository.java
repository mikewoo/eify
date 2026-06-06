package com.eify.knowledge.repository;

import com.eify.knowledge.config.VectorTypeHandler;
import com.eify.knowledge.domain.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * 文档分块数据访问层（PostgreSQL pgvector）
 * <p>
 * 职责：批量写入 + 向量相似度查询。
 * 业务 CRUD 走 MyBatis-Plus，这里只管向量数据。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    private final JdbcTemplate jdbcTemplate;
    private final VectorTypeHandler vectorTypeHandler;

    /**
     * 批量插入分块（含向量）
     */
    public void batchInsert(List<DocumentChunk> chunks) {
        String sql = """
            INSERT INTO document_chunk
              (knowledge_id, document_id, chunk_index, content, embedding, chunk_hash, enabled)
            VALUES (?, ?, ?, ?, ?::vector, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DocumentChunk c = chunks.get(i);
                ps.setLong(1, c.getKnowledgeId());
                ps.setLong(2, c.getDocumentId());
                ps.setInt(3, c.getChunkIndex());
                ps.setString(4, c.getContent());
                ps.setString(5, vectorTypeHandler.toVectorLiteral(c.getEmbedding()));
                ps.setString(6, c.getChunkHash());
                ps.setInt(7, c.getEnabled() != null ? c.getEnabled() : 1);
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });

        log.info("[ChunkRepository] 批量插入 {} 条分块", chunks.size());
    }

    /**
     * 向量相似度检索
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 按相似度降序排列的分块列表（embedding 字段为空，节省内存）
     */
    public List<ChunkSearchResult> search(Long knowledgeId, float[] queryVector, int topK) {
        String sql = """
            SELECT id, knowledge_id, document_id, chunk_index, content,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM document_chunk
            WHERE knowledge_id = ?
              AND enabled = 1
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

        String vectorLiteral = vectorTypeHandler.toVectorLiteral(queryVector);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkSearchResult(
                        rs.getLong("id"),
                        rs.getLong("knowledge_id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getFloat("similarity")
                ),
                vectorLiteral,
                knowledgeId,
                vectorLiteral,
                topK
        );
    }

    /**
     * 按文档ID删除所有分块（文档重新处理时调用）
     */
    public int deleteByDocumentId(Long documentId) {
        int count = jdbcTemplate.update(
                "DELETE FROM document_chunk WHERE document_id = ?",
                documentId
        );
        log.info("[ChunkRepository] 删除文档 {} 的 {} 条分块", documentId, count);
        return count;
    }

    /**
     * 按知识库ID删除所有分块（知识库删除时调用）
     */
    public int deleteByKnowledgeId(Long knowledgeId) {
        int count = jdbcTemplate.update(
                "DELETE FROM document_chunk WHERE knowledge_id = ?",
                knowledgeId
        );
        log.info("[ChunkRepository] 删除知识库 {} 的 {} 条分块", knowledgeId, count);
        return count;
    }

    /**
     * 按文档ID统计分块数
     */
    public int countByDocumentId(Long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        );
        return count != null ? count : 0;
    }

    /**
     * 按哈希查询已存在的分块（增量更新去重用）
     */
    public List<String> findExistingHashes(Long documentId, List<String> hashes) {
        String sql = "SELECT chunk_hash FROM document_chunk WHERE document_id = ? AND chunk_hash IN ("
                + hashes.stream().map(h -> "?").reduce((a, b) -> a + "," + b).orElse("''")
                + ")";

        Object[] params = new Object[hashes.size() + 1];
        params[0] = documentId;
        for (int i = 0; i < hashes.size(); i++) {
            params[i + 1] = hashes.get(i);
        }

        return jdbcTemplate.queryForList(sql, String.class, params);
    }

    /**
     * 按文档ID查询所有分块（不含向量，用于预览）
     */
    public List<DocumentChunk> findByDocumentId(Long documentId) {
        String sql = """
            SELECT id, knowledge_id, document_id, chunk_index, content, chunk_hash, enabled, created_at
            FROM document_chunk WHERE document_id = ? ORDER BY chunk_index ASC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DocumentChunk c = new DocumentChunk();
            c.setId(rs.getLong("id"));
            c.setKnowledgeId(rs.getLong("knowledge_id"));
            c.setDocumentId(rs.getLong("document_id"));
            c.setChunkIndex(rs.getInt("chunk_index"));
            c.setContent(rs.getString("content"));
            c.setChunkHash(rs.getString("chunk_hash"));
            c.setEnabled(rs.getInt("enabled"));
            c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return c;
        }, documentId);
    }

    /**
     * 关键词检索（ILIKE 文本匹配）
     */
    public List<ChunkSearchResult> searchByKeyword(Long knowledgeId, String keyword, int topK) {
        String sql = """
            SELECT id, knowledge_id, document_id, chunk_index, content, 0.0 AS similarity
            FROM document_chunk
            WHERE knowledge_id = ? AND enabled = 1 AND content ILIKE ?
            LIMIT ?
            """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkSearchResult(
                        rs.getLong("id"),
                        rs.getLong("knowledge_id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        0.0f
                ),
                knowledgeId, "%" + keyword + "%", topK
        );
    }

    /**
     * 向量检索结果
     */
    public record ChunkSearchResult(
            Long id,
            Long knowledgeId,
            Long documentId,
            int chunkIndex,
            String content,
            float similarity
    ) {}
}
