package com.eify.workflow.domain.config;

/**
 * 节点配置 sealed interface，所有节点类型的配置都实现此接口。
 */
public sealed interface NodeConfig
        permits StartNodeConfig, EndNodeConfig, LlmNodeConfig, ApiNodeConfig,
                ConditionNodeConfig, CodeNodeConfig, ToolCallNodeConfig {

    /** 节点类型标识，与 workflow_node.type 字段对应 */
    String type();
}
