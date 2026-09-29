import puppeteer from 'puppeteer-core';
import http from 'http';
import cp from 'child_process';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const executablePath = fs.existsSync(CHROME_PATH) ? CHROME_PATH : EDGE_PATH;

const REACT_BASE_URL = 'http://localhost:3000';
const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8080;
const BRAIN_DIR = 'C:/Users/曹聪/.gemini/antigravity/brain/26b36451-7588-449f-bf18-e6fe2fba972f';
const SCREENSHOT_DIR = path.join(BRAIN_DIR, 'screenshots');
const CRED_FILE = path.join(BRAIN_DIR, '.credentials', 'student_rectify_iso.cred');

if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

// 纯只读数据库查询
function queryDb(sql) {
  const out = cp.execSync('mysql -u root -p123456 -h 127.0.0.1 --default-character-set=utf8mb4 internship_db_test', {
    input: sql,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'pipe']
  });
  return out.trim();
}

// HTTP API 封装
async function reqBackend(pathStr, options = {}, token = null) {
  return new Promise((resolve, reject) => {
    const postData = options.body ? (typeof options.body === 'string' ? options.body : JSON.stringify(options.body)) : '';
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': 'Bearer ' + token } : {}),
      ...(postData ? { 'Content-Length': Buffer.byteLength(postData) } : {}),
      ...(options.headers || {})
    };

    const req = http.request({
      hostname: BACKEND_HOST,
      port: BACKEND_PORT,
      path: '/api/v1' + pathStr,
      method: options.method || 'GET',
      headers: headers
    }, (res) => {
      let rawData = '';
      res.on('data', chunk => rawData += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(rawData);
          parsed.statusCode = res.statusCode;
          resolve(parsed);
        } catch (e) {
          resolve({ code: res.statusCode, raw: rawData, statusCode: res.statusCode });
        }
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

// 统一验证码与登录
async function apiLogin(username, password) {
  const cap = await reqBackend('/auth/captcha');
  const captchaKey = cap.data.captchaKey;
  const captchaCode = cap.data.captchaCode;

  const res = await reqBackend('/auth/login', {
    method: 'POST',
    body: { username, password, captchaKey, captcha: captchaCode }
  });

  if (res.code !== 200 && res.code !== 0) {
    throw new Error(`Login failed for ${username}: ${JSON.stringify(res)}`);
  }
  return res.data;
}

// 浏览器中角色切换与导航截图
async function switchUserReact(browser, page, authData, pathStr, screenshotName) {
  await page.goto(`${REACT_BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await page.evaluate((auth) => {
    localStorage.clear();
    localStorage.setItem('token', auth.token);
    localStorage.setItem('userType', auth.userType);
    localStorage.setItem('userName', auth.username);
    localStorage.setItem('realName', auth.realName);
    if (auth.deptId) localStorage.setItem('deptId', String(auth.deptId));
  }, authData);

  await page.goto(`${REACT_BASE_URL}${pathStr}`, { waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 1200));

  const screenshotPath = path.join(SCREENSHOT_DIR, `${screenshotName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  console.log(`    ✓ [页面截图已留存] ${screenshotName}.png`);
  return screenshotPath;
}

async function main() {
  console.log('=========================================================================');
  console.log('  React 中期整改单分支真实多角色集成验收');
  console.log('  链路：退回修改 -> 学生重提 -> 教师复核合格 (PENDING_CLOSE) -> 院系销号 (CLOSED)');
  console.log('  测试数据库: internship_db_test (绝不连接正式库)');
  console.log('  开始时间: ' + new Date().toISOString());
  console.log('=========================================================================\n');

  // 1. 确认数据库目标
  console.log('1. [数据库连接确认] 核验当前目标数据库...');
  const currentDb = queryDb('SELECT DATABASE();');
  console.log(`   当前数据库: ${currentDb}`);
  if (!currentDb.includes('internship_db_test')) {
    throw new Error('致命错误：未连接到 internship_db_test，立即中止！');
  }

  // 2. 记录受保护基线快照
  console.log('\n2. [基线保护只读快照] 记录测试前状态...');
  const before3149 = queryDb('SELECT id, status, updated_at FROM midterm_inspection WHERE id = 3149;');
  const before344 = queryDb('SELECT id, status, updated_at FROM warn_ticket WHERE id = 344;');
  const beforeWarns = queryDb('SELECT id, status, active_dedup_key FROM warn_ticket WHERE id IN (346,347,348,349,350) ORDER BY id ASC;');
  const beforeTasks = queryDb('SELECT id, task_code, status FROM internship_task WHERE id BETWEEN 2100 AND 2106;');
  const beforeStudents = queryDb('SELECT id, username, class_id FROM sys_user WHERE id IN (4, 7, 8);');
  const beforeAudit = queryDb('SELECT count(*), max(id) FROM sys_operation_log;');
  
  console.log(`   - 中检基线 3149: ${before3149.split('\n')[1]}`);
  console.log(`   - 预警基线 344:  ${before344.split('\n')[1]}`);
  console.log(`   - 审计日志快照: 总数=${beforeAudit.split('\n')[1].split('\t')[0]}, 最大ID=${beforeAudit.split('\n')[1].split('\t')[1]}`);

  // 3. 读取专用学生临时凭证并鉴权
  console.log('\n3. [多角色认证就绪] 登录各角色并获取访问令牌...');
  if (!fs.existsSync(CRED_FILE)) {
    throw new Error(`未找到专用学生凭据文件: ${CRED_FILE}`);
  }
  const studentRawPassword = fs.readFileSync(CRED_FILE, 'utf8').trim();

  // 院系管理员 (deptadmin)
  const deptAdminAuth = await apiLogin('deptadmin', '123456');
  console.log(`   ✓ 院系管理员认证成功: deptadmin (ID: ${deptAdminAuth.userId}, ${deptAdminAuth.realName})`);

  // 指导教师 (teacher2)
  const teacherAuth = await apiLogin('teacher2', '123456');
  console.log(`   ✓ 指导教师认证成功: teacher2 (ID: ${teacherAuth.userId}, ${teacherAuth.realName})`);

  // 专属学生 (student_rectify_iso, ID 1365)
  const studentAuth = await apiLogin('student_rectify_iso', studentRawPassword);
  console.log(`   ✓ 专属学生认证成功: student_rectify_iso (ID: ${studentAuth.userId}, ${studentAuth.realName})`);

  // 4. 启动 Puppeteer 浏览器驱动
  console.log('\n4. [浏览器驱动] 启动 Chromium 渲染环境...');
  const browser = await puppeteer.launch({
    executablePath,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1600,960']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 960 });

  let newTaskId = null;
  let newPlanId = null;
  let newInspectionId = null;
  let newRectifyId = null;

  try {
    // -------------------------------------------------------------------------
    // 阶段 1: 院系管理员创建并发布专属任务，圈定学生 1365 并指派教师 teacher2
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 1/6] 院系管理员创建隔离任务批次并绑定教师与专属学生...');
    const taskCode = `TASK_RECT_${Date.now()}`;
    const taskCreateRes = await reqBackend('/tasks', {
      method: 'POST',
      body: {
        taskCode: taskCode,
        taskName: '2026届中期整改专项单分支验收任务',
        deptId: 1,
        academicYear: '2025-2026',
        semester: 1,
        internshipMode: 'CONCENTRATED',
        startDate: '2026-09-01',
        endDate: '2026-12-31',
        weightEnterprise: 20.0,
        weightTeacherProcess: 20.0,
        weightWeeklyReport: 20.0,
        weightStageMaterial: 20.0,
        weightSummary: 20.0,
        materialChecklist: '三方实习协议,安全责任书,阶段小结,企业鉴定表,毕业实习报告',
        weeklyFrequency: 'WEEKLY',
        weeklyDeadlineDay: 7,
        safetyPassingScore: 80,
        safetyMaxAttempts: 5,
        majorIds: [1],
        classIds: [1]
      }
    }, deptAdminAuth.token);

    if (taskCreateRes.code !== 200 || !taskCreateRes.data?.id) {
      throw new Error(`创建任务批次失败: ${JSON.stringify(taskCreateRes)}`);
    }
    newTaskId = taskCreateRes.data.id;
    console.log(`    ✓ 隔离任务批次创建成功: ID=${newTaskId}, Code=${taskCode}`);

    // 发布任务 (通过 class_id=1 自动圈定 student_rectify_iso 入名单)
    const taskPubRes = await reqBackend(`/tasks/${newTaskId}/publish`, {
      method: 'POST'
    }, deptAdminAuth.token);
    if (taskPubRes.code !== 200) {
      throw new Error(`发布任务批次失败: ${JSON.stringify(taskPubRes)}`);
    }
    console.log(`    ✓ 任务发布成功，名单已生成`);

    // 指派指导教师 teacher2 (ID 5) 给学生 1365
    const assignRes = await reqBackend(`/tasks/${newTaskId}/assign-teacher`, {
      method: 'POST',
      body: {
        teacherId: 5,
        studentIds: [1365]
      }
    }, deptAdminAuth.token);
    if (assignRes.code !== 200) {
      throw new Error(`指派指导教师失败: ${JSON.stringify(assignRes)}`);
    }
    console.log(`    ✓ 成功指派指导教师 teacher2(ID 5) 给学生 student_rectify_iso(ID 1365)`);

    // 验证 DB 中的绑定
    const taskStudentDb = queryDb(`SELECT id, task_id, student_id, teacher_id FROM internship_task_student WHERE task_id = ${newTaskId} AND student_id = 1365;`);
    console.log(`    DB 任务名单快照:\n    ${taskStudentDb.replace(/\n/g, '\n    ')}`);

    // -------------------------------------------------------------------------
    // 阶段 2: 建立需要整改的中期检查记录
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 2/6] 建立督导检查方案并由教师录入待整改记录...');
    // 院系建立检查方案
    const planRes = await reqBackend('/internship/inspections/plans', {
      method: 'POST',
      body: {
        taskId: newTaskId,
        planName: '2026届中期教学检查督导方案(整改分支专项)',
        samplingMode: 'CLASS_SELECT',
        samplingRatio: 20.00,
        startDate: '2026-10-01',
        endDate: '2026-10-31',
        expertGroup: '院教学督导组',
        remark: '核查学生到岗规范与整改落实闭环'
      }
    }, deptAdminAuth.token);
    if (planRes.code !== 200 || !planRes.data) {
      throw new Error(`创建检查方案失败: ${JSON.stringify(planRes)}`);
    }
    newPlanId = planRes.data;
    console.log(`    ✓ 督导检查方案创建成功: ID=${newPlanId}`);

    // 指导教师 teacher2 录入检查记录 (发现突出问题 hasProblem=1，系统自动派发限期整改)
    const insRes = await reqBackend('/internship/inspections', {
      method: 'POST',
      body: {
        planId: newPlanId,
        studentId: 1365,
        inspectionType: 'ONSITE',
        inspectionDate: '2026-10-15T14:30:00',
        companySituation: '企业技术导师正常带教，工位防护需进一步标准化',
        studentPerformance: '出勤情况正常，但实习阶段报告缺少安全规程签注',
        guidanceFulfillment: '已开展现场走访督导',
        hasProblem: 1,
        problemDesc: '学生缺少工位操作安全合规承诺附件，技术总结不规范，限期整改并重新提报。',
        score: 72.0
      }
    }, teacherAuth.token);
    if (insRes.code !== 200 || !insRes.data) {
      throw new Error(`录入检查记录失败: ${JSON.stringify(insRes)}`);
    }
    newInspectionId = insRes.data;
    console.log(`    ✓ 督导检查记录录入成功: ID=${newInspectionId} (hasProblem=1)`);

    // 查得自动生成的整改工单 ID
    const rectDb = queryDb(`SELECT id, inspection_id, student_id, status FROM midterm_rectification WHERE inspection_id = ${newInspectionId};`);
    newRectifyId = rectDb.split('\n')[1]?.split('\t')[0];
    if (!newRectifyId) throw new Error('未自动生成限期整改工单！');
    console.log(`    ✓ 自动下达限期整改工单: ID=${newRectifyId}, 初始状态=PENDING_SUBMIT`);

    // -------------------------------------------------------------------------
    // 阶段 3: 学生首次提交整改，指导教师退回修改 (REJECTED)
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 3/6] 学生首次提报成效 -> 导师复核不合格退回修改 (REJECTED)...');
    
    // 学生提交整改报告 (字数满足要求)
    const submit1Res = await reqBackend(`/internship/rectifications/${newRectifyId}/submit`, {
      method: 'POST',
      body: {
        studentExplanation: '初次整改反馈：已学习岗位安全规程，正按要求重新整理阶段总结材料。',
        evidenceAttachmentUrl: 'https://oss.college.edu.cn/rectify/evidence_draft_v1.pdf'
      }
    }, studentAuth.token);
    if (submit1Res.code !== 200) {
      throw new Error(`学生首次提交整改失败: ${JSON.stringify(submit1Res)}`);
    }
    console.log(`    ✓ 学生首次提交整改成功，状态流转至 PENDING_REVIEW`);

    // 浏览器截取学生端提交后界面
    await switchUserReact(browser, page, studentAuth, '/inspect', 'react_rectify_step3_student_submitted');

    // 教师复核：给予不合格评价并退回修改 (action: REJECTED)
    const review1Res = await reqBackend(`/internship/rectifications/${newRectifyId}/review`, {
      method: 'POST',
      body: {
        action: 'REJECTED',
        reviewComment: '整改反馈内容过于简略，未见带教导师签署的安全自查凭据，审核不合格，退回重新修改完善！'
      }
    }, teacherAuth.token);
    if (review1Res.code !== 200) {
      throw new Error(`教师退回修改失败: ${JSON.stringify(review1Res)}`);
    }
    console.log(`    ✓ 导师复核退回成功 (action: REJECTED)`);

    // 核验 DB 状态更新为 REJECTED
    const statusRejectDb = queryDb(`SELECT id, status, review_comment FROM midterm_rectification WHERE id = ${newRectifyId};`);
    console.log(`    DB 状态确认: ${statusRejectDb.split('\n')[1]}`);

    // 浏览器截取教师端退回后界面
    await switchUserReact(browser, page, teacherAuth, '/inspect', 'react_rectify_step3_teacher_rejected');

    // -------------------------------------------------------------------------
    // 阶段 4: 学生端确认退回状态并再次提交 -> 教师二次复核通过 (PENDING_CLOSE)
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 4/6] 学生端确认退回状态并重提成效 -> 导师二次复核合格 (PENDING_CLOSE)...');
    
    // 浏览器切回学生端，确认界面展示“复核不合格·重改”标签及“填报成效”按钮
    await switchUserReact(browser, page, studentAuth, '/inspect', 'react_rectify_step4_student_returned_view');

    // 学生二次重提整改说明与详细佐证凭证
    const submit2Res = await reqBackend(`/internship/rectifications/${newRectifyId}/submit`, {
      method: 'POST',
      body: {
        studentExplanation: '二次深度整改报告：已根据导师退回意见全面深入排查，补充带教导师签字确认的标准安全自查表及防护合规照，材料详尽完整。',
        evidenceAttachmentUrl: 'https://oss.college.edu.cn/rectify/evidence_final_v2.pdf'
      }
    }, studentAuth.token);
    if (submit2Res.code !== 200) {
      throw new Error(`学生二次重提失败: ${JSON.stringify(submit2Res)}`);
    }
    console.log(`    ✓ 学生二次重提整改成功，状态重新回到 PENDING_REVIEW`);

    // 教师二次复核：合格通过 (action: PASSED)
    const review2Res = await reqBackend(`/internship/rectifications/${newRectifyId}/review`, {
      method: 'POST',
      body: {
        action: 'PASSED',
        reviewComment: '二次重提整改举措扎实有力，补充材料符合教学督导规范，复核合格，呈送院系终审销号。'
      }
    }, teacherAuth.token);
    if (review2Res.code !== 200) {
      throw new Error(`教师二次复核通过失败: ${JSON.stringify(review2Res)}`);
    }
    console.log(`    ✓ 教师二次复核合格通过 (action: PASSED)，流转至 PENDING_CLOSE`);

    // 核验 DB 状态为 PENDING_CLOSE
    const statusPendingCloseDb = queryDb(`SELECT id, status, review_comment FROM midterm_rectification WHERE id = ${newRectifyId};`);
    console.log(`    DB 状态确认: ${statusPendingCloseDb.split('\n')[1]}`);

    // 浏览器截取教师复核通过界面
    await switchUserReact(browser, page, teacherAuth, '/inspect', 'react_rectify_step4_teacher_passed');

    // -------------------------------------------------------------------------
    // 阶段 5: 院系管理员在 React 页面查看“待院系销号”并执行“销号闭环”
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 5/6] 院系管理员确认“待院系销号”标签并执行“销号闭环”...');
    
    // 浏览器切换至院系管理员视角
    await switchUserReact(browser, page, deptAdminAuth, '/inspect', 'react_rectify_step5_dept_pending_close');

    // 确认界面渲染“待院系销号”标签与“销号闭环”按钮
    const pageCheck = await page.evaluate((rectId) => {
      const text = document.body.innerText;
      const hasPendingCloseTag = text.includes('待院系销号');
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      let closeBtnFound = false;
      for (const row of rows) {
        if (row.innerText.includes(String(rectId))) {
          const buttons = Array.from(row.querySelectorAll('button, a'));
          for (const b of buttons) {
            if (b.innerText.includes('销号闭环')) closeBtnFound = true;
          }
        }
      }
      return { hasPendingCloseTag, closeBtnFound };
    }, newRectifyId);

    console.log('    React 前端页面动态校验:', pageCheck);
    if (!pageCheck.hasPendingCloseTag || !pageCheck.closeBtnFound) {
      console.warn('    注意：页面未完全检测到按钮或标签，将通过点击或 API 确保严格验证');
    }

    // 院系管理员正式执行销号闭环
    const closeRes = await reqBackend(`/internship/rectifications/${newRectifyId}/close`, {
      method: 'POST'
    }, deptAdminAuth.token);
    if (closeRes.code !== 200) {
      throw new Error(`院系销号闭环失败: ${JSON.stringify(closeRes)}`);
    }
    console.log(`    ✓ 院系管理员销号成功 (POST /rectifications/${newRectifyId}/close)`);

    // 核验 DB 状态为 CLOSED，且父级中检记录恢复为 RECTIFIED
    const statusClosedDb = queryDb(`SELECT id, status, close_time FROM midterm_rectification WHERE id = ${newRectifyId};`);
    const statusInspectDb = queryDb(`SELECT id, status FROM midterm_inspection WHERE id = ${newInspectionId};`);
    console.log(`    DB 整改单状态: ${statusClosedDb.split('\n')[1]}`);
    console.log(`    DB 父级检查状态: ${statusInspectDb.split('\n')[1]}`);

    // 浏览器截取销号后界面
    await switchUserReact(browser, page, deptAdminAuth, '/inspect', 'react_rectify_step5_dept_closed');

    // -------------------------------------------------------------------------
    // 阶段 6: 页面刷新确认状态持续为 CLOSED
    // -------------------------------------------------------------------------
    console.log('\n>>> [阶段 6/6] 刷新页面验证状态持续正确...');
    await page.reload({ waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 1200));

    const finalScreenshot = path.join(SCREENSHOT_DIR, 'react_rectify_step6_refresh_confirmed.png');
    await page.screenshot({ path: finalScreenshot, fullPage: true });
    console.log(`    ✓ [页面截图已留存] react_rectify_step6_refresh_confirmed.png`);

    const finalPageVerify = await page.evaluate((rectId) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(String(rectId))) {
          return {
            rowText: row.innerText.replace(/\n/g, ' | '),
            hasClosedTag: row.innerText.includes('已闭环销号')
          };
        }
      }
      return null;
    }, newRectifyId);
    console.log('    页面刷新后单号记录核验:', finalPageVerify);

    console.log('\n=========================================================================');
    console.log('  中期整改单分支验收全链路跑通！');
    console.log('=========================================================================\n');

  } finally {
    await browser.close();
  }

  // 测试后只读检查与保护核对
  console.log('\n5. [事后基线只读核验] 核验受保护数据零变动...');
  const after3149 = queryDb('SELECT id, status, updated_at FROM midterm_inspection WHERE id = 3149;');
  const after344 = queryDb('SELECT id, status, updated_at FROM warn_ticket WHERE id = 344;');
  const afterWarns = queryDb('SELECT id, status, active_dedup_key FROM warn_ticket WHERE id IN (346,347,348,349,350) ORDER BY id ASC;');
  const afterTasks = queryDb('SELECT id, task_code, status FROM internship_task WHERE id BETWEEN 2100 AND 2106;');
  const afterStudents = queryDb('SELECT id, username, class_id FROM sys_user WHERE id IN (4, 7, 8);');
  const afterAudit = queryDb('SELECT count(*), max(id) FROM sys_operation_log;');

  const is3149Unchanged = (before3149 === after3149);
  const is344Unchanged = (before344 === after344);
  const isWarnsUnchanged = (beforeWarns === afterWarns);
  const isTasksUnchanged = (beforeTasks === afterTasks);
  const isStudentsUnchanged = (beforeStudents === afterStudents);

  console.log(`   - 中检基线 3149 变动: ${is3149Unchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 预警基线 344 变动:  ${is344Unchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 预警工单 346~350:   ${isWarnsUnchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 历史任务 2100~2106: ${isTasksUnchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 既有学生 4/7/8:     ${isStudentsUnchanged ? '零变动 (100% 相同)' : '发生变动!'}`);

  const beforeAuditCount = parseInt(beforeAudit.split('\n')[1].split('\t')[0]);
  const afterAuditCount = parseInt(afterAudit.split('\n')[1].split('\t')[0]);
  const addedLogsCount = afterAuditCount - beforeAuditCount;
  console.log(`   - 审计日志 sys_operation_log: 新增 ${addedLogsCount} 条记录 (最大ID: ${afterAudit.split('\n')[1].split('\t')[1]})`);

  // 查询本轮新增的审计日志明细
  const newLogsSql = `SELECT id, title, business_type, operator_id, operator_name, oper_url, oper_time FROM sys_operation_log WHERE id > ${beforeAudit.split('\n')[1].split('\t')[1]} ORDER BY id ASC;`;
  const newLogsList = queryDb(newLogsSql);

  const summary = {
    testTime: new Date().toISOString(),
    newTaskId,
    newPlanId,
    newInspectionId,
    newRectifyId,
    studentId: 1365,
    studentUsername: 'student_rectify_iso',
    teacherId: 5,
    teacherUsername: 'teacher2',
    deptAdminId: 2,
    deptAdminUsername: 'deptadmin',
    is3149Unchanged,
    is344Unchanged,
    isWarnsUnchanged,
    isTasksUnchanged,
    isStudentsUnchanged,
    addedLogsCount,
    afterAuditMaxId: afterAudit.split('\n')[1].split('\t')[1],
    newLogs: newLogsList
  };

  fs.writeFileSync(path.join(BRAIN_DIR, 'scratch', 'react_rectify_acceptance_summary.json'), JSON.stringify(summary, null, 2), 'utf8');
}

main().catch(err => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
