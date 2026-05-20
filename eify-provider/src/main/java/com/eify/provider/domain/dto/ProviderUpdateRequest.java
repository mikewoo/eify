package com.eify.provider.domain.dto;

import com.eify.provider.constant.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 供应商更新请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "供应商更新请求")
public class ProviderUpdateRequest {

    /**
     * 供应商名称
     */
    @Schema(description = "供应商名称", example = "openai-official")
    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String name;

    /**
     * 供应商类型
     */
    @Schema(description = "供应商类型", example = "OPENAI")
    private ProviderType type;

    /**
     * API 基础地址
     */
    @Schema(description = "API 基础地址", example = "https://api.openai.com/v1")
    @Size(max = 500, message = "API 基础地址长度不能超过500")
    private String baseUrl;

    /**
     * 鉴权配置
     */
    @Schema(description = "鉴权配置（JSON 格式）", example = "{\"api_key\": \"sk-xxx\"}")
    private Map<String, Object> authConfig;

    /**
     * 启用状态：0=禁用，1=启用
     */
    @Schema(description = "启用状态（0=禁用，1=启用）", example = "1")
    private Integer enabled;
}
