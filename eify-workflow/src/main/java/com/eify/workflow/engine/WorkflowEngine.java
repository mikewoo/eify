package com.eify.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowExecution;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.mapper.WorkflowEdgeMapper;
import com.eify.workflow.mapper.WorkflowExecutionMapper;
import com.eify.workflow.mapper.WorkflowMapper;
import com.eify.workflow.mapper.WorkflowNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkflowEngine {

    /** 工作流整体执行超时（毫秒），可通过反射修改用于测试 */
    long workflowTimeoutMs = 5 * 60 * 1000;

    /** LLM 节点超时（毫秒），生成式模型响应较慢 */
    private static final long LLM_NODE_TIMEOUT_MS = 3 * 60 * 1000;

    /** 默认节点超时（毫秒），API/代码/条件等节点 */
    private static final long DEFAULT_NODE_TIMEOUT_MS = 30 * 1000;

    /** API 调用节点超时（毫秒），外部 HTTP 调用应有较短超时 */
    private static final long API_NODE_TIMEOUT_MS = 30 * 1000;

    /** 需要长超时的节点类型（LLM 生成） */
    private static final Set<String> LONG_TIMEOUT_TYPES = Set.of("llm");

    private final Executor workflowExecutor;
    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final Map<String, NodeExecutor> executorRegistry;

    public WorkflowEngine(
            @Qualifier("workflowExecutor") Executor workflowExecutor,
            WorkflowMapper workflowMapper,
            WorkflowNodeMapper nodeMapper,
            WorkflowEdgeMapper edgeMapper,
            WorkflowExecutionMapper executionMapper,
            List<NodeExecutor> executors) {
        this.workflowExecutor = workflowExecutor;
        this.workflowMapper = workflowMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.executionMapper = executionMapper;
        this.executorRegistry = executors.stream()
                .collect(Collectors.toMap(NodeExecutor::getType, Function.identity()));
    }

    /**
     * 提交工作流执行。
     *
     * @param workflowId 工作流 ID
     * @param inputVars  运行时输入变量 (如 user_input="我要退货")
     * @param eventQueue 事件队列，SSE 读取线程从这里取事件
     */
    public void submit(Long workflowId, Map<String, Object> inputVars,
                       BlockingQueue<ExecutionEvent> eventQueue) {
        workflowExecutor.execute(() -> {
            ExecutionContext ctx = null;
            try {
                // 1. 加载工作流快照
                Workflow workflow = workflowMapper.selectById(workflowId);
                if (workflow == null) {
                    eventQueue.put(ExecutionEvent.executionFailed("工作流不存在"));
                    return;
                }
                if (workflow.getStatus() != null && workflow.getStatus() == 2) {
                    eventQueue.put(ExecutionEvent.executionFailed("工作流已禁用"));
                    return;
                }

                List<WorkflowNode> nodes = nodeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowNode>()
                                .eq(WorkflowNode::getWorkflowId, workflowId));
                List<WorkflowEdge> edges = edgeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowEdge>()
                                .eq(WorkflowEdge::getWorkflowId, workflowId));

                if (nodes.isEmpty()) {
                    eventQueue.put(ExecutionEvent.executionFailed("工作流无节点"));
                    return;
                }

                // 2. 创建执行记录
                WorkflowExecution execution = new WorkflowExecution();
                execution.setWorkflowId(workflow.getId());
                execution.setWorkflowVersion(workflow.getVersion());
                execution.setStatus("running");
                execution.setStartedAt(LocalDateTime.now());
                executionMapper.insert(execution);

                // 3. 构建上下文
                ctx = new ExecutionContext(execution.getId(), workflow, nodes, edges);
                if (inputVars != null) {
                    inputVars.forEach(ctx::setVariable);
                }

                eventQueue.put(ExecutionEvent.executionStarted(ctx.getExecutionId()));

                // 4. 核心循环
                coreLoop(ctx, eventQueue);

                // 5. 更新执行记录
                if ("failed".equals(ctx.getStatus())) {
                    updateExecutionRecord(ctx);
                    eventQueue.put(ExecutionEvent.executionFailed(ctx.getErrorMessage()));
                } else {
                    updateExecutionRecord(ctx);
                    eventQueue.put(ExecutionEvent.executionCompleted(ctx.getExecutionId()));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (ctx != null) {
                    ctx.markFailed("执行被中断");
                    updateExecutionRecord(ctx);
                }
            } catch (Exception e) {
                log.error("工作流执行异常: workflowId={}", workflowId, e);
                if (ctx != null) {
                    ctx.markFailed(e.getMessage());
                    updateExecutionRecord(ctx);
                }
                try {
                    eventQueue.put(ExecutionEvent.executionFailed(e.getMessage()));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void coreLoop(ExecutionContext ctx, BlockingQueue<ExecutionEvent> eventQueue)
            throws InterruptedException {

        long startTime = System.currentTimeMillis();
        WorkflowNode node = ctx.getCurrentNode();
        eventQueue.put(ExecutionEvent.nodeStarted(node));

        while (node != null) {
            // 心跳：更新执行记录的 updated_at，用于外部监控检测卡住的执行
            if (ctx.getExecutionId() != null) {
                WorkflowExecution heartbeat = new WorkflowExecution();
                heartbeat.setId(ctx.getExecutionId());
                heartbeat.setStatus("running");
                executionMapper.updateById(heartbeat);
            }

            // 整体执行超时保护
            if (System.currentTimeMillis() - startTime > workflowTimeoutMs) {
                ctx.markFailed("工作流执行超时（超过 " + workflowTimeoutMs / 60000 + " 分钟）");
                return;
            }

            if ("end".equals(node.getType())) {
                NodeExecutor executor = getExecutor("end");
                NodeResult result = executor.execute(node, ctx);
                ctx.setLastResult(result);
                if (result.getOutputs() != null) {
                    result.getOutputs().forEach(ctx::setVariable);
                }
                eventQueue.put(ExecutionEvent.nodeCompleted(node, result));
                ctx.markCompleted();
                break;
            }

            // 执行当前节点（含单节点超时保护）
            long nodeStart = System.currentTimeMillis();
            NodeExecutor executor = getExecutor(node.getType());
            NodeResult result = executor.execute(node, ctx);

            // 单节点超时检查（按节点类型区分：LLM 3 分钟，API 30 秒）
            long nodeTimeout = LONG_TIMEOUT_TYPES.contains(node.getType())
                    ? LLM_NODE_TIMEOUT_MS : DEFAULT_NODE_TIMEOUT_MS;
            long nodeElapsed = System.currentTimeMillis() - nodeStart;
            if (nodeElapsed > nodeTimeout) {
                log.warn("单节点执行超时: nodeId={}, type={}, elapsedMs={}, timeoutMs={}",
                        node.getId(), node.getType(), nodeElapsed, nodeTimeout);
                eventQueue.put(ExecutionEvent.nodeFailed(node, result));
                ctx.markFailed("节点执行超时（" + node.getType() + " 节点超过 "
                        + nodeTimeout / 1000 + " 秒）");
                return;
            }

            ctx.setLastResult(result);

            if (!result.isSuccess()) {
                eventQueue.put(ExecutionEvent.nodeFailed(node, result));
                ctx.markFailed(result.getErrorMessage());
                return;
            }

            // 将输出写入上下文
            if (result.getOutputs() != null) {
                result.getOutputs().forEach(ctx::setVariable);
            }

            eventQueue.put(ExecutionEvent.nodeCompleted(node, result));

            // 决定下一个节点
            String handle = result.getNextHandle() != null ? result.getNextHandle() : "default";
            Long nextNodeId = ctx.resolveNextNode(handle);

            if (nextNodeId == null) {
                ctx.markCompleted();
                break;
            }

            ctx.advanceTo(nextNodeId);
            node = ctx.getCurrentNode();
            eventQueue.put(ExecutionEvent.nodeStarted(node));
        }
    }

    private NodeExecutor getExecutor(String type) {
        NodeExecutor ex = executorRegistry.get(type);
        if (ex == null) {
            throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "不支持的节点类型: " + type);
        }
        return ex;
    }

    private void updateExecutionRecord(ExecutionContext ctx) {
        WorkflowExecution exec = new WorkflowExecution();
        exec.setId(ctx.getExecutionId());
        exec.setStatus(ctx.getStatus());
        if (ctx.getErrorMessage() != null) {
            exec.setErrorMessage(ctx.getErrorMessage());
        }
        if ("completed".equals(ctx.getStatus()) || "failed".equals(ctx.getStatus())) {
            exec.setCompletedAt(LocalDateTime.now());
        }
        executionMapper.updateById(exec);
    }
}
