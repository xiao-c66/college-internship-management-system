package com.college.internship.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教师登记过程指导/走访台账入参 DTO (API-065)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GuidanceCreateDTO extends BaseDTO {

    @NotNull(message = "实习任务ID不能为空")
    private Long taskId;

    @NotNull(message = "被指导学生ID不能为空")
    private Long studentId;

    @NotNull(message = "指导开展时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime guidanceDate;

    @NotBlank(message = "指导方式不能为空 (PHONE, ONLINE, ONSITE, EMAIL_OTHER)")
    private String guidanceType;

    @NotBlank(message = "交流内容要点不能为空")
    private String contentSummary;

    private String location; // 实地走访时建议必填

    private String followupActions;

    private String attachmentUrl;
}
