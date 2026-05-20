package com.eify.provider.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.eify.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商健康状态实体
 * <p>
 * 对应数据库表：provider_health
 * <p>
 * 注意：此表使用 provider_id 唯一索引，每个供应商只有一条健康记录，
 * 更新时使用 ON DUPLICATE KEY UPDATE 实现原地更新
 */
@Data
@TableName("provider_health")
public class ProviderHealth extends BaseEntity {

    /**
     * 供应商 ID（唯一索引）
     */
    @TableField("provider_id")
    private Long providerId;

    /**
     * 健康状态：UP/DOWN/DEGRADED/UNKNOWN
     */
    @TableField("status")
    private String status;

    /**
     * 最后探测时间
     */
    @TableField("last_check_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckAt;

    /**
     * 最后成功时间
     */
    @TableField("last_success_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSuccessAt;

    /**
     * 连续失败次数
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 最近一次延迟（毫秒）
     */
    @TableField("latency_ms")
    private Integer latencyMs;

    /**
     * 最近失败原因
     */
    @TableField("error_message")
    private String errorMessage;
}
