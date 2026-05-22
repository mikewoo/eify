package com.eify.provider.domain.dto;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手动添加模型请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手动添加模型请求")
public class ModelCreateRequest {

    @Schema(description = "API 模型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "text-embedding-v4")
    @NotBlank(message = "模型标识不能为空")
    private String modelId;

    @Schema(description = "模型显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Qwen text-embedding-v4")
    @NotBlank(message = "模型显示名称不能为空")
    private String displayName;

    @Schema(description = "模型类别：0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模型类别不能为空")
    private Integer category;

    @Schema(description = "上下文窗口大小（可选）", example = "8192")
    private Integer contextSize;

    @Schema(description = "扩展参数 JSON（含 dimension、supports_streaming 等）", example = "{\"dimension\": 1024}")
    private JsonNode extraParams;

    @Schema(description = "启用状态（0=禁用，1=启用），默认 1", example = "1")
    private Integer enabled;
}
