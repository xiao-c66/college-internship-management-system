package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.BaseMajor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 专业持久层 Mapper 接口
 */
@Mapper
public interface BaseMajorMapper extends BaseMapper<BaseMajor> {
}
