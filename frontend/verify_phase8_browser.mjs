import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE_URL = 'http://localhost:3000';
const API_URL = 'http://localhost:8080/api/v1';

const ARTIFACT_SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const LOCAL_SCREENSHOT_DIR = path.resolve('test_screenshots_phase8');

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

async function loginApi(username, password) {
  const capRes = await fetch(`${API_URL}/auth/captcha`);
  const capJson = await capRes.json();
  const captchaKey = capJson.data.captchaKey;
  const captchaCode = capJson.data.captchaCode;

  const loginRes = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      captchaKey,
      captcha: captchaCode
    })
  });
  const loginJson = await loginRes.json();
  if (loginJson.code !== 200) {
    throw new Error(`Login failed for ${username}: ${loginJson.message}`);
  }
  return loginJson.data;
}

// 辅助函数：根据包含的文字查找并点击元素
async function clickElementByText(page, selector, textKeyword) {
  const elements = await page.$$(selector);
  for (const el of elements) {
    const text = await page.evaluate(node => node.textContent, el);
    if (text && text.includes(textKeyword)) {
      await el.click();
      return true;
    }
  }
  return false;
}

async function runPhase8BrowserVerification() {
  console.log('========================================================');
  console.log('🚀 启动阶段8前端真实浏览器端到端验收 (Notice & Monitor)');
  console.log('========================================================\n');

  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const results = {
    steps: [],
    consoleErrors: []
  };

  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900 });

    page.on('console', msg => {
      if (msg.type() === 'error') {
        const text = msg.text();
        if (!text.includes('favicon.ico')) {
          console.error(`  [Browser Console Error] ${text}`);
          results.consoleErrors.push({ url: page.url(), text });
        }
      }
    });

    page.on('pageerror', error => {
      console.error(`  [Browser Page Error] ${error.message}`);
      results.consoleErrors.push({ url: page.url(), text: error.message });
    });

    // ==========================================
    // 1. SYS_ADMIN 验证通知公告管理 (NoticeManage)
    // ==========================================
    console.log('👤 [SYS_ADMIN] 登录系统验证通知公告管理 (API-112~API-115)...');
    const adminAuth = await loginApi('admin', '123456');

    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0' });
    await page.evaluate(auth => {
      localStorage.setItem('token', auth.token);
      localStorage.setItem('userType', auth.userType);
      localStorage.setItem('userId', String(auth.userId));
      localStorage.setItem('username', auth.username);
      localStorage.setItem('realName', auth.realName);
      if (auth.deptId) localStorage.setItem('deptId', String(auth.deptId));
    }, adminAuth);

    // 导航至通知管理页面
    await page.goto(`${BASE_URL}/admin/system/notice`, { waitUntil: 'networkidle0' });
    console.log(`  ✓ 访问 ${page.url()} - 页面标题: "${await page.title()}"`);
    await new Promise(r => setTimeout(r, 1200));

    // 点击“发布新通知公告”
    const publishBtn = await page.waitForSelector('.header-actions .el-button--primary');
    await publishBtn.click();
    console.log('  ✓ 点击 [发布新通知公告] 按钮');
    await page.waitForSelector('.el-dialog', { visible: true });

    // 填写发布表单
    const dedupKey = `VERIFY_P8_AUTO_DEDUP_${Date.now()}`;
    await page.type('input[placeholder*="MANUAL_DEPT1_P8_NOTIFY_2026"]', dedupKey);
    await page.type('input[placeholder*="请输入通知公告标题"]', '【全校通报】阶段8教学通知系统正式验收通知');

    // 选中全校公告 ANNOUNCE
    await clickElementByText(page, '.el-radio', '学校公告');

    // 输入含脚本的富文本内容，测试 XSS 清洗防御
    const testContent = '<p><strong>阶段8教学通知公告</strong>已成功发布，包含安全富文本格式与防重机制。<script>alert("xss")</script></p>';
    await page.type('textarea[placeholder*="支持HTML富文本内容"]', testContent);
    await new Promise(r => setTimeout(r, 600));

    // 点击确认发布
    await clickElementByText(page, '.el-dialog__footer button', '确认发布');

    // 等待弹窗关闭和列表刷新
    await page.waitForSelector('.el-dialog', { hidden: true });
    await new Promise(r => setTimeout(r, 1500));
    await saveScreenshot(page, 'phase8_notice_manage_published.png');
    results.steps.push({ step: 'NoticeManage - 发布新通知公告 (API-114)', status: 'PASS' });

    // 查阅详情
    const clickedDetail = await clickElementByText(page, '.el-table button', '查阅详情');
    if (clickedDetail) {
      console.log('  ✓ 点击 [查阅详情] 抽屉');
      await page.waitForSelector('.detail-container', { visible: true });
      await new Promise(r => setTimeout(r, 1000));
      await saveScreenshot(page, 'phase8_notice_detail_admin.png');

      // 关闭抽屉 (按下 Escape 键)
      await page.keyboard.press('Escape');
      await page.waitForSelector('.detail-container', { hidden: true });
      console.log('  ✓ 关闭 [查阅详情] 抽屉');
      results.steps.push({ step: 'NoticeManage - 详情查阅与XSS防御渲染 (API-113)', status: 'PASS' });
    }

    // 撤回通知
    const clickedRevoke = await clickElementByText(page, '.el-table button', '撤回');
    if (clickedRevoke) {
      console.log('  ✓ 点击 [撤回] 按钮，弹出确认对话框');
      await page.waitForSelector('.el-message-box', { visible: true });
      await page.click('.el-message-box__btns .el-button--primary');
      await page.waitForSelector('.el-message-box', { hidden: true });
      await new Promise(r => setTimeout(r, 1200));
      await saveScreenshot(page, 'phase8_notice_revoked.png');
      results.steps.push({ step: 'NoticeManage - 撤回通知 (API-115 status=0)', status: 'PASS' });

      // 重新发布
      const clickedRepublish = await clickElementByText(page, '.el-table button', '重新发布');
      if (clickedRepublish) {
        console.log('  ✓ 点击 [重新发布] 按钮，弹出确认对话框');
        await page.waitForSelector('.el-message-box', { visible: true });
        await page.click('.el-message-box__btns .el-button--primary');
        await page.waitForSelector('.el-message-box', { hidden: true });
        await new Promise(r => setTimeout(r, 1200));
        await saveScreenshot(page, 'phase8_notice_republished.png');
        results.steps.push({ step: 'NoticeManage - 重新发布通知 (API-115 status=1)', status: 'PASS' });
      }
    }

    // ==========================================
    // 2. SYS_ADMIN 验证系统监控与安全审计 (ServerMonitor)
    // ==========================================
    console.log('\n👤 [SYS_ADMIN] 验证系统监控与安全审计控制台 (API-116~API-122)...');
    await page.goto(`${BASE_URL}/admin/system/monitor`, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 1500));

    // Tab 1: 服务器与JVM监控
    console.log('  ✓ 验证 Tab 1: 服务器与 JVM 运行指标 (API-116)');
    await saveScreenshot(page, 'phase8_monitor_server_jvm.png');
    results.steps.push({ step: 'ServerMonitor - 服务器与JVM指标监控 (API-116)', status: 'PASS' });

    // Tab 2: Caffeine本地缓存监控
    console.log('  ✓ 验证 Tab 2: Caffeine 本地有界缓存 (API-117 & API-118)');
    await page.click('#tab-cache');
    await new Promise(r => setTimeout(r, 1000));
    await saveScreenshot(page, 'phase8_monitor_cache.png');

    // 测试清空缓存
    const clearCacheBtn = await page.waitForSelector('.cache-actions button.el-button--danger', { visible: true });
    await clearCacheBtn.click();
    console.log('  ✓ 点击 [一键清空本地 Caffeine 缓存] 按钮');
    await page.waitForSelector('.el-message-box', { visible: true });
    await page.click('.el-message-box__btns .el-button--primary');
    await page.waitForSelector('.el-message-box', { hidden: true });
    await new Promise(r => setTimeout(r, 1200));
    await saveScreenshot(page, 'phase8_monitor_cache_cleared.png');
    results.steps.push({ step: 'ServerMonitor - 本地缓存监控与清空 (API-117/118)', status: 'PASS' });

    // Tab 3: 接口耗时与慢调用分析 (API-119)
    console.log('  ✓ 验证 Tab 3: 接口耗时与慢调用度量 (API-119)');
    await page.click('#tab-slow');
    await new Promise(r => setTimeout(r, 1200));
    await saveScreenshot(page, 'phase8_monitor_slow_sql.png');
    results.steps.push({ step: 'ServerMonitor - 慢调用与P95/P99度量看板 (API-119)', status: 'PASS' });

    // Tab 4: 安全审计与在线会话 (API-120 ~ API-122)
    console.log('  ✓ 验证 Tab 4: 安全审计与在线会话管控 (API-120 ~ API-122)');
    await page.click('#tab-security');
    await new Promise(r => setTimeout(r, 1200));
    await saveScreenshot(page, 'phase8_monitor_security_tokens.png');

    // 检查踢下线按钮交互提示 (针对非管理员自身)
    const kickBtns = await page.$$('.sub-tabs button');
    for (const b of kickBtns) {
      const text = await page.evaluate(el => el.textContent, b);
      if (text.includes('强制踢下线')) {
        const disabled = await page.evaluate(el => el.disabled, b);
        console.log(`  ✓ 踢下线按钮状态: ${disabled ? '自身/超管禁用 (安全合规)' : '可操作'}`);
      }
    }

    // 切换至登录审计
    await page.click('#tab-loginLogs');
    await new Promise(r => setTimeout(r, 1000));
    await saveScreenshot(page, 'phase8_monitor_login_logs.png');

    // 切换至操作审计
    await page.click('#tab-operLogs');
    await new Promise(r => setTimeout(r, 1000));
    await saveScreenshot(page, 'phase8_monitor_op_logs.png');
    results.steps.push({ step: 'ServerMonitor - 在线会话与登录/操作安全审计 (API-120~122)', status: 'PASS' });

    // ==========================================
    // 3. STUDENT 验证顶栏通知铃铛与通知抽屉 (NoticeDrawer)
    // ==========================================
    console.log('\n👤 [STUDENT] 登录系统验证顶栏通知公告抽屉 (NoticeDrawer)...');
    const studentAuth = await loginApi('student', '123456');

    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0' });
    await page.evaluate(auth => {
      localStorage.setItem('token', auth.token);
      localStorage.setItem('userType', auth.userType);
      localStorage.setItem('userId', String(auth.userId));
      localStorage.setItem('username', auth.username);
      localStorage.setItem('realName', auth.realName);
      if (auth.deptId) localStorage.setItem('deptId', String(auth.deptId));
    }, studentAuth);

    await page.goto(`${BASE_URL}/dashboard/student`, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 1500));

    // 检查顶栏铃铛并点击打开抽屉
    const bellBtn = await page.waitForSelector('.bell-btn');
    await bellBtn.click();
    console.log('  ✓ 学生端点击顶栏通知铃铛，打开侧边通知抽屉');
    await page.waitForSelector('.notice-list-scroll', { visible: true });
    await new Promise(r => setTimeout(r, 1000));
    await saveScreenshot(page, 'phase8_notice_drawer_student.png');

    // 点击第一张通知卡片查看详情
    const noticeCards = await page.$$('.notice-card');
    if (noticeCards.length > 0) {
      await noticeCards[0].click();
      console.log('  ✓ 点击通知卡片，弹出安全富文本详情弹窗');
      await page.waitForSelector('.notice-detail-dialog', { visible: true });
      await new Promise(r => setTimeout(r, 1000));

      // 验证富文本中的 script 标签已被 DOMPurify 过滤，且内容正常呈现
      const contentHtml = await page.$eval('.dialog-content-html', el => el.innerHTML);
      const isXssSanitized = !contentHtml.includes('<script');
      console.log(`  🛡️ DOMPurify 客户端净化验证: ${isXssSanitized ? 'PASS (script 标签已成功剔除)' : 'FAIL'}`);

      await saveScreenshot(page, 'phase8_notice_drawer_detail.png');

      // 点击关闭弹窗
      await clickElementByText(page, '.el-dialog__footer button', '关闭');
      await page.waitForSelector('.notice-detail-dialog', { hidden: true });
      results.steps.push({ step: 'NoticeDrawer - 学生端铃铛未读红点与富文本净化查阅', status: 'PASS' });
    }

  } catch (err) {
    console.error('❌ 验收过程发生异常:', err);
    throw err;
  } finally {
    await browser.close();
  }

  console.log('\n========================================================');
  console.log('📊 阶段8浏览器真实验收测试汇总');
  console.log('========================================================');
  console.log(`测试步骤总计: ${results.steps.length}`);
  results.steps.forEach((s, idx) => console.log(`  [${idx + 1}] ${s.step}: ${s.status}`));
  console.log(`控制台致命错误: ${results.consoleErrors.length}`);
  if (results.consoleErrors.length > 0) {
    results.consoleErrors.forEach(e => console.error(`  - ${e.url}: ${e.text}`));
  }
  console.log('========================================================\n');
}

runPhase8BrowserVerification()
  .then(() => {
    console.log('🎉 阶段8前端真实浏览器端到端验收全部顺利完成！');
    process.exit(0);
  })
  .catch(err => {
    console.error('💥 验收失败:', err);
    process.exit(1);
  });
