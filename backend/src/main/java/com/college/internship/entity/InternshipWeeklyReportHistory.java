package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周报流转与退回历史快照实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("internship_weekly_report_history")
public class InternshipWeeklyReportHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private Long taskId;
    private Long studentId;
    private Integer version;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String returnReason;
    private BigDecimal score;
    private String reviewComment;
    private String snapshotContent;
    private LocalDateTime operateTime;
}
