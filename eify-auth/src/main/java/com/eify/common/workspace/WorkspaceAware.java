package com.eify.common.workspace;

/**
 * 声明实体参与工作空间多租户隔离。
 * 实现此接口的实体可由 {@link WorkspaceGuard} 统一进行 CRUD 守卫。
 */
public interface WorkspaceAware {

    Long getWorkspaceId();

    void setWorkspaceId(Long workspaceId);
}
