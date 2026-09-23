-- ==============================================================================
-- 测试数据库最小基础数据初始化脚本 (seed_test_base.sql)
-- 专用于 internship_db_test，仅包含基础组织架构、角色、基准账号、系统配置与全校通用安全资料
-- 绝不包含正式业务数据与历史审计日志
-- ==============================================================================
USE `internship_db_test`;
SET NAMES utf8mb4;

-- 1. 基础院系
INSERT INTO `base_department` (`id`, `dept_code`, `dept_name`, `leader_name`, `phone`, `status`, `is_deleted`)
VALUES (1, 'CS01', '计算机科学与技术学院', '张院长', '0571-88880001', 1, 0)
ON DUPLICATE KEY UPDATE `dept_name` = VALUES(`dept_name`), `leader_name` = VALUES(`leader_name`);

-- 2. 基础专业
INSERT INTO `base_major` (`id`, `dept_id`, `major_code`, `major_name`, `status`, `is_deleted`)
VALUES 
(1, 1, 'CS_SE_01', '软件工程', 1, 0),
(2, 1, 'CS_NET_01', '网络工程', 1, 0)
ON DUPLICATE KEY UPDATE `major_name` = VALUES(`major_name`);

-- 3. 基础班级
INSERT INTO `base_class` (`id`, `dept_id`, `major_id`, `class_code`, `class_name`, `grade`, `status`, `is_deleted`)
VALUES (1, 1, 1, 'SE2101', '软件工程2101班', '2021', 1, 0)
ON DUPLICATE KEY UPDATE `class_name` = VALUES(`class_name`);

-- 4. 核心系统角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `is_deleted`)
VALUES 
(1, 'ROLE_SYS_ADMIN', '学校管理员', '全校教务与系统全局管理最高权限', 0),
(2, 'ROLE_DEPT_ADMIN', '院系负责人', '二级学院实习教学主管与终审权限', 0),
(3, 'ROLE_TEACHER', '指导教师', '实习过程指导、批阅与初审权限', 0),
(4, 'ROLE_STUDENT', '学生', '实习申报、周报填报与材料提交权限', 0)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`);

-- 5. 基准账号 (明文密码统一为 123456, BCrypt Hash)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `user_type`, `user_number`, `phone`, `email`, `dept_id`, `major_id`, `class_id`, `status`, `token_version`, `is_deleted`)
VALUES 
(1, 'admin', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '全校管理员 [DEMO]', 'SYS_ADMIN', 'A1001', '13900000001', 'admin@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(2, 'deptadmin', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '计算机负责人 [DEMO]', 'DEPT_ADMIN', 'D1001', '13900000002', 'dept@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(3, 'teacher', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '李教授 [DEMO]', 'TEACHER', 'T1001', '13900000003', 'teacher@college.edu.cn', 1, NULL, NULL, 1, 1, 0),
(4, 'student', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '张晓峰 [DEMO]', 'STUDENT', '2021003011', '13900000004', 'student@college.edu.cn', 1, 1, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`), `password` = VALUES(`password`), `token_version` = VALUES(`token_version`);

-- 6. 用户角色关联
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`)
VALUES 
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 4)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- 7. 阶段8 运维基准配置
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `is_system`, `remark`, `created_by`, `created_time`, `updated_by`, `updated_time`, `is_deleted`)
VALUES 
(1, '慢接口与慢SQL判定告警阈值', 'system.slow-sql-threshold-ms', '500', 1, '慢接口与慢SQL判定告警阈值 (毫秒，范围 100~5000)', 'SYSTEM_INIT', NOW(), 'SYSTEM_INIT', NOW(), 0),
(2, '数据库热备文件保留周期', 'system.backup.retention-days', '30', 1, '数据库热备文件保留周期 (天，范围 7~365)', 'SYSTEM_INIT', NOW(), 'SYSTEM_INIT', NOW(), 0),
(3, '数据库热备执行防抖限流', 'system.backup.rate-limit-seconds', '30', 1, '数据库热备执行与下载防刷限流冷却 (秒，范围 5~120)', 'SYSTEM_INIT', NOW(), 'SYSTEM_INIT', NOW(), 0),
(4, '定时调度批处理任务执行超时', 'system.job.execution-timeout-seconds', '60', 1, '定时调度批处理任务单次执行硬超时 (秒，范围 10~300)', 'SYSTEM_INIT', NOW(), 'SYSTEM_INIT', NOW(), 0)
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- 8. 全校通用安全资料 (出厂基准内置 SAFE-001, task_id IS NULL)
INSERT INTO `safety_material_item` (`id`, `task_id`, `title`, `content_type`, `content_body`, `sort_order`, `status`, `is_deleted`)
VALUES 
(1, NULL, '高校学生校外实习安全总则与人身权益保护指南 (全校通用)', 'TEXT', '一、严格遵守国家法律法规及实习用人单位安全生产规章制度；二、妥善防范通勤交通事故与工伤意外；三、坚决防范传销、电信诈骗及非法用工陷阱；四、遇突发险情第一时间向校内指导教师与学院负责人报告。', 1, 1, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `content_body` = VALUES(`content_body`), `status` = 1, `is_deleted` = 0;
