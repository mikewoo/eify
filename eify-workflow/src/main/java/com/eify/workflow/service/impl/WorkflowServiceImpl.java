package com.eify.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.dto.WorkflowCreateRequest;
import com.eify.workflow.domain.dto.WorkflowDetailResponse;
import com.eify.workflow.domain.dto.WorkflowDetailResponse.EdgeDetail;
import com.eify.workflow.domain.dto.WorkflowDetailResponse.NodeDetail;
import com.eify.workflow.domain.dto.WorkflowResponse;
import com.eify.workflow.domain.dto.WorkflowUpdateRequest;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.mapper.WorkflowEdgeMapper;
import com.eify.workflow.mapper.WorkflowMapper;
import com.eify.workflow.mapper.WorkflowNodeMapper;
import com.eify.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;

    @Override
    public PageResult<WorkflowResponse> list(Integer page, Integer pageSize) {
        IPage<Workflow> iPage = workflowMapper.selectPage(
                PageHelper.toPage(page, pageSize),
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId())
                        .orderByDesc(Workflow::getId)
        );

        List<WorkflowResponse> list = iPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, iPage.getTotal(), (int) iPage.getCurrent(), (int) iPage.getSize());
    }

    @Override
    public WorkflowDetailResponse getById(Long id) {
        Workflow workflow = getWorkflowOrThrow(id);
        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, id));
        List<WorkflowEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, id));

        return toDetailResponse(workflow, nodes, edges);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResponse create(WorkflowCreateRequest request) {
        validateNodeTypes(request.getNodes());
        checkNameUnique(request.getName(), null);

        Workflow workflow = new Workflow();
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        workflow.setVersion(1);
        WorkspaceGuard.bind(workflow);
        workflow.setVariables(request.getVariables());
        workflowMapper.insert(workflow);

        Map<String, Long> nodeKeyMap = insertNodes(workflow.getId(), request.getNodes());
        insertEdges(workflow.getId(), request.getEdges(), nodeKeyMap);

        return getById(workflow.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResponse update(Long id, WorkflowUpdateRequest request) {
        Workflow workflow = getWorkflowOrThrow(id);

        if (request.getName() != null && !request.getName().equals(workflow.getName())) {
            checkNameUnique(request.getName(), id);
            workflow.setName(request.getName());
        }
        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            // 状态变为「已发布」时，版本号 +1
            if (request.getStatus() == 1 && !Integer.valueOf(1).equals(workflow.getStatus())) {
                workflow.setVersion(workflow.getVersion() + 1);
            }
            workflow.setStatus(request.getStatus());
        }
        if (request.getVariables() != null) {
            workflow.setVariables(request.getVariables());
        }
        workflowMapper.updateById(workflow);

        if (request.getNodes() != null && request.getEdges() != null) {
            replaceNodesAndEdges(id, request.getNodes(), request.getEdges());
        }

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getWorkflowOrThrow(id);

        edgeMapper.delete(new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, id));
        nodeMapper.delete(new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, id));
        workflowMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    private Workflow getWorkflowOrThrow(Long id) {
        Workflow workflow = workflowMapper.selectOne(
                new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getId, id)
                        .eq(Workflow::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (workflow == null) {
            throw new BusinessException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        return workflow;
    }

    private void checkNameUnique(String name, Long excludeId) {
        WorkspaceGuard.checkNameUnique(workflowMapper,
                Workflow::getName, Workflow::getWorkspaceId, Workflow::getId,
                name, excludeId, ErrorCode.WORKFLOW_NAME_DUPLICATE);
    }

    private void validateNodeTypes(List<WorkflowCreateRequest.NodeItem> nodes) {
        for (var node : nodes) {
            String normalized = normalizeType(node.getType());
            if (!NodeConfigParser.isValidType(normalized)) {
                throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "不支持的节点类型: " + node.getType());
            }
        }
    }

    private void validateNodeTypesForUpdate(List<WorkflowUpdateRequest.NodeItem> nodes) {
        for (var node : nodes) {
            if (node.getType() != null) {
                String normalized = normalizeType(node.getType());
                if (!NodeConfigParser.isValidType(normalized)) {
                    throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                            "不支持的节点类型: " + node.getType());
                }
            }
        }
    }

    /** 类型统一转小写：LLM→llm, CONDITION→condition */
    private String normalizeType(String type) {
        return type != null ? type.toLowerCase() : null;
    }

    /** 批量插入节点，返回 nodeKey → realId 映射 */
    private Map<String, Long> insertNodes(Long workflowId,
                                           List<?> nodeItems) {
        Map<String, Long> keyMap = new HashMap<>();

        for (var item : nodeItems) {
            WorkflowNode node = new WorkflowNode();
            node.setWorkflowId(workflowId);

            if (item instanceof WorkflowCreateRequest.NodeItem ci) {
                node.setNodeKey(ci.getNodeKey());
                node.setType(normalizeType(ci.getType()));
                node.setLabel(ci.getName());
                node.setPositionX(ci.getPositionX());
                node.setPositionY(ci.getPositionY());
                node.setConfig(ci.getConfig());
                nodeMapper.insert(node);
                keyMap.put(ci.getNodeKey(), node.getId());
            } else if (item instanceof WorkflowUpdateRequest.NodeItem ui) {
                node.setNodeKey(ui.getNodeKey());
                node.setType(normalizeType(ui.getType()));
                node.setLabel(ui.getName());
                node.setPositionX(ui.getPositionX());
                node.setPositionY(ui.getPositionY());
                node.setConfig(ui.getConfig());
                nodeMapper.insert(node);
                keyMap.put(ui.getNodeKey(), node.getId());
            }
        }
        return keyMap;
    }

    /** 批量插入边，将 nodeKey 引用替换为真实 ID */
    private void insertEdges(Long workflowId, List<?> edgeItems,
                              Map<String, Long> nodeKeyMap) {
        for (var item : edgeItems) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setWorkflowId(workflowId);

            if (item instanceof WorkflowCreateRequest.EdgeItem ci) {
                Long sourceId = nodeKeyMap.get(ci.getSourceNodeKey());
                Long targetId = nodeKeyMap.get(ci.getTargetNodeKey());
                if (sourceId == null || targetId == null) {
                    throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                            "连线引用了不存在的节点 nodeKey: " + ci.getSourceNodeKey() + " -> " + ci.getTargetNodeKey());
                }
                edge.setSourceNodeId(sourceId);
                edge.setTargetNodeId(targetId);
                edge.setSourceHandle(ci.getCondition() != null ? ci.getCondition() : "default");
                edge.setLabel(ci.getLabel());
            } else if (item instanceof WorkflowUpdateRequest.EdgeItem ui) {
                Long sourceId = nodeKeyMap.get(ui.getSourceNodeKey());
                Long targetId = nodeKeyMap.get(ui.getTargetNodeKey());
                if (sourceId == null || targetId == null) {
                    throw new BusinessException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                            "连线引用了不存在的节点 nodeKey");
                }
                edge.setSourceNodeId(sourceId);
                edge.setTargetNodeId(targetId);
                edge.setSourceHandle(ui.getCondition() != null ? ui.getCondition() : "default");
                edge.setLabel(ui.getLabel());
            }
            edgeMapper.insert(edge);
        }
    }

    private void replaceNodesAndEdges(Long workflowId,
                                       List<WorkflowUpdateRequest.NodeItem> nodes,
                                       List<WorkflowUpdateRequest.EdgeItem> edges) {
        validateNodeTypesForUpdate(nodes);

        edgeMapper.delete(new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, workflowId));
        nodeMapper.delete(new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, workflowId));

        Map<String, Long> nodeKeyMap = insertNodes(workflowId, nodes);
        insertEdges(workflowId, edges, nodeKeyMap);
    }

    private WorkflowResponse toResponse(Workflow w) {
        long nodeCount = nodeMapper.selectCount(
                new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, w.getId()));
        long edgeCount = edgeMapper.selectCount(
                new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, w.getId()));

        return WorkflowResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .status(w.getStatus())
                .version(w.getVersion())
                .nodeCount((int) nodeCount)
                .edgeCount((int) edgeCount)
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private WorkflowDetailResponse toDetailResponse(Workflow w,
                                                     List<WorkflowNode> nodes,
                                                     List<WorkflowEdge> edges) {
        return WorkflowDetailResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .status(w.getStatus())
                .version(w.getVersion())
                .variables(w.getVariables())
                .nodes(nodes.stream().map(this::toNodeDetail).collect(Collectors.toList()))
                .edges(edges.stream().map(this::toEdgeDetail).collect(Collectors.toList()))
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private NodeDetail toNodeDetail(WorkflowNode n) {
        return NodeDetail.builder()
                .id(n.getId())
                .workflowId(n.getWorkflowId())
                .nodeKey(n.getNodeKey())
                .type(n.getType())
                .name(n.getLabel())
                .positionX(n.getPositionX())
                .positionY(n.getPositionY())
                .config(n.getConfig())
                .build();
    }

    private EdgeDetail toEdgeDetail(WorkflowEdge e) {
        return EdgeDetail.builder()
                .id(e.getId())
                .workflowId(e.getWorkflowId())
                .sourceNodeId(e.getSourceNodeId())
                .targetNodeId(e.getTargetNodeId())
                .condition("default".equals(e.getSourceHandle()) ? null : e.getSourceHandle())
                .label(e.getLabel())
                .build();
    }
}
