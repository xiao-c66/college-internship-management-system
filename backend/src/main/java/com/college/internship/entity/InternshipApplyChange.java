package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 实习重大信息变更申请实体
 * 针对 APPLY-009 审核锁定后的合规流转
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_apply_change")
public class InternshipApplyChange extends BaseEntity {

    private Long applyId;
    private Long taskId;
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long deptId;
    private Long teacherId;
    private String teacherName;

    // 原信息快照
    private String origCompanyName;
    private String origJobPosition;
    private String origJobAddress;
    private String origContactPerson;
    private String origContactPhone;
    private String origContactEmail;
    private LocalDate origStartDate;
    private LocalDate origEndDate;
    private String origInternshipMode;
    private String origJobDuties;
    private String origAgreementFileUrl;

    // 拟变更的新信息
    private String newCompanyName;
    private String newJobPosition;
    private String newJobAddress;
    private String newContactPerson;
    private String newContactPhone;
    private String newContactEmail;
    private LocalDate newStartDate;
    private LocalDate newEndDate;
    private String newInternshipMode;
    private String newJobDuties;
    private String newAgreementFileUrl;

    // 变更事由与佐证材料
    private String changeReason;
    private String proofFileUrl;

    // 状态机: PENDING_TEACHER, PENDING_DEPT, APPROVED, REJECTED
    private String changeStatus;
    // 当前审批节点: TEACHER_INITIAL, DEPT_FINAL, FINISHED
    private String currentStep;
}
