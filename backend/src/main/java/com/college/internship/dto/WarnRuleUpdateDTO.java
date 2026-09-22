package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarnRuleUpdateDTO {
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;
    @NotBlank(message = "预警级别不能为空")
    private String warnLevel;
    @NotBlank(message = "阈值参数JSON不能为空")
    private String thresholdParamsJson;
    private String dispatchedRole;
    @NotNull(message = "处置超时天数不能为空")
    private Integer handlingTimeoutDays;
    private String description;
}
