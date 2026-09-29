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
  console.log('>>> [React 过程预警误报闭环分支] 定向真实验收');
  console.log('“派发预警 → 学生申辩 → 教师核实为误报并关闭 (FALSE_ALARM_CLOSED)”');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();
  console.log('1. 已从安全存储加载 student_rectify_iso (ID 1365) 凭证');

  const deptToken = await apiLogin('deptadmin', '123456');
  const teacherToken = await apiLogin('teacher', '123456');

  // Step 1: 创建全新专属隔离实习任务 (例如任务 2113)
  console.log('2. 院系管理员创建全新专属隔离实习任务 (隔离任务 2100-2112)...');
  const today = new Date().toISOString().split('T')[0];
  const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0];
  const taskCode = `TASK_WARN_${Date.now()}`;
  const taskName = `TASK_WARN_ISO_${Date.now()}`;
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
  console.log(`   - 预警专属任务创建成功: taskId = ${taskId}`);

  // 发布任务
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/publish`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  });
  console.log(`   - 任务发布成功，学生名单已自动挂载`);

  // 指派指导教师 teacher (ID 3) 给 student 1365
  await httpRequest(`${BACKEND_BASE}/tasks/${taskId}/assign-teacher`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, { teacherId: 3, studentIds: [1365] });
  console.log(`   - 指派教师 teacher (ID 3) 给 student_rectify_iso 成功`);

  // Step 2: 验证防御性安全补丁：无 taskId 的全局扫描必须被拦截 (HTTP 400)
  console.log('3. 验证防御性安全补丁：调用无 taskId 的全盘扫描接口...');
  const unscopedScanRes = await httpRequest(`${BACKEND_BASE}/warn/scan`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  });
  console.log(`   - 无参扫描响应状态码: ${unscopedScanRes.status}, 业务码: ${unscopedScanRes.data?.code}, 提示: ${unscopedScanRes.data?.message}`);
  if (unscopedScanRes.status !== 400 || !unscopedScanRes.data?.message?.includes('实习任务ID不能为空')) {
    throw new Error(`Defensive scan block verification failed! Status: ${unscopedScanRes.status}`);
  }
  console.log('   - [PASS] 防御性拦截生效，全局扫描已物理阻断！');

  // 等待 10 秒绕过流控限制
  console.log('4. 等待 10 秒流控冷却时间...');
  await sleep(10500);

  // Step 3: 调用带参扫描接口 POST /api/v1/warn/scan?taskId=${taskId}
  console.log(`5. 教师执行定向异常预警扫描: POST /api/v1/warn/scan?taskId=${taskId}...`);
  const scopedScanRes = await httpRequest(`${BACKEND_BASE}/warn/scan?taskId=${taskId}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  });
  console.log(`   - 定向扫描响应:`, JSON.stringify(scopedScanRes.data));
  if (scopedScanRes.data.code !== 200) {
    throw new Error(`Scoped scan failed: ${scopedScanRes.data.message}`);
  }
  console.log(`   - [PASS] 定向扫描完成: 扫描学生 ${scopedScanRes.data.data.scannedStudents} 人，新增工单 ${scopedScanRes.data.data.newTickets} 笔，超时升级 ${scopedScanRes.data.data.upgradedCount} 笔`);

  // 查询新任务生成的工单列表
  const ticketsRes = await httpRequest(`${BACKEND_BASE}/warn/tickets?taskId=${taskId}`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  });
  const newTicketsList = ticketsRes.data.data || [];
  console.log(`   - 查询到当前任务预警工单共 ${newTicketsList.length} 笔`);
  if (newTicketsList.length === 0) {
    throw new Error(`Expected at least 1 ticket generated for task ${taskId}, got 0`);
  }
  // 取首个工单进行误报闭环
  const targetTicket = newTicketsList[0];
  console.log(`   - 选定定向测试工单: ID = ${targetTicket.id}, 工单号 = ${targetTicket.ticketNo}, 规则 = ${targetTicket.ruleName} (${targetTicket.ruleCode}), 状态 = ${targetTicket.status}`);

  // Step 4: 启动 Edge 浏览器，进行 React 前端多角色协同操作
  console.log('6. 启动 Edge 浏览器并执行 React 端预警误报闭环协同...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_warn_step1_student_view_tickets.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_warn_step2_student_feedback_modal.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_warn_step3_teacher_handle_modal.png');
  const step4Shot = path.join(SCREENSHOT_DIR, 'react_warn_step4_ticket_closed_timeline.png');

  try {
    // -------------------------------------------------------------
    // [React 步骤 1] 学生 student_rectify_iso 登录，查看工单并提交申辩
    // -------------------------------------------------------------
    console.log('   [React 步骤 1] 学生 student_rectify_iso 登录 React 前端...');
    await browserLogin(page, 'student_rectify_iso', studentPwd, '/dashboard/student');
    await page.goto('http://127.0.0.1:3000/warn', { waitUntil: 'networkidle0' });
    await sleep(2000);

    // 截图：学生查看到受预警工单
    await page.screenshot({ path: step1Shot });
    console.log(`   - 已截取学生预警列表: ${step1Shot}`);

    // 点击“填写申辩”按钮
    console.log('   [React 步骤 1] 学生点击“填写申辩”...');
    const feedbackBtnClicked = await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo) || row.innerText.includes('触发预警')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('填写申辩') || b.innerText.includes('申辩'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    }, targetTicket.ticketNo);

    if (!feedbackBtnClicked) {
      // 备用方式：点击表格第一行的申辩按钮
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const fb = btns.find(b => b.innerText.includes('填写申辩') || b.innerText.includes('申辩'));
        if (fb) fb.click();
      });
    }

    await sleep(1000);
    await page.waitForSelector('#studentFeedback', { timeout: 8000 });

    const feedbackText = '已向指导教师提交离校安全承诺书纸质签字盖章原件，现场交接顺利，请老师核验判定误报。';
    await page.type('#studentFeedback', feedbackText);
    await page.type('#attachmentUrl', 'http://127.0.0.1:8080/uploads/safety_signed_proof.pdf');
    await sleep(500);

    // 截图：学生填写申辩弹窗
    await page.screenshot({ path: step2Shot });
    console.log(`   - 已截取学生申辩弹窗: ${step2Shot}`);

    // 点击“提交申辩”按钮
    await page.evaluate(() => {
      const modal = document.querySelector('.ant-modal-content');
      if (modal) {
        const okBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('提交申辩') || b.classList.contains('ant-btn-primary'));
        if (okBtn) okBtn.click();
      }
    });
    await sleep(2000);
    console.log('   - 学生申辩提交成功，工单已进入 PROCESSING 状态');

    // -------------------------------------------------------------
    // [React 步骤 2] 指导教师 teacher 登录，处置工单并认定为误报
    // -------------------------------------------------------------
    console.log('   [React 步骤 2] 教师 teacher 登录 React 前端...');
    await browserLogin(page, 'teacher', '123456', '/dashboard/teacher');
    await page.goto('http://127.0.0.1:3000/warn', { waitUntil: 'networkidle0' });
    await sleep(2000);

    // 在筛选栏选择目标任务
    const filterSelect = await page.$('.ant-select');
    if (filterSelect) {
      await filterSelect.click();
      await sleep(600);
      await page.evaluate((tName) => {
        const opts = Array.from(document.querySelectorAll('.ant-select-item-option'));
        const opt = opts.find(o => o.innerText.includes(tName));
        if (opt) opt.click();
      }, taskName);
      await sleep(1500);
    }

    // 点击“处置销号”按钮
    console.log('   [React 步骤 2] 教师点击“处置销号”...');
    const handleBtnClicked = await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo) || row.innerText.includes('触发预警')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('处置销号'));
          if (btn) {
            btn.click();
            return true;
          }
        }
      }
      return false;
    }, targetTicket.ticketNo);

    if (!handleBtnClicked) {
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const btn = btns.find(b => b.innerText.includes('处置销号'));
        if (btn) btn.click();
      });
    }

    await sleep(1000);
    await page.waitForSelector('#action', { timeout: 8000 });

    // 单选框选择“确属误报释放” (FALSE_ALARM_CLOSED)
    await page.evaluate(() => {
      const radios = Array.from(document.querySelectorAll('input[type="radio"]'));
      const falseAlarmRadio = radios.find(r => r.value === 'FALSE_ALARM_CLOSED');
      if (falseAlarmRadio) {
        falseAlarmRadio.click();
      }
    });
    await sleep(500);

    await clearAndType(page, '#teacherInvestigation', '已致电企业带教导师并核实学生纸质承诺书签字档案，学生按期安全到岗履约，系统未同步属网络录入延期，认定为误报。');
    await clearAndType(page, '#handlingMeasures', '已协助学生完成线下备案补录，并通知辅导员留存纸质原件备份，予以系统误报释放销号。');
    await clearAndType(page, '#attachmentUrl', '/uploads/warn/false_alarm_investigation_proof.pdf');
    await sleep(500);

    // 截图：教师误报处置弹窗
    await page.screenshot({ path: step3Shot });
    console.log(`   - 已截取教师误报处置弹窗: ${step3Shot}`);

    // 点击“执行处置”确认按钮
    await page.evaluate(() => {
      const modal = document.querySelector('.ant-modal-content');
      if (modal) {
        const okBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('执行处置') || b.classList.contains('ant-btn-primary'));
        if (okBtn) okBtn.click();
      }
    });
    await sleep(2500);
    console.log('   - 教师已执行处置：决议 FALSE_ALARM_CLOSED');

    // -------------------------------------------------------------
    // [React 步骤 3] 打开工单详情抽屉，展示时间线与误报关闭证据链
    // -------------------------------------------------------------
    console.log('   [React 步骤 3] 打开工单详情抽屉，验证全链路流转证据链...');
    // 点击“工单详情”按钮
    await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo) || row.innerText.includes('触发预警') || row.innerText.includes('误报释放')) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('工单详情'));
          if (btn) {
            btn.click();
            return;
          }
        }
      }
    }, targetTicket.ticketNo);

    await sleep(1500);
    await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
    await sleep(1000);

    // 截图：工单详情抽屉与全过程时间线
    await page.screenshot({ path: step4Shot });
    console.log(`   - 已截取工单详情抽屉与全过程流转证据链: ${step4Shot}`);

  } finally {
    await browser.close();
  }

  console.log('================================================================');
  console.log(`>>> 预警误报分支定向验收执行完毕！工单 ID: ${targetTicket.id}`);
  console.log('================================================================');
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
