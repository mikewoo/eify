package com.eify.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
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
public enum ErrorCode {

    // ========== 通用错误 (1000-1999) ==========
    SUCCESS(200, "操作成功"),

    SYSTEM_ERROR(1000, "系统内部错误"),
    PARAM_ERROR(1001, "参数错误"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),
    TIMEOUT(1005, "请求超时"),
    TOO_MANY_REQUESTS(1006, "请求过于频繁"),
    DUPLICATE_REQUEST(1007, "重复请求"),

    // ========== Provider (2000-2999) ==========
    PROVIDER_NOT_FOUND(2000, "模型提供商不存在"),
    PROVIDER_CALL_FAILED(2001, "模型调用失败"),
    PROVIDER_TIMEOUT(2002, "模型调用超时"),
    PROVIDER_RATE_LIMIT(2003, "模型调用限流"),
    PROVIDER_CIRCUIT_OPEN(2004, "模型熔断器已打开"),
    API_KEY_INVALID(2005, "API Key 无效"),
    MODEL_NOT_SUPPORTED(2006, "不支持的模型"),

    // ========== Agent (3000-3999) ==========
    AGENT_NOT_FOUND(3000, "Agent 不存在"),
    AGENT_DISABLED(3001, "Agent 已禁用"),
    AGENT_CONFIG_INVALID(3002, "Agent 配置无效"),
    AGENT_NAME_DUPLICATE(3003, "Agent 名称已存在"),

    // ========== Chat (4000-4999) ==========
    CONVERSATION_NOT_FOUND(4000, "对话不存在"),
    MESSAGE_NOT_FOUND(4001, "消息不存在"),
    CONTEXT_TOO_LONG(4002, "上下文过长"),
    SSE_CLOSED(4003, "SSE 连接已关闭"),
    SSE_TIMEOUT(4004, "SSE 连接超时"),

    // ========== MCP (5000-5999) ==========
    MCP_SERVER_NOT_FOUND(5000, "MCP 服务器不存在"),
    MCP_SERVER_OFFLINE(5001, "MCP 服务器离线"),
    MCP_TOOL_NOT_FOUND(5002, "MCP 工具不存在"),
    MCP_CALL_FAILED(5003, "MCP 工具调用失败"),
    MCP_SERVER_HAS_BINDINGS(5004, "MCP 服务器有 Agent 绑定，无法删除"),
    MCP_TOOL_LIMIT_EXCEEDED(5005, "单个 Agent 最多绑定 10 个 MCP 工具"),

    // ========== Workflow (6000-6999) ==========
    WORKFLOW_NOT_FOUND(6000, "工作流不存在"),
    WORKFLOW_EXECUTION_FAILED(6001, "工作流执行失败"),
    WORKFLOW_CONFIG_INVALID(6002, "工作流配置无效"),
    WORKFLOW_DISABLED(6003, "工作流已禁用"),
    WORKFLOW_NAME_DUPLICATE(6004, "工作流名称已存在"),
    WORKFLOW_EXECUTION_NOT_FOUND(6005, "执行记录不存在"),
    WORKFLOW_NODE_EXECUTION_FAILED(6006, "节点执行失败"),
    WORKFLOW_EXECUTION_CANCELLED(6007, "执行已取消"),
    WORKFLOW_EXPRESSION_ERROR(6008, "条件表达式求值失败"),

    // ========== Auth (8000-8999) ==========
    USER_NOT_FOUND(8000, "用户不存在"),
    USER_DISABLED(8001, "用户已被禁用"),
    USERNAME_OR_EMAIL_EXISTS(8002, "用户名或邮箱已存在"),
    PASSWORD_INCORRECT(8003, "密码错误"),
    TOKEN_EXPIRED(8004, "登录已过期，请重新登录"),
    TOKEN_INVALID(8005, "令牌无效"),
    TOKEN_REUSE_DETECTED(8015, "检测到令牌重用，已撤销所有会话，请重新登录"),
    WORKSPACE_NOT_FOUND(8006, "工作空间不存在"),
    NOT_WORKSPACE_MEMBER(8007, "不是该工作空间成员"),
    WORKSPACE_ACCESS_DENIED(8008, "无权访问该工作空间"),
    INVITE_CODE_INVALID(8009, "邀请码无效"),
    INVITE_CODE_EXPIRED(8010, "邀请码已过期"),
    INVITE_CODE_EXHAUSTED(8011, "邀请码使用次数已用完"),
    ALREADY_WORKSPACE_MEMBER(8012, "已是该工作空间成员"),
    CANNOT_REMOVE_OWNER(8013, "不能移除工作空间拥有者"),
    CANNOT_REMOVE_SELF(8014, "不能移除自己"),
    WORKSPACE_NAME_DUPLICATE(8015, "工作空间名称已存在"),
    CANNOT_LEAVE_AS_OWNER(8016, "拥有者不能退出工作空间，请先转让拥有者或删除工作空间"),

    // ========== Knowledge (7000-7999) ==========
    KNOWLEDGE_NOT_FOUND(7000, "知识库不存在"),
    KNOWLEDGE_DISABLED(7001, "知识库已禁用"),
    DOCUMENT_UPLOAD_FAILED(7002, "文档上传失败"),
    DOCUMENT_PARSE_FAILED(7003, "文档解析失败"),
    EMBEDDING_FAILED(7004, "向量化失败"),
    KNOWLEDGE_NAME_DUPLICATE(7005, "知识库名称已存在"),
    DOCUMENT_NOT_FOUND(7006, "文档不存在"),
    EMBEDDING_MODEL_NOT_AVAILABLE(7007, "选定的嵌入模型不可用，请检查供应商配置"),
    ;

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误描述
     */
    private final String message;

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode of(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
