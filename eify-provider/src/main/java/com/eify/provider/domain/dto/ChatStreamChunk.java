package com.eify.provider.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamChunk {

    private String content;

    private boolean done;

    /**
     * 完成原因：stop / tool_calls / length 等
     */
    private String finishReason;

    /**
     * 工具调用列表（finishReason=tool_calls 时有值）
     */
    private List<ToolCallChunk> toolCalls;

    private ChatResponse.Usage usage;

    public static ChatStreamChunk content(String content) {
        return ChatStreamChunk.builder()
                .content(content)
                .done(false)
                .build();
    }

    public static ChatStreamChunk done(ChatResponse.Usage usage, String finishReason) {
        return ChatStreamChunk.builder()
                .content("")
                .done(true)
                .usage(usage)
                .finishReason(finishReason)
                .build();
    }

    public static ChatStreamChunk done() {
        return ChatStreamChunk.builder()
                .content("")
                .done(true)
                .finishReason("stop")
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallChunk {
        private int index;
        private String id;
        private String name;
        private String arguments;
    }
}
