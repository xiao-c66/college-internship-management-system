package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 安全教育一键催办执行结果 VO (SAFE-008 & API-123)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RemindResultVO extends BaseVO {

    private Long taskId;
    private Integer remindedCount;
    private List<String> studentNames;
    private String noticeMessage;
}
