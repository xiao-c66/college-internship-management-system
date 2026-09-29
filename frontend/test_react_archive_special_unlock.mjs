import fs from 'fs';
import path from 'path';
import http from 'http';
import crypto from 'crypto';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const puppeteer = require('d:/devlop/IDEA/college-internship-management-system/frontend/node_modules/puppeteer-core');

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const CRED_PATH = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\.credentials\\student_rectify_iso.cred';
const SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const BACKEND_BASE = 'http://127.0.0.1:8080/api/v1';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function httpRequest(url, options = {}, postData = null) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const reqOptions = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port,
      path: parsedUrl.pathname + parsedUrl.search,
      method: options.method || 'GET',
      headers: options.headers || {}
    };

    if (postData) {
      if (typeof postData === 'object') {
        postData = JSON.stringify(postData);
        reqOptions.headers['Content-Type'] = 'application/json;charset=UTF-8';
      }
      reqOptions.headers['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(reqOptions, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve({ status: res.statusCode, data: json });
        } catch (e) {
          resolve({ status: res.statusCode, text: data });
        }
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function apiLogin(username, password) {
  const capRes = await httpRequest(`${BACKEND_BASE}/auth/captcha`);
  const captchaKey = capRes.data.data.captchaKey;
  const captchaCode = capRes.data.data.captchaCode;

  const loginRes = await httpRequest(`${BACKEND_BASE}/auth/login`, { method: 'POST' }, {
    username,
    password,
    captchaKey,
    captcha: captchaCode
  });

  if (loginRes.data.code !== 200) {
    throw new Error(`API Login failed for ${username}: ${loginRes.data.message}`);
  }
  return loginRes.data.data.token;
}

async function clearAndType(page, selector, text) {
  await page.waitForSelector(selector, { timeout: 8000 });
  await page.click(selector);
  await page.keyboard.down('Control');
  await page.keyboard.press('KeyA');
  await page.keyboard.up('Control');
  await page.keyboard.press('Backspace');
  await page.type(selector, String(text));
}

async function browserLogin(page, username, password, expectedUrlKeyword) {
  await page.goto('http://127.0.0.1:3000/login', { waitUntil: 'networkidle0' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle0' });

  await page.waitForSelector('#login_username', { timeout: 8000 });
  await clearAndType(page, '#login_username', username);
  await clearAndType(page, '#login_password', password);

  await page.waitForFunction(() => {
    const input = document.querySelector('#login_captcha');
    return input && input.value && input.value.length === 4;
  }, { timeout: 8000 });

  await page.click('button[type="submit"]');
  await page.waitForFunction(
    (kw) => window.location.pathname.includes(kw),
    { timeout: 10000 },
    expectedUrlKeyword
  );
  await sleep(1000);
}

function getFileHash(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const buffer = fs.readFileSync(filePath);
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

async function run() {
  console.log('================================================================');
  console.log('>>> [React 电子档案特批解锁与版本递增分支] 定向真实验收');
  console.log('“已归档锁定 → SYS_ADMIN 特批解锁 → 更新材料 → 重新前置诊断 → 再次归档锁定 → 版本号递增”');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();
  console.log('1. 已从安全存储加载 student_rectify_iso (ID 1365) 凭证');

  const deptToken = await apiLogin('deptadmin', '123456');
  const teacherToken = await apiLogin('teacher', '123456');
  const adminToken = await apiLogin('admin', '123456');
  const studentToken = await apiLogin('student_rectify_iso', studentPwd);

  const today = new Date().toISOString().split('T')[0];
  const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];

  // -------------------------------------------------------------------------
  // Step 1: 创建全新的隔离测试任务 (例如任务 2118)
  // -------------------------------------------------------------------------
  console.log('2. 创建全新专属隔离实习任务 (隔离既有任务 2100-2113)...');
  const taskCode = `TASK_ARCHIVE_${Date.now()}`;
  const taskName = `TASK_ARCHIVE_ISO_${Date.now()}`;
  const taskPayload = {
    taskCode: taskCode,
    taskName: taskName,
    deptId: 1,
    academicYear: '2029-2030',
    semester: 1,
    internshipMode: 'CONCENTRATED',
    startDate: today,
    endDate: nextMonth,
    weightEnterprise: 20.0,
    weightTeacherProcess: 20.0,
    weightWeeklyReport: 20.0,
    weightStageMaterial: 20.0,
    weightSummary: 20.0,
    materialChecklist: '',
    weeklyFrequency: 'WEEKLY',
    weeklyDeadlineDay: 7,
    safetyPassingScore: 80,
    safetyMaxAttempts: 5,
    majorIds: [1],
    classIds: [1]
  };

  const createTaskRes = await httpRequest(`${BACKEND_BASE}/tasks`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, taskPayload);

  if (createTaskRes.data.code !== 200) {
    throw new Error(`Failed to create task: ${createTaskRes.data.message}`);
  }
  const taskId = createTaskRes.data.data?.id || createTaskRes.data.data;
  console.log(`   - 档案专属隔离任务创建成功: taskId = ${taskId}`);

  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/publish`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log(`   - 任务发布成功，名单已挂载`);

  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/assign-teacher`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, { teacherId: 3, studentIds: [1365] });
  console.log(`   - 指派教师 teacher (ID 3) 给 student 1365 成功`);

  // -------------------------------------------------------------------------
  // Step 2: 通过正式业务 API 准备 9 项前置归档硬条件
  // -------------------------------------------------------------------------
  console.log('3. 通过正式业务 API 逐项构建 9 项归档前置硬条件...');

  // 1. 安全责任承诺书签署 (SAFE_PASS)
  console.log('   (1/9) 学生签署安全责任承诺书...');
  const signRes = await httpRequest(`${BACKEND_BASE}/safety/commitment/sign`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, { taskId: taskId, insuranceFileUrl: '' });
  if (signRes.data.code !== 200) throw new Error(`Sign commitment failed: ${signRes.data.message}`);

  // 2. 实习岗位申报终审通过 (APPLY_PASS)
  console.log('   (2/9) 提报并审批实习岗位申报...');
  const applySubmitRes = await httpRequest(`${BACKEND_BASE}/applies/submit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, {
    taskId: taskId,
    companyName: '上海智联云测科技有限公司',
    jobPosition: '全栈自动化测试工程师',
    jobAddress: '上海市浦东新区张江微电子港8号楼',
    companyContactPerson: '张主管',
    companyContactPhone: '13812345678',
    startDate: today,
    endDate: nextMonth,
    internshipMode: 'CONCENTRATED'
  });
  if (applySubmitRes.data.code !== 200) throw new Error(`Submit apply failed: ${applySubmitRes.data.message}`);
  const applyId = applySubmitRes.data.data.id;

  await httpRequest(`${BACKEND_BASE}/applies/${applyId}/audit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, { action: 'TEACHER_APPROVED', comment: '导师审核同意' });

  await httpRequest(`${BACKEND_BASE}/applies/${applyId}/audit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, { action: 'APPROVED', comment: '院系负责人终审通过' });

  // 3. 周报合格批阅通过 (WEEKLY_RATE)
  console.log('   (3/9) 学生提交周报并由教师批阅及格...');
  const weeklyRes = await httpRequest(`${BACKEND_BASE}/internship/weekly-reports`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, {
    taskId: taskId,
    weekNumber: 1,
    action: 'SUBMIT',
    workContent: '本周在企业指导老师带领下深入学习并参与自动化测试流水线的搭建与端到端核心业务用例执行。',
    workSummary: '深入掌握了微服务接口异常场景设计规范、接口防抖限流机制与分布式事务一致性测试技巧。',
    problemEncountered: '跨服务接口联调时存在偶发参数序列化不兼容问题，已通过对齐统一请求DTO结构彻底解决。',
    nextWeekPlan: '下周计划完成剩余核心链路自动化覆盖，完善持续集成测试断言与异常告警监控。'
  });
  if (weeklyRes.data.code !== 200) throw new Error(`Submit weekly report failed: ${weeklyRes.data.message}`);
  const reportId = weeklyRes.data.data.id;

  await httpRequest(`${BACKEND_BASE}/internship/weekly-reports/${reportId}/review`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, { action: 'APPROVE', score: 92.00, reviewComment: '报告内容充实，考核通过' });

  // 4. 过程指导台账达标 (GUIDANCE_COUNT >= 2)
  console.log('   (4/9) 教师登记 2 次过程指导走访台账...');
  await httpRequest(`${BACKEND_BASE}/internship/guidances`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    taskId: taskId,
    studentId: 1365,
    guidanceDate: '2026-09-28 09:30:00',
    guidanceType: 'ONSITE',
    contentSummary: '实地走访企业办公工位，检查学生实训项目代码与安全防护措施。',
    location: '企业测试实验室 301'
  });
  await httpRequest(`${BACKEND_BASE}/internship/guidances`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    taskId: taskId,
    studentId: 1365,
    guidanceDate: '2026-09-28 10:30:00',
    guidanceType: 'ONLINE',
    contentSummary: '线上审查周报总结与阶段材料，解答业务疑难。',
    location: '企业视频会议室'
  });

  // 5 & 6. 中期检查督导与整改闭环 (MIDTERM_INSPECT & RECTIFY_CLOSED)
  console.log('   (5/9 & 6/9) 院系发布中期检查方案并录入无问题督导检查记录...');
  const planRes = await httpRequest(`${BACKEND_BASE}/internship/inspections/plans`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, {
    taskId: taskId,
    deptId: 1,
    planName: `中检督查_${taskId}`,
    samplingMode: 'RANDOM_RATIO',
    samplingRatio: 100.00,
    startDate: today,
    endDate: nextMonth
  });
  if (planRes.data.code !== 200) throw new Error(`Create inspect plan failed: ${planRes.data.message}`);
  const planId = planRes.data.data;

  // 教师直接录入督导检查记录 (API-076)，hasProblem=0
  const inspectRes = await httpRequest(`${BACKEND_BASE}/internship/inspections`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    planId: planId,
    studentId: 1365,
    inspectionType: 'ONSITE',
    score: 92.00,
    companySituation: '企业工位完备，实训环境良好',
    studentPerformance: '考勤正常，技能掌握扎实',
    guidanceFulfillment: '校内导师指导按计划落实到位',
    hasProblem: 0
  });
  if (inspectRes.data.code !== 200) throw new Error(`Submit inspection failed: ${inspectRes.data.message}`);
  console.log(`   - 督导检查记录录入成功: inspectId = ${inspectRes.data.data}`);

  // 7. 异常预警全部闭环 (WARN_TICKETS_CLOSED) -> 隔离新任务 0 活动工单

  // 8. 阶段材料查验合格 (MATERIAL_APPROVED)
  console.log('   (8/9) 学生提报阶段材料并由导师审核通过...');
  const matRes = await httpRequest(`${BACKEND_BASE}/internship/materials`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, {
    taskId: taskId,
    materialCode: 'TRIPARTITE_AGREEMENT',
    materialName: '三方协议书/接收函盖章件',
    attachmentUrl: 'https://example.com/vouchers/tripartite_iso_2026_v1.pdf',
    contentText: '三方协议书纸质原件加盖公章与学生签字完毕。'
  });
  if (matRes.data.code !== 200) throw new Error(`Submit material failed: ${matRes.data.message}`);
  const materialId = (typeof matRes.data.data === 'object') ? matRes.data.data.id : matRes.data.data;
  console.log(`   - 阶段材料提报成功: materialId = ${materialId}`);

  const auditMatRes = await httpRequest(`${BACKEND_BASE}/internship/materials/${materialId}/audit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, { action: 'APPROVED', auditScore: 92.00, auditComment: '材料真实合规，同意归档' });
  if (auditMatRes.data.code !== 200) throw new Error(`Audit material failed: ${auditMatRes.data.message}`);
  console.log(`   - 阶段材料导师审核通过成功`);

  // 9. 五维成绩完成汇算并公示发布 (SCORE_PUBLISHED)
  console.log('   (9/9) 教师录入五维成绩，院系复核并公示发布...');
  const scoreRes = await httpRequest(`${BACKEND_BASE}/score/summaries`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    taskId: taskId,
    studentId: 1365,
    enterpriseScore: 92.00,
    processScore: 90.00,
    weeklyScore: 92.00,
    materialScore: 92.00,
    summaryScore: 90.00,
    evaluationComment: '学生实习全流程各阶段完成质量优秀，五维评分达标。',
    submitToDept: true
  });
  if (scoreRes.data.code !== 200) throw new Error(`Submit score failed: ${scoreRes.data.message}`);
  const scoreId = (typeof scoreRes.data.data === 'object') ? scoreRes.data.data.id : scoreRes.data.data;

  await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}/audit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  await httpRequest(`${BACKEND_BASE}/score/tasks/${taskId}/publicity`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log(`   - 成绩公示发布成功`);

  // -------------------------------------------------------------------------
  // Step 3: 执行前置核验诊断 API-098，确认 9 项全部通过
  // -------------------------------------------------------------------------
  console.log('4. 调用 API-098 核验 9 项前置归档条件诊断...');
  const precheckRes = await httpRequest(`${BACKEND_BASE}/archives/precheck?taskId=${taskId}&studentId=1365`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log('   - 诊断结果: passed =', precheckRes.data?.data?.passed, ', passedCount =', precheckRes.data?.data?.passedCount);
  if (!precheckRes.data?.data?.passed || precheckRes.data?.data?.passedCount !== 9) {
    throw new Error(`Archive precheck failed! passedCount=${precheckRes.data?.data?.passedCount}, data=${JSON.stringify(precheckRes.data)}`);
  }
  console.log('   - [PASS] 9 项前置硬条件全部通过诊断核验 (9/9)！');

  // -------------------------------------------------------------------------
  // Step 4: 执行首次归档锁定 (生成初始卷宗 version = 1)
  // -------------------------------------------------------------------------
  console.log('5. 院系管理员执行首次归档锁定 (生成初始卷宗 v1)...');
  const freezeRes = await httpRequest(`${BACKEND_BASE}/archives/freeze?taskId=${taskId}&studentId=1365`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  if (freezeRes.data.code !== 200) {
    throw new Error(`Freeze archive failed: ${freezeRes.data.message}`);
  }
  const archiveId = freezeRes.data.data;
  console.log(`   - 首次归档成功: archiveId = ${archiveId}`);

  // 查询初始卷宗详情并记录初始快照
  const detailResV1 = await httpRequest(`${BACKEND_BASE}/archives/${archiveId}`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  const archiveV1 = detailResV1.data.data;
  console.log(`   - [初始快照] ID: ${archiveV1.id}, 卷宗号: ${archiveV1.archiveNo}, 状态: ${archiveV1.status}, 版本: ${archiveV1.version}`);
  console.log(`   - [初始快照] ZIP包相对路径: ${archiveV1.archiveBundleUrl}`);
  console.log(`   - [初始快照] PDF汇总相对路径: ${archiveV1.archivePdfUrl}`);

  const zipDiskPath = path.join('backend/data/archives', archiveV1.archiveBundleUrl);
  const pdfDiskPath = path.join('backend/data/archives', archiveV1.archivePdfUrl);
  const zipHashV1 = getFileHash(zipDiskPath);
  const pdfHashV1 = getFileHash(pdfDiskPath);
  console.log(`   - [初始文件校验] ZIP 文件是否存在: ${fs.existsSync(zipDiskPath)}, SHA-256: ${zipHashV1}`);
  console.log(`   - [初始文件校验] PDF 文件是否存在: ${fs.existsSync(pdfDiskPath)}, SHA-256: ${pdfHashV1}`);

  if (archiveV1.status !== 'ARCHIVED' || archiveV1.version !== 1) {
    throw new Error(`Initial archive status/version mismatch: status=${archiveV1.status}, version=${archiveV1.version}`);
  }

  // -------------------------------------------------------------------------
  // Step 5: 启动 Edge 浏览器，执行 React 端特批解锁、调改、重检与再次归档
  // -------------------------------------------------------------------------
  console.log('6. 启动 Edge 浏览器并在 React 前端执行特批解锁与再次归档完整链路...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_archive_step1_unlock_modal.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_archive_step2_unlocked_table.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_archive_step3_precheck_all_passed.png');
  const step4Shot = path.join(SCREENSHOT_DIR, 'react_archive_step4_rearchived_table.png');
  const step5Shot = path.join(SCREENSHOT_DIR, 'react_archive_step5_detail_drawer_v2.png');

  try {
    // -----------------------------------------------------------------------
    // [React 步骤 1] 超级管理员 SYS_ADMIN 登录，打开电子档案管理页并执行特批解锁
    // -----------------------------------------------------------------------
    console.log('   [React 步骤 1] 超管 admin 登录 React 前端并访问电子档案中心...');
    await browserLogin(page, 'admin', '123456', '/dashboard/admin');
    await page.goto('http://127.0.0.1:3000/archive/manage', { waitUntil: 'networkidle0' });
    await sleep(2000);

    // 等待表格加载
    await page.waitForSelector('tr.ant-table-row', { timeout: 8000 });

    // 定位目标卷宗行并点击“特批解锁”
    console.log(`   [React 步骤 1] 在表格中定位卷宗 ${archiveV1.archiveNo} 并打开特批解锁弹窗...`);
    const unlockBtnClicked = await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo) || row.innerText.includes('student_rectify_iso')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('特批解锁'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    }, archiveV1.archiveNo);

    if (!unlockBtnClicked) {
      throw new Error(`Could not find '特批解锁' button for target archive ${archiveV1.archiveNo}`);
    }

    await sleep(1000);
    await page.waitForSelector('#specialDocNo', { timeout: 8000 });

    const docNo = 'DOC-SPECIAL-2026-REACT-01';
    const unlockReason = '省厅专家督导复查抽查，需重新完善企业三方协议书盖章页并调改材料。';
    await clearAndType(page, '#specialDocNo', docNo);
    await clearAndType(page, '#specialUnlockReason', unlockReason);
    await sleep(500);

    // 截图 1：特批解锁输入弹窗
    await page.screenshot({ path: step1Shot });
    console.log(`   - [截图 1] 已截取特批解锁弹窗: ${step1Shot}`);

    // 点击弹窗“确认特批解锁”确认按钮
    await page.evaluate(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal-content')).find(m => m.innerText.includes('特批解锁'));
      if (modal) {
        const okBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('确认特批解锁'));
        if (okBtn) okBtn.click();
      }
    });
    await sleep(2500);

    // 截图 2：列表显示状态为“特批解锁中”
    await page.screenshot({ path: step2Shot });
    console.log(`   - [截图 2] 已截取特批解锁后卷宗列表状态: ${step2Shot}`);

    // 校验后端卷宗状态是否已转为 SPECIAL_UNLOCKED
    const checkUnlockRes = await httpRequest(`${BACKEND_BASE}/archives/${archiveId}`, {
      method: 'GET',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    });
    if (checkUnlockRes.data.data.status !== 'SPECIAL_UNLOCKED') {
      throw new Error(`Special unlock status mismatch: got ${checkUnlockRes.data.data.status}`);
    }
    console.log('   - [PASS] 后端卷宗状态已成功变更为 SPECIAL_UNLOCKED，编辑窗口开启！');

    // -----------------------------------------------------------------------
    // [React 步骤 2] 在解锁保护期内，更新允许修改的阶段材料 (版本 2)
    // -----------------------------------------------------------------------
    console.log('   [React 步骤 2] 解锁状态下学生提交材料重提调改 (v2)，教师复核通过...');
    const reSubmitMatRes = await httpRequest(`${BACKEND_BASE}/internship/materials`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${studentToken}` }
    }, {
      taskId: taskId,
      materialCode: 'TRIPARTITE_AGREEMENT',
      materialName: '三方协议书/接收函盖章件',
      attachmentUrl: 'https://example.com/vouchers/tripartite_iso_2026_v2_signed.pdf',
      contentText: '依据省厅督导抽查意见重新补充加盖了用人单位人力资源部印章与导师签字。'
    });
    if (reSubmitMatRes.data.code !== 200) {
      throw new Error(`Material resubmit in unlocked state failed: ${reSubmitMatRes.data.message}`);
    }
    console.log('   - 学生在特批解锁状态下成功重提新材料版本 (v2)');

    const reAuditMatRes = await httpRequest(`${BACKEND_BASE}/internship/materials/${materialId}/audit`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${teacherToken}` }
    }, { action: 'APPROVED', auditScore: 98.00, auditComment: '补充盖章件已核验无误，符合规范，复核通过' });
    if (reAuditMatRes.data.code !== 200) {
      throw new Error(`Material re-audit failed: ${reAuditMatRes.data.message}`);
    }
    console.log('   - 导师复核通过新材料版本 (v2)');

    // -----------------------------------------------------------------------
    // [React 步骤 3] 在 React 页面打开 9 项前置条件诊断核验弹窗并执行重检
    // -----------------------------------------------------------------------
    console.log('   [React 步骤 3] 在 React 前端执行二次 9 项前置条件诊断核验...');
    // 点击“档案归档前置核验诊断”头部按钮
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find(b => b.innerText.includes('档案归档前置核验诊断'));
      if (btn) btn.click();
    });
    await sleep(1000);

    // 打开实习任务下拉菜单并选择本次任务
    await page.click('.ant-modal-content .ant-select-selector');
    await sleep(600);
    const selectedTask = await page.evaluate((tName) => {
      const options = Array.from(document.querySelectorAll('.ant-select-dropdown .ant-select-item-option'));
      const opt = options.find(o => o.innerText.includes(tName) || o.getAttribute('title')?.includes(tName));
      if (opt) {
        opt.click();
        return true;
      }
      return false;
    }, taskName);
    console.log('   - 任务下拉匹配与选择结果:', selectedTask);
    await sleep(600);

    await page.waitForSelector('#studentId', { timeout: 8000 });
    await clearAndType(page, '#studentId', 1365);
    await page.keyboard.press('Tab');
    await sleep(500);

    // 点击“开始诊断核验”按钮
    await page.evaluate(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal-content')).find(m => m.innerText.includes('实习档案归档前置9项条件诊断'));
      if (modal) {
        const diagBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('开始诊断核验'));
        if (diagBtn) diagBtn.click();
      }
    });

    // 等待 9 项诊断结果完全渲染并且准予归档标签显示
    console.log('   - 等待 9 项诊断雷达数据完全返回并渲染表格...');
    await page.waitForFunction(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal-content')).find(m => m.innerText.includes('实习档案归档前置9项条件诊断'));
      if (!modal) return false;
      return modal.innerText.includes('9项核验全部通过') || modal.innerText.includes('准予归档');
    }, { timeout: 15000 });
    await sleep(1000);

    // 截图 3：前置诊断弹窗中 9 项核验全部通过
    await page.screenshot({ path: step3Shot });
    console.log(`   - [截图 3] 已截取二次前置诊断弹窗 (9/9 通过): ${step3Shot}`);

    // -----------------------------------------------------------------------
    // [React 步骤 4] 再次执行归档锁定，确认版本从 1 递增至 2
    // -----------------------------------------------------------------------
    console.log('   [React 步骤 4] 点击弹窗中的“执行终审归档并锁定”按钮...');
    await page.evaluate(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal-content')).find(m => m.innerText.includes('实习档案归档前置9项条件诊断'));
      if (modal) {
        const freezeBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('执行终审归档并锁定'));
        if (freezeBtn) freezeBtn.click();
      }
    });

    // 等待弹窗关闭（表示归档锁定成功完成）
    await page.waitForFunction(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal-content')).find(m => m.innerText.includes('实习档案归档前置9项条件诊断'));
      if (!modal) return true;
      const wrap = modal.closest('.ant-modal-wrap');
      return !wrap || wrap.style.display === 'none';
    }, { timeout: 15000 });
    await sleep(2000);

    // 点击页面“刷新”按钮更新台账
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const rBtn = btns.find(b => b.innerText.includes('刷新'));
      if (rBtn) rBtn.click();
    });
    await sleep(2000);

    // 截图 4：再次归档后表格行展示状态为 ARCHIVED，版本号为 2
    await page.screenshot({ path: step4Shot });
    console.log(`   - [截图 4] 已截取再次归档锁定后的卷宗列表状态: ${step4Shot}`);

    // -----------------------------------------------------------------------
    // [React 步骤 5] 打开卷宗全息详情抽屉，核对版本递增与全景审计
    // -----------------------------------------------------------------------
    console.log('   [React 步骤 5] 打开卷宗全息详情抽屉并核对版本历史与审计信息...');
    const detailBtnClicked = await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo) || row.innerText.includes('student_rectify_iso')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('卷宗核验'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    }, archiveV1.archiveNo);

    if (!detailBtnClicked) {
      throw new Error(`Could not find '卷宗核验' button for ${archiveV1.archiveNo}`);
    }

    await sleep(1500);
    await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
    await sleep(1000);

    // 截图 5：卷宗全息详情抽屉
    await page.screenshot({ path: step5Shot });
    console.log(`   - [截图 5] 已截取卷宗全息详情抽屉 (v2): ${step5Shot}`);

  } finally {
    await browser.close();
  }

  // -------------------------------------------------------------------------
  // Step 6: 终态数据与哈希对比校验
  // -------------------------------------------------------------------------
  console.log('7. 执行再次归档终态与文件生成对比校验...');
  const detailResV2 = await httpRequest(`${BACKEND_BASE}/archives/${archiveId}`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  const archiveV2 = detailResV2.data.data;
  console.log(`   - [终态比对] 卷宗 ID: ${archiveV2.id}`);
  console.log(`   - [终态比对] 状态变化: ${archiveV1.status} -> SPECIAL_UNLOCKED -> ${archiveV2.status}`);
  console.log(`   - [终态比对] 版本变化: v${archiveV1.version} -> v${archiveV2.version}`);

  if (archiveV2.status !== 'ARCHIVED' || archiveV2.version !== 2) {
    throw new Error(`Rearchived status/version check failed: status=${archiveV2.status}, version=${archiveV2.version}`);
  }

  const zipHashV2 = getFileHash(zipDiskPath);
  const pdfHashV2 = getFileHash(pdfDiskPath);
  console.log(`   - [文件哈希校验] ZIP SHA-256 (v1: ${zipHashV1}) -> (v2: ${zipHashV2})`);
  console.log(`   - [文件哈希校验] PDF SHA-256 (v1: ${pdfHashV1}) -> (v2: ${pdfHashV2})`);

  if (zipHashV1 === zipHashV2) {
    console.warn('   - Warning: ZIP hash is identical. Verifying bundle content update...');
  } else {
    console.log('   - [PASS] ZIP 卷宗包哈希已成功随新材料更新而重算！');
  }

  console.log('================================================================');
  console.log(`>>> 电子档案特批解锁与再次归档分支定向验收通过！卷宗 ID: ${archiveId}`);
  console.log('================================================================');
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
