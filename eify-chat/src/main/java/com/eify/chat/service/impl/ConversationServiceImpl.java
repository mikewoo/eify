package com.eify.chat.service.impl;

import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.mapper.ConversationMapper;
import com.eify.chat.service.ConversationService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话服务实现
 * <p>
 * 使用游标分页 + 优化索引支持高效查询
 * <p>
 * 性能优化策略：
 * <ul>
 *   <li>使用索引 idx_user_status_updated_id 支持用户对话查询</li>
 *   <li>使用索引 idx_agent_updated_id 支持 Agent 对话查询</li>
 *   <li>使用游标分页避免深分页性能问题</li>
 * </ul>
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationMapper conversationMapper;

    public ConversationServiceImpl(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    public PageResult<Conversation> listUserConversations(Long userId, Integer status,
                                                          Long lastId, LocalDateTime lastTimestamp,
                                                          Integer pageSize) {
        // 参数校验
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (status == null) {
            status = 1; // 默认查询进行中的对话
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }

        // 使用 MyBatis-Plus 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Conversation> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, pageSize);

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, status)
                .eq(Conversation::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId())
                .orderByDesc(Conversation::getUpdatedAt, Conversation::getId);

        com.baomidou.mybatisplus.core.metadata.IPage<Conversation> result =
            conversationMapper.selectPage(page, wrapper);

        log.debug("分页查询用户对话：userId={}, status={}, pageSize={}, total={}",
                userId, status, pageSize, result.getTotal());

        return PageResult.of(result.getRecords(), result.getTotal(), pageSize, 1);
    }

    @Override
    public PageResult<Conversation> listAgentConversations(Long agentId,
                                                           Long lastId, LocalDateTime lastTimestamp,
                                                           Integer pageSize) {
        // 参数校验
        if (agentId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }

        List<Conversation> list;

        Long workspaceId = com.eify.common.context.CurrentContext.getWorkspaceId();

        // 执行查询（使用索引 idx_agent_updated_id）
        if (lastId == null) {
            // 第一页 - 显式转换为整数避免类型问题
            Integer limitSize = Integer.valueOf(pageSize + 1);
            list = conversationMapper.selectAgentConversationsFirstPage(agentId, workspaceId, limitSize);
        } else {
            // 下一页（游标分页）
            if (lastTimestamp == null) {
                // 如果没有提供 lastTimestamp，使用当前时间
                lastTimestamp = LocalDateTime.now();
            }
            Integer limitSize = Integer.valueOf(pageSize + 1);
            list = conversationMapper.selectAgentConversationsByCursor(agentId, workspaceId, lastId, lastTimestamp, limitSize);
        }

        // 判断是否有更多数据（多查一条）
        boolean hasMore = list.size() > pageSize;
        if (hasMore) {
            list = list.subList(0, pageSize);
        }

        log.debug("游标分页查询Agent对话：agentId={}, lastId={}, pageSize={}, hasMore={}",
                agentId, lastId, pageSize, hasMore);

        return PageResult.ofCursor(list, pageSize, hasMore);
    }

    @Override
    public Conversation getById(Long id) {
        Conversation conversation = conversationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, id)
                        .eq(Conversation::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    @Override
    public Conversation create(Long userId, Long agentId) {
        return create(userId, agentId, null);
    }

    @Override
    public Conversation create(Long userId, Long agentId, String title) {
        return create(userId, agentId, null, title);
    }

    @Override
    public Conversation create(Long userId, Long agentId, Long workflowId, String title) {
        log.info("开始创建对话：userId={}, agentId={}, workflowId={}, title={}",
                userId, agentId, workflowId, title);

        try {
            Conversation conversation = new Conversation();
            conversation.setUserId(userId);
            conversation.setAgentId(agentId);
            conversation.setWorkflowId(workflowId);
            conversation.setTitle(title != null && !title.trim().isEmpty() ? title : "新对话");
            conversation.setStatus(1); // 进行中
            conversation.setCreatorId(userId); // 设置创建者
            conversation.setWorkspaceId(com.eify.common.context.CurrentContext.getWorkspaceId());

            log.debug("准备插入对话：userId={}, agentId={}, workflowId={}, title={}",
                    conversation.getUserId(), conversation.getAgentId(),
                    conversation.getWorkflowId(), conversation.getTitle());

            conversationMapper.insert(conversation);

            log.info("创建新会话成功：userId={}, agentId={}, workflowId={}, sessionId={}",
                    userId, agentId, workflowId, conversation.getId());

            return conversation;
        } catch (Exception e) {
            log.error("创建对话失败：userId={}, agentId={}, workflowId={}, error={}",
                    userId, agentId, workflowId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void delete(Long id) {
        log.info("删除对话：id={}", id);

        Conversation conversation = conversationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, id)
                        .eq(Conversation::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // MyBatis-Plus 的 @TableLogic 会自动处理软删除
        conversationMapper.deleteById(id);

        log.info("删除对话成功：id={}", id);
    }
}
