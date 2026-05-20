package com.eify.chat.service;

import com.eify.chat.domain.entity.Conversation;
import com.eify.common.result.PageResult;

/**
 * 对话会话服务接口
 * <p>
 * 使用游标分页 + 优化索引支持高效查询
 */
public interface ConversationService {

    /**
     * 游标分页查询用户的对话列表
     * <p>
     * 优化策略：
     * <ul>
     *   <li>使用索引 idx_user_status_updated_id</li>
     *   <li>支持按更新时间倒序排序</li>
     *   <li>使用游标分页避免深分页性能问题</li>
     * </ul>
     *
     * @param userId  用户ID
     * @param status  状态（0=已归档，1=进行中）
     * @param lastId         上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize       每页大小
     * @return 分页结果
     */
    PageResult<Conversation> listUserConversations(Long userId, Integer status,
                                                   Long lastId, java.time.LocalDateTime lastTimestamp,
                                                   Integer pageSize);

    /**
     * 游标分页查询 Agent 的对话列表
     * <p>
     * 优化策略：
     * <ul>
     *   <li>使用索引 idx_agent_updated_id</li>
     *   <li>支持按更新时间倒序排序</li>
     *   <li>使用游标分页避免深分页性能问题</li>
     * </ul>
     *
     * @param agentId  Agent ID
     * @param lastId         上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize       每页大小
     * @return 分页结果
     */
    PageResult<Conversation> listAgentConversations(Long agentId,
                                                     Long lastId, java.time.LocalDateTime lastTimestamp,
                                                     Integer pageSize);

    /**
     * 根据ID查询对话
     *
     * @param id 对话ID
     * @return 对话实体
     */
    Conversation getById(Long id);

    /**
     * 创建新会话
     *
     * @param userId  用户ID
     * @param agentId Agent ID
     * @return 创建的会话
     */
    Conversation create(Long userId, Long agentId);

    /**
     * 创建新会话（带自定义标题）
     *
     * @param userId  用户ID
     * @param agentId Agent ID
     * @param title   对话标题（可选）
     * @return 创建的会话
     */
    Conversation create(Long userId, Long agentId, String title);

    /**
     * 创建新会话（支持工作流绑定）
     *
     * @param userId     用户ID
     * @param agentId    Agent ID（可选）
     * @param workflowId 工作流 ID（可选）
     * @param title      对话标题（可选）
     * @return 创建的会话
     */
    Conversation create(Long userId, Long agentId, Long workflowId, String title);

    /**
     * 删除对话（软删除）
     *
     * @param id 对话ID
     */
    void delete(Long id);
}
