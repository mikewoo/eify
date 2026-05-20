package com.eify.common.log.mq;

import com.eify.common.log.message.MsgLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.ApplicationEvent;

/**
 * Spring 事件监听器日志拦截器
 *
 * <p>自动记录 Spring 事件的发布和处理：
 * <ul>
 *   <li>拦截 @EventListener 注解的方法</li>
 *   <li>记录事件类型和内容</li>
 *   <li>记录处理结果和耗时</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public class EventListenerInterceptor extends MsgLogInterceptor {

    /**
     * 事件名称前缀
     */
    private static final String EVENT_NAME_PREFIX = "spring-event:";

    @Override
    protected MsgLogContext buildContext(MethodInvocation invocation) {
        Object[] args = invocation.getArguments();
        ApplicationEvent event = null;

        // 查找 ApplicationEvent 参数
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof ApplicationEvent) {
                    event = (ApplicationEvent) arg;
                    break;
                }
            }
        }

        String eventType = event != null ? event.getClass().getSimpleName() : "unknown";
        String eventName = EVENT_NAME_PREFIX + eventType;

        return MsgLogContext.builder()
                .msgType(MsgLogMessage.MsgType.EVENT)
                .operationType(MsgLogMessage.OperationType.CONSUME)
                .topic(eventName)
                .key(eventType)
                .payload(event != null ? event.toString() : null)
                .payloadSize(event != null ? event.toString().length() : 0)
                .extension("eventType", eventType)
                .extension("source", event != null ? String.valueOf(event.getSource()) : null)
                .build();
    }

    @Override
    protected MsgLogMessage.MsgType getMsgType() {
        return MsgLogMessage.MsgType.EVENT;
    }

    @Override
    protected MsgLogMessage.OperationType getOperationType() {
        return MsgLogMessage.OperationType.CONSUME;
    }
}
