package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统全局运维参数视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String configName;
    private String configKey;
    private String configValue;
    private String defaultValue;
    private Integer isSystem;
    private String remark;
    private LocalDateTime updatedTime;
    private String updatedBy;
}
