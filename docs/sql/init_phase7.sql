-- ================================================================================================
-- 高校实习全过程管理系统 - 阶段7数据库初始化与增量升级脚本
-- 功能：11张阶段7业务表创建 + internship_task.grade_rules_json 幂等增补 + 预警默认规则预置
-- 红线约束：严禁 DROP/TRUNCATE/重建已有阶段4~6数据表；全量采用 IF NOT EXISTS 保证幂等执行
-- ================================================================================================

USE `internship_db`;

-- ------------------------------------------------------------------------------------------------
-- 0. 幂等增补 internship_task.grade_rules_json 字段
-- ------------------------------------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'internship_task' 
      AND COLUMN_NAME = 'grade_rules_json'
);

SET @alter_sql = IF(@col_exists = 0, 
    'ALTER TABLE `internship_task` ADD COLUMN `grade_rules_json` TEXT DEFAULT NULL COMMENT ''任务级五级制等第划分规则配置JSON'' AFTER `material_checklist`', 
    'SELECT ''Column grade_rules_json already exists in internship_task, skipping.'' AS result'
);

PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------------------------
-- 1. 阶段材料与提报明细主表 (student_material_item)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `student_material_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '材料主键ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生ID',
    `material_code` VARCHAR(64) NOT NULL COMMENT '材料编码: TRIPARTITE_AGREEMENT, EMPLOYMENT_NOTICE, SAFETY_TRAINING_RECORD, MIDTERM_SUMMARY, SUMMARY_REPORT',
    `material_name` VARCHAR(128) NOT NULL COMMENT '材料显示名称',
    `material_type` VARCHAR(32) NOT NULL DEFAULT 'VOUCHER_FILE' COMMENT '材料形态: VOUCHER_FILE(凭据URL), REPORT_TEXT(长文本), HYBRID(图文)',
    `content_text` MEDIUMTEXT DEFAULT NULL COMMENT '长文本正文内容 (总结报告/中期总结正文)',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '凭据佐证网络URL (jpg/png/pdf)',
    `file_name` VARCHAR(256) DEFAULT NULL COMMENT '原始文件名称',
    `file_size` BIGINT UNSIGNED DEFAULT NULL COMMENT '文件字节大小',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前提报版本号',
    `status` VARCHAR(32) NOT NULL DEFAULT 'UNSUBMITTED' COMMENT '状态: UNSUBMITTED(未提交), SUBMITTED(已提交), APPROVED(查验合格), RETURNED(退回重修)',
    `submit_time` DATETIME DEFAULT NULL COMMENT '最新提交时间',
    `audit_teacher_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '查验审核指导教师ID',
    `audit_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '材料查验考评分数 (0.00 ~ 100.00)',
    `audit_comment` TEXT DEFAULT NULL COMMENT '教师查验意见或退回原因',
    `audit_time` DATETIME DEFAULT NULL COMMENT '教师查验时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_student_material` (`task_id`, `student_id`, `material_code`),
    KEY `idx_mat_teacher_status` (`audit_teacher_id`, `status`),
    KEY `idx_mat_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段材料与提报明细主表';

-- ------------------------------------------------------------------------------------------------
-- 2. 阶段材料历史版本快照表 (material_version_history)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `material_version_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '版本历史主键ID',
    `material_id` BIGINT UNSIGNED NOT NULL COMMENT '关联材料主表ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生ID',
    `version` INT UNSIGNED NOT NULL COMMENT '快照版本号',
    `content_text` MEDIUMTEXT DEFAULT NULL COMMENT '该版本长文本正文快照',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '该版本佐证URL快照',
    `file_name` VARCHAR(256) DEFAULT NULL COMMENT '文件名称快照',
    `submit_time` DATETIME NOT NULL COMMENT '该版本提报时间',
    `audit_teacher_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '该版本查验教师ID',
    `audit_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '该版本考评分数快照',
    `audit_comment` TEXT DEFAULT NULL COMMENT '该版本教师评语快照',
    `audit_time` DATETIME DEFAULT NULL COMMENT '该版本查验时间',
    `status` VARCHAR(32) NOT NULL COMMENT '查验终态状态',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_mat_hist_item` (`material_id`, `version`),
    KEY `idx_mat_hist_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段材料历史版本快照表';

