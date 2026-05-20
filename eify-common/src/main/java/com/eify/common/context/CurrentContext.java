package com.eify.common.context;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;

/**
 * 当前请求上下文 — 通过 ThreadLocal 传递 userId 和 workspaceId。
 * <p>
 * 在 JwtAuthFilter 中设置，在 Service 层读取。
 * <p>
 * {@link #getWorkspaceId()} 未设置时返回 {@code null}（不再有默认值 1L）。
 * 需要确保已设置工作空间的场景（如创建实体）使用 {@link #getRequiredWorkspaceId()}。
 */
public final class CurrentContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> WORKSPACE_ID = new ThreadLocal<>();

    private CurrentContext() {}

    public static void set(Long userId, Long workspaceId) {
        USER_ID.set(userId);
        WORKSPACE_ID.set(workspaceId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前工作空间 ID，未设置时返回 {@code null}。
     */
    public static Long getWorkspaceId() {
        return WORKSPACE_ID.get();
    }

    /**
     * 获取当前工作空间 ID，未设置时抛出 {@link ErrorCode#UNAUTHORIZED}。
     * 用于创建实体、绑定工作空间等必须已有认证上下文的场景。
     */
    public static Long getRequiredWorkspaceId() {
        Long wid = WORKSPACE_ID.get();
        if (wid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未设置工作空间上下文");
        }
        return wid;
    }

    public static void clear() {
        USER_ID.remove();
        WORKSPACE_ID.remove();
    }
}
