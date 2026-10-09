package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_task_lifecycle_log")
public class TaskLifecycleLog extends BaseEntity {
    private Long taskId;
    private String action;
    private String reason;
    private String preStatus;
    private String postStatus;
    private Long operatorId;
    private String operatorName;
}
