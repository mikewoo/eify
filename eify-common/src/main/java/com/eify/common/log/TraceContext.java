package com.eify.common.log;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Trace Context 跨线程传递工具
 *
 * <p>解决异步线程中 TraceId/SpanId 丢失的问题
 *
 * <p>使用示例：
 * <pre>{@code
 * // Runnable
 * CompletableFuture.runAsync(TraceContext.wrap(() -> {
 *     log.info("异步任务 - traceId 自动传递");
 * }));
 *
 * // Supplier
 * CompletableFuture.supplyAsync(TraceContext.wrap(() -> {
 *     return "结果";
 * }));
 *
 * // Callable
 * Future<String> future = executor.submit(TraceContext.wrap(() -> {
 *     return "结果";
 * }));
 * }</pre>
 *
 * @author Claude
 * @since 1.0.0
 */
public class TraceContext {

    private static final String[] TRACE_KEYS = {"traceId", "spanId"};

    /**
     * 包装 Runnable，自动传递 Trace Context
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = getRelevantContext();
        return () -> {
            try {
                setContext(contextMap);
                task.run();
            } finally {
                clearContext();
            }
        };
    }

    /**
     * 包装 Supplier，自动传递 Trace Context
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> contextMap = getRelevantContext();
        return () -> {
            try {
                setContext(contextMap);
                return task.get();
            } finally {
                clearContext();
            }
        };
    }

    /**
     * 包装 Callable，自动传递 Trace Context
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> contextMap = getRelevantContext();
        return () -> {
            try {
                setContext(contextMap);
                return task.call();
            } finally {
                clearContext();
            }
        };
    }

    /**
     * 获取当前的 Trace Context
     *
     * <p>仅返回 traceId 和 spanId，避免传递不必要的 MDC 数据
     */
    private static Map<String, String> getRelevantContext() {
        Map<String, String> fullContext = MDC.getCopyOfContextMap();
        if (fullContext == null || fullContext.isEmpty()) {
            return null;
        }

        // 仅保留 traceId 和 spanId
        Map<String, String> relevantContext = new java.util.HashMap<>();
        for (String key : TRACE_KEYS) {
            String value = fullContext.get(key);
            if (value != null) {
                relevantContext.put(key, value);
            }
        }

        return relevantContext.isEmpty() ? null : relevantContext;
    }

    /**
     * 设置 Trace Context 到 MDC
     */
    private static void setContext(Map<String, String> contextMap) {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * 清理 Trace Context
     */
    private static void clearContext() {
        for (String key : TRACE_KEYS) {
            MDC.remove(key);
        }
    }

    /**
     * 在代码块中执行，自动传递 Trace Context
     *
     * <p>使用示例：
     * <pre>{@code
     * TraceContext.run(() -> {
     *     log.info("这段代码的日志会带上 traceId");
     * });
     * }</pre>
     */
    public static void run(Runnable task) {
        wrap(task).run();
    }

    /**
     * 在代码块中执行，自动传递 Trace Context，并返回结果
     *
     * <p>使用示例：
     * <pre>{@code
     * String result = TraceContext.call(() -> {
     *     log.info("这段代码的日志会带上 traceId");
     *     return "结果";
     * });
     * }</pre>
     */
    public static <T> T call(Supplier<T> task) {
        return wrap(task).get();
    }
}