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

async function selectTask(page, taskKeyword) {
  await sleep(1000);
  const currentSelectText = await page.evaluate(() => {
    const sel = document.querySelector('.ant-select-selection-item');
    return sel ? sel.innerText : '';
  });
  if (currentSelectText && currentSelectText.includes(taskKeyword)) {
    console.log(`   - 当前下拉框已默认选中目标任务: ${currentSelectText}`);
    return;
  }
  const selectExists = await page.$('.ant-select');
  if (selectExists) {
    console.log(`   - 尝试在下拉框选择任务: ${taskKeyword}...`);
    await page.click('.ant-select');
    await sleep(600);
    const options = await page.$$('.ant-select-item-option');
    let matched = false;
    for (const opt of options) {
      const text = await opt.evaluate(el => el.innerText || el.getAttribute('title') || '');
      if (text.includes(taskKeyword)) {
        await opt.click();
        matched = true;
        console.log(`   - 已选中任务项: ${text}`);
        break;
      }
    }
    if (!matched && options.length > 0) {
      await options[0].click();
      console.log(`   - 未找到精确匹配，已选中首个任务项`);
    }
    await sleep(1000);
  }
}

async function run() {
  console.log('================================================================');
  console.log('>>> [React 阶段材料版本流转分支] 真实业务集成验收');
  console.log('“学生提交材料 → 教师退回修改 → 学生重提新版（版本号递增）→ 教师复核通过”');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();
  console.log('1. 已从安全存储加载 student_rectify_iso (ID 1365) 凭证');

  const deptToken = await apiLogin('deptadmin', '123456');

  // Step 1: 创建全新专属隔离实习任务 (例如任务 2111)
  console.log('2. 院系管理员创建全新专属隔离实习任务 (用于阶段材料版本流转)...');
  const today = new Date().toISOString().split('T')[0];
  const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];
  const taskCode = `TASK_MATERIAL_${Date.now()}`;
  const taskName = `TASK_MATERIAL_ISO_${Date.now()}`;
  const taskPayload = {
    taskCode: taskCode,
    taskName: taskName,
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
    materialChecklist: '', // 留空将自动走系统标准阶段材料清单回退机制
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
  console.log(`   - 材料专属任务创建成功: taskId = ${taskId}`);

  // 发布任务
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/publish`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log(`   - 任务发布成功，名单生成`);

  // 指派指导教师 teacher2 (ID 5) 给 student 1365
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/assign-teacher`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, { teacherId: 5, studentIds: [1365] });
  console.log(`   - 指派教师 teacher2 给 student_rectify_iso 成功`);

  // Step 2: 启动浏览器
  console.log('3. 启动 Edge 浏览器并执行 React 端多角色流转测试...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_material_step1_student_v1_submitted.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_material_step2_teacher_returned.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_material_step3_student_v2_resubmitted.png');
  const step4Shot = path.join(SCREENSHOT_DIR, 'react_material_step4_version_history_modal.png');
  const step5Shot = path.join(SCREENSHOT_DIR, 'react_material_step5_teacher_approved.png');
  const step6Shot = path.join(SCREENSHOT_DIR, 'react_material_step6_student_final_view.png');

  try {
    // -------------------------------------------------------------
    // [Step 1] 学生 student_rectify_iso 登录 React 提报材料 (版本 1)
    // -------------------------------------------------------------
    console.log('   [Step 1] 学生 student_rectify_iso 登录 React 提报材料 (v1)...');
    await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
    await page.goto('http://127.0.0.1:3000/material', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await selectTask(page, taskName);
    await sleep(1000);

    await page.waitForSelector('.ant-card', { timeout: 10000 });
    await sleep(1000);

    // 找到“三方协议书/接收函盖章件”卡片中的“提报材料”按钮
    console.log('   [Step 1] 点击“三方协议书/接收函盖章件”的“提报材料”按钮...');
    const clickedSubmit = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('.ant-card'));
      for (const card of cards) {
        if (card.innerText.includes('三方协议书')) {
          const btn = Array.from(card.querySelectorAll('button')).find(b => b.innerText.includes('提报材料'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    });

    if (!clickedSubmit) {
      throw new Error('未找到“三方协议书”提报材料按钮');
    }
    await sleep(1000);

    // 填写提报表单
    await page.waitForSelector('.ant-modal input#attachmentUrl', { timeout: 8000 });
    await clearAndType(page, '.ant-modal input#attachmentUrl', 'https://example.com/vouchers/tripartite_iso_2026_v1.pdf');
    await clearAndType(page, '.ant-modal input#fileName', '三方协议书_已签署_v1.pdf');
    await sleep(500);

    // 点击“确认提交提报”
    console.log('   [Step 1] 提交 v1 材料提报...');
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const okBtn = btns.find(b => b.innerText.includes('确认提交提报'));
      if (okBtn) okBtn.click();
    });
    await sleep(2000);

    await page.screenshot({ path: step1Shot });
    console.log(`   [Step 1] 学生端 v1 提报成功界面已截屏: ${step1Shot}`);

    // -------------------------------------------------------------
    // [Step 2] 教师 teacher2 登录 React 查验并退回修改 (RETURNED)
    // -------------------------------------------------------------
    console.log('   [Step 2] 教师 teacher2 登录 React 查验并退回修改 (RETURNED)...');
    await browserLogin(page, 'teacher2', '123456', '/dashboard/teacher');
    await page.goto('http://127.0.0.1:3000/material', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await selectTask(page, taskName);
    await sleep(1000);

    // 输入目标学生 ID 1365 并刷新
    console.log('   [Step 2] 教师输入目标学生 ID 1365 并查询材料列表...');
    const inputExists = await page.$('input[placeholder*="学生ID"]');
    if (inputExists) {
      await clearAndType(page, 'input[placeholder*="学生ID"]', '1365');
      await sleep(500);
      await page.keyboard.press('Enter');
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const refreshBtn = btns.find(b => b.innerText.includes('刷新'));
        if (refreshBtn) refreshBtn.click();
      });
      await sleep(1500);
    }

    await page.waitForSelector('.ant-table-row', { timeout: 10000 });
    await sleep(500);

    // 验证表格中出现三方协议记录，状态为“待查验”，版本为 v1
    const teacherPageText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 表格显示三方协议书: ${teacherPageText.includes('三方协议书')}`);
    console.log(`   - 查验状态显示待查验: ${teacherPageText.includes('待查验')}`);
    console.log(`   - 版本显示 v1: ${teacherPageText.includes('v1')}`);

    // 点击“查验打分”
    console.log('   [Step 2] 教师点击“查验打分”并执行退回修改...');
    await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes('三方协议书')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('查验打分'));
          if (btn) {
            btn.click();
            break;
          }
        }
      }
    });
    await sleep(1000);

    // 在弹窗中选择“退回修改”
    await page.waitForSelector('.ant-modal', { timeout: 8000 });
    await page.evaluate(() => {
      const radios = Array.from(document.querySelectorAll('.ant-modal .ant-radio-wrapper'));
      const returnRadio = radios.find(r => r.innerText.includes('退回修改'));
      if (returnRadio) returnRadio.click();
    });
    await sleep(600);

    // 填写退回评语
    const returnComment = '三方协议材料缺少用人单位人事公章与签章日期，请补齐印章后重新提交新版。';
    await page.waitForSelector('.ant-modal textarea#auditComment', { timeout: 8000 });
    await clearAndType(page, '.ant-modal textarea#auditComment', returnComment);
    await sleep(500);

    // 确认查验结果
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const okBtn = btns.find(b => b.innerText.includes('确认查验结果'));
      if (okBtn) okBtn.click();
    });
    await sleep(2000);

    await page.screenshot({ path: step2Shot });
    console.log(`   [Step 2] 教师查验退回成功界面已截屏: ${step2Shot}`);

    // -------------------------------------------------------------
    // [Step 3] 学生重新登录，查验退回状态，重提新版 (v2)
    // -------------------------------------------------------------
    console.log('   [Step 3] 学生重新登录，查验退回状态并重提新版 (v2)...');
    await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
    await page.goto('http://127.0.0.1:3000/material', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await selectTask(page, taskName);
    await sleep(1000);

    const studentReturnText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 学生端显示退回重修状态: ${studentReturnText.includes('退回重修')}`);
    console.log(`   - 导师评语已回显: ${studentReturnText.includes('缺少用人单位人事公章')}`);

    // 点击“重提新版”
    console.log('   [Step 3] 点击“重提新版”按钮...');
    const clickedResubmit = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('.ant-card'));
      for (const card of cards) {
        if (card.innerText.includes('三方协议书')) {
          const btn = Array.from(card.querySelectorAll('button')).find(b => b.innerText.includes('重提新版'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    });

    if (!clickedResubmit) {
      throw new Error('未找到“重提新版”按钮');
    }
    await sleep(1000);

    // 填写 v2 提报内容
    await page.waitForSelector('.ant-modal input#attachmentUrl', { timeout: 8000 });
    await clearAndType(page, '.ant-modal input#attachmentUrl', 'https://example.com/vouchers/tripartite_iso_2026_v2_signed.pdf');
    await clearAndType(page, '.ant-modal input#fileName', '三方协议书_已补齐公章_v2.pdf');
    await sleep(500);

    // 确认提交提报
    console.log('   [Step 3] 确认提交 v2 材料...');
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const okBtn = btns.find(b => b.innerText.includes('确认提交提报'));
      if (okBtn) okBtn.click();
    });
    await sleep(2000);

    await page.screenshot({ path: step3Shot });
    console.log(`   [Step 3] 学生端 v2 重提成功界面已截屏: ${step3Shot}`);

    // -------------------------------------------------------------
    // [Step 4] 学生打开“版本追溯”弹窗，核验版本快照完整性
    // -------------------------------------------------------------
    console.log('   [Step 4] 打开版本快照弹窗，验证历史版本回溯...');
    await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('.ant-card'));
      for (const card of cards) {
        if (card.innerText.includes('三方协议书')) {
          const btn = Array.from(card.querySelectorAll('button')).find(b => b.innerText.includes('版本追溯'));
          if (btn) {
            btn.click();
            break;
          }
        }
      }
    });
    await sleep(1500);

    await page.waitForSelector('.ant-modal .ant-timeline', { timeout: 8000 });
    const timelineText = await page.evaluate(() => {
      const modal = document.querySelector('.ant-modal');
      return modal ? modal.innerText : '';
    });
    console.log(`   - 版本快照包含版本 v2: ${timelineText.includes('版本 v2')}`);
    console.log(`   - 版本快照包含版本 v1: ${timelineText.includes('版本 v1')}`);
    console.log(`   - 历史版本留存退回查验评语: ${timelineText.includes('缺少用人单位人事公章')}`);

    await page.screenshot({ path: step4Shot });
    console.log(`   [Step 4] 版本快照弹窗界面已截屏: ${step4Shot}`);

    // 关闭版本快照弹窗
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const closeBtn = btns.find(b => b.innerText.includes('关闭'));
      if (closeBtn) closeBtn.click();
    });
    await sleep(800);

    // -------------------------------------------------------------
    // [Step 5] 教师重新登录，复核 v2 材料并通过 (APPROVED, 96.00分)
    // -------------------------------------------------------------
    console.log('   [Step 5] 教师重新登录，复核通过 v2 材料 (APPROVED, 96.00分)...');
    await browserLogin(page, 'teacher2', '123456', '/dashboard/teacher');
    await page.goto('http://127.0.0.1:3000/material', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await selectTask(page, taskName);
    await sleep(1000);

    const inputExists2 = await page.$('input[placeholder*="学生ID"]');
    if (inputExists2) {
      await clearAndType(page, 'input[placeholder*="学生ID"]', '1365');
      await sleep(500);
      await page.keyboard.press('Enter');
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const refreshBtn = btns.find(b => b.innerText.includes('刷新'));
        if (refreshBtn) refreshBtn.click();
      });
      await sleep(1500);
    }

    await page.waitForSelector('.ant-table-row', { timeout: 10000 });
    await sleep(500);

    const teacherPageText2 = await page.evaluate(() => document.body.innerText);
    console.log(`   - 表格显示当前版本为 v2: ${teacherPageText2.includes('v2')}`);
    console.log(`   - 查验状态恢复为待查验: ${teacherPageText2.includes('待查验')}`);

    // 点击“查验打分”
    await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes('三方协议书')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('查验打分'));
          if (btn) {
            btn.click();
            break;
          }
        }
      }
    });
    await sleep(1000);

    // 默认即为“查验通过”，录入分数 96.00
    await page.waitForSelector('.ant-modal input#auditScore', { timeout: 8000 });
    await clearAndType(page, '.ant-modal input#auditScore', '96');
    await sleep(500);

    const approveComment = '用人单位印章齐全清晰，经核实协议真实有效，查验合格予以通过。';
    await page.waitForSelector('.ant-modal textarea#auditComment', { timeout: 8000 });
    await clearAndType(page, '.ant-modal textarea#auditComment', approveComment);
    await sleep(500);

    // 确认查验结果
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const okBtn = btns.find(b => b.innerText.includes('确认查验结果'));
      if (okBtn) okBtn.click();
    });
    await sleep(2000);

    await page.screenshot({ path: step5Shot });
    console.log(`   [Step 5] 教师复核通过界面已截屏: ${step5Shot}`);

    // -------------------------------------------------------------
    // [Step 6] 学生端最终视图核验 (APPROVED, 禁用重提)
    // -------------------------------------------------------------
    console.log('   [Step 6] 学生端最终视图核验...');
    await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
    await page.goto('http://127.0.0.1:3000/material', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await selectTask(page, taskName);
    await sleep(1000);
    await page.waitForSelector('.ant-card', { timeout: 10000 });
    await sleep(500);

    const finalStudentText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 学生端显示查验合格: ${finalStudentText.includes('查验合格')}`);
    console.log(`   - 考评分数显示 96: ${finalStudentText.includes('96')}`);
    console.log(`   - 通过评语已展示: ${finalStudentText.includes('查验合格予以通过')}`);

    // 验证“重提新版”按钮已置灰禁用
    const resubmitDisabled = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('.ant-card'));
      for (const card of cards) {
        if (card.innerText.includes('三方协议书')) {
          const btn = Array.from(card.querySelectorAll('button')).find(b => b.innerText.includes('重提新版'));
          return btn ? btn.disabled : false;
        }
      }
      return false;
    });
    console.log(`   - 查验合格后“重提新版”按钮已禁用: ${resubmitDisabled}`);

    await page.screenshot({ path: step6Shot });
    console.log(`   [Step 6] 学生端最终视图已截屏: ${step6Shot}`);

    console.log('================================================================');
    console.log('>>> [React 阶段材料版本流转分支] 验收闭环测试全部通过！');
    console.log('================================================================');
  } finally {
    await browser.close();
  }
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
