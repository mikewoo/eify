package com.eify.common.config;

import com.eify.common.log.RequestLogInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web 日志配置
 * 注册请求日志拦截器
 */
@Configuration
public class WebLogConfig implements WebMvcConfigurer {

    /**
     * 注册 RequestLogInterceptor 为 Spring Bean
     * 这样可以注入 Tracer 依赖
     */
    @Bean
    public RequestLogInterceptor requestLogInterceptor() {
        return new RequestLogInterceptor();
    }

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor())
                .addPathPatterns("/**")
                // 排除静态资源和健康检查
                .excludePathPatterns(
                        "/static/**",
                        "/favicon.ico",
                        "/error",
                        "/actuator/**"
                );

        // 添加 favicon.ico 拦截器，直接返回 204 No Content
        registry.addInterceptor(faviconInterceptor())
                .addPathPatterns("/favicon.ico");
    }

    /**
     * Favicon 拦截器
     * 直接返回 204 No Content，避免 404 错误和日志记录
     */
    @Bean
    public HandlerInterceptor faviconInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return false;
            }
        };
    }
}
