package com.eify.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置。
 * <p>
 * 若容器中存在 {@link TaskDecorator} bean（如 {@code ContextPropagatingTaskDecorator}），
 * 则自动应用到所有线程池，确保 {@code CurrentContext} 传播到工作线程。
 */
@Configuration
public class ThreadPoolConfig {

    private final TaskDecorator taskDecorator;

    public ThreadPoolConfig(ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        this.taskDecorator = taskDecoratorProvider.getIfAvailable();
    }

    /**
     * LLM 调用线程池
     * 用于外部 LLM API 调用，隔离不同提供商的请求
     */
    @Bean("llmExecutor")
    public Executor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("llm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        if (taskDecorator != null) executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }

    /**
     * 工作流执行线程池
     * 隔离工作流执行，防止阻塞 HTTP 请求线程。
     * 配合 5 分钟整体超时保护，4-10 线程可满足 20-50 人团队需求。
     */
    @Bean("workflowExecutor")
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("workflow-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        if (taskDecorator != null) executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }

    /**
     * 异步任务线程池
     * 用于日志异步写入、消息发送等非关键任务。
     * 保留 CallerRunsPolicy：非关键任务允许回退到调用线程执行。
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        if (taskDecorator != null) executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }

    /**
     * MCP 调用线程池
     * 隔离 MCP 外部调用，防止阻塞公共线程池
     */
    @Bean("mcpExecutor")
    public Executor mcpExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mcp-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        if (taskDecorator != null) executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 流式响应线程池
     * 隔离 SSE 流式处理，防止长时间占用 Tomcat 线程
     */
    @Bean("sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        if (taskDecorator != null) executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }
}
