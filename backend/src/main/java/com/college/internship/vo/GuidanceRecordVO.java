package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 过程指导走访台账列表项视图 VO (API-066)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GuidanceRecordVO extends BaseVO {

    private Long id;
    private Long taskId;
    private String taskName;
    private Long teacherId;
    private String teacherName;
    private Long studentId;
    private String studentName;
    private String studentNumber;
    private String className;
    private String companyName;
    private Long deptId;
    private String deptName;
    private LocalDateTime guidanceDate;
    private String guidanceType; // PHONE, ONLINE, ONSITE, EMAIL_OTHER
    private String contentSummary;
    private String studentFeedback;
    private LocalDateTime feedbackTime;
    private String feedbackStatus; // UNCONFIRMED, CONFIRMED
    private String followupActions;
    private String location;
    private String attachmentUrl;
    private LocalDateTime createTime;
}
