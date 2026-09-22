package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 中期检查方案主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("midterm_inspection_plan")
public class MidtermInspectionPlan extends BasePhase7Entity {

    private String planName;
    private Long taskId;
    private Long deptId;
    private String samplingMode;
    private BigDecimal samplingRatio;
    private LocalDate startDate;
    private LocalDate endDate;
    private String expertGroup;
    private String remark;
    private String status;
    private Long createdBy;
}
