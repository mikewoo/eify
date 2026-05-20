package com.eify.workflow.engine.executor;

import tools.jackson.databind.ObjectMapper;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StartNodeExecutor")
class StartNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    StartNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new StartNodeExecutor();
    }

    private ExecutionContext buildContext(Map<String, Object> variables) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);

        WorkflowNode startNode = new WorkflowNode();
        startNode.setId(1L);
        startNode.setNodeKey("start");
        startNode.setType("start");
        startNode.setLabel("开始");

        ExecutionContext ctx = new ExecutionContext(100L, workflow,
                List.of(startNode), Collections.emptyList());
        variables.forEach(ctx::setVariable);
        return ctx;
    }

    private WorkflowNode buildStartNode(Map<String, Object> configMap) throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId(1L);
        node.setNodeKey("start_1");
        node.setType("start");
        node.setLabel("开始");
        node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        return node;
    }

    @Nested
    @DisplayName("getType")
    class GetTypeTests {

        @Test
        @DisplayName("应返回 start")
        void shouldReturnStart() {
            assertThat(executor.getType()).isEqualTo("start");
        }
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("config 为 null 时应返回空 outputs")
        void shouldReturnEmptyOutputsWhenConfigNull() {
            WorkflowNode node = new WorkflowNode();
            node.setId(1L);
            node.setType("start");
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).isEmpty();
        }

        @Test
        @DisplayName("inputVariables 为空时应返回空 outputs")
        void shouldReturnEmptyOutputsWhenNoVariables() throws Exception {
            WorkflowNode node = buildStartNode(Map.of("inputVariables", List.of()));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).isEmpty();
        }

        @Test
        @DisplayName("应读取上下文中已有变量")
        void shouldReadExistingVariables() throws Exception {
            Map<String, Object> configMap = Map.of("inputVariables", List.of(
                    Map.of("key", "user_input", "varType", "string", "required", true, "defaultVal", "")
            ));
            WorkflowNode node = buildStartNode(configMap);
            ExecutionContext ctx = buildContext(Map.of("user_input", "hello"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsEntry("user_input", "hello");
        }

        @Test
        @DisplayName("变量不存在时应使用默认值")
        void shouldUseDefaultWhenVariableMissing() throws Exception {
            Map<String, Object> configMap = Map.of("inputVariables", List.of(
                    Map.of("key", "name", "varType", "string", "required", false, "defaultVal", "anonymous")
            ));
            WorkflowNode node = buildStartNode(configMap);
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsEntry("name", "anonymous");
        }
    }
}
