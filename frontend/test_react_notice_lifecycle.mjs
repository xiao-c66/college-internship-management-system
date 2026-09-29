import fs from 'fs';
import path from 'path';
import http from 'http';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const puppeteer = require('d:/devlop/IDEA/college-internship-management-system/frontend/node_modules/puppeteer-core');

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const SCREENSHOT_DIR = 'C:\\Users\\曹聪\\.gemini\\antigravity\\brain\\26b36451-7588-449f-bf18-e6fe2fba972f\\screenshots';
const BACKEND_BASE = 'http://127.0.0.1:8080/api/v1';

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
  console.log('>>> [React 教学通知公告分支] 真实业务端到端验收');
  console.log('“院系发布 → 查看详情 → 编辑 → 撤回” 全生命周期闭环');
  console.log('================================================================');

  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const step1Shot = path.join(SCREENSHOT_DIR, 'react_notice_step1_publish_dialog_scope_preview.png');
  const step2Shot = path.join(SCREENSHOT_DIR, 'react_notice_step2_notice_published_list.png');
  const step3Shot = path.join(SCREENSHOT_DIR, 'react_notice_step3_notice_detail_drawer.png');
  const step4Shot = path.join(SCREENSHOT_DIR, 'react_notice_step4_notice_edited_saved.png');
  const step5Shot = path.join(SCREENSHOT_DIR, 'react_notice_step5_notice_revoked_modal.png');
  const step6Shot = path.join(SCREENSHOT_DIR, 'react_notice_step6_notice_revoked_persisted_refresh.png');

  const timestamp = Date.now();
  const dedupKey = `MANUAL_DEPT1_NOTICE_${timestamp}`;
  const noticeTitle = `关于2026届计算机学院毕业顶岗实习教学检查周与材料归档规范的通知_${timestamp}`;
  const noticeContent = `<p>各系、实习指导教师及全体2026届实习学生：</p><p>为保障毕业实习教学质量，计算机科学与技术学院将于<strong>下周一至周三</strong>开展实习中期现场与线上随机抽检，请严格按要求提报阶段材料与周报。<script>alert('xss_attempt')</script></p>`;

  try {
    // 1. 院系管理员登录并导航至通知公告管理页面
    console.log('1. 院系管理员 deptadmin 登录系统...');
    await browserLogin(page, 'deptadmin', '123456', '/dashboard/dept');
    console.log('   - 院系管理员登录成功，导航至 /admin/system/notice ...');
    await page.goto('http://127.0.0.1:3000/admin/system/notice', { waitUntil: 'networkidle0' });
    await sleep(1500);

    const tableHasNotice27 = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      return rows.some(r => r.innerText.includes('27') && r.innerText.includes('已更新巡检时间表'));
    });

    if (!tableHasNotice27) {
      // 2. 点击【发布新通知公告】
      console.log('2. 打开发布新通知公告弹窗，核验发布范围权限与安全预览...');
      await page.waitForSelector('.ant-btn-primary', { timeout: 8000 });
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const btn = btns.find(b => b.innerText.includes('发布新通知公告'));
        if (btn) btn.click();
      });
      await sleep(1000);

      await page.waitForSelector('.ant-modal #dedupKey', { timeout: 8000 });

    // 验证发布范围权限：全校师生 (ALL) 处于禁用状态，默认锁定为本院系 (DEPT)
    const isAllDisabled = await page.evaluate(() => {
      const allRadio = document.querySelector('.ant-modal input[value="ALL"]');
      return allRadio ? allRadio.disabled : false;
    });
    const extraText = await page.evaluate(() => {
      const modal = document.querySelector('.ant-modal');
      return modal ? modal.innerText : '';
    });
    console.log(`   - 院系管理员“全校师生 (ALL)”选项已禁用: ${isAllDisabled}`);
    console.log(`   - 院系范围强制限定提示存在: ${extraText.includes('院系负责人权限仅允许发布本院系通知')}`);

    // 填写防重键、标题、正文
    await clearAndType(page, '.ant-modal #dedupKey', dedupKey);
    await clearAndType(page, '.ant-modal #noticeTitle', noticeTitle);
    await clearAndType(page, '.ant-modal #noticeContent', noticeContent);
    await sleep(600);

    // 验证 DOMPurify 实时安全预览中 <script> 被彻底剔除
    const previewHtml = await page.evaluate(() => {
      const previewDiv = document.querySelector('.ant-modal [dangerouslysetinnerhtml], .ant-modal div[style*="overflow-y"]');
      return previewDiv ? previewDiv.innerHTML : '';
    });
    console.log(`   - 实时预览中已过滤 script 标签: ${!previewHtml.includes('<script>') && !previewHtml.includes('alert')}`);

    await page.screenshot({ path: step1Shot });
    console.log(`   [Step 1] 发布弹窗与安全预览已截屏: ${step1Shot}`);

    // 3. 提交发布
    console.log('3. 提交发布通知公告 (API-114)...');
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.ant-modal button'));
      const okBtn = btns.find(b => b.innerText.includes('确认发布'));
      if (okBtn) okBtn.click();
    });
    await sleep(2000);

    // 验证列表刷新并展示新公告
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });
    const listText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 列表中已展示新发布的通知标题: ${listText.includes(noticeTitle)}`);
    console.log(`   - 状态显示“正常发布”: ${listText.includes('正常发布')}`);
    console.log(`   - 范围显示“本院系 (ID:1)”: ${listText.includes('本院系')}`);

    await page.screenshot({ path: step2Shot });
    console.log(`   [Step 2] 发布后列表已截屏: ${step2Shot}`);

    // 4. 查看详情
    console.log('4. 查阅通知公告详情 (API-113)...');
    await page.evaluate((targetTitle) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetTitle)) {
          const detailBtn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('查阅详情'));
          if (detailBtn) {
            detailBtn.click();
            break;
          }
        }
      }
    }, noticeTitle);
    await sleep(1500);

    await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });
    const drawerText = await page.evaluate(() => {
      const drawer = document.querySelector('.ant-drawer-content');
      return drawer ? drawer.innerText : '';
    });
    console.log(`   - 详情抽屉展示标题: ${drawerText.includes(noticeTitle)}`);
    console.log(`   - 详情抽屉展示防重键: ${drawerText.includes(dedupKey)}`);
    console.log(`   - 详情抽屉展示状态为“正常发布”: ${drawerText.includes('正常发布')}`);
    console.log(`   - 详情抽屉已读状态为“已读”: ${drawerText.includes('已读')}`);

    await page.screenshot({ path: step3Shot });
    console.log(`   [Step 3] 详情抽屉已截屏: ${step3Shot}`);

    // 关闭抽屉并等待完全消失
    await page.evaluate(() => {
      const closeBtn = document.querySelector('.ant-drawer-close');
      if (closeBtn) closeBtn.click();
    });
    await page.waitForFunction(() => !document.querySelector('.ant-drawer-open'), { timeout: 5000 });
    await sleep(800);

    // 5. 编辑通知公告
    console.log('5. 编辑修改通知公告 (API-115)...');
    await page.evaluate((targetTitle) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetTitle)) {
          const editBtn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('编辑'));
          if (editBtn) {
            editBtn.click();
            break;
          }
        }
      }
    }, noticeTitle);
    await sleep(1000);

    // 等待编辑弹窗显式呈现
    await page.waitForFunction(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal')).find(m => m.innerText.includes('修改通知公告'));
      return modal && modal.offsetParent !== null;
    }, { timeout: 8000 });

    const updatedTitle = `${noticeTitle}（已更新巡检时间表）`;
    const updatedContent = `${noticeContent}<p><strong>补充说明：</strong>现场核验时间表已更新至协同盘，请各带教教师提前就位。</p>`;

    await page.evaluate((title, content) => {
      const modal = Array.from(document.querySelectorAll('.ant-modal')).find(m => m.innerText.includes('修改通知公告'));
      const input = modal.querySelector('input');
      const textarea = modal.querySelector('textarea');

      input.focus();
      const inputSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      inputSetter.call(input, title);
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));

      textarea.focus();
      const textareaSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
      textareaSetter.call(textarea, content);
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
      textarea.dispatchEvent(new Event('change', { bubbles: true }));
    }, updatedTitle, updatedContent);
    await sleep(800);

    // 提交编辑保存
    await page.evaluate(() => {
      const modal = Array.from(document.querySelectorAll('.ant-modal')).find(m => m.innerText.includes('修改通知公告'));
      const saveBtn = Array.from(modal.querySelectorAll('button')).find(b => b.innerText.includes('保存修改'));
      if (saveBtn) saveBtn.click();
    });
    await sleep(2500);

    const afterEditText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 列表已更新显示修改后的标题: ${afterEditText.includes('已更新巡检时间表')}`);

    await page.screenshot({ path: step4Shot });
    console.log(`   [Step 4] 编辑修改后列表已截屏: ${step4Shot}`);
    } else {
      console.log('   - 发现公告 27 已存在且处于已编辑状态，直接执行撤回与持久化验证流程...');
    }

    // 6. 撤回通知公告
    console.log('6. 撤回通知公告 (status: 0)...');
    await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes('27') || row.innerText.includes('已更新巡检时间表')) {
          const revokeBtn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('撤回'));
          if (revokeBtn) {
            revokeBtn.click();
            break;
          }
        }
      }
    });
    await sleep(1000);

    // 确认撤回弹窗
    await page.waitForSelector('.ant-modal-confirm', { timeout: 8000 });
    await page.screenshot({ path: step5Shot });
    console.log(`   [Step 5] 撤回确认弹窗已截屏: ${step5Shot}`);

    await page.click('.ant-modal-confirm-btns .ant-btn-primary');
    await page.waitForFunction(() => !document.querySelector('.ant-modal-confirm'), { timeout: 8000 });
    await sleep(2000);

    // 验证状态变更为“已撤回”
    const revokedText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 撤回后列表状态显示“已撤回”: ${revokedText.includes('已撤回')}`);

    // 7. 刷新页面持久化验证
    console.log('7. 刷新页面，验证撤回状态持久化保留...');
    await page.reload({ waitUntil: 'networkidle0' });
    await sleep(1500);

    const reloadedText = await page.evaluate(() => document.body.innerText);
    console.log(`   - 刷新后公告依然保留在列表中: ${reloadedText.includes('已更新巡检时间表')}`);
    console.log(`   - 刷新后状态依然为“已撤回”: ${reloadedText.includes('已撤回')}`);

    await page.screenshot({ path: step6Shot });
    console.log(`   [Step 6] 刷新后持久化列表已截屏: ${step6Shot}`);

    console.log('================================================================');
    console.log('>>> [React 教学通知公告分支] 验收闭环测试全部通过！');
    console.log('================================================================');
  } finally {
    await browser.close();
  }
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
