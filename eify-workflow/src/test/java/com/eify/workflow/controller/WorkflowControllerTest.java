package com.eify.workflow.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.workflow.domain.dto.WorkflowCreateRequest;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.domain.dto.WorkflowResponse;
import com.eify.workflow.domain.dto.WorkflowUpdateRequest;
import com.eify.workflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowController")
class WorkflowControllerTest {

    @Mock
    WorkflowService workflowService;

    @InjectMocks
    WorkflowController controller;

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("应返回分页工作流列表")
        void shouldReturnPagedWorkflows() {
            PageResult<WorkflowResponse> pageResult = new PageResult<>();
            when(workflowService.list(1, 20)).thenReturn(pageResult);

            Result<PageResult<WorkflowResponse>> result = controller.list(1, 20);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(pageResult);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("应返回工作流详情")
        void shouldReturnWorkflowDetail() {
            WorkflowDetailResponse detail = mock(WorkflowDetailResponse.class);
            when(workflowService.getById(1L)).thenReturn(detail);

            Result<WorkflowDetailResponse> result = controller.getById(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(detail);
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("创建成功应返回工作流详情")
        void shouldCreateAndReturnDetail() {
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("test-workflow");
            WorkflowDetailResponse detail = mock(WorkflowDetailResponse.class);
            when(workflowService.create(any())).thenReturn(detail);

            Result<WorkflowDetailResponse> result = controller.create(req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(detail);
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("更新成功应返回工作流详情")
        void shouldUpdateAndReturnDetail() {
            WorkflowUpdateRequest req = new WorkflowUpdateRequest();
            req.setName("updated");
            WorkflowDetailResponse detail = mock(WorkflowDetailResponse.class);
            when(workflowService.update(1L, req)).thenReturn(detail);

            Result<WorkflowDetailResponse> result = controller.update(1L, req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(detail);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("删除成功应返回 Result.success")
        void shouldDeleteAndReturnSuccess() {
            Result<Void> result = controller.delete(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(workflowService).delete(1L);
        }
    }
}
