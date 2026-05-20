package com.eify.knowledge.controller;

import com.eify.common.result.Result;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "检索管理", description = "知识库向量检索与重排序")
@RestController
@RequestMapping("/api/v1/retrieval")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    @PostMapping("/search")
    public Result<List<ChunkRepository.ChunkSearchResult>> search(
            @Valid @RequestBody RetrievalService.RetrievalRequest request) {
        return Result.success(retrievalService.retrieve(request));
    }

    @PostMapping("/chat")
    public Result<String> ragChat(
            @Valid @RequestBody RetrievalService.RagRequest request) {
        return Result.success(retrievalService.ragChat(request));
    }

    @PostMapping("/batch-search")
    public Result<List<List<ChunkRepository.ChunkSearchResult>>> batchSearch(
            @Valid @RequestBody List<RetrievalService.RetrievalRequest> requests) {
        return Result.success(retrievalService.batchRetrieve(requests));
    }

    @GetMapping("/suggestions")
    public Result<List<String>> getSuggestions(
            @RequestParam Long knowledgeId,
            @RequestParam String query) {
        return Result.success(retrievalService.getRetrievalSuggestions(query, knowledgeId));
    }

    @PostMapping("/analyze")
    public Result<RetrievalService.RetrievalAnalysis> analyze(
            @Valid @RequestBody List<RetrievalService.RetrievalHistory> history) {
        return Result.success(retrievalService.analyzeRetrieval(history));
    }
}
