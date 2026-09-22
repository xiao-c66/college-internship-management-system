package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RectifyVO {
    private Long id;
    private Long inspectionId;
    private Long taskId;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long responsibleUserId;
    private String responsibleUserName;
    private String rectifyRequirements;
    private LocalDate deadlineDate;
    private String studentExplanation;
    private String evidenceAttachmentUrl;
    private LocalDateTime submitTime;
    private Long reviewTeacherId;
    private String reviewTeacherName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Long closeDeptUserId;
    private String closeDeptUserName;
    private LocalDateTime closeTime;
    private String status;
    private LocalDateTime createdAt;
}
