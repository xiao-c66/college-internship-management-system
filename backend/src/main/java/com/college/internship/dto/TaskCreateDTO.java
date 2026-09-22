package com.college.internship.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 实习任务创建请求入参 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskCreateDTO extends BaseDTO {

    @NotBlank(message = "任务编码不能为空")
    private String taskCode;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    private Long deptId;

    @NotBlank(message = "学年不能为空")
    private String academicYear;

    @NotNull(message = "学期不能为空")
    private Integer semester;

    @NotBlank(message = "组织模式不能为空")
    private String internshipMode;

    @NotNull(message = "实习开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "实习结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "企业评价权重不能为空")
    @DecimalMin(value = "0.0", message = "权重不能小于0")
    @DecimalMax(value = "100.0", message = "权重不能大于100")
    private BigDecimal weightEnterprise;

    @NotNull(message = "教师过程评价权重不能为空")
    @DecimalMin(value = "0.0", message = "权重不能小于0")
    @DecimalMax(value = "100.0", message = "权重不能大于100")
    private BigDecimal weightTeacherProcess;

    @NotNull(message = "周报综合成绩权重不能为空")
    @DecimalMin(value = "0.0", message = "权重不能小于0")
    @DecimalMax(value = "100.0", message = "权重不能大于100")
    private BigDecimal weightWeeklyReport;

    @NotNull(message = "阶段材料成绩权重不能为空")
    @DecimalMin(value = "0.0", message = "权重不能小于0")
    @DecimalMax(value = "100.0", message = "权重不能大于100")
    private BigDecimal weightStageMaterial;

    @NotNull(message = "实习总结成绩权重不能为空")
    @DecimalMin(value = "0.0", message = "权重不能小于0")
    @DecimalMax(value = "100.0", message = "权重不能大于100")
    private BigDecimal weightSummary;

    private String materialChecklist;
    private String weeklyFrequency;
    private Integer weeklyDeadlineDay;
    private Integer safetyPassingScore;
    private Integer safetyMaxAttempts;

    private List<Long> majorIds;
    private List<Long> classIds;
}
