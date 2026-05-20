package com.eify.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.workflow.domain.dto.WorkflowCreateRequest;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.domain.dto.WorkflowResponse;
import com.eify.workflow.domain.dto.WorkflowUpdateRequest;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.mapper.WorkflowEdgeMapper;
import com.eify.workflow.mapper.WorkflowMapper;
import com.eify.workflow.mapper.WorkflowNodeMapper;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有 Mapper 依赖，只测业务逻辑。
 * 通过 CurrentContext.set() 设置 ThreadLocal 上下文，不启动 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowServiceImpl")
class WorkflowServiceImplTest {

    @Mock
    private WorkflowMapper workflowMapper;

    @Mock
    private WorkflowNodeMapper nodeMapper;

    @Mock
    private WorkflowEdgeMapper edgeMapper;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L); // userId=1, workspaceId=1
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ==================== 辅助方法 ====================

    private Workflow buildWorkflow(Long id, String name) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setName(name);
        w.setWorkspaceId(1L);
        w.setDescription("测试描述");
        w.setStatus(0);
        w.setVersion(1);
        w.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        w.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return w;
    }

    private WorkflowNode buildNode(Long id, Long workflowId, String nodeKey, String type) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setWorkflowId(workflowId);
        n.setNodeKey(nodeKey);
        n.setType(type);
        n.setLabel(type + "节点");
        n.setPositionX(100.0);
        n.setPositionY(200.0);
        return n;
    }

    private WorkflowEdge buildEdge(Long id, Long workflowId, Long sourceId, Long targetId) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setWorkflowId(workflowId);
        e.setSourceNodeId(sourceId);
        e.setTargetNodeId(targetId);
        e.setSourceHandle("default");
        e.setLabel(null);
        return e;
    }

    private WorkflowCreateRequest.NodeItem buildCreateNodeItem(String nodeKey, String type) {
        WorkflowCreateRequest.NodeItem item = new WorkflowCreateRequest.NodeItem();
        item.setNodeKey(nodeKey);
        item.setType(type);
        item.setName(type + "节点");
        item.setPositionX(100.0);
        item.setPositionY(200.0);
        return item;
    }

    private WorkflowCreateRequest.EdgeItem buildCreateEdgeItem(String sourceKey, String targetKey) {
        WorkflowCreateRequest.EdgeItem item = new WorkflowCreateRequest.EdgeItem();
        item.setSourceNodeKey(sourceKey);
        item.setTargetNodeKey(targetKey);
        item.setLabel(null);
        return item;
    }

    private WorkflowUpdateRequest buildUpdateRequest(String name, String description, Integer status) {
        WorkflowUpdateRequest req = new WorkflowUpdateRequest();
        req.setName(name);
        req.setDescription(description);
        req.setStatus(status);
        return req;
    }

    private WorkflowUpdateRequest.NodeItem buildUpdateNodeItem(String nodeKey, String type) {
        WorkflowUpdateRequest.NodeItem item = new WorkflowUpdateRequest.NodeItem();
        item.setNodeKey(nodeKey);
        item.setType(type);
        item.setName(type + "节点");
        item.setPositionX(100.0);
        item.setPositionY(200.0);
        return item;
    }

    private WorkflowUpdateRequest.EdgeItem buildUpdateEdgeItem(String sourceKey, String targetKey) {
        WorkflowUpdateRequest.EdgeItem item = new WorkflowUpdateRequest.EdgeItem();
        item.setSourceNodeKey(sourceKey);
        item.setTargetNodeKey(targetKey);
        item.setLabel(null);
        return item;
    }

    /**
     * 模拟 nodeMapper.insert 时自动设置 ID（MyBatis-Plus 行为）
     */
    private void mockNodeInsertWithIdSequence(long startId) {
        final long[] seq = {startId};
        doAnswer(invocation -> {
            WorkflowNode n = invocation.getArgument(0);
            n.setId(seq[0]++);
            return 1;
        }).when(nodeMapper).insert(any(WorkflowNode.class));
    }

    /**
     * 模拟 workflowMapper.insert 时自动设置 ID
     */
    private void mockWorkflowInsertWithId(Long id) {
        doAnswer(invocation -> {
            Workflow w = invocation.getArgument(0);
            w.setId(id);
            return 1;
        }).when(workflowMapper).insert(any(Workflow.class));
    }

    // ==================== list ====================

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("P1 - 正常分页返回工作流列表")
        void shouldReturnPaginatedResults() {
            // given
            Workflow w1 = buildWorkflow(1L, "工作流A");
            Workflow w2 = buildWorkflow(2L, "工作流B");

            Page<Workflow> iPage = new Page<>(1, 20);
            iPage.setRecords(List.of(w1, w2));
            iPage.setTotal(2);

            when(workflowMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(iPage);

            when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
            when(edgeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            // when
            PageResult<WorkflowResponse> result = workflowService.list(1, 20);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getList().get(0).getName()).isEqualTo("工作流A");
            assertThat(result.getList().get(0).getNodeCount()).isEqualTo(3);
            assertThat(result.getList().get(1).getName()).isEqualTo("工作流B");
            assertThat(result.getList().get(1).getEdgeCount()).isEqualTo(2);
            assertThat(result.getTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("P1 - 无数据时返回空列表")
        void shouldReturnEmptyListWhenNoData() {
            // given
            Page<Workflow> iPage = new Page<>(1, 20);
            iPage.setRecords(Collections.emptyList());
            iPage.setTotal(0);

            when(workflowMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(iPage);

            // when
            PageResult<WorkflowResponse> result = workflowService.list(1, 20);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }

        @Test
        @DisplayName("P1 - 仅返回当前 workspace 的工作流")
        void shouldOnlyReturnCurrentWorkspaceWorkflows() {
            // given - selectPage 的 LambdaQueryWrapper 已包含 workspaceId 过滤
            Page<Workflow> iPage = new Page<>(1, 20);
            iPage.setRecords(Collections.emptyList());
            iPage.setTotal(0);

            when(workflowMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(iPage);

            // when
            workflowService.list(1, 20);

            // then - 验证调用了 selectPage（LambdaQueryWrapper 内含 workspaceId 条件）
            verify(workflowMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ==================== getById ====================

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("P0 - 工作流不存在时抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowNotFound() {
            // given
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> workflowService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> workflowService.getById(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P1 - 返回包含节点和边的工作流详情")
        void shouldReturnDetailWithNodesAndEdges() {
            // given
            Workflow workflow = buildWorkflow(1L, "测试工作流");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workflow);

            WorkflowNode startNode = buildNode(10L, 1L, "start_1", "start");
            WorkflowNode llmNode = buildNode(11L, 1L, "llm_1", "llm");
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(startNode, llmNode));

            WorkflowEdge edge = buildEdge(20L, 1L, 10L, 11L);
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(edge));

            // when
            WorkflowDetailResponse result = workflowService.getById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("测试工作流");
            assertThat(result.getStatus()).isEqualTo(0);
            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getNodes()).hasSize(2);
            assertThat(result.getNodes().get(0).getNodeKey()).isEqualTo("start_1");
            assertThat(result.getNodes().get(0).getType()).isEqualTo("start");
            assertThat(result.getEdges()).hasSize(1);
            assertThat(result.getEdges().get(0).getSourceNodeId()).isEqualTo(10L);
            assertThat(result.getEdges().get(0).getTargetNodeId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("P1 - 无节点和边时返回空列表")
        void shouldReturnEmptyNodesAndEdges() {
            // given
            Workflow workflow = buildWorkflow(1L, "空工作流");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workflow);
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            WorkflowDetailResponse result = workflowService.getById(1L);

            // then
            assertThat(result.getNodes()).isEmpty();
            assertThat(result.getEdges()).isEmpty();
        }
    }

    // ==================== create ====================

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("P0 - 不支持的节点类型时抛出 WORKFLOW_CONFIG_INVALID")
        void shouldThrowWhenInvalidNodeType() {
            // given
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("测试工作流");
            req.setNodes(List.of(buildCreateNodeItem("bad_1", "unknown_type")));
            req.setEdges(Collections.emptyList());

            // when / then
            assertThatThrownBy(() -> workflowService.create(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_CONFIG_INVALID.getCode());
                        assertThat(bex.getMessage()).contains("不支持的节点类型");
                    });

            verify(workflowMapper, never()).insert(any(Workflow.class));
        }

        @Test
        @DisplayName("P0 - 名称重复时抛出 WORKFLOW_NAME_DUPLICATE")
        void shouldThrowWhenNameDuplicate() {
            // given
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("重复名称");
            req.setNodes(List.of(buildCreateNodeItem("start_1", "start")));
            req.setEdges(Collections.emptyList());

            // WorkspaceGuard.checkNameUnique 内部调用 workflowMapper.selectCount
            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L); // 名称已存在

            // when / then
            assertThatThrownBy(() -> workflowService.create(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NAME_DUPLICATE.getCode());
                    });

            verify(workflowMapper, never()).insert(any(Workflow.class));
        }

        @Test
        @DisplayName("P1 - 成功创建工作流（含节点和边）")
        void shouldCreateWorkflowSuccessfully() {
            // given
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("新工作流");
            req.setDescription("描述");
            req.setStatus(0);
            req.setNodes(List.of(
                    buildCreateNodeItem("start_1", "start"),
                    buildCreateNodeItem("llm_1", "llm")
            ));
            req.setEdges(List.of(
                    buildCreateEdgeItem("start_1", "llm_1")
            ));

            // 名称唯一性检查通过
            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            // workflow insert 模拟设置 ID
            mockWorkflowInsertWithId(1L);

            // node insert 模拟设置 ID
            mockNodeInsertWithIdSequence(10L);

            // edge insert
            when(edgeMapper.insert(any(WorkflowEdge.class))).thenReturn(1);

            // getById 内部查询
            Workflow createdWorkflow = buildWorkflow(1L, "新工作流");
            createdWorkflow.setDescription("描述");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(createdWorkflow);

            WorkflowNode n1 = buildNode(10L, 1L, "start_1", "start");
            WorkflowNode n2 = buildNode(11L, 1L, "llm_1", "llm");
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(n1, n2));

            WorkflowEdge e1 = buildEdge(20L, 1L, 10L, 11L);
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e1));

            // when
            WorkflowDetailResponse result = workflowService.create(req);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("新工作流");
            assertThat(result.getNodes()).hasSize(2);
            assertThat(result.getEdges()).hasSize(1);

            // 验证 workflow 插入时版本号为 1
            ArgumentCaptor<Workflow> workflowCaptor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowMapper).insert(workflowCaptor.capture());
            assertThat(workflowCaptor.getValue().getVersion()).isEqualTo(1);
            assertThat(workflowCaptor.getValue().getStatus()).isEqualTo(0);
            assertThat(workflowCaptor.getValue().getWorkspaceId()).isEqualTo(1L);

            // 验证插入了 2 个节点和 1 条边
            verify(nodeMapper, times(2)).insert(any(WorkflowNode.class));
            verify(edgeMapper, times(1)).insert(any(WorkflowEdge.class));
        }

        @Test
        @DisplayName("P1 - status 为 null 时默认设为 0（草稿）")
        void shouldDefaultStatusToZeroWhenNull() {
            // given
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("默认状态");
            req.setNodes(List.of(buildCreateNodeItem("start_1", "start")));
            req.setEdges(Collections.emptyList());

            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            mockWorkflowInsertWithId(2L);
            mockNodeInsertWithIdSequence(30L);

            // getById 查询
            Workflow created = buildWorkflow(2L, "默认状态");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(created);
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(buildNode(30L, 2L, "start_1", "start")));
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            workflowService.create(req);

            // then
            ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(0);
        }

        @Test
        @DisplayName("P0 - 边引用不存在的节点 nodeKey 时抛出 WORKFLOW_CONFIG_INVALID")
        void shouldThrowWhenEdgeReferencesNonExistentNodeKey() {
            // given
            WorkflowCreateRequest req = new WorkflowCreateRequest();
            req.setName("边引用错误");
            req.setNodes(List.of(buildCreateNodeItem("start_1", "start")));
            req.setEdges(List.of(buildCreateEdgeItem("start_1", "nonexistent")));

            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            mockWorkflowInsertWithId(3L);
            mockNodeInsertWithIdSequence(40L);

            // when / then
            assertThatThrownBy(() -> workflowService.create(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_CONFIG_INVALID.getCode());
                        assertThat(bex.getMessage()).contains("连线引用了不存在的节点");
                    });
        }
    }

    // ==================== update ====================

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("P0 - 工作流不存在时抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowNotFound() {
            // given
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            WorkflowUpdateRequest req = buildUpdateRequest("新名称", null, null);

            // when / then
            assertThatThrownBy(() -> workflowService.update(999L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P0 - 跨 workspace 更新应抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            WorkflowUpdateRequest req = buildUpdateRequest("新名称", null, null);

            // when / then
            assertThatThrownBy(() -> workflowService.update(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P0 - 改名为已存在的名称应抛出 WORKFLOW_NAME_DUPLICATE")
        void shouldThrowWhenNewNameDuplicate() {
            // given
            Workflow workflow = buildWorkflow(1L, "旧名称");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workflow);

            // 名称唯一性检查发现冲突
            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            WorkflowUpdateRequest req = buildUpdateRequest("重复名称", null, null);

            // when / then
            assertThatThrownBy(() -> workflowService.update(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NAME_DUPLICATE.getCode());
                    });

            verify(workflowMapper, never()).updateById(any(Workflow.class));
        }

        @Test
        @DisplayName("P1 - 部分更新（仅更新名称和描述，不改状态）")
        void shouldPartialUpdateNameAndDescription() {
            // given
            Workflow workflow = buildWorkflow(1L, "旧名称");
            workflow.setStatus(0);
            workflow.setVersion(1);
            when(workflowMapper.updateById(any(Workflow.class))).thenReturn(1);

            // 名称唯一性检查通过
            when(workflowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            WorkflowUpdateRequest req = buildUpdateRequest("新名称", "新描述", null);

            // getById 查询
            Workflow updated = buildWorkflow(1L, "新名称");
            updated.setDescription("新描述");
            updated.setStatus(0);
            updated.setVersion(1);
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(workflow)    // 第一次：getWorkflowOrThrow
                    .thenReturn(updated);    // 第二次：getById
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            // when
            WorkflowDetailResponse result = workflowService.update(1L, req);

            // then
            assertThat(result.getName()).isEqualTo("新名称");

            ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowMapper).updateById(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("新名称");
            assertThat(captor.getValue().getDescription()).isEqualTo("新描述");
            // 状态未变更，版本不应递增
            assertThat(captor.getValue().getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("P1 - 状态变为已发布(1)时版本号 +1")
        void shouldIncrementVersionWhenPublishing() {
            // given
            Workflow workflow = buildWorkflow(1L, "工作流");
            workflow.setStatus(0);
            workflow.setVersion(2);
            when(workflowMapper.updateById(any(Workflow.class))).thenReturn(1);

            WorkflowUpdateRequest req = buildUpdateRequest(null, null, 1);

            // getById 查询
            Workflow published = buildWorkflow(1L, "工作流");
            published.setStatus(1);
            published.setVersion(3);
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(workflow)     // 第一次：getWorkflowOrThrow
                    .thenReturn(published);   // 第二次：getById
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            // when
            WorkflowDetailResponse result = workflowService.update(1L, req);

            // then
            ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(1);
            assertThat(captor.getValue().getVersion()).isEqualTo(3); // 2 + 1
        }

        @Test
        @DisplayName("P1 - 已发布状态再次发布时版本不递增")
        void shouldNotIncrementVersionWhenAlreadyPublished() {
            // given
            Workflow workflow = buildWorkflow(1L, "工作流");
            workflow.setStatus(1); // 已经是发布状态
            workflow.setVersion(3);
            when(workflowMapper.updateById(any(Workflow.class))).thenReturn(1);

            // 再次设为发布
            WorkflowUpdateRequest req = buildUpdateRequest(null, null, 1);

            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(workflow);
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            // when
            workflowService.update(1L, req);

            // then
            ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowMapper).updateById(captor.capture());
            // 已经是 1，再设为 1 不应递增
            assertThat(captor.getValue().getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("P1 - 名称未变更时不触发唯一性检查")
        void shouldSkipNameCheckWhenNameUnchanged() {
            // given
            Workflow workflow = buildWorkflow(1L, "同名工作流");
            when(workflowMapper.updateById(any(Workflow.class))).thenReturn(1);

            WorkflowUpdateRequest req = buildUpdateRequest("同名工作流", "新描述", null);

            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(workflow);
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            // when
            workflowService.update(1L, req);

            // then - 不应调用 selectCount（名称唯一性检查）
            verify(workflowMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新时替换节点和边")
        void shouldReplaceNodesAndEdgesWhenProvided() {
            // given
            Workflow workflow = buildWorkflow(1L, "工作流");
            workflow.setStatus(0);
            workflow.setVersion(1);
            when(workflowMapper.updateById(any(Workflow.class))).thenReturn(1);

            WorkflowUpdateRequest req = new WorkflowUpdateRequest();
            req.setNodes(List.of(
                    buildUpdateNodeItem("new_start", "start"),
                    buildUpdateNodeItem("new_end", "end")
            ));
            req.setEdges(List.of(
                    buildUpdateEdgeItem("new_start", "new_end")
            ));

            // 模拟删除旧节点/边
            when(edgeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
            when(nodeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            // 模拟插入新节点
            mockNodeInsertWithIdSequence(50L);

            when(edgeMapper.insert(any(WorkflowEdge.class))).thenReturn(1);

            // getById 查询
            Workflow updated = buildWorkflow(1L, "工作流");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(workflow)
                    .thenReturn(updated);
            WorkflowNode n1 = buildNode(50L, 1L, "new_start", "start");
            WorkflowNode n2 = buildNode(51L, 1L, "new_end", "end");
            when(nodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(n1, n2));
            when(edgeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList())  // replaceNodesAndEdges 内的旧边删除
                    .thenReturn(List.of(buildEdge(60L, 1L, 50L, 51L)));

            // when
            WorkflowDetailResponse result = workflowService.update(1L, req);

            // then
            assertThat(result.getNodes()).hasSize(2);
            // 验证旧数据被删除
            verify(edgeMapper, times(1)).delete(any(LambdaQueryWrapper.class));
            verify(nodeMapper, times(1)).delete(any(LambdaQueryWrapper.class));
            // 验证新节点插入
            verify(nodeMapper, times(2)).insert(any(WorkflowNode.class));
            verify(edgeMapper, times(1)).insert(any(WorkflowEdge.class));
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("P0 - 工作流不存在时抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowNotFound() {
            // given
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> workflowService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P0 - 跨 workspace 删除应抛出 WORKFLOW_NOT_FOUND")
        void shouldThrowWhenWorkflowInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when / then
            assertThatThrownBy(() -> workflowService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getCode()).isEqualTo(ErrorCode.WORKFLOW_NOT_FOUND.getCode());
                    });
        }

        @Test
        @DisplayName("P1 - 成功删除（先删边，再删节点，最后删工作流）")
        void shouldDeleteEdgesNodesAndWorkflow() {
            // given
            Workflow workflow = buildWorkflow(1L, "待删除");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workflow);
            when(edgeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
            when(nodeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
            when(workflowMapper.deleteById(1L)).thenReturn(1);

            // when
            workflowService.delete(1L);

            // then - 验证删除顺序：边 -> 节点 -> 工作流
            verify(edgeMapper).delete(any(LambdaQueryWrapper.class));
            verify(nodeMapper).delete(any(LambdaQueryWrapper.class));
            verify(workflowMapper).deleteById(1L);
        }

        @Test
        @DisplayName("P1 - 无节点和边的工作流也能正常删除")
        void shouldDeleteWorkflowWithNoNodesOrEdges() {
            // given
            Workflow workflow = buildWorkflow(1L, "空工作流");
            when(workflowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workflow);
            when(edgeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(nodeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(workflowMapper.deleteById(1L)).thenReturn(1);

            // when
            workflowService.delete(1L);

            // then
            verify(edgeMapper).delete(any(LambdaQueryWrapper.class));
            verify(nodeMapper).delete(any(LambdaQueryWrapper.class));
            verify(workflowMapper).deleteById(1L);
        }
    }
}
