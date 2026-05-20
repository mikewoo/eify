package com.eify.provider.domain.dto;

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
public class ChatMessage {

    private String role;

    private String content;

    /**
     * 工具调用列表（assistant 角色，当 LLM 决定调用工具时）
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具调用 ID（tool 角色，关联到 assistant 的 tool_call）
     */
    private String toolCallId;

    /**
     * 工具名称（tool 角色，与 toolCallId 一起使用）
     */
    private String name;

    /**
     * 向后兼容：2 参数构造函数
     */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role("system")
                .content(content)
                .build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .build();
    }

    /**
     * 创建带工具调用的助手消息
     */
    public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
        return ChatMessage.builder()
                .role("assistant")
                .toolCalls(toolCalls)
                .build();
    }

    /**
     * 创建工具执行结果消息
     */
    public static ChatMessage tool(String toolCallId, String name, String content) {
        return ChatMessage.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(content)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> arguments;
    }
}
