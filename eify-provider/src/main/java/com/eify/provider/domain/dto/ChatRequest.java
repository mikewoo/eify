package com.eify.provider.domain.dto;

import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String model;

    private List<ChatMessage> messages;

    /**
     * 工具定义列表（Function Calling）
     */
    private List<ToolDefinition> tools;

    @Builder.Default
    private Double temperature = 0.7;

    @Builder.Default
    private Integer maxTokens = 2000;

    @Builder.Default
    private Double topP = 1.0;

    @Builder.Default
    private Double frequencyPenalty = 0.0;

    @Builder.Default
    private Double presencePenalty = 0.0;

    private List<String> stop;

    private Map<String, Object> extraParams;

    public <T> T getParam(String key, Class<T> type, T defaultValue) {
        if (extraParams != null && extraParams.containsKey(key)) {
            Object value = extraParams.get(key);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return defaultValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String type;
        private FunctionDef function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionDef {
            private String name;
            private String description;
            private JsonNode parameters;
        }
    }
}
