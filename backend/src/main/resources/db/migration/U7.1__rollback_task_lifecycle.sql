-- 任务生命周期与名单审计回滚脚本
DROP TABLE IF EXISTS internship_task_student_audit_log;
DROP TABLE IF EXISTS internship_task_lifecycle_log;
ALTER TABLE internship_task DROP COLUMN assign_mode;
