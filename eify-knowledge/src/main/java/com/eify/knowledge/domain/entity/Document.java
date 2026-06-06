package com.eify.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document")
public class Document extends BaseEntity implements WorkspaceAware {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("file_name")
    private String fileName;

    @TableField("original_name")
    private String originalName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_path")
    private String filePath;

    /**
     * 文档字符数
     */
    @TableField("char_count")
    private Integer charCount;

    /**
     * 分块数
     */
    @TableField("chunk_count")
    private Integer chunkCount;

    /**
     * 处理状态：0=待处理，1=处理中，2=已完成，3=失败
     */
    @TableField("process_status")
    private Integer processStatus;

    @TableField("error_message")
    private String errorMessage;

    private Integer enabled;

}
