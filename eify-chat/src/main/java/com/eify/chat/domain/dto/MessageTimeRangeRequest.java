package com.eify.chat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 消息时间范围查询请求
 */
@Data
@Schema(description = "消息时间范围查询请求")
public class MessageTimeRangeRequest {

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间（毫秒时间戳）", required = true, example = "1704067200000")
    private Long startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间（毫秒时间戳）", required = true, example = "1704153600000")
    private Long endTime;

    @Schema(description = "上一页最后一条记录的 ID（首页为 null）", example = "12345")
    private Long lastId;

    @Schema(description = "每页大小（1-100）", example = "20")
    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private Integer pageSize = 20;

    public Integer getPageSize() {
        return pageSize != null ? pageSize : 20;
    }
}
