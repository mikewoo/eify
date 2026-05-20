package com.eify.common.log.mq;

import com.eify.common.log.config.MqLogConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * MSG 日志自动配置
 *
 * <p>自动配置消息队列日志的拦截器和增强：
 * <ul>
 *   <li>异步任务（@Async）自动记录日志</li>
 *   <li>Spring 事件（@EventListener）自动记录日志</li>
 *   <li>事务方法（@Transactional）可选记录日志</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * // 在启动类上添加注解
 * &#64;SpringBootApplication
 * &#64;Import(MsgLogAutoConfiguration.class)
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 *
 * // 异步任务会自动记录日志
 * &#64;Async
 * public void asyncTask() {
 *     // 任务执行
 * }
 *
 * // 事件监听会自动记录日志
 * &#64;EventListener
 * public void handleEvent(CustomEvent event) {
 *     // 事件处理
 * }
 * </pre>
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
public class MsgLogAutoConfiguration implements AsyncConfigurer {

    @Autowired(required = false)
    private MqLogConfig mqLogConfig;

    /**
     * 配置异步任务执行器
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名称前缀
        executor.setThreadNamePrefix("async-");
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 包装任务以记录日志
        executor.setTaskDecorator(runnable -> {
            // 获取当前线程的 MDC
            String traceId = org.slf4j.MDC.get("traceId");
            String spanId = org.slf4j.MDC.get("spanId");

            // 创建日志上下文
            MsgLogContext context = MsgLogContext.builder()
                    .msgType(com.eify.common.log.message.MsgLogMessage.MsgType.ASYNC)
                    .operationType(com.eify.common.log.message.MsgLogMessage.OperationType.CONSUME)
                    .build();

            // 包装 runnable
            Runnable wrapped = AsyncTaskInterceptor.wrap(runnable, context);

            return () -> {
                try {
                    // 恢复 MDC
                    if (traceId != null) {
                        org.slf4j.MDC.put("traceId", traceId);
                    }
                    if (spanId != null) {
                        org.slf4j.MDC.put("spanId", spanId);
                    }

                    // 执行任务
                    wrapped.run();
                } finally {
                    // 清理 MDC
                    if (traceId != null) {
                        org.slf4j.MDC.remove("traceId");
                    }
                    if (spanId != null) {
                        org.slf4j.MDC.remove("spanId");
                    }
                }
            };
        });

        executor.initialize();
        return executor;
    }

    /**
     * 配置异步异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async task execution failed: method={}, params={}",
                    method.getName(), java.util.Arrays.toString(params), throwable);

            // 记录失败日志
            MsgLogContext context = MsgLogContext.builder()
                    .msgType(com.eify.common.log.message.MsgLogMessage.MsgType.ASYNC)
                    .operationType(com.eify.common.log.message.MsgLogMessage.OperationType.CONSUME)
                    .topic("async-task:" + method.getDeclaringClass().getSimpleName() + "." + method.getName())
                    .result(com.eify.common.log.message.MsgLogMessage.ProcessResult.FAILED)
                    .error(throwable.getMessage())
                    .build();

            try {
                com.eify.common.log.util.StructuredLogger.logMsg(() ->
                    com.eify.common.log.message.MsgLogMessage.builder()
                            .msgType(context.getMsgType())
                            .operationType(context.getOperationType())
                            .topic(context.getTopic())
                            .processResult(context.getResult())
                            .error(context.getError())
                            .build()
                );
            } catch (Exception e) {
                log.debug("Failed to log async error", e);
            }
        };
    }

    /**
     * 事件监听器拦截器 Advisor
     */
    @Bean
    public DefaultPointcutAdvisor eventListenerAdvisor() {
        // 创建切点：匹配所有带 @EventListener 注解的方法
        org.springframework.aop.support.annotation.AnnotationMatchingPointcut pointcut =
            new org.springframework.aop.support.annotation.AnnotationMatchingPointcut(
                EventListener.class, true
            );

        // 创建拦截器
        EventListenerInterceptor interceptor = new EventListenerInterceptor();

        // 配置拦截器
        if (mqLogConfig != null) {
            interceptor.setSlowMessageThreshold(mqLogConfig.getSlowMessageThreshold());
            interceptor.setLogEnabled(true);
        }

        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }

    /**
     * 初始化配置
     */
    @Bean
    public MsgLogAutoConfigurationInitializer initConfig() {
        log.info("MSG 日志自动配置已启用");

        if (mqLogConfig != null) {
            log.info("MSG 日志配置: recordPayload={}, maxPayloadLength={}, samplingRate={}",
                    mqLogConfig.isRecordPayload(),
                    mqLogConfig.getMaxPayloadLength(),
                    mqLogConfig.getSamplingRate());
        }

        return new MsgLogAutoConfigurationInitializer();
    }

    /**
     * 配置初始化器
     */
    public static class MsgLogAutoConfigurationInitializer {
        // 占位符，用于触发配置初始化
    }
}
