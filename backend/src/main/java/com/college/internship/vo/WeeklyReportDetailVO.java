package com.college.internship.vo;

import com.college.internship.entity.InternshipWeeklyReportHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周报详情与流转快照视图 VO (API-057)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WeeklyReportDetailVO extends BaseVO {

    private Long id;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNumber;
    private String className;
    private Long teacherId;
    private String teacherName;
    private Long deptId;
    private String deptName;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime deadlineTime;

    // 四大要素
    private String workContent;
    private String workSummary;
    private String problemEncountered;
    private String nextWeekPlan;
    private String attachmentUrl;

    // 状态与流转
    private Integer version;
    private String status;
    private Integer isOverdue;
    private Integer overdueDays;
    private LocalDateTime submitTime;
    private BigDecimal score;
    private String reviewComment;
    private String reviewAnnotations;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 历史版本快照列表
    private List<InternshipWeeklyReportHistory> historyList;
}
