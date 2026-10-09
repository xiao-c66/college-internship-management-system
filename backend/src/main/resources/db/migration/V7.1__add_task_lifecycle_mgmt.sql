-- 任务生命周期与名单审计迁移脚本
ALTER TABLE internship_task 
    ADD COLUMN assign_mode VARCHAR(32) NOT NULL DEFAULT 'ORG' COMMENT '分配模式: ORG-组织范围, STUDENT-指定学生' AFTER internship_mode;

CREATE TABLE IF NOT EXISTS internship_task_lifecycle_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务ID',
    action VARCHAR(32) NOT NULL COMMENT '操作动作(REVOKE, DISABLE, ARCHIVE)',
    reason VARCHAR(500) NOT NULL COMMENT '操作原因',
    pre_status VARCHAR(32) NOT NULL COMMENT '操作前状态',
    post_status VARCHAR(32) NOT NULL COMMENT '操作后状态',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务生命周期日志';

CREATE TABLE IF NOT EXISTS internship_task_student_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    action VARCHAR(32) NOT NULL COMMENT '操作动作(ADD, REMOVE)',
    reason VARCHAR(500) NOT NULL COMMENT '操作原因',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    INDEX idx_task_student (task_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务学生调整审计日志';
