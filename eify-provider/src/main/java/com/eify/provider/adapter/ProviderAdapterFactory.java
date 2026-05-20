package com.eify.provider.adapter;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.provider.constant.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商适配器工厂
 * 负责管理和获取适配器实例
 */
@Component
public class ProviderAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderAdapterFactory.class);

    private final Map<ProviderType, ProviderAdapter> adapters;

    public ProviderAdapterFactory(List<ProviderAdapter> adapterList) {
        // 使用 EnumMap 保证类型安全
        this.adapters = new EnumMap<>(ProviderType.class);

        // 自动注册所有实现了 ProviderAdapter 接口的 Bean
        for (ProviderAdapter adapter : adapterList) {
            registerAdapter(adapter);
        }

        log.info("[AdapterFactory] 已注册 {} 个适配器: {}",
                adapters.size(), adapters.keySet());
    }

    /**
     * 注册适配器
     */
    private void registerAdapter(ProviderAdapter adapter) {
        ProviderType type = adapter.getSupportedType();
        if (adapters.containsKey(type)) {
            log.warn("[AdapterFactory] 适配器已存在，将被覆盖: {}", type);
        }
        adapters.put(type, adapter);
        log.info("[AdapterFactory] 注册适配器: {} -> {}", type, adapter.getClass().getSimpleName());
    }

    /**
     * 获取适配器
     *
     * @param type 供应商类型
     * @return 对应的适配器
     * @throws BusinessException 如果适配器不存在
     */
    public ProviderAdapter getAdapter(ProviderType type) {
        ProviderAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "不支持的供应商类型: " + type + "，请先实现对应的适配器");
        }
        return adapter;
    }

    /**
     * 获取所有已注册的适配器类型
     */
    public Set<ProviderType> getSupportedTypes() {
        return adapters.keySet();
    }
}
