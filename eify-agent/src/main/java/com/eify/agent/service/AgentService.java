package com.eify.agent.service;

import com.eify.common.result.PageResult;
import com.eify.agent.domain.dto.*;

/**
 * Agent 服务接口
 */
public interface AgentService {

    /**
     * 分页查询 Agent（基础）
     *
     * @param page     当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<AgentResponse> list(Integer page, Integer pageSize);

    /**
     * 分页查询 Agent（支持筛选）
     *
     * @param page     当前页
     * @param pageSize 每页大小
     * @param name     Agent 名称（模糊搜索，可选）
     * @param enabled  启用状态（可选）
     * @return 分页结果
     */
    PageResult<AgentResponse> list(Integer page, Integer pageSize, String name, Integer enabled);

    /**
     * 根据 ID 查询 Agent（含关联数据）
     *
     * @param id 主键 ID
     * @return 响应对象
     */
    AgentResponse getById(Long id);

    /**
     * 根据 ID 查询 Agent 实体（内部使用）
     *
     * @param id 主键 ID
     * @return Agent 实体
     */
    com.eify.agent.domain.entity.Agent getEntityById(Long id);

    /**
     * 创建 Agent
     *
     * @param request 创建请求
     * @return 创建的实体
     */
    com.eify.agent.domain.entity.Agent create(AgentCreateRequest request);

    /**
     * 更新 Agent
     *
     * @param id      主键 ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    com.eify.agent.domain.entity.Agent update(Long id, AgentUpdateRequest request);

    /**
     * 删除 Agent
     *
     * @param id 主键 ID
     */
    void delete(Long id);

    /**
     * 测试对话
     *
     * @param id      Agent ID
     * @param request 测试对话请求
     * @return 测试对话响应
     */
    AgentTestChatResponse testChat(Long id, AgentTestChatRequest request);

    /**
     * 绑定 MCP 工具列表（全量替换）
     *
     * @param id      Agent ID
     * @param request 绑定请求
     */
    void bindTools(Long id, BindToolsRequest request);
}
