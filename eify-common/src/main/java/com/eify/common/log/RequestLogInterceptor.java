package com.eify.common.log;

import com.eify.common.log.config.ReqLogConfig;
import com.eify.common.log.util.StructuredLogger;
import com.eify.common.log.message.ReqLogMessage;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器
 * <p>
 * 记录每个请求的 method、path、status、耗时、响应体
 * 慢请求（>1s）标 WARN
 * 支持同步和异步请求的响应记录
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String RESPONSE_WRAPPER_ATTR = "responseWrapper";
    private static final String SLOW_REQUEST_THRESHOLD_MS = "1000";

    @Autowired(required = false)
    private ReqLogConfig reqLogConfig;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // 确保 MDC 中有 traceId
        if (MDC.get("traceId") == null) {
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
        }

        // 记录请求开始时间
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        // 检查是否是异步请求
        if (request.isAsyncStarted()) {
            handleAsyncRequest(request, response, ex);
            return;
        }

        // 同步请求处理
        handleSyncRequest(request, response, ex);
    }

    /**
     * 处理同步请求
     */
    private void handleSyncRequest(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0L);

        ReqLogConfig config = getReqLogConfig();
        ReqLogMessage.ReqLogMessageBuilder builder = ReqLogMessage.builder()
                .method(request.getMethod())
                .path(request.getRequestURI())
                .status(response.getStatus())
                .clientIp(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .duration(duration)
                .asyncRequest(false);

        // 处理响应体（由 Filter 设置）
        ResponseWrapper responseWrapper = (ResponseWrapper) request.getAttribute(RESPONSE_WRAPPER_ATTR);
        if (responseWrapper != null && shouldRecordResponse()) {
            responseWrapper.flushContent();
            String responseBody = responseWrapper.getCapturedResponse();
            boolean isError = ex != null || response.getStatus() >= 400;

            if (config != null && config.shouldRecordResponse(response.getStatus(), isError)) {
                Object processedResponse = config.processResponse(responseBody, isError);
                builder.responseBody(processedResponse)
                       .responseSize(responseWrapper.getResponseSize());
            }
        }

        // 如果有异常，添加错误信息
        if (ex != null) {
            builder.error(ex.getMessage());
        }

        ReqLogMessage message = builder.build();
        message.setDuration(duration);
        StructuredLogger.logReq(() -> message);

        // 慢请求额外告警
        if (duration > Long.parseLong(SLOW_REQUEST_THRESHOLD_MS)) {
            log.warn("SLOW REQUEST: {} {} {} - {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
        }
    }

    /**
     * 处理异步请求
     */
    private void handleAsyncRequest(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        ReqLogConfig config = getReqLogConfig();
        if (!config.isAsyncLoggingEnabled()) {
            return;
        }

        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);

        // 构建日志构建器，在异步完成时使用
        ReqLogMessage.ReqLogMessageBuilder builder = ReqLogMessage.builder()
                .method(request.getMethod())
                .path(request.getRequestURI())
                .clientIp(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .asyncRequest(true);

        // 如果有异常，添加错误信息
        if (ex != null) {
            builder.error(ex.getMessage());
        }

        // 添加异步监听器
        try {
            AsyncContext asyncContext = request.getAsyncContext();
            asyncContext.addListener(new AsyncLoggingListener(builder, startTime, request.getMethod(), request.getRequestURI()));
        } catch (Exception e) {
            log.error("Failed to add async logging listener: {}", e.getMessage());
        }

        // 慢请求额外告警
        long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0L);
        if (duration > Long.parseLong(SLOW_REQUEST_THRESHOLD_MS)) {
            log.warn("SLOW ASYNC REQUEST STARTED: {} {} - {}ms (may complete later)", request.getMethod(), request.getRequestURI(), duration);
        }
    }

    /**
     * 判断是否应该记录响应
     */
    private boolean shouldRecordResponse() {
        ReqLogConfig config = getReqLogConfig();
        return config != null && config.isRecordResponse();
    }

    /**
     * 获取 ReqLogConfig 实例
     */
    private ReqLogConfig getReqLogConfig() {
        if (reqLogConfig != null) {
            return reqLogConfig;
        }
        return ReqLogConfig.getInstance();
    }

    /**
     * 获取客户端真实 IP
     * 优先从代理头获取，否则使用 RemoteAddr
     *
     * @param request HttpServletRequest
     * @return 客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个 IP 的情况（X-Forwarded-For 可能包含多个 IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
