package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审计日志持久层 Mapper 接口
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    /**
     * 院系负责人专属：本院系操作日志关联查询 (严格排除系统调度 operator_id = 0)
     */
    @Select("<script>" +
            "SELECT log.* FROM sys_operation_log log " +
            "INNER JOIN sys_user u ON log.operator_id = u.id " +
            "WHERE u.dept_id = #{deptId} " +
            "AND log.operator_id != 0 " +
            "AND log.business_type NOT IN ('LOGIN', 'LOGOUT') " +
            "<if test='businessType != null and businessType != \"\"'> AND log.business_type = #{businessType} </if> " +
            "<if test='status != null'> AND log.status = #{status} </if> " +
            "ORDER BY log.oper_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<SysOperationLog> selectDeptOperationLogs(@Param("deptId") Long deptId,
                                                @Param("businessType") String businessType,
                                                @Param("status") Integer status,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /**
     * 超级管理员专属：全校全量操作日志检索
     */
    @Select("<script>" +
            "SELECT * FROM sys_operation_log " +
            "WHERE business_type NOT IN ('LOGIN', 'LOGOUT') " +
            "<if test='businessType != null and businessType != \"\"'> AND business_type = #{businessType} </if> " +
            "<if test='status != null'> AND status = #{status} </if> " +
            "ORDER BY oper_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<SysOperationLog> selectAdminOperationLogs(@Param("businessType") String businessType,
                                                 @Param("status") Integer status,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /**
     * 登录与注销安全审计查询 (API-120)
     */
    @Select("<script>" +
            "SELECT * FROM sys_operation_log " +
            "WHERE business_type IN ('LOGIN', 'LOGOUT') " +
            "<if test='username != null and username != \"\"'> AND operator_name LIKE CONCAT('%', #{username}, '%') </if> " +
            "<if test='status != null'> AND status = #{status} </if> " +
            "ORDER BY oper_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<SysOperationLog> selectLoginLogs(@Param("username") String username,
                                         @Param("status") Integer status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);
}
