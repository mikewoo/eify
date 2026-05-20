package com.eify.knowledge.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.knowledge.domain.entity.Document;
import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.service.ChunkService;
import com.eify.knowledge.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController")
class DocumentControllerTest {

    @Mock
    DocumentService documentService;

    @Mock
    ChunkService chunkService;

    @InjectMocks
    DocumentController controller;

    @Nested
    @DisplayName("uploadDocument")
    class UploadDocumentTests {

        @Test
        @DisplayName("上传成功应触发异步处理并返回文档")
        void shouldUploadAndTriggerAsyncProcessing() {
            MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
            Document doc = new Document();
            doc.setId(1L);
            when(documentService.uploadDocument(eq(1L), any())).thenReturn(doc);

            Result<Document> result = controller.uploadDocument(1L, file);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getId()).isEqualTo(1L);
            verify(documentService).processDocument(1L);
        }
    }

    @Nested
    @DisplayName("batchUpload")
    class BatchUploadTests {

        @Test
        @DisplayName("批量上传应为每个文档触发异步处理")
        void shouldUploadAndProcessEach() {
            MockMultipartFile f1 = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
            MockMultipartFile f2 = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
            Document d1 = new Document();
            d1.setId(1L);
            Document d2 = new Document();
            d2.setId(2L);
            when(documentService.batchUpload(eq(1L), any())).thenReturn(List.of(d1, d2));

            Result<List<Document>> result = controller.batchUpload(1L, List.of(f1, f2));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(2);
            verify(documentService).processDocument(1L);
            verify(documentService).processDocument(2L);
        }
    }

    @Nested
    @DisplayName("listByKnowledge")
    class ListByKnowledgeTests {

        @Test
        @DisplayName("分页查询应委托 service 并返回结果")
        void shouldDelegateAndReturnPagedResult() {
            PageResult<Document> pageResult = new PageResult<>();
            when(documentService.listByKnowledge(1L, 1, 20)).thenReturn(pageResult);

            Result<PageResult<Document>> result = controller.listByKnowledge(1L, 1, 20);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(pageResult);
        }
    }

    @Nested
    @DisplayName("getDocument")
    class GetDocumentTests {

        @Test
        @DisplayName("获取文档应委托 service.getDocumentStatus")
        void shouldDelegateToGetDocumentStatus() {
            Document doc = new Document();
            doc.setId(1L);
            when(documentService.getDocumentStatus(1L)).thenReturn(doc);

            Result<Document> result = controller.getDocument(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(doc);
        }
    }

    @Nested
    @DisplayName("reprocessDocument")
    class ReprocessDocumentTests {

        @Test
        @DisplayName("重新处理应委托 service 并返回成功")
        void shouldDelegateAndReturnSuccess() {
            Result<Void> result = controller.reprocessDocument(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(documentService).reprocessDocument(1L);
        }
    }

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocumentTests {

        @Test
        @DisplayName("删除应委托 service 并返回成功")
        void shouldDelegateAndReturnSuccess() {
            Result<Void> result = controller.deleteDocument(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(documentService).deleteDocument(1L);
        }
    }

    @Nested
    @DisplayName("getDocumentContent")
    class GetDocumentContentTests {

        @Test
        @DisplayName("获取内容应委托 service.extractContent")
        void shouldDelegateAndReturnContent() {
            when(documentService.extractContent(1L)).thenReturn("文档内容");

            Result<String> result = controller.getDocumentContent(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo("文档内容");
        }
    }

    @Nested
    @DisplayName("getDocumentChunks")
    class GetDocumentChunksTests {

        @Test
        @DisplayName("获取分块应验证文档存在后返回分块列表")
        void shouldVerifyDocumentThenReturnChunks() {
            List<DocumentChunk> chunks = List.of(new DocumentChunk());
            when(chunkService.findByDocumentId(1L)).thenReturn(chunks);

            Result<List<DocumentChunk>> result = controller.getDocumentChunks(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(1);
            verify(documentService).getDocumentStatus(1L);
        }
    }

    @Nested
    @DisplayName("getSupportedFileTypes")
    class GetSupportedFileTypesTests {

        @Test
        @DisplayName("应返回支持的文件类型列表")
        void shouldReturnSupportedTypes() {
            when(documentService.getSupportedFileTypes()).thenReturn(List.of("txt", "pdf", "md"));

            Result<List<String>> result = controller.getSupportedFileTypes();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).contains("txt", "pdf", "md");
        }
    }
}
