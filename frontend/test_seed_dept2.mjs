import { execSync } from 'child_process';

const sql = `
INSERT IGNORE INTO base_department (id, dept_code, dept_name, leader_name, phone, status, is_deleted)
VALUES (2, 'EM01', '经济管理学院', '李院长', '0571-88880002', 1, 0);

INSERT IGNORE INTO base_major (id, dept_id, major_code, major_name, status, is_deleted)
VALUES (3, 2, 'EM_TRADE_01', '国际经济与贸易', 1, 0);

INSERT IGNORE INTO base_class (id, dept_id, major_id, class_code, class_name, grade, status, is_deleted)
VALUES (2, 2, 3, 'TRADE2101', '国贸2101班', '2021', 1, 0);

INSERT IGNORE INTO sys_user (id, username, password, real_name, user_type, user_number, phone, email, dept_id, status, token_version, is_deleted)
SELECT 3010, 'deptadmin_em', password, '李经管', 'DEPT_ADMIN', 'EM_ADMIN_01', '13800138099', 'deptadmin_em@test.edu.cn', 2, 1, 1, 0
FROM sys_user WHERE id = 1
ON DUPLICATE KEY UPDATE dept_id = 2;

INSERT IGNORE INTO sys_user_role (user_id, role_id, create_time)
VALUES (3010, 2, NOW());
`;

execSync('docker exec -e MYSQL_PWD=123456 -i internship-mysql-isolated-e2e mysql -u root --default-character-set=utf8mb4 internship_db_isolated_warn', {
  input: sql
});

console.log('Seeded Dept 2 successfully');
