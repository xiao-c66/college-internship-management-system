package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 实习变更审批历史轨迹实体
 * 保留不可篡改的审批人、意见、时间和状态流转记录
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_apply_change_history")
public class InternshipApplyChangeHistory extends BaseEntity {

    private Long changeId;
    private String nodeName;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String auditAction;
    private String auditOpinion;
    private String snapshotStatus;
}