-- ------------------------------------------------------------------------------------------------
-- 3. 中期检查方案主表 (midterm_inspection_plan)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `midterm_inspection_plan` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '方案主键ID',
    `plan_name` VARCHAR(128) NOT NULL COMMENT '检查方案名称',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '发起二级院系ID',
    `sampling_mode` VARCHAR(32) NOT NULL DEFAULT 'RANDOM_RATIO' COMMENT '抽样模式: RANDOM_RATIO(按比例随机), CLASS_SELECT(整建制班级)',
    `sampling_ratio` DECIMAL(5, 2) DEFAULT NULL COMMENT '抽样比例百分比(如 20.00)',
    `start_date` DATE NOT NULL COMMENT '检查启动日期',
    `end_date` DATE NOT NULL COMMENT '检查截止日期',
    `expert_group` VARCHAR(500) DEFAULT NULL COMMENT '检查专家组成员信息',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '方案编制要求及指导说明',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '方案状态: DRAFT(草稿), PUBLISHED(已发布), COMPLETED(已完成)',
    `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    KEY `idx_plan_task_dept` (`task_id`, `dept_id`),
    KEY `idx_plan_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中期检查方案主表';

-- ------------------------------------------------------------------------------------------------
-- 4. 中期检查抽查与督导记录明细表 (midterm_inspection)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `midterm_inspection` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '检查记录主键ID',
    `plan_id` BIGINT UNSIGNED NOT NULL COMMENT '所属中期检查方案ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '被检查学生ID',
    `teacher_id` BIGINT UNSIGNED NOT NULL COMMENT '学生指导教师ID',
    `inspector_id` BIGINT UNSIGNED NOT NULL COMMENT '执行督导检查人员ID',
    `sampling_batch_no` VARCHAR(64) NOT NULL COMMENT '抽样批次号',
    `inspection_type` VARCHAR(32) NOT NULL DEFAULT 'ONSITE' COMMENT '检查形式: ONSITE(现场走访), ONLINE(网络视频连线), PHONE(电话问询)',
    `inspection_date` DATETIME NOT NULL COMMENT '实际检查执行时间',
    `company_situation` TEXT DEFAULT NULL COMMENT '实习单位环境与岗位匹配考察情况',
    `student_performance` TEXT DEFAULT NULL COMMENT '学生出勤、工作作风与专业技能掌握情况',
    `guidance_fulfillment` TEXT DEFAULT NULL COMMENT '指导教师履职与带教台账核查情况',
    `score` DECIMAL(5, 2) DEFAULT NULL COMMENT '督导评分 (0.00 ~ 100.00)',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '督导走访现场照片/签到凭证链接',
    `has_problem` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否发现突出问题: 0-无问题, 1-存在问题需整改',
    `problem_desc` TEXT DEFAULT NULL COMMENT '检查发现的问题明细描述',
    `status` VARCHAR(32) NOT NULL DEFAULT 'INSPECTED' COMMENT '记录状态: INSPECTED(已检查合格), PENDING_RECTIFY(待整改), RECTIFIED(已整改闭环)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_student` (`plan_id`, `student_id`),
    KEY `idx_inspect_task_student` (`task_id`, `student_id`),
    KEY `idx_inspect_teacher` (`teacher_id`),
    KEY `idx_inspect_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中期检查抽查与督导记录明细表';

-- ------------------------------------------------------------------------------------------------
-- 5. 中期检查限期整改通知与落实表 (midterm_rectification)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `midterm_rectification` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '整改记录主键ID',
    `inspection_id` BIGINT UNSIGNED NOT NULL COMMENT '关联中期检查记录ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '被整改学生ID',
    `responsible_user_id` BIGINT UNSIGNED NOT NULL COMMENT '整改主要责任人ID',
    `rectify_requirements` TEXT NOT NULL COMMENT '限期整改具体要求与改进指标',
    `deadline_date` DATE NOT NULL COMMENT '整改完成截止日期',
    `student_explanation` TEXT DEFAULT NULL COMMENT '学生整改措施落实说明与深刻检讨',
    `evidence_attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '整改后佐证凭据网络链接 (图片/PDF)',
    `submit_time` DATETIME DEFAULT NULL COMMENT '整改提交时间',
    `review_teacher_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '复核教师ID',
    `review_comment` TEXT DEFAULT NULL COMMENT '教师复核评价与整改成效批语',
    `review_time` DATETIME DEFAULT NULL COMMENT '教师复核时间',
    `close_dept_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '院系终审闭环人ID',
    `close_time` DATETIME DEFAULT NULL COMMENT '整改终审闭环时间',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_SUBMIT' COMMENT '整改状态: PENDING_SUBMIT(待提交整改), PENDING_REVIEW(待教师复核), REJECTED(复核不合格退回), CLOSED(已销号闭环)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    KEY `idx_rectify_inspect` (`inspection_id`),
    KEY `idx_rectify_task_student` (`task_id`, `student_id`),
    KEY `idx_rectify_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中期检查限期整改通知与落实表';

-- ------------------------------------------------------------------------------------------------
-- 6. 全局异常预警规则配置表 (warn_rule_config)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `warn_rule_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规则主键ID',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '预警规则唯一业务标识编码 (如 WARN_01)',
    `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `anomaly_category` VARCHAR(64) NOT NULL COMMENT '管理异常分类: SAFETY, SCHEDULE, GUIDANCE, QUALITY',
    `warn_level` VARCHAR(16) NOT NULL DEFAULT 'YELLOW' COMMENT '预警级别: YELLOW, ORANGE, RED',
    `threshold_params_json` TEXT NOT NULL COMMENT '动态判定阈值参数JSON格式',
    `dispatched_role` VARCHAR(32) NOT NULL DEFAULT 'TEACHER' COMMENT '默认派单处置责任角色: TEACHER, DEPT_ADMIN',
    `handling_timeout_days` INT UNSIGNED NOT NULL DEFAULT 3 COMMENT '处置超时天数',
    `is_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用: 0-停用, 1-启用',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '规则版本号',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '规则判定说明与管理依据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`),
    KEY `idx_rule_category_enabled` (`anomaly_category`, `is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局异常预警规则配置表';

