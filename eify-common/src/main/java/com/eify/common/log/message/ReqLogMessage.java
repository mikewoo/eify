package com.eify.common.log.message;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.eify.common.log.model.LogHeader;
import com.eify.common.log.model.LogMessage;
import com.eify.common.log.model.LogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求日志消息（REQ）
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqLogMessage extends LogMessage {

    /**
     * HTTP 方法：GET、POST、PUT、DELETE 等
     */
    private String method;

    /**
     * 请求路径：/api/v1/chat
     */
    private String path;

    /**
     * HTTP 状态码：200、400、500 等
     */
    private Integer status;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 请求体（可选，避免记录敏感信息）
     */
    private Object requestBody;

    /**
     * 响应体（可选，根据配置记录）
     * 如果是 JSON 字符串，会自动解析为对象
     */
    private Object responseBody;

    /**
     * 请求体大小（字节）
     */
    private Integer requestBodySize;

    /**
     * 响应体大小（字节）
     */
    private Integer responseSize;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 请求耗时（毫秒）
     */
    private Long duration;

    /**
     * 是否异步请求
     */
    private Boolean asyncRequest;

    /**
     * 异步类型：ASYNC_CONTEXT、FUTURE、SSE、ASYNC_METHOD
     */
    private String asyncType;

    /**
     * 异步完成状态：COMPLETED、TIMEOUT、ERROR
     */
    private String asyncCompletionStatus;

    /**
     * 构造函数（带 header）
     */
    public ReqLogMessage(LogHeader header) {
        this.header = header;
        if (this.header != null) {
            this.header.setLogType(LogType.REQ);
            if (this.duration != null) {
                this.header.setDuration(this.duration);
            }
        }
    }

    @Override
    public String getBodyJson() {
        return toJson(ReqBody.builder()
                .method(method)
                .path(path)
                .status(status)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .requestBody(parseJsonIfNeeded(requestBody))
                .responseBody(parseJsonIfNeeded(responseBody))
                .requestBodySize(requestBodySize)
                .responseSize(responseSize)
                .error(error)
                .duration(duration)
                .asyncRequest(asyncRequest)
                .asyncType(asyncType)
                .asyncCompletionStatus(asyncCompletionStatus)
                .build());
    }

    /**
     * 如果对象是 JSON 字符串，解析为对象
     * 这样在最终序列化时不会被转义
     *
     * @param obj 原始对象（可能是字符串）
     * @return 解析后的对象
     */
    private Object parseJsonIfNeeded(Object obj) {
        if (obj == null) {
            return null;
        }
        if (!(obj instanceof String)) {
            return obj;
        }

        String str = (String) obj;
        if (str.isEmpty() || str.trim().isEmpty()) {
            return null;
        }

        // 尝试解析为 JSON
        try {
            // 去除可能的换行符和多余空格
            str = str.trim();
            if (str.startsWith("{") || str.startsWith("[")) {
                Object parsed = JSON.parse(str);
                // 如果解析成功且是 JSONObject/JSONArray，返回解析后的对象
                // 否则返回原始字符串
                return (parsed instanceof JSONObject) ? parsed : str;
            }
        } catch (Exception e) {
            // JSON 解析失败，返回原始字符串
        }
        return str;
    }

    /**
     * 重写 toJson 方法，处理 JSON 字符串的特殊情况
     */
    @Override
    protected String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        // 如果已经是解析后的对象（JSONObject），直接序列化
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).toJSONString();
        }
        return super.toJson(obj);
    }

    /**
     * 请求日志 Body 结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReqBody {
        private String method;
        private String path;
        private Integer status;
        private String clientIp;
        private String userAgent;
        private Object requestBody;
        private Object responseBody;
        private Integer requestBodySize;
        private Integer responseSize;
        private String error;
        private Long duration;
        private Boolean asyncRequest;
        private String asyncType;
        private String asyncCompletionStatus;
    }
}
