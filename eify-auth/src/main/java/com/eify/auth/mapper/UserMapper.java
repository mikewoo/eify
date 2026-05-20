package com.eify.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
