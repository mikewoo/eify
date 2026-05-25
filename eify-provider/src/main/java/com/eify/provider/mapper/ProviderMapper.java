package com.eify.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.provider.domain.entity.Provider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 供应商 Mapper
 */
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {

    @Select("SELECT COUNT(*) FROM ai_agent WHERE default_provider_id = #{providerId} AND deleted = 0")
    int countAgentReferences(@Param("providerId") Long providerId);

    @Select("SELECT COUNT(*) FROM ai_workflow_node n " +
            "INNER JOIN ai_workflow w ON n.workflow_id = w.id " +
            "WHERE n.type = 'llm' " +
            "AND JSON_EXTRACT(n.config, '$.providerId') = #{providerId} " +
            "AND n.deleted = 0 " +
            "AND w.deleted = 0")
    int countWorkflowLlmReferences(@Param("providerId") Long providerId);
}
