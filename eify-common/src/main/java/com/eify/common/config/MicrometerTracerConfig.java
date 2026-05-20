package com.eify.common.config;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Micrometer Tracer 手动配置
 *
 * <p>确保 Micrometer Tracing 的 Tracer bean 被正确创建
 *
 * @author Claude
 * @since 1.0.0
 */
@Configuration
public class MicrometerTracerConfig {

    private static final Logger log = LoggerFactory.getLogger(MicrometerTracerConfig.class);

    @PostConstruct
    public void init() {
        log.info("========== MicrometerTracerConfig LOADED ==========");
    }

    @Bean
    public ThreadLocalCurrentTraceContext currentTraceContext() {
        log.info("Creating ThreadLocalCurrentTraceContext bean");
        return ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(brave.context.slf4j.MDCScopeDecorator.newBuilder().build())
                .build();
    }

    @Bean
    public Tracing tracing(ThreadLocalCurrentTraceContext currentTraceContext) {
        log.info("Creating Tracing bean");
        return Tracing.newBuilder()
                .currentTraceContext(currentTraceContext)
                .build();
    }

    @Bean
    public Tracer braveTracer(Tracing tracing) {
        log.info("Creating brave.Tracer bean: {}", tracing.tracer());
        return tracing.tracer();
    }

    @Bean
    public io.micrometer.tracing.Tracer micrometerTracer(Tracer braveTracer, ThreadLocalCurrentTraceContext currentTraceContext) {
        log.info("Creating io.micrometer.tracing.Tracer bean");
        BraveCurrentTraceContext braveContext = new BraveCurrentTraceContext(currentTraceContext);
        return new BraveTracer(braveTracer, braveContext);
    }
}
