package com.eify.knowledge.controller;

import com.eify.common.error.ErrorCode;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.knowledge.domain.dto.request.KnowledgeCreateRequest;
import com.eify.knowledge.domain.dto.request.KnowledgeUpdateRequest;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


/**
 * 知识库管理控制器
 */
@Tag(name = "知识库管理", description = "知识库 CRUD 与配置")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public Result<KnowledgeBase> createKnowledge(@Valid @RequestBody KnowledgeCreateRequest request) {
        return Result.success(knowledgeService.createKnowledge(request));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBase> getKnowledge(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeService.getKnowledge(id);
        if (kb == null) {
            return Result.fail(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        return Result.success(kb);
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBase> updateKnowledge(
        @PathVariable Long id,
        @Valid @RequestBody KnowledgeUpdateRequest request
    ) {
        return Result.success(knowledgeService.updateKnowledge(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteKnowledge(id);
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<KnowledgeBase>> listKnowledge(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return Result.success(knowledgeService.listKnowledge(page, pageSize));
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        knowledgeService.toggleKnowledgeStatus(id, status);
        return Result.success();
    }
}
