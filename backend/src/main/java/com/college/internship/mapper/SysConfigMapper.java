package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统全局运维参数配置 Mapper 接口
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
