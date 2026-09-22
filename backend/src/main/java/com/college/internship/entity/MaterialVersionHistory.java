package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 阶段材料历史版本快照表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("material_version_history")
public class MaterialVersionHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private Long taskId;
    private Long studentId;
    private Integer version;
    private String contentText;
    private String attachmentUrl;
    private String fileName;
    private LocalDateTime submitTime;
    private Long auditTeacherId;
    private BigDecimal auditScore;
    private String auditComment;
    private LocalDateTime auditTime;
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
