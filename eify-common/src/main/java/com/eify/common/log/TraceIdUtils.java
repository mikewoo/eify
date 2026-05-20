package com.eify.common.log;

import org.slf4j.MDC;

/**
 * TraceId 工具类
 *
 * <p>统一使用 Micrometer Tracing (Brave) 生成 traceId 和 spanId
 * 本类仅提供 MDC 读取功能，不再手动生成 ID
 *
 * @author Claude
 * @since 1.0.0
 */
public class TraceIdUtils {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    /**
     * 获取当前 TraceId
     *
     * <p>从 MDC 中读取，由 Brave Tracing 自动生成和管理
     *
     * @return TraceId，如果不存在返回 "N/A"
     */
    public static String getTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : "N/A";
    }

    /**
     * 获取当前 SpanId
     *
     * <p>从 MDC 中读取，由 Brave Tracing 自动生成和管理
     *
     * @return SpanId，如果不存在返回 "N/A"
     */
    public static String getSpanId() {
        String spanId = MDC.get(MDC_SPAN_ID);
        return spanId != null ? spanId : "N/A";
    }

    /**
     * 清除 TraceId 和 SpanId
     *
     * <p>注意：通常不需要手动调用，TracingFilter 会自动清理
     */
    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }
}
