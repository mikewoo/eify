package com.eify.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.eify.common.context.CurrentContext;
import com.eify.common.crypto.CryptoUtil;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.provider.constant.ModelCategory;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.dto.ModelCreateRequest;
import com.eify.provider.domain.dto.ProviderCreateRequest;
import com.eify.provider.domain.dto.ProviderResponse;
import com.eify.provider.domain.dto.ProviderUpdateRequest;
import com.eify.provider.domain.entity.ModelConfig;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.domain.entity.ProviderHealth;
import com.eify.provider.mapper.ModelConfigMapper;
import com.eify.provider.mapper.ProviderHealthMapper;
import com.eify.provider.mapper.ProviderMapper;
import com.eify.provider.service.ProviderConnectionTestService;
import com.eify.provider.service.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商服务实现。
 * 自动对 authConfig 中的 api_key 做 AES-256-GCM 加解密，确保数据库中不存储明文密钥。
 */
@Service
public class ProviderServiceImpl implements ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderServiceImpl.class);
    private static final String ENC_PREFIX = "enc:";

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderConnectionTestService connectionTestService;
    private final byte[] cryptoKek;

    public ProviderServiceImpl(
            ProviderMapper providerMapper,
            ModelConfigMapper modelConfigMapper,
            ProviderHealthMapper providerHealthMapper,
            ProviderConnectionTestService connectionTestService,
            byte[] cryptoKek) {
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.providerHealthMapper = providerHealthMapper;
        this.connectionTestService = connectionTestService;
        this.cryptoKek = cryptoKek;
    }

    @Override
    public PageResult<ProviderResponse> list(Integer page, Integer pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<Provider> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
                .eq(Provider::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId())
                .orderByDesc(Provider::getId);

        IPage<Provider> result = providerMapper.selectPage(pageObj, wrapper);

        List<ProviderResponse> responses = buildListResponses(result.getRecords());

        return PageResult.of(responses, result.getTotal(), page, pageSize);
    }

    /**
     * 分页查询供应商（支持筛选）
     */
    public PageResult<ProviderResponse> list(Integer page, Integer pageSize, String name, ProviderType type, Integer enabled) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<Provider> pageObj = PageHelper.toPage(page, pageSize);

        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
                .eq(Provider::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId())
                .orderByDesc(Provider::getId);

        // 添加筛选条件
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Provider::getName, name.trim());
        }
        if (type != null) {
            wrapper.eq(Provider::getType, type);
        }
        if (enabled != null) {
            wrapper.eq(Provider::getEnabled, enabled);
        }

        IPage<Provider> result = providerMapper.selectPage(pageObj, wrapper);

        List<ProviderResponse> responses = buildListResponses(result.getRecords());

        return PageResult.of(responses, result.getTotal(), page, pageSize);
    }

    @Override
    @Cacheable(value = "provider-cache", key = "#id")
    public ProviderResponse getById(Long id) {
        Provider provider = providerMapper.selectOne(new LambdaQueryWrapper<Provider>()
                .eq(Provider::getId, id)
                .eq(Provider::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }

        decryptApiKey(provider.getAuthConfig());
        return toFullResponse(provider);
    }

    @Override
    public Provider getEntityById(Long id) {
        Provider provider = providerMapper.selectOne(new LambdaQueryWrapper<Provider>()
                .eq(Provider::getId, id)
                .eq(Provider::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        decryptApiKey(provider.getAuthConfig());
        return provider;
    }

    @Override
    public List<ProviderResponse.ModelConfigInfo> getModels(Long id) {
        return getModels(id, null, null);
    }

    @Override
    public List<ProviderResponse.ModelConfigInfo> getModels(Long id, ModelCategory category) {
        return getModels(id, category, null);
    }

    @Override
    public List<ProviderResponse.ModelConfigInfo> getModels(Long id, ModelCategory category, Integer enabled) {
        WorkspaceGuard.requireInWorkspace(
                providerMapper.selectById(id), ErrorCode.NOT_FOUND);
        return getModelConfigs(id, category, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "provider-cache", key = "#providerId")
    public ProviderResponse.ModelConfigInfo createModel(Long providerId, ModelCreateRequest request) {
        Provider provider = WorkspaceGuard.requireInWorkspace(
                providerMapper.selectById(providerId), ErrorCode.NOT_FOUND);
        if (provider.getEnabled() == null || provider.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "供应商已禁用，无法添加模型");
        }

        ModelConfig mc = new ModelConfig();
        mc.setProviderId(providerId);
        mc.setModelId(request.getModelId());
        mc.setName(request.getDisplayName());
        mc.setModelCategory(ModelCategory.fromValue(request.getCategory()));
        mc.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        mc.setExtraParams(request.getExtraParams() != null
                ? request.getExtraParams()
                : new tools.jackson.databind.node.ObjectNode(tools.jackson.databind.node.JsonNodeFactory.instance));
        if (request.getContextSize() != null) {
            mc.setContextSize(request.getContextSize());
        }
        WorkspaceGuard.bind(mc);

        modelConfigMapper.insert(mc);
        log.info("手动添加模型成功: providerId={}, modelId={}, category={}",
                providerId, request.getModelId(), mc.getModelCategory());

        return toModelConfigInfo(mc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "provider-cache", allEntries = true)
    public Provider create(ProviderCreateRequest request) {
        WorkspaceGuard.checkNameUnique(providerMapper,
                Provider::getName, Provider::getWorkspaceId, Provider::getId,
                request.getName(), null, ErrorCode.PARAM_ERROR, "供应商名称已存在");

        Provider provider = new Provider();
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setEnabled(request.getEnabled());
        WorkspaceGuard.bind(provider);

        if (request.getAuthConfig() != null) {
            JsonNode config = convertToJsonNode(request.getAuthConfig());
            encryptApiKey(config);
            provider.setAuthConfig(config);
        }

        try {
            providerMapper.insert(provider);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "供应商名称已存在");
        }
        log.info("创建供应商成功，id: {}, name: {}", provider.getId(), provider.getName());

        decryptApiKey(provider.getAuthConfig());
        return provider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "provider-cache", key = "#id")
    public Provider update(Long id, ProviderUpdateRequest request) {
        Provider existing = WorkspaceGuard.requireInWorkspace(
                providerMapper.selectById(id), ErrorCode.NOT_FOUND);

        if (request.getName() != null && !request.getName().equals(existing.getName())) {
            WorkspaceGuard.checkNameUnique(providerMapper,
                    Provider::getName, Provider::getWorkspaceId, Provider::getId,
                    request.getName(), id, ErrorCode.PARAM_ERROR, "供应商名称已存在");
            existing.setName(request.getName());
        }

        if (request.getType() != null) {
            existing.setType(request.getType());
        }
        if (request.getBaseUrl() != null) {
            existing.setBaseUrl(request.getBaseUrl());
        }
        if (request.getAuthConfig() != null) {
            JsonNode config = convertToJsonNode(request.getAuthConfig());
            encryptApiKey(config);
            existing.setAuthConfig(config);
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }

        try {
            providerMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "供应商名称已存在");
        }
        log.info("更新供应商成功，id: {}, name: {}", id, existing.getName());

        decryptApiKey(existing.getAuthConfig());
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "provider-cache", key = "#id")
    public void delete(Long id) {
        Provider existing = WorkspaceGuard.requireInWorkspace(
                providerMapper.selectById(id), ErrorCode.NOT_FOUND);

        int agentRefs = providerMapper.countAgentReferences(id);
        if (agentRefs > 0) {
            throw new BusinessException(ErrorCode.PROVIDER_IN_USE);
        }

        try {
            int workflowRefs = providerMapper.countWorkflowLlmReferences(id);
            if (workflowRefs > 0) {
                throw new BusinessException(ErrorCode.PROVIDER_IN_USE_BY_WORKFLOW);
            }
        } catch (Exception e) {
            log.warn("跳过工作流 LLM 节点引用检查（可能是不支持 JSON_EXTRACT 的数据库）: {}", e.getMessage());
        }

        modelConfigMapper.delete(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getProviderId, id)
                .eq(ModelConfig::getWorkspaceId, CurrentContext.getWorkspaceId()));

        providerMapper.deleteById(id);
        log.info("删除供应商成功，id: {}, name: {}", id, existing.getName());
    }

    @Override
    public ConnectionTestResult testConnection(Long id) {
        Provider provider = getEntityById(id);
        return connectionTestService.testConnection(provider);
    }

    /**
     * 转换为基本响应对象（不含详情）
     */
    private ProviderResponse toBasicResponse(Provider provider) {
        return ProviderResponse.builder()
                .id(provider.getId())
                .name(provider.getName())
                .type(provider.getType())
                .baseUrl(provider.getBaseUrl())
                .authConfig(provider.getAuthConfig())
                .enabled(provider.getEnabled())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }

    /**
     * 批量构建列表响应（含模型配置，不含健康状态）
     */
    private List<ProviderResponse> buildListResponses(List<Provider> providers) {
        if (providers.isEmpty()) {
            return List.of();
        }

        List<Long> providerIds = providers.stream()
                .map(Provider::getId)
                .collect(Collectors.toList());

        List<ModelConfig> allModelConfigs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .in(ModelConfig::getProviderId, providerIds));

        java.util.Map<Long, List<ProviderResponse.ModelConfigInfo>> modelsByProvider =
                allModelConfigs.stream()
                        .collect(Collectors.groupingBy(
                                ModelConfig::getProviderId,
                                Collectors.mapping(this::toModelConfigInfo, Collectors.toList())));

        return providers.stream()
                .peek(p -> decryptApiKey(p.getAuthConfig()))
                .map(p -> {
                    ProviderResponse response = toBasicResponse(p);
                    response.setModelConfigs(
                            modelsByProvider.getOrDefault(p.getId(), List.of()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    private ProviderResponse.ModelConfigInfo toModelConfigInfo(ModelConfig config) {
        ProviderResponse.ModelConfigInfo info = new ProviderResponse.ModelConfigInfo();
        info.setId(config.getId());
        info.setModelName(config.getModelId());
        info.setDisplayName(config.getName());
        info.setCategory(config.getModelCategory());
        info.setExtraParams(config.getExtraParams());
        return info;
    }

    /**
     * 转换为完整响应对象（含模型配置和健康状态）
     */
    private ProviderResponse toFullResponse(Provider provider) {
        // 查询模型配置
        List<ProviderResponse.ModelConfigInfo> modelConfigs = getModelConfigs(provider.getId(), null, null);

        // 查询健康状态
        ProviderResponse.ProviderHealthInfo health = getProviderHealth(provider.getId());

        return ProviderResponse.builder()
                .id(provider.getId())
                .name(provider.getName())
                .type(provider.getType())
                .baseUrl(provider.getBaseUrl())
                .authConfig(provider.getAuthConfig())
                .enabled(provider.getEnabled())
                .modelConfigs(modelConfigs)
                .health(health)
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }

    /**
     * 获取模型配置列表
     */
    private List<ProviderResponse.ModelConfigInfo> getModelConfigs(Long providerId, ModelCategory category, Integer enabled) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getProviderId, providerId);
        if (category != null) {
            wrapper.eq(ModelConfig::getModelCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(ModelConfig::getEnabled, enabled);
        }

        List<ModelConfig> configs = modelConfigMapper.selectList(wrapper);
        return configs.stream()
                .map(this::toModelConfigInfo)
                .collect(Collectors.toList());
    }

    /**
     * 获取健康状态
     */
    private ProviderResponse.ProviderHealthInfo getProviderHealth(Long providerId) {
        LambdaQueryWrapper<ProviderHealth> wrapper = new LambdaQueryWrapper<ProviderHealth>()
                .eq(ProviderHealth::getProviderId, providerId);
        ProviderHealth health = providerHealthMapper.selectOne(wrapper);

        if (health == null) {
            return null;
        }

        ProviderResponse.ProviderHealthInfo info = new ProviderResponse.ProviderHealthInfo();
        info.setStatus(health.getStatus());
        info.setLastCheckAt(health.getLastCheckAt());
        info.setLastSuccessAt(health.getLastSuccessAt());
        info.setFailCount(health.getFailCount());
        info.setLatencyMs(health.getLatencyMs());
        info.setErrorMessage(health.getErrorMessage());
        return info;
    }

    /**
     * 加密 authConfig 中的 api_key 字段（原地修改）。
     * 已加密的值（以 "enc:" 开头）不重复加密，兼容旧明文数据。
     */
    private void encryptApiKey(JsonNode authConfig) {
        if (authConfig == null || !authConfig.has("api_key")) return;
        String value = authConfig.get("api_key").asText();
        if (value.isEmpty() || value.startsWith(ENC_PREFIX)) return;
        ((ObjectNode) authConfig).put("api_key", ENC_PREFIX + CryptoUtil.encrypt(value, cryptoKek));
    }

    /**
     * 解密 authConfig 中的 api_key 字段（原地修改）。
     * 非加密值（不以 "enc:" 开头）保持不变，兼容旧明文数据。
     */
    private void decryptApiKey(JsonNode authConfig) {
        if (authConfig == null || !authConfig.has("api_key")) return;
        String value = authConfig.get("api_key").asText();
        if (!value.startsWith(ENC_PREFIX)) return;
        ((ObjectNode) authConfig).put("api_key", CryptoUtil.decrypt(value.substring(ENC_PREFIX.length()), cryptoKek));
    }

    /**
     * 将 Map 转换为 JsonNode
     */
    private JsonNode convertToJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        return new tools.jackson.databind.ObjectMapper().valueToTree(obj);
    }
}
