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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final KnowledgeRepository knowledgeRepository;
    private final RetrievalStrategy retrievalStrategy;
    private final ProviderService providerService;
    private final ProviderAdapterFactory adapterFactory;

    @Resource(name = "embeddingExecutor")
    private Executor embeddingExecutor;

    @Override
    public List<ChunkRepository.ChunkSearchResult> retrieve(RetrievalRequest request) {
        KnowledgeBase knowledge = requireKnowledgeInWorkspace(request.getKnowledgeId());
        return retrieve(request, retrievalStrategy);
    }

    @Override
    public List<ChunkRepository.ChunkSearchResult> retrieve(
            RetrievalRequest request, RetrievalStrategy strategy) {
        log.info("Retrieving knowledge: {}, query: {}, strategy: {}",
                request.getKnowledgeId(), request.getQuery(), request.getStrategy());

        try {
            KnowledgeBase knowledge = requireKnowledgeInWorkspace(request.getKnowledgeId());

            List<ChunkRepository.ChunkSearchResult> chunks;

            switch (request.getStrategy().toLowerCase()) {
                case "vector":
                    chunks = strategy.vectorRetrieval(request.getQuery(), knowledge, request.getTopK());
                    break;
                case "keyword":
                    chunks = strategy.keywordRetrieval(request.getQuery(), knowledge, request.getTopK());
                    break;
                case "hybrid":
                default:
                    chunks = strategy.hybridRetrieval(
                            request.getQuery(),
                            knowledge,
                            request.getTopK(),
                            request.getKeywordWeight(),
                            request.getVectorWeight()
                    );
                    break;
            }

            knowledgeRepository.incrementRetrievalCount(request.getKnowledgeId());
            return chunks;
        } catch (Exception e) {
            log.error("Retrieval failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public String ragChat(RagRequest request) {
        log.info("RAG chat: knowledgeId={}, query: {}",
                request.getKnowledgeId(), request.getQuery());

        requireKnowledgeInWorkspace(request.getKnowledgeId());

        try {
            RetrievalRequest retrievalRequest = new RetrievalRequest();
            retrievalRequest.setKnowledgeId(request.getKnowledgeId());
            retrievalRequest.setQuery(request.getQuery());
            retrievalRequest.setTopK(5);
            retrievalRequest.setStrategy("hybrid");

            List<ChunkRepository.ChunkSearchResult> retrievedChunks = retrieve(retrievalRequest);

            if (retrievedChunks.isEmpty()) {
                log.warn("No relevant chunks found for query: {}", request.getQuery());
                return "抱歉，我没有找到相关的信息来回答您的问题。";
            }

            String context = buildContext(retrievedChunks);
            String systemPrompt = buildSystemPrompt(request.getSystemPrompt(), context);
            String userPrompt = buildUserPrompt(request.getQuery(), request.getConversationHistory());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", systemPrompt));
            messages.add(new ChatMessage("user", userPrompt));

            ChatRequest chatRequest = ChatRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .temperature(request.getTemperature() != null
                            ? request.getTemperature().doubleValue() : 0.7)
                    .build();

            if (request.getProviderId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "providerId 不能为空");
            }
            Provider provider = providerService.getEntityById(request.getProviderId());
            ChatResponse response = adapterFactory.getAdapter(provider.getType())
                    .chat(provider, chatRequest);

            return response != null ? response.getContent() : "抱歉，生成回答失败。";
        } catch (Exception e) {
            log.error("RAG chat failed", e);
            return "抱歉，处理您的请求时出现了错误，请稍后重试。";
        }
    }

    @Override
    public List<List<ChunkRepository.ChunkSearchResult>> batchRetrieve(
            List<RetrievalRequest> requests) {
        List<CompletableFuture<List<ChunkRepository.ChunkSearchResult>>> futures = requests.stream()
            .map(request -> CompletableFuture.supplyAsync(
                    () -> retrieve(request), embeddingExecutor))
            .collect(Collectors.toList());

        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getRetrievalSuggestions(String query, Long knowledgeId) {
        Set<String> suggestions = new HashSet<>();

        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId(knowledgeId);
        req.setQuery(query);
        req.setTopK(10);

        List<ChunkRepository.ChunkSearchResult> chunks = retrieve(req);

        chunks.forEach(chunk -> {
            String[] words = chunk.content().split("\\s+");
            for (String word : words) {
                if (word.length() > 3) {
                    suggestions.add(word);
                }
            }
        });

        return suggestions.stream()
            .limit(5)
            .collect(Collectors.toList());
    }

    @Override
    public RetrievalAnalysis analyzeRetrieval(List<RetrievalHistory> history) {
        RetrievalAnalysis analysis = new RetrievalAnalysis();

        if (history.isEmpty()) {
            analysis.setAverageRelevance(0f);
            analysis.setAverageResponseTime(0L);
            analysis.setRetrievalEfficiency(0f);
            return analysis;
        }

        float totalRelevance = history.stream()
            .map(h -> h.getRelevanceScore() != null ? h.getRelevanceScore() : 0f)
            .reduce(0f, Float::sum);
        analysis.setAverageRelevance(totalRelevance / history.size());

        long totalTime = history.stream()
            .mapToLong(h -> h.getResponseTime() != null ? h.getResponseTime() : 0L)
            .sum();
        analysis.setAverageResponseTime(totalTime / history.size());

        analysis.setRetrievalEfficiency(calculateEfficiency(history));
        analysis.setOptimizationSuggestions(generateOptimizationSuggestions(history));
        analysis.setCommonIssues(findCommonIssues(history));

        return analysis;
    }

    private KnowledgeBase requireKnowledgeInWorkspace(Long knowledgeId) {
        KnowledgeBase knowledge = knowledgeRepository.selectById(knowledgeId);
        if (knowledge == null || !knowledge.getWorkspaceId()
                .equals(CurrentContext.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        return knowledge;
    }

    private String buildContext(List<ChunkRepository.ChunkSearchResult> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("以下是与用户问题相关的文档内容：\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            context.append(String.format("【文档%d】\n%s\n\n", i + 1, chunk.content()));
        }

        return context.toString();
    }

    private String buildSystemPrompt(String customPrompt, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的AI助手，请基于以下提供的文档内容回答用户的问题。\n\n");
        prompt.append("文档内容：\n");
        prompt.append(context);
        prompt.append("\n");
        prompt.append("回答要求：\n");
        prompt.append("1. 基于文档内容回答，不要编造信息\n");
        prompt.append("2. 如果文档中没有相关信息，请明确告知\n");
        prompt.append("3. 回答要准确、简洁、有条理\n");
        prompt.append("4. 如果文档内容与问题相关但不完整，可以在不偏离主题的情况下进行合理推断\n\n");

        if (StringUtils.hasText(customPrompt)) {
            prompt.append("额外要求：\n");
            prompt.append(customPrompt);
            prompt.append("\n\n");
        }

        return prompt.toString();
    }

    private String buildUserPrompt(String query, String conversationHistory) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(conversationHistory)) {
            prompt.append("对话历史：\n");
            prompt.append(conversationHistory);
            prompt.append("\n\n");
        }
        prompt.append("用户问题：\n");
        prompt.append(query);
        return prompt.toString();
    }

    private float calculateEfficiency(List<RetrievalHistory> history) {
        float totalScore = 0f;
        long totalTime = 0L;
        for (RetrievalHistory item : history) {
            totalScore += item.getRelevanceScore() != null ? item.getRelevanceScore() : 0f;
            totalTime += item.getResponseTime() != null ? item.getResponseTime() : 0L;
        }
        if (totalTime > 0) {
            return (totalScore / history.size()) / (totalTime / history.size());
        }
        return 0f;
    }

    private List<String> generateOptimizationSuggestions(List<RetrievalHistory> history) {
        List<String> suggestions = new ArrayList<>();
        float avgRelevance = (float) history.stream()
            .map(h -> h.getRelevanceScore() != null ? h.getRelevanceScore() : 0f)
            .mapToDouble(Float::doubleValue)
            .average()
            .orElse(0.0);

        if (avgRelevance < 0.5) {
            suggestions.add("相关性较低，建议优化分块策略或增加文档上下文");
        }
        long avgResponseTime = (long) history.stream()
            .mapToLong(h -> h.getResponseTime() != null ? h.getResponseTime() : 0L)
            .average()
            .orElse(0.0);
        if (avgResponseTime > 5000) {
            suggestions.add("响应时间较长，建议启用缓存或优化检索算法");
        }
        return suggestions;
    }

    private List<String> findCommonIssues(List<RetrievalHistory> history) {
        List<String> issues = new ArrayList<>();
        long emptyResults = history.stream()
            .filter(item -> item.getRetrievedContent() == null
                    || item.getRetrievedContent().isEmpty())
            .count();
        if (emptyResults > history.size() * 0.3) {
            issues.add("检索结果经常为空，可能需要增加更多相关文档");
        }
        long zeroRelevance = history.stream()
            .filter(item -> item.getRelevanceScore() != null
                    && item.getRelevanceScore() == 0f)
            .count();
        if (zeroRelevance > history.size() * 0.5) {
            issues.add("相关性分数普遍较低，建议调整检索权重");
        }
        return issues;
    }
}
