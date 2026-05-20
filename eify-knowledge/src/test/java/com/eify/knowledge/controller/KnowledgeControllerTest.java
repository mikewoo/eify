package com.eify.knowledge.controller;

import com.eify.common.error.ErrorCode;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.knowledge.domain.dto.request.KnowledgeCreateRequest;
import com.eify.knowledge.domain.dto.request.KnowledgeUpdateRequest;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeController")
class KnowledgeControllerTest {

    @Mock
    KnowledgeService knowledgeService;

    @InjectMocks
    KnowledgeController controller;

    @Nested
    @DisplayName("createKnowledge")
    class CreateKnowledgeTests {

        @Test
        @DisplayName("创建成功应返回 Result.success")
        void shouldReturnSuccessWhenCreated() {
            KnowledgeCreateRequest req = new KnowledgeCreateRequest();
            req.setName("test");
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(1L);
            when(knowledgeService.createKnowledge(any())).thenReturn(kb);

            Result<KnowledgeBase> result = controller.createKnowledge(req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(kb);
        }
    }

    @Nested
    @DisplayName("getKnowledge")
    class GetKnowledgeTests {

        @Test
        @DisplayName("知识库存在应返回 Result.success")
        void shouldReturnSuccessWhenFound() {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(1L);
            when(knowledgeService.getKnowledge(1L)).thenReturn(kb);

            Result<KnowledgeBase> result = controller.getKnowledge(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(kb);
        }

        @Test
        @DisplayName("知识库不存在应返回 KNOWLEDGE_NOT_FOUND")
        void shouldReturnFailWhenNotFound() {
            when(knowledgeService.getKnowledge(1L)).thenReturn(null);

            Result<KnowledgeBase> result = controller.getKnowledge(1L);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getCode()).isEqualTo(ErrorCode.KNOWLEDGE_NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("updateKnowledge")
    class UpdateKnowledgeTests {

        @Test
        @DisplayName("更新成功应委托 service 并返回结果")
        void shouldDelegateAndReturnResult() {
            KnowledgeUpdateRequest req = new KnowledgeUpdateRequest();
            req.setName("updated");
            KnowledgeBase kb = new KnowledgeBase();
            when(knowledgeService.updateKnowledge(1L, req)).thenReturn(kb);

            Result<KnowledgeBase> result = controller.updateKnowledge(1L, req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(kb);
        }
    }

    @Nested
    @DisplayName("deleteKnowledge")
    class DeleteKnowledgeTests {

        @Test
        @DisplayName("删除成功应返回 Result.success")
        void shouldReturnSuccessWhenDeleted() {
            Result<Void> result = controller.deleteKnowledge(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(knowledgeService).deleteKnowledge(1L);
        }
    }

    @Nested
    @DisplayName("listKnowledge")
    class ListKnowledgeTests {

        @Test
        @DisplayName("分页查询应返回 Result.success")
        void shouldReturnPagedResult() {
            PageResult<KnowledgeBase> pageResult = new PageResult<>();
            when(knowledgeService.listKnowledge(2, 10)).thenReturn(pageResult);

            Result<PageResult<KnowledgeBase>> result = controller.listKnowledge(2, 10);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isSameAs(pageResult);
        }
    }

    @Nested
    @DisplayName("toggleStatus")
    class ToggleStatusTests {

        @Test
        @DisplayName("状态切换应委托 service 并返回成功")
        void shouldDelegateAndReturnSuccess() {
            Result<Void> result = controller.toggleStatus(1L, 1);

            assertThat(result.isSuccess()).isTrue();
            verify(knowledgeService).toggleKnowledgeStatus(1L, 1);
        }
    }
}
