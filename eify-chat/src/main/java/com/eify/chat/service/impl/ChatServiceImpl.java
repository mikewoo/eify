package com.eify.chat.service.impl;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.eify.agent.domain.entity.Agent;
import com.eify.agent.service.AgentService;
import com.eify.chat.domain.dto.SseEvent;
import com.eify.chat.domain.dto.SendChatRequest;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.domain.entity.Message;
import com.eify.chat.service.ChatService;
import com.eify.chat.service.ConversationService;
import com.eify.chat.service.MessageService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.service.ChunkService;
import com.eify.knowledge.strategy.EmbeddingStrategy;
import com.eify.mcp.domain.entity.McpTool;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.mcp.service.McpClientService;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.domain.dto.ChatMessage;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatRequest.ToolDefinition;
import com.eify.provider.domain.dto.ChatRequest.ToolDefinition.FunctionDef;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.service.ProviderService;
import tools.jackson.databind.JsonNode;
import com.eify.workflow.engine.ExecutionEvent;
import com.eify.workflow.engine.WorkflowEngine;
import com.eify.workflow.mapper.WorkflowNodeMapper;
import com.eify.workflow.domain.entity.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 聊天服务实现
 * <p>
 * 流式对话核心服务
 * <p>
 * 技术要点：
 * <ul>
 *   <li>SSE 流式响应</li>
 *   <li>异常处理：超时、断线、错误</li>
 *   <li>事务隔离：写操作独立事务</li>
 *   <li>上下文管理：滑动窗口策略</li>
 * </ul>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private static final int SSE_TIMEOUT_MS = 5 * 60 * 1000;  // 5 分钟
    private static final int DEFAULT_CONTEXT_ROUNDS = 10;    // 默认 10 轮

    private static final int MAX_TOOL_ROUNDS = 5;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AgentService agentService;
    private final ProviderService providerService;
    private final ProviderAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final ObjectMapper sseObjectMapper;
    private final ChunkService chunkService;
    private final EmbeddingStrategy embeddingStrategy;
    private final WorkflowEngine workflowEngine;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final McpClientService mcpClientService;
    private final McpToolMapper mcpToolMapper;

    private final Executor sseExecutor;

    private final ConcurrentHashMap<Long, Boolean> activeSessions = new ConcurrentHashMap<>();

    @Value("${chat.context.max-rounds:20}")
    private int maxContextRounds;

    public ChatServiceImpl(
            ConversationService conversationService,
            MessageService messageService,
            AgentService agentService,
            ProviderService providerService,
            ProviderAdapterFactory adapterFactory,
            ObjectMapper objectMapper,
            ChunkService chunkService,
            EmbeddingStrategy embeddingStrategy,
            WorkflowEngine workflowEngine,
            WorkflowNodeMapper workflowNodeMapper,
            McpClientService mcpClientService,
            McpToolMapper mcpToolMapper,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.agentService = agentService;
        this.providerService = providerService;
        this.adapterFactory = adapterFactory;
        this.objectMapper = objectMapper;
        this.sseObjectMapper = objectMapper.rebuild()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
        this.chunkService = chunkService;
        this.embeddingStrategy = embeddingStrategy;
        this.workflowEngine = workflowEngine;
        this.workflowNodeMapper = workflowNodeMapper;
        this.mcpClientService = mcpClientService;
        this.mcpToolMapper = mcpToolMapper;
        this.sseExecutor = sseExecutor;
    }

    @Override
    public SseEmitter sendMessage(Long userId, SendChatRequest request) {
        // 1. 参数校验
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 2. 获取或创建会话
        Conversation conversation = getOrCreateConversation(
                userId, request.getSessionId(), request.getAgentId(), request.getWorkflowId());

        // 2.5 防重：同一会话同一时间只能有一个活跃请求
        if (activeSessions.putIfAbsent(conversation.getId(), Boolean.TRUE) != null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该会话正在处理中，请等待回复完成后再发送");
        }

        // 3. 创建 SSE Emitter
        SseEmitter emitter = new SseEmitter((long) SSE_TIMEOUT_MS);
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        AtomicReference<String> accumulatedContent = new AtomicReference("");
        AtomicBoolean messageSaved = new AtomicBoolean(false);

        // 5. 设置回调
        emitter.onTimeout(() -> {
            activeSessions.remove(conversation.getId());
            log.warn("[ChatService] SSE 连接超时 - sessionId={}, userId={}", conversation.getId(), userId);
            // 检查是否已完成，避免重复发送
            if (!clientDisconnected.get()) {
                try {
                    sendSseEvent(emitter, SseEvent.timeout("连接超时"));
                } catch (IllegalStateException e) {
                    log.debug("[ChatService] Emitter 已完成，忽略超时事件: {}", e.getMessage());
                } catch (IOException e) {
                    log.error("[ChatService] 发送超时事件失败", e);
                }
            }
            try {
                emitter.complete();
            } catch (IllegalStateException e) {
                log.debug("[ChatService] Emitter 已完成，忽略 complete 调用");
            }
        });

        emitter.onCompletion(() -> {
            activeSessions.remove(conversation.getId());
            clientDisconnected.set(true);
            log.debug("[ChatService] SSE 连接完成 - sessionId={}, userId={}", conversation.getId(), userId);
        });

        emitter.onError(error -> {
            activeSessions.remove(conversation.getId());
            log.error("[ChatService] SSE 连接错误 - sessionId={}, userId={}, error={}",
                    conversation.getId(), userId, error.getMessage());
        });

        final Long convWorkspaceId = conversation.getWorkspaceId();

        // 6. 保存用户消息（独立事务）
        long saveStart = System.currentTimeMillis();
        Message userMessage = saveUserMessage(conversation.getId(), request.getContent(), userId, convWorkspaceId);
        log.info("[ChatService] 保存用户消息耗时 - sessionId={}, {}ms", conversation.getId(), System.currentTimeMillis() - saveStart);

        // 7. 路由决策：conversation.workflowId 优先，其次 agent.workflowId
        final Long execWorkflowId;
        final Agent agent;

        Long convWorkflowId = conversation.getWorkflowId();
        if (convWorkflowId != null) {
            execWorkflowId = convWorkflowId;
            agent = null;
        } else if (conversation.getAgentId() != null) {
            Agent a = agentService.getEntityById(conversation.getAgentId());
            if (a.getEnabled() == null || a.getEnabled() != 1) {
                throw new BusinessException(ErrorCode.AGENT_DISABLED);
            }
            agent = a;
            execWorkflowId = a.getWorkflowId();
        } else {
            execWorkflowId = null;
            agent = null;
        }

        if (execWorkflowId != null) {
            executeWorkflow(execWorkflowId, agent, conversation.getId(), request.getContent(),
                    emitter, clientDisconnected, accumulatedContent, userId, convWorkspaceId);
            return emitter;
        }

        // 8. 直接 LLM 调用需绑定 Agent
        if (agent == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "对话未绑定 Agent 或工作流");
        }

        // 9. 构建上下文
        long contextStart = System.currentTimeMillis();
        List<ChatMessage> context = buildContext(conversation.getId(), agent, request.getContent());
        log.info("[ChatService] 构建上下文耗时 - sessionId={}, messages={}, {}ms",
                conversation.getId(), context.size(), System.currentTimeMillis() - contextStart);

        // 10. 构建 LLM 请求
        Provider provider = providerService.getEntityById(agent.getDefaultProviderId());

        ChatRequest chatRequest = buildChatRequest(agent, context);

        // 11. 工具调用分支：Agent 绑定了 MCP 工具时，走工具循环
        List<Long> mcpToolIds = agent.getMcpToolIds();
        List<ToolDefinition> toolDefs = (mcpToolIds != null && !mcpToolIds.isEmpty())
                ? buildToolDefinitions(mcpToolIds) : null;

        if (toolDefs != null && !toolDefs.isEmpty()) {
            log.info("[ChatService] Agent 绑定了 {} 个工具，进入工具调用分支 - sessionId={}",
                    toolDefs.size(), conversation.getId());
            executeWithTools(emitter, provider, agent, chatRequest, context, toolDefs,
                    clientDisconnected, accumulatedContent, conversation.getId(), 0, userId, convWorkspaceId);
            return emitter;
        }

        // 12. 异步调用 LLM 并流式返回（无工具分支）
        long llmStart = System.currentTimeMillis();
        log.info("[ChatService] 开始调用 LLM API - sessionId={}, provider={}, model={}",
                conversation.getId(), provider.getType(), agent.getDefaultModel());
        Flux<ChatStreamChunk> stream;
        try {
            stream = adapterFactory
                    .getAdapter(provider.getType())
                    .streamChat(provider, chatRequest)
                    .doOnSubscribe(subscription -> {
                        log.info("[ChatService] LLM API 订阅成功 - sessionId={}, 距开始调用{}ms",
                                conversation.getId(), System.currentTimeMillis() - llmStart);
                    });
        } catch (Exception e) {
            log.error("[ChatService] 初始化 LLM 连接失败 - sessionId={}, providerType={}, error={}",
                    conversation.getId(), provider.getType(), e.getMessage());

            try {
                sendSseEvent(emitter, SseEvent.error(
                        "AI 服务暂时不可用，请稍后重试。错误: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())
                ));
            } catch (IllegalStateException ioException) {
                log.debug("[ChatService] Emitter 已完成，忽略错误事件: {}", ioException.getMessage());
            } catch (IOException ioException) {
                log.error("[ChatService] 发送错误事件失败", ioException);
            }
            try {
                emitter.complete();
            } catch (IllegalStateException ex) {
                log.debug("[ChatService] Emitter 已完成，忽略 complete 调用");
            }
            return emitter;
        }

        // 13. 订阅流
        stream.subscribe(
                // onNext: 处理每个内容块
                chunk -> {
                    try {
                        if (clientDisconnected.get()) {
                            return;
                        }

                        if (!chunk.isDone()) {
                            // 内容块
                            accumulatedContent.updateAndGet(existing -> existing + chunk.getContent());
                            sendSseEvent(emitter, SseEvent.message(chunk.getContent()));
                        } else {
                            // 保存 AI 响应（去重末尾重复句）— 必须在 complete 之前，且只保存一次
                            if (accumulatedContent.get().length() > 0 && messageSaved.compareAndSet(false, true)) {
                                saveAssistantMessageWithRetry(
                                        conversation.getId(),
                                        dedupeTrailing(accumulatedContent.get()),
                                        chunk.getUsage(),
                                        agent.getDefaultProviderId(),
                                        userId,
                                        convWorkspaceId
                                );
                            }

                            sendSseEvent(emitter, SseEvent.complete(
                                    convertUsage(chunk.getUsage()),
                                    chunk.getFinishReason()
                            ));
                        }
                    } catch (IllegalStateException e) {
                        clientDisconnected.set(true);
                        log.debug("[ChatService] Emitter 已完成，客户端断开连接 - sessionId={}", conversation.getId());
                    } catch (IOException e) {
                        clientDisconnected.set(true);
                        log.warn("[ChatService] 客户端断开连接 - sessionId={}", conversation.getId());
                    }
                },
                // onError: 处理错误
                error -> {
                    clientDisconnected.set(true);
                    log.error("[ChatService] LLM 流式调用失败 - sessionId={}, error={}",
                            conversation.getId(), error.getMessage());
                    try {
                        sendSseEvent(emitter, SseEvent.error(error.getMessage()));
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] Emitter 已完成，忽略错误事件: {}", e.getMessage());
                    } catch (IOException e) {
                        log.error("[ChatService] 发送错误事件失败", e);
                    }
                    // 错误时也要完成 emitter
                    try {
                        emitter.complete();
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] Emitter 已完成，忽略 complete 调用");
                    }
                },
                // onComplete: 流正常结束
                () -> {
                    clientDisconnected.set(true);
                    log.info("[ChatService] LLM 流正常结束 - sessionId={}", conversation.getId());
                    // 确保 emitter 完成（可能在 onNext 中已经完成）
                    try {
                        emitter.complete();
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] Emitter 已完成，忽略 complete 调用");
                    }
                }
        );

        return emitter;
    }

    @Override
    public int getDefaultContextRounds() {
        return DEFAULT_CONTEXT_ROUNDS;
    }

    // ========== 私有方法 ==========

    /**
     * 获取或创建会话
     */
    private Conversation getOrCreateConversation(Long userId, Long sessionId, Long agentId, Long workflowId) {
        if (sessionId != null) {
            // 继续已有会话
            return conversationService.getById(sessionId);
        } else {
            // 创建新会话
            return conversationService.create(userId, agentId, workflowId, null);
        }
    }

    /**
     * 保存用户消息（独立事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public Message saveUserMessage(Long sessionId, String content, Long userId, Long workspaceId) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setWorkspaceId(workspaceId);
        message.setRole("user");
        message.setContent(content);
        message.setCreatorId(userId);

        return messageService.save(message);
    }

    /**
     * 保存 AI 响应（独立事务），带重试机制应对瞬时 DB 故障。
     * <p>
     * 保存失败时最多重试 3 次（指数退避：100ms / 200ms / 400ms），
     * 全部失败后写入死信日志（含完整消息内容），可通过 ClickHouse 查询恢复。
     */
    private void saveAssistantMessageWithRetry(Long sessionId, String content,
                                                ChatResponse.Usage usage, Long modelId, Long userId, Long workspaceId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                saveAssistantMessage(sessionId, content, usage, modelId, userId, workspaceId);
                return;
            } catch (Exception e) {
                if (attempt < 2) {
                    log.warn("[ChatService] 保存助手消息失败，重试 {}/2: sessionId={}, error={}",
                            attempt + 1, sessionId, e.getMessage());
                    try {
                        Thread.sleep((long) (100 * Math.pow(2, attempt)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        writeDeadLetter(sessionId, content, usage, modelId, userId, workspaceId, ie);
                        return;
                    }
                }
            }
        }
        writeDeadLetter(sessionId, content, usage, modelId, userId, workspaceId,
                new RuntimeException("3次重试全部失败"));
    }

    private void writeDeadLetter(Long sessionId, String content, ChatResponse.Usage usage,
                                  Long modelId, Long userId, Long workspaceId, Exception cause) {
        int tokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
        log.error("[DEAD_LETTER_MSG] sessionId={} userId={} workspaceId={} modelId={} tokens={} content={}",
                sessionId, userId, workspaceId, modelId, tokens,
                content != null ? content.substring(0, Math.min(content.length(), 5000)) : "null",
                cause);
    }

    /**
     * 保存 AI 响应（独立事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(Long sessionId, String content,
                                     ChatResponse.Usage usage, Long modelId, Long userId, Long workspaceId) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setWorkspaceId(workspaceId);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatorId(userId);

        if (usage != null) {
            message.setTokensUsed(usage.getTotalTokens());
        }

        messageService.save(message);
    }

    /**
     * 构建对话上下文
     * <p>
     * 滑动窗口策略：
     * <ul>
     *   <li>系统提示词始终保留</li>
     *   <li>最近 N 轮对话（N = min(request.contextRounds, agent.maxHistoryRounds, maxContextRounds)）</li>
     *   <li>当前用户消息</li>
     * </ul>
     */
    private List<ChatMessage> buildContext(Long sessionId, Agent agent, String currentUserMessage) {
        List<ChatMessage> context = new ArrayList<>();

        // 1. 系统提示词（可能被 RAG 内容增强）
        String systemPrompt = agent.getSystemPrompt();

        List<Long> knowledgeIds = agent.getKnowledgeIds();
        if (knowledgeIds != null && !knowledgeIds.isEmpty()
                && Integer.valueOf(1).equals(agent.getRagEnabled())) {
            try {
                float[] queryVector = embeddingStrategy.embed(currentUserMessage);
                int topK = agent.getRagTopK() != null ? agent.getRagTopK() : 5;

                // 按各知识库配额分别检索
                int perKbTopK = Math.max(topK / knowledgeIds.size(), 1);
                List<ChunkRepository.ChunkSearchResult> allChunks = new ArrayList<>();
                for (Long kbId : knowledgeIds) {
                    try {
                        List<ChunkRepository.ChunkSearchResult> kbChunks =
                                chunkService.search(kbId, queryVector, perKbTopK);
                        allChunks.addAll(kbChunks);
                    } catch (Exception e) {
                        log.warn("[ChatService] 知识库 {} 检索失败，跳过 - sessionId={}, error={}",
                                kbId, sessionId, e.getMessage());
                    }
                }

                // 合并、去重、重排序
                List<ChunkRepository.ChunkSearchResult> merged = mergeAndRerank(allChunks, topK);

                if (!merged.isEmpty()) {
                    systemPrompt = buildRagPrompt(systemPrompt, merged);
                    log.info("[ChatService] RAG 检索命中 {} 条 ({} 个知识库) - sessionId={}",
                            merged.size(), knowledgeIds.size(), sessionId);
                }
            } catch (Exception e) {
                log.error("[ChatService] RAG 检索失败，降级为普通对话 - sessionId={}, error={}",
                        sessionId, e.getMessage());
            }
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            context.add(new ChatMessage("system", systemPrompt));
        }

        // 2. 加载历史消息（滑动窗口）
        int rounds = agent.getMaxHistoryRounds() != null
                ? Math.min(agent.getMaxHistoryRounds(), maxContextRounds)
                : DEFAULT_CONTEXT_ROUNDS;

        List<Message> history = messageService.loadRecentMessages(sessionId, rounds * 2);
        for (Message msg : history) {
            context.add(new ChatMessage(msg.getRole(), msg.getContent()));
        }

        // 3. 添加当前用户消息
        context.add(new ChatMessage("user", currentUserMessage));

        log.debug("[ChatService] 构建上下文 - sessionId={}, messages={}, rounds={}",
                sessionId, context.size(), rounds);

        return context;
    }

    /**
     * 构建 RAG 增强的系统提示词
     * 将检索到的知识库片段注入系统提示词末尾
     */
    private String buildRagPrompt(String systemPrompt, List<ChunkRepository.ChunkSearchResult> chunks) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt).append("\n\n");
        }
        sb.append("---\n以下是从知识库中检索到的相关内容，请基于这些信息回答用户问题：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            ChunkRepository.ChunkSearchResult chunk = chunks.get(i);
            sb.append("【").append(i + 1).append("】").append(chunk.content());
            if (chunk.similarity() > 0) {
                sb.append(" (相似度: ").append(String.format("%.2f", chunk.similarity())).append(")");
            }
            sb.append("\n\n");
        }
        sb.append("---");
        return sb.toString();
    }

    /**
     * 合并多个知识库的检索结果：按内容去重，按相似度降序重排序，取 topK
     */
    private List<ChunkRepository.ChunkSearchResult> mergeAndRerank(
            List<ChunkRepository.ChunkSearchResult> allChunks, int topK) {
        return allChunks.stream()
                .collect(Collectors.toMap(
                        c -> c.content(),
                        c -> c,
                        (a, b) -> a.similarity() > b.similarity() ? a : b
                ))
                .values().stream()
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 构建 LLM 请求
     */
    private ChatRequest buildChatRequest(Agent agent, List<ChatMessage> context) {
        return ChatRequest.builder()
                .model(agent.getDefaultModel())
                .messages(context)
                .temperature(agent.getTemperature() != null ? agent.getTemperature().doubleValue() : 0.7)
                .maxTokens(agent.getMaxTokens() != null ? agent.getMaxTokens() : 2000)
                .topP(agent.getTopP() != null ? agent.getTopP().doubleValue() : 1.0)
                .frequencyPenalty(agent.getFrequencyPenalty() != null ? agent.getFrequencyPenalty().doubleValue() : 0.0)
                .presencePenalty(agent.getPresencePenalty() != null ? agent.getPresencePenalty().doubleValue() : 0.0)
                .build();
    }

    /**
     * 发送 SSE 事件。以 emitter 实例为锁，保证同一连接的发送互斥，
     * 同时允许不同连接的发送并发执行。
     */
    private void sendSseEvent(SseEmitter emitter, SseEvent event) throws IOException {
        synchronized (emitter) {
            String jsonData = sseObjectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name(event.getEvent())
                    .data(jsonData));
        }
    }

    /**
     * 完成并返回错误
     */
    private SseEmitter completeWithError(SseEmitter emitter, String errorMessage) {
        try {
            sendSseEvent(emitter, SseEvent.error(errorMessage));
        } catch (IOException e) {
            log.error("[ChatService] 发送错误事件失败", e);
        }
        emitter.completeWithError(new BusinessException(ErrorCode.PROVIDER_CALL_FAILED, errorMessage));
        return emitter;
    }

    /**
     * 工作流执行：提交到 WorkflowEngine，桥接事件到 SSE。
     */
    private void executeWorkflow(Long workflowId, Agent agent, Long conversationId, String userMessage,
                                  SseEmitter emitter, AtomicBoolean clientDisconnected,
                                  AtomicReference<String> accumulatedContent, Long userId, Long workspaceId) {
        BlockingQueue<ExecutionEvent> eventQueue = new LinkedBlockingQueue<>();
        // user_input 保持干净（仅当前消息），conversation_input 包含历史上下文供提取节点使用
        Map<String, Object> inputVars = new java.util.HashMap<>();
        inputVars.put("user_input", userMessage);
        inputVars.put("conversation_input", buildWorkflowInput(conversationId, userMessage));

        // 找到 End 节点的 outputKey，用于过滤哪些 LLM 输出是最终展示内容
        String displayOutputKey = getEndNodeOutputKey(workflowId);

        try {
            workflowEngine.submit(workflowId, inputVars, eventQueue);
        } catch (Exception e) {
            log.error("[ChatService] 工作流提交失败 - workflowId={}", workflowId, e);
            try {
                sendSseEvent(emitter, SseEvent.error("工作流启动失败: " + e.getMessage()));
            } catch (IllegalStateException | IOException ex) {
                log.debug("[ChatService] SSE 已关闭，无法发送错误事件");
            }
            try {
                emitter.complete();
            } catch (IllegalStateException ex) {
                log.debug("[ChatService] Emitter 已完成");
            }
            return;
        }

        sseExecutor.execute(() -> {
            Set<String> emittedOutputKeys = new java.util.HashSet<>();
            try {
                while (true) {
                    ExecutionEvent event = eventQueue.poll(5, TimeUnit.SECONDS);
                    if (event == null) {
                        try {
                            emitter.send(SseEmitter.event().comment("keepalive"));
                        } catch (IllegalStateException e) {
                            log.debug("[ChatService] SSE 连接已关闭，停止工作流桥接");
                            break;
                        }
                        continue;
                    }

                    try {
                        switch (event.getEvent()) {
                            case "node_started" -> {
                                log.info("[ChatService] 工作流节点开始: nodeName={}, nodeType={}",
                                        event.getNodeName(), event.getNodeType());
                            }
                            case "node_completed" -> {
                                log.info("[ChatService] 工作流节点完成: nodeName={}",
                                        event.getNodeName());
                                if (("llm".equals(event.getNodeType()) || "code".equals(event.getNodeType()))
                                        && event.getOutputs() != null) {
                                    // 优先取 End 节点 outputKey 对应的输出，不匹配时回退到第一个非系统文本输出
                                    Object targetOutput = null;
                                    String matchedKey = null;
                                    if (displayOutputKey != null) {
                                        targetOutput = event.getOutputs().get(displayOutputKey);
                                        matchedKey = displayOutputKey;
                                    }
                                    if (targetOutput == null) {
                                        for (Map.Entry<String, Object> e : event.getOutputs().entrySet()) {
                                            if (!e.getKey().startsWith("_") && e.getValue() instanceof String text && !text.isEmpty()) {
                                                targetOutput = text;
                                                matchedKey = e.getKey();
                                                break;
                                            }
                                        }
                                    }
                                    if (targetOutput instanceof String text && !text.isEmpty() && matchedKey != null) {
                                        String dedupKey = event.getNodeName() + ":" + matchedKey;
                                        if (emittedOutputKeys.add(dedupKey)) {
                                            accumulatedContent.updateAndGet(e -> e + text);
                                            sendSseEvent(emitter, SseEvent.message(text));
                                        }
                                    }
                                }
                            }
                            case "node_failed" -> {
                                log.error("[ChatService] 工作流节点失败: nodeName={}, error={}",
                                        event.getNodeName(), event.getError());
                                sendSseEvent(emitter, SseEvent.error(
                                        event.getError() != null ? event.getError() : "节点执行失败"));
                                emitter.complete();
                                return;
                            }
                            case "execution_completed" -> {
                                String finalContent = dedupeTrailing(accumulatedContent.get());
                                sendSseEvent(emitter, SseEvent.complete(null, "stop"));
                                if (finalContent.length() > 0) {
                                    saveAssistantMessageWithRetry(conversationId, finalContent, null, null, userId, workspaceId);
                                }
                                emitter.complete();
                                return;
                            }
                            case "execution_failed" -> {
                                log.error("[ChatService] 工作流执行失败: error={}",
                                        event.getError());
                                sendSseEvent(emitter, SseEvent.error(
                                        event.getError() != null ? event.getError() : "工作流执行失败"));
                                emitter.complete();
                                return;
                            }
                        }
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] SSE 连接已关闭");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("[ChatService] 工作流桥接异常", e);
                try {
                    sendSseEvent(emitter, SseEvent.error("工作流执行异常: " + e.getMessage()));
                } catch (IllegalStateException | IOException ex) {
                    log.debug("[ChatService] SSE 已关闭");
                }
            } finally {
                try {
                    emitter.complete();
                } catch (IllegalStateException e) {
                    log.debug("[ChatService] Emitter 已完成");
                }
            }
        });
    }

    /**
     * 查询工作流的 End 节点 outputKey。
     * 用于在 SSE 桥接中过滤，只发送最终展示内容。
     */
    private String getEndNodeOutputKey(Long workflowId) {
        try {
            List<WorkflowNode> nodes = workflowNodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowNode>()
                            .eq(WorkflowNode::getWorkflowId, workflowId)
                            .eq(WorkflowNode::getType, "end"));
            if (nodes.isEmpty()) {
                return null;
            }
            JsonNode config = nodes.get(0).getConfig();
            if (config != null && config.has("outputKey")) {
                return config.get("outputKey").asText();
            }
        } catch (Exception e) {
            log.warn("[ChatService] 获取 End 节点 outputKey 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 构建工作流输入：将最近用户消息拼入当前消息，使代码提取节点能跨轮匹配订单号/用户ID等信息。
     * <p>
     * 仅包含用户消息，避免助手中的示例文本（如 "ORD-10001"）被正则误提取。
     */
    private String buildWorkflowInput(Long conversationId, String currentMessage) {
        try {
            List<Message> history = messageService.loadRecentMessages(conversationId, 6);
            List<String> userMessages = new ArrayList<>();
            for (Message msg : history) {
                if ("user".equals(msg.getRole()) && !msg.getContent().equals(currentMessage)) {
                    userMessages.add("用户：" + msg.getContent());
                }
            }
            if (userMessages.isEmpty()) {
                return currentMessage;
            }
            return String.join("\n", userMessages) + "\n用户：" + currentMessage;
        } catch (Exception e) {
            log.warn("[ChatService] 构建上下文输入失败，回退到仅当前消息: {}", e.getMessage());
            return currentMessage;
        }
    }

    /**
     * 转换 Token 使用数据
     */
    private SseEvent.UsageData convertUsage(ChatResponse.Usage usage) {
        if (usage == null) {
            return null;
        }
        return SseEvent.UsageData.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    // ========== MCP 工具调用 ==========

    /**
     * 构建工具定义列表（从 MCP 工具 ID 加载 schema）
     */
    private List<ToolDefinition> buildToolDefinitions(List<Long> toolIds) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Long toolId : toolIds) {
            McpTool tool = mcpToolMapper.selectById(toolId);
            if (tool == null) continue;
            defs.add(ToolDefinition.builder()
                    .type("function")
                    .function(FunctionDef.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .parameters(tool.getInputSchema())
                            .build())
                    .build());
        }
        return defs;
    }

    /**
     * 工具调用循环：LLM 决定调用工具 → 执行工具 → 结果回传 → 循环直到 LLM 返回文本。
     * <p>
     * <b>异步模式说明</b>：本方法看似在末尾"递归"调用自身（line: executeWithTools(...)），
     * 实际上这是<b>异步回调链</b>，而非栈递归。每次调用通过 {@code stream.collectList().subscribe(...)}
     * 发起新的 HTTP 请求，前一次调用的栈帧在 {@code subscribe()} 返回后即已弹出。
     * 因此工具调用轮数只受 {@code MAX_TOOL_ROUNDS} 限制，不会导致栈溢出。
     */
    private void executeWithTools(SseEmitter emitter, Provider provider, Agent agent,
                                   ChatRequest chatRequest, List<ChatMessage> context,
                                   List<ToolDefinition> toolDefs,
                                   AtomicBoolean clientDisconnected,
                                   AtomicReference<String> accumulatedContent,
                                   Long conversationId, int round, Long userId, Long workspaceId) {
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("[ChatService] 工具调用超过最大轮数 {} - sessionId={}", MAX_TOOL_ROUNDS, conversationId);
            try {
                sendSseEvent(emitter, SseEvent.message("抱歉，工具调用次数过多，请稍后重试。"));
                sendSseEvent(emitter, SseEvent.complete(null, "stop"));
            } catch (IllegalStateException e) {
                log.debug("[ChatService] Emitter 已完成");
            } catch (IOException e) {
                log.error("[ChatService] 发送超限错误失败", e);
            }
            try {
                emitter.complete();
            } catch (IllegalStateException e) {
                log.debug("[ChatService] Emitter 已完成");
            }
            return;
        }

        chatRequest.setTools(toolDefs);
        chatRequest.setMessages(context);

        Flux<ChatStreamChunk> stream = adapterFactory
                .getAdapter(provider.getType())
                .streamChat(provider, chatRequest);

        stream.collectList().subscribe(
                chunks -> {
                    if (clientDisconnected.get()) return;

                    List<ChatStreamChunk.ToolCallChunk> toolCallChunks = extractToolCallChunks(chunks);
                    if (!toolCallChunks.isEmpty()) {
                        // --- 有工具调用：执行工具，继续循环 ---
                        List<ChatMessage.ToolCall> toolCalls = toToolCalls(toolCallChunks);
                        if (!toolCalls.isEmpty()) {
                            log.info("[ChatService] LLM 请求调用 {} 个工具 - sessionId={}, round={}",
                                    toolCalls.size(), conversationId, round);

                            context.add(ChatMessage.assistantWithToolCalls(toolCalls));

                            for (ChatMessage.ToolCall tc : toolCalls) {
                                String toolResult = executeToolSafely(tc, toolDefs);
                                context.add(ChatMessage.tool(tc.getId(), tc.getName(), toolResult));
                            }

                            executeWithTools(emitter, provider, agent, chatRequest, context,
                                    toolDefs, clientDisconnected, accumulatedContent,
                                    conversationId, round + 1, userId, workspaceId);
                            return;
                        }
                    }

                    // --- 无工具调用：正常文本回复，流式输出到 SSE ---
                    for (ChatStreamChunk chunk : chunks) {
                        if (clientDisconnected.get()) return;
                        try {
                            if (!chunk.isDone()) {
                                if (chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                                    accumulatedContent.updateAndGet(e -> e + chunk.getContent());
                                    sendSseEvent(emitter, SseEvent.message(chunk.getContent()));
                                }
                            } else {
                                if (accumulatedContent.get().length() > 0) {
                                    saveAssistantMessageWithRetry(conversationId,
                                            accumulatedContent.get(), chunk.getUsage(),
                                            agent.getDefaultProviderId(), userId, workspaceId);
                                }
                                sendSseEvent(emitter, SseEvent.complete(
                                        convertUsage(chunk.getUsage()),
                                        chunk.getFinishReason() != null ? chunk.getFinishReason() : "stop"));
                            }
                        } catch (IllegalStateException e) {
                            clientDisconnected.set(true);
                            log.debug("[ChatService] Emitter 已完成");
                        } catch (IOException e) {
                            clientDisconnected.set(true);
                            log.warn("[ChatService] 客户端断开连接 - sessionId={}", conversationId);
                        }
                    }
                    try {
                        emitter.complete();
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] Emitter 已完成");
                    }
                },
                error -> {
                    clientDisconnected.set(true);
                    log.error("[ChatService] 工具调用流程失败 - sessionId={}, error={}",
                            conversationId, error.getMessage());
                    try {
                        sendSseEvent(emitter, SseEvent.error(error.getMessage()));
                    } catch (IllegalStateException | IOException e) {
                        log.debug("[ChatService] 发送错误事件失败");
                    }
                    try {
                        emitter.complete();
                    } catch (IllegalStateException e) {
                        log.debug("[ChatService] Emitter 已完成");
                    }
                }
        );
    }

    /**
     * 从 chunks 中提取工具调用（来自 done chunk 的 toolCalls）
     */
    private List<ChatStreamChunk.ToolCallChunk> extractToolCallChunks(List<ChatStreamChunk> chunks) {
        for (ChatStreamChunk chunk : chunks) {
            if (chunk.isDone() && "tool_calls".equals(chunk.getFinishReason())
                    && chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                return chunk.getToolCalls();
            }
        }
        return List.of();
    }

    /**
     * 将 ToolCallChunk 转换为 ChatMessage.ToolCall（解析 arguments JSON）
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage.ToolCall> toToolCalls(List<ChatStreamChunk.ToolCallChunk> chunks) {
        List<ChatMessage.ToolCall> result = new ArrayList<>();
        for (ChatStreamChunk.ToolCallChunk tc : chunks) {
            try {
                Map<String, Object> args = objectMapper.readValue(tc.getArguments(), Map.class);
                result.add(ChatMessage.ToolCall.builder()
                        .id(tc.getId())
                        .name(tc.getName())
                        .arguments(args)
                        .build());
            } catch (Exception e) {
                log.error("[ChatService] 解析工具调用参数失败: tool={}, args={}, error={}",
                        tc.getName(), tc.getArguments(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 安全执行工具调用：失败时返回错误信息，不抛异常
     */
    private String executeToolSafely(ChatMessage.ToolCall tc, List<ToolDefinition> toolDefs) {
        Long serverId = findServerIdForTool(tc.getName(), toolDefs);
        if (serverId == null) {
            return "工具未找到对应的 MCP Server: " + tc.getName();
        }
        try {
            return mcpClientService.callTool(serverId, tc.getName(), tc.getArguments());
        } catch (Exception e) {
            log.error("[ChatService] 工具调用失败: tool={}, serverId={}, error={}",
                    tc.getName(), serverId, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }

    /**
     * 从 toolDefs 中查找工具对应的 MCP Server ID。
     * serverId 存储在 ToolDefinition 的 extraParams 中（由 buildToolDefinitions 写入）。
     */
    /**
     * 去除末尾重复的句子（LLM 偶尔会在结尾重复最后一句）。
     */
    private String dedupeTrailing(String text) {
        if (text == null || text.length() < 10) return text;
        String[] parts = text.split("(?<=[。！？\\n])(?=\\S)");
        if (parts.length < 2) return text;
        String last = parts[parts.length - 1].trim();
        String prev = parts[parts.length - 2].trim();
        if (last.length() > 5 && last.equals(prev)) {
            return text.substring(0, text.length() - parts[parts.length - 1].length()).trim();
        }
        return text;
    }

    private Long findServerIdForTool(String toolName, List<ToolDefinition> toolDefs) {
        // 遍历 mcpToolMapper 查询 serverId
        // 这里用数据库查询保证准确性（避免在 ToolDefinition 里缓存 serverId）
        try {
            List<McpTool> tools = mcpToolMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpTool>()
                            .eq(McpTool::getName, toolName));
            if (tools != null && !tools.isEmpty()) {
                return tools.get(0).getServerId();
            }
        } catch (Exception e) {
            log.warn("[ChatService] 查找工具 Server ID 失败: toolName={}, error={}", toolName, e.getMessage());
        }
        return null;
    }
}
