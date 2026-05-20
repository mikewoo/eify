package com.eify.provider.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import com.eify.provider.constant.ProviderType;
import com.eify.common.handler.JsonNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("provider")
public class Provider extends BaseEntity implements WorkspaceAware {

    /**
     * 供应商名称，唯一
     */
    @TableField("name")
    private String name;

    @TableField("workspace_id")
    private Long workspaceId;

    /**
     * 供应商类型：OPENAI/ANTHROPIC/OLLAMA/OPENAI_COMPATIBLE
     */
    @TableField("type")
    private ProviderType type;

    /**
     * API 基础地址
     * <p>
     * 示例：
     * <ul>
     *   <li>OpenAI: https://api.openai.com/v1</li>
     *   <li>Anthropic: https://api.anthropic.com/v1</li>
     *   <li>Ollama: http://localhost:11434/v1</li>
     * </ul>
     */
    @TableField("base_url")
    private String baseUrl;

    /**
     * 鉴权配置（JSON 格式）
     * <p>
     * 根据 type 不同，结构不同：
     * <ul>
     *   <li>OPENAI: {"api_key": "sk-xxx"}</li>
     *   <li>ANTHROPIC: {"api_key": "sk-ant-xxx"}</li>
     *   <li>OLLAMA: {"require_auth": false}</li>
     *   <li>OPENAI_COMPATIBLE: {"api_key": "sk-xxx", "custom_headers": {...}}</li>
     * </ul>
     */
    @TableField(value = "auth_config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode authConfig;

    /**
     * 启用状态：0=禁用，1=启用
     */
    @TableField("enabled")
    private Integer enabled;
}
