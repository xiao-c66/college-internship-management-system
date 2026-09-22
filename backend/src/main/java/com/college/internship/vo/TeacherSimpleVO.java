package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 可用指导教师简要信息 VO (ASSIGN-001)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "可用指导教师简要信息 VO")
public class TeacherSimpleVO extends BaseVO {

    @Schema(description = "教师用户ID", example = "3")
    private Long id;

    @Schema(description = "教师工号", example = "TEA001")
    private String userNumber;

    @Schema(description = "教师真实姓名", example = "李教授 [DEMO]")
    private String realName;

    @Schema(description = "所属院系ID", example = "1")
    private Long deptId;

    @Schema(description = "所属院系名称", example = "计算机科学与技术学院")
    private String deptName;

    @Schema(description = "当前任务已分配学生数", example = "5")
    private Integer assignedStudentsCount;
}
