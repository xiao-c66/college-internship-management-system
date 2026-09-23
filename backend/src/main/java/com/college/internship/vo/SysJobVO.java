package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysJobVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String jobCode;
    private String jobName;
    private String cronExpression;
    private Integer status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
