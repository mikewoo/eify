package com.eify.common.log.message;

import com.eify.common.log.model.LogHeader;
import com.eify.common.log.model.LogMessage;
import com.eify.common.log.model.LogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 日志消息（SQL）
 *
 * <p>统一日志格式的 SQL 日志消息
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlLogMessage extends LogMessage {

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * TraceId
     */
    private String traceId;

    /**
     * SQL 语句
     */
    private String sql;

    /**
     * SQL 参数列表
     */
    private Object params;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * Mapper ID
     */
    private String mappedId;

    /**
     * 是否慢查询
     */
    private Boolean isSlowQuery;

    /**
     * 影响行数（或查询结果行数）
     */
    private Integer rowCount;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 错误堆栈（慢查询或失败时记录）
     */
    private String errorStack;

    /**
     * 构造函数（带 header）
     */
    public SqlLogMessage(LogHeader header) {
        this.header = header;
        if (this.header != null) {
            this.header.setLogType(LogType.SQL);
            // SQL 日志的耗时就是执行时间
            if (this.executionTime != null) {
                this.header.setDuration(this.executionTime);
            }
        }
    }

    @Override
    public String getBodyJson() {
        return toJson(SqlBody.builder()
                .appName(appName)
                .sql(sql)
                .params(params)
                .executionTime(executionTime)
                .mappedId(mappedId)
                .isSlowQuery(isSlowQuery)
                .rowCount(rowCount)
                .error(error)
                .errorStack(errorStack)
                .build());
    }

    /**
     * SQL 日志 Body 结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SqlBody {
        private String appName;
        private String sql;
        private Object params;
        private Long executionTime;
        private String mappedId;
        private Boolean isSlowQuery;
        private Integer rowCount;
        private String error;
        private String errorStack;
    }
}
