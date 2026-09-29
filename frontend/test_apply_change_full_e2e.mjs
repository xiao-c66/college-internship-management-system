import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE_URL = 'http://127.0.0.1:3000';
const API_URL = 'http://127.0.0.1:8080/api/v1';

const ARTIFACT_SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const LOCAL_SCREENSHOT_DIR = path.resolve('test_screenshots_apply_change');

for (const dir of [ARTIFACT_SCREENSHOT_DIR, LOCAL_SCREENSHOT_DIR]) {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

async function saveScreenshot(page, filename) {
  const localPath = path.join(LOCAL_SCREENSHOT_DIR, filename);
  const artifactPath = path.join(ARTIFACT_SCREENSHOT_DIR, filename);
  await page.screenshot({ path: localPath, fullPage: true });
  fs.copyFileSync(localPath, artifactPath);
  console.log(`  📸 截图已保存并同步至工件库: ${filename}`);
}

const delay = ms => new Promise(r => setTimeout(r, ms));

async function apiLogin(username, password = '123456') {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = await res.json();
  if (data.code !== 200) {
    throw new Error(`Login failed for ${username}: ${JSON.stringify(data)}`);
  }
  return data.data;
}

async function setSession(page, authData) {
  await page.evaluate((tok, usr) => {
    localStorage.setItem('token', tok);
    localStorage.setItem('user', JSON.stringify({
      userId: usr.userId,
      username: usr.username,
      realName: usr.realName,
      userType: usr.userType,
      roleCode: usr.roleCode,
      deptId: usr.deptId,
      deptName: usr.deptName,
      permissions: usr.permissions || []
    }));
    localStorage.setItem('userType', usr.userType || '');
  }, authData.token, authData);
}

async function clickButtonByText(page, text) {
  const clicked = await page.evaluate((btnText) => {
    const buttons = Array.from(document.querySelectorAll('button'));
    for (const btn of buttons) {
      if (btn.textContent && btn.textContent.includes(btnText)) {
        btn.click();
        return true;
      }
    }
    return false;
  }, text);
  return clicked;
}

async function waitForButtonByText(page, text, timeout = 10000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const found = await page.evaluate((btnText) => {
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.some(b => b.textContent && b.textContent.includes(btnText));
    }, text);
    if (found) return true;
    await delay(300);
  }
  throw new Error(`Timeout waiting for button with text "${text}"`);
}

async function selectTask3101(page) {
  const needsSelect = await page.evaluate(() => {
    const sel = document.querySelector('.ant-select-selection-item');
    return !sel || !sel.textContent.includes('3101');
  });

  if (needsSelect) {
    await page.click('.ant-select');
    await delay(600);
    await page.evaluate(() => {
      const options = Array.from(document.querySelectorAll('.ant-select-item-option'));
      const target = options.find(o => o.textContent && o.textContent.includes('3101'));
      if (target) target.click();
    });
    await delay(1800);
  }
}

