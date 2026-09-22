package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveVO {
    private Long id;
    private String archiveNo;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String className;
    private String majorName;
    private Long deptId;
    private String deptName;
    private String academicYear;
    private String checkMatrixJson;
    private String archiveBundleUrl;
    private String archivePdfUrl;
    private Long archivedUserId;
    private String archivedUserName;
    private LocalDateTime archivedTime;
    private String status;
    private String specialUnlockReason;
    private String specialDocNo;
    private Long unlockedBy;
    private String unlockedByName;
    private LocalDateTime unlockedTime;
    private LocalDateTime unlockExpireTime;
    private Integer version;
    private LocalDateTime createdAt;
}
