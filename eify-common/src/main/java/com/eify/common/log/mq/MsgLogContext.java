package com.eify.common.log.mq;

import com.eify.common.log.message.MsgLogMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * MSG 日志上下文
 *
 * <p>使用 ThreadLocal 存储当前消息的日志上下文
 *
 * @author Claude
 * @since 1.0.0
 */
public class MsgLogContext {

    /**
     * 线程本地上下文
     */
    private static final ThreadLocal<MsgLogContext> CONTEXTHolder = new ThreadLocal<>();

    /**
     * 消息类型
     */
    private MsgLogMessage.MsgType msgType;

    /**
     * 操作类型
     */
    private MsgLogMessage.OperationType operationType;

    /**
     * 主题
     */
    private String topic;

    /**
     * 分区
     */
    private Integer partition;

    /**
     * 偏移量
     */
    private Long offset;

    /**
     * 消息键
     */
    private String key;

    /**
     * 消息体
     */
    private Object payload;

    /**
     * 消息体大小
     */
    private Integer payloadSize;

    /**
     * 消费者组
     */
    private String consumerGroupId;

    /**
     * 开始时间
     */
    private Long startTime;

    /**
     * 结束时间
     */
    private Long endTime;

    /**
     * 耗时
     */
    private Long duration;

    /**
     * 处理结果
     */
    private MsgLogMessage.ProcessResult result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 扩展字段
     */
    private Map<String, Object> extensions;

    /**
     * 开始新的日志上下文
     */
    public static MsgLogContext begin(MsgLogContext context) {
        CONTEXTHolder.set(context);
        return context;
    }

    /**
     * 获取当前日志上下文
     */
    public static MsgLogContext get() {
        return CONTEXTHolder.get();
    }

    /**
     * 清除当前日志上下文
     */
    public static void clear() {
        CONTEXTHolder.remove();
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final MsgLogContext context = new MsgLogContext();

        public Builder msgType(MsgLogMessage.MsgType msgType) {
            context.msgType = msgType;
            return this;
        }

        public Builder operationType(MsgLogMessage.OperationType operationType) {
            context.operationType = operationType;
            return this;
        }

        public Builder topic(String topic) {
            context.topic = topic;
            return this;
        }

        public Builder partition(Integer partition) {
            context.partition = partition;
            return this;
        }

        public Builder offset(Long offset) {
            context.offset = offset;
            return this;
        }

        public Builder key(String key) {
            context.key = key;
            return this;
        }

        public Builder payload(Object payload) {
            context.payload = payload;
            return this;
        }

        public Builder payloadSize(Integer payloadSize) {
            context.payloadSize = payloadSize;
            return this;
        }

        public Builder consumerGroupId(String consumerGroupId) {
            context.consumerGroupId = consumerGroupId;
            return this;
        }

        public Builder startTime(Long startTime) {
            context.startTime = startTime;
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            context.extensions = extensions;
            return this;
        }

        public Builder extension(String key, Object value) {
            if (context.extensions == null) {
                context.extensions = new HashMap<>();
            }
            context.extensions.put(key, value);
            return this;
        }

        public Builder result(MsgLogMessage.ProcessResult result) {
            context.result = result;
            return this;
        }

        public Builder error(String error) {
            context.error = error;
            return this;
        }

        public Builder duration(Long duration) {
            context.duration = duration;
            return this;
        }

        public MsgLogContext build() {
            if (context.startTime == null) {
                context.startTime = System.currentTimeMillis();
            }
            return context;
        }
    }

    // Getters and Setters

    public MsgLogMessage.MsgType getMsgType() {
        return msgType;
    }

    public void setMsgType(MsgLogMessage.MsgType msgType) {
        this.msgType = msgType;
    }

    public MsgLogMessage.OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(MsgLogMessage.OperationType operationType) {
        this.operationType = operationType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Integer getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(Integer payloadSize) {
        this.payloadSize = payloadSize;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public MsgLogMessage.ProcessResult getResult() {
        return result;
    }

    public void setResult(MsgLogMessage.ProcessResult result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
}
