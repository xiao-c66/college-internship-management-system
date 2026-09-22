-- ==============================================================================
-- 高校实习全过程管理系统 - 阶段4基础数据库初始化脚本 (init_phase4.sql)
-- 仅包含阶段4所需的7张基础表及演示种子数据，可重复安全执行
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS `internship_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `internship_db`;

-- 1. 二级院系表
CREATE TABLE IF NOT EXISTS `base_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dept_code` VARCHAR(32) NOT NULL COMMENT '院系编码',
    `dept_name` VARCHAR(64) NOT NULL COMMENT '院系名称',
    `leader_name` VARCHAR(64) DEFAULT NULL COMMENT '负责人姓名',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:启用, 0:停用)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_dept_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二级院系表';

-- 2. 专业表
CREATE TABLE IF NOT EXISTS `base_major` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dept_id` BIGINT NOT NULL COMMENT '所属院系ID',
    `major_code` VARCHAR(32) NOT NULL COMMENT '专业编码',
    `major_name` VARCHAR(64) NOT NULL COMMENT '专业名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:启用, 0:停用)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_major_code` (`major_code`),
    KEY `idx_major_dept_id` (`dept_id`),
    KEY `idx_major_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业信息表';

-- 3. 班级表
CREATE TABLE IF NOT EXISTS `base_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dept_id` BIGINT NOT NULL COMMENT '所属院系ID',
    `major_id` BIGINT NOT NULL COMMENT '所属专业ID',
    `class_code` VARCHAR(32) NOT NULL COMMENT '班级代码',
    `class_name` VARCHAR(64) NOT NULL COMMENT '班级名称',
    `grade` VARCHAR(16) NOT NULL COMMENT '年级/入学年份',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:启用, 0:停用)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_code` (`class_code`),
    KEY `idx_class_dept_major` (`dept_id`, `major_id`),
    KEY `idx_class_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行政班级表';

-- 4. 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_code` VARCHAR(32) NOT NULL COMMENT '角色标识',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色定义表';

-- 5. 用户主表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '登录登录名/账号',
    `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `user_type` VARCHAR(32) NOT NULL COMMENT '用户类型 (STUDENT, TEACHER, DEPT_ADMIN, SYS_ADMIN)',
    `user_number` VARCHAR(64) NOT NULL COMMENT '学号/教工号',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '联系邮箱',
    `dept_id` BIGINT DEFAULT NULL COMMENT '所属学院ID',
    `major_id` BIGINT DEFAULT NULL COMMENT '所属专业ID (学生专有)',
    `class_id` BIGINT DEFAULT NULL COMMENT '所属班级ID (学生专有)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态 (1:正常, 0:禁用)',
    `token_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Token版本号 (注销/改密时自增使旧Token失效)',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_user_number` (`user_number`),
    KEY `idx_user_dept` (`dept_id`),
    KEY `idx_user_class` (`class_id`),
    KEY `idx_user_type_status` (`user_type`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 6. 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_user` (`role_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 7. 操作与审计日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(64) NOT NULL COMMENT '模块标题/操作名称',
    `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型 (LOGIN, LOGOUT, QUERY, INSERT, UPDATE, DELETE, DENIED)',
    `method` VARCHAR(128) NOT NULL COMMENT '调用方法名称',
    `request_method` VARCHAR(16) NOT NULL COMMENT 'HTTP请求方式',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人员ID',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人员姓名/账号',
    `oper_url` VARCHAR(255) NOT NULL COMMENT '请求URL',
    `oper_ip` VARCHAR(64) DEFAULT NULL COMMENT '主机IP地址',
    `oper_param` TEXT DEFAULT NULL COMMENT '请求参数 (脱敏后)',
    `json_result` TEXT DEFAULT NULL COMMENT '响应结果',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态 (1:成功, 0:失败)',
    `error_msg` TEXT DEFAULT NULL COMMENT '错误信息',
    `oper_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_user_time` (`operator_id`, `oper_time`),
    KEY `idx_log_type_status` (`business_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作与安全审计日志表';

-- ==============================================================================
-- 阶段4 演示种子数据 (全部密码统一使用 BCrypt 散列，对应明文: 123456)
-- 密码 Hash: $2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6
-- ==============================================================================

-- 插入基础院系 (若不存在)
INSERT INTO `base_department` (`id`, `dept_code`, `dept_name`, `leader_name`, `phone`, `status`, `is_deleted`)
VALUES (1, 'CS01', '计算机科学与技术学院', '张院长', '0571-88880001', 1, 0)
ON DUPLICATE KEY UPDATE `dept_name` = VALUES(`dept_name`);

-- 插入基础专业 (若不存在)
INSERT INTO `base_major` (`id`, `dept_id`, `major_code`, `major_name`, `status`, `is_deleted`)
VALUES 
(1, 1, 'CS_SE_01', '软件工程', 1, 0),
(2, 1, 'CS_NET_01', '网络工程', 1, 0)
ON DUPLICATE KEY UPDATE `major_name` = VALUES(`major_name`);

-- 插入基础班级 (若不存在)
INSERT INTO `base_class` (`id`, `dept_id`, `major_id`, `class_code`, `class_name`, `grade`, `status`, `is_deleted`)
VALUES (1, 1, 1, 'SE2101', '软件工程2101班', '2021', 1, 0)
ON DUPLICATE KEY UPDATE `class_name` = VALUES(`class_name`);

-- 插入4类核心系统角色 (若不存在)
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `is_deleted`)
VALUES 
(1, 'ROLE_SYS_ADMIN', '学校管理员', '全校教务与系统全局管理最高权限', 0),
(2, 'ROLE_DEPT_ADMIN', '院系负责人', '二级学院实习教学主管与终审权限', 0),
(3, 'ROLE_TEACHER', '指导教师', '实习过程指导、批阅与初审权限', 0),
(4, 'ROLE_STUDENT', '学生', '实习申报、周报填报与材料提交权限', 0)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- 插入4类角色演示账号 (明文密码均为 123456)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `user_type`, `user_number`, `phone`, `email`, `dept_id`, `major_id`, `class_id`, `status`, `token_version`, `is_deleted`)
VALUES 
(1, 'admin', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '全校管理员 [DEMO]', 'SYS_ADMIN', 'A1001', '13900000001', 'admin@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(2, 'deptadmin', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '计算机负责人 [DEMO]', 'DEPT_ADMIN', 'D1001', '13900000002', 'dept@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(3, 'teacher', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '李教授 [DEMO]', 'TEACHER', 'T1001', '13900000003', 'teacher@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(4, 'student', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '张晓峰 [DEMO]', 'STUDENT', '2021003011', '13900000004', 'student@college.edu.cn', 1, 1, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`), `password` = VALUES(`password`), `token_version` = VALUES(`token_version`);

-- 绑定用户角色关系
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`)
VALUES 
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 4)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
