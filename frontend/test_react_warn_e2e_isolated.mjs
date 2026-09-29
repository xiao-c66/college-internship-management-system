import { execSync } from 'child_process';

const BASE_URL = 'http://127.0.0.1:3000/api/v1';

async function request(url, options = {}) {
  const res = await fetch(`${BASE_URL}${url}`, options);
  const data = await res.json().catch(() => ({}));
  return { status: res.status, ok: res.ok, data };
}

async function login(username, password = '123456') {
  const res = await request('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (res.data.code !== 200) {
    throw new Error(`Login failed for ${username}: ${JSON.stringify(res.data)}`);
  }
  return res.data.data.token;
}

function execSql(sql) {
  const res = execSync(
    'docker exec -e MYSQL_PWD=123456 -i internship-mysql-isolated-e2e mysql -u root -s -N --default-character-set=utf8mb4 internship_db_isolated_warn',
    { input: sql }
  ).toString();
  return res.split('\n').map(l => l.trim()).filter(l => l && !l.includes('[Warning]')).join('\n');
}

async function run() {
  console.log('================================================================');
  console.log('>>> [独立隔离环境] 预警 3 条分支真实端到端 (E2E) 验收测试');
  console.log('>>> 数据库: internship_db_isolated_warn (Docker port 3308)');
  console.log('>>> 严格隔离: 零连接/写入 internship_db_test 与正式库');
  console.log('================================================================\n');

  // 1. 登录各角色
  console.log('1. 获取各测试角色 JWT 认证令牌...');
  const adminToken = await login('admin');
  const deptAdminToken = await login('deptadmin');
  const teacherAToken = await login('teacher_warn_a');
  const teacherBToken = await login('teacher_warn_b');
  const studentToken = await login('student_warn_iso');
  console.log('   - admin / deptadmin / teacher_warn_a / teacher_warn_b / student_warn_iso 登录成功');

  console.log('0. 初始化隔离测试环境 (清空本轮运行工单表)...');
  execSql('DELETE FROM warn_process_history; DELETE FROM warn_ticket;');

  // 等待 10s 防刷冷却
  await new Promise(r => setTimeout(r, 10000));

  // =========================================================================
  // 分支 1: 预警正常闭环 (触发 -> 学生申辩 -> 教师处置 -> CLOSED -> 释放 active_dedup_key)
  // =========================================================================
  console.log('\n================================================================');
  console.log('>>> 分支 1: 预警正常闭环真实端到端验收');
  console.log('================================================================');

  console.log('1.1 教师 teacher_warn_a 触发任务 3101 预警扫描 (必须带 taskId)...');
  const scanRes1 = await request('/warn/scan?taskId=3101', {
    method: 'POST',
    headers: { Authorization: `Bearer ${teacherAToken}` }
  });
  console.log('    - 扫描响应:', scanRes1.data);
  if (scanRes1.data.code !== 200 || scanRes1.data.data.newTickets < 1) {
    throw new Error(`预警扫描未触发新工单: ${JSON.stringify(scanRes1.data)}`);
  }

  console.log('1.2 教师获取任务 3101 工单列表，核验学生 3003 的初始工单状态...');
  const ticketsRes1 = await request('/warn/tickets?taskId=3101', {
    headers: { Authorization: `Bearer ${teacherAToken}` }
  });
  const ticket1 = ticketsRes1.data.data.find(t => t.studentId === 3003);
  if (!ticket1) {
    throw new Error('未找到学生 3003 的预警工单');
  }
  const ticketId1 = ticket1.id;
  console.log(`    - 命中工单 ID: ${ticketId1}, 编号: ${ticket1.ticketNo}, 规则: ${ticket1.warnTitle}`);
  console.log(`    - 初始状态: ${ticket1.status} (预期: TRIGGERED)`);
  if (ticket1.status !== 'TRIGGERED') throw new Error(`工单初始状态不符: ${ticket1.status}`);

  console.log('1.3 学生 student_warn_iso (3003) 提交在线申辩事实说明...');
  const feedbackRes = await request(`/warn/tickets/${ticketId1}/feedback`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${studentToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      studentFeedback: '本人已在线下向指导教师递交纸质安全协议书，因网络故障尚未完成在线签字，现已同步补齐。'
    })
  });
  console.log('    - 申辩响应:', feedbackRes.data);
  if (feedbackRes.data.code !== 200) throw new Error('学生提交申辩失败');

  console.log('1.4 教师查看工单详情，核验流转历史包含 STUDENT_FEEDBACK 记录...');
  const detailRes1 = await request(`/warn/tickets/${ticketId1}`, {
    headers: { Authorization: `Bearer ${teacherAToken}` }
  });
  console.log(`    - 当前工单状态: ${detailRes1.data.data.status} (预期: PROCESSING)`);
  console.log(`    - 学生申辩说明: ${detailRes1.data.data.studentFeedback}`);
  const feedbackHist = detailRes1.data.data.processHistory.find(h => h.action === 'STUDENT_FEEDBACK');
  if (!feedbackHist) throw new Error('流转记录中未找到 STUDENT_FEEDBACK');
  console.log(`    - 流转记录包含申辩痕迹: [${feedbackHist.action}] ${feedbackHist.contentRemark}`);

  console.log('1.5 教师 teacher_warn_a 处置工单并闭环销号 (action=CLOSED)...');
  const handleRes = await request(`/warn/tickets/${ticketId1}/handle`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${teacherAToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      action: 'CLOSED',
      handlingMeasures: '指导教师已当面核验学生纸质签字原件，完成安全排查与合规建档，准予闭环销号。'
    })
  });
  console.log('    - 处置响应:', handleRes.data);
  if (handleRes.data.code !== 200) throw new Error('工单闭环处置失败');

  console.log('1.6 校验工单终态为 CLOSED，并核查 active_dedup_key 物理释放...');
  const detailResClosed = await request(`/warn/tickets/${ticketId1}`, {
    headers: { Authorization: `Bearer ${teacherAToken}` }
  });
  console.log(`    - 闭环后状态: ${detailResClosed.data.data.status} (预期: CLOSED)`);
  console.log(`    - 闭环处置措施: ${detailResClosed.data.data.handlingMeasures}`);
  const closedHist = detailResClosed.data.data.processHistory.find(h => h.action === 'CLOSED');
  if (!closedHist) throw new Error('流转记录中未找到 CLOSED');
  console.log(`    - 流转记录包含闭环痕迹: [${closedHist.action}] ${closedHist.contentRemark}`);

  const dedupKeyInDb = execSql(`SELECT IFNULL(active_dedup_key, 'NULL') FROM warn_ticket WHERE id = ${ticketId1};`);
  console.log(`    - 数据库 active_dedup_key 字段值: ${dedupKeyInDb} (预期: NULL - 成功释放去重锁)`);
  if (dedupKeyInDb !== 'NULL') throw new Error(`active_dedup_key 未被释放: ${dedupKeyInDb}`);
  console.log('>>> [PASS] 分支 1: 预警正常闭环端到端验证通过！');

  // =========================================================================
  // 分支 2: 工单指派/转派 (权限拦截 -> 院系指派 -> 新责任人接手并处理)
  // =========================================================================
  console.log('\n================================================================');
  console.log('>>> 分支 2: 工单指派/转派真实端到端验收');
  console.log('================================================================');

  const ticket2 = ticketsRes1.data.data.find(t => t.studentId === 3004);
  if (!ticket2) throw new Error('未找到学生 3004 的工单');
  const ticketId2 = ticket2.id;
  console.log(`2.1 目标工单 ID: ${ticketId2}, 原责任人: ${ticket2.currentAssigneeId} (teacher_warn_a)`);

  console.log('2.2 权限拦截测试: 学生/普通教师尝试转派工单 (预期 403)...');
  const illegalStudentDispatch = await request(`/warn/tickets/${ticketId2}/dispatch`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${studentToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ assigneeId: 3002, assigneeRole: 'TEACHER', remark: '越权转派' })
  });
  console.log(`    - 学生转派响应状态码: ${illegalStudentDispatch.status} (预期 403)`);
  if (illegalStudentDispatch.status !== 403) throw new Error('学生越权转派未被 403 拦截');

  const illegalTeacherDispatch = await request(`/warn/tickets/${ticketId2}/dispatch`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${teacherAToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ assigneeId: 3002, assigneeRole: 'TEACHER', remark: '平级转派' })
  });
  console.log(`    - 教师转派响应状态码: ${illegalTeacherDispatch.status} (预期 403)`);
  if (illegalTeacherDispatch.status !== 403) throw new Error('教师平级转派未被 403 拦截');

  console.log('2.3 院系管理员 deptadmin (2) 执行工单转派至 teacher_warn_b (3002)...');
  const dispatchRes = await request(`/warn/tickets/${ticketId2}/dispatch`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${deptAdminToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      assigneeId: 3002,
      assigneeRole: 'TEACHER',
      remark: '原指导教师外出学术会议，转由王教师跟进处理学生安全自查。'
    })
  });
  console.log('    - 院系指派响应:', dispatchRes.data);
  if (dispatchRes.data.code !== 200) throw new Error('院系指派工单失败');

  console.log('2.4 核验工单状态更新与新责任人接手...');
  const detailRes2 = await request(`/warn/tickets/${ticketId2}`, {
    headers: { Authorization: `Bearer ${teacherBToken}` }
  });
  console.log(`    - 转派后状态: ${detailRes2.data.data.status} (预期: DISPATCHED)`);
  console.log(`    - 新责任人 ID: ${detailRes2.data.data.currentAssigneeId} (预期: 3002)`);
  if (detailRes2.data.data.status !== 'DISPATCHED' || detailRes2.data.data.currentAssigneeId !== 3002) {
    throw new Error('工单转派后状态或新责任人 ID 不符');
  }
  const dispatchHist = detailRes2.data.data.processHistory.find(h => h.action === 'DISPATCH');
  if (!dispatchHist) throw new Error('未记录 DISPATCH 流转历史');
  console.log(`    - 流转历史记录: [${dispatchHist.action}] ${dispatchHist.contentRemark}`);

  console.log('2.5 新责任教师 teacher_warn_b 处置并关闭工单...');
  const handleRes2 = await request(`/warn/tickets/${ticketId2}/handle`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${teacherBToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      action: 'CLOSED',
      handlingMeasures: '新指导教师已完成对该学生的警示谈话并签署告知书。'
    })
  });
  if (handleRes2.data.code !== 200) throw new Error('新责任教师关闭工单失败');
  console.log('    - 新责任人成功处理并关闭工单');
  console.log('>>> [PASS] 分支 2: 工单指派/转派端到端验证通过！');

  // =========================================================================
  // 分支 3: 超时升级及 taskId 隔离 (4天历史数据构造 -> 指定 taskId 扫描升级 -> 跨任务隔离)
  // =========================================================================
  console.log('\n================================================================');
  console.log('>>> 分支 3: 超时升级及 taskId 隔离真实端到端验收');
  console.log('================================================================');

  console.log('3.1 在隔离库构造超时测试数据 (4天前创建，超时阈值3天)...');
  execSql(`
    DELETE FROM warn_ticket WHERE ticket_no IN ('WT_ISO_3101_TIMEOUT', 'WT_ISO_3102_ISOLATED');
    INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, warn_level, warn_title, evidence_snapshot_json, status, is_upgraded, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key, created_at, updated_at, is_deleted)
    VALUES ('WT_ISO_3101_TIMEOUT', 3101, 3005, 3001, 1, 1, 1, 'YELLOW', '触发预警: 处置超时测试', '{}', 'TRIGGERED', 0, 3001, 'TEACHER', 'DEDUP_TIMEOUT_3101_3005', 'DEDUP_TIMEOUT_3101_3005', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0);
    INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, warn_level, warn_title, evidence_snapshot_json, status, is_upgraded, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key, created_at, updated_at, is_deleted)
    VALUES ('WT_ISO_3102_ISOLATED', 3102, 3006, 3001, 1, 1, 1, 'YELLOW', '触发预警: 对照任务隔离测试', '{}', 'TRIGGERED', 0, 3001, 'TEACHER', 'DEDUP_ISOLATED_3102_3006', 'DEDUP_ISOLATED_3102_3006', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0);
  `);

  const ticket3101Id = parseInt(execSql("SELECT id FROM warn_ticket WHERE ticket_no = 'WT_ISO_3101_TIMEOUT';"));
  const ticket3102Id = parseInt(execSql("SELECT id FROM warn_ticket WHERE ticket_no = 'WT_ISO_3102_ISOLATED';"));
  console.log(`    - 任务 3101 目标工单 ID: ${ticket3101Id}, 状态: TRIGGERED, is_upgraded: 0`);
  console.log(`    - 任务 3102 对照工单 ID: ${ticket3102Id}, 状态: TRIGGERED, is_upgraded: 0`);

  // 等待 10s 防刷冷却
  console.log('3.2 等待 10 秒防刷流控冷却...');
  await new Promise(r => setTimeout(r, 11000));

  console.log('3.3 院系管理员执行定向扫描 (指定 taskId=3101)...');
  const scanRes3 = await request('/warn/scan?taskId=3101', {
    method: 'POST',
    headers: { Authorization: `Bearer ${deptAdminToken}` }
  });
  console.log('    - 定向扫描响应:', scanRes3.data);
  if (scanRes3.data.code !== 200) throw new Error('扫描请求失败');
  console.log(`    - 扫描结果: upgradedCount = ${scanRes3.data.data.upgradedCount}`);

  console.log('3.4 核验任务 3101 工单成功升级至院系...');
  const detailRes3 = await request(`/warn/tickets/${ticket3101Id}`, {
    headers: { Authorization: `Bearer ${deptAdminToken}` }
  });
  console.log(`    - 任务 3101 工单 isUpgraded: ${detailRes3.data.data.isUpgraded} (预期: 1)`);
  console.log(`    - 任务 3101 当前责任角色: ${detailRes3.data.data.currentAssigneeRole} (预期: DEPT_ADMIN)`);
  console.log(`    - 任务 3101 升级原因: ${detailRes3.data.data.upgradeReason}`);
  if (detailRes3.data.data.isUpgraded !== 1 || detailRes3.data.data.currentAssigneeRole !== 'DEPT_ADMIN') {
    throw new Error('任务 3101 工单未完成超时升级');
  }
  const upgradeHist = detailRes3.data.data.processHistory.find(h => h.action === 'TIMEOUT_UPGRADE');
  if (!upgradeHist) throw new Error('未记录 TIMEOUT_UPGRADE 流转记录');
  console.log(`    - 流转记录: [${upgradeHist.action}] ${upgradeHist.contentRemark}`);

  console.log('3.5 核心隔离校验: 核验任务 3102 工单未受波及 (taskId 严格隔离)...');
  const ticket3102State = execSql(`SELECT CONCAT('is_upgraded=', is_upgraded, ',role=', current_assignee_role) FROM warn_ticket WHERE id = ${ticket3102Id};`);
  console.log(`    - 任务 3102 工单当前状态: ${ticket3102State} (预期: is_upgraded=0,role=TEACHER)`);
  if (!ticket3102State.includes('is_upgraded=0') || !ticket3102State.includes('role=TEACHER')) {
    throw new Error(`taskId 隔离失败！任务 3102 的工单被意外升级: ${ticket3102State}`);
  }
  console.log('    - [隔离验证 PASS] 任务 3102 保持未升级，证明扫描与超时升级严格限制在指定 taskId 内！');

  console.log('3.6 升级后权限校验: 指导教师尝试关闭已升级工单 (预期 403 拦截)...');
  const teacherCloseUpgraded = await request(`/warn/tickets/${ticket3101Id}/handle`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${teacherAToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ action: 'CLOSED', handlingMeasures: '教师试图关闭已升级工单' })
  });
  console.log(`    - 教师关闭已升级工单响应状态码: ${teacherCloseUpgraded.status} (预期 403)`);
  console.log(`    - 错误提示: ${teacherCloseUpgraded.data.message}`);
  if (teacherCloseUpgraded.status !== 403) throw new Error('教师关闭已升级工单未被 403 拦截');

  console.log('3.7 院系管理员处置并完成闭环销号...');
  const deptAdminClose = await request(`/warn/tickets/${ticket3101Id}/handle`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${deptAdminToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      action: 'CLOSED',
      handlingMeasures: '院系已召开督导联席会核实，责成责任单位补齐实习协议与自查报告，予以闭环销号。'
    })
  });
  if (deptAdminClose.data.code !== 200) throw new Error('院系管理员关闭工单失败');
  console.log('    - 院系负责人成功关闭已升级工单');
  console.log('>>> [PASS] 分支 3: 超时升级与 taskId 隔离端到端验证通过！');

  console.log('\n================================================================');
  console.log('>>> 全量 3 条预警分支真实端到端 (E2E) 验收全部顺利通过 (PASS)！');
  console.log('================================================================');
}

run().catch(err => {
  console.error('\n[FATAL ERROR]:', err);
  process.exit(1);
});
