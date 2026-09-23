-- ================================================================================================
-- 高校实习全过程管理系统 - 阶段8 物理数据表初始化脚本
-- 技术特征: 具备严格幂等性 (CREATE TABLE IF NOT EXISTS), UTF8MB4 字符集, 严禁包含 DROP / TRUNCATE
-- 新增数据表: 5 张 (sys_config, sys_job, sys_backup_record, sys_notice, sys_notice_read)
-- 执行规约: 严禁覆盖或修改阶段 4~7 已建的 32 张物理表
-- ================================================================================================

-- 1. 系统全局运维参数配置表 (仅限管理阶段8自身运维参数，严禁覆盖阶段6/7参数)
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '参数主键ID',
  `config_name` VARCHAR(100) NOT NULL COMMENT '参数名称',
  `config_key` VARCHAR(100) NOT NULL COMMENT '参数键名 (全局唯一)',
  `config_value` TEXT NOT NULL COMMENT '参数键值',
  `is_system` TINYINT NOT NULL DEFAULT 1 COMMENT '系统内置 (0-否, 1-是不可删)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建者',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新者',
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统全局运维参数配置表';

-- 2. 系统受限定时任务配置表 (仅限白名单任务编码，严禁反射执行)
CREATE TABLE IF NOT EXISTS `sys_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_code` VARCHAR(64) NOT NULL COMMENT '受限任务编码白名单 (WARN_SCAN_JOB / ARCHIVE_EXPIRE_RECLOCK_JOB / BACKUP_CLEANUP_JOB)',
  `job_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
  `cron_expression` VARCHAR(255) NOT NULL COMMENT 'Cron执行表达式',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-暂停, 1-正常)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建者',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新者',
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统受限定时任务配置表';

-- 3. 数据库受控备份记录表 (仅记录相对路径与散列)
CREATE TABLE IF NOT EXISTS `sys_backup_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '备份ID',
  `backup_file_name` VARCHAR(255) NOT NULL COMMENT '受控相对文件名 (如 internship_db_backup_xxx.sql)',
  `file_size_bytes` BIGINT NOT NULL COMMENT '文件字节大小',
  `table_count` INT NOT NULL COMMENT '包含数据表数',
  `sha256_digest` VARCHAR(64) NOT NULL COMMENT '备份文件SHA-256完整性校验摘要',
  `is_locked` TINYINT NOT NULL DEFAULT 0 COMMENT '锁定保护标识 (0-可清理, 1-锁定防删基线)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态 (SUCCESS-成功, FAILED-失败)',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败错误信息',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人员ID (0为定时触发)',
  `backup_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_backup_time` (`backup_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库受控备份记录表';

-- 4. 全局教学通知公告表 (支持 dedup_key 业务防重，强制 Jsoup XSS 过滤)
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `dedup_key` VARCHAR(128) DEFAULT NULL COMMENT '业务防重键 (人工批次号或事件唯一标识)',
  `notice_title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `notice_type` VARCHAR(32) NOT NULL COMMENT '类型 (NOTICE-通知, ANNOUNCE-公告)',
  `notice_content` LONGTEXT NOT NULL COMMENT '公告正文 (服务端 Jsoup 白名单净化存储)',
  `target_scope` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '发布范围 (ALL-全校, DEPT-本院系)',
  `target_dept_id` BIGINT DEFAULT NULL COMMENT '限定院系ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-关闭, 1-正常发布)',
  `publisher_id` BIGINT NOT NULL COMMENT '发布人ID',
  `publisher_name` VARCHAR(64) NOT NULL COMMENT '发布人姓名',
  `publish_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识 (0-未删除, 1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_dedup` (`dedup_key`, `is_deleted`),
  KEY `idx_scope` (`target_scope`, `target_dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局教学通知公告表';

-- 5. 通知公告用户阅读状态记录表 (联合唯一防重复并发写入)
CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notice_id` BIGINT NOT NULL COMMENT '公告ID',
  `user_id` BIGINT NOT NULL COMMENT '阅读用户ID',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知公告用户阅读状态记录表';
