package com.eify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.knowledge.domain.dto.request.KnowledgeCreateRequest;
import com.eify.knowledge.domain.dto.request.KnowledgeUpdateRequest;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.repository.KnowledgeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KnowledgeServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeServiceImpl")
class KnowledgeServiceImplTest {

    @Mock
    KnowledgeRepository knowledgeRepository;

    @Mock
    ChunkRepository chunkRepository;

    @Spy
    @InjectMocks
    KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L); // userId=1, workspaceId=1
        // ServiceImpl 基类的 baseMapper 需要手动注入
        ReflectionTestUtils.setField(knowledgeService, "baseMapper", knowledgeRepository);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private KnowledgeBase buildKnowledgeBase(Long id, String name, Long workspaceId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(name);
        kb.setWorkspaceId(workspaceId);
        kb.setDescription("测试知识库");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setVectorDimension(1536);
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setEnabled(1);
        return kb;
    }

    private KnowledgeCreateRequest buildCreateRequest(String name) {
        KnowledgeCreateRequest request = new KnowledgeCreateRequest();
        request.setName(name);
        request.setDescription("测试知识库");
        request.setEmbeddingModel("text-embedding-3-small");
        request.setVectorDimension(1536);
        request.setChunkSize(500);
        request.setChunkOverlap(50);
        return request;
    }

    // ========== createKnowledge() ==========

    @Nested
    @DisplayName("createKnowledge()")
    class CreateKnowledgeTests {

        @Test
        @DisplayName("P0 - 名称重复应抛异常")
        void shouldThrowWhenNameDuplicate() {
            // given
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L);

            KnowledgeCreateRequest request = buildCreateRequest("重复名称");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.createKnowledge(request));
            assertEquals(ErrorCode.KNOWLEDGE_NAME_DUPLICATE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 创建成功应绑定 workspaceId 并设置默认值")
        void shouldBindWorkspaceAndSetDefaults() {
            // given
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            doReturn(true).when(knowledgeService).save(any(KnowledgeBase.class));

            KnowledgeCreateRequest request = buildCreateRequest("新知识库");

            // when
            KnowledgeBase result = knowledgeService.createKnowledge(request);

            // then
            ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
            verify(knowledgeService).save(captor.capture());

            KnowledgeBase saved = captor.getValue();
            assertEquals("新知识库", saved.getName());
            assertEquals("测试知识库", saved.getDescription());
            assertEquals("text-embedding-3-small", saved.getEmbeddingModel());
            assertEquals(1536, saved.getVectorDimension());
            assertEquals(500, saved.getChunkSize());
            assertEquals(50, saved.getChunkOverlap());
            assertEquals(1L, saved.getWorkspaceId());
            assertEquals(1, saved.getEnabled());
            assertEquals(0, saved.getDocumentCount());
            assertEquals(0, saved.getChunkCount());

            // 返回的就是同一个实体
            assertSame(saved, result);
        }

        @Test
        @DisplayName("P1 - description 为 null 时不应报错")
        void shouldHandleNullDescription() {
            // given
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            doReturn(true).when(knowledgeService).save(any(KnowledgeBase.class));

            KnowledgeCreateRequest request = buildCreateRequest("无描述");
            request.setDescription(null);

            // when
            KnowledgeBase result = knowledgeService.createKnowledge(request);

            // then
            assertNull(result.getDescription());
            verify(knowledgeService).save(any(KnowledgeBase.class));
        }

        @Test
        @DisplayName("P1 - 创建时应设置默认启用状态和零计数")
        void shouldSetDefaultEnabledAndZeroCounts() {
            // given
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            doReturn(true).when(knowledgeService).save(any(KnowledgeBase.class));

            KnowledgeCreateRequest request = buildCreateRequest("新知识库");

            // when
            KnowledgeBase result = knowledgeService.createKnowledge(request);

            // then
            assertEquals(1, result.getEnabled());
            assertEquals(0, result.getDocumentCount());
            assertEquals(0, result.getChunkCount());
        }

        @Test
        @DisplayName("P1 - 名称重复时不应执行保存操作")
        void shouldNotSaveWhenNameDuplicate() {
            // given
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L);

            KnowledgeCreateRequest request = buildCreateRequest("重复名称");

            // when
            assertThrows(BusinessException.class,
                    () -> knowledgeService.createKnowledge(request));

            // then
            verify(knowledgeService, never()).save(any(KnowledgeBase.class));
        }
    }

    // ========== updateKnowledge() ==========

    @Nested
    @DisplayName("updateKnowledge()")
    class UpdateKnowledgeTests {

        @Test
        @DisplayName("P0 - 知识库不存在应抛异常")
        void shouldThrowWhenNotFound() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setName("更新名称");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.updateKnowledge(999L, request));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨工作空间更新应抛异常")
        void shouldThrowWhenInDifferentWorkspace() {
            // given - 知识库属于 workspace 999
            KnowledgeBase otherWorkspace = buildKnowledgeBase(1L, "其他空间", 999L);
            doReturn(otherWorkspace).when(knowledgeService).getById(1L);

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setName("尝试更新");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.updateKnowledge(1L, request));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 改名为已存在的名称应抛异常")
        void shouldThrowWhenNewNameDuplicate() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "旧名称", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L); // 名称已存在

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setName("重复名称");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.updateKnowledge(1L, request));
            assertEquals(ErrorCode.KNOWLEDGE_NAME_DUPLICATE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 部分更新只修改提供的字段")
        void shouldOnlyUpdateProvidedFields() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "原名称", 1L);
            existing.setDescription("原描述");
            existing.setChunkSize(500);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setChunkSize(1000);
            request.setChunkOverlap(100);
            // name, description, embeddingModel, vectorDimension 均为 null -> 不修改

            // when
            KnowledgeBase result = knowledgeService.updateKnowledge(1L, request);

            // then
            assertEquals("原名称", result.getName());         // 未修改
            assertEquals("原描述", result.getDescription());  // 未修改
            assertEquals(1000, result.getChunkSize());        // 已修改
            assertEquals(100, result.getChunkOverlap());      // 已修改
            verify(knowledgeService).updateById(any(KnowledgeBase.class));
        }

        @Test
        @DisplayName("P1 - 改名时应检查名称唯一性")
        void shouldCheckNameUniquenessWhenNameChanged() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "旧名称", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            when(knowledgeRepository.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 新名称不重复
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setName("新名称");

            // when
            KnowledgeBase result = knowledgeService.updateKnowledge(1L, request);

            // then
            assertEquals("新名称", result.getName());
            verify(knowledgeRepository).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 名称未变不应触发唯一性检查")
        void shouldSkipNameCheckWhenNameUnchanged() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "不变名称", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            KnowledgeUpdateRequest request = new KnowledgeUpdateRequest();
            request.setName("不变名称"); // 与原名称相同
            request.setDescription("新描述");

            // when
            KnowledgeBase result = knowledgeService.updateKnowledge(1L, request);

            // then
            assertEquals("不变名称", result.getName());
            assertEquals("新描述", result.getDescription());
            // 不应调用 selectCount（名称唯一性检查）
            verify(knowledgeRepository, never()).selectCount(any(LambdaQueryWrapper.class));
        }
    }

    // ========== deleteKnowledge() ==========

    @Nested
    @DisplayName("deleteKnowledge()")
    class DeleteKnowledgeTests {

        @Test
        @DisplayName("P0 - 知识库不存在应抛异常")
        void shouldThrowWhenNotFound() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.deleteKnowledge(999L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨工作空间删除应抛异常")
        void shouldThrowWhenInDifferentWorkspace() {
            // given
            KnowledgeBase otherWorkspace = buildKnowledgeBase(1L, "其他空间", 999L);
            doReturn(otherWorkspace).when(knowledgeService).getById(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.deleteKnowledge(1L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 正常删除应先删分块再删知识库")
        void shouldDeleteChunksThenKnowledgeBase() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "待删除", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).removeById(1L);

            // when
            assertDoesNotThrow(() -> knowledgeService.deleteKnowledge(1L));

            // then - 验证调用顺序：先删分块，再删知识库
            var inOrder = inOrder(chunkRepository, knowledgeService);
            inOrder.verify(chunkRepository).deleteByKnowledgeId(1L);
            inOrder.verify(knowledgeService).removeById(1L);
        }

        @Test
        @DisplayName("P1 - 校验失败时不应删除分块和知识库")
        void shouldNotDeleteAnythingWhenValidationFails() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            // when
            assertThrows(BusinessException.class,
                    () -> knowledgeService.deleteKnowledge(999L));

            // then
            verify(chunkRepository, never()).deleteByKnowledgeId(anyLong());
            verify(knowledgeService, never()).removeById(anyLong());
        }

        @Test
        @DisplayName("P1 - 删除无分块的知识库应正常完成")
        void shouldDeleteKnowledgeBaseWithoutChunks() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "空知识库", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).removeById(1L);

            // when & then
            assertDoesNotThrow(() -> knowledgeService.deleteKnowledge(1L));
            verify(chunkRepository).deleteByKnowledgeId(1L);
            verify(knowledgeService).removeById(1L);
        }
    }

    // ========== getKnowledge() ==========

    @Nested
    @DisplayName("getKnowledge()")
    class GetKnowledgeTests {

        @Test
        @DisplayName("P0 - 跨工作空间查询应抛异常")
        void shouldThrowWhenInDifferentWorkspace() {
            // given
            KnowledgeBase otherWorkspace = buildKnowledgeBase(1L, "其他空间", 999L);
            doReturn(otherWorkspace).when(knowledgeService).getById(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.getKnowledge(1L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 知识库不存在应抛异常")
        void shouldThrowWhenNotFound() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.getKnowledge(999L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 正常查询应返回知识库实体")
        void shouldReturnKnowledgeBase() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "我的知识库", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);

            // when
            KnowledgeBase result = knowledgeService.getKnowledge(1L);

            // then
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("我的知识库", result.getName());
            assertEquals(1L, result.getWorkspaceId());
        }

        @Test
        @DisplayName("P1 - 禁用状态的知识库仍可查询到")
        void shouldReturnDisabledKnowledgeBase() {
            // given - getKnowledge 不检查启用状态
            KnowledgeBase disabled = buildKnowledgeBase(1L, "已禁用", 1L);
            disabled.setEnabled(0);
            doReturn(disabled).when(knowledgeService).getById(1L);

            // when
            KnowledgeBase result = knowledgeService.getKnowledge(1L);

            // then
            assertNotNull(result);
            assertEquals(0, result.getEnabled());
        }

        @Test
        @DisplayName("P1 - 查询结果应包含完整的嵌入模型配置")
        void shouldReturnEntityWithEmbeddingConfig() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "知识库", 1L);
            existing.setEmbeddingModel("text-embedding-3-large");
            existing.setVectorDimension(3072);
            existing.setChunkSize(1000);
            existing.setChunkOverlap(100);
            doReturn(existing).when(knowledgeService).getById(1L);

            // when
            KnowledgeBase result = knowledgeService.getKnowledge(1L);

            // then
            assertEquals("text-embedding-3-large", result.getEmbeddingModel());
            assertEquals(3072, result.getVectorDimension());
            assertEquals(1000, result.getChunkSize());
            assertEquals(100, result.getChunkOverlap());
        }
    }

    // ========== listKnowledge() ==========

    @Nested
    @DisplayName("listKnowledge()")
    class ListKnowledgeTests {

        @Test
        @DisplayName("P1 - 正常分页返回结果")
        void shouldReturnPaginatedResults() {
            // given
            KnowledgeBase kb = buildKnowledgeBase(1L, "知识库A", 1L);
            Page<KnowledgeBase> pageObj = new Page<>(1, 20);
            pageObj.setRecords(List.of(kb));
            pageObj.setTotal(1);

            when(knowledgeRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<KnowledgeBase> result = knowledgeService.listKnowledge(1, 20);

            // then
            assertNotNull(result);
            assertEquals(1, result.getList().size());
            assertEquals("知识库A", result.getList().get(0).getName());
            assertEquals(1L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - 空列表应返回空结果")
        void shouldReturnEmptyListWhenNoRecords() {
            // given
            Page<KnowledgeBase> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(knowledgeRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<KnowledgeBase> result = knowledgeService.listKnowledge(1, 20);

            // then
            assertNotNull(result);
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - page 为 null 时应使用默认值 page=1")
        void shouldUseDefaultPageWhenNull() {
            // given
            Page<KnowledgeBase> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(knowledgeRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            knowledgeService.listKnowledge(null, null);

            // then
            ArgumentCaptor<Page<KnowledgeBase>> captor = ArgumentCaptor.forClass(Page.class);
            verify(knowledgeRepository).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
            assertEquals(1L, captor.getValue().getCurrent());
        }

        @Test
        @DisplayName("P1 - 多条记录应正确返回")
        void shouldReturnMultipleRecords() {
            // given
            KnowledgeBase kb1 = buildKnowledgeBase(1L, "知识库A", 1L);
            KnowledgeBase kb2 = buildKnowledgeBase(2L, "知识库B", 1L);
            Page<KnowledgeBase> pageObj = new Page<>(1, 20);
            pageObj.setRecords(List.of(kb2, kb1));
            pageObj.setTotal(2);

            when(knowledgeRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<KnowledgeBase> result = knowledgeService.listKnowledge(1, 20);

            // then
            assertEquals(2, result.getList().size());
            assertEquals(2L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - 分页参数应正确传递到 mapper")
        void shouldPassCorrectPaginationParams() {
            // given
            Page<KnowledgeBase> pageObj = new Page<>(2, 10);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(knowledgeRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            knowledgeService.listKnowledge(2, 10);

            // then
            ArgumentCaptor<Page<KnowledgeBase>> captor = ArgumentCaptor.forClass(Page.class);
            verify(knowledgeRepository).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
            assertEquals(2L, captor.getValue().getCurrent());
            assertEquals(10L, captor.getValue().getSize());
        }
    }

    // ========== toggleKnowledgeStatus() ==========

    @Nested
    @DisplayName("toggleKnowledgeStatus()")
    class ToggleKnowledgeStatusTests {

        @Test
        @DisplayName("P0 - 知识库不存在应抛异常")
        void shouldThrowWhenNotFound() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.toggleKnowledgeStatus(999L, 0));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨工作空间操作应抛异常")
        void shouldThrowWhenInDifferentWorkspace() {
            // given
            KnowledgeBase otherWorkspace = buildKnowledgeBase(1L, "其他空间", 999L);
            doReturn(otherWorkspace).when(knowledgeService).getById(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.toggleKnowledgeStatus(1L, 0));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 禁用知识库应将 enabled 设为 0")
        void shouldDisableKnowledgeBase() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "测试知识库", 1L);
            existing.setEnabled(1);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            // when
            knowledgeService.toggleKnowledgeStatus(1L, 0);

            // then
            ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
            verify(knowledgeService).updateById(captor.capture());
            assertEquals(0, captor.getValue().getEnabled());
        }

        @Test
        @DisplayName("P1 - 启用知识库应将 enabled 设为 1")
        void shouldEnableKnowledgeBase() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "测试知识库", 1L);
            existing.setEnabled(0);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            // when
            knowledgeService.toggleKnowledgeStatus(1L, 1);

            // then
            ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
            verify(knowledgeService).updateById(captor.capture());
            assertEquals(1, captor.getValue().getEnabled());
        }

        @Test
        @DisplayName("P1 - 切换状态后应调用 updateById 保存")
        void shouldCallUpdateByIdAfterToggle() {
            // given
            KnowledgeBase existing = buildKnowledgeBase(1L, "测试知识库", 1L);
            doReturn(existing).when(knowledgeService).getById(1L);
            doReturn(true).when(knowledgeService).updateById(any(KnowledgeBase.class));

            // when
            knowledgeService.toggleKnowledgeStatus(1L, 0);

            // then
            verify(knowledgeService).updateById(any(KnowledgeBase.class));
        }
    }

    // ========== isKnowledgeAvailable() ==========

    @Nested
    @DisplayName("isKnowledgeAvailable()")
    class IsKnowledgeAvailableTests {

        @Test
        @DisplayName("P0 - 知识库不存在应抛异常")
        void shouldThrowWhenNotFound() {
            // given
            doReturn(null).when(knowledgeService).getById(999L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.isKnowledgeAvailable(999L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨工作空间应抛异常")
        void shouldThrowWhenInDifferentWorkspace() {
            // given
            KnowledgeBase otherWorkspace = buildKnowledgeBase(1L, "其他空间", 999L);
            otherWorkspace.setEnabled(1);
            doReturn(otherWorkspace).when(knowledgeService).getById(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> knowledgeService.isKnowledgeAvailable(1L));
            assertEquals(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 知识库已禁用应返回 false")
        void shouldReturnFalseWhenDisabled() {
            // given
            KnowledgeBase disabled = buildKnowledgeBase(1L, "已禁用", 1L);
            disabled.setEnabled(0);
            doReturn(disabled).when(knowledgeService).getById(1L);

            // when
            boolean result = knowledgeService.isKnowledgeAvailable(1L);

            // then
            assertFalse(result);
        }

        @Test
        @DisplayName("P1 - 知识库存在且启用应返回 true")
        void shouldReturnTrueWhenAvailable() {
            // given
            KnowledgeBase available = buildKnowledgeBase(1L, "可用知识库", 1L);
            available.setEnabled(1);
            doReturn(available).when(knowledgeService).getById(1L);

            // when
            boolean result = knowledgeService.isKnowledgeAvailable(1L);

            // then
            assertTrue(result);
        }

        @Test
        @DisplayName("P1 - enabled 值不为 1 时应返回 false")
        void shouldReturnFalseWhenEnabledIsNotOne() {
            // given - enabled=2 不是有效启用状态
            KnowledgeBase kb = buildKnowledgeBase(1L, "异常状态", 1L);
            kb.setEnabled(2);
            doReturn(kb).when(knowledgeService).getById(1L);

            // when
            boolean result = knowledgeService.isKnowledgeAvailable(1L);

            // then
            assertFalse(result);
        }
    }
}
