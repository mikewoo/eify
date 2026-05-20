package com.eify.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.provider.constant.ProviderType;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProviderServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderServiceImpl")
class ProviderServiceImplTest {

    @Mock ProviderMapper providerMapper;
    @Mock ModelConfigMapper modelConfigMapper;
    @Mock ProviderHealthMapper providerHealthMapper;
    @Mock ProviderConnectionTestService connectionTestService;

    @InjectMocks
    ProviderServiceImpl providerService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L); // userId=1, workspaceId=1
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private Provider buildProvider(Long id, String name, Long workspaceId) {
        Provider p = new Provider();
        p.setId(id);
        p.setName(name);
        p.setType(ProviderType.OPENAI);
        p.setBaseUrl("https://api.openai.com");
        p.setWorkspaceId(workspaceId);
        p.setEnabled(1);
        return p;
    }

    // ========== list() ==========

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("P0 - page < 1 应抛参数异常")
        void shouldThrowWhenPageLessThan1() {
            assertThrows(BusinessException.class,
                    () -> providerService.list(0, 20));
        }

        @Test
        @DisplayName("P0 - pageSize < 1 应抛参数异常")
        void shouldThrowWhenPageSizeLessThan1() {
            assertThrows(BusinessException.class,
                    () -> providerService.list(1, 0));
        }

        @Test
        @DisplayName("P0 - pageSize > 100 应抛参数异常")
        void shouldThrowWhenPageSizeExceeds100() {
            assertThrows(BusinessException.class,
                    () -> providerService.list(1, 101));
        }

        @Test
        @DisplayName("P1 - 正常分页返回结果")
        void shouldReturnPaginatedResults() {
            // given
            Page<Provider> pageObj = new Page<>(1, 20);
            Provider provider = buildProvider(1L, "OpenAI", 1L);
            pageObj.setRecords(List.of(provider));
            pageObj.setTotal(1);

            when(providerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<ProviderResponse> result = providerService.list(1, 20);

            // then
            assertEquals(1, result.getList().size());
            assertEquals("OpenAI", result.getList().get(0).getName());
            assertEquals(1L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - 空列表应返回空结果")
        void shouldReturnEmptyListWhenNoRecords() {
            // given
            Page<Provider> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(providerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<ProviderResponse> result = providerService.list(1, 20);

            // then
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    // ========== getById() ==========

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("P0 - 供应商不存在应抛异常")
        void shouldThrowWhenProviderNotFound() {
            // given
            when(providerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> providerService.getById(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常（模拟返回 null）")
        void shouldThrowWhenProviderInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(providerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.getById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回完整响应（含模型配置和健康状态）")
        void shouldReturnFullResponseWithModelConfigsAndHealth() {
            // given
            Provider provider = buildProvider(1L, "OpenAI", 1L);
            when(providerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(provider);

            ModelConfig config = new ModelConfig();
            config.setId(10L);
            config.setModelId("gpt-4o");
            config.setName("GPT-4o");
            when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(config));

            ProviderHealth health = new ProviderHealth();
            health.setStatus("UP");
            health.setLatencyMs(150);
            when(providerHealthMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(health);

            // when
            ProviderResponse response = providerService.getById(1L);

            // then
            assertEquals(1L, response.getId());
            assertEquals("OpenAI", response.getName());
            assertNotNull(response.getModelConfigs());
            assertEquals(1, response.getModelConfigs().size());
            assertEquals("gpt-4o", response.getModelConfigs().get(0).getModelName());
            assertNotNull(response.getHealth());
            assertEquals("UP", response.getHealth().getStatus());
            assertEquals(150, response.getHealth().getLatencyMs());
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("P0 - 名称重复应抛异常")
        void shouldThrowWhenNameDuplicate() {
            // given
            ProviderCreateRequest request = ProviderCreateRequest.builder()
                    .name("OpenAI")
                    .type(ProviderType.OPENAI)
                    .baseUrl("https://api.openai.com")
                    .authConfig(Map.of("api_key", "sk-test"))
                    .enabled(1)
                    .build();

            // WorkspaceGuard.checkNameUnique 内部调用 selectCount
            when(providerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L); // 名称已存在

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> providerService.create(request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 创建成功应绑定 workspaceId 并插入")
        void shouldBindWorkspaceAndInsert() {
            // given
            ProviderCreateRequest request = ProviderCreateRequest.builder()
                    .name("NewProvider")
                    .type(ProviderType.OPENAI)
                    .baseUrl("https://api.new.com")
                    .authConfig(Map.of("api_key", ""))
                    .enabled(1)
                    .build();

            when(providerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 名称不重复
            when(providerMapper.insert(any(Provider.class))).thenReturn(1);

            // when
            Provider result = providerService.create(request);

            // then
            ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
            verify(providerMapper).insert(captor.capture());

            Provider saved = captor.getValue();
            assertEquals("NewProvider", saved.getName());
            assertEquals(ProviderType.OPENAI, saved.getType());
            assertEquals(1L, saved.getWorkspaceId()); // WorkspaceGuard.bind 设置
            assertEquals(1, saved.getEnabled());
        }

        @Test
        @DisplayName("P1 - authConfig 为 null 时不应报错")
        void shouldHandleNullAuthConfig() {
            // given
            ProviderCreateRequest request = ProviderCreateRequest.builder()
                    .name("NoAuth")
                    .type(ProviderType.OLLAMA)
                    .baseUrl("http://localhost:11434")
                    .authConfig(null)
                    .enabled(1)
                    .build();

            when(providerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.insert(any(Provider.class))).thenReturn(1);

            // when
            Provider result = providerService.create(request);

            // then
            assertNull(result.getAuthConfig());
            verify(providerMapper).insert(any(Provider.class));
        }
    }

    // ========== update() ==========

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("P0 - 供应商不存在应抛异常")
        void shouldThrowWhenProviderNotFound() {
            // given
            when(providerMapper.selectById(999L)).thenReturn(null);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setName("Updated");

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.update(999L, request));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 更新应抛异常")
        void shouldThrowWhenProviderInDifferentWorkspace() {
            // given - 供应商属于 workspace 999
            Provider provider = buildProvider(1L, "OpenAI", 999L);
            when(providerMapper.selectById(1L)).thenReturn(provider);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setName("Updated");

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.update(1L, request));
        }

        @Test
        @DisplayName("P0 - 改名为已存在的名称应抛异常")
        void shouldThrowWhenNewNameDuplicate() {
            // given
            Provider existing = buildProvider(1L, "OpenAI", 1L);
            when(providerMapper.selectById(1L)).thenReturn(existing);

            // 名称唯一性检查发现冲突
            when(providerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setName("DuplicateName");

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.update(1L, request));
        }

        @Test
        @DisplayName("P1 - 部分更新只修改提供的字段")
        void shouldOnlyUpdateProvidedFields() {
            // given
            Provider existing = buildProvider(1L, "OpenAI", 1L);
            existing.setBaseUrl("https://old.api.com");
            when(providerMapper.selectById(1L)).thenReturn(existing);
            when(providerMapper.updateById(any(Provider.class))).thenReturn(1);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setBaseUrl("https://new.api.com");
            // name, type, authConfig, enabled 均为 null → 不修改

            // when
            Provider result = providerService.update(1L, request);

            // then
            assertEquals("OpenAI", result.getName());         // 未修改
            assertEquals("https://new.api.com", result.getBaseUrl()); // 已修改
            verify(providerMapper).updateById(any(Provider.class));
        }

        @Test
        @DisplayName("P1 - 更新名称时检查唯一性")
        void shouldCheckNameUniquenessWhenNameChanged() {
            // given
            Provider existing = buildProvider(1L, "OldName", 1L);
            when(providerMapper.selectById(1L)).thenReturn(existing);
            when(providerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 新名称不重复
            when(providerMapper.updateById(any(Provider.class))).thenReturn(1);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setName("NewName");

            // when
            Provider result = providerService.update(1L, request);

            // then
            assertEquals("NewName", result.getName());
            // 验证调用了名称唯一性检查
            verify(providerMapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新相同名称不应触发唯一性检查")
        void shouldSkipNameCheckWhenNameUnchanged() {
            // given
            Provider existing = buildProvider(1L, "OpenAI", 1L);
            when(providerMapper.selectById(1L)).thenReturn(existing);
            when(providerMapper.updateById(any(Provider.class))).thenReturn(1);

            ProviderUpdateRequest request = new ProviderUpdateRequest();
            request.setName("OpenAI"); // 名称未变
            request.setEnabled(0);

            // when
            providerService.update(1L, request);

            // then - 不应调用 selectCount（名称唯一性检查）
            verify(providerMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }
    }

    // ========== delete() ==========

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("P0 - 供应商不存在应抛异常")
        void shouldThrowWhenProviderNotFound() {
            // given
            when(providerMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.delete(999L));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 删除应抛异常")
        void shouldThrowWhenProviderInDifferentWorkspace() {
            // given
            Provider provider = buildProvider(1L, "OpenAI", 999L);
            when(providerMapper.selectById(1L)).thenReturn(provider);

            // when & then
            assertThrows(BusinessException.class,
                    () -> providerService.delete(1L));
        }

        @Test
        @DisplayName("P1 - 正常删除应调用 deleteById")
        void shouldDeleteSuccessfully() {
            // given
            Provider provider = buildProvider(1L, "OpenAI", 1L);
            when(providerMapper.selectById(1L)).thenReturn(provider);
            when(providerMapper.deleteById(1L)).thenReturn(1);

            // when
            assertDoesNotThrow(() -> providerService.delete(1L));

            // then
            verify(providerMapper).deleteById(1L);
        }
    }

    // ========== testConnection() ==========

    @Nested
    @DisplayName("testConnection()")
    class TestConnectionTests {

        @Test
        @DisplayName("P1 - 应委托给 connectionTestService")
        void shouldDelegateToConnectionTestService() {
            // given
            Provider provider = buildProvider(1L, "Test", 1L);
            when(providerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(provider);
            var expectedResult = new com.eify.provider.domain.dto.ConnectionTestResult();
            when(connectionTestService.testConnection(provider)).thenReturn(expectedResult);

            // when
            var result = providerService.testConnection(1L);

            // then
            assertSame(expectedResult, result);
            verify(connectionTestService).testConnection(provider);
        }
    }
}
