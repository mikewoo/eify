package com.eify.workflow.engine;

import com.eify.common.exception.BusinessException;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionContext")
class ExecutionContextTest {

    private Workflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);
    }

    private WorkflowNode node(Long id, String nodeKey, String type) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setNodeKey(nodeKey);
        n.setType(type);
        n.setLabel(type + "节点");
        return n;
    }

    private WorkflowEdge edge(Long id, Long source, Long target, String handle) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setSourceNodeId(source);
        e.setTargetNodeId(target);
        e.setSourceHandle(handle);
        return e;
    }

    // ==================== 构造 ====================

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("从节点和边构造，自动定位 start 节点")
        void shouldConstructAndStartAtStartNode() {
            WorkflowNode start = node(1L, "start_1", "start");
            WorkflowNode end = node(2L, "end_1", "end");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, end), Collections.emptyList());

            assertThat(ctx.getCurrentNodeId()).isEqualTo(1L);
            assertThat(ctx.getCurrentNode().getType()).isEqualTo("start");
            assertThat(ctx.getStatus()).isEqualTo("running");
            assertThat(ctx.getExecutionId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("缺少 start 节点时抛出异常")
        void shouldThrowWhenNoStartNode() {
            WorkflowNode end = node(1L, "end_1", "end");

            assertThatThrownBy(() -> new ExecutionContext(100L, workflow,
                    List.of(end), Collections.emptyList()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缺少 start 节点");
        }

        @Test
        @DisplayName("多个 start 时使用第一个")
        void shouldUseFirstStartWhenMultiple() {
            WorkflowNode start1 = node(1L, "start_1", "start");
            WorkflowNode start2 = node(2L, "start_2", "start");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start1, start2), Collections.emptyList());

            assertThat(ctx.getCurrentNodeId()).isEqualTo(1L);
        }
    }

    // ==================== 节点导航 ====================

    @Nested
    @DisplayName("node navigation")
    class NodeNavigation {

        @Test
        @DisplayName("resolveNextNode 精确匹配 handle")
        void shouldExactMatchHandle() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode a = node(2L, "a", "llm");
            WorkflowNode b = node(3L, "b", "code");
            WorkflowEdge e1 = edge(1L, 1L, 2L, "true");
            WorkflowEdge e2 = edge(2L, 1L, 3L, "false");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, a, b), List.of(e1, e2));

            assertThat(ctx.resolveNextNode("true")).isEqualTo(2L);
            assertThat(ctx.resolveNextNode("false")).isEqualTo(3L);
        }

        @Test
        @DisplayName("无精确匹配时回退到 default handle")
        void shouldFallbackToDefaultHandle() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode target = node(2L, "target", "llm");
            WorkflowEdge e = edge(1L, 1L, 2L, "default");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, target), List.of(e));

            assertThat(ctx.resolveNextNode("unknown")).isEqualTo(2L);
        }

        @Test
        @DisplayName("无任何匹配时返回 null")
        void shouldReturnNullWhenNoMatch() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode target = node(2L, "target", "llm");
            WorkflowEdge e = edge(1L, 1L, 2L, "specific");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, target), List.of(e));

            assertThat(ctx.resolveNextNode("unknown")).isNull();
        }

        @Test
        @DisplayName("无出边时返回 null")
        void shouldReturnNullWhenNoOutgoingEdges() {
            WorkflowNode start = node(1L, "start", "start");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            assertThat(ctx.resolveNextNode("default")).isNull();
        }

        @Test
        @DisplayName("advanceTo 切换到目标节点")
        void shouldAdvanceToTargetNode() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode next = node(2L, "next", "llm");
            WorkflowEdge e = edge(1L, 1L, 2L, "default");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, next), List.of(e));

            ctx.advanceTo(2L);

            assertThat(ctx.getCurrentNodeId()).isEqualTo(2L);
            assertThat(ctx.getCurrentNode().getNodeKey()).isEqualTo("next");
        }
    }

    // ==================== 变量管理 ====================

    @Nested
    @DisplayName("variable management")
    class VariableManagement {

        @Test
        @DisplayName("setVariable 和 getVariable")
        void shouldSetAndGetVariable() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            ctx.setVariable("key", "value");
            assertThat(ctx.getVariable("key")).isEqualTo("value");
        }

        @Test
        @DisplayName("resolveTemplate 替换模板变量")
        void shouldResolveTemplate() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());
            ctx.setVariable("name", "World");

            assertThat(ctx.resolveTemplate("Hello, {{name}}!")).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("getVariableSnapshot 返回所有变量")
        void shouldReturnVariableSnapshot() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());
            ctx.setVariable("a", 1);
            ctx.setVariable("b", 2);

            var snapshot = ctx.getVariableSnapshot();

            assertThat(snapshot).containsEntry("a", 1).containsEntry("b", 2);
        }
    }

    // ==================== 状态管理 ====================

    @Nested
    @DisplayName("status management")
    class StatusManagement {

        @Test
        @DisplayName("初始状态为 running")
        void shouldStartAsRunning() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            assertThat(ctx.getStatus()).isEqualTo("running");
        }

        @Test
        @DisplayName("markCompleted 设置状态为 completed")
        void shouldMarkCompleted() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            ctx.markCompleted();

            assertThat(ctx.getStatus()).isEqualTo("completed");
            assertThat(ctx.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("markFailed 设置状态和错误消息")
        void shouldMarkFailedWithErrorMessage() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            ctx.markFailed("something went wrong");

            assertThat(ctx.getStatus()).isEqualTo("failed");
            assertThat(ctx.getErrorMessage()).isEqualTo("something went wrong");
        }
    }

    // ==================== getNode / lastResult ====================

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("getNode 按 ID 查找节点")
        void shouldFindNodeById() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode llm = node(2L, "llm", "llm");

            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start, llm), Collections.emptyList());

            assertThat(ctx.getNode(1L).getNodeKey()).isEqualTo("start");
            assertThat(ctx.getNode(2L).getNodeKey()).isEqualTo("llm");
            assertThat(ctx.getNode(999L)).isNull();
        }

        @Test
        @DisplayName("setLastResult / getLastResult")
        void shouldStoreAndRetrieveLastResult() {
            WorkflowNode start = node(1L, "start", "start");
            ExecutionContext ctx = new ExecutionContext(100L, workflow,
                    List.of(start), Collections.emptyList());

            NodeResult result = NodeResult.ok(java.util.Map.of("key", "value"));
            ctx.setLastResult(result);

            assertThat(ctx.getLastResult()).isSameAs(result);
        }

        @Test
        @DisplayName("hasNextNode 判断有无出边")
        void shouldDetermineIfHasNextNode() {
            WorkflowNode start = node(1L, "start", "start");
            WorkflowNode next = node(2L, "next", "llm");
            WorkflowEdge e = edge(1L, 1L, 2L, "default");

            ExecutionContext ctxWithEdge = new ExecutionContext(100L, workflow,
                    List.of(start, next), List.of(e));
            assertThat(ctxWithEdge.hasNextNode()).isTrue();

            ExecutionContext ctxWithoutEdge = new ExecutionContext(200L, workflow,
                    List.of(start), Collections.emptyList());
            assertThat(ctxWithoutEdge.hasNextNode()).isFalse();
        }
    }
}
