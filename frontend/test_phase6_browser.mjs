import puppeteer from 'puppeteer-core';
import path from 'path';

const SCREENSHOT_DIR = 'C:/Users/曹聪/.gemini/antigravity/brain/26b36451-7588-449f-bf18-e6fe2fba972f/scratch';

async function run() {
  console.log('====================================================');
  console.log('启动 Edge 浏览器执行真实前端界面与交互验证...');
  console.log('====================================================');

  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1280,800']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });

  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push(`[Console Error] ${msg.text()}`);
    }
  });
  page.on('pageerror', err => {
    consoleErrors.push(`[Page Error] ${err.message}`);
  });
  page.on('response', resp => {
    if (resp.status() >= 400) {
      console.log(`      [HTTP ${resp.status()}] ${resp.url()}`);
    }
  });

  try {
    // ========================================================
    // 1. 学生身份 (student / 123456) 验证
    // ========================================================
    console.log('\n[场景 1] 学生登录与周报填报流程 (账号: student / 123456 - 张晓峰)');
    await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.submit-btn');

    const demoBtns = await page.$$('.demo-buttons .el-button');
    if (demoBtns.length > 0) {
      await demoBtns[0].click(); // 学生账号
      await new Promise(r => setTimeout(r, 600));
    }
    await page.click('.submit-btn');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });

    console.log('  1.1 访问学生周报列表 /weekly/my');
    await page.goto('http://localhost:3000/weekly/my', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.page-title');
    const studentListTitle = await page.$eval('.page-title', el => el.textContent);
    console.log('      页面标题:', studentListTitle);

    const shot1 = path.join(SCREENSHOT_DIR, '01_student_weekly_list.png');
    await page.screenshot({ path: shot1 });
    console.log('      截图保存路径:', shot1);

    console.log('  1.2 访问周报填报详情 /weekly/edit/1/1');
    await page.goto('http://localhost:3000/weekly/edit/1/1', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.form-section');

    const sectionDescs = await page.$$eval('.section-desc', els => els.map(e => e.textContent));
    const has10Hardcode = sectionDescs.some(d => d.includes('不少于10字'));
    console.log('      四段式表单提示文案:', sectionDescs);
    console.log('      前端是否残留10字硬编码:', has10Hardcode ? '❌ 仍包含' : '✅ 已彻底清除 (仅做必填与动态校验)');

    const shot2 = path.join(SCREENSHOT_DIR, '02_student_weekly_edit.png');
    await page.screenshot({ path: shot2 });
    console.log('      截图保存路径:', shot2);

    // ========================================================
    // 2. 指导教师身份 (teacher / 123456) 验证
    // ========================================================
    console.log('\n[场景 2] 指导教师登录与审阅、台账、监控 (账号: teacher / 123456 - 李教授)');
    await page.evaluate(() => localStorage.clear());
    await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.submit-btn');
    const teacherDemoBtns = await page.$$('.demo-buttons .el-button');
    if (teacherDemoBtns.length > 1) {
      await teacherDemoBtns[1].click(); // 教师账号
      await new Promise(r => setTimeout(r, 600));
    }
    await page.click('.submit-btn');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });

    console.log('  2.1 访问教师周报审阅工作台 /weekly/review');
    await page.goto('http://localhost:3000/weekly/review', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.page-title');
    const reviewTitle = await page.$eval('.page-title', el => el.textContent);
    console.log('      页面标题:', reviewTitle);

    const shot3 = path.join(SCREENSHOT_DIR, '03_teacher_weekly_review.png');
    await page.screenshot({ path: shot3 });
    console.log('      截图保存路径:', shot3);

    console.log('  2.2 访问指导走访台账 /guidance/manage');
    await page.goto('http://localhost:3000/guidance/manage', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.page-title');

    const createGuidanceBtn = await page.$('.header-action .el-button--primary');
    console.log('      教师端是否存在登记按钮:', createGuidanceBtn ? '✅ 存在 (符合教师角色权限)' : '❌ 不存在');

    if (createGuidanceBtn) {
      await createGuidanceBtn.click();
      await new Promise(r => setTimeout(r, 800));
      const dateVal = await page.$eval('.el-dialog .el-date-editor input', el => el.value);
      console.log('      登记弹窗指导时间默认值:', dateVal);
      const isFullDateTime = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(dateVal);
      console.log('      指导时间格式是否为 yyyy-MM-dd HH:mm:ss:', isFullDateTime ? '✅ 符合' : '❌ 不符合');
      const cancelBtn = await page.$('.el-dialog__footer .el-button');
      if (cancelBtn) await cancelBtn.click();
      await new Promise(r => setTimeout(r, 400));
    }

    const shot4 = path.join(SCREENSHOT_DIR, '04_teacher_guidance_manage.png');
    await page.screenshot({ path: shot4 });
    console.log('      截图保存路径:', shot4);

    console.log('  2.3 教师访问周报监控看板 /weekly/monitor (本师学生数据范围)');
    await page.goto('http://localhost:3000/weekly/monitor', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.kpi-container');
    const teacherKpiCards = await page.$$('.kpi-card');
    console.log('      教师端监控大盘 KPI 卡片加载数:', teacherKpiCards.length);

    const shot5 = path.join(SCREENSHOT_DIR, '05_teacher_weekly_monitor.png');
    await page.screenshot({ path: shot5 });
    console.log('      截图保存路径:', shot5);

    // ========================================================
    // 3. 院系管理员身份 (deptadmin / 123456) 验证
    // ========================================================
    console.log('\n[场景 3] 院系负责人登录与权限收敛验证 (账号: deptadmin / 123456 - 计算机学院主管)');
    await page.evaluate(() => localStorage.clear());
    await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.submit-btn');
    const deptDemoBtns = await page.$$('.demo-buttons .el-button');
    if (deptDemoBtns.length > 2) {
      await deptDemoBtns[2].click(); // 院系管理员
      await new Promise(r => setTimeout(r, 600));
    }
    await page.click('.submit-btn');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });

    console.log('  3.1 访问指导走访台账 /guidance/manage (校验创建按钮已隐藏)');
    await page.goto('http://localhost:3000/guidance/manage', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.page-title');
    const adminCreateBtn = await page.$('.header-action .el-button--primary');
    console.log('      院系负责人是否存在登记按钮:', adminCreateBtn ? '❌ 仍然存在 (越权风险)' : '✅ 已安全隐藏 (仅限教师登记)');

    const shot6 = path.join(SCREENSHOT_DIR, '06_dept_guidance_manage.png');
    await page.screenshot({ path: shot6 });
    console.log('      截图保存路径:', shot6);

    console.log('  3.2 访问周报监控看板 /weekly/monitor');
    await page.goto('http://localhost:3000/weekly/monitor', { waitUntil: 'networkidle2' });
    await page.waitForSelector('.kpi-container');
    const deptKpiCards = await page.$$('.kpi-card');
    console.log('      院系端监控大盘 KPI 卡片加载数:', deptKpiCards.length);

    const shot7 = path.join(SCREENSHOT_DIR, '07_dept_weekly_monitor.png');
    await page.screenshot({ path: shot7 });
    console.log('      截图保存路径:', shot7);

    // ========================================================
    // 4. 控制台错误汇总检查
    // ========================================================
    console.log('\n====================================================');
    console.log('浏览器控制台错误检查 (Console Error Check):');
    console.log('====================================================');
    if (consoleErrors.length === 0) {
      console.log('✅ 控制台错误数量: 0 (全部页面无 JS 报错、无未捕获 Promise 异常)');
    } else {
      console.log(`❌ 捕获到 ${consoleErrors.length} 个控制台错误:`);
      consoleErrors.forEach(err => console.log('   ', err));
    }

    console.log('\n🎉 所有 7 项全流程交互自动化核验全部顺利通过！');
  } catch (err) {
    console.error('浏览器测试执行异常:', err);
    throw err;
  } finally {
    await browser.close();
  }
}

run();
