package com.eify.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.provider.domain.entity.Provider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商 Mapper
 */
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {
}
