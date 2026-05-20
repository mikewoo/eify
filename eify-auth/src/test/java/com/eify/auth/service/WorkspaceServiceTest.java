package com.eify.auth.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceService")
class WorkspaceServiceTest {

    @Mock WorkspaceMapper workspaceMapper;
    @Mock WorkspaceMemberMapper memberMapper;
    @Mock WorkspaceInviteMapper inviteMapper;
    @Mock UserMapper userMapper;

    WorkspaceService workspaceService;

    private static final Long USER_ID = 100L;
    private static final Long WS_ID = 10L;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceMapper, memberMapper, inviteMapper, userMapper);
        CurrentContext.set(USER_ID, WS_ID);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    private Workspace buildWorkspace(Long id, String name) {
        Workspace ws = new Workspace();
        ws.setId(id);
        ws.setName(name);
        ws.setDescription("desc");
        return ws;
    }

    private WorkspaceMember buildMember(Long id, Long wsId, Long userId, String role) {
        WorkspaceMember m = new WorkspaceMember();
        m.setId(id);
        m.setWorkspaceId(wsId);
        m.setUserId(userId);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        return m;
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("Display " + username);
        user.setEmail(username + "@test.com");
        return user;
    }

    // ==================== createWorkspace ====================

    @Nested
    @DisplayName("createWorkspace")
    class CreateWorkspace {

        @Test
        @DisplayName("创建工作空间并自动成为 owner")
        void shouldCreateWorkspaceAndAddOwner() {
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(inv -> {
                Workspace ws = inv.getArgument(0);
                ws.setId(200L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            AuthResponse.WorkspaceInfo result = workspaceService.createWorkspace("My WS", "desc");

            assertThat(result.getId()).isEqualTo(200L);
            assertThat(result.getName()).isEqualTo("My WS");
            assertThat(result.getRole()).isEqualTo("owner");

            ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
            verify(memberMapper).insert(memberCaptor.capture());
            WorkspaceMember member = memberCaptor.getValue();
            assertThat(member.getUserId()).isEqualTo(USER_ID);
            assertThat(member.getRole()).isEqualTo("owner");
            assertThat(member.getWorkspaceId()).isEqualTo(200L);
        }
    }

    // ==================== generateInviteCode ====================

    @Nested
    @DisplayName("generateInviteCode")
    class GenerateInviteCode {

        @Test
        @DisplayName("owner 生成邀请码成功")
        void shouldGenerateInviteCodeAsOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"));
            when(inviteMapper.insert(any(WorkspaceInvite.class))).thenReturn(1);

            String code = workspaceService.generateInviteCode(WS_ID);

            assertThat(code).isNotNull().hasSize(8);
            ArgumentCaptor<WorkspaceInvite> captor = ArgumentCaptor.forClass(WorkspaceInvite.class);
            verify(inviteMapper).insert(captor.capture());
            WorkspaceInvite invite = captor.getValue();
            assertThat(invite.getWorkspaceId()).isEqualTo(WS_ID);
            assertThat(invite.getEnabled()).isEqualTo(1);
            assertThat(invite.getMaxUses()).isEqualTo(1);
        }

        @Test
        @DisplayName("admin 生成邀请码成功")
        void shouldGenerateInviteCodeAsAdmin() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "admin"));
            when(inviteMapper.insert(any(WorkspaceInvite.class))).thenReturn(1);

            String code = workspaceService.generateInviteCode(WS_ID);

            assertThat(code).isNotNull();
        }

        @Test
        @DisplayName("普通成员无权生成邀请码")
        void shouldThrowWhenNotOwnerOrAdmin() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "member"));

            assertThatThrownBy(() -> workspaceService.generateInviteCode(WS_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== joinByInviteCode ====================

    @Nested
    @DisplayName("joinByInviteCode")
    class JoinByInviteCode {

        @Test
        @DisplayName("凭有效邀请码加入成功")
        void shouldJoinByValidInviteCode() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(WS_ID).code("ABC12345")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .maxUses(5).useCount(1).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);
            when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(memberMapper.selectRaw(WS_ID, USER_ID)).thenReturn(null);
            when(workspaceMapper.selectById(WS_ID)).thenReturn(buildWorkspace(WS_ID, "Test WS"));
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);
            when(inviteMapper.updateById(invite)).thenReturn(1);

            AuthResponse.WorkspaceInfo result = workspaceService.joinByInviteCode("ABC12345");

            assertThat(result.getId()).isEqualTo(WS_ID);
            assertThat(result.getRole()).isEqualTo("member");
            assertThat(invite.getUseCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("邀请码不存在或已禁用时抛出异常")
        void shouldThrowWhenInviteInvalid() {
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> workspaceService.joinByInviteCode("INVALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邀请码无效");
        }

        @Test
        @DisplayName("邀请码已过期时抛出异常")
        void shouldThrowWhenInviteExpired() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(WS_ID).code("OLD")
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .maxUses(5).useCount(0).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);

            assertThatThrownBy(() -> workspaceService.joinByInviteCode("OLD"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邀请码已过期");
        }

        @Test
        @DisplayName("邀请码使用次数已耗尽时抛出异常")
        void shouldThrowWhenInviteExhausted() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(WS_ID).code("FULL")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .maxUses(3).useCount(3).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);

            assertThatThrownBy(() -> workspaceService.joinByInviteCode("FULL"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邀请码使用次数已用完");
        }

        @Test
        @DisplayName("工作空间不存在时抛出异常")
        void shouldThrowWhenWorkspaceNotFound() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(999L).code("GHOST")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .maxUses(5).useCount(0).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);
            when(workspaceMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> workspaceService.joinByInviteCode("GHOST"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("工作空间不存在");
        }

        @Test
        @DisplayName("已是活跃成员时抛出异常")
        void shouldThrowWhenAlreadyMember() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(WS_ID).code("DUPE")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .maxUses(5).useCount(0).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);
            when(workspaceMapper.selectById(WS_ID)).thenReturn(buildWorkspace(WS_ID, "Test WS"));
            when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> workspaceService.joinByInviteCode("DUPE"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已是该工作空间成员");
        }

        @Test
        @DisplayName("之前退出的成员通过邀请码恢复")
        void shouldRecoverSoftDeletedMember() {
            WorkspaceInvite invite = WorkspaceInvite.builder()
                    .workspaceId(WS_ID).code("COMEBACK")
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .maxUses(5).useCount(0).enabled(1).build();
            when(inviteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invite);
            when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            WorkspaceMember deletedMember = buildMember(5L, WS_ID, USER_ID, "member");
            deletedMember.setDeleted(1);
            when(memberMapper.selectRaw(WS_ID, USER_ID)).thenReturn(deletedMember);
            when(workspaceMapper.selectById(WS_ID)).thenReturn(buildWorkspace(WS_ID, "Test WS"));
            when(memberMapper.updateById(any(WorkspaceMember.class))).thenReturn(1);
            when(inviteMapper.updateById(invite)).thenReturn(1);

            AuthResponse.WorkspaceInfo result = workspaceService.joinByInviteCode("COMEBACK");

            assertThat(result.getId()).isEqualTo(WS_ID);
            assertThat(result.getRole()).isEqualTo("member");
            verify(memberMapper, never()).insert(any(WorkspaceMember.class));
        }
    }

    // ==================== listMembers ====================

    @Nested
    @DisplayName("listMembers")
    class ListMembers {

        @Test
        @DisplayName("返回成员列表含用户信息")
        void shouldListMembersWithUserInfo() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "admin"));
            WorkspaceMember m1 = buildMember(1L, WS_ID, USER_ID, "admin");
            WorkspaceMember m2 = buildMember(2L, WS_ID, 200L, "member");
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(m1, m2));
            when(userMapper.selectBatchIds(anyCollection()))
                    .thenReturn(List.of(buildUser(USER_ID, "user1"), buildUser(200L, "user2")));

            List<MemberInfo> members = workspaceService.listMembers(WS_ID);

            assertThat(members).hasSize(2);
            assertThat(members.get(0).getUsername()).isEqualTo("user1");
            assertThat(members.get(0).getRole()).isEqualTo("admin");
            assertThat(members.get(1).getUsername()).isEqualTo("user2");
        }

        @Test
        @DisplayName("非成员查询时抛出异常")
        void shouldThrowWhenNotMember() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> workspaceService.listMembers(WS_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("无成员时返回空列表")
        void shouldReturnEmptyListWhenNoMembers() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "admin"));
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<MemberInfo> members = workspaceService.listMembers(WS_ID);

            assertThat(members).isEmpty();
        }
    }

    // ==================== removeMember ====================

    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("owner 成功移除普通成员")
        void shouldRemoveMemberAsOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"))
                    .thenReturn(buildMember(2L, WS_ID, 200L, "member"));
            when(memberMapper.deleteById(2L)).thenReturn(1);

            workspaceService.removeMember(WS_ID, 200L);

            verify(memberMapper).deleteById(2L);
        }

        @Test
        @DisplayName("不能移除自己")
        void shouldThrowWhenRemoveSelf() {
            assertThatThrownBy(() -> workspaceService.removeMember(WS_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能移除自己");
        }

        @Test
        @DisplayName("目标用户不是成员时抛出异常")
        void shouldThrowWhenTargetNotMember() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"))
                    .thenReturn(null);

            assertThatThrownBy(() -> workspaceService.removeMember(WS_ID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不是该工作空间成员");
        }

        @Test
        @DisplayName("不能移除 owner")
        void shouldThrowWhenRemoveOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"))
                    .thenReturn(buildMember(2L, WS_ID, 200L, "owner"));

            assertThatThrownBy(() -> workspaceService.removeMember(WS_ID, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能移除工作空间拥有者");
        }

        @Test
        @DisplayName("admin 不能移除其他 admin")
        void shouldThrowWhenAdminRemovesAdmin() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "admin"))
                    .thenReturn(buildMember(2L, WS_ID, 200L, "admin"));

            assertThatThrownBy(() -> workspaceService.removeMember(WS_ID, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("管理员不能移除其他管理员");
        }
    }

    // ==================== updateRole ====================

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("owner 成功更新成员角色")
        void shouldUpdateRoleAsOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"))
                    .thenReturn(buildMember(2L, WS_ID, 200L, "member"));
            when(memberMapper.updateById(any(WorkspaceMember.class))).thenReturn(1);

            workspaceService.updateRole(WS_ID, 200L, "admin");

            verify(memberMapper).updateById(any(WorkspaceMember.class));
        }

        @Test
        @DisplayName("不能修改自己的角色")
        void shouldThrowWhenUpdateOwnRole() {
            assertThatThrownBy(() -> workspaceService.updateRole(WS_ID, USER_ID, "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能修改自己的角色");
        }

        @Test
        @DisplayName("无效的角色值时抛出异常")
        void shouldThrowWhenInvalidRole() {
            assertThatThrownBy(() -> workspaceService.updateRole(WS_ID, 200L, "superadmin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无效的角色");
        }

        @Test
        @DisplayName("非 owner 无权修改角色")
        void shouldThrowWhenNotOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "admin"));

            assertThatThrownBy(() -> workspaceService.updateRole(WS_ID, 200L, "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅拥有者可执行此操作");
        }

        @Test
        @DisplayName("不能修改 owner 的角色")
        void shouldThrowWhenTargetIsOwner() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"))
                    .thenReturn(buildMember(2L, WS_ID, 200L, "owner"));

            assertThatThrownBy(() -> workspaceService.updateRole(WS_ID, 200L, "member"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能修改拥有者的角色");
        }
    }

    // ==================== leaveWorkspace ====================

    @Nested
    @DisplayName("leaveWorkspace")
    class LeaveWorkspace {

        @Test
        @DisplayName("成员成功退出工作空间")
        void shouldLeaveWorkspaceAsMember() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "member"));
            when(memberMapper.deleteById(1L)).thenReturn(1);

            workspaceService.leaveWorkspace(WS_ID);

            verify(memberMapper).deleteById(1L);
        }

        @Test
        @DisplayName("非成员无法退出")
        void shouldThrowWhenNotMember() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> workspaceService.leaveWorkspace(WS_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不是该工作空间成员");
        }

        @Test
        @DisplayName("owner 不能退出工作空间")
        void shouldThrowWhenOwnerLeaves() {
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(buildMember(1L, WS_ID, USER_ID, "owner"));

            assertThatThrownBy(() -> workspaceService.leaveWorkspace(WS_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("拥有者不能退出工作空间");
        }
    }
}
