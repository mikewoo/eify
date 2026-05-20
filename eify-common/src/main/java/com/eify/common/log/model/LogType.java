package com.eify.common.log.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日志类型枚举
 *
 * @author Claude
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum LogType {

    /**
     * 请求日志
     */
    REQ("req", "请求日志"),

    /**
     * SQL 日志
     */
    SQL("sql", "SQL日志"),

    /**
     * 消息日志（Kafka、异步任务、事件通知）
     */
    MSG("msg", "消息日志"),

    /**
     * 简单业务日志
     */
    SIMPLE("simple", "简单业务日志"),

    /**
     * 系统/框架日志
     */
    SYS("sys", "系统/框架日志");

    /**
     * 日志类型代码
     */
    private final String code;

    /**
     * 日志类型描述
     */
    private final String description;

    /**
     * 根据代码获取日志类型
     *
     * @param code 日志类型代码
     * @return 日志类型
     */
    public static LogType fromCode(String code) {
        if (code == null) {
            return SYS;
        }
        for (LogType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return SYS;
    }
}
