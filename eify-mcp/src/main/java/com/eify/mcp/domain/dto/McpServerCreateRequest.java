package com.eify.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP Server 创建请求")
public class McpServerCreateRequest {

    @NotBlank(message = "服务器名称不能为空")
    @Schema(description = "服务器名称", example = "订单查询服务")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    @Schema(description = "服务器描述", example = "用于查询订单状态和物流信息")
    private String description;

    @NotBlank(message = "Endpoint 不能为空")
    @Schema(description = "MCP Server URL", example = "http://localhost:8080/mcp")
    private String endpoint;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "启用状态：0=禁用，1=启用", example = "1")
    private Integer enabled;
}
