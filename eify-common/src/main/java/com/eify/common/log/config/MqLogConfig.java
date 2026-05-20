package com.eify.common.log.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 消息队列日志配置
 *
 * <p>控制消息队列日志的行为，包括：
 * <ul>
 *   <li>是否记录消息体</li>
 *   <li>消息体最大长度</li>
 *   <li>是否记录完整堆栈</li>
 *   <li>采样率配置</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Component
public class MqLogConfig {

    /**
     * 是否记录消息体（默认 true）
     * <p>设置为 false 可以避免序列化开销，提高性能
     */
    @Value("${mq.logging.record-payload:true}")
    private boolean recordPayload = true;

    /**
     * 消息体最大长度（默认 1000 字符）
     * <p>超过此长度的消息体会被截断
     */
    @Value("${mq.logging.max-payload-length:1000}")
    private int maxPayloadLength = 1000;

    /**
     * 是否记录完整错误堆栈（默认 false）
     * <p>设置为 true 会记录完整堆栈，便于排查问题
     */
    @Value("${mq.logging.record-full-stack:false}")
    private boolean recordFullStack = false;

    /**
     * 堆栈最大深度（默认 50 行）
     * <p>当 recordFullStack = true 时有效
     */
    @Value("${mq.logging.max-stack-depth:50}")
    private int maxStackDepth = 50;

    /**
     * 默认采样率（默认 0.1，即 10%）
     * <p>可通过消息级别覆盖此配置
     */
    @Value("${mq.logging.sampling-rate:0.1}")
    private double samplingRate = 0.1;

    /**
     * 慢消息阈值（毫秒，默认 1000ms）
     * <p>超过此阈值的消息会被完整记录，不受采样率限制
     */
    @Value("${mq.logging.slow-message-threshold:1000}")
    private long slowMessageThreshold = 1000L;

    /**
     * 单例实例（用于静态访问）
     */
    private static MqLogConfig instance;

    /**
     * 初始化单例
     */
    public MqLogConfig() {
        instance = this;
    }

    /**
     * 获取配置实例（用于静态访问）
     *
     * @return 配置实例
     */
    public static MqLogConfig getInstance() {
        return instance;
    }

    /**
     * 截断消息体（如果超过最大长度）
     *
     * @param payload 原始消息体
     * @return 截断后的消息体
     */
    public String truncatePayload(Object payload) {
        if (payload == null) {
            return null;
        }

        String payloadStr;
        if (payload instanceof String) {
            payloadStr = (String) payload;
        } else {
            payloadStr = payload.toString();
        }

        if (payloadStr.length() <= maxPayloadLength) {
            return payloadStr;
        }

        return payloadStr.substring(0, maxPayloadLength) + "...(truncated)";
    }

    /**
     * 截断错误堆栈
     *
     * @param stackTrace 原始堆栈
     * @return 截断后的堆栈
     */
    public String truncateStackTrace(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }

        if (recordFullStack) {
            // 记录完整堆栈，但限制最大深度
            String[] lines = stackTrace.split("\\n");
            if (lines.length <= maxStackDepth) {
                return stackTrace;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maxStackDepth; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("... (").append(lines.length - maxStackDepth).append(" more lines)");
            return sb.toString();
        } else {
            // 只记录第一行（异常信息）
            int newlineIndex = stackTrace.indexOf('\n');
            return newlineIndex > 0 ? stackTrace.substring(0, newlineIndex) : stackTrace;
        }
    }

    /**
     * 判断是否应该采样
     *
     * @param customSamplingRate 自定义采样率（null 表示使用默认配置）
     * @return 是否采样
     */
    public boolean shouldSample(Double customSamplingRate) {
        double rate = customSamplingRate != null ? customSamplingRate : samplingRate;
        return Math.random() < rate;
    }

    /**
     * 判断是否应该记录消息体
     *
     * @param forceRecord 是否强制记录（如失败消息）
     * @return 是否记录
     */
    public boolean shouldRecordPayload(boolean forceRecord) {
        return recordPayload || forceRecord;
    }
}
