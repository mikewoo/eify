package com.eify.provider.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 健康状态枚举
 */
public enum HealthStatus {

    /**
     * 正常运行
     */
    UP("UP", "正常运行"),

    /**
     * 不可用
     */
    DOWN("DOWN", "不可用"),

    /**
     * 降级运行
     */
    DEGRADED("DEGRADED", "降级运行"),

    /**
     * 未知状态
     */
    UNKNOWN("UNKNOWN", "未知状态");

    /**
     * 数据库存储值
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 描述
     */
    private final String description;

    HealthStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据值获取枚举
     */
    public static HealthStatus fromValue(String value) {
        for (HealthStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
