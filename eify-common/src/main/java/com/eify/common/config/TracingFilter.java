package com.eify.common.config;

import brave.Span;
import brave.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Micrometer Tracing HTTP Filter
 *
 * <p>为每个 HTTP 请求创建一个 span，确保 traceId 和 spanId 正确放入 MDC
 *
 * <p>这个 Filter 在 Spring MVC 的 DispatcherServlet 之前执行，
 * 确保在请求处理的早期阶段就有正确的 traceId 和 spanId
 *
 * <p>注意：在 Brave 中，根 span（root span）的 spanId 等于 traceId，这是正确的设计。
 * 只有子 span（child span）才有不同的 spanId。
 *
 * <p>要查看不同的 spanId，请：
 * 1. 在 Jaeger 中查看 trace tree（可以看到完整的父子关系）
 * 2. 在请求中携带 B3 trace headers（这样会创建子 span）
 *
 * @author Claude
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
@ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);

    private final Tracer braveTracer;

    public TracingFilter(Tracer braveTracer) {
        this.braveTracer = braveTracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 创建一个不可见的父 span（仅用于 traceId）
        // 这样子 span（实际记录的）会有不同的 spanId
        Span parentSpan = braveTracer.newTrace()
                .name("HTTP_ROOT")
                .start();

        // 将父 span 置于 scope 中
        Tracer.SpanInScope parentScope = braveTracer.withSpanInScope(parentSpan);

        try {
            // 在父 span 的 context 中创建子 span（这个会被记录）
            Span childSpan = braveTracer.newChild(parentSpan.context())
                    .name(request.getMethod() + " " + request.getRequestURI())
                    .tag("http.method", request.getMethod())
                    .tag("http.url", request.getRequestURI())
                    .start();

            // 将子 span 置于 scope 中（覆盖父 span）
            Tracer.SpanInScope childScope = braveTracer.withSpanInScope(childSpan);

            try {
                // 更新 MDC - traceId 来自父 span，spanId 来自子 span
                MDC.put("traceId", parentSpan.context().traceIdString());
                MDC.put("spanId", childSpan.context().spanIdString());

                if (log.isDebugEnabled()) {
                    log.debug("Spans started - traceId: {}, parentSpanId: {}, childSpanId: {}",
                            parentSpan.context().traceIdString(),
                            parentSpan.context().spanIdString(),
                            childSpan.context().spanIdString());
                }

                // 继续处理请求
                filterChain.doFilter(request, response);

            } finally {
                childScope.close();
                childSpan.finish();
            }
        } finally {
            // 清理 MDC - 确保在请求结束时清理
            MDC.remove("traceId");
            MDC.remove("spanId");

            parentScope.close();
            parentSpan.finish();
        }
    }
}
