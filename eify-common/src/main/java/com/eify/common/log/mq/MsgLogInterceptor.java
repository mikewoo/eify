package com.eify.common.log.mq;

import com.eify.common.log.config.MqLogConfig;
import com.eify.common.log.util.StructuredLogger;
import com.eify.common.log.message.MsgLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * MSG 日志拦截器基类
 *
 * <p>提供自动记录消息日志的基础功能：
 * <ul>
 *   <li>支持异步任务（ASYNC）</li>
 *   <li>支持 Spring 事件（EVENT）</li>
 *   <li>支持消息队列（KAFKA/ROCKETMQ/RABBITMQ）</li>
 * </ul>
 *
 * <p>性能优化：
 * <ul>
 *   <li>异步日志记录，不阻塞业务</li>
 *   <li>支持采样记录</li>
 *   <li>可配置消息体记录</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public abstract class MsgLogInterceptor implements MethodInterceptor {

    /**
     * 日志记录线程池
     */
    private static final java.util.concurrent.ExecutorService LOGGING_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "msg-log-logger");
                t.setDaemon(true);
                return t;
            });

    /**
     * 慢消息阈值（毫秒）
     */
    protected long slowMessageThreshold = 1000;

    /**
     * 是否启用日志
     */
    protected boolean logEnabled = true;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!logEnabled) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        // 记录开始时间
        MsgLogContext context = MsgLogContext.begin(buildContext(invocation));

        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            context.setEndTime(startTime + duration);
            context.setDuration(duration);
            context.setResult(exception == null ? MsgLogMessage.ProcessResult.SUCCESS : MsgLogMessage.ProcessResult.FAILED);
            if (exception != null) {
                context.setError(exception.getMessage());
            }

            // 异步记录日志
            logAsync(context);
        }
    }

    /**
     * 构建日志上下文
     *
     * @param invocation 方法调用
     * @return 日志上下文
     */
    protected abstract MsgLogContext buildContext(MethodInvocation invocation);

    /**
     * 获取消息队列类型
     */
    protected abstract MsgLogMessage.MsgType getMsgType();

    /**
     * 获取操作类型
     */
    protected abstract MsgLogMessage.OperationType getOperationType();

    /**
     * 异步记录日志
     */
    protected void logAsync(MsgLogContext context) {
        // 判断是否需要记录
        if (!shouldLog(context)) {
            return;
        }

        LOGGING_EXECUTOR.submit(() -> {
            try {
                doLog(context);
            } catch (Exception e) {
                log.debug("Failed to log message", e);
            }
        });
    }

    /**
     * 判断是否应该记录日志
     */
    protected boolean shouldLog(MsgLogContext context) {
        MqLogConfig config = MqLogConfig.getInstance();
        if (config == null) {
            return true;
        }

        // 慢消息始终记录
        if (context.getDuration() != null && context.getDuration() > slowMessageThreshold) {
            return true;
        }

        // 失败消息始终记录
        if (context.getResult() == MsgLogMessage.ProcessResult.FAILED) {
            return true;
        }

        // 根据采样率决定
        return config.shouldSample(null);
    }

    /**
     * 执行日志记录
     */
    protected void doLog(MsgLogContext context) {
        MsgLogMessage.MsgType msgType = getMsgType();
        MsgLogMessage.OperationType operationType = getOperationType();

        MsgLogMessage.MsgLogMessageBuilder builder = MsgLogMessage.builder()
                .msgType(msgType)
                .operationType(operationType)
                .topic(context.getTopic())
                .partition(context.getPartition())
                .offset(context.getOffset())
                .key(context.getKey())
                .payload(context.getPayload())
                .payloadSize(context.getPayloadSize())
                .consumerGroupId(context.getConsumerGroupId())
                .processResult(context.getResult())
                .error(context.getError())
                .duration(context.getDuration())
                .producerTime(context.getStartTime())
                .consumerTime(context.getStartTime())
                .sampled(true);

        // 添加扩展字段
        if (context.getExtensions() != null && !context.getExtensions().isEmpty()) {
            builder.extensions(context.getExtensions());
        }

        StructuredLogger.logMsg(() -> builder.build());
    }

    /**
     * 设置慢消息阈值
     */
    public void setSlowMessageThreshold(long slowMessageThreshold) {
        this.slowMessageThreshold = slowMessageThreshold;
    }

    /**
     * 设置是否启用日志
     */
    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }
}
