package com.eify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.domain.dto.request.KnowledgeCreateRequest;
import com.eify.knowledge.domain.dto.request.KnowledgeUpdateRequest;
import com.eify.knowledge.repository.KnowledgeRepository;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.service.KnowledgeService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.context.CurrentContext;
import com.eify.common.workspace.WorkspaceGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeRepository, KnowledgeBase> implements KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final ChunkRepository chunkRepository;

    @Override
    @Transactional
    public KnowledgeBase createKnowledge(KnowledgeCreateRequest request) {
        log.info("Creating knowledge base: {}", request.getName());

        WorkspaceGuard.checkNameUnique(getBaseMapper(),
                KnowledgeBase::getName, KnowledgeBase::getWorkspaceId, KnowledgeBase::getId,
                request.getName(), null, ErrorCode.KNOWLEDGE_NAME_DUPLICATE);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setEmbeddingModel(request.getEmbeddingModel());
        kb.setVectorDimension(request.getVectorDimension());
        kb.setChunkSize(request.getChunkSize());
        kb.setChunkOverlap(request.getChunkOverlap());
        kb.setEnabled(1);
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        WorkspaceGuard.bind(kb);

        save(kb);
        return kb;
    }

    @Override
    @Transactional
    public KnowledgeBase updateKnowledge(Long id, KnowledgeUpdateRequest request) {
        log.info("Updating knowledge base: {}", id);

        KnowledgeBase kb = WorkspaceGuard.requireInWorkspace(getById(id), ErrorCode.KNOWLEDGE_NOT_FOUND);

        if (request.getName() != null && !request.getName().equals(kb.getName())) {
            WorkspaceGuard.checkNameUnique(getBaseMapper(),
                    KnowledgeBase::getName, KnowledgeBase::getWorkspaceId, KnowledgeBase::getId,
                    request.getName(), id, ErrorCode.KNOWLEDGE_NAME_DUPLICATE);
            kb.setName(request.getName());
        }
        if (request.getDescription() != null) kb.setDescription(request.getDescription());
        if (request.getEmbeddingModel() != null) kb.setEmbeddingModel(request.getEmbeddingModel());
        if (request.getVectorDimension() != null) kb.setVectorDimension(request.getVectorDimension());
        if (request.getChunkSize() != null) kb.setChunkSize(request.getChunkSize());
        if (request.getChunkOverlap() != null) kb.setChunkOverlap(request.getChunkOverlap());

        updateById(kb);
        return kb;
    }

    @Override
    @Transactional
    public void deleteKnowledge(Long id) {
        log.info("Deleting knowledge base: {}", id);

        WorkspaceGuard.requireInWorkspace(getById(id), ErrorCode.KNOWLEDGE_NOT_FOUND);

        chunkRepository.deleteByKnowledgeId(id);
        removeById(id);
    }

    @Override
    public KnowledgeBase getKnowledge(Long id) {
        return WorkspaceGuard.requireInWorkspace(getById(id), ErrorCode.KNOWLEDGE_NOT_FOUND);
    }

    @Override
    public PageResult<KnowledgeBase> listKnowledge(Integer page, Integer pageSize) {
        Page<KnowledgeBase> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getWorkspaceId, CurrentContext.getWorkspaceId())
                .eq(KnowledgeBase::getEnabled, 1)
                .orderByDesc(KnowledgeBase::getCreatedAt);

        IPage<KnowledgeBase> result = knowledgeRepository.selectPage(pageObj, wrapper);
        return PageHelper.toPageResult(result);
    }

    @Override
    @Transactional
    public void toggleKnowledgeStatus(Long id, Integer status) {
        log.info("Toggling knowledge base status: id={}, status={}", id, status);

        KnowledgeBase kb = WorkspaceGuard.requireInWorkspace(getById(id), ErrorCode.KNOWLEDGE_NOT_FOUND);
        kb.setEnabled(status);
        updateById(kb);
    }

    @Override
    public boolean isKnowledgeAvailable(Long id) {
        KnowledgeBase kb = WorkspaceGuard.requireInWorkspace(getById(id), ErrorCode.KNOWLEDGE_NOT_FOUND);
        return kb.getEnabled() == 1;
    }
}
