package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 中期检查限期整改通知与落实表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("midterm_rectification")
public class MidtermRectification extends BasePhase7Entity {

    private Long inspectionId;
    private Long taskId;
    private Long studentId;
    private Long responsibleUserId;
    private String rectifyRequirements;
    private LocalDate deadlineDate;
    private String studentExplanation;
    private String evidenceAttachmentUrl;
    private LocalDateTime submitTime;
    private Long reviewTeacherId;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Long closeDeptUserId;
    private LocalDateTime closeTime;
    private String status;
}
