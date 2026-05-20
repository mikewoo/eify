package com.eify.workflow.service;

import com.eify.common.result.PageResult;
import com.eify.workflow.domain.dto.WorkflowCreateRequest;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.domain.dto.WorkflowResponse;
import com.eify.workflow.domain.dto.WorkflowUpdateRequest;

public interface WorkflowService {

    PageResult<WorkflowResponse> list(Integer page, Integer pageSize);

    WorkflowDetailResponse getById(Long id);

    WorkflowDetailResponse create(WorkflowCreateRequest request);

    WorkflowDetailResponse update(Long id, WorkflowUpdateRequest request);

    void delete(Long id);
}
