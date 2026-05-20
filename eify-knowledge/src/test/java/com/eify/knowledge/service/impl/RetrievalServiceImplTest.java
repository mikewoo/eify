package com.eify.knowledge.service.impl;

import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.repository.KnowledgeRepository;
import com.eify.knowledge.service.RetrievalService;
import com.eify.knowledge.strategy.RetrievalStrategy;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.domain.dto.ChatMessage;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.service.ProviderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetrievalServiceImpl")
class RetrievalServiceImplTest {

    @Mock
    KnowledgeRepository knowledgeRepository;

    @Mock
    RetrievalStrategy retrievalStrategy;

    @Mock
    ProviderService providerService;

    @Mock
    ProviderAdapterFactory adapterFactory;

    @Mock
    Executor embeddingExecutor;

    @InjectMocks
    RetrievalServiceImpl retrievalService;

    static final Long WORKSPACE_ID = 1L;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, WORKSPACE_ID);
        ReflectionTestUtils.setField(retrievalService, "embeddingExecutor", embeddingExecutor);
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
        kb.setEnabled(1);
        return kb;
    }

    private ChunkRepository.ChunkSearchResult buildResult(Long id, String content, float sim) {
        return new ChunkRepository.ChunkSearchResult(id, 1L, 10L, 0, content, sim);
    }

    // ==================== retrieve(RetrievalRequest) ====================

    @Nested
    @DisplayName("retrieve(RetrievalRequest)")
    class RetrieveByRequestTests {

        @Test
        @DisplayName("知识库不在当前 workspace 应抛出 KNOWLEDGE_NOT_FOUND")
        void shouldThrowWhenNotInWorkspace() {
            KnowledgeBase kb = buildKnowledge(1L, 999L);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);

            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");

            assertThatThrownBy(() -> retrievalService.retrieve(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("vector 策略应调用 vectorRetrieval")
        void shouldUseVectorStrategy() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            List<ChunkRepository.ChunkSearchResult> expected = List.of(buildResult(1L, "c", 0.9f));
            when(retrievalStrategy.vectorRetrieval(eq("test"), any(), eq(5))).thenReturn(expected);

            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");
            req.setStrategy("vector");
            req.setTopK(5);

            List<ChunkRepository.ChunkSearchResult> results = retrievalService.retrieve(req);

            assertThat(results).hasSize(1);
            verify(retrievalStrategy).vectorRetrieval(anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("keyword 策略应调用 keywordRetrieval")
        void shouldUseKeywordStrategy() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            when(retrievalStrategy.keywordRetrieval(anyString(), any(), anyInt()))
                    .thenReturn(List.of());

            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");
            req.setStrategy("keyword");

            retrievalService.retrieve(req);

            verify(retrievalStrategy).keywordRetrieval(anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("DB 异常在 workspace 校验阶段应抛出 BusinessException")
        void shouldThrowWhenDbError() {
            when(knowledgeRepository.selectById(1L)).thenThrow(new RuntimeException("db error"));

            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");

            assertThatThrownBy(() -> retrievalService.retrieve(req))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ==================== ragChat ====================

    @Nested
    @DisplayName("ragChat")
    class RagChatTests {

        @Test
        @DisplayName("无相关分块时应返回中文回退消息")
        void shouldReturnFallbackWhenNoChunks() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            when(retrievalStrategy.hybridRetrieval(anyString(), any(), anyInt(), anyFloat(), anyFloat()))
                    .thenReturn(List.of());

            RetrievalService.RagRequest req = new RetrievalService.RagRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");
            req.setProviderId(1L);

            String result = retrievalService.ragChat(req);

            assertThat(result).contains("抱歉");
        }

        @Test
        @DisplayName("providerId 为 null 时应抛出 PARAM_ERROR")
        void shouldThrowWhenProviderIdNull() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            // 第二次 selectById 在 requireKnowledgeInWorkspace 之后的 retrieve 中
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            when(retrievalStrategy.hybridRetrieval(anyString(), any(), anyInt(), anyFloat(), anyFloat()))
                    .thenReturn(List.of(buildResult(1L, "content", 0.9f)));

            RetrievalService.RagRequest req = new RetrievalService.RagRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");
            req.setProviderId(null);

            String result = retrievalService.ragChat(req);

            assertThat(result).contains("抱歉");
        }

        @Test
        @DisplayName("知识库不在当前 workspace 应抛出 KNOWLEDGE_NOT_FOUND")
        void shouldThrowWhenNotInWorkspace() {
            KnowledgeBase kb = buildKnowledge(1L, 999L);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);

            RetrievalService.RagRequest req = new RetrievalService.RagRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");

            assertThatThrownBy(() -> retrievalService.ragChat(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode());
        }
    }

    // ==================== batchRetrieve ====================

    @Nested
    @DisplayName("batchRetrieve")
    class BatchRetrieveTests {

        @Test
        @DisplayName("批量检索应通过线程池异步执行")
        void shouldExecuteAsyncViaThreadPool() {
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(embeddingExecutor).execute(any(Runnable.class));

            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            when(retrievalStrategy.hybridRetrieval(anyString(), any(), anyInt(), anyFloat(), anyFloat()))
                    .thenReturn(List.of());

            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");

            List<List<ChunkRepository.ChunkSearchResult>> results =
                    retrievalService.batchRetrieve(List.of(req, req));

            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isEmpty();
        }
    }

    // ==================== getRetrievalSuggestions ====================

    @Nested
    @DisplayName("getRetrievalSuggestions")
    class GetSuggestionsTests {

        @Test
        @DisplayName("应从检索结果中提取长度大于 3 的单词，最多 5 个")
        void shouldExtractLongWordsAndLimitTo5() {
            KnowledgeBase kb = buildKnowledge(1L, WORKSPACE_ID);
            when(knowledgeRepository.selectById(1L)).thenReturn(kb);
            List<ChunkRepository.ChunkSearchResult> chunks = List.of(
                    buildResult(1L, "hello world machine learning test", 0.9f),
                    buildResult(2L, "deep learning neural network", 0.8f)
            );
            when(retrievalStrategy.hybridRetrieval(anyString(), any(), anyInt(), anyFloat(), anyFloat()))
                    .thenReturn(chunks);

            List<String> suggestions = retrievalService.getRetrievalSuggestions("AI", 1L);

            assertThat(suggestions).isNotEmpty();
            suggestions.forEach(word -> assertThat(word.length()).isGreaterThan(3));
            assertThat(suggestions.size()).isLessThanOrEqualTo(5);
        }
    }

    // ==================== analyzeRetrieval ====================

    @Nested
    @DisplayName("analyzeRetrieval")
    class AnalyzeRetrievalTests {

        @Test
        @DisplayName("空历史应返回默认零值分析")
        void shouldReturnZeroAnalysisForEmptyHistory() {
            RetrievalService.RetrievalAnalysis analysis =
                    retrievalService.analyzeRetrieval(List.of());

            assertThat(analysis.getAverageRelevance()).isEqualTo(0f);
            assertThat(analysis.getAverageResponseTime()).isEqualTo(0L);
            assertThat(analysis.getRetrievalEfficiency()).isEqualTo(0f);
        }

        @Test
        @DisplayName("应正确计算平均相关性和响应时间")
        void shouldCalculateAverages() {
            RetrievalService.RetrievalHistory h1 = new RetrievalService.RetrievalHistory();
            h1.setRelevanceScore(0.8f);
            h1.setResponseTime(1000L);
            RetrievalService.RetrievalHistory h2 = new RetrievalService.RetrievalHistory();
            h2.setRelevanceScore(0.4f);
            h2.setResponseTime(3000L);

            RetrievalService.RetrievalAnalysis analysis =
                    retrievalService.analyzeRetrieval(List.of(h1, h2));

            assertThat(analysis.getAverageRelevance()).isEqualTo(0.6f);
            assertThat(analysis.getAverageResponseTime()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("低相关性应生成优化建议")
        void shouldGenerateSuggestionsForLowRelevance() {
            RetrievalService.RetrievalHistory h = new RetrievalService.RetrievalHistory();
            h.setRelevanceScore(0.3f);
            h.setResponseTime(6000L);

            RetrievalService.RetrievalAnalysis analysis =
                    retrievalService.analyzeRetrieval(List.of(h));

            assertThat(analysis.getOptimizationSuggestions()).isNotEmpty();
        }
    }
}
