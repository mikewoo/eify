package com.eify.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.chat.domain.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话数据访问层
 * <p>
 * 使用优化索引支持游标分页
 * <p>
 * Mapper XML 位于: resources/mapper/ConversationMapper.xml
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 使用优化索引查询用户的对话列表
     * <p>
     * 对应索引：idx_user_status_updated_id (user_id, status, updated_at, id)
     * 支持按更新时间倒序排序
     *
     * @param userId     用户ID
     * @param status     状态
     * @param lastId     上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize   每页大小
     * @return 对话列表
     */
    List<Conversation> selectUserConversationsByCursor(
            @Param("userId") Long userId,
            @Param("status") Integer status,
            @Param("workspaceId") Long workspaceId,
            @Param("lastId") Long lastId,
            @Param("lastTimestamp") LocalDateTime lastTimestamp,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 查询用户对话第一页
     * <p>
     * 对应索引：idx_user_status_updated_id (user_id, status, updated_at, id)
     *
     * @param userId   用户ID
     * @param status   状态
     * @param pageSize 每页大小
     * @return 对话列表
     */
    List<Conversation> selectUserConversationsFirstPage(
            @Param("userId") Long userId,
            @Param("status") Integer status,
            @Param("workspaceId") Long workspaceId,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 查询 Agent 的对话列表
     * <p>
     * 对应索引：idx_agent_updated_id (agent_id, updated_at, id)
     *
     * @param agentId    Agent ID
     * @param lastId     上一页最后一条记录的ID
     * @param lastTimestamp  上一页最后一条记录的更新时间
     * @param pageSize   每页大小
     * @return 对话列表
     */
    List<Conversation> selectAgentConversationsByCursor(
            @Param("agentId") Long agentId,
            @Param("workspaceId") Long workspaceId,
            @Param("lastId") Long lastId,
            @Param("lastTimestamp") LocalDateTime lastTimestamp,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 查询 Agent 对话第一页
     * <p>
     * 对应索引：idx_agent_updated_id (agent_id, updated_at, id)
     *
     * @param agentId  Agent ID
     * @param pageSize 每页大小
     * @return 对话列表
     */
    List<Conversation> selectAgentConversationsFirstPage(
            @Param("agentId") Long agentId,
            @Param("workspaceId") Long workspaceId,
            @Param("pageSize") Integer pageSize
    );
}
