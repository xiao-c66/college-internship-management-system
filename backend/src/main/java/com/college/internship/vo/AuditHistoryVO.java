package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审批流转历史记录 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuditHistoryVO extends BaseVO {

    private Long id;
    private Long applyId;
    private String nodeName;
    private String nodeDesc;
    private Long auditorId;
    private String auditorName;
    private String auditorRole;
    private String auditAction;
    private String auditOpinion;
    private String snapshotData;
    private LocalDateTime auditTime;
}
