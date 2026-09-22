package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 中期检查抽查与督导记录明细表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("midterm_inspection")
public class MidtermInspection extends BasePhase7Entity {

    private Long planId;
    private Long taskId;
    private Long studentId;
    private Long teacherId;
    private Long inspectorId;
    private String samplingBatchNo;
    private String inspectionType;
    private LocalDateTime inspectionDate;
    private String companySituation;
    private String studentPerformance;
    private String guidanceFulfillment;
    private BigDecimal score;
    private String attachmentUrl;
    private Integer hasProblem;
    private String problemDesc;
    private String status;
}
