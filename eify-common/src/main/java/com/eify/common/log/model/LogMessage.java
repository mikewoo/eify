package com.eify.common.log.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 统一日志消息基类
 *
 * <p>每种日志类型（REQ、MSG、SQL、SIMPLE）都应继承此类，
 * 实现自己的 body 结构。
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
public abstract class LogMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 日志 header（包含 13 个标准字段）
     */
    protected LogHeader header;

    /**
     * 获取日志 body 的 JSON 字符串
     *
     * @return JSON 字符串
     */
    public abstract String getBodyJson();

    /**
     * 获取完整的日志字符串（仅 body JSON，让 StructuredLogLayout 处理 header）
     *
     * @return body JSON 字符串
     */
    public String format() {
        // 只返回 body JSON，由 StructuredLogLayout 统一处理 header
        // 不包含 logType 字段，因为 header 中已经有了
        return getBodyJson();
    }

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    protected String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            return "{}";
        }
    }
}
