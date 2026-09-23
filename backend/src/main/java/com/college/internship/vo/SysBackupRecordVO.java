package com.college.internship.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库受控备份记录视图对象 (API-109 / API-110)
 * 严格屏蔽 Windows 物理磁盘盘符，仅向前端暴露受控相对文件名
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库受控备份记录视图")
public class SysBackupRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "备份记录主键ID")
    private Long id;

    @Schema(description = "受控相对备份文件名 (如 internship_db_backup_20260923_112000.sql)")
    private String backupFileName;

    @Schema(description = "备份物理文件大小 (字节)")
    private Long fileSizeBytes;

    @Schema(description = "包含数据表总数")
    private Integer tableCount;

    @Schema(description = "SHA-256 完整性散列值")
    private String sha256Digest;

    @Schema(description = "是否锁定基线 (0-可清理, 1-锁定基线)")
    private Integer isLocked;

    @Schema(description = "备份状态 (SUCCESS, FAILED)")
    private String status;

    @Schema(description = "失败错误信息")
    private String errorMessage;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "备份时间戳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime backupTime;
}
