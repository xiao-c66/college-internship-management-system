-- 阶段7教师演示数据：3名学生、三种过程状态（仅DML，可重复执行）
-- 用途：给老师演示周报、指导台账、材料、中期检查、预警、成绩和归档页面。
USE `internship_db`;
SET NAMES utf8mb4;
START TRANSACTION;

-- 1. 创建/复用三名演示学生账号，密码均为 123456
INSERT INTO sys_user
(username, password, real_name, user_type, user_number, phone, email, dept_id, major_id, class_id, status, token_version, is_deleted)
VALUES
('student_demo_01', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '王晨（演示）', 'STUDENT', '2021003021', '13900000021', 'student_demo_01@college.edu.cn', 1, 1, 1, 1, 1, 0),
('student_demo_02', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '李雪（演示）', 'STUDENT', '2021003022', '13900000022', 'student_demo_02@college.edu.cn', 1, 1, 1, 1, 1, 0),
('student_demo_03', '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', '赵磊（演示）', 'STUDENT', '2021003023', '13900000023', 'student_demo_03@college.edu.cn', 1, 1, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE
password = VALUES(password), real_name = VALUES(real_name), user_type = VALUES(user_type),
user_number = VALUES(user_number), dept_id = VALUES(dept_id), major_id = VALUES(major_id),
class_id = VALUES(class_id), status = 1, is_deleted = 0;

SET @s1 = (SELECT id FROM sys_user WHERE username = 'student_demo_01');
SET @s2 = (SELECT id FROM sys_user WHERE username = 'student_demo_02');
SET @s3 = (SELECT id FROM sys_user WHERE username = 'student_demo_03');
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (@s1, 4), (@s2, 4), (@s3, 4);

-- 2. 创建一项独立演示任务，并清理该演示任务上一次运行留下的明细
INSERT INTO internship_task
(task_code, task_name, dept_id, academic_year, semester, internship_mode, start_date, end_date,
 weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary,
 material_checklist, grade_rules_json, weekly_frequency, weekly_deadline_day,
 safety_passing_score, safety_max_attempts, status, is_deleted)
VALUES
('DEMO-P7-2026', '阶段7教师验收演示任务', 1, '2026-2027', 1, 'DISTRIBUTED', '2026-09-01', '2026-12-31',
 20.00, 20.00, 20.00, 20.00, 20.00,
 '[{"name":"三方协议","required":true},{"name":"中期进展总结","required":true},{"name":"实习总结报告","required":true}]',
 '{"source":"DEMO","rules":{"excellentMin":90.00,"goodMin":80.00,"mediumMin":70.00,"passMin":60.00}',
 'WEEKLY', 7, 80, 3, 'PUBLISHED', 0)
ON DUPLICATE KEY UPDATE
 task_name = VALUES(task_name), dept_id = 1, academic_year = VALUES(academic_year), semester = 1,
 start_date = VALUES(start_date), end_date = VALUES(end_date), weight_enterprise = 20.00,
 weight_teacher_process = 20.00, weight_weekly_report = 20.00, weight_stage_material = 20.00,
 weight_summary = 20.00, material_checklist = VALUES(material_checklist), grade_rules_json = VALUES(grade_rules_json),
 weekly_frequency = 'WEEKLY', weekly_deadline_day = 7, safety_passing_score = 80, safety_max_attempts = 3,
 status = 'PUBLISHED', is_deleted = 0;
SET @task_id = (SELECT id FROM internship_task WHERE task_code = 'DEMO-P7-2026');

-- 清理本演示任务的数据（不碰阶段4至阶段7已有任务数据）
DELETE FROM warn_process_history WHERE ticket_id IN (SELECT id FROM warn_ticket WHERE task_id = @task_id);
DELETE FROM warn_ticket WHERE task_id = @task_id;
DELETE FROM midterm_rectification WHERE task_id = @task_id;
DELETE FROM midterm_inspection WHERE task_id = @task_id;
DELETE FROM midterm_inspection_plan WHERE task_id = @task_id;
DELETE FROM material_version_history WHERE task_id = @task_id;
DELETE FROM internship_weekly_report_history WHERE task_id = @task_id;
DELETE FROM internship_weekly_report WHERE task_id = @task_id;
DELETE FROM internship_guidance_record WHERE task_id = @task_id;
DELETE FROM student_material_item WHERE task_id = @task_id;
DELETE FROM score_audit_history WHERE score_id IN (SELECT id FROM score_summary WHERE task_id = @task_id);
DELETE FROM score_summary WHERE task_id = @task_id;
DELETE FROM internship_archive WHERE task_id = @task_id;
DELETE FROM safety_exam_answer_detail WHERE attempt_id IN (SELECT id FROM safety_exam_attempt WHERE task_id = @task_id);
DELETE FROM safety_exam_attempt WHERE task_id = @task_id;
DELETE FROM safety_commitment_sign WHERE task_id = @task_id;
DELETE FROM internship_apply WHERE task_id = @task_id;
DELETE FROM internship_task_student WHERE task_id = @task_id;

-- 3. 三名学生进入同一任务，形成可对比的三组演示场景
INSERT INTO internship_task_student
(task_id, student_id, student_number, student_name, class_id, teacher_id, read_material_ids, read_material_count,
 study_start_time, study_complete_time, safety_status, is_deleted)
VALUES
(@task_id, @s1, '2021003021', '王晨（演示）', 1, 3, '[1,2]', 2, NOW(), NULL, 'STUDYING', 0),
(@task_id, @s2, '2021003022', '李雪（演示）', 1, 3, '[1,2]', 2, NOW(), NOW(), 'COMPLETED', 0),
(@task_id, @s3, '2021003023', '赵磊（演示）', 1, 3, '[]', 0, NULL, NULL, 'NOT_STARTED', 0);

-- 4. 阶段5准入数据：三名学生均有申报，均已终审通过；安全签署/考试按场景区分
INSERT INTO internship_apply
(task_id, student_id, student_number, student_name, dept_id, major_id, class_id,
 company_name, job_position, job_address, company_contact_person, company_contact_phone,
 start_date, end_date, internship_mode, job_duties, apply_status, is_locked, is_deleted)
VALUES
(@task_id, @s1, '2021003021', '王晨（演示）', 1, 1, 1, '华星软件技术有限公司', 'Java开发实习生', '深圳市南山区', '周老师', '13800000021', '2026-09-01', '2026-12-31', 'DISTRIBUTED', '参与后端接口开发与测试', 'APPROVED', 1, 0),
(@task_id, @s2, '2021003022', '李雪（演示）', 1, 1, 1, '远航智能科技有限公司', '数据分析实习生', '广州市天河区', '陈经理', '13800000022', '2026-09-01', '2026-12-31', 'DISTRIBUTED', '参与业务数据分析与报表制作', 'APPROVED', 1, 0),
(@task_id, @s3, '2021003023', '赵磊（演示）', 1, 1, 1, '新锐信息服务有限公司', '测试工程实习生', '东莞市松山湖', '刘经理', '13800000023', '2026-09-01', '2026-12-31', 'DISTRIBUTED', '参与系统测试与缺陷跟踪', 'APPROVED', 1, 0);

INSERT INTO safety_commitment_sign
(task_id, student_id, commitment_text, is_signed, sign_ip, sign_time, is_deleted)
VALUES
(@task_id, @s1, '高校实习安全知晓承诺书', 1, '127.0.0.1', NOW(), 0),
(@task_id, @s2, '高校实习安全知晓承诺书', 1, '127.0.0.1', NOW(), 0);
INSERT INTO safety_exam_attempt
(task_id, student_id, attempt_no, total_score, is_passed, start_time, submit_time, is_deleted)
VALUES
(@task_id, @s1, 1, 92.00, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(@task_id, @s2, 1, 96.00, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(@task_id, @s3, 1, 56.00, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

-- 5. 周报三种状态：待批阅、已通过、已退回
INSERT INTO internship_weekly_report
(task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time,
 work_content, work_summary, problem_encountered, next_week_plan, version, status, is_overdue, overdue_days,
 submit_time, score, review_comment, reviewer_id, review_time, is_deleted)
VALUES
(@task_id, @s1, 3, 1, 1, '2026-09-01', '2026-09-07', '2026-09-07 23:59:59',
 '完成用户模块接口开发与单元测试，整理提交记录。', '熟悉了项目分层结构和接口联调流程。', '首次联调时发现参数校验提示不够清晰。', '继续完善异常处理并补充接口测试。', 1, 'SUBMITTED', 0, 0, NOW(), NULL, NULL, NULL, NULL, 0),
(@task_id, @s2, 3, 1, 1, '2026-09-01', '2026-09-07', '2026-09-07 23:59:59',
 '完成数据清洗、指标统计和可视化报表初版。', '掌握了从原始数据到业务指标的完整分析过程。', '部分历史数据存在缺失值，需要继续核验。', '完成报表优化并向导师提交分析说明。', 1, 'APPROVED', 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), 95.00, '数据分析过程清晰，结论可靠，继续保持规范记录。', 3, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(@task_id, @s3, 3, 1, 1, '2026-09-01', '2026-09-07', '2026-09-07 23:59:59',
 '完成登录模块测试用例编写和第一轮缺陷登记。', '初步熟悉了缺陷管理流程。', '测试证据不足，部分边界场景未覆盖。', '补充异常输入和权限边界测试并重新提交。', 2, 'RETURNED', 1, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, '请补充边界场景、复现步骤和整改计划后重新提交。', 3, DATE_SUB(NOW(), INTERVAL 1 HOUR), 0);
SET @r1 = (SELECT id FROM internship_weekly_report WHERE task_id=@task_id AND student_id=@s1 AND week_number=1);
SET @r2 = (SELECT id FROM internship_weekly_report WHERE task_id=@task_id AND student_id=@s2 AND week_number=1);
SET @r3 = (SELECT id FROM internship_weekly_report WHERE task_id=@task_id AND student_id=@s3 AND week_number=1);
INSERT INTO internship_weekly_report_history
(report_id, task_id, student_id, version, action, operator_id, operator_name, operator_role, return_reason, score, review_comment, snapshot_content)
VALUES
(@r1, @task_id, @s1, 1, 'SUBMIT', @s1, '王晨（演示）', 'STUDENT', NULL, NULL, NULL, '{"status":"SUBMITTED","scenario":"待教师批阅"}'),
(@r2, @task_id, @s2, 1, 'APPROVE', 3, '李教授', 'TEACHER', NULL, 95.00, '数据分析过程清晰，结论可靠。', '{"status":"APPROVED","scenario":"已批阅通过"}'),
(@r3, @task_id, @s3, 2, 'RETURN', 3, '李教授', 'TEACHER', '请补充边界场景、复现步骤和整改计划后重新提交。', NULL, '请按退回意见补充材料。', '{"status":"RETURNED","scenario":"待整改"}');

-- 6. 指导台账：一条待学生确认、两条已确认、一条风险场景待确认
INSERT INTO internship_guidance_record
(task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_date, guidance_type,
 content_summary, student_feedback, feedback_time, feedback_status, followup_actions, location, is_deleted)
VALUES
(@task_id, 3, '李教授', @s1, '王晨（演示）', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 'ONLINE', '线上检查开发进度并指导接口测试，明确下一周任务。', NULL, NULL, 'UNCONFIRMED', '下次检查异常处理覆盖情况。', NULL, 0),
(@task_id, 3, '李教授', @s2, '李雪（演示）', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 'ONSITE', '现场了解数据分析进度，核验工作记录与岗位匹配情况。', '已了解指导意见，将按计划完成报表优化。', DATE_SUB(NOW(), INTERVAL 2 DAY), 'CONFIRMED', '继续完善分析报告。', '广州市天河区远航智能', 0),
(@task_id, 3, '李教授', @s2, '李雪（演示）', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 'ONLINE', '线上复盘指标口径和数据质量问题，确认改进方案。', '已确认本次指导内容和后续安排。', DATE_SUB(NOW(), INTERVAL 1 DAY), 'CONFIRMED', '提交最终分析说明。', NULL, 0),
(@task_id, 3, '李教授', @s3, '赵磊（演示）', 1, NOW(), 'PHONE', '电话了解测试任务完成情况，发现边界测试证据不足。', NULL, NULL, 'UNCONFIRMED', '补充测试证据后再次核验。', NULL, 0);

-- 7. 阶段材料：李雪已通过，王晨待查验，赵磊退回整改
INSERT INTO student_material_item
(task_id, student_id, material_code, material_name, material_type, content_text, version, status, submit_time,
 audit_teacher_id, audit_score, audit_comment, audit_time, is_deleted)
VALUES
(@task_id, @s1, 'MIDTERM_SUMMARY', '中期进展总结', 'REPORT_TEXT', '王晨阶段进展总结：已完成接口开发、测试用例整理和问题复盘。', 1, 'SUBMITTED', NOW(), NULL, NULL, NULL, NULL, 0),
(@task_id, @s2, 'TRIPARTITE_AGREEMENT', '三方协议', 'VOUCHER_FILE', NULL, 1, 'APPROVED', DATE_SUB(NOW(), INTERVAL 2 DAY), 3, 94.00, '材料完整，单位盖章信息清晰。', DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(@task_id, @s2, 'SUMMARY_REPORT', '实习总结报告', 'REPORT_TEXT', '李雪实习总结报告：围绕数据质量、指标体系和业务分析展开总结，形成了完整的实践记录。', 1, 'APPROVED', DATE_SUB(NOW(), INTERVAL 1 DAY), 3, 96.00, '总结内容完整，分析过程和成果描述清晰。', NOW(), 0),
(@task_id, @s3, 'SUMMARY_REPORT', '实习总结报告', 'REPORT_TEXT', '赵磊实习总结报告初稿，待补充边界测试证据和问题整改过程。', 2, 'RETURNED', NOW(), 3, NULL, '请补充测试证据、缺陷复现步骤和整改结果。', NOW(), 0);

-- 8. 中期检查与整改：两名正常，一名待整改
INSERT INTO midterm_inspection_plan
(plan_name, task_id, dept_id, sampling_mode, sampling_ratio, start_date, end_date, expert_group, remark, status, created_by)
VALUES ('阶段7教师演示中期检查方案', @task_id, 1, 'RANDOM_RATIO', 100.00, '2026-09-15', '2026-10-15', '学院实习督导组', '用于验收演示三种中期状态。', 'PUBLISHED', 2);
SET @plan_id = LAST_INSERT_ID();
INSERT INTO midterm_inspection
(plan_id, task_id, student_id, teacher_id, inspector_id, sampling_batch_no, inspection_type, inspection_date,
 company_situation, student_performance, guidance_fulfillment, score, has_problem, problem_desc, status, is_deleted)
VALUES
(@plan_id, @task_id, @s1, 3, 2, 'DEMO-BATCH-01', 'ONLINE', DATE_SUB(NOW(), INTERVAL 1 DAY), '单位岗位匹配，工作环境正常。', '能够按计划完成开发任务。', '教师指导记录完整。', 88.00, 0, NULL, 'INSPECTED', 0),
(@plan_id, @task_id, @s2, 3, 2, 'DEMO-BATCH-01', 'ONSITE', DATE_SUB(NOW(), INTERVAL 2 DAY), '单位反馈良好，岗位匹配度高。', '工作态度认真，阶段成果清晰。', '指导教师履职记录完整。', 95.00, 0, NULL, 'INSPECTED', 0),
(@plan_id, @task_id, @s3, 3, 2, 'DEMO-BATCH-01', 'PHONE', NOW(), '单位岗位基本匹配，需加强过程沟通。', '测试边界覆盖不足，证据留存不完整。', '指导记录已提出整改要求。', 62.00, 1, '边界测试证据不足，需补充整改说明。', 'PENDING_RECTIFY', 0);
SET @inspection3 = (SELECT id FROM midterm_inspection WHERE task_id=@task_id AND student_id=@s3 LIMIT 1);
INSERT INTO midterm_rectification
(inspection_id, task_id, student_id, responsible_user_id, rectify_requirements, deadline_date, status, is_deleted)
VALUES (@inspection3, @task_id, @s3, @s3, '补充登录、权限和异常输入边界测试证据，提交复现步骤与整改结果。', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'PENDING_SUBMIT', 0);

-- 9. 异常预警：赵磊保留一条活动预警，供教师/院系演示处置闭环
SET @rule_id = (SELECT id FROM warn_rule_config WHERE rule_code = 'WARN_02' LIMIT 1);
INSERT INTO warn_ticket
(ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, warn_level, warn_title,
 evidence_snapshot_json, status, is_upgraded, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key, is_deleted)
VALUES
('WT-DEMO-20260922-03', @task_id, @s3, 3, 1, @rule_id, 1, 'ORANGE', '周报退回且中期检查发现测试证据不足',
 '{"scenario":"DEMO_RISK","source":"周报退回+中期整改","need":"补充边界测试证据"}', 'TRIGGERED', 0, 3, 'TEACHER',
 SHA2(CONCAT('DEMO-P7-2026:', @s3, ':QUALITY'), 256), SHA2(CONCAT('DEMO-P7-2026:', @s3, ':QUALITY'), 256), 0);
SET @ticket3 = LAST_INSERT_ID();
INSERT INTO warn_process_history
(ticket_id, action, operator_id, operator_name, operator_role, content_remark)
VALUES (@ticket3, 'TRIGGERED', 3, '李教授', 'TEACHER', '系统演示数据：待教师核查并推进整改闭环。');

-- 10. 五维成绩：待批阅、已发布、待公示三种状态
INSERT INTO score_summary
(task_id, student_id, teacher_id, dept_id, enterprise_score, process_score, weekly_score, material_score, summary_score,
 final_score, score_level, grade_rule_snapshot_json, evaluation_comment, status, version, is_deleted)
VALUES
(@task_id, @s1, 3, 1, 90.00, 88.00, 92.00, 86.00, NULL, NULL, NULL,
 '{"source":"DEMO","rules":{"excellentMin":90.00,"goodMin":80.00,"mediumMin":70.00,"passMin":60.00}}', '总结报告尚待提交，成绩暂未汇算。', 'PENDING_AUDIT', 1, 0),
(@task_id, @s2, 3, 1, 96.00, 94.00, 95.00, 94.00, 93.00, 94.40, 'EXCELLENT',
 '{"source":"DEMO","rules":{"excellentMin":90.00,"goodMin":80.00,"mediumMin":70.00,"passMin":60.00}}', '实习表现优秀，已完成阶段性成果验收。', 'PUBLISHED', 1, 0),
(@task_id, @s3, 3, 1, 78.00, 65.00, NULL, 60.00, NULL, NULL, NULL,
 '{"source":"DEMO","rules":{"excellentMin":90.00,"goodMin":80.00,"mediumMin":70.00,"passMin":60.00}}', '存在过程整改事项，待整改完成后继续评定。', 'PENDING_PUBLICITY', 1, 0);

-- 11. 李雪形成一份可查询的已归档卷宗（物理文件随后由归档服务生成）
INSERT INTO internship_archive
(archive_no, task_id, student_id, dept_id, academic_year, check_matrix_json, archive_bundle_url, archive_pdf_url,
 archived_user_id, archived_time, status, version, is_deleted)
VALUES
('ARC2026DEMO_02', @task_id, @s2, 1, '2026-2027',
 '{"SAFE_PASS":true,"APPLY_PASS":true,"WEEKLY_RATE":true,"GUIDANCE_COUNT":true,"MIDTERM_INSPECT":true,"RECTIFY_CLOSED":true,"WARN_TICKETS_CLOSED":true,"MATERIAL_APPROVED":true,"SCORE_PUBLISHED":true}',
 NULL, NULL, 2, NOW(), 'ARCHIVED', 1, 0);

COMMIT;
SELECT @task_id AS demo_task_id, @s1 AS demo_student_01, @s2 AS demo_student_02, @s3 AS demo_student_03;
