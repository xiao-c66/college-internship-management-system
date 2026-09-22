package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实习申报审批流转历史轨迹与快照实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("apply_audit_history")
public class ApplyAuditHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applyId;
    private String nodeName;
    private Long auditorId;
    private String auditorName;
    private String auditorRole;
    private String auditAction;
    private String auditOpinion;
    private String snapshotData;
    private LocalDateTime auditTime;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}
