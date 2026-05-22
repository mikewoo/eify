package com.eify.provider.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;
import com.eify.provider.constant.ModelCategory;
import com.eify.provider.constant.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 供应商响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "供应商响应对象")
public class ProviderResponse {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "供应商名称", example = "openai-official")
    private String name;

    @Schema(description = "供应商类型", example = "OPENAI")
    private ProviderType type;

    @Schema(description = "API 基础地址", example = "https://api.openai.com")
    private String baseUrl;

    @Schema(description = "鉴权配置")
    private JsonNode authConfig;

    @Schema(description = "启用状态（0=禁用，1=启用）", example = "1")
    private Integer enabled;

    @Schema(description = "模型配置列表")
    private java.util.List<ModelConfigInfo> modelConfigs;

    @Schema(description = "健康状态")
    private ProviderHealthInfo health;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 模型配置信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "模型配置信息")
    public static class ModelConfigInfo {

        @Schema(description = "模型ID")
        private Long id;

        @Schema(description = "模型名称")
        private String modelName;

        @Schema(description = "显示名称")
        private String displayName;

        @Schema(description = "模型主类别", example = "EMBEDDING")
        private ModelCategory category;

        @Schema(description = "扩展参数")
        private JsonNode extraParams;
    }

    /**
     * 健康状态信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "健康状态信息")
    public static class ProviderHealthInfo {

        @Schema(description = "健康状态")
        private String status;

        @Schema(description = "最后探测时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastCheckAt;

        @Schema(description = "最后成功时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastSuccessAt;

        @Schema(description = "连续失败次数")
        private Integer failCount;

        @Schema(description = "最近一次延迟（毫秒）")
        private Integer latencyMs;

        @Schema(description = "最近失败原因")
        private String errorMessage;
    }
}
