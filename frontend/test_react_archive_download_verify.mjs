import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const puppeteer = require('d:/devlop/IDEA/college-internship-management-system/frontend/node_modules/puppeteer-core');

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const DOWNLOAD_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\scratch\\archive_verify\\downloaded_browser';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
  console.log('>>> [React 电子档案模块] 文件下载能力与 ZIP 状态核验');
  console.log('目标归档卷宗: ID 9002 (ARC20252026_student_p9)');
  console.log('================================================================');

  // 清空已有的下载目录
  if (fs.existsSync(DOWNLOAD_DIR)) {
    const files = fs.readdirSync(DOWNLOAD_DIR);
    for (const f of files) {
      fs.unlinkSync(path.join(DOWNLOAD_DIR, f));
    }
  } else {
    fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });
  }

  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  // 启用 CDP 浏览器下载拦截
  const client = await page.target().createCDPSession();
  await client.send('Page.setDownloadBehavior', {
    behavior: 'allow',
    downloadPath: DOWNLOAD_DIR
  });

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_archive_step1_list_page.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_archive_step2_detail_drawer.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_archive_step3_after_zip_download.png');

  try {
    // 1. 登录院系管理员并进入电子档案页面
    console.log('1. 登录院系管理员 deptadmin 并导航至 /archive/manage ...');
    await browserLogin(page, 'deptadmin', '123456', '/dashboard/dept');
    await page.goto('http://127.0.0.1:3000/archive/manage', { waitUntil: 'networkidle0' });
    await sleep(1500);

    await page.waitForSelector('.ant-table-row', { timeout: 8000 });
    await page.screenshot({ path: step1Shot });
    console.log(`   [Step 1] 档案列表页面已截屏: ${step1Shot}`);

    // 2. 检查 React 页面和抽屉是否存在单独 PDF 下载入口
    console.log('2. 检查 React 档案页是否存在单独 PDF 下载入口...');
    const pageButtons = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button, a'));
      return btns.map(b => ({
        tag: b.tagName,
        text: (b.innerText || b.textContent || '').trim(),
        title: b.getAttribute('title') || '',
        href: b.getAttribute('href') || ''
      }));
    });

    const pdfButtonsOnPage = pageButtons.filter(b => 
      b.text.toLowerCase().includes('pdf') || 
      b.title.toLowerCase().includes('pdf') ||
      b.href.toLowerCase().includes('.pdf')
    );
    console.log(`   - 档案台账表格区域包含“PDF”关键词的按钮/链接数量: ${pdfButtonsOnPage.length}`);

    // 打开卷宗 9002 的详情抽屉
    console.log('   - 点击卷宗 9002 (ARC20252026_student_p9) 的【卷宗核验】打开抽屉...');
    await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes('ARC20252026_student_p9') || row.innerText.includes('9002')) {
          const verifyBtn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('卷宗核验'));
          if (verifyBtn) {
            verifyBtn.click();
            break;
          }
        }
      }
    });
    await sleep(1200);

    await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
    await page.screenshot({ path: step2Shot });
    console.log(`   [Step 2] 详情抽屉已截屏: ${step2Shot}`);

    const drawerButtons = await page.evaluate(() => {
      const drawer = document.querySelector('.ant-drawer-content');
      if (!drawer) return [];
      const btns = Array.from(drawer.querySelectorAll('button, a'));
      return btns.map(b => ({
        tag: b.tagName,
        text: (b.innerText || b.textContent || '').trim(),
        title: b.getAttribute('title') || '',
        href: b.getAttribute('href') || ''
      }));
    });

    const pdfButtonsInDrawer = drawerButtons.filter(b => 
      b.text.toLowerCase().includes('pdf') || 
      b.title.toLowerCase().includes('pdf') ||
      b.href.toLowerCase().includes('.pdf')
    );
    console.log(`   - 详情抽屉区域包含“PDF”关键词的按钮/链接数量: ${pdfButtonsInDrawer.length}`);
    console.log(`   - 详情抽屉内所有操作按钮文本: [${drawerButtons.map(b => b.text).join(', ')}]`);

    // 关闭抽屉
    await page.evaluate(() => {
      const closeBtn = document.querySelector('.ant-drawer-close');
      if (closeBtn) closeBtn.click();
    });
    await page.waitForFunction(() => !document.querySelector('.ant-drawer-open'), { timeout: 5000 });
    await sleep(800);

    // 3. 通过 React 页面已有入口触发 9002 的 ZIP 浏览器下载
    console.log('3. 通过已有入口触发卷宗 9002 的 ZIP 浏览器下载 (API-101)...');
    
    // 注意后端流控：如果距离上次导出未满10秒，稍作等待
    await sleep(2000);

    await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes('ARC20252026_student_p9') || row.innerText.includes('9002')) {
          const exportBtn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('导出ZIP'));
          if (exportBtn) {
            exportBtn.click();
            break;
          }
        }
      }
    });

    console.log('   - 等待浏览器下载完成...');
    let downloadedFilePath = null;
    const maxWaitMs = 15000;
    const startTime = Date.now();

    while (Date.now() - startTime < maxWaitMs) {
      const files = fs.readdirSync(DOWNLOAD_DIR);
      const zipFile = files.find(f => f.endsWith('.zip') && !f.endsWith('.crdownload') && !f.endsWith('.tmp'));
      if (zipFile) {
        const fullPath = path.join(DOWNLOAD_DIR, zipFile);
        const stats = fs.statSync(fullPath);
        if (stats.size > 0) {
          // 稍微稳定 500ms
          await sleep(500);
          downloadedFilePath = fullPath;
          break;
        }
      }
      await sleep(500);
    }

    if (!downloadedFilePath) {
      throw new Error('浏览器在限时内未完成 ZIP 下载！');
    }

    await page.screenshot({ path: step3Shot });
    console.log(`   [Step 3] 下载触发后列表状态已截屏: ${step3Shot}`);

    const stats = fs.statSync(downloadedFilePath);
    const fileBuffer = fs.readFileSync(downloadedFilePath);
    const sha256 = crypto.createHash('sha256').update(fileBuffer).digest('hex');

    console.log('================================================================');
    console.log('>>> [浏览器下载核验证据]');
    console.log(`   - 下载文件名: ${path.basename(downloadedFilePath)}`);
    console.log(`   - 文件绝对路径: ${downloadedFilePath}`);
    console.log(`   - 文件字节大小: ${stats.size} bytes (${(stats.size / 1024).toFixed(2)} KB)`);
    console.log(`   - SHA-256 哈希值: ${sha256}`);
    console.log('================================================================');

  } finally {
    await browser.close();
  }
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
