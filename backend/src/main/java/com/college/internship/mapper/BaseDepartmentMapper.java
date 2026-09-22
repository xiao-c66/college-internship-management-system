package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.BaseDepartment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 院系持久层 Mapper 接口
 */
@Mapper
public interface BaseDepartmentMapper extends BaseMapper<BaseDepartment> {
}
