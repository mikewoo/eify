package com.eify.provider.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.handler.JsonTypeHandler;
import com.eify.common.workspace.WorkspaceAware;
import com.eify.provider.constant.ModelCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型配置实体
 * <p>
 * 对应数据库表：model_config
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_config")
public class ModelConfig extends BaseEntity implements WorkspaceAware {

    /**
     * 所属供应商 ID
     */
    @TableField("provider_id")
    private Long providerId;

    @TableField("workspace_id")
    private Long workspaceId;

    /**
     * 展示名，如 GPT-4o
     */
    @TableField("name")
    private String name;

    /**
     * 调用时传给 API 的值
     * <p>
     * 示例：gpt-4o、claude-3-5-sonnet-20241022
     */
    @TableField("model_id")
    private String modelId;

    /**
     * 模型主类别：0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL
     */
    @TableField("model_category")
    private ModelCategory modelCategory;

    /**
     * 上下文窗口大小（token 数）
     * <p>
     * 示例：
     * <ul>
     *   <li>GPT-4o: 128000</li>
     *   <li>Claude 3.5 Sonnet: 200000</li>
     *   <li>Llama 3: 8192</li>
     * </ul>
     */
    @TableField("context_size")
    private Integer contextSize;

    /**
     * 模型级别扩展参数（JSON 格式）
     * <p>
     * 示例：
     * <pre>
     * {
     *   "max_tokens": 4096,
     *   "temperature": 0.7,
     *   "top_p": 0.9,
     *   "supports_streaming": true,
     *   "supports_function_call": true,
     *   "supports_vision": false,
     *   "default_system_prompt": "You are a helpful assistant."
     * }
     * </pre>
     */
    @TableField(value = "extra_params", typeHandler = JsonTypeHandler.class)
    private JsonNode extraParams;

    /**
     * 启用状态：0=禁用，1=启用
     */
    @TableField("enabled")
    private Integer enabled;
}
