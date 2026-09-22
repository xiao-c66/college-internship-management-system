package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 实习任务圈定学生与分配状态 VO (ASSIGN-001 ~ ASSIGN-003)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实习任务学生圈定与指导教师分配 VO")
public class TaskStudentVO extends BaseVO {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "实习任务ID", example = "1")
    private Long taskId;

    @Schema(description = "学生用户ID", example = "4")
    private Long studentId;

    @Schema(description = "学生学号", example = "2021003011")
    private String studentNumber;

    @Schema(description = "学生姓名", example = "李同学 [DEMO]")
    private String studentName;

    @Schema(description = "所属班级ID", example = "1")
    private Long classId;

    @Schema(description = "班级名称", example = "软件工程2101班")
    private String className;

    @Schema(description = "指导教师ID (未分配时为null)", example = "3")
    private Long teacherId;

    @Schema(description = "指导教师姓名 (未分配时显示未分配)", example = "李教授 [DEMO]")
    private String teacherName;

    @Schema(description = "安全教育五阶段状态", example = "NOT_STARTED")
    private String safetyStatus;
}
