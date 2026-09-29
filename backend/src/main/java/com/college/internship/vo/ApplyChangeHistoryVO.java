package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实习变更审批流转历史轨迹VO")
public class ApplyChangeHistoryVO {

    @Schema(description = "历史轨迹ID")
    private Long id;

    @Schema(description = "变更单ID")
    private Long changeId;

    @Schema(description = "审批节点名称")
    private String nodeName;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "操作人角色")
    private String operatorRole;

    @Schema(description = "审核动作: SUBMIT, APPROVE, REJECT")
    private String auditAction;

    @Schema(description = "审核意见")
    private String auditOpinion;

    @Schema(description = "节点流转后状态快照")
    private String snapshotStatus;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
