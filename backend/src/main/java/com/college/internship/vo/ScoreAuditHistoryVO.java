package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAuditHistoryVO {
    private Long id;
    private Long scoreId;
    private Long taskId;
    private Long studentId;
    private String action;
    private String appealReason;
    private String appealAttachmentUrl;
    private String oldScoreSnapshot;
    private String newScoreSnapshot;
    private Long auditUserId;
    private String auditUserName;
    private String auditComment;
    private String approvalDocNo;
    private LocalDateTime operateTime;
}
