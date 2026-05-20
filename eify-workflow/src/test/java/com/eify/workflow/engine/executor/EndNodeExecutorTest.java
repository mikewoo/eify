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

@DisplayName("EndNodeExecutor")
class EndNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    EndNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EndNodeExecutor();
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

    private WorkflowNode buildEndNode(Map<String, Object> configMap) throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId(2L);
        node.setNodeKey("end_1");
        node.setType("end");
        node.setLabel("结束");
        node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        return node;
    }

    @Nested
    @DisplayName("getType")
    class GetTypeTests {

        @Test
        @DisplayName("应返回 end")
        void shouldReturnEnd() {
            assertThat(executor.getType()).isEqualTo("end");
        }
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("config 为 null 时应返回空 outputs")
        void shouldReturnEmptyOutputsWhenConfigNull() {
            WorkflowNode node = new WorkflowNode();
            node.setId(2L);
            node.setType("end");
            ExecutionContext ctx = buildContext(Map.of("key", "value"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).isEmpty();
        }

        @Test
        @DisplayName("outputKey 为空时应返回空 outputs")
        void shouldReturnEmptyOutputsWhenNoOutputKey() throws Exception {
            WorkflowNode node = buildEndNode(Map.of("outputKey", ""));
            ExecutionContext ctx = buildContext(Map.of("key", "value"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).isEmpty();
        }

        @Test
        @DisplayName("outputKey 非空时应返回变量快照")
        void shouldReturnSnapshotWhenOutputKeyPresent() throws Exception {
            WorkflowNode node = buildEndNode(Map.of("outputKey", "result"));
            ExecutionContext ctx = buildContext(Map.of("key", "value", "count", 42));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsKey("result");
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) result.getOutputs().get("result");
            assertThat(snapshot).containsEntry("count", 42);
        }
    }
}
