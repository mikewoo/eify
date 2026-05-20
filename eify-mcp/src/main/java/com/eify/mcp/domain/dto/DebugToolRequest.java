package com.eify.mcp.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DebugToolRequest {

    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    @NotNull(message = "参数不能为 null")
    private Map<String, Object> arguments;
}