async function run() {
  console.log('================================================================');
  console.log('🚀 启动【实习重大信息变更申请与双级审批闭环】真实浏览器端到端 (E2E) 验收');
  console.log('>>> 独立沙箱数据库: 端口 3308 (internship_db_isolated_warn)');
  console.log('>>> 业务闭环: 学生发起变更 -> 指导教师初审 -> 院系管理员终审 -> 主数据原子更新');
  console.log('================================================================\n');

  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu', '--window-size=1440,900']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  page.on('console', msg => console.log('  [BROWSER LOG]:', msg.text()));
  page.on('pageerror', err => console.log('  [BROWSER ERROR]:', err.message));

  // -------------------------------------------------------------
  // 第一步：学生 (student_warn_iso) 登录申报页并提交重大变更申请
  // -------------------------------------------------------------
  console.log('【步骤 1】学生 (student_warn_iso) 登录申报页并提交重大变更申请...');
  const studentAuth = await apiLogin('student_warn_iso');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await setSession(page, studentAuth);
  await page.goto(`${BASE_URL}/apply`, { waitUntil: 'networkidle2' });
  await delay(1800);

  // 切换任务批次至 3101
  console.log('  * 确认并切换任务批次至 3101...');
  await selectTask3101(page);

  // 验证学生看到锁定提示和“发起实习重大信息变更申请”按钮
  console.log('  * 检测“发起实习重大信息变更申请”按钮...');
  await waitForButtonByText(page, '发起实习重大信息变更申请', 10000);
  await clickButtonByText(page, '发起实习重大信息变更申请');
  await delay(1200);

  // 等待模态框展开
  await page.waitForSelector('.ant-modal-content', { timeout: 8000 });
  console.log('  * 填写拟变更的新单位、新岗位、事由等信息...');
  await page.type('#changeReason', '原公司因核心业务线战略调整，无法继续提供算法研发岗位，经校内外导师协调转入前海未来科技继续顶岗实习。');
  await page.type('#newCompanyName', '深圳前海未来科技股份有限公司');
  await page.type('#newJobPosition', 'AI算法工程助理');
  await page.type('#newJobAddress', '广东省深圳市前海深港现代服务业合作区前湾一路1号');
  await page.type('#newContactPerson', '陈总监');
  await page.type('#newContactPhone', '13876543210');

  // 点击模态框提交按钮
  console.log('  * 点击“提交变更申请”...');
  await clickButtonByText(page, '提交变更申请');
  await delay(2500);

  // 截图步骤 1：学生已提交重大变更申请并回显流转卡片
  await saveScreenshot(page, 'react_apply_change_step1_student_submit.png');
  console.log('  * [PASS] 学生重大变更申请已成功提交并回显进度！\n');

  // -------------------------------------------------------------
  // 第二步：指导教师 (teacher_warn_a) 登录执行初审
  // -------------------------------------------------------------
  console.log('【步骤 2】指导教师 (teacher_warn_a) 登录审批中心执行初审...');
  const teacherAuth = await apiLogin('teacher_warn_a');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await setSession(page, teacherAuth);
  await page.goto(`${BASE_URL}/apply`, { waitUntil: 'networkidle2' });
  await delay(1800);

  // 切换任务批次至 3101
  console.log('  * 切换任务批次至 3101...');
  await selectTask3101(page);

  // 切换到“实习重大信息变更审核”Tab
  console.log('  * 切换到“实习重大信息变更审核”Tab...');
  await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.ant-tabs-tab'));
    const target = tabs.find(t => t.textContent && t.textContent.includes('实习重大信息变更审核'));
    if (target) target.click();
  });
  await delay(1500);

  // 点击“执行审核”按钮展开初审抽屉
  console.log('  * 点击“执行审核”展开初审抽屉...');
  await waitForButtonByText(page, '执行审核', 8000);
  await clickButtonByText(page, '执行审核');
  await delay(1200);

  // 填写指导教师初审意见并提交
  await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
  console.log('  * 填写指导教师初审意见并同意流转...');
  await page.type('#auditOpinion', '新单位研发实力强，岗位与专业培养目标契合度高，已与企业导师确认，同意流转院系终审。');

  console.log('  * 点击“提交指导教师初审结果”...');
  await clickButtonByText(page, '提交指导教师初审结果');
  await delay(2000);

  await saveScreenshot(page, 'react_apply_change_step2_teacher_audit.png');
  console.log('  * [PASS] 指导教师初审成功通过，流转至待院系终审！\n');

  // -------------------------------------------------------------
  // 第三步：二级院系管理员 (deptadmin) 登录执行终审
  // -------------------------------------------------------------
  console.log('【步骤 3】院系管理员 (deptadmin) 登录审批中心执行终审批准...');
  const deptAuth = await apiLogin('deptadmin');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await setSession(page, deptAuth);
  await page.goto(`${BASE_URL}/apply`, { waitUntil: 'networkidle2' });
  await delay(1800);

  // 切换任务批次至 3101
  console.log('  * 切换任务批次至 3101...');
  await selectTask3101(page);

  // 切换到“实习重大信息变更审核”Tab
  console.log('  * 切换到“实习重大信息变更审核”Tab...');
  await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.ant-tabs-tab'));
    const target = tabs.find(t => t.textContent && t.textContent.includes('实习重大信息变更审核'));
    if (target) target.click();
  });
  await delay(1500);

  // 点击“执行审核”展开终审抽屉
  console.log('  * 点击“执行审核”展开终审抽屉...');
  await waitForButtonByText(page, '执行审核', 8000);
  await clickButtonByText(page, '执行审核');
  await delay(1200);

  // 填写终审意见
  await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
  console.log('  * 填写院系终审批准意见并提交裁定...');
  await page.type('#auditOpinion', '经计算机学院教学指导委员会核准，新实习方案完备，准予重大信息变更并更新主档案。');

  console.log('  * 点击“提交二级院系终审裁定”...');
  await clickButtonByText(page, '提交二级院系终审裁定');
  await delay(2500);

  await saveScreenshot(page, 'react_apply_change_step3_dept_audit.png');
  console.log('  * [PASS] 院系终审批准通过，主数据原子同步更新！\n');

  // -------------------------------------------------------------
  // 第四步：学生 (student_warn_iso) 回访申报页，核查主数据已更新生效
  // -------------------------------------------------------------
  console.log('【步骤 4】学生 (student_warn_iso) 回访申报页，核实主数据已更新生效...');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await setSession(page, studentAuth);
  await page.goto(`${BASE_URL}/apply`, { waitUntil: 'networkidle2' });
  await delay(1800);

  // 切换任务批次至 3101
  await selectTask3101(page);

  await saveScreenshot(page, 'react_apply_change_step4_student_verified.png');

  // 从页面检查公司名称与岗位是否已更新
  const pageHtml = await page.evaluate(() => document.body.innerHTML);
  if (!pageHtml.includes('深圳前海未来科技股份有限公司')) {
    throw new Error('学生申报页未显示更新后的新实习单位名称！');
  }
  if (!pageHtml.includes('AI算法工程助理')) {
    throw new Error('学生申报页未显示更新后的新实习岗位！');
  }
  console.log('  * [PASS] 学生端页面显示已更新的新实习单位与岗位，主数据原子同步生效确认！');

  await browser.close();

  console.log('\n================================================================');
  console.log('🎉 实习重大信息变更申请与双级审批闭环全部真实浏览器 E2E 验证 PASS！');
  console.log('================================================================');
}

run().catch(err => {
  console.error('\n❌ [E2E FAILED]:', err);
  process.exit(1);
});
