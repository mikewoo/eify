package com.eify.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmHttpClient;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.agent.domain.dto.*;
import com.eify.agent.domain.entity.Agent;
import com.eify.agent.domain.entity.AgentKnowledge;
import com.eify.agent.mapper.AgentKnowledgeMapper;
import com.eify.agent.mapper.AgentMapper;
import com.eify.agent.service.AgentService;
import com.eify.mcp.domain.entity.AgentMcpTool;
import com.eify.common.context.CurrentContext;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.domain.entity.McpTool;
import com.eify.mcp.mapper.AgentMcpToolMapper;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ProviderMapper;
import com.eify.provider.service.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 服务实现
 */
@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final AgentMapper agentMapper;
    private final AgentKnowledgeMapper agentKnowledgeMapper;
    private final AgentMcpToolMapper agentMcpToolMapper;
    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentServiceImpl(
            AgentMapper agentMapper,
            AgentKnowledgeMapper agentKnowledgeMapper,
            AgentMcpToolMapper agentMcpToolMapper,
            McpToolMapper mcpToolMapper,
            McpServerMapper mcpServerMapper,
            ProviderMapper providerMapper,
            ProviderService providerService,
            LlmHttpClient llmHttpClient
    ) {
        this.agentMapper = agentMapper;
        this.agentKnowledgeMapper = agentKnowledgeMapper;
        this.agentMcpToolMapper = agentMcpToolMapper;
        this.mcpToolMapper = mcpToolMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.providerMapper = providerMapper;
        this.providerService = providerService;
        this.llmHttpClient = llmHttpClient;
    }

    @Override
    public PageResult<AgentResponse> list(Integer page, Integer pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<Agent> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId())
                .orderByDesc(Agent::getId);

        IPage<Agent> result = agentMapper.selectPage(pageObj, wrapper);

        batchLoadKnowledgeIds(result.getRecords());
        batchLoadMcpToolIds(result.getRecords());

        List<AgentResponse> responses = result.getRecords().stream()
                .map(this::toBasicResponse)
                .collect(Collectors.toList());

        return PageResult.of(responses, result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<AgentResponse> list(Integer page, Integer pageSize, String name, Integer enabled) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<Agent> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId())
                .orderByDesc(Agent::getId);

        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Agent::getName, name.trim());
        }
        if (enabled != null) {
            wrapper.eq(Agent::getEnabled, enabled);
        }

        IPage<Agent> result = agentMapper.selectPage(pageObj, wrapper);

        batchLoadKnowledgeIds(result.getRecords());
        batchLoadMcpToolIds(result.getRecords());

        List<AgentResponse> responses = result.getRecords().stream()
                .map(this::toBasicResponse)
                .collect(Collectors.toList());

        return PageResult.of(responses, result.getTotal(), page, pageSize);
    }

    @Override
    @Cacheable(value = "agent-cache", key = "#id")
    public AgentResponse getById(Long id) {
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getId, id)
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId()));
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }

        return toFullResponse(agent);
    }

    @Override
    public Agent getEntityById(Long id) {
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getId, id)
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId()));
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        List<Long> knowledgeIds = agentKnowledgeMapper.selectKnowledgeIdsByAgentId(id);
        agent.setKnowledgeIds(knowledgeIds);
        List<Long> mcpToolIds = agentMcpToolMapper.selectToolIdsByAgentId(id, CurrentContext.getWorkspaceId());
        agent.setMcpToolIds(mcpToolIds);
        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "agent-cache", allEntries = true)
    public Agent create(AgentCreateRequest request) {
        WorkspaceGuard.checkNameUnique(agentMapper,
                Agent::getName, Agent::getWorkspaceId, Agent::getId,
                request.getName(), null, ErrorCode.PARAM_ERROR, "Agent 名称已存在");

        Provider provider = providerMapper.selectById(request.getDefaultProviderId());
        if (provider == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "指定的供应商不存在");
        }

        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setAvatar(request.getAvatar());
        agent.setDefaultProviderId(request.getDefaultProviderId());
        agent.setDefaultModel(request.getDefaultModel());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setUserMessagePrefix(request.getUserMessagePrefix());
        agent.setWelcomeMessage(request.getWelcomeMessage());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setTopP(request.getTopP());
        agent.setFrequencyPenalty(request.getFrequencyPenalty());
        agent.setPresencePenalty(request.getPresencePenalty());
        agent.setMaxHistoryRounds(request.getMaxHistoryRounds());
        agent.setStreamEnabled(request.getStreamEnabled());

        if (request.getAgentConfig() != null) {
            agent.setAgentConfig(convertToJsonNode(request.getAgentConfig()));
        }

        agent.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        agent.setRagEnabled(request.getRagEnabled() != null ? request.getRagEnabled() : 0);
        agent.setRagTopK(request.getRagTopK() != null ? request.getRagTopK() : 5);
        agent.setRagStrategy(request.getRagStrategy() != null ? request.getRagStrategy() : "hybrid");
        WorkspaceGuard.bind(agent);

        agentMapper.insert(agent);

        // 保存知识库关联（使用 upsert 避免重复键冲突）
        if (request.getKnowledgeIds() != null && !request.getKnowledgeIds().isEmpty()) {
            agentKnowledgeMapper.upsertKnowledgeIds(agent.getId(), request.getKnowledgeIds());
        }
        agent.setKnowledgeIds(request.getKnowledgeIds());

        // 保存 MCP 工具关联
        if (request.getMcpToolIds() != null && !request.getMcpToolIds().isEmpty()) {
            validateAndInsertMcpTools(agent.getId(), request.getMcpToolIds());
        }
        agent.setMcpToolIds(request.getMcpToolIds());

        log.info("创建 Agent 成功，id: {}, name: {}", agent.getId(), agent.getName());

        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "agent-cache", key = "#id")
    public Agent update(Long id, AgentUpdateRequest request) {
        Agent existing = WorkspaceGuard.requireInWorkspace(
                agentMapper.selectById(id), ErrorCode.NOT_FOUND);

        if (request.getName() != null && !request.getName().equals(existing.getName())) {
            WorkspaceGuard.checkNameUnique(agentMapper,
                    Agent::getName, Agent::getWorkspaceId, Agent::getId,
                    request.getName(), id, ErrorCode.PARAM_ERROR, "Agent 名称已存在");
            existing.setName(request.getName());
        }

        // 更新字段
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getAvatar() != null) {
            existing.setAvatar(request.getAvatar());
        }
        if (request.getDefaultProviderId() != null) {
            // 验证 Provider 是否存在
            Provider provider = providerMapper.selectById(request.getDefaultProviderId());
            if (provider == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "指定的供应商不存在");
            }
            existing.setDefaultProviderId(request.getDefaultProviderId());
        }
        if (request.getDefaultModel() != null) {
            existing.setDefaultModel(request.getDefaultModel());
        }
        if (request.getSystemPrompt() != null) {
            existing.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getUserMessagePrefix() != null) {
            existing.setUserMessagePrefix(request.getUserMessagePrefix());
        }
        if (request.getWelcomeMessage() != null) {
            existing.setWelcomeMessage(request.getWelcomeMessage());
        }
        if (request.getTemperature() != null) {
            existing.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            existing.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            existing.setTopP(request.getTopP());
        }
        if (request.getFrequencyPenalty() != null) {
            existing.setFrequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            existing.setPresencePenalty(request.getPresencePenalty());
        }
        if (request.getMaxHistoryRounds() != null) {
            existing.setMaxHistoryRounds(request.getMaxHistoryRounds());
        }
        if (request.getStreamEnabled() != null) {
            existing.setStreamEnabled(request.getStreamEnabled());
        }
        if (request.getAgentConfig() != null) {
            existing.setAgentConfig(convertToJsonNode(request.getAgentConfig()));
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        if (request.getRagEnabled() != null) {
            existing.setRagEnabled(request.getRagEnabled());
        }
        if (request.getRagTopK() != null) {
            existing.setRagTopK(request.getRagTopK());
        }
        if (request.getRagStrategy() != null) {
            existing.setRagStrategy(request.getRagStrategy());
        }

        agentMapper.updateById(existing);

        // 更新知识库关联（差量更新：upsert + 删除多余）
        if (request.getKnowledgeIds() != null) {
            if (!request.getKnowledgeIds().isEmpty()) {
                agentKnowledgeMapper.upsertKnowledgeIds(id, request.getKnowledgeIds());
                agentKnowledgeMapper.softDeleteExcept(id, request.getKnowledgeIds());
            } else {
                agentKnowledgeMapper.softDeleteByAgentId(id);
            }
            existing.setKnowledgeIds(request.getKnowledgeIds());
        }

        // 更新 MCP 工具关联（全量替换）
        if (request.getMcpToolIds() != null) {
            agentMcpToolMapper.deleteByAgentId(id, CurrentContext.getWorkspaceId());
            if (!request.getMcpToolIds().isEmpty()) {
                validateAndInsertMcpTools(id, request.getMcpToolIds());
            }
            existing.setMcpToolIds(request.getMcpToolIds());
        }

        log.info("更新 Agent 成功，id: {}, name: {}", id, existing.getName());

        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "agent-cache", key = "#id")
    public void delete(Long id) {
        Agent existing = WorkspaceGuard.requireInWorkspace(
                agentMapper.selectById(id), ErrorCode.NOT_FOUND);

        agentMapper.deleteById(id);
        agentKnowledgeMapper.softDeleteByAgentId(id);
        agentMcpToolMapper.deleteByAgentId(id, CurrentContext.getWorkspaceId());
        log.info("删除 Agent 成功，id: {}, name: {}", id, existing.getName());
    }

    @Override
    public AgentTestChatResponse testChat(Long id, AgentTestChatRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 获取 Agent 配置（校验工作空间归属）
            Agent agent = WorkspaceGuard.requireInWorkspace(
                    agentMapper.selectById(id), ErrorCode.NOT_FOUND);

            // 确定使用的 Provider 和 Model
            Long providerId = request.getOverrideProviderId() != null
                ? request.getOverrideProviderId()
                : agent.getDefaultProviderId();
            String model = request.getOverrideModel() != null
                ? request.getOverrideModel()
                : agent.getDefaultModel();

            // 获取 Provider（通过 ProviderService 获取，会自动解密 API Key）
            Provider provider = providerService.getEntityById(providerId);

            // 构建请求体
            String requestBody = buildChatRequest(agent, request, model);

            // 构建请求头
            Map<String, String> headers = buildHeaders(provider);

            // 构建 API URL
            String apiUrl = buildChatUrl(provider.getBaseUrl());

            // 发送请求
            String responseBody = llmHttpClient.post(apiUrl, headers, requestBody);

            long latencyMs = System.currentTimeMillis() - startTime;

            // 解析响应
            return parseChatResponse(responseBody, latencyMs, providerId, model);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("[Agent] 测试对话失败 - Agent ID: {}, 延迟: {}ms, 错误: {}",
                    id, latencyMs, e.getMessage(), e);

            // 获取详细错误信息
            String errorMsg = e.getMessage();
            if (e instanceof com.eify.common.http.LlmApiException) {
                com.eify.common.http.LlmApiException llmEx = (com.eify.common.http.LlmApiException) e;
                errorMsg = llmEx.getFullMessage();
            }

            return AgentTestChatResponse.builder()
                    .success(false)
                    .errorMessage(errorMsg)
                    .build();
        }
    }

    /**
     * 构建聊天请求体
     */
    private String buildChatRequest(Agent agent, AgentTestChatRequest request, String model) throws Exception {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", model);

        // 构建消息数组
        tools.jackson.databind.node.ArrayNode messages = rootNode.putArray("messages");

        // 添加系统消息（系统提示词）
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", agent.getSystemPrompt());

        // 添加用户消息（可选前缀 + 实际消息）
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        String fullUserMessage = agent.getUserMessagePrefix() != null
            ? agent.getUserMessagePrefix() + request.getMessage()
            : request.getMessage();
        userMessage.put("content", fullUserMessage);

        // 添加参数
        if (agent.getTemperature() != null) {
            rootNode.put("temperature", agent.getTemperature());
        }
        if (agent.getMaxTokens() != null) {
            rootNode.put("max_tokens", agent.getMaxTokens());
        }
        if (agent.getTopP() != null) {
            rootNode.put("top_p", agent.getTopP());
        }
        if (agent.getFrequencyPenalty() != null && agent.getFrequencyPenalty().compareTo(BigDecimal.ZERO) != 0) {
            rootNode.put("frequency_penalty", agent.getFrequencyPenalty());
        }
        if (agent.getPresencePenalty() != null && agent.getPresencePenalty().compareTo(BigDecimal.ZERO) != 0) {
            rootNode.put("presence_penalty", agent.getPresencePenalty());
        }

        return objectMapper.writeValueAsString(rootNode);
    }

    /**
     * 构建请求头
     */
    private Map<String, String> buildHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String apiKey = extractApiKey(provider);
        log.info("[Agent] 构建 API 请求头 - Provider: {}, API Key present: {}, Key length: {}",
                provider.getName(), apiKey != null, apiKey != null ? apiKey.length() : 0);
        if (apiKey != null) {
            // 根据 Provider 类型添加认证头
            if (provider.getType().toString().contains("ANTHROPIC")) {
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
            } else {
                headers.put("Authorization", "Bearer " + apiKey);
            }
        }

        return headers;
    }

    /**
     * 构建 API URL
     */
    private String buildChatUrl(String baseUrl) {
        // 去除尾部斜杠
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        // 追加 /v1（如果不存在）
        if (!normalized.endsWith("/v1")) {
            normalized += "/v1";
        }
        return normalized + "/chat/completions";
    }

    /**
     * 从 Provider 中提取 API Key
     */
    private String extractApiKey(Provider provider) {
        log.info("[Agent] 提取 API Key - Provider: {}, authConfig type: {}, authConfig value: {}",
                provider.getName(),
                provider.getAuthConfig() != null ? provider.getAuthConfig().getClass().getName() : "null",
                provider.getAuthConfig());

        if (provider.getAuthConfig() == null) {
            return null;
        }
        JsonNode authConfig = provider.getAuthConfig();
        log.info("[Agent] authConfig is JsonNode: {}, has api_key: {}, has apiKey: {}",
                authConfig instanceof JsonNode,
                authConfig.has("api_key"),
                authConfig.has("apiKey"));

        if (authConfig.has("api_key")) {
            return authConfig.get("api_key").asText();
        } else if (authConfig.has("apiKey")) {
            return authConfig.get("apiKey").asText();
        }
        return null;
    }

    /**
     * 解析聊天响应
     */
    private AgentTestChatResponse parseChatResponse(String responseBody, long latencyMs, Long providerId, String model) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // 提取回复内容
        String reply = root.path("choices").get(0).path("message").path("content").asText("无法获取回复");

        // 提取 token 统计
        JsonNode usage = root.path("usage");
        AgentTestChatResponse.TokenStats tokenStats = AgentTestChatResponse.TokenStats.builder()
                .promptTokens(usage.path("prompt_tokens").asInt(0))
                .completionTokens(usage.path("completion_tokens").asInt(0))
                .totalTokens(usage.path("total_tokens").asInt(0))
                .build();

        // 构建性能指标
        AgentTestChatResponse.PerformanceMetrics performance = AgentTestChatResponse.PerformanceMetrics.builder()
                .latencyMs(latencyMs)
                .actualProviderId(providerId)
                .actualModel(model)
                .build();

        return AgentTestChatResponse.builder()
                .reply(reply)
                .tokens(tokenStats)
                .performance(performance)
                .success(true)
                .build();
    }

    /**
     * 转换为基本响应对象
     */
    private AgentResponse toBasicResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .avatar(agent.getAvatar())
                .defaultProviderId(agent.getDefaultProviderId())
                .defaultModel(agent.getDefaultModel())
                .systemPrompt(agent.getSystemPrompt())
                .userMessagePrefix(agent.getUserMessagePrefix())
                .welcomeMessage(agent.getWelcomeMessage())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP())
                .frequencyPenalty(agent.getFrequencyPenalty())
                .presencePenalty(agent.getPresencePenalty())
                .maxHistoryRounds(agent.getMaxHistoryRounds())
                .streamEnabled(agent.getStreamEnabled())
                .agentConfig(agent.getAgentConfig())
                .enabled(agent.getEnabled())
                .createdAt(agent.getCreatedAt().toString())
                .updatedAt(agent.getUpdatedAt().toString())
                .creatorId(agent.getCreatorId())
                .knowledgeIds(agent.getKnowledgeIds())
                .mcpToolIds(agent.getMcpToolIds())
                .ragEnabled(agent.getRagEnabled())
                .ragTopK(agent.getRagTopK())
                .ragStrategy(agent.getRagStrategy())
                .build();
    }

    /**
     * 转换为完整响应对象（含关联数据）
     */
    private AgentResponse toFullResponse(Agent agent) {
        // 查询关联的 Provider
        Provider provider = providerMapper.selectById(agent.getDefaultProviderId());

        // 加载知识库 IDs
        List<Long> knowledgeIds = agentKnowledgeMapper.selectKnowledgeIdsByAgentId(agent.getId());
        agent.setKnowledgeIds(knowledgeIds);

        // 加载 MCP 工具 IDs 及简要信息
        List<Long> mcpToolIds = agentMcpToolMapper.selectToolIdsByAgentId(agent.getId(), CurrentContext.getWorkspaceId());
        agent.setMcpToolIds(mcpToolIds);
        List<AgentResponse.McpToolBrief> mcpTools = loadMcpToolBriefs(mcpToolIds);

        AgentResponse.AgentResponseBuilder builder = AgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .avatar(agent.getAvatar())
                .defaultProviderId(agent.getDefaultProviderId())
                .defaultModel(agent.getDefaultModel())
                .systemPrompt(agent.getSystemPrompt())
                .userMessagePrefix(agent.getUserMessagePrefix())
                .welcomeMessage(agent.getWelcomeMessage())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP())
                .frequencyPenalty(agent.getFrequencyPenalty())
                .presencePenalty(agent.getPresencePenalty())
                .maxHistoryRounds(agent.getMaxHistoryRounds())
                .streamEnabled(agent.getStreamEnabled())
                .agentConfig(agent.getAgentConfig())
                .enabled(agent.getEnabled())
                .createdAt(agent.getCreatedAt().toString())
                .updatedAt(agent.getUpdatedAt().toString())
                .creatorId(agent.getCreatorId())
                .knowledgeIds(knowledgeIds)
                .mcpToolIds(mcpToolIds)
                .mcpTools(mcpTools)
                .ragEnabled(agent.getRagEnabled())
                .ragTopK(agent.getRagTopK())
                .ragStrategy(agent.getRagStrategy());

        // 添加 Provider 信息
        if (provider != null) {
            builder.defaultProvider(AgentResponse.ProviderInfo.builder()
                    .id(provider.getId())
                    .name(provider.getName())
                    .type(provider.getType().toString())
                    .baseUrl(provider.getBaseUrl())
                    .build());
        }

        return builder.build();
    }

    /**
     * 批量加载 Agent 的知识库 IDs
     */
    private void batchLoadKnowledgeIds(List<Agent> agents) {
        if (agents == null || agents.isEmpty()) return;
        List<Long> agentIds = agents.stream().map(Agent::getId).collect(Collectors.toList());
        List<AgentKnowledge> mappings = agentKnowledgeMapper.selectByAgentIds(agentIds);
        Map<Long, List<Long>> grouped = mappings.stream()
                .collect(Collectors.groupingBy(
                        AgentKnowledge::getAgentId,
                        Collectors.mapping(AgentKnowledge::getKnowledgeId, Collectors.toList())
                ));
        agents.forEach(a -> a.setKnowledgeIds(grouped.getOrDefault(a.getId(), Collections.emptyList())));
    }

    /**
     * 批量加载 Agent 的 MCP 工具 IDs
     */
    private void batchLoadMcpToolIds(List<Agent> agents) {
        if (agents == null || agents.isEmpty()) return;
        List<Long> agentIds = agents.stream().map(Agent::getId).collect(Collectors.toList());
        List<AgentMcpTool> mappings = agentMcpToolMapper.selectByAgentIds(agentIds, CurrentContext.getWorkspaceId());
        Map<Long, List<Long>> grouped = mappings.stream()
                .collect(Collectors.groupingBy(
                        AgentMcpTool::getAgentId,
                        Collectors.mapping(AgentMcpTool::getToolId, Collectors.toList())
                ));
        agents.forEach(a -> a.setMcpToolIds(grouped.getOrDefault(a.getId(), Collections.emptyList())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "agent-cache", key = "#id")
    public void bindTools(Long id, BindToolsRequest request) {
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getId, id)
                .eq(Agent::getWorkspaceId, CurrentContext.getWorkspaceId()));
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }

        List<Long> toolIds = request.getToolIds();
        if (toolIds == null) {
            toolIds = List.of();
        }

        // 全量替换：先删后插
        agentMcpToolMapper.deleteByAgentId(id, CurrentContext.getWorkspaceId());
        if (!toolIds.isEmpty()) {
            validateAndInsertMcpTools(id, toolIds);
        }

        log.info("绑定 MCP 工具成功: agentId={}, toolIds={}", id, toolIds);
    }

    /**
     * 校验工具 ID 列表并批量插入。
     * 校验规则：每个 toolId 必须存在、对应 Server 启用状态、总数不超过 10。
     */
    private void validateAndInsertMcpTools(Long agentId, List<Long> toolIds) {
        if (toolIds.size() > 10) {
            throw new BusinessException(ErrorCode.MCP_TOOL_LIMIT_EXCEEDED);
        }

        for (Long toolId : toolIds) {
            McpTool tool = WorkspaceGuard.requireInWorkspace(
                    mcpToolMapper.selectById(toolId), ErrorCode.MCP_TOOL_NOT_FOUND);
            McpServer server = WorkspaceGuard.requireInWorkspace(
                    mcpServerMapper.selectById(tool.getServerId()), ErrorCode.MCP_SERVER_OFFLINE);
            if (server.getEnabled() == null || server.getEnabled() != 1) {
                throw new BusinessException(ErrorCode.MCP_SERVER_OFFLINE);
            }
        }

        agentMcpToolMapper.batchInsert(agentId, toolIds, CurrentContext.getWorkspaceId());
    }

    /**
     * 加载 MCP 工具简要信息（含 Server 名称）
     */
    private List<AgentResponse.McpToolBrief> loadMcpToolBriefs(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return List.of();

        List<McpTool> tools = mcpToolMapper.selectBatchIds(toolIds);
        if (tools.isEmpty()) return List.of();

        Set<Long> serverIds = tools.stream()
                .map(McpTool::getServerId)
                .collect(Collectors.toSet());
        Map<Long, String> serverNameMap = mcpServerMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(McpServer::getId, McpServer::getName));

        return tools.stream()
                .map(tool -> AgentResponse.McpToolBrief.builder()
                        .id(tool.getId())
                        .name(tool.getName())
                        .serverName(serverNameMap.get(tool.getServerId()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 批量插入知识库关联
     */
    private void batchInsertKnowledge(Long agentId, List<Long> knowledgeIds) {
        for (Long kbId : knowledgeIds) {
            AgentKnowledge ak = new AgentKnowledge();
            ak.setAgentId(agentId);
            ak.setKnowledgeId(kbId);
            agentKnowledgeMapper.insert(ak);
        }
    }

    /**
     * 将 Map 转换为 JsonNode
     */
    private JsonNode convertToJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        return objectMapper.valueToTree(obj);
    }
}
