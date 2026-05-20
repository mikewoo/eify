package com.eify.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Agent 使用统计实体（预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_usage_stats")
public class AgentUsageStats extends BaseEntity {

    /**
     * Agent ID
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 调用次数
     */
    private Integer callCount;

    /**
     * 提示词 tokens
     */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /**
     * 完成 tokens
     */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /**
     * 总 tokens
     */
    @TableField("total_tokens")
    private Integer totalTokens;

    /**
     * 预估成本（元）
     */
    private BigDecimal estimatedCost;

    /**
     * 统计日期
     */
    @TableField("stat_date")
    private LocalDate statDate;

    /**
     * 统计小时（0-23）
     */
    @TableField("stat_hour")
    private Integer statHour;
}
