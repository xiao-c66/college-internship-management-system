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
public class MaterialItemVO {
    private Long id;
    private Long taskId;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String materialCode;
    private String materialName;
    private String materialType;
    private Boolean required;
    private Integer minContentLength;
    private String contentText;
    private String attachmentUrl;
    private String fileName;
    private Long fileSize;
    private Integer version;
    private String status;
    private LocalDateTime submitTime;
    private Long auditTeacherId;
    private String auditTeacherName;
    private BigDecimal auditScore;
    private String auditComment;
    private LocalDateTime auditTime;
}
