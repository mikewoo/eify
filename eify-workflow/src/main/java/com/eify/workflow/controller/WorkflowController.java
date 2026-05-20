package com.eify.workflow.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.workflow.domain.dto.WorkflowCreateRequest;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.domain.dto.WorkflowResponse;
import com.eify.workflow.domain.dto.WorkflowUpdateRequest;
import com.eify.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "工作流管理", description = "工作流的创建、查询、更新和删除")
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "分页查询工作流列表")
    @GetMapping
    public Result<PageResult<WorkflowResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(workflowService.list(page, pageSize));
    }

    @Operation(summary = "查询工作流详情")
    @GetMapping("/{id}")
    public Result<WorkflowDetailResponse> getById(@PathVariable Long id) {
        return Result.success(workflowService.getById(id));
    }

    @Operation(summary = "创建工作流")
    @PostMapping
    public Result<WorkflowDetailResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        return Result.success(workflowService.create(request));
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/{id}")
    public Result<WorkflowDetailResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody WorkflowUpdateRequest request) {
        return Result.success(workflowService.update(id, request));
    }

    @Operation(summary = "删除工作流")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.success();
    }
}
