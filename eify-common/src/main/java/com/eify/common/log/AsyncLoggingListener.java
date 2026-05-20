package com.eify.common.log;

import com.eify.common.log.util.StructuredLogger;
import com.eify.common.log.message.ReqLogMessage;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步请求日志监听器
 * <p>
 * 用于监听 Servlet 异步请求的完成、超时和错误事件
 * 确保异步请求的日志正确记录
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public class AsyncLoggingListener implements AsyncListener {

    private final ReqLogMessage.ReqLogMessageBuilder logBuilder;
    private final Long startTime;
    private final String method;
    private final String path;

    /**
     * 构造函数
     *
     * @param logBuilder 日志构建器
     * @param startTime 请求开始时间
     * @param method    HTTP 方法
     * @param path      请求路径
     */
    public AsyncLoggingListener(ReqLogMessage.ReqLogMessageBuilder logBuilder, Long startTime, String method, String path) {
        this.logBuilder = logBuilder;
        this.startTime = startTime;
        this.method = method;
        this.path = path;
    }

    @Override
    public void onComplete(AsyncEvent event) {
        try {
            AsyncContext asyncCtx = event.getAsyncContext();
            if (asyncCtx == null) {
                return;
            }

            HttpServletResponse response = (HttpServletResponse) asyncCtx.getResponse();
            if (response == null) {
                return;
            }

            // 计算总耗时
            long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0L);

            // 构建完成日志
            ReqLogMessage message = logBuilder
                    .method(method)
                    .path(path)
                    .status(response.getStatus())
                    .asyncRequest(true)
                    .asyncType("ASYNC_CONTEXT")
                    .asyncCompletionStatus("COMPLETED")
                    .responseBody("[ASYNC_COMPLETED]")
                    .duration(duration)
                    .build();

            // 在当前线程（可能是异步线程）记录日志
            StructuredLogger.logReq(() -> message);

        } catch (Exception e) {
            log.error("[ASYNC] Failed to log async request completion: {}", e.getMessage());
        }
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        try {
            long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0L);

            ReqLogMessage message = logBuilder
                    .method(method)
                    .path(path)
                    .status(504)  // Gateway Timeout
                    .asyncRequest(true)
                    .asyncType("ASYNC_CONTEXT")
                    .asyncCompletionStatus("TIMEOUT")
                    .error("Async request timeout")
                    .duration(duration)
                    .build();

            StructuredLogger.logReq(() -> message);

            log.warn("[ASYNC] Timeout: {} {} - {}ms", method, path, duration);

        } catch (Exception e) {
            log.error("[ASYNC] Failed to log async request timeout: {}", e.getMessage());
        }
    }

    @Override
    public void onError(AsyncEvent event) {
        try {
            long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0L);
            Throwable throwable = event.getThrowable();

            ReqLogMessage message = logBuilder
                    .method(method)
                    .path(path)
                    .status(500)
                    .asyncRequest(true)
                    .asyncType("ASYNC_CONTEXT")
                    .asyncCompletionStatus("ERROR")
                    .error(throwable != null ? throwable.getMessage() : "Async error")
                    .duration(duration)
                    .build();

            StructuredLogger.logReq(() -> message);

            log.error("[ASYNC] Error: {} {} - {}ms", method, path, duration, throwable);

        } catch (Exception e) {
            log.error("[ASYNC] Failed to log async request error: {}", e.getMessage());
        }
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
        // 不需要处理
    }
}
