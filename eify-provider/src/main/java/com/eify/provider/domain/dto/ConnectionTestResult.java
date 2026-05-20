package com.eify.provider.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 连通性测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 延迟（毫秒）
     */
    private long latencyMs;

    /**
     * 模型数量
     */
    private Integer modelCount;

    /**
     * 发现的模型名称列表
     */
    private List<String> modelNames;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建成功结果（不含模型名列表，向后兼容）
     */
    public static ConnectionTestResult success(long latencyMs, Integer modelCount) {
        return success(latencyMs, modelCount, null);
    }

    /**
     * 创建成功结果（含模型名列表）
     */
    public static ConnectionTestResult success(long latencyMs, Integer modelCount, List<String> modelNames) {
        return ConnectionTestResult.builder()
                .success(true)
                .latencyMs(latencyMs)
                .modelCount(modelCount)
                .modelNames(modelNames)
                .errorMessage(null)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ConnectionTestResult failure(long latencyMs, String errorMessage) {
        return ConnectionTestResult.builder()
                .success(false)
                .latencyMs(latencyMs)
                .modelCount(null)
                .errorMessage(errorMessage)
                .build();
    }
}
