package com.eify.chat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送聊天请求
 * <p>
 * 用于流式对话接口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送聊天请求")
public class SendChatRequest {

    /**
     * 会话ID（可选）
     * <p>
     * 不传则创建新会话，传入则继续已有会话
     */
    @Schema(description = "会话ID（可选，不传则创建新会话）", example = "123")
    private Long sessionId;

    /**
     * Agent ID（已有会话时可从会话中获取，新会话时与 workflowId 至少选一）
     */
    @Schema(description = "Agent ID（已有会话时可选）", example = "1")
    private Long agentId;

    /**
     * 工作流 ID（已有会话时可从会话中获取，新会话时与 agentId 至少选一）
     */
    @Schema(description = "工作流 ID（已有会话时可选）", example = "1")
    private Long workflowId;

    /**
     * 用户消息内容（必填）
     */
    @Schema(description = "用户消息内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，请介绍一下自己")
    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 上下文轮数（可选）
     * <p>
     * 加载最近多少轮对话作为上下文，默认 10 轮（20 条消息）
     */
    @Schema(description = "上下文轮数（默认10轮）", example = "10")
    private Integer contextRounds;
}
