package com.eify.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 与知识库关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_knowledge")
public class AgentKnowledge extends BaseEntity {

    @TableField("agent_id")
    private Long agentId;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long creatorId;
}
