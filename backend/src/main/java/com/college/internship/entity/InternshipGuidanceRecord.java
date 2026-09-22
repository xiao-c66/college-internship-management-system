package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 指导教师过程走访与指导台账实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_guidance_record")
public class InternshipGuidanceRecord extends BaseEntity {

    private Long taskId;
    private Long teacherId;
    private String teacherName;
    private Long studentId;
    private String studentName;
    private Long deptId;
    private LocalDateTime guidanceDate;
    private String guidanceType;
    private String contentSummary;
    private String studentFeedback;
    private LocalDateTime feedbackTime;
    private String feedbackStatus;
    private String followupActions;
    private String location;
    private String attachmentUrl;
}
