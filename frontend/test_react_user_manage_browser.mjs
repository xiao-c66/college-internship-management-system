import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE_URL = 'http://127.0.0.1:3000';
const API_URL = 'http://127.0.0.1:3000/api/v1';

const ARTIFACT_SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const LOCAL_SCREENSHOT_DIR = path.resolve('test_screenshots_user_manage');

for (const dir of [ARTIFACT_SCREENSHOT_DIR, LOCAL_SCREENSHOT_DIR]) {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

async function saveScreenshot(page, filename) {
  const localPath = path.join(LOCAL_SCREENSHOT_DIR, filename);
  const artifactPath = path.join(ARTIFACT_SCREENSHOT_DIR, filename);
  await page.screenshot({ path: localPath });
  fs.copyFileSync(localPath, artifactPath);
  console.log(`  📸 截图已保存: ${filename}`);
}

async function loginApi(username, password = '123456') {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = await res.json();
  if (data.code !== 200) throw new Error(`API login failed for ${username}: ${JSON.stringify(data)}`);
  return data.data;
}

async function run() {
  console.log('========================================================');
  console.log('🚀 启动 React 教师/学生账号管理页面真实浏览器端到端截屏');
  console.log('========================================================\n');

  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu', '--window-size=1440,900']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const delay = ms => new Promise(r => setTimeout(r, ms));

  // 1. 访问登录页面
  console.log('1. 访问 React 登录页面...');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await delay(1000);

  // 2. 展开忘记密码弹窗
  console.log('2. 展开忘记密码安全找回模态框...');
  const forgotLink = await page.$('button.ant-btn-link');
  if (forgotLink) {
    await forgotLink.click();
    await delay(600);
    await saveScreenshot(page, 'react_forgot_password_modal.png');
    // 关闭模态框
    const cancelBtn = await page.$('.ant-modal-footer button');
    if (cancelBtn) await cancelBtn.click();
    await delay(400);
  }

  // 3. 管理员授权并进入用户管理列表页
  console.log('3. 管理员登录并进入 /users 账号管理列表页面...');
  const adminAuth = await loginApi('admin', '123456');
  await page.evaluate((tok, usr) => {
    localStorage.setItem('token', tok);
    localStorage.setItem('user', JSON.stringify({
      id: usr.userId,
      username: usr.username,
      realName: usr.realName,
      userType: usr.userType,
      roleCode: usr.roleCode,
      deptId: usr.deptId
    }));
    localStorage.setItem('userType', usr.userType);
  }, adminAuth.token, adminAuth);

  await page.goto(`${BASE_URL}/users`, { waitUntil: 'networkidle2' });
  await page.waitForSelector('.ant-table-tbody', { timeout: 10000 });
  await delay(1000);
  await saveScreenshot(page, 'react_user_manage_table.png');

  // 4. 点击“批量导入用户”打开导入抽屉/弹窗
  console.log('4. 点击“批量导入用户”按钮展开导入弹窗...');
  const buttons = await page.$$('button');
  for (const btn of buttons) {
    const text = await page.evaluate(el => el.textContent, btn);
    if (text && text.includes('批量导入')) {
      await btn.click();
      break;
    }
  }
  await page.waitForSelector('.ant-modal-content', { timeout: 5000 });
  await delay(800);
  await saveScreenshot(page, 'react_user_import_modal.png');

  // 5. 首次登录强制改密弹窗截图
  console.log('5. 测试待改密账号登录触发强制改密弹窗...');
  await page.evaluate(() => localStorage.clear());
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await delay(600);

  // 输入临时密码账号
  await page.type('#login_username', 't_syn_01');
  await page.type('#login_password', 'Jy3KsCgVb!');
  const submitBtn = await page.$('button[type="submit"]');
  if (submitBtn) {
    await submitBtn.click();
    await delay(1200);
    await saveScreenshot(page, 'react_must_change_password_login.png');
  }

  await browser.close();
  console.log('\n========================================================');
  console.log('✅ React 用户管理页面真实浏览器截图完成！');
  console.log('========================================================');
}

run().catch(err => {
  console.error('[ERROR]:', err);
  process.exit(1);
});
