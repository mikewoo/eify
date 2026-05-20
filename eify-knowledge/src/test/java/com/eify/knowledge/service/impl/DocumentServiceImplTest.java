package com.eify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.knowledge.domain.entity.Document;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.repository.DocumentRepository;
import com.eify.knowledge.repository.KnowledgeRepository;
import com.eify.knowledge.strategy.EmbeddingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentServiceImpl")
class DocumentServiceImplTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    KnowledgeRepository knowledgeRepository;

    @Mock
    ChunkRepository chunkRepository;

    @Mock
    EmbeddingStrategy embeddingStrategy;

    @InjectMocks
    DocumentServiceImpl documentService;

    static final Long WORKSPACE_ID = 1L;
    static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        CurrentContext.set(USER_ID, WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    private KnowledgeBase buildKnowledge(Long id, Long wsId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setWorkspaceId(wsId);
        kb.setName("test-kb");
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setEnabled(1);
        return kb;
    }

    private Document buildDocument(Long id, Long knowledgeId, Long wsId) {
        Document doc = new Document();
        doc.setId(id);
        doc.setKnowledgeId(knowledgeId);
        doc.setWorkspaceId(wsId);
        doc.setFileName("test.txt");
        doc.setOriginalName("test.txt");
        doc.setFileType("txt");
        doc.setFileSize(100L);
        doc.setFilePath("/tmp/test.txt");
        doc.setProcessStatus(0);
        doc.setEnabled(1);
        return doc;
    }

    // ==================== uploadDocument ====================

    @Nested
    @DisplayName("uploadDocument")
    class UploadDocumentTests {

        @Test
        @DisplayName("知识库不在当前 workspace 应抛出 KNOWLEDGE_NOT_FOUND")
        void shouldThrowWhenKnowledgeNotInWorkspace() {
            KnowledgeBase kb = buildKnowledge(1L, 999L);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

            assertThatThrownBy(() -> documentService.uploadDocument(1L, file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("上传成功应创建文档记录并递增计数")
        void shouldUploadAndCreateDocument() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
            when(documentRepository.insert(any(Document.class))).thenReturn(1);

            Document result = documentService.uploadDocument(1L, file);

            assertThat(result).isNotNull();
            assertThat(result.getKnowledgeId()).isEqualTo(1L);
            assertThat(result.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.getOriginalName()).isEqualTo("test.txt");
            assertThat(result.getFileType()).isEqualTo("txt");
            assertThat(result.getProcessStatus()).isEqualTo(0);
            verify(documentRepository).insert(any(Document.class));
            verify(knowledgeRepository).incrementDocumentCount(eq(1L), eq(1));
        }
    }

    // ==================== getDocumentStatus ====================

    @Nested
    @DisplayName("getDocumentStatus")
    class GetDocumentStatusTests {

        @Test
        @DisplayName("文档不在当前 workspace 应抛出 DOCUMENT_NOT_FOUND")
        void shouldThrowWhenNotInWorkspace() {
            Document doc = buildDocument(1L, 1L, 999L);
            when(documentRepository.selectById(1L)).thenReturn(doc);

            assertThatThrownBy(() -> documentService.getDocumentStatus(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("文档在当前 workspace 应返回文档")
        void shouldReturnDocumentWhenInWorkspace() {
            Document doc = buildDocument(1L, 1L, WORKSPACE_ID);
            when(documentRepository.selectById(1L)).thenReturn(doc);

            Document result = documentService.getDocumentStatus(1L);

            assertThat(result).isSameAs(doc);
        }

        @Test
        @DisplayName("文档不存在应抛出 DOCUMENT_NOT_FOUND")
        void shouldThrowWhenDocumentNotFound() {
            when(documentRepository.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> documentService.getDocumentStatus(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== deleteDocument ====================

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocumentTests {

        @Test
        @DisplayName("删除应清理分块、更新统计、删除物理文件和数据库记录")
        void shouldCascadeDelete() throws Exception {
            Document doc = buildDocument(1L, 1L, WORKSPACE_ID);
            doc.setFilePath(Files.createTempFile("test-delete", ".txt").toString());
            when(documentRepository.selectById(1L)).thenReturn(doc);
            when(chunkRepository.deleteByDocumentId(1L)).thenReturn(5);
            when(documentRepository.deleteById(any())).thenReturn(1);

            documentService.deleteDocument(1L);

            verify(chunkRepository).deleteByDocumentId(1L);
            verify(knowledgeRepository).incrementDocumentCount(eq(1L), eq(-1));
            verify(knowledgeRepository).incrementChunkCount(eq(1L), eq(-5));
            verify(documentRepository).deleteById(1L);
        }

        @Test
        @DisplayName("文档不在当前 workspace 应抛出异常")
        void shouldThrowWhenNotInWorkspace() {
            Document doc = buildDocument(1L, 1L, 999L);
            when(documentRepository.selectById(1L)).thenReturn(doc);

            assertThatThrownBy(() -> documentService.deleteDocument(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== listByKnowledge ====================

    @Nested
    @DisplayName("listByKnowledge")
    class ListByKnowledgeTests {

        @Test
        @DisplayName("知识库不在当前 workspace 应抛出异常")
        void shouldThrowWhenKnowledgeNotInWorkspace() {
            KnowledgeBase kb = buildKnowledge(1L, 999L);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);

            assertThatThrownBy(() -> documentService.listByKnowledge(1L, 1, 20))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("应返回带 workspace 过滤的分页结果")
        void shouldReturnFilteredPagedResults() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            when(documentRepository.selectPage(any(), any())).thenReturn(mock(IPage.class));

            PageResult<Document> result = documentService.listByKnowledge(1L, 1, 20);

            assertThat(result).isNotNull();
            verify(documentRepository).selectPage(any(), any(LambdaQueryWrapper.class));
        }
    }

    // ==================== getSupportedFileTypes ====================

    @Nested
    @DisplayName("getSupportedFileTypes")
    class GetSupportedFileTypesTests {

        @Test
        @DisplayName("应返回支持的文件类型列表")
        void shouldReturnSupportedTypes() {
            List<String> types = documentService.getSupportedFileTypes();

            assertThat(types).contains("txt", "pdf", "doc", "docx", "md");
        }
    }

    // ==================== reprocessDocument ====================

    @Nested
    @DisplayName("reprocessDocument")
    class ReprocessDocumentTests {

        @Test
        @DisplayName("应清理旧分块后重新处理")
        void shouldCleanAndReprocess() {
            Document doc = buildDocument(1L, 1L, WORKSPACE_ID);
            when(documentRepository.selectById(1L)).thenReturn(doc);

            documentService.reprocessDocument(1L);

            verify(chunkRepository).deleteByDocumentId(1L);
            verify(documentRepository).updateChunkCount(eq(1L), eq(0));
            verify(documentRepository).updateProcessStatus(eq(1L), eq(0), isNull());
        }
    }
}
