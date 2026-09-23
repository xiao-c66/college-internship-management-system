package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysBackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库受控备份记录 Mapper
 */
@Mapper
public interface SysBackupRecordMapper extends BaseMapper<SysBackupRecord> {
}
