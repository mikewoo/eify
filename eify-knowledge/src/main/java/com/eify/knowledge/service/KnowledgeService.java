package com.eify.knowledge.service;

import com.eify.common.result.PageResult;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.domain.dto.request.KnowledgeCreateRequest;
import com.eify.knowledge.domain.dto.request.KnowledgeUpdateRequest;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {

    KnowledgeBase createKnowledge(KnowledgeCreateRequest request);

    KnowledgeBase updateKnowledge(Long id, KnowledgeUpdateRequest request);

    void deleteKnowledge(Long id);

    KnowledgeBase getKnowledge(Long id);

    /**
     * 分页查询知识库列表
     *
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小（最大 100）
     * @return 分页结果
     */
    PageResult<KnowledgeBase> listKnowledge(Integer page, Integer pageSize);

    void toggleKnowledgeStatus(Long id, Integer status);

    boolean isKnowledgeAvailable(Long id);
}
