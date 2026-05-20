package com.eify.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP Server 更新请求")
public class McpServerUpdateRequest {

    @Size(max = 100, message = "服务器名称长度不能超过100")
    @Schema(description = "服务器名称")
    private String name;

    @Size(max = 500, message = "Endpoint 长度不能超过500")
    @Schema(description = "MCP Server URL")
    private String endpoint;

    @Schema(description = "启用状态：0=禁用，1=启用")
    private Integer enabled;
}
