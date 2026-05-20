package com.eify.mcp.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP Server 响应对象")
public class McpServerResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "服务器名称")
    private String name;

    @Schema(description = "MCP Server URL")
    private String endpoint;

    @Schema(description = "启用状态：0=禁用，1=启用")
    private Integer enabled;

    @Schema(description = "工具数量（列表接口返回）")
    private Integer toolCount;

    @Schema(description = "工具列表（详情接口返回）")
    private List<McpToolResponse> tools;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "MCP 工具信息")
    public static class McpToolResponse {

        @Schema(description = "工具 ID")
        private Long id;

        @Schema(description = "工具名称")
        private String name;

        @Schema(description = "工具描述")
        private String description;

        @Schema(description = "输入参数 JSON Schema")
        private JsonNode inputSchema;
    }
}
