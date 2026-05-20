package com.eify.chat.controller;

import com.eify.common.context.CurrentContext;
import com.eify.chat.domain.dto.ConversationResponse;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.domain.dto.SendChatRequest;
import com.eify.chat.service.ChatService;
import com.eify.chat.service.ConversationService;
import com.eify.chat.service.MessageService;
import com.eify.common.error.ErrorCode;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

/**
 * 聊天控制器
 * <p>
 * 提供流式对话接口
 */
@Tag(name = "聊天管理", description = "流式对话接口")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final JdbcTemplate jdbcTemplate;

    public ChatController(ChatService chatService, ConversationService conversationService, MessageService messageService, JdbcTemplate jdbcTemplate) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发送消息（流式响应）
     * <p>
     * SSE 事件类型：
     * <ul>
     *   <li>message - 内容块：{"content":"部分文本","done":false}</li>
     *   <li>complete - 完成：{"done":true,"usage":{...},"finishReason":"stop"}</li>
     *   <li>error - 错误：{"error":"错误信息"}</li>
     *   <li>timeout - 超时：{"timeout":true,"message":"超时信息"}</li>
     * </ul>
     * <p>
     * 使用示例：
     * <pre>
     * const eventSource = new EventSource('/api/v1/chat/send', {
     *   method: 'POST',
     *   headers: {'Content-Type': 'application/json'},
     *   body: JSON.stringify({
     *     agentId: 1,
     *     content: '你好'
     *   })
     * });
     *
     * eventSource.addEventListener('message', (e) => {
     *   const data = JSON.parse(e.data);
     *   console.log(data.content); // 部分内容
     * });
     *
     * eventSource.addEventListener('complete', (e) => {
     *   const data = JSON.parse(e.data);
     *   console.log('完成:', data.usage);
     *   eventSource.close();
     * });
     *
     * eventSource.addEventListener('error', (e) => {
     *   const data = JSON.parse(e.data);
     *   console.error('错误:', data.error);
     *   eventSource.close();
     * });
     * </pre>
     *
     * @param request 发送聊天请求
     * @return SSE Emitter，用于流式响应
     */
    @Operation(summary = "发送消息", description = "流式返回 AI 响应，使用 Server-Sent Events")
    @PostMapping("/send")
    public SseEmitter sendMessage(
            @Parameter(description = "发送聊天请求", required = true)
            @Valid @RequestBody SendChatRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = com.eify.common.context.CurrentContext.getUserId();

        log.info("[ChatController] 接收消息 - userId={}, sessionId={}, agentId={}, contentLength={}",
                userId, request.getSessionId(), request.getAgentId(), request.getContent().length());

        return chatService.sendMessage(userId, request);
    }

    /**
     * 获取默认上下文轮数
     * <p>
     * 用于前端展示或配置
     *
     * @return 默认上下文轮数
     */
    @Operation(summary = "获取默认上下文轮数")
    @GetMapping("/context/default-rounds")
    public Result<Integer> getDefaultContextRounds() {
        return Result.success(chatService.getDefaultContextRounds());
    }

    /**
     * 健康检查（测试端点）
     */
    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        log.info("[ChatController] 健康检查被调用");
        return Result.success("ChatController is working");
    }

    /**
     * 创建新对话
     * <p>
     * 用于前端创建新的聊天会话
     *
     * @param request 创建请求
     * @param httpRequest HTTP 请求
     * @return 对话信息
     */
    @Operation(summary = "创建新对话", description = "创建新的聊天会话")
    @PostMapping("/conversations")
    public Result<ConversationResponse> createConversation(
            @Parameter(description = "创建对话请求", required = true)
            @Valid @RequestBody com.eify.chat.domain.dto.CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            Long userId = com.eify.common.context.CurrentContext.getUserId();

            log.info("[ChatController] 创建对话开始 - userId={}, agentId={}, workflowId={}, title={}",
                    userId, request.getAgentId(), request.getWorkflowId(), request.getTitle());

            Conversation conversation = conversationService.create(
                    userId, request.getAgentId(), request.getWorkflowId(), request.getTitle());

            log.info("[ChatController] 创建对话成功 - id={}", conversation.getId());

            ConversationResponse response = ConversationResponse.builder()
                    .id(conversation.getId())
                    .userId(conversation.getUserId())
                    .agentId(conversation.getAgentId())
                    .workflowId(conversation.getWorkflowId())
                    .title(conversation.getTitle())
                    .status(conversation.getStatus())
                    .createdAt(conversation.getCreatedAt())
                    .updatedAt(conversation.getUpdatedAt())
                    .build();

            return Result.success(response);
        } catch (Exception e) {
            log.error("[ChatController] 创建对话异常 - userId={}, agentId={}, error={}",
                    CurrentContext.getUserId(), request.getAgentId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取对话消息历史
     *
     * @param conversationId 对话ID
     * @param lastId 上一页最后一条记录的ID
     * @param lastTimestamp 上一页最后一条记录的时间
     * @param pageSize 每页大小
     * @return 消息列表
     */
    @Operation(summary = "获取对话消息历史", description = "获取指定对话的消息历史记录")
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<PageResult<com.eify.chat.domain.entity.Message>> getConversationMessages(
            @Parameter(description = "对话ID", required = true)
            @PathVariable Long conversationId,
            @Parameter(description = "上一页最后一条记录的ID")
            @RequestParam(required = false) Long lastId,
            @Parameter(description = "上一页最后一条记录的更新时间", example = "2024-01-01T12:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastTimestamp,
            @Parameter(description = "每页大小", example = "50")
            @RequestParam(defaultValue = "50") Integer pageSize
    ) {
        log.info("[ChatController] 获取对话消息历史 - conversationId={}", conversationId);

        // 验证对话是否存在
        try {
            conversationService.getById(conversationId);
        } catch (Exception e) {
            return Result.fail(com.eify.common.error.ErrorCode.NOT_FOUND);
        }

        // 获取消息（使用第一页查询）
        var messages = messageService.listByCursor(conversationId,
                new com.eify.common.dto.CursorPageRequest(lastId, pageSize));

        return Result.success(messages);
    }

    /**
     * 修复数据库 metadata 字段（临时开发用）
     */
    @Operation(summary = "修复数据库", description = "临时开发接口，修复 metadata 字段")
    @PostMapping("/fix-database")
    public Result<String> fixDatabase() {
        try {
            // 检查列是否允许 NULL
            String checkSql = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_SCHEMA = 'eify' AND TABLE_NAME = 'ai_chat_message' AND COLUMN_NAME = 'metadata'";
            String isNullable = jdbcTemplate.queryForObject(checkSql, String.class);

            if ("YES".equals(isNullable)) {
                return Result.success("数据库已经是正确的结构，无需修复");
            }

            // 修改字段允许 NULL
            String fixSql = "ALTER TABLE `ai_chat_message` " +
                           "MODIFY COLUMN `metadata` JSON NULL COMMENT '元数据：{\"model\":\"gpt-4\",\"latency_ms\":1234}'";
            jdbcTemplate.execute(fixSql);

            log.info("[ChatController] 数据库修复成功：metadata 字段已允许 NULL");
            return Result.success("数据库修复成功");
        } catch (Exception e) {
            log.error("[ChatController] 数据库修复失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }

}
