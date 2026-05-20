package com.eify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.agent.domain.entity.AgentUsageStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 使用统计 Mapper
 */
@Mapper
public interface AgentUsageStatsMapper extends BaseMapper<AgentUsageStats> {
}
