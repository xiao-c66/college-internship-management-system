package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 异常预警工单主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("warn_ticket")
public class WarnTicket extends BasePhase7Entity {

    private String ticketNo;
    private Long taskId;
    private Long studentId;
    private Long teacherId;
    private Long deptId;
    private Long ruleId;
    private Integer ruleVersion;
    private String warnLevel;
    private String warnTitle;
    private String evidenceSnapshotJson;
    private String status;
    private Integer isUpgraded;
    private LocalDateTime upgradedTime;
    private String upgradeReason;
    private Long currentAssigneeId;
    private String currentAssigneeRole;
    private String dedupKey;

    @TableField(value = "active_dedup_key", updateStrategy = FieldStrategy.ALWAYS)
    private String activeDedupKey;
    private String studentFeedback;
    private LocalDateTime studentFeedbackTime;
    private String teacherInvestigation;
    private String handlingMeasures;
    private LocalDateTime closedTime;
    private Long closedBy;
}
