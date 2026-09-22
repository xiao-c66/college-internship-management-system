-- ==============================================================================
-- 高校实习全过程管理系统 - 阶段5业务数据库初始化脚本 (init_phase5.sql)
-- 仅包含阶段5所需的11张核心业务表及初始演示数据，可重复安全执行
-- 绝不删除、不重建、不覆盖阶段4的7张基础物理表及演示账号数据
-- ==============================================================================

USE `internship_db`;

-- 1. 实习批次任务主表
CREATE TABLE IF NOT EXISTS `internship_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_code` VARCHAR(32) NOT NULL COMMENT '任务编码',
    `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `dept_id` BIGINT NOT NULL COMMENT '所属二级院系ID',
    `academic_year` VARCHAR(16) NOT NULL COMMENT '所属学年 (如 2025-2026)',
    `semester` TINYINT NOT NULL COMMENT '所属学期 (1:第一学期, 2:第二学期)',
    `internship_mode` VARCHAR(32) NOT NULL DEFAULT 'DISTRIBUTED' COMMENT '组织模式 (CENTRALIZED:集中, DISTRIBUTED:分散, HYBRID:混合)',
    `start_date` DATE NOT NULL COMMENT '实习开始日期',
    `end_date` DATE NOT NULL COMMENT '实习结束日期',
    `weight_enterprise` DECIMAL(5,2) DEFAULT NULL COMMENT '企业评价成绩权重(%)',
    `weight_teacher_process` DECIMAL(5,2) DEFAULT NULL COMMENT '指导教师过程评价权重(%)',
    `weight_weekly_report` DECIMAL(5,2) DEFAULT NULL COMMENT '周报综合成绩权重(%)',
    `weight_stage_material` DECIMAL(5,2) DEFAULT NULL COMMENT '阶段材料成绩权重(%)',
    `weight_summary` DECIMAL(5,2) DEFAULT NULL COMMENT '实习总结成绩权重(%)',
    `material_checklist` TEXT DEFAULT NULL COMMENT '任务材料清单规范 (JSON格式)',
    `weekly_frequency` VARCHAR(32) DEFAULT NULL COMMENT '周报提交频次 (WEEKLY, BIWEEKLY)',
    `weekly_deadline_day` TINYINT DEFAULT NULL COMMENT '每周周报截止日 (1:周一 ~ 7:周日)',
    `safety_passing_score` INT DEFAULT NULL COMMENT '安全考试及格分',
    `safety_max_attempts` INT DEFAULT NULL COMMENT '安全考试最大允许重测次数',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态 (DRAFT:草稿, PUBLISHED:已发布, IN_PROGRESS:进行中, ENDED:已结束, ARCHIVED:已归档)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_code` (`task_code`),
    KEY `idx_task_dept` (`dept_id`),
    KEY `idx_task_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习批次任务主表';

-- 2. 任务关联专业表
CREATE TABLE IF NOT EXISTS `internship_task_major` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `major_id` BIGINT NOT NULL COMMENT '专业ID',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_major` (`task_id`, `major_id`),
    KEY `idx_major_task` (`major_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务关联专业表';

-- 3. 任务关联班级表
CREATE TABLE IF NOT EXISTS `internship_task_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_class` (`task_id`, `class_id`),
    KEY `idx_class_task` (`class_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务关联班级表';

-- 4. 任务圈定参与学生名单表
CREATE TABLE IF NOT EXISTS `internship_task_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `student_number` VARCHAR(64) NOT NULL COMMENT '学号',
    `student_name` VARCHAR(64) NOT NULL COMMENT '学生姓名',
    `class_id` BIGINT DEFAULT NULL COMMENT '班级ID',
    `teacher_id` BIGINT DEFAULT NULL COMMENT '指导教师用户ID (ASSIGN-001/003)',
    `read_material_ids` VARCHAR(256) DEFAULT NULL COMMENT '已阅读安全资料ID集合 (JSON数组格式)',
    `read_material_count` INT NOT NULL DEFAULT 0 COMMENT '已阅读资料篇数',
    `study_start_time` DATETIME DEFAULT NULL COMMENT '首次阅读安全资料时间',
    `study_complete_time` DATETIME DEFAULT NULL COMMENT '全部必读资料阅读完成时间',
    `safety_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '当前安全教育五阶段状态 (NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_student` (`task_id`, `student_id`),
    KEY `idx_student_task` (`student_id`, `task_id`),
    KEY `idx_task_teacher` (`task_id`, `teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务圈定参与学生名单表';

-- 5. 安全教育学习资料表 (SAFE-001 & SAFE-002)
CREATE TABLE IF NOT EXISTS `safety_material_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT DEFAULT NULL COMMENT '所属任务ID (NULL为全校通用资料 SAFE-001)',
    `title` VARCHAR(128) NOT NULL COMMENT '资料标题',
    `content_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '资料类型 (TEXT:图文, PDF:文档, VIDEO:视频, URL:外链)',
    `content_body` LONGTEXT DEFAULT NULL COMMENT '图文正文内容',
    `file_url` VARCHAR(512) DEFAULT NULL COMMENT '附件文件路径',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:启用, 0:停用)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_safety_mat_task` (`task_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全教育学习资料表';

-- 6. 安全教育题库表 (SAFE-003)
CREATE TABLE IF NOT EXISTS `safety_test_question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT DEFAULT NULL COMMENT '所属任务ID (NULL为全校通用题库)',
    `question_type` VARCHAR(32) NOT NULL COMMENT '题型 (SINGLE_CHOICE:单选, MULTIPLE_CHOICE:多选, JUDGMENT:判断)',
    `stem` TEXT NOT NULL COMMENT '题干描述',
    `options` TEXT NOT NULL COMMENT '选项内容 (JSON格式)',
    `correct_answer` VARCHAR(64) NOT NULL COMMENT '正确答案 (如 "A" 或 "A,B" 或 "TRUE")',
    `score` INT NOT NULL DEFAULT 10 COMMENT '该题分值',
    `analysis` TEXT DEFAULT NULL COMMENT '试题解析与安全警示说明',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:启用, 0:停用)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_safety_quest_task` (`task_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全教育题库表';

-- 7. 学生安全测试记录表 (SAFE-005)
CREATE TABLE IF NOT EXISTS `safety_exam_attempt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `attempt_no` INT NOT NULL DEFAULT 1 COMMENT '第几次测试',
    `total_score` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '测试得分',
    `is_passed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否达标合格 (1:是, 0:否)',
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开考时间',
    `submit_time` DATETIME DEFAULT NULL COMMENT '交卷时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_exam_student_task` (`student_id`, `task_id`, `attempt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生安全测试记录表';

-- 8. 安全测试逐题作答明细表
CREATE TABLE IF NOT EXISTS `safety_exam_answer_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `attempt_id` BIGINT NOT NULL COMMENT '测试记录ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `student_answer` VARCHAR(64) NOT NULL COMMENT '学生作答答案',
    `is_correct` TINYINT NOT NULL DEFAULT 0 COMMENT '是否正确 (1:正确, 0:错误)',
    `score_obtained` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '获得分值',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_detail_attempt` (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全测试逐题作答明细表';

-- 9. 安全承诺书签署与保险凭据表 (SAFE-006 & SAFE-007)
CREATE TABLE IF NOT EXISTS `safety_commitment_sign` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `commitment_text` TEXT NOT NULL COMMENT '承诺书文本内容快照',
    `is_signed` TINYINT NOT NULL DEFAULT 1 COMMENT '是否签署 (1:已签署, 0:未签署)',
    `sign_ip` VARCHAR(64) DEFAULT NULL COMMENT '签署客户端IP',
    `sign_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签署时间',
    `insurance_file_url` VARCHAR(512) DEFAULT NULL COMMENT '商业人身意外伤害保险保单凭据',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_student_sign` (`task_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全承诺书签署与保险凭据表';

-- 10. 学生实习申报主表 (APPLY-001 ~ APPLY-009)
CREATE TABLE IF NOT EXISTS `internship_apply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '关联实习任务ID',
    `student_id` BIGINT NOT NULL COMMENT '申请学生用户ID',
    `student_number` VARCHAR(64) NOT NULL COMMENT '学号',
    `student_name` VARCHAR(64) NOT NULL COMMENT '学生姓名',
    `dept_id` BIGINT NOT NULL COMMENT '所属二级院系ID',
    `major_id` BIGINT DEFAULT NULL COMMENT '所属专业ID',
    `class_id` BIGINT DEFAULT NULL COMMENT '所属班级ID',
    `company_name` VARCHAR(128) NOT NULL COMMENT '实习单位全称',
    `job_position` VARCHAR(64) NOT NULL COMMENT '实习岗位名称',
    `job_address` VARCHAR(255) NOT NULL COMMENT '工作详细地址',
    `company_contact_person` VARCHAR(64) NOT NULL COMMENT '企业指导教师/联系人姓名',
    `company_contact_phone` VARCHAR(32) NOT NULL COMMENT '联系人电话',
    `company_contact_email` VARCHAR(64) DEFAULT NULL COMMENT '联系人邮箱',
    `start_date` DATE NOT NULL COMMENT '实习开始日期',
    `end_date` DATE NOT NULL COMMENT '实习结束日期',
    `internship_mode` VARCHAR(32) NOT NULL DEFAULT 'DISTRIBUTED' COMMENT '实习模式 (CENTRALIZED:集中, DISTRIBUTED:分散)',
    `job_duties` TEXT DEFAULT NULL COMMENT '具体职责与工作内容',
    `agreement_file_url` VARCHAR(512) DEFAULT NULL COMMENT '实习三方协议/单位接收函附件',
    `apply_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态 (DRAFT:草稿, SUBMITTED:待初审, TEACHER_APPROVED:初审通过待复审, TEACHER_REJECTED:初审退回, APPROVED:终审生效锁定, DEPT_REJECTED:复审退回)',
    `is_locked` TINYINT NOT NULL DEFAULT 0 COMMENT '生效锁定标识 (0:未锁定, 1:终审生效只读锁定 APPLY-009)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_apply_student_task` (`student_id`, `task_id`),
    KEY `idx_apply_dept_status` (`dept_id`, `apply_status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习申报主表';

-- 11. 实习申报审批轨迹与快照表 (REVIEW-001 ~ REVIEW-006)
CREATE TABLE IF NOT EXISTS `apply_audit_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `apply_id` BIGINT NOT NULL COMMENT '实习申请ID',
    `node_name` VARCHAR(32) NOT NULL COMMENT '审核节点 (TEACHER_AUDIT:教师初审, DEPT_AUDIT:院系复审)',
    `auditor_id` BIGINT NOT NULL COMMENT '审核人用户ID',
    `auditor_name` VARCHAR(64) NOT NULL COMMENT '审核人姓名',
    `auditor_role` VARCHAR(32) NOT NULL COMMENT '审核人角色 (TEACHER, DEPT_ADMIN)',
    `audit_action` VARCHAR(32) NOT NULL COMMENT '审核动作 (APPROVED:通过, REJECTED:退回)',
    `audit_opinion` TEXT NOT NULL COMMENT '审核意见 (退回时强制trim后不少于5个字)',
    `snapshot_data` LONGTEXT DEFAULT NULL COMMENT '当时申报表单完整快照 (JSON格式)',
    `audit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (历史记录严禁物理删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_apply_id` (`apply_id`, `audit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习申报审批流转历史轨迹表';

-- ==============================================================================
-- 阶段5 初始演示业务种子数据 [DEMO ONLY]
-- ==============================================================================

-- 预置1条已发布实习任务 (计算机学院 2026届毕业实习批次)
INSERT INTO `internship_task` (
    `id`, `task_code`, `task_name`, `dept_id`, `academic_year`, `semester`, `internship_mode`, 
    `start_date`, `end_date`, `weight_enterprise`, `weight_teacher_process`, `weight_weekly_report`, 
    `weight_stage_material`, `weight_summary`, `material_checklist`, `weekly_frequency`, 
    `weekly_deadline_day`, `safety_passing_score`, `safety_max_attempts`, `status`, `is_deleted`
) VALUES (
    1, 'TASK2026CS01', '2026届计算机学院软件工程毕业实习批次 [DEMO]', 1, '2025-2026', 2, 'DISTRIBUTED',
    '2026-03-01', '2026-06-30', 20.00, 20.00, 20.00, 20.00, 20.00,
    '[{"name":"实习三方协议书","required":true},{"name":"企业接收函","required":true},{"name":"实习鉴定表","required":true}]',
    'WEEKLY', 7, 80, 3, 'PUBLISHED', 0
) ON DUPLICATE KEY UPDATE `task_name` = VALUES(`task_name`);

-- 绑定专业与班级
INSERT INTO `internship_task_major` (`id`, `task_id`, `major_id`, `is_deleted`)
VALUES (1, 1, 1, 0)
ON DUPLICATE KEY UPDATE `major_id` = VALUES(`major_id`);

INSERT INTO `internship_task_class` (`id`, `task_id`, `class_id`, `is_deleted`)
VALUES (1, 1, 1, 0)
ON DUPLICATE KEY UPDATE `class_id` = VALUES(`class_id`);

-- 圈定学生参与名单并指派指导教师 (演示学生 张晓峰 指派给 李教授 teacher_id=3)
INSERT INTO `internship_task_student` (`id`, `task_id`, `student_id`, `student_number`, `student_name`, `class_id`, `teacher_id`, `is_deleted`)
VALUES (1, 1, 4, '2021003011', '张晓峰 [DEMO]', 1, 3, 0)
ON DUPLICATE KEY UPDATE `student_name` = VALUES(`student_name`), `teacher_id` = VALUES(`teacher_id`);

-- 预置全校通用安全资料 (SAFE-001, task_id IS NULL)
INSERT INTO `safety_material_item` (`id`, `task_id`, `title`, `content_type`, `content_body`, `sort_order`, `status`, `is_deleted`)
VALUES 
(1, NULL, '高校学生校外实习安全总则与人身权益保护指南 (全校通用)', 'TEXT', '一、严格遵守国家法律法规及实习用人单位安全生产规章制度；二、妥善防范通勤交通事故与工伤意外；三、坚决防范传销、电信诈骗及非法用工陷阱；四、遇突发险情第一时间向校内指导教师与学院负责人报告。', 1, 1, 0),
(2, 1, '计算机与互联网行业驻场与远程实习安全注意事项 (任务专属)', 'TEXT', '一、严格遵守用人单位商业机密与知识产权合规要求；二、注意用电安全与长时间高负荷作业防劳损；三、外出调研或现场技术服务务必佩戴工牌并向导师报备。', 2, 1, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- 预置安全测试试题 (SAFE-003)
INSERT INTO `safety_test_question` (`id`, `task_id`, `question_type`, `stem`, `options`, `correct_answer`, `score`, `analysis`, `sort_order`, `status`, `is_deleted`)
VALUES 
(1, 1, 'SINGLE_CHOICE', '在校外实习期间，若遇到用人单位要求缴纳押金、扣押身份证件或介绍非专业高薪离奇工作，学生应当采取的正确做法是？', '[{"key":"A","text":"顺从单位要求并私下支付"},{"key":"B","text":"坚决拒绝并立即向学校指导教师及学院报告"},{"key":"C","text":"自行向同学校友借款支付"},{"key":"D","text":"隐瞒不报继续试岗"}]', 'B', 30, '我国劳动合同法明确规定任何用人单位不得以任何名义扣押劳动者居民身份证等证件，不得要求劳动者提供担保或者以其他名义向劳动者收取财物。', 1, 1, 0),
(2, 1, 'JUDGMENT', '学生在校外自主分散实习期间，可以未经请假擅自脱岗、跨省旅行或擅自更换未经学校审核批准的实习单位。', '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]', 'FALSE', 35, '根据教育部《职业学校学生实习管理规定》及普通高校实习管理规范，学生在实习期间严禁擅自脱岗，单位或岗位变动必须履行正式变更审批流程。', 2, 1, 0),
(3, 1, 'SINGLE_CHOICE', '校外实习学生遇突发人身安全侵害、意外工伤或重大纠纷时，第一应急汇报责任人是：', '[{"key":"A","text":"仅发朋友圈求助"},{"key":"B","text":"校内指导教师及学院实习负责人"},{"key":"C","text":"等待实习结束后返校再汇报"},{"key":"D","text":"自行私下和解不再联系学校"}]', 'B', 35, '发生人身意外伤亡或重大险情必须第一时间向校内指导教师和学院实习负责人汇报，以便学校启动应急救援与保险理赔联动机制。', 3, 1, 0)
ON DUPLICATE KEY UPDATE `stem` = VALUES(`stem`);
