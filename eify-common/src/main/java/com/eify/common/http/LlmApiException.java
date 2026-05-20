package com.eify.common.http;

import lombok.Getter;

/**
 * LLM API 调用异常
 */
@Getter
public class LlmApiException extends RuntimeException {

    /**
     * 错误类型
     */
    private final ErrorType errorType;

    /**
     * 提供商代码
     */
    private final String providerCode;

    /**
     * HTTP 状态码
     */
    private final Integer httpStatus;

    /**
     * 错误信息
     */
    private final String errorMessage;

    public LlmApiException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.providerCode = null;
        this.httpStatus = null;
        this.errorMessage = message;
    }

    public LlmApiException(ErrorType errorType, String providerCode, Integer httpStatus, String message) {
        super(message);
        this.errorType = errorType;
        this.providerCode = providerCode;
        this.httpStatus = httpStatus;
        this.errorMessage = message;
    }

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 超时
         */
        TIMEOUT("超时"),

        /**
         * 认证失败
         */
        AUTH_FAILED("认证失败"),

        /**
         * 限流
         */
        RATE_LIMITED("限流"),

        /**
         * 熔断器打开
         */
        CIRCUIT_OPEN("熔断器打开"),

        /**
         * 网络错误
         */
        NETWORK_ERROR("网络错误"),

        /**
         * 未知错误
         */
        UNKNOWN("未知错误");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 判断是否为超时错误
     */
    public boolean isTimeout() {
        return errorType == ErrorType.TIMEOUT;
    }

    /**
     * 判断是否为认证失败
     */
    public boolean isAuthFailed() {
        return errorType == ErrorType.AUTH_FAILED;
    }

    /**
     * 判断是否为限流错误
     */
    public boolean isRateLimited() {
        return errorType == ErrorType.RATE_LIMITED;
    }

    /**
     * 判断是否为熔断器打开
     */
    public boolean isCircuitOpen() {
        return errorType == ErrorType.CIRCUIT_OPEN;
    }

    /**
     * 获取描述信息
     */
    public String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getDescription()).append("]");
        if (providerCode != null) {
            sb.append(" Provider: ").append(providerCode);
        }
        if (httpStatus != null) {
            sb.append(" HTTP ").append(httpStatus);
        }
        if (errorMessage != null) {
            sb.append(" ").append(errorMessage);
        }
        return sb.toString();
    }
}
