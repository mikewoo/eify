package com.eify.common.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果
 * <p>
 * 支持两种分页模式：
 * <ul>
 *   <li>传统分页：适用于小表（< 10 万行），返回 total</li>
 *   <li>游标分页：适用于大表（≥ 10 万行），使用 hasMore 替代 total</li>
 * </ul>
 *
 * @param <T> 数据类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Boolean hasMore;

    /**
     * 默认构造函数
     */
    public PageResult() {
    }

    /**
     * 全参构造函数
     */
    public PageResult(List<T> list, Long total, Integer page, Integer pageSize, Boolean hasMore) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.hasMore = hasMore;
    }

    // ========== Getter/Setter ==========

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    // ========== 传统分页构造方法 ==========

    /**
     * 传统分页构造（包含 total）
     */
    public static <T> PageResult<T> of(List<T> list, Long total, Integer page, Integer pageSize) {
        return new PageResult<>(list, total, page, pageSize, null);
    }

    /**
     * 空数据分页
     */
    public static <T> PageResult<T> empty(Integer page, Integer pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, page, pageSize, null);
    }

    // ========== 游标分页构造方法 ==========

    /**
     * 游标分页构造（不包含 total，使用 hasMore）
     * <p>
     * 适用于大表场景，避免 COUNT(*) 查询
     *
     * @param list     数据列表
     * @param pageSize 每页大小
     * @param hasMore  是否有更多数据
     */
    public static <T> PageResult<T> ofCursor(List<T> list, Integer pageSize, Boolean hasMore) {
        return new PageResult<>(list, null, null, pageSize, hasMore);
    }

    // ========== 辅助方法 ==========

    /**
     * 判断是否为游标分页模式
     */
    public boolean isCursorMode() {
        return total == null;
    }

    /**
     * 判断是否有数据
     */
    public boolean hasData() {
        return list != null && !list.isEmpty();
    }

    /**
     * 判断是否有下一页
     */
    public boolean hasNext() {
        if (isCursorMode()) {
            return Boolean.TRUE.equals(hasMore);
        }
        return page != null && pageSize != null && page * pageSize < total;
    }

    /**
     * 判断是否有上一页（传统分页）
     */
    public boolean hasPrevious() {
        return !isCursorMode() && page != null && page > 1;
    }

    /**
     * 获取总页数（传统分页）
     * <p>
     * 使用整数运算避免浮点数精度问题
     */
    public Integer getTotalPages() {
        if (isCursorMode() || pageSize == null || pageSize == 0) {
            return null;
        }
        // 整数运算：(total + pageSize - 1) / pageSize 等价于 Math.ceil(total / pageSize)
        long pages = (total + pageSize - 1) / pageSize;
        // 防止溢出
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
