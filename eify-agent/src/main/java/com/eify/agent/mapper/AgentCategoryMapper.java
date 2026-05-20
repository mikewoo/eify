package com.eify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.agent.domain.entity.AgentCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 分类 Mapper
 */
@Mapper
public interface AgentCategoryMapper extends BaseMapper<AgentCategory> {
}
