package com.eify.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.eify.common.context.CurrentContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * <p>
 * 自动处理以下字段的填充：
 * <ul>
 *   <li>creatorId: 插入时自动填充当前用户 ID</li>
 *   <li>createdAt: 插入时自动填充当前时间</li>
 *   <li>updatedAt: 插入和更新时自动填充当前时间</li>
 *   <li>deleted: 插入时默认填充 0</li>
 * </ul>
 */
@Slf4j
@Component
public class FieldMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");

        this.strictInsertFill(metaObject, "creatorId", Long.class, getCurrentUserId());

        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());

        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());

        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");

        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object userId = attrs.getRequest().getAttribute("currentUserId");
                if (userId instanceof Long id) {
                    return id;
                }
            }
        } catch (Exception ignored) {
            // 非 Web 请求上下文（如定时任务、测试），忽略
        }
        return CurrentContext.getUserId();
    }
}
