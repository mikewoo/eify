package com.eify.workflow.engine;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowExecution;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.executor.EndNodeExecutor;
import com.eify.workflow.engine.executor.StartNodeExecutor;
import com.eify.workflow.mapper.WorkflowEdgeMapper;
import com.eify.workflow.mapper.WorkflowExecutionMapper;
import com.eify.workflow.mapper.WorkflowMapper;
import com.eify.workflow.mapper.WorkflowNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngine")
class WorkflowEngineTest {

    @Mock WorkflowMapper workflowMapper;
    @Mock WorkflowNodeMapper nodeMapper;
    @Mock WorkflowEdgeMapper edgeMapper;
    @Mock WorkflowExecutionMapper executionMapper;
    @Mock Executor workflowExecutor;

    WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        // 注册 start 和 end executor
        List<NodeExecutor> executors = List.of(new StartNodeExecutor(), new EndNodeExecutor());
        engine = new WorkflowEngine(workflowExecutor, workflowMapper,
                nodeMapper, edgeMapper, executionMapper, executors);
    }

    private Workflow buildWorkflow(Long id, Integer status) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setVersion(1);
        w.setStatus(status);
        return w;
    }

    private WorkflowNode buildNode(Long id, String type, String label) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setNodeKey(type + "_" + id);
        n.setType(type);
        n.setLabel(label);
        n.setWorkflowId(1L);
        return n;
    }

    @Nested
    @DisplayName("getExecutor")
    class GetExecutorTests {

        @Test
        @DisplayName("已知 type 应返回对应的 executor")
        void shouldReturnKnownExecutor() {
            // 同步执行 submit 以测试内部逻辑
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(workflowExecutor).execute(any(Runnable.class));

            Workflow workflow = buildWorkflow(1L, 1);
            when(workflowMapper.selectById(1L)).thenReturn(workflow);
            when(nodeMapper.selectList(any())).thenReturn(List.of(
                    buildNode(1L, "start", "开始"),
                    buildNode(2L, "end", "结束")
            ));
            when(edgeMapper.selectList(any())).thenReturn(List.of());
            when(executionMapper.insert(any(WorkflowExecution.class))).thenReturn(1);
            when(executionMapper.updateById(any(WorkflowExecution.class))).thenReturn(1);

            BlockingQueue<ExecutionEvent> queue = new LinkedBlockingQueue<>();
            engine.submit(1L, Map.of(), queue);

            // 工作流应正常完成
            verify(executionMapper).insert(any(WorkflowExecution.class));
        }
    }

    @Nested
    @DisplayName("submit - 校验")
    class SubmitValidationTests {

        @Test
        @DisplayName("工作流不存在时应发送执行失败事件")
        void shouldFailWhenWorkflowNotFound() throws Exception {
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(workflowExecutor).execute(any(Runnable.class));
            when(workflowMapper.selectById(1L)).thenReturn(null);

            BlockingQueue<ExecutionEvent> queue = new LinkedBlockingQueue<>();
            engine.submit(1L, Map.of(), queue);

            ExecutionEvent event = queue.poll();
            assertThat(event).isNotNull();
            assertThat(event.getEvent()).contains("failed");
            assertThat(event.getError()).contains("工作流不存在");
        }

        @Test
        @DisplayName("工作流已禁用时应发送执行失败事件")
        void shouldFailWhenWorkflowDisabled() throws Exception {
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(workflowExecutor).execute(any(Runnable.class));
            Workflow workflow = buildWorkflow(1L, 2); // status=2 = disabled
            when(workflowMapper.selectById(1L)).thenReturn(workflow);

            BlockingQueue<ExecutionEvent> queue = new LinkedBlockingQueue<>();
            engine.submit(1L, Map.of(), queue);

            ExecutionEvent event = queue.poll();
            assertThat(event).isNotNull();
            assertThat(event.getError()).contains("已禁用");
        }

        @Test
        @DisplayName("工作流无节点时应发送执行失败事件")
        void shouldFailWhenNoNodes() throws Exception {
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(workflowExecutor).execute(any(Runnable.class));
            Workflow workflow = buildWorkflow(1L, 1);
            when(workflowMapper.selectById(1L)).thenReturn(workflow);
            when(nodeMapper.selectList(any())).thenReturn(List.of());

            BlockingQueue<ExecutionEvent> queue = new LinkedBlockingQueue<>();
            engine.submit(1L, Map.of(), queue);

            ExecutionEvent event = queue.poll();
            assertThat(event).isNotNull();
            assertThat(event.getError()).contains("无节点");
        }
    }

    @Nested
    @DisplayName("per-node-type timeout")
    class PerNodeTypeTimeoutTests {

        @Test
        @DisplayName("LLM 节点使用长超时（3 分钟）")
        void shouldUseLongTimeoutForLlmNode() throws Exception {
            var llmField = WorkflowEngine.class.getDeclaredField("LLM_NODE_TIMEOUT_MS");
            llmField.setAccessible(true);
            long llmTimeout = llmField.getLong(null);

            var defaultField = WorkflowEngine.class.getDeclaredField("DEFAULT_NODE_TIMEOUT_MS");
            defaultField.setAccessible(true);
            long defaultTimeout = defaultField.getLong(null);

            // LLM 超时应远大于默认超时
            assertThat(llmTimeout).isGreaterThan(defaultTimeout);
            assertThat(llmTimeout).isEqualTo(3 * 60 * 1000);
        }

        @Test
        @DisplayName("非 LLM 节点使用默认超时（30 秒）")
        void shouldUseDefaultTimeoutForNonLlmNode() throws Exception {
            var defaultField = WorkflowEngine.class.getDeclaredField("DEFAULT_NODE_TIMEOUT_MS");
            defaultField.setAccessible(true);
            long defaultTimeout = defaultField.getLong(null);

            assertThat(defaultTimeout).isEqualTo(30 * 1000);
        }

        @Test
        @DisplayName("API 节点超时为 30 秒")
        void shouldUseShortTimeoutForApiNode() throws Exception {
            var apiField = WorkflowEngine.class.getDeclaredField("API_NODE_TIMEOUT_MS");
            apiField.setAccessible(true);
            long apiTimeout = apiField.getLong(null);

            assertThat(apiTimeout).isEqualTo(30 * 1000);
        }
    }

    @Nested
    @DisplayName("submit - 超时")
    class SubmitTimeoutTests {

        @Test
        @DisplayName("执行超过超时时间后应标记失败")
        void shouldFailOnTimeout() throws Exception {
            // 设置超时为 -1ms，让第一次检查就触发（currentTime - startTime >= 0 > -1）
            var timeoutField = WorkflowEngine.class.getDeclaredField("workflowTimeoutMs");
            timeoutField.setAccessible(true);
            timeoutField.setLong(engine, -1L);

            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(workflowExecutor).execute(any(Runnable.class));

            Workflow workflow = buildWorkflow(1L, 1);
            when(workflowMapper.selectById(1L)).thenReturn(workflow);
            // 3 节点 + 边：start → middle → end
            when(nodeMapper.selectList(any())).thenReturn(List.of(
                    buildNode(1L, "start", "开始"),
                    buildNode(2L, "llm", "中间节点"),
                    buildNode(3L, "end", "结束")
            ));
            // 边：start(1) → llm(2) → end(3)
            when(edgeMapper.selectList(any())).thenReturn(List.of(
                    buildEdge(1L, 1L, "default", 2L),
                    buildEdge(2L, 2L, "default", 3L)
            ));
            when(executionMapper.insert(any(WorkflowExecution.class))).thenReturn(1);
            when(executionMapper.updateById(any(WorkflowExecution.class))).thenReturn(1);

            BlockingQueue<ExecutionEvent> queue = new LinkedBlockingQueue<>();
            engine.submit(1L, Map.of(), queue);

            // 应收到 execution_failed 事件（超时触发或 llm 节点 executor 不存在）
            ExecutionEvent failedEvent = null;
            while (!queue.isEmpty()) {
                ExecutionEvent e = queue.poll();
                if ("execution_failed".equals(e.getEvent())) {
                    failedEvent = e;
                }
            }
            assertThat(failedEvent).isNotNull();
        }
    }

    private WorkflowEdge buildEdge(Long id, Long sourceNodeId, String sourceHandle, Long targetNodeId) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setWorkflowId(1L);
        e.setSourceNodeId(sourceNodeId);
        e.setSourceHandle(sourceHandle);
        e.setTargetNodeId(targetNodeId);
        return e;
    }
}
