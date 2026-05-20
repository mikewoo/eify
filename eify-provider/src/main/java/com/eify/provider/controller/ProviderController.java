package com.eify.provider.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.dto.ProviderCreateRequest;
import com.eify.provider.domain.dto.ProviderResponse;
import com.eify.provider.domain.dto.ProviderUpdateRequest;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商 Controller
 */
@Tag(name = "供应商管理", description = "模型供应商 CRUD 接口")
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * 分页查询
     */
    @Operation(summary = "分页查询供应商", description = "支持分页查询供应商列表，可按名称、类型和启用状态筛选")
    @GetMapping
    public Result<PageResult<ProviderResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) ProviderType type,
            @RequestParam(required = false) Integer enabled
    ) {
        PageResult<ProviderResponse> result = providerService.list(page, pageSize, name, type, enabled);
        return Result.success(result);
    }

    /**
     * 根据ID查询（含模型配置和健康状态）
     */
    @Operation(summary = "根据ID查询供应商", description = "返回供应商详情，包含模型配置和健康状态")
    @GetMapping("/{id}")
    public Result<ProviderResponse> getById(@PathVariable Long id) {
        ProviderResponse response = providerService.getById(id);
        return Result.success(response);
    }

    /**
     * 创建供应商
     */
    @Operation(summary = "创建供应商", description = "创建新的模型供应商")
    @PostMapping
    public Result<ProviderResponse> create(@Valid @RequestBody ProviderCreateRequest request) {
        Provider provider = providerService.create(request);
        return Result.success(toResponse(provider));
    }

    /**
     * 更新供应商
     */
    @Operation(summary = "更新供应商", description = "更新供应商配置")
    @PutMapping("/{id}")
    public Result<ProviderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProviderUpdateRequest request
    ) {
        Provider provider = providerService.update(id, request);
        return Result.success(toResponse(provider));
    }

    /**
     * 删除供应商
     */
    @Operation(summary = "删除供应商", description = "删除供应商（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.success();
    }

    /**
     * 测试连通性（同步模型）
     */
    @Operation(summary = "同步模型", description = "测试供应商连通性并自动同步可用模型列表到 model_config")
    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        ConnectionTestResult result = providerService.testConnection(id);
        return Result.success(result);
    }

    /**
     * 获取供应商下的模型列表
     */
    @Operation(summary = "获取供应商模型列表", description = "返回该供应商下所有已同步的模型配置")
    @GetMapping("/{id}/models")
    public Result<List<ProviderResponse.ModelConfigInfo>> getModels(@PathVariable Long id) {
        return Result.success(providerService.getModels(id));
    }

    /**
     * 实体转响应对象
     */
    private ProviderResponse toResponse(Provider entity) {
        return ProviderResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .baseUrl(entity.getBaseUrl())
                .authConfig(entity.getAuthConfig())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
