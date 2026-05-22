package com.eify.knowledge.domain.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建知识库请求
 */
@Data
public class KnowledgeCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    /**
     * 嵌入模型名称，默认 text-embedding-3-small（保留兼容）
     */
    private String embeddingModel = "text-embedding-3-small";

    /**
     * 嵌入模型 ID（来自 model_config 表），优先于 embeddingModel
     */
    private Long embeddingModelId;

    /**
     * 向量维度，默认 1536
     */
    private Integer vectorDimension = 1536;

    /**
     * 分块大小（字符数）
     */
    private Integer chunkSize = 500;

    /**
     * 分块重叠（字符数）
     */
    private Integer chunkOverlap = 50;
}
