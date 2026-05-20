package com.eify.knowledge.service.impl;

import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.service.ChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final ChunkRepository chunkRepository;

    @Override
    public List<ChunkRepository.ChunkSearchResult> search(Long knowledgeId, float[] query, int topK) {
        return chunkRepository.search(knowledgeId, query, topK);
    }

    @Override
    public List<DocumentChunk> findByDocumentId(Long documentId) {
        return chunkRepository.findByDocumentId(documentId);
    }

    @Override
    public int deleteByDocumentId(Long documentId) {
        return chunkRepository.deleteByDocumentId(documentId);
    }

    @Override
    public int deleteByKnowledgeId(Long knowledgeId) {
        return chunkRepository.deleteByKnowledgeId(knowledgeId);
    }
}
