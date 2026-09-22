package com.college.internship.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指导教师分配 DTO (ASSIGN-001 ~ ASSIGN-003)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "指导教师分配入参")
public class TeacherAssignDTO {

    @NotNull(message = "指导教师ID不能为空")
    @Schema(description = "指导教师用户ID", example = "3")
    private Long teacherId;

    @NotEmpty(message = "待分配学生ID列表不能为空")
    @Schema(description = "学生用户ID列表", example = "[4, 5]")
    private List<Long> studentIds;
}
