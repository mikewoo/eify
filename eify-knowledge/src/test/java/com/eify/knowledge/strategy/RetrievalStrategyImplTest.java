package com.eify.knowledge.strategy;

import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetrievalStrategyImpl")
class RetrievalStrategyImplTest {

    @Mock
    ChunkRepository chunkRepository;

    @Mock
    EmbeddingStrategy embeddingStrategy;

    RetrievalStrategyImpl strategy;

    KnowledgeBase knowledge;

    @BeforeEach
    void setUp() {
        strategy = new RetrievalStrategyImpl(chunkRepository, embeddingStrategy);
        knowledge = new KnowledgeBase();
        knowledge.setId(1L);
        knowledge.setName("test-kb");
    }

    private ChunkRepository.ChunkSearchResult buildResult(Long id, String content, float similarity) {
        return new ChunkRepository.ChunkSearchResult(id, 1L, 10L, 0, content, similarity);
    }

    // ==================== vectorRetrieval ====================

    @Nested
    @DisplayName("vectorRetrieval")
    class VectorRetrieval {

        @Test
        @DisplayName("通过 embedding + pgvector 返回 topK 结果")
        void shouldReturnTopKResultsByVectorSimilarity() {
            float[] queryVec = new float[]{0.1f, 0.2f};
            when(embeddingStrategy.embed("hello world")).thenReturn(queryVec);

            List<ChunkRepository.ChunkSearchResult> chunks = List.of(
                    buildResult(1L, "hello world", 0.95f),
                    buildResult(2L, "foo bar", 0.30f),
                    buildResult(3L, "hello test", 0.80f)
            );
            when(chunkRepository.search(1L, queryVec, 2)).thenReturn(chunks.subList(0, 2));

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.vectorRetrieval("hello world", knowledge, 2);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).id()).isEqualTo(1L);
            assertThat(results.get(0).similarity()).isGreaterThan(0f);
        }

        @Test
        @DisplayName("无分块时返回空列表")
        void shouldReturnEmptyWhenNoChunks() {
            when(embeddingStrategy.embed(anyString())).thenReturn(new float[]{0.1f});
            when(chunkRepository.search(anyLong(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.vectorRetrieval("query", knowledge, 5);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("embedding 失败时返回空列表")
        void shouldReturnEmptyWhenEmbeddingFails() {
            when(embeddingStrategy.embed(anyString()))
                    .thenThrow(new RuntimeException("embedding error"));

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.vectorRetrieval("query", knowledge, 5);

            assertThat(results).isEmpty();
        }
    }

    // ==================== keywordRetrieval ====================

    @Nested
    @DisplayName("keywordRetrieval")
    class KeywordRetrieval {

        @Test
        @DisplayName("返回 ILIKE 匹配的结果")
        void shouldReturnMatchingChunksByKeyword() {
            List<ChunkRepository.ChunkSearchResult> chunks = List.of(
                    buildResult(1L, "apple apple banana", 0f),
                    buildResult(2L, "apple cherry", 0f)
            );
            when(chunkRepository.searchByKeyword(1L, "apple", 5)).thenReturn(chunks);

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.keywordRetrieval("apple", knowledge, 5);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("无匹配时返回空列表")
        void shouldReturnEmptyWhenNoMatch() {
            when(chunkRepository.searchByKeyword(1L, "absent", 5))
                    .thenReturn(Collections.emptyList());

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.keywordRetrieval("absent", knowledge, 5);

            assertThat(results).isEmpty();
        }
    }

    // ==================== hybridRetrieval ====================

    @Nested
    @DisplayName("hybridRetrieval")
    class HybridRetrieval {

        @Test
        @DisplayName("合并向量和关键词结果，加权排序")
        void shouldMergeAndWeightResults() {
            float[] queryVec = new float[]{0.1f};
            when(embeddingStrategy.embed("hello world")).thenReturn(queryVec);

            when(chunkRepository.search(eq(1L), eq(queryVec), anyInt()))
                    .thenReturn(List.of(
                            buildResult(1L, "hello world test", 0.9f),
                            buildResult(2L, "foo bar", 0.2f)));
            when(chunkRepository.searchByKeyword(eq(1L), eq("hello world"), anyInt()))
                    .thenReturn(List.of(
                            buildResult(1L, "hello world test", 0f),
                            buildResult(3L, "hello world", 0f)));

            List<ChunkRepository.ChunkSearchResult> results = strategy.hybridRetrieval(
                    "hello world", knowledge, 2, 0.4f, 0.6f);

            assertThat(results).isNotEmpty();
            // chunk 1 matches both => highest merged score
            assertThat(results.get(0).id()).isEqualTo(1L);
            assertThat(results.get(0).similarity()).isGreaterThan(0f);
        }

        @Test
        @DisplayName("无结果时返回空列表")
        void shouldReturnEmptyWhenNoResults() {
            when(embeddingStrategy.embed(anyString())).thenReturn(new float[]{0.1f});
            when(chunkRepository.search(anyLong(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(chunkRepository.searchByKeyword(anyLong(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<ChunkRepository.ChunkSearchResult> results = strategy.hybridRetrieval(
                    "query", knowledge, 5, 0.5f, 0.5f);

            assertThat(results).isEmpty();
        }
    }

    // ==================== filteredRetrieval ====================

    @Nested
    @DisplayName("filteredRetrieval")
    class FilteredRetrieval {

        @Test
        @DisplayName("按 documentId 过滤")
        void shouldFilterByDocumentId() {
            when(embeddingStrategy.embed("hello")).thenReturn(new float[]{0.1f});
            when(chunkRepository.search(1L, new float[]{0.1f}, 10))
                    .thenReturn(List.of(
                            buildResult(1L, "hello world", 0.8f),
                            new ChunkRepository.ChunkSearchResult(
                                    2L, 1L, 20L, 0, "hello test", 0.7f)));

            RetrievalFilters filters = new RetrievalFilters();
            filters.setDocumentId(10L);

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.filteredRetrieval("hello", knowledge, 5, filters);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("按 minScore 过滤")
        void shouldFilterByMinScore() {
            when(embeddingStrategy.embed("hello")).thenReturn(new float[]{0.1f});
            when(chunkRepository.search(1L, new float[]{0.1f}, 10))
                    .thenReturn(List.of(
                            buildResult(1L, "hello world", 0.8f),
                            buildResult(2L, "nothing", 0.0f)));

            RetrievalFilters filters = new RetrievalFilters();
            filters.setMinScore(0.5f);

            List<ChunkRepository.ChunkSearchResult> results =
                    strategy.filteredRetrieval("hello", knowledge, 5, filters);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo(1L);
        }
    }

    // ==================== enhanceResults ====================

    @Nested
    @DisplayName("enhanceResults")
    class EnhanceResults {

        @Test
        @DisplayName("为每个分块计算匹配类型和置信度")
        void shouldEnhanceWithMatchTypeAndConfidence() {
            ChunkRepository.ChunkSearchResult chunk =
                    buildResult(1L, "hello world test", 0.5f);

            List<EnhancedChunk> results = strategy.enhanceResults(
                    List.of(chunk), "hello world");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(1L);
            assertThat(results.get(0).getContent()).isEqualTo("hello world test");
            assertThat(results.get(0).getRelevanceScore()).isEqualTo(0.5f);
            assertThat(results.get(0).getMatchType()).isEqualTo("vector");
            assertThat(results.get(0).getConfidenceScore()).isNotNull();
            assertThat(results.get(0).getConfidenceScore()).isBetween(0f, 1f);
        }

        @Test
        @DisplayName("空列表返回空")
        void shouldReturnEmptyForEmptyInput() {
            List<EnhancedChunk> results = strategy.enhanceResults(
                    Collections.emptyList(), "query");
            assertThat(results).isEmpty();
        }
    }
}
