import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const executablePath = fs.existsSync(CHROME_PATH) ? CHROME_PATH : EDGE_PATH;

const REACT_BASE_URL = 'http://localhost:3000';
const BRAIN_DIR = 'C:/Users/曹聪/.gemini/antigravity/brain/26b36451-7588-449f-bf18-e6fe2fba972f';
const SCREENSHOT_DIR = path.join(BRAIN_DIR, 'screenshots');

async function main() {
  const browser = await puppeteer.launch({
    executablePath,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1600,960']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 960 });

  // 1. 以院系管理员身份打开 /inspect 并切换到整改 Tab
  await page.goto(`${REACT_BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await page.evaluate(() => {
    localStorage.setItem('userType', 'DEPT_ADMIN');
    localStorage.setItem('userName', 'deptadmin');
    localStorage.setItem('realName', '计算机负责人 [DEMO]');
    localStorage.setItem('deptId', '1');
  });

  await page.goto(`${REACT_BASE_URL}/inspect`, { waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 1500));

  // 点击“限期整改通知与闭环” Tab
  await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.ant-tabs-tab'));
    for (const t of tabs) {
      if (t.innerText.includes('限期整改')) {
        t.click();
        break;
      }
    }
  });
  await new Promise(r => setTimeout(r, 1200));

  // 切换到刚刚生成的任务 2108
  await page.evaluate(() => {
    const select = document.querySelector('.ant-select-selection-item');
    // 如果有下拉菜单，选择 2108
  });

  const ssTab = path.join(SCREENSHOT_DIR, 'react_rectify_step6_rectify_tab_dept.png');
  await page.screenshot({ path: ssTab, fullPage: true });
  console.log(`Saved screenshot: ${ssTab}`);

  // 打开抽屉查看整改单 96 详情
  const drawerClicked = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
    for (const row of rows) {
      if (row.innerText.includes('96') || row.innerText.includes('整改验收专用学生')) {
        const btns = Array.from(row.querySelectorAll('button, a'));
        for (const b of btns) {
          if (b.innerText.includes('查看详情')) {
            b.click();
            return true;
          }
        }
      }
    }
    return false;
  });

  if (drawerClicked) {
    await new Promise(r => setTimeout(r, 1000));
    const ssDrawer = path.join(SCREENSHOT_DIR, 'react_rectify_step6_drawer_detail.png');
    await page.screenshot({ path: ssDrawer, fullPage: true });
    console.log(`Saved drawer screenshot: ${ssDrawer}`);
  }

  await browser.close();
}

main().catch(console.error);
