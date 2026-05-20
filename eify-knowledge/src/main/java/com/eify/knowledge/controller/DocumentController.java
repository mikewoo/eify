package com.eify.knowledge.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.knowledge.domain.entity.Document;
import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.service.ChunkService;
import com.eify.knowledge.service.DocumentService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理控制器
 */
@Tag(name = "文档管理", description = "知识库文档上传、查询与删除")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ChunkService chunkService;

    /**
     * 上传文档（自动触发异步处理）
     */
    @PostMapping("/{knowledgeId}/upload")
    public Result<Document> uploadDocument(
        @PathVariable Long knowledgeId,
        @RequestParam("file") MultipartFile file
    ) {
        Document document = documentService.uploadDocument(knowledgeId, file);
        // 异步触发处理
        documentService.processDocument(document.getId());
        return Result.success(document);
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/{knowledgeId}/batch-upload")
    public Result<List<Document>> batchUpload(
        @PathVariable Long knowledgeId,
        @RequestParam("files") List<MultipartFile> files
    ) {
        List<Document> documents = documentService.batchUpload(knowledgeId, files);
        // 异步触发处理
        documents.forEach(doc -> documentService.processDocument(doc.getId()));
        return Result.success(documents);
    }

    /**
     * 按知识库查询文档列表（分页）
     */
    @GetMapping("/knowledge/{knowledgeId}")
    public Result<PageResult<Document>> listByKnowledge(
            @PathVariable Long knowledgeId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return Result.success(documentService.listByKnowledge(knowledgeId, page, pageSize));
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/{documentId}")
    public Result<Document> getDocument(@PathVariable Long documentId) {
        return Result.success(documentService.getDocumentStatus(documentId));
    }

    /**
     * 重新处理文档
     */
    @PostMapping("/{documentId}/reprocess")
    public Result<Void> reprocessDocument(@PathVariable Long documentId) {
        documentService.reprocessDocument(documentId);
        return Result.success();
    }

    /**
     * 删除文档（级联清理 pgvector）
     */
    @DeleteMapping("/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return Result.success();
    }

    /**
     * 获取文档原始文本内容（用于预览）
     */
    @GetMapping("/{documentId}/content")
    public Result<String> getDocumentContent(@PathVariable Long documentId) {
        return Result.success(documentService.extractContent(documentId));
    }

    /**
     * 获取文档分块列表（用于预览）
     */
    @GetMapping("/{documentId}/chunks")
    public Result<List<DocumentChunk>> getDocumentChunks(@PathVariable Long documentId) {
        documentService.getDocumentStatus(documentId);
        return Result.success(chunkService.findByDocumentId(documentId));
    }

    /**
     * 获取支持的文件类型
     */
    @GetMapping("/supported-types")
    public Result<List<String>> getSupportedFileTypes() {
        return Result.success(documentService.getSupportedFileTypes());
    }
}
