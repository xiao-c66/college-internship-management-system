package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务关联专业实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("internship_task_major")
public class InternshipTaskMajor implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long majorId;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}
