package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表持久层 Mapper 接口
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
