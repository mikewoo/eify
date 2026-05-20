package com.eify.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.auth.entity.WorkspaceMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMember> {

    /** 绕过 @TableLogic 查询原始记录（含软删除），用于恢复已退出的成员。 */
    @Select("SELECT * FROM ai_workspace_member WHERE workspace_id = #{workspaceId} AND user_id = #{userId}")
    WorkspaceMember selectRaw(Long workspaceId, Long userId);
}
