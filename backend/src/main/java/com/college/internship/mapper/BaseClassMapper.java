package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.BaseClass;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班级持久层 Mapper 接口
 */
@Mapper
public interface BaseClassMapper extends BaseMapper<BaseClass> {
}
