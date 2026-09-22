package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 学生周报暂存草稿/正式提交入参 DTO (API-058)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WeeklyReportSaveDTO extends BaseDTO {

    @NotNull(message = "实习任务ID不能为空")
    private Long taskId;

    @NotNull(message = "周次序号不能为空")
    private Integer weekNumber;

    @NotBlank(message = "操作动作不能为空 (DRAFT 或 SUBMIT)")
    private String action; // DRAFT, SUBMIT

    private String workContent;
    private String workSummary;
    private String problemEncountered;
    private String nextWeekPlan;
    private String attachmentUrl;
}
