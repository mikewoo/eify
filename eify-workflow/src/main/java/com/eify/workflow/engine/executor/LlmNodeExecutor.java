package com.eify.workflow.engine.executor;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.domain.dto.ChatMessage;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.service.ProviderService;
import com.eify.workflow.domain.config.LlmNodeConfig;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final ProviderService providerService;
    private final ProviderAdapterFactory adapterFactory;

    @Override
    public String getType() {
        return "llm";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        LlmNodeConfig config = (LlmNodeConfig) NodeConfigParser.parse("llm", node.getConfig());
        if (config == null) {
            return NodeResult.fail("LLM 节点配置为空");
        }

        String systemPrompt = ctx.resolveTemplate(config.systemPrompt());
        String userPrompt = ctx.resolveTemplate(config.userPrompt());

        Provider provider = resolveProvider(config);
        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());

        ChatRequest request = ChatRequest.builder()
                .model(config.model())
                .temperature(config.temperature() != null ? config.temperature() : 0.7)
                .maxTokens(config.maxTokens() != null ? config.maxTokens() : 2000)
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)))
                .build();

        try {
            ChatResponse response = adapter.chat(provider, request);
            String content = response.getContent();
            log.info("LLM 节点响应: outputKey={}, contentLength={}, content={}",
                    config.outputKey(), content != null ? content.length() : 0,
                    content != null ? content.substring(0, Math.min(200, content.length())) : "null");
            Map<String, Object> outputs = new HashMap<>();
            outputs.put(config.outputKey(), content);
            if (response.getUsage() != null) {
                outputs.put("_usage", response.getUsage());
            }
            return NodeResult.ok(outputs);
        } catch (Exception e) {
            log.error("LLM 节点执行失败: nodeId={}, model={}", node.getId(), config.model(), e);
            return NodeResult.fail("LLM 调用失败: " + e.getMessage());
        }
    }

    private Provider resolveProvider(LlmNodeConfig config) {
        if (config.providerId() != null) {
            return providerService.getEntityById(config.providerId());
        }
        throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                "LLM 节点缺少 providerId，请在节点配置中指定");
    }
}
