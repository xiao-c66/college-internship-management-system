package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库受控备份记录实体 (阶段8新增)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_backup_record")
public class SysBackupRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String backupFileName;
    private Long fileSizeBytes;
    private Integer tableCount;
    private String sha256Digest;
    private Integer isLocked; // 0-可清理, 1-锁定防删基线
    private String status; // SUCCESS, FAILED
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime backupTime;
}
