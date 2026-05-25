package com.eify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.workflow.domain.entity.Workflow;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {

    @Select("SELECT COUNT(*) FROM ai_chat_session WHERE workflow_id = #{workflowId} AND deleted = 0 AND status = 1")
    int countConversationReferences(@Param("workflowId") Long workflowId);
}
