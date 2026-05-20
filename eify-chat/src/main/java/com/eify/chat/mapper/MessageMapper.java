package com.eify.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.chat.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息数据访问层
 * <p>
 * 使用覆盖索引优化查询性能
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 使用覆盖索引查询消息列表
     * <p>
     * 对应索引：idx_session_id_id_role_time (session_id, id, role, created_at)
     * 避免回表查询，提升性能
     *
     * @param sessionId 会话ID
     * @param lastId    上一页最后一条记录的ID
     * @param pageSize  每页大小
     * @return 消息列表
     */
    @Select("""
        SELECT id, session_id, role, content, token_count, model_id, metadata, created_at, updated_at
        FROM ai_chat_message
        WHERE session_id = #{sessionId}
          AND id < #{lastId}
        ORDER BY id DESC
        LIMIT #{pageSize}
        """)
    List<Message> selectByCursorWithCoveringIndex(
            @Param("sessionId") Long sessionId,
            @Param("lastId") Long lastId,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 使用覆盖索引查询第一页消息
     * <p>
     * 对应索引：idx_session_id_id_role_time (session_id, id, role, created_at)
     *
     * @param sessionId 会话ID
     * @param pageSize  每页大小
     * @return 消息列表
     */
    @Select("""
        SELECT id, session_id, role, content, token_count, model_id, metadata, created_at, updated_at
        FROM ai_chat_message
        WHERE session_id = #{sessionId}
        ORDER BY id DESC
        LIMIT #{pageSize}
        """)
    List<Message> selectFirstPageBySessionId(
            @Param("sessionId") Long sessionId,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 按时间范围查询消息
     * <p>
     * 对应索引：idx_created_at_id (created_at, id)
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param lastId    上一页最后一条记录的ID
     * @param limit     查询数量
     * @return 消息列表
     */
    @Select("""
        SELECT id, session_id, role, content, token_count, model_id, metadata, created_at, updated_at
        FROM ai_chat_message
        WHERE created_at >= #{startTime}
          AND created_at < #{endTime}
          AND id < #{lastId}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<Message> selectByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("lastId") Long lastId,
            @Param("limit") Integer limit
    );

    /**
     * 按时间范围查询第一页消息
     * <p>
     * 对应索引：idx_created_at_id (created_at, id)
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     查询数量
     * @return 消息列表
     */
    @Select("""
        SELECT id, session_id, role, content, token_count, model_id, metadata, created_at, updated_at
        FROM ai_chat_message
        WHERE created_at >= #{startTime}
          AND created_at < #{endTime}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<Message> selectFirstPageByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * 统计时间范围内的消息数量
     * <p>
     * 用于统计和监控
     *
     * @param sessionId 会话ID（可选）
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 消息数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM ai_chat_message
        WHERE created_at >= #{startTime}
          AND created_at < #{endTime}
          AND (#{sessionId} IS NULL OR session_id = #{sessionId})
        """)
    Long countByTimeRange(
            @Param("sessionId") Long sessionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
