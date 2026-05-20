package com.eify.chat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建对话请求
 * <p>
 * agentId 和 workflowId 至少提供一个
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建对话请求")
public class CreateConversationRequest {

    @Schema(description = "Agent ID（与 workflowId 至少选一）")
    private Long agentId;

    @Schema(description = "工作流 ID（与 agentId 至少选一）")
    private Long workflowId;

    @Schema(description = "对话标题（可选，留空自动生成）")
    private String title;
}
