package com.eify.common.log.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 请求日志配置
 *
 * <p>控制请求日志的行为，包括：
 * <ul>
 *   <li>是否记录请求体</li>
 *   <li>是否记录响应体</li>
 *   <li>响应体记录策略（错误/成功）</li>
 *   <li>采样率配置</li>
 *   <li>敏感字段脱敏</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Component
public class ReqLogConfig {

    /**
     * 是否记录请求体（默认 true）
     */
    @Value("${req.logging.record-request:true}")
    private boolean recordRequest = true;

    /**
     * 是否记录响应体（默认 false，避免性能开销）
     */
    @Value("${req.logging.record-response:false}")
    private boolean recordResponse = false;

    /**
     * 响应体最大长度（默认 1000 字符）
     */
    @Value("${req.logging.max-response-length:1000}")
    private int maxResponseLength = 1000;

    /**
     * 错误响应是否完整记录（默认 true）
     */
    @Value("${req.logging.record-error-response:true}")
    private boolean recordErrorResponse = true;

    /**
     * 成功响应是否记录（默认 false，只记录摘要）
     */
    @Value("${req.logging.record-success-response:false}")
    private boolean recordSuccessResponse = false;

    /**
     * 成功响应摘要长度（默认 200 字符）
     */
    @Value("${req.logging.success-preview-length:200}")
    private int successPreviewLength = 200;

    /**
     * 成功响应采样率（默认 0.01，即 1%）
     */
    @Value("${req.logging.sampling-rate:0.01}")
    private double samplingRate = 0.01;

    /**
     * 是否启用异步请求日志（默认 true）
     */
    @Value("${req.logging.async-logging-enabled:true}")
    private boolean asyncLoggingEnabled = true;

    /**
     * 敏感字段名称（用于脱敏）
     */
    private static final Set<String> DEFAULT_SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd",
            "token", "accessToken", "refreshToken",
            "secret", "apiKey", "apiSecret",
            "idCard", "ssn", "socialSecurityNumber",
            "creditCard", "cvv",
            "pin", "otp"
    ));

    /**
     * 单例实例（用于静态访问）
     */
    private static ReqLogConfig instance;

    /**
     * 初始化单例
     */
    public ReqLogConfig() {
        instance = this;
    }

    /**
     * 获取配置实例（用于静态访问）
     *
     * @return 配置实例
     */
    public static ReqLogConfig getInstance() {
        return instance;
    }

    /**
     * 判断是否应该记录响应体
     *
     * @param status HTTP 状态码
     * @param isError 是否有错误
     * @return 是否记录
     */
    public boolean shouldRecordResponse(int status, boolean isError) {
        if (!recordResponse) {
            return false;
        }

        // 错误响应：根据配置决定
        if (isError || status >= 400) {
            return recordErrorResponse;
        }

        // 成功响应：根据配置和采样率决定
        if (recordSuccessResponse) {
            return true; // 完整记录
        }

        // 否则只采样记录
        return shouldSampleSuccess();
    }

    /**
     * 判断是否应该采样成功响应
     *
     * @return 是否采样
     */
    public boolean shouldSampleSuccess() {
        return Math.random() < samplingRate;
    }

    /**
     * 处理响应体（返回 JSON 对象或原始字符串）
     *
     * @param responseBody 原始响应体
     * @param isError 是否是错误响应
     * @return JSON 对象（如果是 JSON）或原始字符串
     */
    public Object processResponse(String responseBody, boolean isError) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }

        // 尝试解析为 JSON
        try {
            Object parsed = com.alibaba.fastjson2.JSON.parse(responseBody);
            if (parsed instanceof com.alibaba.fastjson2.JSONObject) {
                // 返回 JSONObject，会被序列化为 JSON 对象
                return parsed;
            }
        } catch (Exception e) {
            // 不是 JSON，返回原始字符串
        }

        // 不是 JSON 或解析失败，返回原始字符串
        return responseBody;
    }

    /**
     * 获取响应记录策略描述
     *
     * @return 策略描述
     */
    public String getResponseStrategy() {
        if (!recordResponse) {
            return "disabled";
        }
        if (recordSuccessResponse) {
            return "full";
        }
        if (samplingRate > 0) {
            return "sampling(" + (int)(samplingRate * 100) + "%)";
        }
        return "error-only";
    }

    /**
     * 检查字段是否敏感
     *
     * @param fieldName 字段名
     * @return 是否敏感
     */
    public static boolean isSensitiveField(String fieldName) {
        return fieldName != null && DEFAULT_SENSITIVE_FIELDS.contains(fieldName.toLowerCase());
    }
}
