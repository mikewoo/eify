package com.eify.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.provider.domain.entity.ProviderHealth;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商健康状态 Mapper
 */
@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealth> {

    /**
     * 使用 ON CONFLICT 原地更新健康状态。
     * provider_health 表以 provider_id 为唯一索引。
     */
    @Insert("INSERT INTO provider_health (provider_id, status, error_message, last_check_at, updated_at) " +
            "VALUES (#{providerId}, #{status}, #{errorMessage}, #{lastCheckAt}, NOW()) " +
            "ON CONFLICT (provider_id) DO UPDATE SET status = EXCLUDED.status, " +
            "error_message = EXCLUDED.error_message, " +
            "last_check_at = EXCLUDED.last_check_at, " +
            "updated_at = NOW()")
    int upsertHealth(ProviderHealth health);
}
