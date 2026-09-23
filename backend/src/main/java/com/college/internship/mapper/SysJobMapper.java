package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统受限定时任务数据访问 Mapper
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
