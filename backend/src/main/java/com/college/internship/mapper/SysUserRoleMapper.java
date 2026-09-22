package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联持久层 Mapper 接口
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
