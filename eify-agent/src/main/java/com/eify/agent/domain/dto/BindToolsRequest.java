package com.eify.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BindToolsRequest {

    @NotNull(message = "工具 ID 列表不能为 null")
    @Size(max = 10, message = "单个 Agent 最多绑定 10 个 MCP 工具")
    private List<Long> toolIds;
}
