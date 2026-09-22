package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 成绩异议申诉与调分审批历史表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("score_audit_history")
public class ScoreAuditHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
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
