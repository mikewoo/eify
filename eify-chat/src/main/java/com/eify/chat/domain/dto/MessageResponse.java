package com.eify.chat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息响应对象")
public class MessageResponse {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "角色：user/assistant/system")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "token 数")
    private Integer tokenCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