-- ------------------------------------------------------------------------------------------------
-- 7. 异常预警工单主表 (warn_ticket)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `warn_ticket` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预警工单主键ID',
    `ticket_no` VARCHAR(64) NOT NULL COMMENT '预警工单流水号 (WT+年月日+8位序列)',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '涉及学生ID',
    `teacher_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联指导教师ID',
    `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '所属二级院系ID',
    `rule_id` BIGINT UNSIGNED NOT NULL COMMENT '命中预警规则ID',
    `rule_version` INT UNSIGNED NOT NULL COMMENT '命中时规则版本快照',
    `warn_level` VARCHAR(16) NOT NULL COMMENT '预警级别快照: YELLOW, ORANGE, RED',
    `warn_title` VARCHAR(256) NOT NULL COMMENT '预警概要标题',
    `evidence_snapshot_json` TEXT NOT NULL COMMENT '触发时固化的原始业务数据与证据快照JSON',
    `status` VARCHAR(32) NOT NULL DEFAULT 'TRIGGERED' COMMENT '工单主状态: TRIGGERED, DISPATCHED, PROCESSING, PENDING_REVIEW, CLOSED, FALSE_ALARM_CLOSED',
    `is_upgraded` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已升级至院系: 0-未升级, 1-已升级',
    `upgraded_time` DATETIME DEFAULT NULL COMMENT '升级时间戳',
    `upgrade_reason` VARCHAR(500) DEFAULT NULL COMMENT '升级原因陈述',
    `current_assignee_id` BIGINT UNSIGNED NOT NULL COMMENT '当前责任人ID',
    `current_assignee_role` VARCHAR(32) NOT NULL COMMENT '当前责任人角色: TEACHER, DEPT_ADMIN',
    `dedup_key` VARCHAR(128) NOT NULL COMMENT '永久去重特征哈希指纹 (永不置空)',
    `active_dedup_key` VARCHAR(128) DEFAULT NULL COMMENT '活动去重键 (活动中锁定，终态关闭置NULL释放)',
    `student_feedback` TEXT DEFAULT NULL COMMENT '学生填写的申辩情况说明',
    `student_feedback_time` DATETIME DEFAULT NULL COMMENT '学生提交申辩时间',
    `teacher_investigation` TEXT DEFAULT NULL COMMENT '教师调查核实陈述或误报判定报告',
    `handling_measures` TEXT DEFAULT NULL COMMENT '采取的干预措施与处理结果记录',
    `closed_time` DATETIME DEFAULT NULL COMMENT '最终闭环销号时间',
    `closed_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '最终关闭审核人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    UNIQUE KEY `uk_active_dedup` (`active_dedup_key`),
    KEY `idx_ticket_task_student` (`task_id`, `student_id`),
    KEY `idx_ticket_assignee` (`current_assignee_id`, `status`),
    KEY `idx_ticket_dept_level` (`dept_id`, `warn_level`, `status`),
    KEY `idx_ticket_dedup_history` (`dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常预警工单主表';

