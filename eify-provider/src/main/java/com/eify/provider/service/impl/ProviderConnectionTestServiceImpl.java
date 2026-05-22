package com.eify.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.constant.ModelCategory;
import com.eify.provider.domain.entity.ModelConfig;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ModelConfigMapper;
import com.eify.provider.mapper.ProviderMapper;
import com.eify.provider.service.ProviderConnectionTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商连通性测试服务实现
 * <p>
 * 测试成功后自动将发现的模型列表同步到 model_config 表（只新增，不删除、不覆盖）。
 */
@Service
public class ProviderConnectionTestServiceImpl implements ProviderConnectionTestService {

    private static final Logger log = LoggerFactory.getLogger(ProviderConnectionTestServiceImpl.class);

    private final ProviderAdapterFactory adapterFactory;
    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;

    public ProviderConnectionTestServiceImpl(
            ProviderAdapterFactory adapterFactory,
            ProviderMapper providerMapper,
            ModelConfigMapper modelConfigMapper
    ) {
        this.adapterFactory = adapterFactory;
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestResult testConnection(Provider provider) {
        log.info("[连通性测试] 开始 - Provider: {}, Type: {}",
                provider.getName(), provider.getType());

        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());
        ConnectionTestResult result = adapter.testConnection(provider);

        if (result.isSuccess() && result.getModelNames() != null && !result.getModelNames().isEmpty()) {
            int added = syncModels(provider, result.getModelNames());
            log.info("[同步模型] Provider: {}, 发现 {} 个模型, 新增 {} 个",
                    provider.getName(), result.getModelNames().size(), added);
        }

        return result;
    }

    @Override
    public ConnectionTestResult testConnectionById(Long providerId) {
        Provider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new com.eify.common.exception.BusinessException(
                    com.eify.common.error.ErrorCode.NOT_FOUND, "供应商不存在");
        }
        return testConnection(provider);
    }

    /**
     * 增量同步模型：只 INSERT 不存在的 model_id，不删除、不覆盖已有
     *
     * @return 本次新增的模型数量
     */
    private int syncModels(Provider provider, List<String> modelIds) {
        Set<String> existingIds = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getProviderId, provider.getId())
        ).stream().map(ModelConfig::getModelId).collect(Collectors.toSet());

        int added = 0;
        for (String modelId : modelIds) {
            if (!existingIds.contains(modelId)) {
                ModelConfig mc = new ModelConfig();
                mc.setProviderId(provider.getId());
                mc.setName(modelId);
                mc.setModelId(modelId);
                mc.setContextSize(0);
                mc.setModelCategory(ModelCategory.fromModelId(modelId));
                mc.setEnabled(1);
                mc.setExtraParams(JsonNodeFactory.instance.objectNode());
                WorkspaceGuard.bind(mc);
                modelConfigMapper.insert(mc);
                added++;
            }
        }
        return added;
    }
}
