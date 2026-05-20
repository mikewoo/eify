package com.eify.chat.service;

import com.eify.chat.domain.dto.MessageTimeRangeRequest;
import com.eify.chat.domain.entity.Message;
import com.eify.common.dto.CursorPageRequest;
import com.eify.common.result.PageResult;

import java.util.List;

/**
 * 消息服务接口
 * <p>
 * 使用游标分页 + 覆盖索引优化查询性能
 */
public interface MessageService {

    /**
     * 游标分页查询消息（按会话ID）
     * <p>
     * 优化策略：
     * <ul>
     *   <li>使用覆盖索引 idx_session_id_id_role_time</li>
     *   <li>避免回表查询 role 和 created_at 字段</li>
     *   <li>使用游标分页避免深分页性能问题</li>
     * </ul>
     *
     * @param sessionId 会话ID
     * @param request   游标分页请求
     * @return 分页结果
     */
    PageResult<Message> listByCursor(Long sessionId, CursorPageRequest request);

    /**
     * 游标分页查询消息（按时间范围）
     * <p>
     * 优化策略：
     * <ul>
     *   <li>使用索引 idx_created_at_id</li>
     *   <li>支持时间范围查询</li>
     *   <li>使用游标分页避免深分页性能问题</li>
     * </ul>
     *
     * @param request 时间范围查询请求
     * @return 分页结果
     */
    PageResult<Message> listByTimeRange(MessageTimeRangeRequest request);

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息实体
     */
    Message getById(Long id);

    /**
     * 保存消息
     *
     * @param message 消息实体
     * @return 保存后的消息
     */
    Message save(Message message);

    /**
     * 加载最近的消息（用于上下文）
     *
     * @param sessionId 会话ID
     * @param limit     数量限制
     * @return 消息列表（按时间正序）
     */
    List<Message> loadRecentMessages(Long sessionId, int limit);
}
