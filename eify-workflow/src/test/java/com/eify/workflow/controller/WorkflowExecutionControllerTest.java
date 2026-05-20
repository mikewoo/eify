package com.eify.workflow.controller;

import com.eify.common.exception.BusinessException;
import com.eify.common.error.ErrorCode;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.engine.ExecutionEvent;
import com.eify.workflow.engine.WorkflowEngine;
import com.eify.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionController")
class WorkflowExecutionControllerTest {

    @Mock
    WorkflowEngine workflowEngine;

    @Mock
    WorkflowService workflowService;

    WorkflowExecutionController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkflowExecutionController(workflowEngine, workflowService);
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("工作流不存在时应传播异常")
        void shouldPropagateExceptionWhenWorkflowNotFound() {
            when(workflowService.getById(999L))
                    .thenThrow(new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND));

            assertThatThrownBy(() -> controller.execute(999L, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("执行成功应返回 SseEmitter")
        void shouldReturnSseEmitterOnSuccess() {
            when(workflowService.getById(1L)).thenReturn(mock(WorkflowDetailResponse.class));
            doAnswer(invocation -> {
                // 不执行任何操作 — 让 submit 安静通过
                return null;
            }).when(workflowEngine).submit(eq(1L), anyMap(), any(BlockingQueue.class));

            SseEmitter emitter = controller.execute(1L, null);

            assertThat(emitter).isNotNull();
            verify(workflowEngine).submit(eq(1L), anyMap(), any(BlockingQueue.class));
        }
    }
}
