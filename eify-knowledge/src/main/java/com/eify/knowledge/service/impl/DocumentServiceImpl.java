package com.eify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.knowledge.domain.entity.Document;
import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.repository.DocumentRepository;
import com.eify.knowledge.repository.KnowledgeRepository;
import com.eify.knowledge.route.EmbeddingRoute;
import com.eify.knowledge.route.EmbeddingRouteResolver;
import com.eify.knowledge.service.DocumentService;
import com.eify.knowledge.strategy.EmbeddingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档服务实现 — RAG 数据管线核心
 *
 * 管线：上传 → 解析 → 分块 → 向量化 → 存储(pgvector)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingStrategy embeddingStrategy;
    private final EmbeddingRouteResolver routeResolver;

    private static final Path UPLOAD_DIR = Paths.get("uploads/documents");

    // ==================== ① 文件接收 ====================

    @Override
    @Transactional
    public Document uploadDocument(Long knowledgeId, MultipartFile file) {
        KnowledgeBase kb = WorkspaceGuard.requireInWorkspace(
                knowledgeRepository.selectById(knowledgeId), ErrorCode.KNOWLEDGE_NOT_FOUND);

        try {
            Files.createDirectories(UPLOAD_DIR);

            String ext = getExtension(file.getOriginalFilename());
            String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID() + "." + ext;
            Path target = UPLOAD_DIR.resolve(storedName).toAbsolutePath();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Document doc = new Document();
            doc.setKnowledgeId(knowledgeId);
            doc.setFileName(storedName);
            doc.setOriginalName(file.getOriginalFilename());
            doc.setFileType(ext);
            doc.setFileSize(file.getSize());
            doc.setFilePath(target.toString());
            doc.setCharCount(0);
            doc.setChunkCount(0);
            doc.setProcessStatus(0);
            doc.setEnabled(1);
            WorkspaceGuard.bind(doc);
            documentRepository.insert(doc);

            // 更新知识库文档计数
            knowledgeRepository.incrementDocumentCount(knowledgeId, 1);

            log.info("[Document] 上传成功: id={}, file={}", doc.getId(), file.getOriginalFilename());
            return doc;

        } catch (IOException e) {
            log.error("[Document] 上传失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.DOCUMENT_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public List<Document> batchUpload(Long knowledgeId, List<MultipartFile> files) {
        return files.stream()
                .map(file -> uploadDocument(knowledgeId, file))
                .collect(Collectors.toList());
    }

    // ==================== ②③④⑤ 文档处理管线 ====================

    @Override
    @Async("documentProcessExecutor")
    public void processDocument(Long documentId) {
        Document doc = documentRepository.selectById(documentId);
        if (doc == null) {
            log.warn("[Document] 文档不存在: {}", documentId);
            return;
        }

        // 异步上下文无 ThreadLocal，通过文档自身 workspace_id 做数据完整性校验
        KnowledgeBase kb = knowledgeRepository.selectById(doc.getKnowledgeId());
        if (kb == null || !doc.getWorkspaceId().equals(kb.getWorkspaceId())) {
            log.error("[Document] 工作空间不一致，拒绝处理: id={}, docWs={}, kbWs={}",
                    documentId, doc.getWorkspaceId(), kb != null ? kb.getWorkspaceId() : "null");
            documentRepository.updateProcessStatus(documentId, 3, "工作空间校验失败");
            return;
        }

        log.info("[Document] 开始处理: id={}, file={}", doc.getId(), doc.getOriginalName());

        try {
            // ② 解析：读取文件内容
            documentRepository.updateProcessStatus(documentId, 1, null);
            String content = extractContent(doc);
            documentRepository.updateCharCount(documentId, content.length());

            // ③ 分块：递归分段 + overlap
            List<String> chunkTexts = splitText(content, kb.getChunkSize(), kb.getChunkOverlap());

            // 增量去重：按哈希判断哪些块是新的
            List<String> hashes = chunkTexts.stream()
                    .map(this::computeHash)
                    .collect(Collectors.toList());

            List<String> existingHashes = chunkRepository.findExistingHashes(doc.getId(), hashes);
            Set<String> existingSet = new HashSet<>(existingHashes);

            List<String> newTexts = new ArrayList<>();
            List<String> newHashes = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                if (!existingSet.contains(hashes.get(i))) {
                    newTexts.add(chunkTexts.get(i));
                    newHashes.add(hashes.get(i));
                }
            }

            if (newTexts.isEmpty()) {
                log.info("[Document] 所有分块已存在，跳过向量化: id={}", documentId);
                documentRepository.updateProcessStatus(documentId, 2, null);
                return;
            }

            // ④ 向量化：按知识库绑定的嵌入模型路由
            log.info("[Document] 向量化 {} 个新分块 (跳过 {} 个已存在)", newTexts.size(), existingSet.size());
            EmbeddingRoute route = routeResolver.resolve(kb);
            List<float[]> embeddings = embeddingStrategy.embedBatch(newTexts, route);

            // ⑤ 存储：写入 pgvector
            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < newTexts.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setKnowledgeId(doc.getKnowledgeId());
                chunk.setDocumentId(doc.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(newTexts.get(i));
                chunk.setEmbedding(embeddings.get(i));
                chunk.setChunkHash(newHashes.get(i));
                chunk.setEnabled(1);
                chunks.add(chunk);
            }
            chunkRepository.batchInsert(chunks);

            // 更新文档统计
            int totalChunks = chunkRepository.countByDocumentId(doc.getId());
            documentRepository.updateChunkCount(doc.getId(), totalChunks);

            // 更新知识库分块计数
            knowledgeRepository.incrementChunkCount(doc.getKnowledgeId(), chunks.size());

            // ⑥ 标记完成
            documentRepository.updateProcessStatus(documentId, 2, null);
            log.info("[Document] 处理完成: id={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            log.error("[Document] 处理失败: id={}", documentId, e);
            documentRepository.updateProcessStatus(documentId, 3, e.getMessage());
        }
    }

    @Override
    public void batchProcessDocuments(List<Long> documentIds) {
        documentIds.forEach(this::processDocument);
    }

    @Override
    public void reprocessDocument(Long documentId) {
        Document doc = WorkspaceGuard.requireInWorkspace(
                documentRepository.selectById(documentId), ErrorCode.DOCUMENT_NOT_FOUND);

        // 清理旧的向量数据
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.updateChunkCount(documentId, 0);
        documentRepository.updateProcessStatus(documentId, 0, null);

        // 重新处理
        processDocument(documentId);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        Document doc = WorkspaceGuard.requireInWorkspace(
                documentRepository.selectById(documentId), ErrorCode.DOCUMENT_NOT_FOUND);

        // 删除 pgvector 中的分块
        int deletedChunks = chunkRepository.deleteByDocumentId(documentId);

        // 更新知识库统计
        knowledgeRepository.incrementDocumentCount(doc.getKnowledgeId(), -1);
        knowledgeRepository.incrementChunkCount(doc.getKnowledgeId(), -deletedChunks);

        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("[Document] 删除文件失败: {}", doc.getFilePath());
        }

        // 删除数据库记录
        documentRepository.deleteById(documentId);
        log.info("[Document] 已删除: id={}, chunks={}", documentId, deletedChunks);
    }

    @Override
    public Document getDocumentStatus(Long documentId) {
        return WorkspaceGuard.requireInWorkspace(
                documentRepository.selectById(documentId), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Override
    public String extractContent(Long documentId) {
        Document doc = WorkspaceGuard.requireInWorkspace(
                documentRepository.selectById(documentId), ErrorCode.DOCUMENT_NOT_FOUND);
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return extractContent(doc);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED);
        }
    }

    @Override
    public List<String> getSupportedFileTypes() {
        return List.of("txt", "pdf", "doc", "docx", "md");
    }

    // ==================== ② 文档解析 ====================

    private String extractContent(Document doc) throws IOException {
        Path path = Paths.get(doc.getFilePath());
        return switch (doc.getFileType().toLowerCase()) {
            case "pdf" -> extractPdf(path);
            case "doc", "docx" -> extractWordAsHtml(path);
            default -> Files.readString(path);
        };
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument pdf = PDDocument.load(path.toFile())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    /**
     * 提取 Word 文档为 HTML，保留基本格式（标题、加粗、斜体、列表）
     */
    private String extractWordAsHtml(Path path) throws IOException {
        try (XWPFDocument docx = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder sb = new StringBuilder();
            sb.append("<div class=\"doc-content\">");
            for (XWPFParagraph para : docx.getParagraphs()) {
                String styleName = para.getStyle();
                String tag = "p";
                if (styleName != null) {
                    if (styleName.startsWith("Heading") || styleName.startsWith("heading")) {
                        try {
                            int level = Integer.parseInt(styleName.replaceAll("[^1-9]", ""));
                            tag = "h" + Math.min(level, 6);
                        } catch (NumberFormatException ignored) {
                            tag = "h2";
                        }
                    }
                }
                sb.append("<").append(tag).append(">");
                for (var run : para.getRuns()) {
                    String text = run.text();
                    if (text == null || text.isEmpty()) continue;
                    String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    if (run.isBold() && run.isItalic()) {
                        sb.append("<strong><em>").append(escaped).append("</em></strong>");
                    } else if (run.isBold()) {
                        sb.append("<strong>").append(escaped).append("</strong>");
                    } else if (run.isItalic()) {
                        sb.append("<em>").append(escaped).append("</em>");
                    } else {
                        sb.append(escaped);
                    }
                }
                sb.append("</").append(tag).append(">");
            }
            sb.append("</div>");
            return sb.toString();
        }
    }

    /**
     * 获取文档文件路径（用于原始文件预览）
     */
    @Override
    public Path getDocumentFilePath(Long documentId) {
        Document doc = WorkspaceGuard.requireInWorkspace(
                documentRepository.selectById(documentId), ErrorCode.DOCUMENT_NOT_FOUND);
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return path;
    }

    // ==================== ③ 文档分块 ====================

    /**
     * 递归分段：按 段落 → 句子 → 字符 三级分隔符递归切分
     * 支持 chunk_overlap 重叠，防止上下文断裂
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        if (!StringUtils.hasText(text)) return List.of();

        List<String> chunks = new ArrayList<>();
        // 先按段落拆分
        List<String> segments = List.of(text.split("\\n\\n+"));

        StringBuilder buffer = new StringBuilder();
        for (String segment : segments) {
            if (buffer.length() + segment.length() > chunkSize && buffer.length() > 0) {
                // buffer 已满，切出一块
                chunks.addAll(splitSegment(buffer.toString(), chunkSize, overlap));
                buffer.setLength(0);
            }
            if (segment.length() > chunkSize) {
                // 单个段落超长，先 flush buffer 再拆分这个段落
                if (buffer.length() > 0) {
                    chunks.addAll(splitSegment(buffer.toString(), chunkSize, overlap));
                    buffer.setLength(0);
                }
                chunks.addAll(splitSegment(segment, chunkSize, overlap));
            } else {
                if (buffer.length() > 0) buffer.append("\n\n");
                buffer.append(segment);
            }
        }
        if (buffer.length() > 0) {
            chunks.addAll(splitSegment(buffer.toString(), chunkSize, overlap));
        }

        return chunks.stream().filter(StringUtils::hasText).collect(Collectors.toList());
    }

    /**
     * 对单个段落按句子边界切分
     */
    private List<String> splitSegment(String segment, int chunkSize, int overlap) {
        if (segment.length() <= chunkSize) return List.of(segment);

        // 确保 overlap 小于 chunkSize，防止死循环
        int safeOverlap = Math.min(overlap, chunkSize / 2);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < segment.length()) {
            int end = Math.min(start + chunkSize, segment.length());

            // 尝试在句子边界（。！？.!?）处断开
            if (end < segment.length()) {
                int sentenceEnd = findSentenceEnd(segment, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String chunk = segment.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 下一块从 overlap 位置开始，确保至少前进 1 个字符
            int nextStart = end - safeOverlap;
            if (nextStart <= start) {
                nextStart = start + 1;
            }
            start = nextStart;
        }
        return chunks;
    }

    private int findSentenceEnd(String text, int start, int end) {
        // 从 end 向前找最近的句子结束符
        for (int i = end - 1; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    // ==================== 分页查询 ====================

    @Override
    public PageResult<Document> listByKnowledge(Long knowledgeId, Integer page, Integer pageSize) {
        // 前置校验 knowledge 是否属于当前 workspace
        KnowledgeBase kb = knowledgeRepository.selectById(knowledgeId);
        if (kb == null || !kb.getWorkspaceId().equals(CurrentContext.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        Page<Document> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getWorkspaceId, CurrentContext.getWorkspaceId())
                .eq(Document::getEnabled, 1)
                .orderByDesc(Document::getCreatedAt);

        IPage<Document> result = documentRepository.selectPage(pageObj, wrapper);
        return PageHelper.toPageResult(result);
    }

    // ==================== 工具方法 ====================

    private String computeHash(String content) {
        return DigestUtils.md5DigestAsHex(content.getBytes());
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) return "txt";
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "txt" : filename.substring(dot + 1).toLowerCase();
    }
}
