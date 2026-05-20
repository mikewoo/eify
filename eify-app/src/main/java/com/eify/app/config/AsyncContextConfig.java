package com.eify.app.config;

import com.eify.common.context.CurrentContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * 注册 {@link TaskDecorator} bean，供 {@code ThreadPoolConfig} 中的线程池使用。
 * <p>
 * 确保 {@code CurrentContext} (userId、workspaceId) 在异步任务中可用。
 */
@Configuration
public class AsyncContextConfig {

    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return runnable -> {
            Long userId = CurrentContext.getUserId();
            Long workspaceId = CurrentContext.getWorkspaceId();
            return () -> {
                try {
                    CurrentContext.set(userId, workspaceId);
                    runnable.run();
                } finally {
                    CurrentContext.clear();
                }
            };
        };
    }
}
