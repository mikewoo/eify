package com.eify.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 *
 * <p>错误码按模块分段：
 * <ul>
 *   <li>1000-1999 通用</li>
 *   <li>2000-2999 Provider</li>
 *   <li>3000-3999 Agent</li>
 *   <li>4000-4999 Chat</li>
 *   <li>5000-5999 MCP</li>
 *   <li>6000-6999 Workflow</li>
 *   <li>7000-7999 Knowledge</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@Deprecated
public enum ResponseCode {

    // ========== 通用 (1000-1999) ==========
    SUCCESS(200, "success"),
    ERROR(1000, "系统错误"),
    PARAM_VALID_ERROR(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未登录"),
    FORBIDDEN(1003, "无权限"),
    NOT_FOUND(1004, "资源不存在"),
    TIMEOUT(1005, "请求超时"),
    TOO_MANY_REQUESTS(1006, "请求过于频繁"),

    // ========== Provider (2000-2999) ==========
    PROVIDER_NOT_FOUND(2000, "LLM 提供商不存在"),
    PROVIDER_CALL_FAILED(2001, "LLM 调用失败"),
    PROVIDER_TIMEOUT(2002, "LLM 超时"),
    PROVIDER_RATE_LIMIT(2003, "LLM 限流"),
    PROVIDER_CIRCUIT_OPEN(2004, "LLM 熔断器打开"),
    API_KEY_INVALID(2005, "API Key 无效"),

    // ========== Agent (3000-3999) ==========
    AGENT_NOT_FOUND(3000, "Agent 不存在"),
    AGENT_DISABLED(3001, "Agent 已禁用"),
    AGENT_CONFIG_INVALID(3002, "Agent 配置无效"),

    // ========== Chat (4000-4999) ==========
    CONVERSATION_NOT_FOUND(4000, "对话不存在"),
    MESSAGE_NOT_FOUND(4001, "消息不存在"),
    CONTEXT_TOO_LONG(4002, "上下文过长"),

    // ========== MCP (5000-5999) ==========
    MCP_SERVER_NOT_FOUND(5000, "MCP 服务器不存在"),
    MCP_TOOL_NOT_FOUND(5001, "MCP 工具不存在"),
    MCP_CALL_FAILED(5002, "MCP 调用失败"),

    // ========== Workflow (6000-6999) ==========
    WORKFLOW_NOT_FOUND(6000, "工作流不存在"),
    WORKFLOW_EXECUTION_FAILED(6001, "工作流执行失败"),
    WORKFLOW_CONFIG_INVALID(6002, "工作流配置无效"),

    // ========== Knowledge (7000-7999) ==========
    KNOWLEDGE_NOT_FOUND(7000, "知识库不存在"),
    KNOWLEDGE_DISABLED(7001, "知识库已禁用"),
    DOCUMENT_UPLOAD_FAILED(7002, "文档上传失败"),
    DOCUMENT_PARSE_FAILED(7003, "文档解析失败"),
    EMBEDDING_FAILED(7004, "向量化失败"),
    ;

    /**
     * 响应码
     */
    private final Integer code;

    /**
     * 响应消息
     */
    private final String message;
}
