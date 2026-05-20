package com.eify.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eify.common.context.CurrentContext;
import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.MemberInfo;
import com.eify.auth.entity.User;
import com.eify.auth.entity.Workspace;
import com.eify.auth.entity.WorkspaceInvite;
import com.eify.auth.entity.WorkspaceMember;
import com.eify.auth.mapper.UserMapper;
import com.eify.auth.mapper.WorkspaceInviteMapper;
import com.eify.auth.mapper.WorkspaceMapper;
import com.eify.auth.mapper.WorkspaceMemberMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceInviteMapper inviteMapper;
    private final UserMapper userMapper;

    /**
     * 创建新工作空间，当前用户自动成为 owner。
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse.WorkspaceInfo createWorkspace(String name, String description) {
        Long userId = CurrentContext.getUserId();

        Workspace workspace = Workspace.builder()
                .name(name)
                .description(description)
                .build();
        workspace.setCreatorId(userId);
        workspaceMapper.insert(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(userId)
                .role("owner")
                .joinedAt(LocalDateTime.now())
                .build();
        member.setCreatorId(userId);
        memberMapper.insert(member);

        log.info("工作空间创建成功: workspaceId={}, name={}, ownerId={}", workspace.getId(), name, userId);

        return AuthResponse.WorkspaceInfo.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .role("owner")
                .build();
    }

    /**
     * 生成邀请码（仅 owner/admin 可操作）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String generateInviteCode(Long workspaceId) {
        Long userId = CurrentContext.getUserId();
        requireOwnerOrAdmin(workspaceId, userId);

        String code = RandomUtil.randomString(8).toUpperCase();
        WorkspaceInvite invite = WorkspaceInvite.builder()
                .workspaceId(workspaceId)
                .code(code)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .maxUses(1)
                .useCount(0)
                .enabled(1)
                .build();
        invite.setCreatorId(userId);
        inviteMapper.insert(invite);

        log.info("邀请码生成成功: workspaceId={}, code={}, createdBy={}", workspaceId, code, userId);
        return code;
    }

    /**
     * 凭邀请码加入工作空间。
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse.WorkspaceInfo joinByInviteCode(String code) {
        Long userId = CurrentContext.getUserId();

        WorkspaceInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<WorkspaceInvite>()
                .eq(WorkspaceInvite::getCode, code));
        if (invite == null || invite.getEnabled() == 0) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXPIRED);
        }
        if (invite.getMaxUses() > 0 && invite.getUseCount() >= invite.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
        }

        Long workspaceId = invite.getWorkspaceId();
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        // 检查是否已是活跃成员（@TableLogic 自动过滤 deleted=1）
        if (memberMapper.selectCount(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId)) > 0) {
            throw new BusinessException(ErrorCode.ALREADY_WORKSPACE_MEMBER);
        }

        // 检查软删除记录：若之前退出了，直接恢复
        WorkspaceMember existing = memberMapper.selectRaw(workspaceId, userId);
        if (existing != null) {
            existing.setRole("member");
            existing.setJoinedAt(LocalDateTime.now());
            existing.setDeleted(0);
            memberMapper.updateById(existing);
        } else {
            WorkspaceMember member = WorkspaceMember.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .role("member")
                    .joinedAt(LocalDateTime.now())
                    .build();
            member.setCreatorId(userId);
            memberMapper.insert(member);
        }

        // 更新使用次数
        invite.setUseCount(invite.getUseCount() + 1);
        inviteMapper.updateById(invite);

        log.info("用户加入工作空间: userId={}, workspaceId={}, code={}", userId, workspaceId, code);

        return AuthResponse.WorkspaceInfo.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .role("member")
                .build();
    }

    /**
     * 获取工作空间成员列表。
     */
    public List<MemberInfo> listMembers(Long workspaceId) {
        Long userId = CurrentContext.getUserId();
        requireMember(workspaceId, userId);

        List<WorkspaceMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId));
        if (members.isEmpty()) return List.of();

        List<Long> userIds = members.stream()
                .map(WorkspaceMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<MemberInfo> list = new ArrayList<>();
        for (WorkspaceMember m : members) {
            User user = userMap.get(m.getUserId());
            if (user != null) {
                list.add(MemberInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .email(user.getEmail())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build());
            }
        }
        return list;
    }

    /**
     * 移除成员（owner 可移除任何人，admin 可移除 member，不能移除自己）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long workspaceId, Long targetUserId) {
        Long userId = CurrentContext.getUserId();

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_SELF);
        }

        WorkspaceMember operator = requireOwnerOrAdmin(workspaceId, userId);
        WorkspaceMember target = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, targetUserId));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        if ("owner".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_OWNER);
        }
        if ("admin".equals(operator.getRole()) && "admin".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.WORKSPACE_ACCESS_DENIED, "管理员不能移除其他管理员");
        }

        memberMapper.deleteById(target.getId());
        log.info("成员被移除: workspaceId={}, targetUserId={}, operatorId={}", workspaceId, targetUserId, userId);
    }

    /**
     * 更新成员角色（仅 owner 可操作，不能修改自己的角色）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long workspaceId, Long targetUserId, String newRole) {
        Long userId = CurrentContext.getUserId();

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能修改自己的角色");
        }
        if (!"admin".equals(newRole) && !"member".equals(newRole)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的角色，只能设置为 admin 或 member");
        }

        requireOwner(workspaceId, userId);

        WorkspaceMember target = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, targetUserId));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        if ("owner".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能修改拥有者的角色");
        }

        target.setRole(newRole);
        memberMapper.updateById(target);
        log.info("成员角色已更新: workspaceId={}, targetUserId={}, newRole={}", workspaceId, targetUserId, newRole);
    }

    /**
     * 退出工作空间（owner 不能退出）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void leaveWorkspace(Long workspaceId) {
        Long userId = CurrentContext.getUserId();

        WorkspaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        if ("owner".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.CANNOT_LEAVE_AS_OWNER);
        }

        memberMapper.deleteById(member.getId());
        log.info("用户退出工作空间: userId={}, workspaceId={}", userId, workspaceId);
    }

    // ========== 权限校验 ==========

    private WorkspaceMember requireMember(Long workspaceId, Long userId) {
        WorkspaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        return member;
    }

    private WorkspaceMember requireOwnerOrAdmin(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        if (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.WORKSPACE_ACCESS_DENIED);
        }
        return member;
    }

    private void requireOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        if (!"owner".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.WORKSPACE_ACCESS_DENIED, "仅拥有者可执行此操作");
        }
    }
}
