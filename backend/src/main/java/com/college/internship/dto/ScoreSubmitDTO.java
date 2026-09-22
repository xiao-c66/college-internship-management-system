package com.college.internship.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreSubmitDTO {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;
    @NotNull(message = "学生ID不能为空")
    private Long studentId;
    @NotNull(message = "企业鉴定分不能为空")
    @DecimalMin(value = "0.00", message = "企业分不能低于0")
    @DecimalMax(value = "100.00", message = "企业分不能高于100")
    private BigDecimal enterpriseScore;
    @NotNull(message = "过程表现分不能为空")
    @DecimalMin(value = "0.00", message = "过程分不能低于0")
    @DecimalMax(value = "100.00", message = "过程分不能高于100")
    private BigDecimal processScore;
    @NotNull(message = "周报均分不能为空")
    @DecimalMin(value = "0.00", message = "周报分不能低于0")
    @DecimalMax(value = "100.00", message = "周报分不能高于100")
    private BigDecimal weeklyScore;
    @NotNull(message = "阶段材料分不能为空")
    @DecimalMin(value = "0.00", message = "材料分不能低于0")
    @DecimalMax(value = "100.00", message = "材料分不能高于100")
    private BigDecimal materialScore;
    @NotNull(message = "总结报告分不能为空")
    @DecimalMin(value = "0.00", message = "总结分不能低于0")
    @DecimalMax(value = "100.00", message = "总结分不能高于100")
    private BigDecimal summaryScore;
    private String evaluationComment;
    private String enterpriseEvaluationUrl;
    private Boolean submitToDept;
}
