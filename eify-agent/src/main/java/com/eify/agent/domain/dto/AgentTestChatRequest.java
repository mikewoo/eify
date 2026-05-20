package com.eify.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 测试对话请求
 */
@Data
public class AgentTestChatRequest {

    /**
     * 测试消息
     */
    @NotBlank(message = "测试消息不能为空")
    @Size(min = 1, max = 2000, message = "消息长度在 1 到 2000 个字符")
    private String message;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 覆盖默认模型（可选）
     */
    private Long overrideProviderId;

    /**
     * 覆盖默认模型标识（可选）
     */
    private String overrideModel;
}
