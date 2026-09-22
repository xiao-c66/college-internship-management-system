package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 阶段材料与提报明细主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("student_material_item")
public class StudentMaterialItem extends BasePhase7Entity {

    private Long taskId;
    private Long studentId;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String contentText;
    private String attachmentUrl;
    private String fileName;
    private Long fileSize;
    private Integer version;
    private String status;
    private LocalDateTime submitTime;
    private Long auditTeacherId;
    private BigDecimal auditScore;
    private String auditComment;
    private LocalDateTime auditTime;
}
