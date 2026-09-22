package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 实习电子卷宗归档主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_archive")
public class InternshipArchive extends BasePhase7Entity {

    private String archiveNo;
    private Long taskId;
    private Long studentId;
    private Long deptId;
    private String academicYear;
    private String checkMatrixJson;
    private String archiveBundleUrl;
    private String archivePdfUrl;
    private Long archivedUserId;
    private LocalDateTime archivedTime;
    private String status;
    private String specialUnlockReason;
    private String specialDocNo;
    private Long unlockedBy;
    private LocalDateTime unlockedTime;
    private LocalDateTime unlockExpireTime;
    private Integer version;
}
