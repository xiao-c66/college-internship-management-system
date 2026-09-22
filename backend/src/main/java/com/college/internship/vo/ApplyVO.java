package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实习申报详情与流转视图 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApplyVO extends BaseVO {

    private Long id;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long deptId;
    private String deptName;
    private Long majorId;
    private String majorName;
    private Long classId;
    private String className;
    private String companyName;
    private String jobPosition;
    private String jobAddress;
    private String companyContactPerson;
    private String companyContactPhone;
    private String companyContactEmail;
    private LocalDate startDate;
    private LocalDate endDate;
    private String internshipMode;
    private String jobDuties;
    private String agreementFileUrl;
    private String applyStatus;
    private String statusDesc;
    private Integer isLocked;
    private Boolean canEdit;
    private Boolean canSubmit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<AuditHistoryVO> auditHistories;
}
