package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialVersionVO {
    private Long id;
    private Long materialId;
    private Integer version;
    private String contentText;
    private String attachmentUrl;
    private String fileName;
    private LocalDateTime submitTime;
    private Long auditTeacherId;
    private String auditTeacherName;
    private BigDecimal auditScore;
    private String auditComment;
    private LocalDateTime auditTime;
    private String status;
    private LocalDateTime createdAt;
}
