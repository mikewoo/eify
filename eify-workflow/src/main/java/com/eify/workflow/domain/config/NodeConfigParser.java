package com.eify.workflow.domain.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 节点配置解析器，根据 type 字符串将 JsonNode 反序列化为对应的 NodeConfig 子类。
 */
public final class NodeConfigParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Class<? extends NodeConfig>> REGISTRY = Map.of(
            "start",     StartNodeConfig.class,
            "end",       EndNodeConfig.class,
            "llm",       LlmNodeConfig.class,
            "api_call",  ApiNodeConfig.class,
            "condition", ConditionNodeConfig.class,
            "code",      CodeNodeConfig.class,
            "tool_call", ToolCallNodeConfig.class
    );

    /** 根据 type + JsonNode 反序列化到对应的 NodeConfig 子类 */
    public static NodeConfig parse(String type, JsonNode json) {
        if (json == null || json.isNull()) {
            return null;
        }
        Class<? extends NodeConfig> clazz = REGISTRY.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown node type: " + type);
        }
        return MAPPER.convertValue(json, clazz);
    }

    /** 将 NodeConfig 序列化为 JsonNode */
    public static JsonNode toJson(NodeConfig config) {
        if (config == null) {
            return null;
        }
        return MAPPER.valueToTree(config);
    }

    /** 检查 type 是否合法 */
    public static boolean isValidType(String type) {
        return REGISTRY.containsKey(type);
    }
}
