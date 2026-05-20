package com.eify.knowledge.service.impl;

import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkServiceImpl")
class ChunkServiceImplTest {

    @Mock
    ChunkRepository chunkRepository;

    @InjectMocks
    ChunkServiceImpl chunkService;

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("委托给 chunkRepository.search 并返回结果")
        void shouldDelegateToRepository() {
            float[] queryVec = new float[]{0.1f, 0.2f};
            List<ChunkRepository.ChunkSearchResult> expected = List.of(
                    new ChunkRepository.ChunkSearchResult(1L, 1L, 10L, 0, "test", 0.95f));
            when(chunkRepository.search(1L, queryVec, 5)).thenReturn(expected);

            List<ChunkRepository.ChunkSearchResult> result = chunkService.search(1L, queryVec, 5);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("空结果应正常返回空列表")
        void shouldReturnEmptyListWhenNoResults() {
            when(chunkRepository.search(1L, new float[0], 3)).thenReturn(List.of());

            List<ChunkRepository.ChunkSearchResult> result = chunkService.search(1L, new float[0], 3);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByDocumentId")
    class FindByDocumentIdTests {

        @Test
        @DisplayName("委托给 chunkRepository.findByDocumentId")
        void shouldDelegateToRepository() {
            List<DocumentChunk> expected = List.of(new DocumentChunk());
            when(chunkRepository.findByDocumentId(10L)).thenReturn(expected);

            List<DocumentChunk> result = chunkService.findByDocumentId(10L);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("deleteByDocumentId")
    class DeleteByDocumentIdTests {

        @Test
        @DisplayName("委托给 chunkRepository.deleteByDocumentId 并返回删除数")
        void shouldDelegateAndReturnCount() {
            when(chunkRepository.deleteByDocumentId(10L)).thenReturn(5);

            int count = chunkService.deleteByDocumentId(10L);

            assertThat(count).isEqualTo(5);
            verify(chunkRepository).deleteByDocumentId(10L);
        }
    }

    @Nested
    @DisplayName("deleteByKnowledgeId")
    class DeleteByKnowledgeIdTests {

        @Test
        @DisplayName("委托给 chunkRepository.deleteByKnowledgeId 并返回删除数")
        void shouldDelegateAndReturnCount() {
            when(chunkRepository.deleteByKnowledgeId(1L)).thenReturn(20);

            int count = chunkService.deleteByKnowledgeId(1L);

            assertThat(count).isEqualTo(20);
            verify(chunkRepository).deleteByKnowledgeId(1L);
        }
    }
}
