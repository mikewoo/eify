package com.eify.common.log.message;

import com.eify.common.log.model.LogHeader;
import com.eify.common.log.model.LogMessage;
import com.eify.common.log.model.LogType;
import com.eify.common.log.config.MqLogConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息日志消息（MSG）
 *
 * <p>支持多种消息队列和异步任务场景：
 * <ul>
 *   <li>Kafka 消息的生产和消费</li>
 *   <li>RocketMQ 消息的生产和消费</li>
 *   <li>RabbitMQ 消息的生产和消费</li>
 *   <li>Redis 消息</li>
 *   <li>异步任务</li>
 *   <li>事件通知</li>
 * </ul>
 *
 * <p>性能优化：
 * <ul>
 *   <li>消息体默认记录，可通过配置关闭</li>
 *   <li>支持采样记录（高频消息只记录部分）</li>
 *   <li>异步日志输出，不阻塞消息操作</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MsgLogMessage extends LogMessage {

    /**
     * 消息队列类型（扩展支持多种 MQ）
     */
    private MsgType msgType;

    /**
     * 操作类型：PRODUCE（生产）或 CONSUME（消费）
     */
    private OperationType operationType;

    /**
     * 主题名称（Kafka topic、RocketMQ topic 等）
     */
    private String topic;

    /**
     * 分区 ID
     */
    private Integer partition;

    /**
     * 偏移量（Kafka）
     */
    private Long offset;

    /**
     * 消息键（可选）
     */
    private String key;

    /**
     * 消息体（默认记录，可通过配置控制）
     * <p>可通过 {@link #forceRecordPayload} 强制记录或跳过
     */
    private Object payload;

    /**
     * 是否强制记录消息体（优先级高于配置）
     * <p>true：强制记录（即使配置关闭）
     * <p>false：强制不记录（即使配置开启）
     * <p>null：使用配置
     */
    private Boolean forceRecordPayload;

    /**
     * 消息体大小（字节）
     */
    private Integer payloadSize;

    /**
     * 消费者组 ID
     */
    private String consumerGroupId;

    /**
     * 消息状态：SENT、RECEIVED、PROCESSED、FAILED（兼容旧版本）
     * @deprecated 使用 processResult 替代
     */
    @Deprecated
    private String status;

    /**
     * 处理结果：SUCCESS、FAILED、RETRY
     */
    private ProcessResult processResult;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 错误堆栈（可选，仅失败时记录）
     */
    private String errorStack;

    /**
     * 处理耗时（毫秒）
     */
    private Long duration;

    /**
     * 生产时间戳（消息发送到 MQ 的时间）
     */
    private Long producerTime;

    /**
     * 消费时间戳（从 MQ 拉取消息的时间）
     */
    private Long consumerTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 是否采样记录
     */
    private Boolean sampled;

    /**
     * 消息时间戳
     */
    private Long timestamp;

    /**
     * 扩展字段（用于不同 MQ 的特定字段）
     */
    private java.util.Map<String, Object> extensions;

    /**
     * 构造函数（带 header）
     */
    public MsgLogMessage(LogHeader header) {
        this.header = header;
        if (this.header != null) {
            this.header.setLogType(LogType.MSG);
            if (this.duration != null) {
                this.header.setDuration(this.duration);
            }
        }
    }

    @Override
    public String getBodyJson() {
        // 根据配置决定是否记录消息体
        Object finalPayload = null;

        if (shouldRecordPayload()) {
            finalPayload = getProcessedPayload();
        }

        // 根据配置处理错误堆栈
        String finalErrorStack = errorStack;
        MqLogConfig config = MqLogConfig.getInstance();
        if (config != null && errorStack != null) {
            finalErrorStack = config.truncateStackTrace(errorStack);
        }

        return toJson(MsgBody.builder()
                .msgType(msgType != null ? msgType.name() : null)
                .operationType(operationType != null ? operationType.name() : null)
                .topic(topic)
                .partition(partition)
                .offset(offset)
                .key(key)
                .payload(finalPayload)
                .payloadSize(payloadSize)
                .consumerGroupId(consumerGroupId)
                .status(status)
                .processResult(processResult != null ? processResult.name() : null)
                .error(error)
                .errorStack(finalErrorStack)
                .duration(duration)
                .producerTime(producerTime)
                .consumerTime(consumerTime)
                .retryCount(retryCount)
                .sampled(sampled)
                .extensions(extensions)
                .build());
    }

    /**
     * 判断是否应该记录消息体
     * <p>优先级：forceRecordPayload > 全局配置
     *
     * @return 是否记录
     */
    private boolean shouldRecordPayload() {
        // 失败消息强制记录消息体
        boolean isFailed = processResult == ProcessResult.FAILED || error != null;

        if (forceRecordPayload != null) {
            return forceRecordPayload;
        }

        MqLogConfig config = MqLogConfig.getInstance();
        if (config != null) {
            return config.shouldRecordPayload(isFailed);
        }

        // 默认行为：记录消息体
        return true;
    }

    /**
     * 获取处理后的消息体（截断或保持原样）
     *
     * @return 处理后的消息体
     */
    private Object getProcessedPayload() {
        if (payload == null) {
            return null;
        }

        MqLogConfig config = MqLogConfig.getInstance();
        if (config == null) {
            return payload;
        }

        return config.truncatePayload(payload);
    }

    /**
     * 消息队列类型枚举（扩展支持多种 MQ）
     */
    public enum MsgType {
        /**
         * Kafka 消息
         */
        KAFKA("kafka", "Apache Kafka"),

        /**
         * RocketMQ 消息
         */
        ROCKETMQ("rocketmq", "Apache RocketMQ"),

        /**
         * RabbitMQ 消息
         */
        RABBITMQ("rabbitmq", "RabbitMQ"),

        /**
         * Redis 消息/发布订阅
         */
        REDIS("redis", "Redis Pub/Sub"),

        /**
         * 异步任务（线程池执行的任务）
         */
        ASYNC("async", "异步任务"),

        /**
         * 事件通知（应用内事件）
         */
        EVENT("event", "事件通知");

        private final String code;
        private final String description;

        MsgType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        /**
         * 消息生产（发送）
         */
        PRODUCE,

        /**
         * 消息消费（接收）
         */
        CONSUME
    }

    /**
     * 处理结果枚举
     */
    public enum ProcessResult {
        /**
         * 处理成功
         */
        SUCCESS,

        /**
         * 处理失败
         */
        FAILED,

        /**
         * 重试中
         */
        RETRY
    }

    /**
     * 消息日志 Body 结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgBody {
        private String msgType;
        private String operationType;
        private String topic;
        private Integer partition;
        private Long offset;
        private String key;
        private Object payload;
        private Integer payloadSize;
        private String consumerGroupId;
        private String status;
        private String processResult;
        private String error;
        private String errorStack;
        private Long duration;
        private Long producerTime;
        private Long consumerTime;
        private Integer retryCount;
        private Boolean sampled;
        private java.util.Map<String, Object> extensions;
    }
}
