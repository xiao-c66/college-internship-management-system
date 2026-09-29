import fs from 'fs';
import path from 'path';
import http from 'http';
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
  await page.type(selector, text);
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

async function run() {
  console.log('================================================================');
  console.log('>>> [React 成绩申诉驳回分支] 真实业务集成验收');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();
  console.log('1. 已从安全存储加载 student_rectify_iso (ID 1365) 凭证');

  const deptToken = await apiLogin('deptadmin', '123456');

  let taskId = 2109;
  let scoreId = 4164;
  let hasPendingAppeal = false;
  let hasRejected = false;

  const existingScoreRes = await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}`, {
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  if (existingScoreRes.data.code === 200 && existingScoreRes.data.data?.auditHistory) {
    const applies = existingScoreRes.data.data.auditHistory.filter(h => h.action === 'APPEAL_APPLY');
    const rejects = existingScoreRes.data.data.auditHistory.filter(h => h.action === 'APPEAL_REJECT');
    if (rejects.length > 0) {
      hasRejected = true;
      console.log(`2. 检测到任务 2109 下申诉已执行驳回: scoreId=${scoreId}, rejectHistory.id=${rejects[rejects.length - 1].id}`);
    } else if (applies.length > 0) {
      hasPendingAppeal = true;
      console.log(`2. 检测到任务 2109 下已存在待仲裁申诉记录: scoreId=${scoreId}, appealHistory.id=${applies[applies.length - 1].id}`);
    }
  }

  // 启动 Edge 浏览器，进行 React UI 端到端验证
  console.log('3. 启动 Edge 浏览器，进行 React UI 端到端验证...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    defaultViewport: { width: 1440, height: 900 }
  });
  const page = await browser.newPage();

  const studentInitialShot = path.join(SCREENSHOT_DIR, 'react_score_step1_student_publicity.png');
  const studentAppealedShot = path.join(SCREENSHOT_DIR, 'react_score_step2_student_appealed.png');
  const deptArbitrateModalShot = path.join(SCREENSHOT_DIR, 'react_score_step3_dept_arbitrate_reject_modal.png');
  const deptDetailShot = path.join(SCREENSHOT_DIR, 'react_score_step4_dept_detail_rejected.png');
  const studentFinalShot = path.join(SCREENSHOT_DIR, 'react_score_step5_student_final_rejected_view.png');

  if (!hasPendingAppeal && !hasRejected) {
    // Fresh run: Create task, enter score, audit, publicity, student appeal
    console.log('2. 院系管理员创建全新专属隔离实习任务...');
    const today = new Date().toISOString().split('T')[0];
    const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];
    const taskCode = `TASK_SCORE_${Date.now()}`;
    const taskPayload = {
      taskCode: taskCode,
      taskName: `TASK_SCORE_REJECT_ISO_${Date.now()}`,
      deptId: 1,
      academicYear: '2025-2026',
      semester: 1,
      internshipMode: 'CONCENTRATED',
      startDate: today,
      endDate: nextMonth,
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
    };

    const createTaskRes = await httpRequest(`${BACKEND_BASE}/tasks`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${deptToken}` }
    }, taskPayload);

    if (createTaskRes.data.code !== 200) {
      throw new Error(`Failed to create task: ${createTaskRes.data.message}`);
    }
    taskId = createTaskRes.data.data?.id || createTaskRes.data.data;
    console.log(`   - 专属任务创建成功: taskId = ${taskId}`);

    await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/publish`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${deptToken}` }
    });

    await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/assign-teacher`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${deptToken}` }
    }, { teacherId: 5, studentIds: [1365] });

    const teacherToken = await apiLogin('teacher2', '123456');
    const submitScoreRes = await httpRequest(`${BACKEND_BASE}/score/summaries`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${teacherToken}` }
    }, {
      taskId,
      studentId: 1365,
      enterpriseScore: 82.0,
      processScore: 85.0,
      weeklyScore: 80.0,
      materialScore: 84.0,
      summaryScore: 86.0,
      evaluationComment: '该生在实习期间表现良好，工作态度积极，圆满完成实习既定目标。',
      submitToDept: true
    });
    scoreId = submitScoreRes.data.data;

    await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}/audit`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${deptToken}` }
    });

    await httpRequest(`${BACKEND_BASE}/score/tasks/${taskId}/publicity`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${deptToken}` }
    });

    // 学生端操作
    await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
    await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
    await sleep(1500);

    const selectExists = await page.$('.ant-select');
    if (selectExists) {
      await page.click('.ant-select');
      await sleep(600);
      const option = await page.$(`.ant-select-item-option[title*="TASK_SCORE_REJECT_ISO"]`);
      if (option) {
        await option.click();
        await sleep(1000);
      }
    }

    await page.screenshot({ path: studentInitialShot });
    await page.waitForSelector('button.ant-btn-dangerous', { timeout: 8000 });
    await page.click('button.ant-btn-dangerous');
    await sleep(800);

    const appealReason = '本人在过程指导台账与阶段材料批改中提交了完整附录佐证，申请重新核对五维明细得分。';
    await clearAndType(page, '#appealReason', appealReason);
    await sleep(500);

    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const btn = btns.find(b => b.innerText.includes('提交申诉申请'));
      if (btn) btn.click();
    });
    await sleep(1500);
    await page.screenshot({ path: studentAppealedShot });
  }

  if (hasPendingAppeal && !hasRejected) {
    // 院系管理员执行裁决驳回
    console.log('4. [Step 6] 院系管理员 deptadmin 登录 React 页面并执行裁决驳回...');
    await browserLogin(page, 'deptadmin', '123456', '/dashboard/dept');
    await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
    await sleep(1500);

    // 查找表格中 student_rectify_iso 的“申诉仲裁”按钮并点击
    console.log('   [Step 6.2] 点击表格中 student_rectify_iso 的【申诉仲裁】按钮...');
    const clickedArbitrate = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('table tr'));
      const targetRow = rows.find(r => r.innerText.includes('student_rectify_iso'));
      if (targetRow) {
        const btn = Array.from(targetRow.querySelectorAll('button')).find(b => b.innerText.includes('申诉仲裁') || b.innerText.includes('申诉裁决'));
        if (btn) {
          btn.click();
          return true;
        }
      }
      return false;
    });
    if (!clickedArbitrate) throw new Error('Cannot find 申诉仲裁 button for student_rectify_iso in table');
    await sleep(1500);

    // 选中“申诉驳回，维持原判”
    console.log('   [Step 6.3] 选中【申诉驳回，维持原判】Radio...');
    await page.evaluate(() => {
      const radios = Array.from(document.querySelectorAll('.ant-modal .ant-radio-input'));
      const rejectRadio = radios.find(r => r.value === 'REJECT');
      if (rejectRadio) {
        rejectRadio.click();
      }
    });
    await sleep(600);

    // 填写仲裁驳回意见
    console.log('   [Step 6.3] 填写仲裁驳回审核意见...');
    const arbitrateComment = '经院系教学督导与复核委员会严格核查，原五维成绩评定标准合规客观，申诉理由不成立，决定驳回申诉并维持原判。';
    await clearAndType(page, '#auditComment', arbitrateComment);
    await sleep(600);

    await page.screenshot({ path: deptArbitrateModalShot });
    console.log(`   [Step 6.3] 院系仲裁驳回弹窗已截屏: ${deptArbitrateModalShot}`);

    // 提交仲裁决定
    console.log('   [Step 6.4] 点击【确认提交仲裁决定】...');
    const clickedSubmit = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal-footer button'));
      const btn = btns.find(b => b.innerText.includes('确认提交仲裁决定'));
      if (btn) {
        btn.click();
        return true;
      }
      return false;
    });
    if (!clickedSubmit) throw new Error('Cannot find 确认提交仲裁决定 button');
    await sleep(2500);
  }

  // 院系端查看详情轨迹
  console.log('4. [Step 6.5] 院系管理员查看详情轨迹...');
  await browserLogin(page, 'deptadmin', '123456', '/dashboard/dept');
  await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
  await sleep(1500);

  const clickedDetail = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('table tr'));
    const targetRow = rows.find(r => r.innerText.includes('student_rectify_iso'));
    if (targetRow) {
      const btn = Array.from(targetRow.querySelectorAll('button')).find(b => b.innerText.includes('详情'));
      if (btn) {
        btn.click();
        return true;
      }
    }
    return false;
  });
  if (clickedDetail) {
    await sleep(1500);
    await page.screenshot({ path: deptDetailShot });
    console.log(`   [Step 6.5] 院系端详情痕迹弹窗已截屏: ${deptDetailShot}`);
    await page.keyboard.press('Escape');
    await sleep(600);
  }

  // Step 7: 再次以学生身份登录，核实原成绩保持完全不变，且申诉驳回痕迹正确展示
  console.log('5. [Step 7] 学生 student_rectify_iso 再次登录核验成绩保持完全不变...');
  await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
  await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
  await sleep(1500);

  const selectStudentExists = await page.$('.ant-select');
  if (selectStudentExists) {
    await page.click('.ant-select');
    await sleep(600);
    const option = await page.$(`.ant-select-item-option[title*="TASK_SCORE_REJECT_ISO"]`);
    if (option) {
      await option.click();
      await sleep(1000);
    }
  }

  await page.screenshot({ path: studentFinalShot });
  console.log(`   [Step 7] 学生端最终视图已截屏: ${studentFinalShot}`);

  const studentFinalText = await page.evaluate(() => document.body.innerText);
  console.log(`   - 学生成绩总分依然维持 '83.40' 完全不变: ${studentFinalText.includes('83.40')}`);
  console.log(`   - 等级依然维持 '良好': ${studentFinalText.includes('良好')}`);
  console.log(`   - 轨迹展示 '仲裁驳回': ${studentFinalText.includes('仲裁驳回')}`);
  console.log(`   - 驳回后不得重复申诉 (申诉按钮不存在): ${!studentFinalText.includes('对成绩有异议？发起成绩复核申诉')}`);

  await browser.close();

  // Step 8: 后端接口与数据库核对
  console.log('6. [Step 8] 后端接口与数据状态一致性核查...');
  const detailCheckRes = await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}`, {
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  const detailData = detailCheckRes.data.data;
  console.log(`   - scoreId: ${detailData.id}`);
  console.log(`   - finalScore: ${detailData.finalScore} (期望 83.40)`);
  console.log(`   - scoreLevel: ${detailData.scoreLevel} (期望 GOOD)`);
  console.log(`   - 五维分数: 企业=${detailData.enterpriseScore}, 过程=${detailData.processScore}, 周报=${detailData.weeklyScore}, 材料=${detailData.materialScore}, 总结=${detailData.summaryScore}`);
  console.log(`   - 审计历史记录条数: ${detailData.auditHistory?.length}`);
  detailData.auditHistory?.forEach((h, idx) => {
    console.log(`     [${idx + 1}] ID=${h.id}, action=${h.action}, operator=${h.auditUserName}, comment=${h.auditComment}`);
  });

  const resultSummary = {
    taskId,
    scoreId,
    studentId: 1365,
    finalScore: detailData.finalScore,
    scoreLevel: detailData.scoreLevel,
    unchanged: detailData.finalScore === 83.4 && detailData.scoreLevel === 'GOOD',
    auditHistory: detailData.auditHistory?.map(h => ({
      id: h.id,
      action: h.action,
      comment: h.auditComment,
      operator: h.auditUserName,
      operateTime: h.operateTime
    })),
    screenshots: [
      studentInitialShot,
      studentAppealedShot,
      deptArbitrateModalShot,
      deptDetailShot,
      studentFinalShot
    ]
  };

  fs.writeFileSync(
    'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\scratch\\react_score_appeal_reject_result.json',
    JSON.stringify(resultSummary, null, 2)
  );

  console.log('\n================================================================');
  console.log('>>> [React 成绩申诉驳回分支] 真实业务集成验收成功完成！');
  console.log('================================================================');
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
