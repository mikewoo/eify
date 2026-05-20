package com.eify.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 分类实体（预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_category")
public class AgentCategory extends BaseEntity {

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类图标
     */
    private String icon;

    /**
     * 分类颜色
     */
    private String color;

    /**
     * 排序
     */
    private Integer sortOrder;
}
