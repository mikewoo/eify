package com.eify.provider.service.impl;

import com.eify.common.exception.BusinessException;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderConnectionTestServiceImpl")
class ProviderConnectionTestServiceImplTest {

    @Mock ProviderAdapterFactory adapterFactory;
    @Mock ProviderMapper providerMapper;
    @Mock ProviderAdapter adapter;

    @InjectMocks
    ProviderConnectionTestServiceImpl connectionTestService;

    @Nested
    @DisplayName("testConnectionById()")
    class TestConnectionByIdTests {

        @Test
        @DisplayName("P0 - 供应商不存在应抛异常")
        void shouldThrowWhenProviderNotFound() {
            // given
            when(providerMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> connectionTestService.testConnectionById(999L));
        }

        @Test
        @DisplayName("P1 - 正常测试应委托给适配器")
        void shouldDelegateToAdapter() {
            // given
            Provider provider = new Provider();
            provider.setId(1L);
            provider.setName("OpenAI");
            provider.setType(ProviderType.OPENAI);
            provider.setBaseUrl("https://api.openai.com");

            when(providerMapper.selectById(1L)).thenReturn(provider);
            when(adapterFactory.getAdapter(ProviderType.OPENAI)).thenReturn(adapter);
            when(adapter.testConnection(provider))
                    .thenReturn(ConnectionTestResult.success(100L, 5));

            // when
            ConnectionTestResult result = connectionTestService.testConnectionById(1L);

            // then
            assertTrue(result.isSuccess());
            assertEquals(100L, result.getLatencyMs());
            assertEquals(5, result.getModelCount());
            verify(adapter).testConnection(provider);
        }
    }

    @Nested
    @DisplayName("testConnection(Provider)")
    class TestConnectionTests {

        @Test
        @DisplayName("P0 - 不支持的供应商类型应抛异常")
        void shouldThrowWhenAdapterNotFound() {
            // given
            Provider provider = new Provider();
            provider.setType(ProviderType.OPENAI);
            when(adapterFactory.getAdapter(ProviderType.OPENAI))
                    .thenThrow(new BusinessException(
                            com.eify.common.error.ErrorCode.NOT_FOUND, "不支持的供应商类型"));

            // when & then
            assertThrows(BusinessException.class,
                    () -> connectionTestService.testConnection(provider));
        }

        @Test
        @DisplayName("P1 - 连接失败应返回失败结果")
        void shouldReturnFailureResult() {
            // given
            Provider provider = new Provider();
            provider.setType(ProviderType.OPENAI);
            when(adapterFactory.getAdapter(ProviderType.OPENAI)).thenReturn(adapter);
            when(adapter.testConnection(provider))
                    .thenReturn(ConnectionTestResult.failure(200L, "Connection refused"));

            // when
            ConnectionTestResult result = connectionTestService.testConnection(provider);

            // then
            assertFalse(result.isSuccess());
            assertEquals("Connection refused", result.getErrorMessage());
        }
    }
}
