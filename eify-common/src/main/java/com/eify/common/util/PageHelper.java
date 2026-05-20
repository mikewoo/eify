package com.eify.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.result.PageResult;

import java.util.List;

/**
 * 分页工具类
 * <p>
 * 提供 MyBatis-Plus 分页对象与自定义分页结果之间的转换
 */
public class PageHelper {

    /**
     * 默认页大小
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 最大页大小
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 将前端分页参数转换为 MyBatis-Plus 的 Page 对象
     * <p>
     * page 从 1 开始，pageSize 默认 20，最大 100
     *
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return MyBatis-Plus 的 Page 对象
     */
    public static <T> Page<T> toPage(Integer page, Integer pageSize) {
        // 参数校验与默认值处理
        int currentPage = page != null && page > 0 ? page : 1;
        int size = normalizePageSize(pageSize);

        return new Page<>(currentPage, size);
    }

    /**
     * 将前端分页参数转换为 MyBatis-Plus 的 Page 对象（无参重载）
     *
     * @return MyBatis-Plus 的 Page 对象（page=1, pageSize=20）
     */
    public static <T> Page<T> toPage() {
        return new Page<>(1, DEFAULT_PAGE_SIZE);
    }

    /**
     * 将 MyBatis-Plus 的 IPage 查询结果转换为自定义的 PageResult
     *
     * @param iPage MyBatis-Plus 分页查询结果
     * @return 自定义分页结果
     */
    public static <T> PageResult<T> toPageResult(IPage<T> iPage) {
        return PageResult.of(
                iPage.getRecords(),
                iPage.getTotal(),
                (int) iPage.getCurrent(),
                (int) iPage.getSize()
        );
    }

    /**
     * 规范化页大小
     * <p>
     * - null 或小于等于 0：使用默认值 20
     * - 大于 100：限制为 100
     *
     * @param pageSize 页大小
     * @return 规范化后的页大小
     */
    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return pageSize;
    }

    /**
     * 计算偏移量（用于 SQL 查询）
     *
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 偏移量
     */
    public static long getOffset(long page, long pageSize) {
        return (page - 1) * pageSize;
    }

    /**
     * 计算总页数
     *
     * @param total    总记录数
     * @param pageSize 每页大小
     * @return 总页数
     */
    public static long getTotalPages(long total, long pageSize) {
        return pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }
}
