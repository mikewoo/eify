package com.eify.chat.service;

import com.eify.chat.domain.dto.SendChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天服务接口
 * <p>
 * 提供流式对话功能，支持：
 * <ul>
 *   <li>创建新会话或继续已有会话</li>
 *   <li>SSE 流式响应</li>
 *   <li>上下文管理（滑动窗口策略）</li>
 *   <li>异常处理（超时、断线、错误）</li>
 * </ul>
 */
public interface ChatService {

    /**
     * 发送消息（流式响应）
     * <p>
     * 业务流程：
     * <ol>
     *   <li>获取或创建会话</li>
     *   <li>保存用户消息（独立事务）</li>
     *   <li>构建上下文（系统提示词 + 历史消息 + 当前消息）</li>
     *   <li>调用 LLM Provider（流式）</li>
     *   <li>流式返回内容块</li>
     *   <li>保存 AI 响应（独立事务）</li>
     * </ol>
     * <p>
     * 异常处理：
     * <ul>
     *   <li>LLM 超时 → 触发 onTimeout 回调，发送 timeout 事件</li>
     *   <li>客户端断开 → catch IOException，停止 LLM 调用</li>
     *   <li>Send 失败 → 调用 completeWithError</li>
     * </ul>
     * <p>
     * 事务注意：
     * <ul>
     *   <li>不在本方法上加 @Transactional</li>
     *   <li>写消息操作拆成独立方法，使用独立事务</li>
     * </ul>
     *
     * @param userId  当前用户ID
     * @param request 发送聊天请求
     * @return SSE Emitter，用于流式响应
     */
    SseEmitter sendMessage(Long userId, SendChatRequest request);

    /**
     * 获取默认上下文轮数
     */
    int getDefaultContextRounds();
}