-- ------------------------------------------------------------------------------------------------
-- 8. 异常预警流转历史表 (warn_process_history)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `warn_process_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史记录主键ID',
    `ticket_id` BIGINT UNSIGNED NOT NULL COMMENT '关联预警工单ID',
    `action` VARCHAR(32) NOT NULL COMMENT '动作类型',
    `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
    `operator_role` VARCHAR(32) NOT NULL COMMENT '操作人角色',
    `content_remark` TEXT DEFAULT NULL COMMENT '流转说明',
    `attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '支撑凭证链接',
    `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    PRIMARY KEY (`id`),
    KEY `idx_warn_hist_ticket` (`ticket_id`),
    KEY `idx_warn_hist_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常预警流转历史表';

-- ------------------------------------------------------------------------------------------------
-- 9. 实习成绩五维综合评定主表 (score_summary)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `score_summary` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成绩主键ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生ID',
    `teacher_id` BIGINT UNSIGNED NOT NULL COMMENT '录入指导教师ID',
    `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '所属院系ID',
    `enterprise_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '五维之一: 企业鉴定分 (0-100)，未录入为NULL',
    `process_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '五维之二: 过程表现分 (0-100)，未录入为NULL',
    `weekly_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '五维之三: 阶段6周报综合均分 (0-100)，未汇算为NULL',
    `material_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '五维之四: 过程材料考评分 (0-100)，未评定为NULL',
    `summary_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '五维之五: 实习总结报告分 (0-100)，未评定为NULL',
    `final_score` DECIMAL(5, 2) DEFAULT NULL COMMENT '加权计算最终总分 (0.00-100.00)，未完成汇算为NULL',
    `score_level` VARCHAR(16) DEFAULT NULL COMMENT '五级制等第: EXCELLENT, GOOD, MEDIUM, PASS, FAIL，未定为NULL',
    `grade_rule_snapshot_json` TEXT NOT NULL COMMENT '评定时采用的任务级或全局等第划分规则快照JSON (固化后不可变)',
    `evaluation_comment` TEXT DEFAULT NULL COMMENT '教师综合实习总评语',
    `enterprise_evaluation_url` VARCHAR(512) DEFAULT NULL COMMENT '企业鉴定表盖章扫描件佐证链接',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '成绩状态: DRAFT(草稿), PENDING_AUDIT(待院系复核), PUBLICITY(公示中), PUBLISHED(已正式发布)',
    `publicity_start_time` DATETIME DEFAULT NULL COMMENT '公示开始时间',
    `publicity_end_time` DATETIME DEFAULT NULL COMMENT '公示截止时间',
    `confirmed_teacher_time` DATETIME DEFAULT NULL COMMENT '教师提交确认时间',
    `audited_dept_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '院系审核人ID',
    `audited_dept_time` DATETIME DEFAULT NULL COMMENT '院系审核发布时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本乐观锁',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_score_task_student` (`task_id`, `student_id`),
    KEY `idx_score_teacher` (`teacher_id`),
    KEY `idx_score_dept_status` (`dept_id`, `status`),
    KEY `idx_score_level` (`score_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习成绩五维综合评定主表';

