package com.eify.provider.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 供应商类型枚举
 */
public enum ProviderType {

    /**
     * OpenAI 官方
     */
    OPENAI("OPENAI", "OpenAI 官方"),

    /**
     * Anthropic Claude
     */
    ANTHROPIC("ANTHROPIC", "Anthropic Claude"),

    /**
     * 本地 Ollama
     */
    OLLAMA("OLLAMA", "本地 Ollama"),

    /**
     * OpenAI 兼容接口（通义千问、智谱 GLM、DeepSeek 等）
     */
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE", "OpenAI 兼容接口");

    /**
     * 数据库存储值
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 描述
     */
    private final String description;

    ProviderType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据值获取枚举
     */
    public static ProviderType fromValue(String value) {
        for (ProviderType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ProviderType: " + value);
    }
}
