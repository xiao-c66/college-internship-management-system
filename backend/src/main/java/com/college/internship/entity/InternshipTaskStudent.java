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
 * 任务圈定参与学生名单实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("internship_task_student")
public class InternshipTaskStudent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long classId;

    /**
     * 指导教师用户ID (ASSIGN-001/003)
     */
    private Long teacherId;

    /**
     * 已阅读安全资料ID集合 (JSON数组格式，如 [1, 2])
     */
    private String readMaterialIds;

    /**
     * 已阅读完成资料篇数
     */
    private Integer readMaterialCount;

    /**
     * 首次阅读安全资料时间
     */
    private LocalDateTime studyStartTime;

    /**
     * 全部必读资料阅读完成时间
     */
    private LocalDateTime studyCompleteTime;

    /**
     * 当前安全教育五阶段状态 (NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED)
     */
    private String safetyStatus;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}
