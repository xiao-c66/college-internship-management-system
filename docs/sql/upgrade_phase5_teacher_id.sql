-- ==========================================================
-- 阶段5数据库增量升级脚本：为 internship_task_student 表增加 teacher_id 字段及索引
-- 适用环境：MySQL 8.0+
-- 作用：支持指导教师分配 (ASSIGN-001/003) 与行级数据权限隔离 (SAFE-008)
-- 幂等性：具备字段与索引存在性检测，支持多次重复执行
-- ==========================================================

USE `internship_db`;

-- 1. 添加 teacher_id 字段（若不存在）
SET @exist_col := (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'internship_task_student' 
      AND COLUMN_NAME = 'teacher_id'
);

SET @sql_col := IF(@exist_col = 0, 
    'ALTER TABLE `internship_task_student` ADD COLUMN `teacher_id` BIGINT DEFAULT NULL COMMENT ''指导教师用户ID (ASSIGN-001/003)'' AFTER `class_id`', 
    'SELECT "Column teacher_id already exists"'
);
PREPARE stmt_col FROM @sql_col;
EXECUTE stmt_col;
DEALLOCATE PREPARE stmt_col;

-- 2. 添加联合索引 idx_task_teacher（若不存在）
SET @exist_idx := (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'internship_task_student' 
      AND INDEX_NAME = 'idx_task_teacher'
);

SET @sql_idx := IF(@exist_idx = 0, 
    'ALTER TABLE `internship_task_student` ADD INDEX `idx_task_teacher` (`task_id`, `teacher_id`)', 
    'SELECT "Index idx_task_teacher already exists"'
);
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
