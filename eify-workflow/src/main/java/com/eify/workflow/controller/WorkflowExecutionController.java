package com.eify.workflow.controller;

import tools.jackson.databind.ObjectMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import com.eify.workflow.domain.dto.WorkflowExecuteRequest;
import com.eify.workflow.engine.ExecutionEvent;
import com.eify.workflow.engine.WorkflowEngine;
import com.eify.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Tag(name = "工作流执行", description = "工作流执行、SSE 实时日志推送")
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private static final int SSE_TIMEOUT_MS = 5 * 60 * 1000; // 5 分钟
    private static final ObjectMapper SSE_MAPPER = new ObjectMapper();

    private final WorkflowEngine workflowEngine;
    private final WorkflowService workflowService;

    /**
     * 执行工作流，SSE 流式返回执行事件。
     */
    @PostMapping(value = "/{id}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter execute(@PathVariable Long id,
                              @RequestBody(required = false) WorkflowExecuteRequest request) {
        // 验证工作流存在
        workflowService.getById(id);

        SseEmitter emitter = new SseEmitter((long) SSE_TIMEOUT_MS);
        BlockingQueue<ExecutionEvent> eventQueue = new LinkedBlockingQueue<>();
        Map<String, Object> inputVars = request != null ? request.getVariables() : Map.of();

        // 提交到引擎线程池
        workflowEngine.submit(id, inputVars, eventQueue);

        // 桥接线程：从 eventQueue 读取事件，转换为 SSE 发送
        Thread bridgeThread = new Thread(() -> {
            try {
                while (true) {
                    ExecutionEvent event = eventQueue.poll(5, TimeUnit.SECONDS);
                    if (event == null) {
                        // 超时检查：发送心跳
                        try {
                            emitter.send(SseEmitter.event().comment("keepalive"));
                        } catch (IllegalStateException e) {
                            log.debug("SSE 连接已关闭，停止桥接");
                            break;
                        }
                        continue;
                    }

                    try {
                        String json = SSE_MAPPER.writeValueAsString(event);
                        emitter.send(SseEmitter.event()
                                .name(event.getEvent())
                                .data(json));
                    } catch (IllegalStateException e) {
                        log.debug("SSE 连接已关闭");
                        break;
                    }

                    // 终止事件
                    if (event.getEvent() != null &&
                            (event.getEvent().contains("completed") || event.getEvent().contains("failed"))) {
                        break;
                    }
                }
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("SSE 桥接异常", e);
                emitter.completeWithError(e);
            }
        }, "workflow-sse-" + id);
        bridgeThread.setDaemon(true);
        bridgeThread.start();

        emitter.onCompletion(bridgeThread::interrupt);
        emitter.onTimeout(() -> {
            bridgeThread.interrupt();
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {}
        });
        emitter.onError(e -> bridgeThread.interrupt());

        return emitter;
    }
}
