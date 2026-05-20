package com.eify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.workflow.domain.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}