-- ------------------------------------------------------------------------------------------------
-- 10. 成绩异议申诉与调分审批历史表 (score_audit_history)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `score_audit_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `score_id` BIGINT UNSIGNED NOT NULL COMMENT '关联成绩ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生ID',
    `action` VARCHAR(32) NOT NULL COMMENT '动作: APPEAL_APPLY, APPEAL_PASS, APPEAL_REJECT, SPECIAL_MODIFY',
    `appeal_reason` TEXT DEFAULT NULL COMMENT '申诉理由',
    `appeal_attachment_url` VARCHAR(512) DEFAULT NULL COMMENT '佐证链接',
    `old_score_snapshot` TEXT NOT NULL COMMENT '调分前快照JSON',
    `new_score_snapshot` TEXT DEFAULT NULL COMMENT '调分后快照JSON',
    `audit_user_id` BIGINT UNSIGNED NOT NULL COMMENT '处理人ID',
    `audit_user_name` VARCHAR(64) NOT NULL COMMENT '处理人姓名',
    `audit_comment` TEXT NOT NULL COMMENT '调分依据说明/驳回理由',
    `approval_doc_no` VARCHAR(128) DEFAULT NULL COMMENT '线下调分红头批文备案号',
    `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_score_hist_score` (`score_id`),
    KEY `idx_score_hist_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩异议申诉与调分审批历史表';

-- ------------------------------------------------------------------------------------------------
-- 11. 实习电子卷宗归档主表 (internship_archive)
-- ------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `internship_archive` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '卷宗主键ID',
    `archive_no` VARCHAR(64) NOT NULL COMMENT '电子卷宗唯一归档编号 (ARC+届别+学号)',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实习任务ID',
    `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生ID',
    `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '二级院系ID',
    `academic_year` VARCHAR(32) NOT NULL COMMENT '归档学年学期',
    `check_matrix_json` TEXT NOT NULL COMMENT '归档9项诊断核验快照JSON',
    `archive_bundle_url` VARCHAR(512) DEFAULT NULL COMMENT '电子档案袋完整ZIP打包文件相对存储路径',
    `archive_pdf_url` VARCHAR(512) DEFAULT NULL COMMENT '标准化实习登记表PDF相对存储路径',
    `archived_user_id` BIGINT UNSIGNED NOT NULL COMMENT '执行归档锁定操作人ID',
    `archived_time` DATETIME NOT NULL COMMENT '归档冻结时间',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ARCHIVED' COMMENT '状态: ARCHIVED(已归档锁定), SPECIAL_UNLOCKED(特批解锁调改中)',
    `special_unlock_reason` VARCHAR(500) DEFAULT NULL COMMENT '特批解锁事由',
    `special_doc_no` VARCHAR(128) DEFAULT NULL COMMENT '线下特批红头批文号',
    `unlocked_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '解锁超管ID',
    `unlocked_time` DATETIME DEFAULT NULL COMMENT '特批解锁起始时间',
    `unlock_expire_time` DATETIME DEFAULT NULL COMMENT '特批解锁到期截止时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '归档版本号 (再次归档递增)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_archive_no` (`archive_no`),
    UNIQUE KEY `uk_archive_task_student` (`task_id`, `student_id`),
    KEY `idx_archive_dept_year` (`dept_id`, `academic_year`),
    KEY `idx_archive_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习电子卷宗归档主表';

-- ------------------------------------------------------------------------------------------------
-- 12. 预置基础异常预警规则初始数据 (warn_rule_config)
-- ------------------------------------------------------------------------------------------------
INSERT INTO `warn_rule_config` (`rule_code`, `rule_name`, `anomaly_category`, `warn_level`, `threshold_params_json`, `dispatched_role`, `handling_timeout_days`, `is_enabled`, `version`, `description`)
VALUES 
('WARN_01', '长期无过程指导记录', 'GUIDANCE', 'YELLOW', '{"maxInactiveDays": 14}', 'TEACHER', 3, 1, 1, '指导教师超过14天未对学生进行过程走访或指导'),
('WARN_02', '周报连续逾期未提交', 'SCHEDULE', 'YELLOW', '{"consecutiveOverdueWeeks": 2}', 'TEACHER', 3, 1, 1, '学生连续2周以上逾期未提交周报'),
('WARN_03', '实习单位频繁变动', 'SAFETY', 'ORANGE', '{"maxChangeCount": 3}', 'DEPT_ADMIN', 3, 1, 1, '学生在单一实习周期内更换企业超过3次'),
('WARN_04', '安全准入超时未达标', 'SAFETY', 'RED', '{"maxDaysAfterTaskStart": 7}', 'TEACHER', 2, 1, 1, '实习任务启动7天后学生仍未完成安全考核承诺签署')
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`), `threshold_params_json` = VALUES(`threshold_params_json`);
