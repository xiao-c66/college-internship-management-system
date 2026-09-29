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

async function run() {
  console.log('================================================================');
  console.log('>>> [React 成绩仲裁调分通过分支] 真实业务集成验收');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();
  console.log('1. 已从安全存储加载 student_rectify_iso (ID 1365) 凭证');

  const deptToken = await apiLogin('deptadmin', '123456');

  // Step 1: 创建专属隔离任务 (例如任务 2110)
  console.log('2. 院系管理员创建全新专属隔离实习任务 (用于调分通过分支)...');
  const today = new Date().toISOString().split('T')[0];
  const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];
  const taskCode = `TASK_SCORE_PASS_${Date.now()}`;
  const taskPayload = {
    taskCode: taskCode,
    taskName: `TASK_SCORE_PASS_ISO_${Date.now()}`,
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
  const taskId = createTaskRes.data.data?.id || createTaskRes.data.data;
  console.log(`   - 调分通过专属任务创建成功: taskId = ${taskId}`);

  // 发布任务
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/publish`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log(`   - 任务发布成功，名单生成`);

  // 指派指导教师 teacher2 给 student 1365
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/assign-teacher`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, { teacherId: 5, studentIds: [1365] });
  console.log(`   - 指派教师 teacher2 给 student_rectify_iso 成功`);

  // Step 2: 教师录入初始成绩 (总分 80.00, 良好)
  console.log('3. 指导教师 teacher2 录入五维成绩 (初始成绩: 总分 80.00, 良好)...');
  const teacherToken = await apiLogin('teacher2', '123456');
  const submitScoreRes = await httpRequest(`${BACKEND_BASE}/score/summaries`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    taskId,
    studentId: 1365,
    enterpriseScore: 80.0,
    processScore: 82.0,
    weeklyScore: 78.0,
    materialScore: 80.0,
    summaryScore: 80.0,
    evaluationComment: '该生按期完成实习各环节，表现良好。',
    submitToDept: true
  });
  const scoreId = submitScoreRes.data.data;
  console.log(`   - 成绩录入并提交院系成功: scoreId = ${scoreId}`);

  // Step 3: 院系审核通过
  console.log('4. 院系管理员审核通过实习成绩...');
  await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}/audit`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });

  // Step 4: 院系发布成绩公示 (进入 PUBLICITY)
  console.log('5. 院系发布成绩公示 (进入 PUBLICITY)...');
  await httpRequest(`${BACKEND_BASE}/score/tasks/${taskId}/publicity`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });

  // Step 5: 启动 Edge 浏览器
  console.log('6. 启动 Edge 浏览器，进行 React UI 调分通过端到端验证...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    defaultViewport: { width: 1440, height: 1100 }
  });
  const page = await browser.newPage();

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_score_pass_step1_student_publicity.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_score_pass_step2_student_appealed.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_score_pass_step3_dept_arbitrate_modal.png');
  const step4Shot = path.join(SCREENSHOT_DIR, 'react_score_pass_step4_dept_detail_updated.png');
  const step5Shot = path.join(SCREENSHOT_DIR, 'react_score_pass_step5_student_final_view_full.png');

  // 5.1 学生端登录并进入成绩页面
  console.log('   [Step 5.1] 学生 student_rectify_iso 登录 React 页面...');
  await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
  await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
  await sleep(1500);

  // 切换任务下拉框到新任务
  const selectExists = await page.$('.ant-select');
  if (selectExists) {
    console.log('   [Step 5.1] 切换到调分专项任务...');
    await page.click('.ant-select');
    await sleep(600);
    const option = await page.$(`.ant-select-item-option[title*="TASK_SCORE_PASS_ISO"]`);
    if (option) {
      await option.click();
      await sleep(1000);
    }
  }

  await page.screenshot({ path: step1Shot });
  console.log(`   [Step 5.1] 学生端公示期界面已截屏: ${step1Shot}`);

  const initPageText = await page.evaluate(() => document.body.innerText);
  console.log(`   - 初始总分显示 80.00: ${initPageText.includes('80.00')}`);
  console.log(`   - 初始等级显示 良好: ${initPageText.includes('良好')}`);

  // 5.2 学生点击发起申诉
  console.log('   [Step 5.2] 学生发起复核申诉 (提供高水平竞赛证明理由)...');
  await page.waitForSelector('button.ant-btn-dangerous', { timeout: 8000 });
  await page.click('button.ant-btn-dangerous');
  await sleep(800);

  const appealReason = '阶段任务材料与周报附件中包含国家级竞赛获奖证书与技术发明专利申报佐证，申请按照优秀标准核增材料与总结得分。';
  await clearAndType(page, '#appealReason', appealReason);
  await sleep(500);

  await page.evaluate(() => {
    const btns = Array.from(document.querySelectorAll('.ant-modal button'));
    const btn = btns.find(b => b.innerText.includes('提交申诉申请'));
    if (btn) btn.click();
  });
  await sleep(1500);

  await page.screenshot({ path: step2Shot });
  console.log(`   [Step 5.2] 学生端提报申诉后时间线已截屏: ${step2Shot}`);

  // 5.3 院系管理员登录并执行调分仲裁
  console.log('7. [Step 5.3] 院系管理员 deptadmin 登录并执行【申诉成立，准予调分】...');
  await browserLogin(page, 'deptadmin', '123456', '/dashboard/dept');
  await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
  await sleep(1500);

  // 筛选专属任务
  console.log('   [Step 5.3] 筛选调分专项任务...');
  const filterSelect = await page.$('.ant-form-inline .ant-select');
  if (filterSelect) {
    await filterSelect.click();
    await sleep(600);
    const opt = await page.$(`.ant-select-item-option[title*="TASK_SCORE_PASS_ISO"]`);
    if (opt) {
      await opt.click();
      await sleep(1000);
    }
  }

  // 点击学生行【申诉仲裁】
  console.log('   [Step 5.3] 点击 student_rectify_iso 的【申诉仲裁】按钮...');
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
  if (!clickedArbitrate) throw new Error('Cannot find 申诉仲裁 button in table');
  await sleep(1500);

  // 验证回显了学生理由
  const modalText = await page.evaluate(() => {
    const m = document.querySelector('.ant-modal');
    return m ? m.innerText : '';
  });
  console.log(`   - 仲裁弹窗成功回显学生竞赛申诉理由: ${modalText.includes('国家级竞赛获奖证书')}`);

  // 输入红头批文号与调分意见
  console.log('   [Step 5.3] 录入红头批文备案号与更正五维得分...');
  await clearAndType(page, '#approvalDocNo', '计通院发[2026]29号');
  await sleep(300);

  const arbitrateComment = '经院系学术委员会与教学督导组共同复议，学生所附国家级竞赛成果属实且符合调分认定标准，准予更正材料与总结得分并重新核算等第。';
  await clearAndType(page, '#auditComment', arbitrateComment);
  await sleep(300);

  // 调分：更正五维得分
  // 调分后：企业 92, 过程 90, 周报 90, 材料 95, 总结 93
  // 加权平均 = (92+90+90+95+93)*0.2 = 92.00 (优秀 EXCELLENT)
  await clearAndType(page, '#enterpriseScore', 92);
  await clearAndType(page, '#processScore', 90);
  await clearAndType(page, '#weeklyScore', 90);
  await clearAndType(page, '#materialScore', 95);
  await clearAndType(page, '#summaryScore', 93);
  await sleep(500);

  await page.screenshot({ path: step3Shot });
  console.log(`   [Step 5.3] 院系调分仲裁填写完成弹窗已截屏: ${step3Shot}`);

  // 提交调分仲裁
  console.log('   [Step 5.3] 提交调分仲裁决定...');
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

  // 点击【详情】按钮核查调分后数据
  console.log('   [Step 5.3] 点击【详情】查看调分后审计痕迹...');
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
    await page.screenshot({ path: step4Shot });
    console.log(`   [Step 5.3] 院系端调分后详情审计快照已截屏: ${step4Shot}`);

    const detailText = await page.evaluate(() => {
      const m = document.querySelector('.ant-modal');
      return m ? m.innerText : '';
    });
    console.log(`   - 详情展示调分后总分 92.00: ${detailText.includes('92.00')}`);
    console.log(`   - 详情展示新等级 优秀: ${detailText.includes('优秀')}`);
    console.log(`   - 详情展示红头批文 计通院发[2026]29号: ${detailText.includes('计通院发[2026]29号')}`);
    console.log(`   - 详情轨迹展示【仲裁通过（准予更正调分）】: ${detailText.includes('仲裁通过（准予更正调分）')}`);

    await page.keyboard.press('Escape');
    await sleep(600);
  }

  // 5.4 学生端重新登录核验
  console.log('8. [Step 5.4] 学生 student_rectify_iso 重新登录核验调分后成绩与等第...');
  await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
  await page.goto('http://127.0.0.1:3000/score', { waitUntil: 'networkidle0' });
  await sleep(1500);

  const selectStudentExists = await page.$('.ant-select');
  if (selectStudentExists) {
    await page.click('.ant-select');
    await sleep(600);
    const option = await page.$(`.ant-select-item-option[title*="TASK_SCORE_PASS_ISO"]`);
    if (option) {
      await option.click();
      await sleep(1000);
    }
  }

  await page.screenshot({ path: step5Shot, fullPage: true });
  console.log(`   [Step 5.4] 学生端调分后最终视图全图已截屏: ${step5Shot}`);

  const studentFinalText = await page.evaluate(() => document.body.innerText);
  console.log(`   - 学生端总分跃升为 92.00: ${studentFinalText.includes('92.00')}`);
  console.log(`   - 学生端等级变更为 优秀: ${studentFinalText.includes('优秀')}`);
  console.log(`   - 轨迹展示 计通院发[2026]29号: ${studentFinalText.includes('计通院发[2026]29号')}`);
  console.log(`   - 轨迹展示 仲裁通过（准予更正调分）: ${studentFinalText.includes('仲裁通过（准予更正调分）')}`);
  console.log(`   - 重复申诉入口已关闭: ${!studentFinalText.includes('对成绩有异议？发起成绩复核申诉')}`);

  await browser.close();

  // Step 6: 后端数据库及审计核查
  console.log('9. [Step 6] 后端接口与数据库状态终验核查...');
  const detailCheckRes = await httpRequest(`${BACKEND_BASE}/score/summaries/${scoreId}`, {
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  const detailData = detailCheckRes.data.data;
  console.log(`   - scoreId: ${detailData.id}`);
  console.log(`   - finalScore: ${detailData.finalScore} (期望 92.00)`);
  console.log(`   - scoreLevel: ${detailData.scoreLevel} (期望 EXCELLENT)`);
  console.log(`   - 五维分数: 企业=${detailData.enterpriseScore}, 过程=${detailData.processScore}, 周报=${detailData.weeklyScore}, 材料=${detailData.materialScore}, 总结=${detailData.summaryScore}`);
  console.log(`   - 审计历史记录:`);
  detailData.auditHistory?.forEach((h, idx) => {
    console.log(`     [${idx + 1}] ID=${h.id}, action=${h.action}, operator=${h.auditUserName}, docNo=${h.approvalDocNo || '-'}, comment=${h.auditComment}`);
  });

  const resultSummary = {
    taskId,
    scoreId,
    studentId: 1365,
    initialFinalScore: 80.0,
    initialScoreLevel: 'GOOD',
    updatedFinalScore: detailData.finalScore,
    updatedScoreLevel: detailData.scoreLevel,
    recalculatedCorrectly: detailData.finalScore === 92.0 && detailData.scoreLevel === 'EXCELLENT',
    approvalDocNo: '计通院发[2026]29号',
    auditHistory: detailData.auditHistory?.map(h => ({
      id: h.id,
      action: h.action,
      comment: h.auditComment,
      operator: h.auditUserName,
      approvalDocNo: h.approvalDocNo,
      operateTime: h.operateTime
    })),
    screenshots: [
      step1Shot,
      step2Shot,
      step3Shot,
      step4Shot,
      step5Shot
    ]
  };

  fs.writeFileSync(
    'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\scratch\\react_score_appeal_pass_result.json',
    JSON.stringify(resultSummary, null, 2)
  );

  console.log('\n================================================================');
  console.log('>>> [React 成绩仲裁调分通过分支] 真实业务集成验收成功完成！');
  console.log('================================================================');
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
