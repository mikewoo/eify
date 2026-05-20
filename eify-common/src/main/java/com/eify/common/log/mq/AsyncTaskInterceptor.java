package com.eify.common.log.mq;

import com.eify.common.log.message.MsgLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;

/**
 * 异步任务日志拦截器
 *
 * <p>自动记录异步任务的执行日志：
 * <ul>
 *   <li>拦截 @Async 注解的方法</li>
 *   <li>记录任务开始和结束时间</li>
 *   <li>记录执行结果和耗时</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * &#64;Configuration
 * public class AsyncConfig implements AsyncConfigurer {
 *
 *     &#64;Bean
 *     public AsyncTaskInterceptor asyncTaskInterceptor() {
 *         return new AsyncTaskInterceptor();
 *     }
 *
 *     &#64;Override
 *     public Executor getAsyncExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         // 包装 executor 以记录日志
 *         executor.setTaskDecorator(runnable -> {
 *             MsgLogContext context = MsgLogContext.builder()
 *                 .msgType(MsgLogMessage.MsgType.ASYNC)
 *                 .operationType(MsgLogMessage.OperationType.CONSUME)
 *                 .build();
 *             return AsyncTaskInterceptor.wrap(runnable, context);
 *         });
 *         return executor;
 *     }
 * }
 * </pre>
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public class AsyncTaskInterceptor extends MsgLogInterceptor {

    /**
     * 任务名称前缀
     */
    private static final String TASK_NAME_PREFIX = "async-task:";

    @Override
    protected MsgLogContext buildContext(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Class<?> targetClass = AopUtils.getTargetClass(invocation.getThis());
        String methodName = method.getName();
        String className = targetClass.getSimpleName();

        // 生成任务名称
        String taskName = TASK_NAME_PREFIX + className + "." + methodName;

        // 生成 key（使用方法签名）
        String key = className + "." + methodName;

        return MsgLogContext.builder()
                .msgType(MsgLogMessage.MsgType.ASYNC)
                .operationType(MsgLogMessage.OperationType.CONSUME)
                .topic(taskName)
                .key(key)
                .extension("methodName", methodName)
                .extension("className", className)
                .extension("parameters", getParameterSummary(invocation))
                .build();
    }

    @Override
    protected MsgLogMessage.MsgType getMsgType() {
        return MsgLogMessage.MsgType.ASYNC;
    }

    @Override
    protected MsgLogMessage.OperationType getOperationType() {
        return MsgLogMessage.OperationType.CONSUME;
    }

    /**
     * 获取参数摘要
     */
    private String getParameterSummary(MethodInvocation invocation) {
        Object[] args = invocation.getArguments();
        if (args == null || args.length == 0) {
            return "no-args";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg instanceof String) {
                String str = (String) arg;
                sb.append("\"").append(truncate(str, 50)).append("\"");
            } else {
                sb.append(arg.getClass().getSimpleName());
            }
        }
        return sb.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 包装 Runnable 以记录日志
     */
    public static Runnable wrap(Runnable runnable, MsgLogContext context) {
        return () -> {
            long startTime = System.currentTimeMillis();
            context.setStartTime(startTime);

            try {
                runnable.run();
                context.setResult(MsgLogMessage.ProcessResult.SUCCESS);
            } catch (Exception e) {
                context.setResult(MsgLogMessage.ProcessResult.FAILED);
                context.setError(e.getMessage());
                throw e;
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                context.setDuration(duration);

                // 记录日志
                logContext(context);
            }
        };
    }

    /**
     * 记录上下文
     */
    private static void logContext(MsgLogContext context) {
        try {
            MsgLogMessage message = MsgLogMessage.builder()
                    .msgType(context.getMsgType())
                    .operationType(context.getOperationType())
                    .topic(context.getTopic())
                    .key(context.getKey())
                    .processResult(context.getResult())
                    .error(context.getError())
                    .duration(context.getDuration())
                    .producerTime(context.getStartTime())
                    .consumerTime(context.getStartTime())
                    .extensions(context.getExtensions())
                    .build();

            com.eify.common.log.util.StructuredLogger.logMsg(() -> message);
        } catch (Exception e) {
            log.debug("Failed to log async task", e);
        }
    }
}
