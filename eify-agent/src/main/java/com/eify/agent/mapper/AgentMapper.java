package com.eify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.agent.domain.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
