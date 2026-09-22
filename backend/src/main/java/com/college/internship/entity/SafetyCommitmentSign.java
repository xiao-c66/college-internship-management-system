package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 安全承诺书签署与保单凭据实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("safety_commitment_sign")
public class SafetyCommitmentSign extends BaseEntity {

    private Long taskId;
    private Long studentId;
    private String commitmentText;
    private Integer isSigned;
    private String signIp;
    private LocalDateTime signTime;
    private String insuranceFileUrl;
}
