package com.eify.common.config;

import com.eify.common.context.CurrentContext;

/**
 * 将 {@link CurrentContext} (ThreadLocal) 从父线程传播到线程池工作线程。
 * <p>
 * 所有使用 {@code @Async} 或自定义线程池的模块，应在
 * {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
 * 中配置此 TaskDecorator，确保工作线程能访问 userId 和 workspaceId。
 */
public class ContextPropagatingTaskDecorator implements org.springframework.core.task.TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
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
    }
}
