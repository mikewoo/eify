package com.eify.provider.domain.dto;

import com.eify.provider.constant.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 供应商创建请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "供应商创建请求")
public class ProviderCreateRequest {

    /**
     * 供应商名称
     */
    @Schema(description = "供应商名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "openai-official")
    @NotBlank(message = "供应商名称不能为空")
    private String name;

    /**
     * 供应商类型
     */
    @Schema(description = "供应商类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "OPENAI")
    @NotNull(message = "供应商类型不能为空")
    private ProviderType type;

    /**
     * API 基础地址（无需 /v1 后缀，系统自动添加）
     */
    @Schema(description = "API 基础地址（无需 /v1 后缀）", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.openai.com")
    @NotBlank(message = "API 基础地址不能为空")
    private String baseUrl;

    /**
     * 鉴权配置
     */
    @Schema(description = "鉴权配置（JSON 格式）", example = "{\"api_key\": \"sk-xxx\"}")
    @NotNull(message = "鉴权配置不能为空")
    private Map<String, Object> authConfig;

    /**
     * 启用状态：0=禁用，1=启用
     */
    @Schema(description = "启用状态（0=禁用，1=启用）", example = "1")
    @NotNull(message = "启用状态不能为空")
    private Integer enabled;
}
