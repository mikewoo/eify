package com.eify.provider.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eify.common.result.PageResult;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.dto.ProviderCreateRequest;
import com.eify.provider.domain.dto.ProviderResponse;
import com.eify.provider.domain.dto.ProviderUpdateRequest;
import com.eify.provider.domain.entity.Provider;

import java.util.List;

/**
 * 供应商服务
 */
public interface ProviderService {

    /**
     * 分页查询
     *
     * @param page     当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<ProviderResponse> list(Integer page, Integer pageSize);

    /**
     * 分页查询（支持筛选）
     *
     * @param page     当前页
     * @param pageSize 每页大小
     * @param name     供应商名称（可选，模糊搜索）
     * @param type     供应商类型（可选）
     * @param enabled 启用状态（可选）
     * @return 分页结果
     */
    PageResult<ProviderResponse> list(Integer page, Integer pageSize, String name, ProviderType type, Integer enabled);

    /**
     * 根据ID查询（含模型配置和健康状态）
     *
     * @param id 主键ID
     * @return 响应对象
     */
    ProviderResponse getById(Long id);

    /**
     * 根据ID查询 Provider 实体（内部使用）
     *
     * @param id 主键ID
     * @return Provider 实体
     */
    Provider getEntityById(Long id);

    /**
     * 创建
     *
     * @param request 创建请求
     * @return 创建的实体
     */
    Provider create(ProviderCreateRequest request);

    /**
     * 更新
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的实体
     */
    Provider update(Long id, ProviderUpdateRequest request);

    /**
     * 删除
     *
     * @param id 主键ID
     */
    void delete(Long id);

    /**
     * 测试连通性
     *
     * @param id 供应商ID
     * @return 测试结果
     */
    ConnectionTestResult testConnection(Long id);

    /**
     * 获取供应商下的模型列表
     *
     * @param id 供应商ID
     * @return 模型配置列表
     */
    List<ProviderResponse.ModelConfigInfo> getModels(Long id);
}
