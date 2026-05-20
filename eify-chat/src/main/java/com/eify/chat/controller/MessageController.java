package com.eify.chat.controller;

import com.eify.chat.domain.dto.MessageResponse;
import com.eify.chat.domain.dto.MessageTimeRangeRequest;
import com.eify.chat.domain.entity.Message;
import com.eify.chat.service.MessageService;
import com.eify.common.dto.CursorPageRequest;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息控制器
 * <p>
 * 使用游标分页 + 覆盖索引优化查询性能
 * <p>
 * 性能优化：
 * <ul>
 *   <li>使用覆盖索引，无需回表查询</li>
 *   <li>支持时间范围查询</li>
 *   <li>游标分页，性能稳定</li>
 * </ul>
 */
@Tag(name = "消息管理", description = "消息查询接口（优化版）")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 游标分页查询消息（按会话ID）
     * <p>
     * 性能优化：
     * <ul>
     *   <li>使用覆盖索引 idx_session_id_id_role_time，无需回表</li>
     *   <li>游标分页，性能稳定，深分页不降速</li>
     * </ul>
     * <p>
     * 使用示例：
     * <pre>
     * // 第一页
     * GET /api/v1/messages/cursor/session/123?pageSize=20
     *
     * // 下一页（使用上一页返回的最后一条消息的 ID）
     * GET /api/v1/messages/cursor/session/123?pageSize=20&lastId=456
     * </pre>
     *
     * @param sessionId 会话ID
     * @param request   游标分页请求
     * @return 分页结果
     */
    @Operation(summary = "游标分页查询消息", description = "适用于百万级数据，性能优化版")
    @GetMapping("/cursor/session/{sessionId}")
    public Result<PageResult<MessageResponse>> listByCursor(
            @Parameter(description = "会话ID", required = true)
            @PathVariable Long sessionId,
            @Valid CursorPageRequest request
    ) {
        log.info("游标分页查询消息：sessionId={}, lastId={}, pageSize={}",
                sessionId, request.getLastId(), request.getPageSize());

        PageResult<Message> pageResult = messageService.listByCursor(sessionId, request);

        // 转换为 Response
        List<MessageResponse> responses = pageResult.getList().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResult<MessageResponse> result = PageResult.ofCursor(
                responses,
                pageResult.getPageSize(),
                pageResult.getHasMore()
        );

        return Result.success(result);
    }

    /**
     * 游标分页查询消息（按时间范围）
     * <p>
     * 用于数据导出、统计分析等场景
     * <p>
     * 使用示例：
     * <pre>
     * // 第一页
     * GET /api/v1/messages/cursor/time-range?startTime=1704067200000&endTime=1704153600000&pageSize=20
     *
     * // 下一页
     * GET /api/v1/messages/cursor/time-range?startTime=1704067200000&endTime=1704153600000&pageSize=20&lastId=456
     * </pre>
     *
     * @param request 时间范围查询请求
     * @return 分页结果
     */
    @Operation(summary = "按时间范围查询消息", description = "用于数据导出、统计分析")
    @GetMapping("/cursor/time-range")
    public Result<PageResult<MessageResponse>> listByTimeRange(
            @Valid MessageTimeRangeRequest request
    ) {
        log.info("时间范围查询消息：startTime={}, endTime={}, lastId={}, pageSize={}",
                request.getStartTime(), request.getEndTime(), request.getLastId(), request.getPageSize());

        PageResult<Message> pageResult = messageService.listByTimeRange(request);

        // 转换为 Response
        List<MessageResponse> responses = pageResult.getList().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResult<MessageResponse> result = PageResult.ofCursor(
                responses,
                pageResult.getPageSize(),
                pageResult.getHasMore()
        );

        return Result.success(result);
    }

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息详情
     */
    @Operation(summary = "根据ID查询消息")
    @GetMapping("/{id}")
    public Result<MessageResponse> getById(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long id
    ) {
        log.info("查询消息详情：id={}", id);
        Message message = messageService.getById(id);
        return Result.success(toResponse(message));
    }

    /**
     * 实体转响应对象
     */
    private MessageResponse toResponse(Message entity) {
        return MessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .tokenCount(entity.getTokensUsed())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
