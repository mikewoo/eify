package com.eify.common.log.message;

import com.eify.common.log.model.LogHeader;
import com.eify.common.log.model.LogMessage;
import com.eify.common.log.model.LogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 普通日志消息（SIMPLE）
 *
 * <p>用于记录常规的业务日志，支持自定义标签
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleLogMessage extends LogMessage {

    /**
     * 日志消息内容
     */
    private String message;

    /**
     * 日志级别：TRACE、DEBUG、INFO、WARN、ERROR
     */
    private String level;

    /**
     * 自定义标签（用于结构化查询）
     */
    private Map<String, Object> tags;

    /**
     * 异常信息（如果有）
     */
    private String exception;

    /**
     * 构造函数（带 header）
     */
    public SimpleLogMessage(LogHeader header) {
        this.header = header;
        if (this.header != null) {
            this.header.setLogType(LogType.SIMPLE);
        }
    }

    @Override
    public String getBodyJson() {
        return toJson(SimpleBody.builder()
                .message(message)
                .level(level)
                .tags(tags)
                .exception(exception)
                .build());
    }

    /**
     * 普通日志 Body 结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleBody {
        private String message;
        private String level;
        private Map<String, Object> tags;
        private String exception;
    }
}
