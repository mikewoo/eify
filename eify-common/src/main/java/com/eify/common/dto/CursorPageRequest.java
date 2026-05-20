package com.eify.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 游标分页请求
 * <p>
 * 适用于大表分页场景，避免 COUNT(*) 查询和深分页性能问题
 * <p>
 * 使用示例：
 * <pre>
 * // 第一页
 * GET /api/v1/messages/cursor/session/123?pageSize=20
 *
 * // 下一页
 * GET /api/v1/messages/cursor/session/123?pageSize=20&lastId=100
 * </pre>
 */
@Schema(description = "游标分页请求")
public class CursorPageRequest {

    /**
     * 上一页最后一条记录的 ID（游标）
     * <p>
     * 首次查询时传 null，后续查询使用上一页返回的 lastId
     */
    @Schema(description = "游标 ID（首次查询为 null）", example = "12345")
    private Long lastId;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "20")
    @Min(value = 1, message = "每页大小不能小于 1")
    @Max(value = 100, message = "每页大小不能超过 100")
    private Integer pageSize = 20;

    /**
     * 默认构造函数
     */
    public CursorPageRequest() {
    }

    /**
     * 全参构造函数
     */
    public CursorPageRequest(Long lastId, Integer pageSize) {
        this.lastId = lastId;
        this.pageSize = pageSize != null ? pageSize : 20;
    }

    /**
     * 获取 lastId
     */
    public Long getLastId() {
        return lastId;
    }

    /**
     * 设置 lastId
     */
    public void setLastId(Long lastId) {
        this.lastId = lastId;
    }

    /**
     * 获取 pageSize
     */
    public Integer getPageSize() {
        return pageSize;
    }

    /**
     * 设置 pageSize
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize != null ? pageSize : 20;
    }

    /**
     * 创建 Builder 实例
     */
    public static CursorPageRequestBuilder builder() {
        return new CursorPageRequestBuilder();
    }

    /**
     * Builder 类
     */
    public static class CursorPageRequestBuilder {
        private Long lastId;
        private Integer pageSize = 20;

        public CursorPageRequestBuilder lastId(Long lastId) {
            this.lastId = lastId;
            return this;
        }

        public CursorPageRequestBuilder pageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public CursorPageRequest build() {
            return new CursorPageRequest(lastId, pageSize);
        }
    }
}
