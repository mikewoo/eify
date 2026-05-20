package com.eify.knowledge.controller;

import com.eify.common.result.Result;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.service.RetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetrievalController")
class RetrievalControllerTest {

    @Mock
    RetrievalService retrievalService;

    @InjectMocks
    RetrievalController controller;

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("检索成功应返回 Result.success")
        void shouldReturnSuccessWithResults() {
            RetrievalService.RetrievalRequest req = new RetrievalService.RetrievalRequest();
            req.setKnowledgeId(1L);
            req.setQuery("test");
            List<ChunkRepository.ChunkSearchResult> results = List.of(
                    new ChunkRepository.ChunkSearchResult(1L, 1L, 10L, 0, "content", 0.9f));
            when(retrievalService.retrieve(any())).thenReturn(results);

            Result<List<ChunkRepository.ChunkSearchResult>> result = controller.search(req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("ragChat")
    class RagChatTests {

        @Test
        @DisplayName("RAG 对话应委托 service 并返回结果")
        void shouldDelegateAndReturnResult() {
            RetrievalService.RagRequest req = new RetrievalService.RagRequest();
            req.setKnowledgeId(1L);
            req.setQuery("hello");
            when(retrievalService.ragChat(any())).thenReturn("回答内容");

            Result<String> result = controller.ragChat(req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo("回答内容");
        }
    }

    @Nested
    @DisplayName("batchSearch")
    class BatchSearchTests {

        @Test
        @DisplayName("批量检索应委托 service 并返回结果")
        void shouldDelegateAndReturnResults() {
            List<RetrievalService.RetrievalRequest> requests = List.of(new RetrievalService.RetrievalRequest());
            List<List<ChunkRepository.ChunkSearchResult>> batchResults = List.of(List.of());
            when(retrievalService.batchRetrieve(requests)).thenReturn(batchResults);

            Result<List<List<ChunkRepository.ChunkSearchResult>>> result = controller.batchSearch(requests);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(batchResults);
        }
    }

    @Nested
    @DisplayName("getSuggestions")
    class GetSuggestionsTests {

        @Test
        @DisplayName("获取建议应委托 service 并返回结果")
        void shouldDelegateAndReturnSuggestions() {
            List<String> suggestions = List.of("keyword1", "keyword2");
            when(retrievalService.getRetrievalSuggestions("query", 1L)).thenReturn(suggestions);

            Result<List<String>> result = controller.getSuggestions(1L, "query");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("analyze")
    class AnalyzeTests {

        @Test
        @DisplayName("分析应委托 service 并返回结果")
        void shouldDelegateAndReturnAnalysis() {
            List<RetrievalService.RetrievalHistory> history = List.of(new RetrievalService.RetrievalHistory());
            RetrievalService.RetrievalAnalysis analysis = new RetrievalService.RetrievalAnalysis();
            analysis.setAverageRelevance(0.8f);
            when(retrievalService.analyzeRetrieval(history)).thenReturn(analysis);

            Result<RetrievalService.RetrievalAnalysis> result = controller.analyze(history);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getAverageRelevance()).isEqualTo(0.8f);
        }
    }
}
