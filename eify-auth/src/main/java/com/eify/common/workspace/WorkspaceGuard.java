package com.eify.common.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;

import java.util.Objects;

/**
 * 工作空间数据隔离守卫。
 * <p>
 * 统一封装 CRUD 中的 workspace 校验、绑定与查询辅助，消除散落在各
 * ServiceImpl 中的重复样板代码。所有方法均从 {@link CurrentContext#getWorkspaceId()}
 * 获取当前请求的工作空间 ID。
 */
public final class WorkspaceGuard {

    private WorkspaceGuard() {}

    /**
     * 将当前请求的工作空间 ID 绑定到实体。
     * 未设置工作空间时抛出 {@link ErrorCode#UNAUTHORIZED}，防止创建无归属的数据。
     */
    public static void bind(WorkspaceAware entity) {
        entity.setWorkspaceId(CurrentContext.getRequiredWorkspaceId());
    }

    /**
     * 校验实体属于当前工作空间。
     * <p>
     * 用于 {@code selectById + 手动比对} 的二步模式替换：
     * {@code return requireInWorkspace(mapper.selectById(id), ERROR_CODE);}
     *
     * @return 实体本身（非空且 workspace 匹配）
     * @throws BusinessException 实体为 null 或 workspace 不匹配
     */
    public static <T extends WorkspaceAware> T requireInWorkspace(T entity, ErrorCode errorCode) {
        if (entity == null || !Objects.equals(entity.getWorkspaceId(), CurrentContext.getWorkspaceId())) {
            throw new BusinessException(errorCode);
        }
        return entity;
    }

    /**
     * 检查名称在所属工作空间内唯一。
     */
    public static <T extends WorkspaceAware> void checkNameUnique(
            BaseMapper<T> mapper,
            SFunction<T, String> nameCol,
            SFunction<T, Long> wsCol,
            SFunction<T, Long> idCol,
            String name,
            Long excludeId,
            ErrorCode errorCode
    ) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<T>()
                .eq(nameCol, name)
                .eq(wsCol, CurrentContext.getWorkspaceId());
        if (excludeId != null) {
            wrapper.ne(idCol, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 检查名称唯一，支持自定义错误消息。
     */
    public static <T extends WorkspaceAware> void checkNameUnique(
            BaseMapper<T> mapper,
            SFunction<T, String> nameCol,
            SFunction<T, Long> wsCol,
            SFunction<T, Long> idCol,
            String name,
            Long excludeId,
            ErrorCode errorCode,
            String message
    ) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<T>()
                .eq(nameCol, name)
                .eq(wsCol, CurrentContext.getWorkspaceId());
        if (excludeId != null) {
            wrapper.ne(idCol, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(errorCode, message);
        }
    }
}
