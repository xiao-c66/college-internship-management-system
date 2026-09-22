package com.college.internship.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InspectPlanCreateDTO {
    @NotNull(message = "实习任务ID不能为空")
    private Long taskId;
    private Long deptId;
    @NotBlank(message = "方案名称不能为空")
    private String planName;
    private String samplingMode; // RANDOM_RATIO, CLASS_SELECT
    @DecimalMin(value = "1.00", message = "抽样比例不能低于1%")
    @DecimalMax(value = "100.00", message = "抽样比例不能高于100%")
    private BigDecimal samplingRatio;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "截止日期不能为空")
    private LocalDate endDate;
    private String expertGroup;
    private String remark;
    private List<Long> classIds;
}
