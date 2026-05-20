package com.eify.auth.context;

import com.eify.common.context.CurrentContext;

/**
 * 测试专用工具 — 设置/清理 CurrentContext 的 ThreadLocal。
 * <p>
 * 用法：
 * <pre>
 *   {@literal @}BeforeEach  void setUp()   { CurrentContextTestUtil.setMockContext(); }
 *   {@literal @}AfterEach   void tearDown() { CurrentContextTestUtil.clear(); }
 * </pre>
 */
public final class CurrentContextTestUtil {

    private CurrentContextTestUtil() {}

    /** 设置默认测试上下文（userId=1, workspaceId=1） */
    public static void setMockContext() {
        CurrentContext.set(1L, 1L);
    }

    /** 设置自定义上下文 */
    public static void setMockContext(Long userId, Long workspaceId) {
        CurrentContext.set(userId, workspaceId);
    }

    /** 清理 ThreadLocal，防止测试间泄漏 */
    public static void clear() {
        CurrentContext.clear();
    }
}
