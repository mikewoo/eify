package com.eify.knowledge.service;

import com.eify.common.result.PageResult;
import com.eify.knowledge.domain.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * 文档服务接口
 *
 * MVP 功能：
 * - 文件上传
 * - 文档解析
 * - 文档分块
 *
 * 扩展支持：
 * - 异步处理
 * - 批量处理
 * - 增量更新
 * - 错误重试
 */
public interface DocumentService {

    /**
     * 上传文档
     * MVP：同步处理
     * 扩展：支持异步、断点续传
     */
    Document uploadDocument(Long knowledgeId, MultipartFile file);

    /**
     * 批量上传文档
     * 扩展功能
     */
    List<Document> batchUpload(Long knowledgeId, List<MultipartFile> files);

    /**
     * 处理文档（解析、分块）
     * MVP：同步处理
     * 扩展：异步处理、队列化
     */
    void processDocument(Long documentId);

    /**
     * 批量处理文档
     * 扩展功能
     */
    void batchProcessDocuments(List<Long> documentIds);

    /**
     * 重新处理文档（增量更新）
     * 扩展功能
     */
    void reprocessDocument(Long documentId);

    /**
     * 删除文档
     * MVP：物理删除
     * 扩展：软删除、归档
     */
    void deleteDocument(Long documentId);

    /**
     * 获取文档处理状态
     * 扩展功能
     */
    Document getDocumentStatus(Long documentId);

    /**
     * 提取文档原始文本内容（用于预览）
     */
    String extractContent(Long documentId);

    /**
     * 获取文档文件路径（用于原始文件预览）
     */
    Path getDocumentFilePath(Long documentId);

    /**
     * 支持的文件类型
     */
    List<String> getSupportedFileTypes();

    /**
     * 按知识库分页查询文档列表
     *
     * @param knowledgeId 知识库 ID
     * @param page        当前页码（从 1 开始）
     * @param pageSize    每页大小（最大 100）
     * @return 分页结果
     */
    PageResult<Document> listByKnowledge(Long knowledgeId, Integer page, Integer pageSize);
}