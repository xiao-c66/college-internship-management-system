package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_task_student_audit_log")
public class TaskStudentAuditLog extends BaseEntity {
    private Long taskId;
    private Long studentId;
    private String action;
    private String reason;
    private Long operatorId;
    private String operatorName;
}
