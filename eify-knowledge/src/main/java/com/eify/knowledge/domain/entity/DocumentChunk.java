package com.eify.knowledge.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块（PostgreSQL pgvector）
 * <p>
 * 纯 POJO，不使用 MyBatis-Plus 注解。
 * 通过 JdbcTemplate 操作 pgvector。
 */
@Data
public class DocumentChunk {

    private Long id;
    private Long knowledgeId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private float[] embedding;
    private String chunkHash;
    private Integer enabled;
    private LocalDateTime createdAt;
}
