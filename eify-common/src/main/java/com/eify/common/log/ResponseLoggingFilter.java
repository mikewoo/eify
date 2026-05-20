package com.eify.common.log;

import com.eify.common.log.config.ReqLogConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 响应日志过滤器
 * <p>
 * 包装 HttpServletResponse 以捕获响应体内容
 * 必须在 RequestLogInterceptor 之前执行
 *
 * @author Claude
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseLoggingFilter extends OncePerRequestFilter {

    private static final String RESPONSE_WRAPPER_ATTR = "responseWrapper";

    @Autowired(required = false)
    private ReqLogConfig reqLogConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        // 检查是否启用响应记录
        if (!shouldRecordResponse()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 包装响应对象
        ResponseWrapper responseWrapper = new ResponseWrapper(response);
        request.setAttribute(RESPONSE_WRAPPER_ATTR, responseWrapper);

        // 继续过滤器链
        filterChain.doFilter(request, responseWrapper);

        // 刷新内容以确保所有数据都被捕获
        responseWrapper.flushContent();
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
}
