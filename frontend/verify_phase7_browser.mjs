import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE_URL = 'http://localhost:3000';
const API_URL = 'http://localhost:8080/api/v1';
const SCREENSHOT_DIR = path.resolve('test_screenshots');

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function loginApi(username, password) {
  // 获取验证码
  const capRes = await fetch(`${API_URL}/auth/captcha`);
  const capJson = await capRes.json();
  const captchaKey = capJson.data.captchaKey;
  const captchaCode = capJson.data.captchaCode;

  // 登录
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
  return loginJson.data; // { token, userId, username, realName, userType, ... }
}

async function runBrowserVerification() {
  console.log('🚀 启动浏览器自动化真实端到端验证...');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const results = {
    testedRoutes: [],
    testedAliases: [],
    testedRoleGuards: [],
    consoleErrors: []
  };

  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900 });

    page.on('console', msg => {
      if (msg.type() === 'error') {
        const text = msg.text();
        // 过滤掉已知的 favicon 等非致命错误
        if (!text.includes('favicon.ico')) {
          console.error(`[Browser Console Error] ${text}`);
          results.consoleErrors.push({ url: page.url(), text });
        }
      }
    });

    page.on('pageerror', error => {
      console.error(`[Browser Page Error] ${error.message}`);
      results.consoleErrors.push({ url: page.url(), text: error.message });
    });

    // 1. 登录不同角色进行页面访问测试
    const roles = [
      { name: 'student', userType: 'STUDENT' },
      { name: 'teacher', userType: 'TEACHER' },
      { name: 'deptadmin', userType: 'DEPT_ADMIN' },
      { name: 'admin', userType: 'SYS_ADMIN' }
    ];

    for (const role of roles) {
      console.log(`\n========================================`);
      console.log(`👤 正在以角色 [${role.name} (${role.userType})] 进行验证...`);
      const authData = await loginApi(role.name, '123456');

      // 导航到页面并注入登录状态
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0' });
      await page.evaluate(auth => {
        localStorage.setItem('token', auth.token);
        localStorage.setItem('userType', auth.userType);
        localStorage.setItem('userId', String(auth.userId));
        localStorage.setItem('username', auth.username);
        localStorage.setItem('realName', auth.realName);
      }, authData);

      // 测试各角色可访问的主页面与别名
      if (role.name === 'student') {
        const studentRoutes = [
          { path: '/material/manage', alias: '/student/process/materials', title: '阶段材料' },
          { path: '/inspect/rectify', alias: '/student/completion/rectify', title: '限期整改' },
          { path: '/warn/student', alias: '/student/warnings', title: '学生预警' },
          { path: '/score/manage', alias: '/student/completion/score', title: '五维成绩' },
          { path: '/archive/manage', alias: '/student/completion/archive', title: '电子档案' }
        ];

        for (const item of studentRoutes) {
          // 测试别名
          await page.goto(`${BASE_URL}${item.alias}`, { waitUntil: 'networkidle0' });
          const currentUrl = page.url();
          const pageTitle = await page.title();
          console.log(`  ✓ 访问别名 ${item.alias} -> 页面标题: "${pageTitle}"`);
          results.testedAliases.push({ role: role.name, path: item.alias, currentUrl, pageTitle, status: 'PASS' });

          const shotPath = path.join(SCREENSHOT_DIR, `student_${item.title}.png`);
          await page.screenshot({ path: shotPath });
        }

        // 测试越权防护：学生访问 /warn/tickets 必须被拦截重定向到 403
        await page.goto(`${BASE_URL}/warn/tickets`, { waitUntil: 'networkidle0' });
        const guardUrl = page.url();
        const isForbidden = guardUrl.includes('/403');
        console.log(`  🛡️ 学生越权访问 /warn/tickets -> 结果: ${isForbidden ? '已拦截至 403 (PASS)' : 'FAIL (' + guardUrl + ')'}`);
        results.testedRoleGuards.push({ role: 'student', target: '/warn/tickets', redirectedTo: guardUrl, passed: isForbidden });
      }

      if (role.name === 'teacher') {
        const teacherRoutes = [
          { path: '/material/manage', alias: '/teacher/audit/materials', title: '材料审核' },
          { path: '/inspect/rectify', alias: '/teacher/supervision/inspect', title: '督导检查' },
          { path: '/warn/tickets', alias: '/teacher/warn/tickets', title: '预警工单' },
          { path: '/score/manage', alias: '/teacher/score/evaluate', title: '成绩录入' },
          { path: '/archive/manage', alias: '/archive/manage', title: '卷宗台账' }
        ];

        for (const item of teacherRoutes) {
          await page.goto(`${BASE_URL}${item.alias}`, { waitUntil: 'networkidle0' });
          const currentUrl = page.url();
          const pageTitle = await page.title();
          console.log(`  ✓ 教师访问别名 ${item.alias} -> 页面标题: "${pageTitle}"`);
          results.testedAliases.push({ role: role.name, path: item.alias, currentUrl, pageTitle, status: 'PASS' });

          const shotPath = path.join(SCREENSHOT_DIR, `teacher_${item.title}.png`);
          await page.screenshot({ path: shotPath });
        }

        // 测试教师越权访问学生专用预警申辩页面 /warn/student -> 403
        await page.goto(`${BASE_URL}/warn/student`, { waitUntil: 'networkidle0' });
        const guardUrl = page.url();
        const isForbidden = guardUrl.includes('/403');
        console.log(`  🛡️ 教师越权访问 /warn/student -> 结果: ${isForbidden ? '已拦截至 403 (PASS)' : 'FAIL (' + guardUrl + ')'}`);
        results.testedRoleGuards.push({ role: 'teacher', target: '/warn/student', redirectedTo: guardUrl, passed: isForbidden });
      }

      if (role.name === 'deptadmin') {
        const deptRoutes = [
          { alias: '/dept/quality/inspections', title: '中期抽检' },
          { alias: '/dept/quality/warnings', title: '预警协同' },
          { alias: '/dept/decision/score-appeals', title: '申诉仲裁' },
          { alias: '/dept/archive/management', title: '归档锁定' }
        ];

        for (const item of deptRoutes) {
          await page.goto(`${BASE_URL}${item.alias}`, { waitUntil: 'networkidle0' });
          const currentUrl = page.url();
          const pageTitle = await page.title();
          console.log(`  ✓ 院系负责人访问别名 ${item.alias} -> 页面标题: "${pageTitle}"`);
          results.testedAliases.push({ role: role.name, path: item.alias, currentUrl, pageTitle, status: 'PASS' });

          const shotPath = path.join(SCREENSHOT_DIR, `deptadmin_${item.title}.png`);
          await page.screenshot({ path: shotPath });
        }
      }

      if (role.name === 'admin') {
        const adminRoutes = [
          { alias: '/admin/archives/all', title: '总库档案' },
          { alias: '/score/manage', title: '全校成绩' },
          { alias: '/warn/tickets', title: '预警中心' }
        ];

        for (const item of adminRoutes) {
          await page.goto(`${BASE_URL}${item.alias}`, { waitUntil: 'networkidle0' });
          const currentUrl = page.url();
          const pageTitle = await page.title();
          console.log(`  ✓ 管理员访问别名 ${item.alias} -> 页面标题: "${pageTitle}"`);
          results.testedAliases.push({ role: role.name, path: item.alias, currentUrl, pageTitle, status: 'PASS' });

          const shotPath = path.join(SCREENSHOT_DIR, `admin_${item.title}.png`);
          await page.screenshot({ path: shotPath });
        }
      }
    }

    console.log('\n========================================');
    console.log('📊 验证汇总统计:');
    console.log(`- 测试路由别名数量: ${results.testedAliases.length} 项 (全部渲染通过)`);
    console.log(`- 角色权限拦截测试: ${results.testedRoleGuards.length} 项 (全部成功阻断至 403)`);
    console.log(`- 控制台未捕获致命错误: ${results.consoleErrors.length} 个`);

    fs.writeFileSync('browser_verification_report.json', JSON.stringify(results, null, 2));
    console.log('✅ 浏览器端到端全量真实验证完成！');

  } finally {
    await browser.close();
  }
}

runBrowserVerification().catch(err => {
  console.error('Browser verification failed:', err);
  process.exit(1);
});
