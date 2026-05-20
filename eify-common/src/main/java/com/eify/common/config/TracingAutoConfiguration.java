package com.eify.common.config;

import brave.Tracer;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing 自动配置
 *
 * <p>注册 TracingFilter，确保每个 HTTP 请求都有正确的 traceId 和 spanId
 *
 * @author Claude
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
@ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnBean(Tracer.class)
    public FilterRegistrationBean<Filter> tracingFilterRegistration(Tracer braveTracer) {
        TracingFilter filter = new TracingFilter(braveTracer);

        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        registrationBean.setName("TracingFilter");

        return registrationBean;
    }
}
