package com.eify.knowledge.config;

import com.eify.common.context.CurrentContext;

/**
 * 将 CurrentContext (ThreadLocal) 从父线程传播到线程池工作线程。
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
