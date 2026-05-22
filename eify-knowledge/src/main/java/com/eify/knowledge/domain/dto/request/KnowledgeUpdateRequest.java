package com.eify.knowledge.domain.dto.request;

import lombok.Data;

/**
 * 更新知识库请求
 */
@Data
public class KnowledgeUpdateRequest {

    private String name;

    private String description;

    private String embeddingModel;

    private Long embeddingModelId;

    private Integer vectorDimension;

    private Integer chunkSize;

    private Integer chunkOverlap;
}
