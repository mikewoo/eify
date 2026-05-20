package com.eify.chat.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件数据
 * <p>
 * 用于流式对话的 Server-Sent Events 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "SSE 事件数据")
public class SseEvent {

    /**
     * 事件类型
     * <p>
     * - message: 内容块
     * - complete: 正常完成
     * - error: 错误
     * - timeout: 超时
     */
    @Schema(description = "事件类型：message/complete/error/timeout")
    private String event;

    /**
     * 数据（JSON 字符串）
     */
    @Schema(description = "事件数据")
    private Object data;

    // ========== 事件类型常量 ==========

    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_COMPLETE = "complete";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_TIMEOUT = "timeout";

    // ========== 静态工厂方法 ==========

    /**
     * 创建消息事件（内容块）
     */
    public static SseEvent message(String content) {
        return SseEvent.builder()
                .event(EVENT_MESSAGE)
                .data(MessageData.of(content))
                .build();
    }

    /**
     * 创建完成事件
     */
    public static SseEvent complete(UsageData usage, String finishReason) {
        return SseEvent.builder()
                .event(EVENT_COMPLETE)
                .data(CompleteData.of(usage, finishReason))
                .build();
    }

    /**
     * 创建错误事件
     */
    public static SseEvent error(String errorMessage) {
        return SseEvent.builder()
                .event(EVENT_ERROR)
                .data(ErrorData.of(errorMessage))
                .build();
    }

    /**
     * 创建超时事件
     */
    public static SseEvent timeout(String message) {
        return SseEvent.builder()
                .event(EVENT_TIMEOUT)
                .data(TimeoutData.of(message))
                .build();
    }

    // ========== 内部数据类 ==========

    /**
     * 消息数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageData {
        private String content;
        private boolean done;

        public static MessageData of(String content) {
            return new MessageData(content, false);
        }
    }

    /**
     * 完成数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompleteData {
        private boolean done;
        private UsageData usage;
        private String finishReason;

        public static CompleteData of(UsageData usage, String finishReason) {
            return new CompleteData(true, usage, finishReason);
        }
    }

    /**
     * 错误数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorData {
        private String error;

        public static ErrorData of(String error) {
            return new ErrorData(error);
        }
    }

    /**
     * 超时数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeoutData {
        private boolean timeout;
        private String message;

        public static TimeoutData of(String message) {
            return new TimeoutData(true, message);
        }
    }

    /**
     * Token 使用数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageData {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
