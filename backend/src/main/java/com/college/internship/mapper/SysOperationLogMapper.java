package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志持久层 Mapper 接口
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
