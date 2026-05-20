package com.eify.common.log.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日志等级枚举
 *
 * <p>使用 Java 标准日志等级，符合 SLF4J/Logback 规范
 *
 * @author Claude
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum LogLevel {

    /**
     * 最详细的跟踪信息
     */
    TRACE("TRACE", 0),

    /**
     * 调试信息
     */
    DEBUG("DEBUG", 1),

    /**
     * 一般信息（默认）
     */
    INFO("INFO", 2),

    /**
     * 警告信息
     */
    WARN("WARN", 3),

    /**
     * 错误信息
     */
    ERROR("ERROR", 4);

    /**
     * 日志等级名称
     */
    private final String name;

    /**
     * 日志等级数值（用于比较）
     */
    private final int value;

    /**
     * 根据名称获取日志等级
     *
     * @param name 日志等级名称
     * @return 日志等级
     */
    public static LogLevel fromName(String name) {
        for (LogLevel level : values()) {
            if (level.name.equalsIgnoreCase(name)) {
                return level;
            }
        }
        return INFO;
    }

    /**
     * 从 Logback Level 转换
     *
     * @param level Logback Level
     * @return 日志等级
     */
    public static LogLevel fromLogbackLevel(ch.qos.logback.classic.Level level) {
        if (level == null) {
            return INFO;
        }
        if (level.isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR)) {
            return ERROR;
        } else if (level.isGreaterOrEqual(ch.qos.logback.classic.Level.WARN)) {
            return WARN;
        } else if (level.isGreaterOrEqual(ch.qos.logback.classic.Level.INFO)) {
            return INFO;
        } else if (level.isGreaterOrEqual(ch.qos.logback.classic.Level.DEBUG)) {
            return DEBUG;
        } else {
            return TRACE;
        }
    }

    /**
     * 转换为 Logback Level
     *
     * @return Logback Level
     */
    public ch.qos.logback.classic.Level toLogbackLevel() {
        switch (this) {
            case TRACE:
                return ch.qos.logback.classic.Level.TRACE;
            case DEBUG:
                return ch.qos.logback.classic.Level.DEBUG;
            case INFO:
                return ch.qos.logback.classic.Level.INFO;
            case WARN:
                return ch.qos.logback.classic.Level.WARN;
            case ERROR:
                return ch.qos.logback.classic.Level.ERROR;
            default:
                return ch.qos.logback.classic.Level.INFO;
        }
    }
}
