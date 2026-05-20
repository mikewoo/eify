package com.eify.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP Server 连通性测试结果")
public class ConnectionTestResult {

    @Schema(description = "是否连接成功")
    private boolean success;

    @Schema(description = "耗时（毫秒）")
    private long latencyMs;

    @Schema(description = "发现的工具数量")
    private int toolCount;

    @Schema(description = "发现的工具名称列表")
    private List<String> toolNames;

    @Schema(description = "错误信息（失败时）")
    private String errorMessage;

    public static ConnectionTestResult success(long latencyMs, int toolCount, List<String> toolNames) {
        return ConnectionTestResult.builder()
                .success(true)
                .latencyMs(latencyMs)
                .toolCount(toolCount)
                .toolNames(toolNames)
                .build();
    }

    public static ConnectionTestResult failure(long latencyMs, String errorMessage) {
        return ConnectionTestResult.builder()
                .success(false)
                .latencyMs(latencyMs)
                .toolCount(0)
                .toolNames(List.of())
                .errorMessage(errorMessage)
                .build();
    }
}
