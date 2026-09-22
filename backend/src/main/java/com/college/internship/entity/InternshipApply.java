package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 学生实习申报主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_apply")
public class InternshipApply extends BaseEntity {

    private Long taskId;
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long deptId;
    private Long majorId;
    private Long classId;
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
    private Integer isLocked;
}
