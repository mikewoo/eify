package com.eify.workflow.engine.executor;

import tools.jackson.databind.ObjectMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.service.ProviderService;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmNodeExecutor")
class LlmNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    ProviderService providerService;

    @Mock
    ProviderAdapterFactory adapterFactory;

    @Mock
    ProviderAdapter adapter;

    LlmNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LlmNodeExecutor(providerService, adapterFactory);
    }

    private ExecutionContext buildContext() {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);
        WorkflowNode startNode = new WorkflowNode();
        startNode.setId(1L);
        startNode.setNodeKey("start");
        startNode.setType("start");
        startNode.setLabel("开始");
        return new ExecutionContext(100L, workflow,
                List.of(startNode), Collections.emptyList());
    }

    private WorkflowNode buildLlmNode(Map<String, Object> configMap) throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId(2L);
        node.setNodeKey("llm_1");
        node.setType("llm");
        node.setLabel("LLM");
        node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        return node;
    }

    @Nested
    @DisplayName("getType")
    class GetTypeTests {

        @Test
        @DisplayName("应返回 llm")
        void shouldReturnLlm() {
            assertThat(executor.getType()).isEqualTo("llm");
        }
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("config 为 null 时应返回失败结果")
        void shouldReturnFailWhenConfigNull() {
            WorkflowNode node = new WorkflowNode();
            node.setId(2L);
            node.setType("llm");
            ExecutionContext ctx = buildContext();

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("配置为空");
        }

        @Test
        @DisplayName("providerId 为 null 时应抛出 WORKFLOW_CONFIG_INVALID")
        void shouldThrowWhenProviderIdNull() throws Exception {
            WorkflowNode node = buildLlmNode(Map.of(
                    "model", "gpt-4",
                    "systemPrompt", "You are helpful",
                    "userPrompt", "Hello",
                    "outputKey", "response"
            ));
            ExecutionContext ctx = buildContext();

            assertThatThrownBy(() -> executor.execute(node, ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.WORKFLOW_CONFIG_INVALID.getCode());
        }

        @Test
        @DisplayName("LLM 调用成功应返回内容")
        void shouldReturnContentOnSuccess() throws Exception {
            WorkflowNode node = buildLlmNode(Map.of(
                    "model", "gpt-4",
                    "systemPrompt", "You are helpful",
                    "userPrompt", "Hello",
                    "outputKey", "response",
                    "providerId", 1
            ));
            ExecutionContext ctx = buildContext();
            Provider provider = new Provider();
            provider.setType(ProviderType.OPENAI);
            when(providerService.getEntityById(1L)).thenReturn(provider);
            when(adapterFactory.getAdapter(ProviderType.OPENAI)).thenReturn(adapter);
            ChatResponse response = new ChatResponse();
            response.setContent("Hello! How can I help you?");
            when(adapter.chat(eq(provider), any())).thenReturn(response);

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsKey("response");
            assertThat(result.getOutputs().get("response")).isEqualTo("Hello! How can I help you?");
        }

        @Test
        @DisplayName("LLM 调用异常应返回失败结果")
        void shouldReturnFailOnException() throws Exception {
            WorkflowNode node = buildLlmNode(Map.of(
                    "model", "gpt-4",
                    "systemPrompt", "You are helpful",
                    "userPrompt", "Hello",
                    "outputKey", "response",
                    "providerId", 1
            ));
            ExecutionContext ctx = buildContext();
            Provider provider = new Provider();
            provider.setType(ProviderType.OPENAI);
            when(providerService.getEntityById(1L)).thenReturn(provider);
            when(adapterFactory.getAdapter(ProviderType.OPENAI)).thenReturn(adapter);
            when(adapter.chat(eq(provider), any())).thenThrow(new RuntimeException("API error"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("API error");
        }
    }
}
