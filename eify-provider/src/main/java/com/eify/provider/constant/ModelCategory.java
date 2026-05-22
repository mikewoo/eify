package com.eify.provider.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模型主类别枚举
 * <p>
 * 与 model_config.model_category (TINYINT) 列对应，使用 @EnumValue 做整数映射。
 * extra_params JSON 保留给细粒度能力标记（supports_streaming, supports_vision 等）。
 */
public enum ModelCategory {

    /** 文本对话 LLM */
    CHAT(0),

    /** 文本向量化 */
    EMBEDDING(1),

    /** RAG 重排序 */
    RERANK(2),

    /** 多模态（图/音/视频理解） */
    MULTIMODAL(3);

    @EnumValue
    @JsonValue
    private final int value;

    ModelCategory(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * 根据整数值获取枚举。
     */
    public static ModelCategory fromValue(int value) {
        for (ModelCategory c : values()) {
            if (c.value == value) return c;
        }
        return CHAT;
    }

    /**
     * 根据模型 ID 名称自动推断类别。
     * 同步模型时使用，未匹配的默认为 CHAT。
     */
    public static ModelCategory fromModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return CHAT;
        }
        String lower = modelId.toLowerCase();
        if (lower.contains("embed") || lower.startsWith("bge-") || lower.startsWith("e5-")
                || lower.startsWith("gte-") || lower.contains("stella")) {
            return EMBEDDING;
        }
        if (lower.contains("rerank")) {
            return RERANK;
        }
        return CHAT;
    }
}
