-- ==============================================================================
-- 阶段6: 实习过程管理与周报批阅数据库增量脚本 (init_phase6.sql)
-- 包含 3 张核心业务表:
-- 1. internship_weekly_report (学生实习周报主表)
-- 2. internship_weekly_report_history (周报流转与退回历史快照表)
-- 3. internship_guidance_record (指导教师过程指导与走访台账表)
-- 具备幂等性 (CREATE TABLE IF NOT EXISTS)
-- ==============================================================================

USE `internship_db`;

-- 1. 学生实习周报主表
CREATE TABLE IF NOT EXISTS `internship_weekly_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `teacher_id` BIGINT NOT NULL COMMENT '指导教师用户ID',
    `dept_id` BIGINT NOT NULL COMMENT '二级院系ID',
    `week_number` INT NOT NULL COMMENT '周次序号 (第几周, 如1, 2, 3...)',
    `start_date` DATE NOT NULL COMMENT '本周起止开始日期',
    `end_date` DATE NOT NULL COMMENT '本周起止结束日期',
    `deadline_time` DATETIME NOT NULL COMMENT '本周截止提交时刻',
    `work_content` TEXT DEFAULT NULL COMMENT '本周工作内容 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
    `work_summary` TEXT DEFAULT NULL COMMENT '实习收获与体会 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
    `problem_encountered` TEXT DEFAULT NULL COMMENT '遇到的问题与思路 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
    `next_week_plan` TEXT DEFAULT NULL COMMENT '下周工作计划 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '成果或佐证附件URL (引用路径)',
    `version` INT NOT NULL DEFAULT 1 COMMENT '修改版本号 (退回重提自增)',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '周报状态 (DRAFT:草稿, SUBMITTED:待批阅, REVIEWED:已批阅, RETURNED:已退回)',
    `is_overdue` TINYINT NOT NULL DEFAULT 0 COMMENT '是否逾期提交 (0:按期, 1:逾期)',
    `overdue_days` INT NOT NULL DEFAULT 0 COMMENT '逾期天数',
    `submit_time` DATETIME DEFAULT NULL COMMENT '正式提交时间',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '周报批阅得分 (百分制 0.00-100.00)',
    `review_comment` TEXT DEFAULT NULL COMMENT '导师指导评语/退回意见 (GUIDANCE-001)',
    `review_annotations` TEXT DEFAULT NULL COMMENT '逐项批注内容 (JSON格式)',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '实际批阅人用户ID',
    `review_time` DATETIME DEFAULT NULL COMMENT '批阅完成时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_student_week` (`task_id`, `student_id`, `week_number`),
    KEY `idx_weekly_teacher_status` (`teacher_id`, `status`, `is_deleted`),
    KEY `idx_weekly_student_task` (`student_id`, `task_id`),
    KEY `idx_weekly_dept_status` (`dept_id`, `status`),
    KEY `idx_weekly_status_submit` (`status`, `submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习周报主表';

-- 2. 周报流转与退回历史快照表
CREATE TABLE IF NOT EXISTS `internship_weekly_report_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `report_id` BIGINT NOT NULL COMMENT '周报主表ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `version` INT NOT NULL COMMENT '本次流转对应的周报版本号',
    `action` VARCHAR(32) NOT NULL COMMENT '操作动作 (SUBMIT:正式提交, APPROVE:批阅通过, RETURN:退回修改)',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
    `operator_role` VARCHAR(32) NOT NULL COMMENT '操作人角色',
    `return_reason` TEXT DEFAULT NULL COMMENT '退回修改原因 (退回时必填)',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '批阅得分 (若有)',
    `review_comment` TEXT DEFAULT NULL COMMENT '批语快照 (若有)',
    `snapshot_content` LONGTEXT NOT NULL COMMENT '当时周报全文数据快照 (JSON格式)',
    `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_history_report` (`report_id`, `version`),
    KEY `idx_history_student` (`student_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周报流转与退回历史快照表';

-- 3. 指导教师过程走访与指导台账表
CREATE TABLE IF NOT EXISTS `internship_guidance_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `teacher_id` BIGINT NOT NULL COMMENT '指导教师用户ID',
    `teacher_name` VARCHAR(64) NOT NULL COMMENT '指导教师姓名',
    `student_id` BIGINT NOT NULL COMMENT '被指导学生用户ID',
    `student_name` VARCHAR(64) NOT NULL COMMENT '被指导学生姓名',
    `dept_id` BIGINT NOT NULL COMMENT '二级院系ID',
    `guidance_date` DATETIME NOT NULL COMMENT '指导开展时间 (GUIDANCE-003)',
    `guidance_type` VARCHAR(32) NOT NULL COMMENT '指导方式 (PHONE:电话, ONLINE:网络, ONSITE:实地走访, EMAIL_OTHER:其他)',
    `content_summary` TEXT NOT NULL COMMENT '交流内容要点 (GUIDANCE-003)',
    `student_feedback` TEXT DEFAULT NULL COMMENT '学生在岗确认反馈 (API-067)',
    `feedback_time` DATETIME DEFAULT NULL COMMENT '首次确认反馈时间，确认后不可修改',
    `feedback_status` VARCHAR(32) NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '反馈确认状态 (UNCONFIRMED:未确认, CONFIRMED:已确认锁定)',
    `followup_actions` TEXT DEFAULT NULL COMMENT '后续关注与改进跟进举措',
    `location` VARCHAR(255) DEFAULT NULL COMMENT '走访地点 (实地走访时必填)',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '走访凭证/谈话记录佐证材料URL (引用路径)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_guidance_teacher` (`teacher_id`, `task_id`),
    KEY `idx_guidance_student_date` (`student_id`, `guidance_date`),
    KEY `idx_guidance_dept` (`dept_id`, `guidance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指导教师过程指导与走访台账表';
