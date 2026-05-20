package com.eify.common.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SQL 日志配置属性
 *
 * <p>从 application.yml 中读取 sql.logging 配置
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "sql.logging")
public class SqlLogProperties {

    /**
     * 是否启用 SQL 日志（默认 true）
     */
    private boolean enabled = true;

    /**
     * 慢查询阈值（毫秒，默认 1000ms）
     */
    private long slowQueryThreshold = 1000;

    /**
     * SQL 最大长度（字符数，默认 2000）
     */
    private int maxSqlLength = 2000;

    /**
     * 采样率（默认 0.1，即 10%）
     */
    private double samplingRate = 0.1;

    /**
     * 是否记录参数（默认 true）
     */
    private boolean recordParams = true;

    /**
     * 是否记录完整错误堆栈（默认 false）
     */
    private boolean recordFullStack = false;

    /**
     * 堆栈最大深度（行数，默认 50）
     */
    private int maxStackDepth = 50;
}
