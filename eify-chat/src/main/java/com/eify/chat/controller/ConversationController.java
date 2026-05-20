package com.eify.chat.controller;

import com.eify.chat.domain.dto.ConversationResponse;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.service.ConversationService;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话会话控制器
 * <p>
 * 使用游标分页 + 优化索引支持高效查询
 * <p>
 * 性能优化：
 * <ul>
 *   <li>使用索引 idx_user_status_updated_id 支持用户对话查询</li>
 *   <li>使用索引 idx_agent_updated_id 支持 Agent 对话查询</li>
 *   <li>游标分页，性能稳定</li>
 * </ul>
 */
@Tag(name = "对话管理", description = "对话会话查询接口（优化版）")
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 游标分页查询用户的对话列表
     * <p>
     * 性能优化：
     * <ul>
     *   <li>使用索引 idx_user_status_updated_id，支持按更新时间倒序</li>
     *   <li>游标分页，性能稳定</li>
     * </ul>
     * <p>
     * 使用示例：
     * <pre>
     * // 第一页
     * GET /api/v1/conversations/user/123?status=1&pageSize=20
     *
     * // 下一页（使用上一页返回的最后一个对话的 ID 和 updatedAt）
     * GET /api/v1/conversations/user/123?status=1&pageSize=20&lastId=456&lastTimestamp=2024-01-01T12:00:00
     * </pre>
     *
     * @param userId    用户ID
     * @param status    状态（0=已归档，1=进行中）
     * @param lastId         上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize       每页大小
     * @return 分页结果
     */
    @Operation(summary = "游标分页查询用户对话", description = "使用优化索引，按更新时间倒序")
    @GetMapping("/user/{userId}")
    public Result<PageResult<ConversationResponse>> listUserConversations(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "状态（0=已归档，1=进行中）", example = "1")
            @RequestParam(defaultValue = "1") Integer status,
            @Parameter(description = "上一页最后一条记录的ID")
            @RequestParam(required = false) Long lastId,
            @Parameter(description = "上一页最后一条记录的更新时间", example = "2024-01-01T12:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastTimestamp,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        log.info("游标分页查询用户对话：userId={}, status={}, lastId={}, pageSize={}",
                userId, status, lastId, pageSize);

        PageResult<Conversation> pageResult = conversationService.listUserConversations(
                userId, status, lastId, lastTimestamp, pageSize
        );

        // 转换为 Response
        List<ConversationResponse> responses = pageResult.getList().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResult<ConversationResponse> result = PageResult.ofCursor(
                responses,
                pageResult.getPageSize(),
                pageResult.getHasMore()
        );

        return Result.success(result);
    }

    /**
     * 游标分页查询 Agent 的对话列表
     * <p>
     * 使用示例：
     * <pre>
     * // 第一页
     * GET /api/v1/conversations/agent/456?pageSize=20
     *
     * // 下一页
     * GET /api/v1/conversations/agent/456?pageSize=20&lastId=789&lastTimestamp=2024-01-01T12:00:00
     * </pre>
     *
     * @param agentId   Agent ID
     * @param lastId         上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize       每页大小
     * @return 分页结果
     */
    @Operation(summary = "游标分页查询Agent对话", description = "查询使用该Agent的所有对话")
    @GetMapping("/agent/{agentId}")
    public Result<PageResult<ConversationResponse>> listAgentConversations(
            @Parameter(description = "Agent ID", required = true)
            @PathVariable Long agentId,
            @Parameter(description = "上一页最后一条记录的ID")
            @RequestParam(required = false) Long lastId,
            @Parameter(description = "上一页最后一条记录的更新时间", example = "2024-01-01T12:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastTimestamp,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        log.info("游标分页查询Agent对话：agentId={}, lastId={}, pageSize={}",
                agentId, lastId, pageSize);

        PageResult<Conversation> pageResult = conversationService.listAgentConversations(
                agentId, lastId, lastTimestamp, pageSize
        );

        // 转换为 Response
        List<ConversationResponse> responses = pageResult.getList().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResult<ConversationResponse> result = PageResult.ofCursor(
                responses,
                pageResult.getPageSize(),
                pageResult.getHasMore()
        );

        return Result.success(result);
    }

    /**
     * 根据ID查询对话
     *
     * @param id 对话ID
     * @return 对话详情
     */
    @Operation(summary = "根据ID查询对话")
    @GetMapping("/{id}")
    public Result<ConversationResponse> getById(
            @Parameter(description = "对话ID", required = true)
            @PathVariable Long id
    ) {
        log.info("查询对话详情：id={}", id);
        Conversation conversation = conversationService.getById(id);
        return Result.success(toResponse(conversation));
    }

    /**
     * 删除对话
     *
     * @param id 对话ID
     * @return 成功响应
     */
    @Operation(summary = "删除对话", description = "软删除对话及其相关消息")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "对话ID", required = true)
            @PathVariable Long id
    ) {
        log.info("删除对话：id={}", id);
        conversationService.delete(id);
        return Result.success();
    }

    /**
     * 实体转响应对象
     */
    private ConversationResponse toResponse(Conversation entity) {
        return ConversationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
