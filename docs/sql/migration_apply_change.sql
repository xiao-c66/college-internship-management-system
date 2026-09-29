-- ==============================================================================
-- 迁移脚本：高校实习重大信息变更申请与双级审批闭环
-- 对应基线：APPLY-009 一票否决机制下的重大信息合规变更
-- 仅限在可丢弃的隔离 Docker 测试库 (端口 3308) 中验证，严禁向生产库或共享库执行！
-- ==============================================================================

CREATE TABLE IF NOT EXISTS `internship_apply_change` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '变更单主键ID',
  `apply_id` BIGINT NOT NULL COMMENT '原实习申报ID',
  `task_id` BIGINT NOT NULL COMMENT '所属实习任务ID',
  `student_id` BIGINT NOT NULL COMMENT '申请学生ID',
  `student_number` VARCHAR(64) NOT NULL COMMENT '学生学号',
  `student_name` VARCHAR(64) NOT NULL COMMENT '学生姓名',
  `dept_id` BIGINT NOT NULL COMMENT '院系ID',
  `teacher_id` BIGINT NULL COMMENT '指导教师ID (初审责任人)',
  `teacher_name` VARCHAR(64) NULL COMMENT '指导教师姓名',
  
  -- 原信息快照 (提交时从原申请只读提取)
  `orig_company_name` VARCHAR(128) NOT NULL COMMENT '原实习单位',
  `orig_job_position` VARCHAR(64) NOT NULL COMMENT '原岗位',
  `orig_job_address` VARCHAR(255) NOT NULL COMMENT '原地点',
  `orig_contact_person` VARCHAR(64) NOT NULL COMMENT '原联系人',
  `orig_contact_phone` VARCHAR(32) NOT NULL COMMENT '原联系电话',
  `orig_contact_email` VARCHAR(64) NULL COMMENT '原联系邮箱',
  `orig_start_date` DATE NOT NULL COMMENT '原开始日期',
  `orig_end_date` DATE NOT NULL COMMENT '原结束日期',
  `orig_internship_mode` VARCHAR(32) NOT NULL COMMENT '原组织模式',
  `orig_job_duties` TEXT NULL COMMENT '原工作职责',
  `orig_agreement_file_url` VARCHAR(512) NULL COMMENT '原协议文件URL',

  -- 学生提交的新信息
  `new_company_name` VARCHAR(128) NOT NULL COMMENT '拟变更实习单位',
  `new_job_position` VARCHAR(64) NOT NULL COMMENT '拟变更岗位',
  `new_job_address` VARCHAR(255) NOT NULL COMMENT '拟变更地点',
  `new_contact_person` VARCHAR(64) NOT NULL COMMENT '拟变更联系人',
  `new_contact_phone` VARCHAR(32) NOT NULL COMMENT '拟变更联系电话',
  `new_contact_email` VARCHAR(64) NULL COMMENT '拟变更联系邮箱',
  `new_start_date` DATE NOT NULL COMMENT '拟开始日期',
  `new_end_date` DATE NOT NULL COMMENT '拟结束日期',
  `new_internship_mode` VARCHAR(32) NOT NULL COMMENT '拟组织模式',
  `new_job_duties` TEXT NULL COMMENT '拟工作职责',
  `new_agreement_file_url` VARCHAR(512) NULL COMMENT '新三方协议附件URL',

  -- 变更事由与补充佐证
  `change_reason` TEXT NOT NULL COMMENT '变更事由详细陈述 (不少于10字)',
  `proof_file_url` VARCHAR(512) NULL COMMENT '变更证明材料URL',

  -- 状态机与并发防重: PENDING_TEACHER, PENDING_DEPT, APPROVED, REJECTED
  `change_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_TEACHER' COMMENT '状态',
  `current_step` VARCHAR(32) NOT NULL DEFAULT 'TEACHER_INITIAL' COMMENT '当前审批节点',
  
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_apply_id` (`apply_id`),
  INDEX `idx_task_student` (`task_id`, `student_id`),
  INDEX `idx_teacher_status` (`teacher_id`, `change_status`),
  INDEX `idx_dept_status` (`dept_id`, `change_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习重大信息变更申请表';

CREATE TABLE IF NOT EXISTS `internship_apply_change_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '轨迹主键ID',
  `change_id` BIGINT NOT NULL COMMENT '所属变更单ID',
  `node_name` VARCHAR(32) NOT NULL COMMENT '审批节点: SUBMIT, TEACHER_INITIAL_AUDIT, DEPT_FINAL_AUDIT',
  `operator_id` BIGINT NOT NULL COMMENT '操作人用户ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  `operator_role` VARCHAR(32) NOT NULL COMMENT '操作人角色: STUDENT, TEACHER, DEPT_ADMIN, SYS_ADMIN',
  `audit_action` VARCHAR(32) NOT NULL COMMENT '动作: SUBMIT, APPROVE, REJECT',
  `audit_opinion` TEXT NOT NULL COMMENT '审核意见/说明',
  `snapshot_status` VARCHAR(32) NOT NULL COMMENT '流转后状态快照',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间戳',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_change_id` (`change_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实习变更审批历史轨迹表';
