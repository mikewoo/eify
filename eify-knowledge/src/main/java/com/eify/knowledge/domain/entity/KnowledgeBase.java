package com.eify.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity implements WorkspaceAware {

    private String name;

    @TableField("workspace_id")
    private Long workspaceId;

    private String description;

    /**
     * 嵌入模型名称，如 text-embedding-3-small
     */
    @TableField("embedding_model")
    private String embeddingModel;

    /**
     * 嵌入模型配置 ID，关联 ModelConfig 表
     */
    @TableField("embedding_model_id")
    private Long embeddingModelId;

    /**
     * 向量维度，与 embedding_model 一致
     */
    @TableField("vector_dimension")
    private Integer vectorDimension;

    /**
     * 分块大小（字符数）
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * 分块重叠（字符数）
     */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /**
     * 文档数（冗余计数）
     */
    @TableField("document_count")
    private Integer documentCount;

    /**
     * 分块数（冗余计数）
     */
    @TableField("chunk_count")
    private Integer chunkCount;

    /**
     * 检索次数
     */
    @TableField("retrieval_count")
    private Integer retrievalCount;

    private Integer enabled;

    @TableField("creator_id")
    private Long creatorId;
}
