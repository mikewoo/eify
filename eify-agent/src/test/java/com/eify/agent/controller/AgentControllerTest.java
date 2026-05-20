package com.eify.agent.controller;

import com.eify.agent.domain.dto.*;
import com.eify.agent.domain.entity.Agent;
import com.eify.agent.service.AgentService;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentController")
class AgentControllerTest {

    @Mock
    AgentService agentService;

    AgentController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentController(agentService);
    }

    private Agent buildAgent(Long id, String name) {
        Agent a = new Agent();
        a.setId(id);
        a.setName(name);
        a.setDescription("desc");
        a.setAvatar("avatar.png");
        a.setDefaultProviderId(10L);
        a.setDefaultModel("gpt-4");
        a.setSystemPrompt("system prompt");
        a.setUserMessagePrefix("prefix");
        a.setWelcomeMessage("welcome");
        a.setTemperature(new BigDecimal("0.7"));
        a.setMaxTokens(2048);
        a.setTopP(new BigDecimal("0.9"));
        a.setFrequencyPenalty(new BigDecimal("0.1"));
        a.setPresencePenalty(new BigDecimal("0.1"));
        a.setMaxHistoryRounds(10);
        a.setStreamEnabled(1);
        a.setEnabled(1);
        a.setKnowledgeIds(List.of(1L, 2L));
        a.setMcpToolIds(List.of(3L, 4L));
        a.setRagEnabled(1);
        a.setRagTopK(5);
        a.setRagStrategy("hybrid");
        a.setCreatedAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        a.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 12, 0));
        a.setCreatorId(100L);
        return a;
    }

    // ==================== list ====================

    @Nested
    @DisplayName("GET /api/v1/agents")
    class ListAgents {

        @Test
        @DisplayName("分页查询并包装 Result")
        void shouldListAndWrapResult() {
            PageResult<AgentResponse> pageResult = PageResult.empty(1, 20);
            when(agentService.list(1, 20, null, null)).thenReturn(pageResult);

            Result<PageResult<AgentResponse>> result = controller.list(1, 20, null, null);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(pageResult);
        }

        @Test
        @DisplayName("带筛选条件查询")
        void shouldListWithFilters() {
            PageResult<AgentResponse> pageResult = PageResult.empty(1, 20);
            when(agentService.list(2, 10, "test", 1)).thenReturn(pageResult);

            Result<PageResult<AgentResponse>> result = controller.list(2, 10, "test", 1);

            assertThat(result.getCode()).isEqualTo(200);
            verify(agentService).list(2, 10, "test", 1);
        }
    }

    // ==================== getById ====================

    @Nested
    @DisplayName("GET /api/v1/agents/{id}")
    class GetById {

        @Test
        @DisplayName("返回 Agent 详情")
        void shouldReturnAgentDetail() {
            AgentResponse response = AgentResponse.builder().id(1L).name("test").build();
            when(agentService.getById(1L)).thenReturn(response);

            Result<AgentResponse> result = controller.getById(1L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(response);
        }
    }

    // ==================== create ====================

    @Nested
    @DisplayName("POST /api/v1/agents")
    class Create {

        @Test
        @DisplayName("创建成功并返回映射后的响应")
        void shouldCreateAndReturnMappedResponse() {
            AgentCreateRequest request = new AgentCreateRequest();
            request.setName("new-agent");
            Agent entity = buildAgent(1L, "new-agent");
            when(agentService.create(request)).thenReturn(entity);

            Result<AgentResponse> result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getId()).isEqualTo(1L);
            assertThat(result.getData().getName()).isEqualTo("new-agent");
            assertThat(result.getData().getCreatedAt()).isEqualTo("2026-05-15T10:00");
        }
    }

    // ==================== update ====================

    @Nested
    @DisplayName("PUT /api/v1/agents/{id}")
    class Update {

        @Test
        @DisplayName("更新成功并返回映射后的响应")
        void shouldUpdateAndReturnMappedResponse() {
            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("updated-agent");
            Agent entity = buildAgent(2L, "updated-agent");
            when(agentService.update(2L, request)).thenReturn(entity);

            Result<AgentResponse> result = controller.update(2L, request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getId()).isEqualTo(2L);
            assertThat(result.getData().getName()).isEqualTo("updated-agent");
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("DELETE /api/v1/agents/{id}")
    class Delete {

        @Test
        @DisplayName("删除成功返回 Void Result")
        void shouldDeleteAndReturnVoid() {
            Result<Void> result = controller.delete(1L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(agentService).delete(1L);
        }
    }

    // ==================== bindTools ====================

    @Nested
    @DisplayName("PUT /api/v1/agents/{id}/tools")
    class BindTools {

        @Test
        @DisplayName("绑定工具成功返回 Void Result")
        void shouldBindToolsAndReturnVoid() {
            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(1L, 2L));

            Result<Void> result = controller.bindTools(5L, request);

            assertThat(result.getCode()).isEqualTo(200);
            verify(agentService).bindTools(5L, request);
        }
    }

    // ==================== testChat ====================

    @Nested
    @DisplayName("POST /api/v1/agents/{id}/test-chat")
    class TestChat {

        @Test
        @DisplayName("测试对话成功返回响应")
        void shouldTestChatAndReturnResponse() {
            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("hello");
            AgentTestChatResponse chatResponse = AgentTestChatResponse.builder()
                    .reply("hi there")
                    .success(true)
                    .build();
            when(agentService.testChat(1L, request)).thenReturn(chatResponse);

            Result<AgentTestChatResponse> result = controller.testChat(1L, request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getReply()).isEqualTo("hi there");
        }
    }

    // ==================== toResponse mapping ====================

    @Nested
    @DisplayName("toResponse mapping")
    class ToResponseMapping {

        @Test
        @DisplayName("完整字段映射正确")
        void shouldMapAllFields() {
            Agent entity = buildAgent(1L, "agent-name");

            // toResponse is private, tested via create/update
            AgentCreateRequest request = new AgentCreateRequest();
            request.setName("agent-name");
            when(agentService.create(request)).thenReturn(entity);

            Result<AgentResponse> result = controller.create(request);
            AgentResponse r = result.getData();

            assertThat(r.getId()).isEqualTo(1L);
            assertThat(r.getName()).isEqualTo("agent-name");
            assertThat(r.getDescription()).isEqualTo("desc");
            assertThat(r.getAvatar()).isEqualTo("avatar.png");
            assertThat(r.getDefaultProviderId()).isEqualTo(10L);
            assertThat(r.getDefaultModel()).isEqualTo("gpt-4");
            assertThat(r.getSystemPrompt()).isEqualTo("system prompt");
            assertThat(r.getUserMessagePrefix()).isEqualTo("prefix");
            assertThat(r.getWelcomeMessage()).isEqualTo("welcome");
            assertThat(r.getTemperature()).isEqualByComparingTo("0.7");
            assertThat(r.getMaxTokens()).isEqualTo(2048);
            assertThat(r.getTopP()).isEqualByComparingTo("0.9");
            assertThat(r.getFrequencyPenalty()).isEqualByComparingTo("0.1");
            assertThat(r.getPresencePenalty()).isEqualByComparingTo("0.1");
            assertThat(r.getMaxHistoryRounds()).isEqualTo(10);
            assertThat(r.getStreamEnabled()).isEqualTo(1);
            assertThat(r.getEnabled()).isEqualTo(1);
            assertThat(r.getKnowledgeIds()).containsExactly(1L, 2L);
            assertThat(r.getMcpToolIds()).containsExactly(3L, 4L);
            assertThat(r.getRagEnabled()).isEqualTo(1);
            assertThat(r.getRagTopK()).isEqualTo(5);
            assertThat(r.getRagStrategy()).isEqualTo("hybrid");
            assertThat(r.getCreatedAt()).isEqualTo("2026-05-15T10:00");
            assertThat(r.getUpdatedAt()).isEqualTo("2026-05-15T12:00");
            assertThat(r.getCreatorId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("createdAt 为 null 时映射为 null")
        void shouldMapNullCreatedAt() {
            Agent entity = buildAgent(1L, "test");
            entity.setCreatedAt(null);
            entity.setUpdatedAt(null);

            AgentCreateRequest request = new AgentCreateRequest();
            request.setName("test");
            when(agentService.create(request)).thenReturn(entity);

            Result<AgentResponse> result = controller.create(request);

            assertThat(result.getData().getCreatedAt()).isNull();
            assertThat(result.getData().getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("AgentConfig JsonNode 正确映射")
        void shouldMapAgentConfig() {
            Agent entity = buildAgent(1L, "test");
            AgentCreateRequest request = new AgentCreateRequest();
            request.setName("test");
            when(agentService.create(request)).thenReturn(entity);

            Result<AgentResponse> result = controller.create(request);

            // agentConfig is null in builder since we didn't set it in buildAgent
            assertThat(result.getData().getAgentConfig()).isNull();
        }
    }
}
