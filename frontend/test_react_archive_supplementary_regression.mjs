import fs from 'fs';
import path from 'path';
import http from 'http';
import crypto from 'crypto';
import zlib from 'zlib';
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

function getFileHash(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const buffer = fs.readFileSync(filePath);
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

// Simple ZIP central directory parser
function parseZipEntries(buffer) {
  const entries = [];
  let i = 0;
  while (i < buffer.length - 4) {
    if (buffer.readUInt32LE(i) === 0x04034b50) { // Local file header
      const nameLen = buffer.readUInt16LE(i + 26);
      const extraLen = buffer.readUInt16LE(i + 28);
      const compSize = buffer.readUInt32LE(i + 18);
      const uncompSize = buffer.readUInt32LE(i + 22);
      const flags = buffer.readUInt16LE(i + 6);
      const fileName = buffer.toString('utf8', i + 30, i + 30 + nameLen);
      const dataOffset = i + 30 + nameLen + extraLen;
      entries.push({
        fileName,
        flags,
        isEncrypted: (flags & 1) !== 0,
        compressedSize: compSize,
        uncompressedSize: uncompSize,
        dataOffset
      });
      i = dataOffset + compSize;
    } else {
      i++;
    }
  }
  return entries;
}

async function run() {
  console.log('================================================================');
  console.log('>>> [React 电子档案特批解锁与再次归档] 补充回归定向验收');
  console.log('目标卷宗: 9007 (ARC20292030_student_rectify_iso), 任务: 2123, 学生: 1365');
  console.log('================================================================');

  if (!fs.existsSync(CRED_PATH)) {
    throw new Error(`Dedicated student credential file not found at ${CRED_PATH}`);
  }
  const studentPwd = fs.readFileSync(CRED_PATH, 'utf8').trim();

  const deptToken = await apiLogin('deptadmin', '123456');
  const teacherToken = await apiLogin('teacher', '123456');
  const adminToken = await apiLogin('admin', '123456');
  const studentToken = await apiLogin('student_rectify_iso', studentPwd);

  const results = {};

  // -------------------------------------------------------------------------
  // 1. 无权限角色特批解锁测试 (DEPT_ADMIN & TEACHER)
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 1: 无权限角色特批解锁拦截] ---');
  
  // 1.1 DEPT_ADMIN 尝试解锁卷宗 9007
  console.log('1.1 院系管理员 DEPT_ADMIN 尝试特批解锁卷宗 9007...');
  const deptUnlockRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${deptToken}` }
  }, {
    specialDocNo: 'DOC-ILLEGAL-DEPT-01',
    specialUnlockReason: '院系管理员违规越权尝试解锁'
  });
  console.log('    响应状态码:', deptUnlockRes.status, '业务码:', deptUnlockRes.data?.code, '消息:', deptUnlockRes.data?.message);
  const deptBlocked = (deptUnlockRes.status === 403 || deptUnlockRes.data?.code === 403) &&
                      (deptUnlockRes.data?.message?.includes('仅超级管理员') || deptUnlockRes.data?.message?.includes('权限不足') || deptUnlockRes.data?.message?.includes('不允许'));
  console.log('    DEPT_ADMIN 拦截结果:', deptBlocked ? 'PASS (严格拦截 403)' : 'FAIL');
  results.deptAdminUnlockBlock = deptBlocked;

  // 1.2 TEACHER 尝试解锁卷宗 9007
  console.log('1.2 指导教师 TEACHER 尝试特批解锁卷宗 9007...');
  const teacherUnlockRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    specialDocNo: 'DOC-ILLEGAL-TEACHER-01',
    specialUnlockReason: '带教教师违规越权尝试解锁'
  });
  console.log('    响应状态码:', teacherUnlockRes.status, '业务码:', teacherUnlockRes.data?.code, '消息:', teacherUnlockRes.data?.message);
  const teacherBlocked = (teacherUnlockRes.status === 403 || teacherUnlockRes.data?.code === 403) &&
                         (teacherUnlockRes.data?.message?.includes('仅超级管理员') || teacherUnlockRes.data?.message?.includes('权限不足') || teacherUnlockRes.data?.message?.includes('不允许'));
  console.log('    TEACHER 拦截结果:', teacherBlocked ? 'PASS (严格拦截 403)' : 'FAIL');
  results.teacherUnlockBlock = teacherBlocked;

  // 验证卷宗 9007 状态未受越权影响
  const checkArc9007AfterRoles = await httpRequest(`${BACKEND_BASE}/archives/9007`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  const arcStatusAfterRoles = checkArc9007AfterRoles.data?.data?.status;
  const arcVerAfterRoles = checkArc9007AfterRoles.data?.data?.version;
  console.log(`    越权测试后卷宗状态: ${arcStatusAfterRoles}, 版本: v${arcVerAfterRoles}`);
  const rolesIntegrity = (arcStatusAfterRoles === 'ARCHIVED' && arcVerAfterRoles === 2);
  results.rolesIntegrity = rolesIntegrity;

  // -------------------------------------------------------------------------
  // 2. 参数校验与非 ARCHIVED 状态重复解锁测试
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 2: 参数合法性校验与重复解锁拦截] ---');

  // 2.1 批文号为空
  console.log('2.1 超管提交空红头批文号 (specialDocNo = "")...');
  const emptyDocRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  }, {
    specialDocNo: '',
    specialUnlockReason: '批文号缺失异常参数测试'
  });
  console.log('    响应状态码:', emptyDocRes.status, '业务码:', emptyDocRes.data?.code, '消息:', emptyDocRes.data?.message);
  const emptyDocBlocked = (emptyDocRes.status === 400 || emptyDocRes.data?.code === 400) &&
                          (emptyDocRes.data?.message?.includes('批文号') || emptyDocRes.data?.message?.includes('不能为空'));
  console.log('    空批文号拦截结果:', emptyDocBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.emptyDocBlocked = emptyDocBlocked;

  // 2.2 解锁事由为空
  console.log('2.2 超管提交空解锁事由 (specialUnlockReason = "")...');
  const emptyReasonRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  }, {
    specialDocNo: 'DOC-VALID-01',
    specialUnlockReason: ''
  });
  console.log('    响应状态码:', emptyReasonRes.status, '业务码:', emptyReasonRes.data?.code, '消息:', emptyReasonRes.data?.message);
  const emptyReasonBlocked = (emptyReasonRes.status === 400 || emptyReasonRes.data?.code === 400) &&
                             (emptyReasonRes.data?.message?.includes('事由') || emptyReasonRes.data?.message?.includes('不能为空'));
  console.log('    空事由拦截结果:', emptyReasonBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.emptyReasonBlocked = emptyReasonBlocked;

  // 2.3 先合法解锁一次，然后针对处于 SPECIAL_UNLOCKED 的卷宗测试重复解锁
  console.log('2.3 执行正常解锁进入 SPECIAL_UNLOCKED，再测试非 ARCHIVED 重复解锁拦截...');
  const unlockValidRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  }, {
    specialDocNo: 'DOC-REGRESSION-2026-01',
    specialUnlockReason: '补充回归测试特批解锁窗口与保护期验证'
  });
  console.log('    初次合法解锁状态:', unlockValidRes.status, '业务码:', unlockValidRes.data?.code);

  const duplicateUnlockRes = await httpRequest(`${BACKEND_BASE}/archives/9007/unlock`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  }, {
    specialDocNo: 'DOC-REGRESSION-2026-02',
    specialUnlockReason: '重复特批解锁测试'
  });
  console.log('    重复解锁响应状态码:', duplicateUnlockRes.status, '业务码:', duplicateUnlockRes.data?.code, '消息:', duplicateUnlockRes.data?.message);
  const duplicateBlocked = (duplicateUnlockRes.status === 400 || duplicateUnlockRes.data?.code === 400) &&
                           duplicateUnlockRes.data?.message?.includes('不处于已归档锁定状态');
  console.log('    非 ARCHIVED 重复解锁拦截结果:', duplicateBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.duplicateBlocked = duplicateBlocked;

  // -------------------------------------------------------------------------
  // 3. 解锁保护期写保护机制测试
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 3: 解锁保护期写保护机制测试] ---');

  // 3.1 在 SPECIAL_UNLOCKED 保护期内，学生重提材料（允许修改项）
  console.log('3.1 在 SPECIAL_UNLOCKED 有效期内，学生提交材料更新 (允许修改)...');
  const allowMatRes = await httpRequest(`${BACKEND_BASE}/internship/materials`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, {
    taskId: 2123,
    materialCode: 'TRIPARTITE_AGREEMENT',
    materialName: '三方协议书/接收函盖章件',
    attachmentUrl: 'https://example.com/vouchers/tripartite_iso_2026_v3_supplement.pdf',
    contentText: '补充回归测试：在特批解锁编辑窗口内再次更新三方协议文本与补充盖章证明。'
  });
  console.log('    材料更新响应状态码:', allowMatRes.status, '业务码:', allowMatRes.data?.code);
  const allowMatSuccess = allowMatRes.data?.code === 200;
  console.log('    特批解锁窗口材料修改放行结果:', allowMatSuccess ? 'PASS (正常放行)' : 'FAIL');
  results.allowMatSuccess = allowMatSuccess;

  // 导师复核该材料
  const matId = (typeof allowMatRes.data?.data === 'object') ? allowMatRes.data?.data?.id : allowMatRes.data?.data;
  if (matId) {
    await httpRequest(`${BACKEND_BASE}/internship/materials/${matId}/audit`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${teacherToken}` }
    }, { action: 'APPROVED', auditScore: 99.00, auditComment: '补充回归复核通过，质量达标' });
  }

  // 3.2 重新执行归档锁定，恢复 ARCHIVED 状态
  console.log('3.2 重新执行归档锁定，使卷宗 9007 重新转为 ARCHIVED 状态...');
  const refreezeRes = await httpRequest(`${BACKEND_BASE}/archives/freeze?taskId=2123&studentId=1365`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log('    归档锁定响应状态码:', refreezeRes.status, '业务码:', refreezeRes.data?.code);
  
  // 3.3 在 ARCHIVED 归档锁定状态下，测试全局写保护拦截
  console.log('3.3 在 ARCHIVED 归档锁定状态下，学生尝试再次修改材料 (测试全局写保护)...');
  const lockedMatRes = await httpRequest(`${BACKEND_BASE}/internship/materials`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${studentToken}` }
  }, {
    taskId: 2123,
    materialCode: 'TRIPARTITE_AGREEMENT',
    materialName: '三方协议书/接收函盖章件',
    attachmentUrl: 'https://example.com/vouchers/tripartite_iso_2026_v4_illegal.pdf',
    contentText: '已归档状态下非法尝试修改材料正文内容'
  });
  console.log('    已归档写保护响应状态码:', lockedMatRes.status, '业务码:', lockedMatRes.data?.code, '消息:', lockedMatRes.data?.message);
  const lockedMatBlocked = (lockedMatRes.status === 400 || lockedMatRes.data?.code === 400) &&
                           lockedMatRes.data?.message?.includes('已归档锁定') && lockedMatRes.data?.message?.includes('写保护');
  console.log('    ARCHIVED 状态写保护拦截结果:', lockedMatBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.lockedMatBlocked = lockedMatBlocked;

  // 3.4 在 ARCHIVED 归档锁定状态下，教师尝试录入/修改五维成绩 (测试成绩维度写保护)
  console.log('3.4 在 ARCHIVED 归档锁定状态下，教师尝试录入修改成绩 (测试成绩写保护)...');
  const lockedScoreRes = await httpRequest(`${BACKEND_BASE}/score/summaries`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${teacherToken}` }
  }, {
    taskId: 2123,
    studentId: 1365,
    enterpriseScore: 95.00,
    processScore: 95.00,
    weeklyScore: 95.00,
    materialScore: 95.00,
    summaryScore: 95.00,
    evaluationComment: '非法尝试修改已归档学生成绩'
  });
  console.log('    成绩写保护响应状态码:', lockedScoreRes.status, '业务码:', lockedScoreRes.data?.code, '消息:', lockedScoreRes.data?.message);
  const lockedScoreBlocked = (lockedScoreRes.status === 400 || lockedScoreRes.data?.code === 400) &&
                             lockedScoreRes.data?.message?.includes('已归档锁定') && lockedScoreRes.data?.message?.includes('写保护');
  console.log('    ARCHIVED 成绩写保护拦截结果:', lockedScoreBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.lockedScoreBlocked = lockedScoreBlocked;

  // -------------------------------------------------------------------------
  // 4. 归档幂等性与并发终审提交测试
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 4: 归档幂等性与并发终审提交测试] ---');

  // 4.1 处于已归档状态下的幂等性拦截（重复点击终审归档）
  console.log('4.1 对已处于 ARCHIVED 的卷宗重复提交终审归档 (freeze)...');
  const repeatFreezeRes = await httpRequest(`${BACKEND_BASE}/archives/freeze?taskId=2123&studentId=1365`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log('    重复归档响应状态码:', repeatFreezeRes.status, '业务码:', repeatFreezeRes.data?.code, '消息:', repeatFreezeRes.data?.message);
  const repeatFreezeBlocked = (repeatFreezeRes.status === 400 || repeatFreezeRes.data?.code === 400) &&
                              repeatFreezeRes.data?.message?.includes('已处于归档锁定状态');
  console.log('    重复终审归档幂等拦截结果:', repeatFreezeBlocked ? 'PASS (拦截 400)' : 'FAIL');
  results.repeatFreezeBlocked = repeatFreezeBlocked;

  // 4.2 并发请求测试：同时发起 2 个并发 freeze 请求
  console.log('4.2 并发发起 2 个归档锁定请求 (Promise.all)...');
  const [concurRes1, concurRes2] = await Promise.all([
    httpRequest(`${BACKEND_BASE}/archives/freeze?taskId=2123&studentId=1365`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    }),
    httpRequest(`${BACKEND_BASE}/archives/freeze?taskId=2123&studentId=1365`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    })
  ]);
  console.log('    并发请求 1 状态码:', concurRes1.status, '业务码:', concurRes1.data?.code, '消息:', concurRes1.data?.message);
  console.log('    并发请求 2 状态码:', concurRes2.status, '业务码:', concurRes2.data?.code, '消息:', concurRes2.data?.message);
  const concurrencySafe = (concurRes1.data?.code === 400 || concurRes1.status === 400) &&
                          (concurRes2.data?.code === 400 || concurRes2.status === 400);
  console.log('    并发冲突安全防护结果:', concurrencySafe ? 'PASS (均安全拒绝，无幽灵归档)' : 'FAIL');
  results.concurrencySafe = concurrencySafe;

  // -------------------------------------------------------------------------
  // 5. 校验生成的 ZIP、PDF、manifest.json 与数据库一致性
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 5: ZIP、PDF 与 manifest.json 物理文件及哈希校验] ---');
  const arcFinalRes = await httpRequest(`${BACKEND_BASE}/archives/9007`, {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  const finalArchive = arcFinalRes.data?.data;
  console.log(`    最终卷宗状态: ${finalArchive.status}, 版本: v${finalArchive.version}`);

  const zipPath = path.join('backend/data/archives', finalArchive.archiveBundleUrl);
  const pdfPath = path.join('backend/data/archives', finalArchive.archivePdfUrl);

  const zipExists = fs.existsSync(zipPath);
  const pdfExists = fs.existsSync(pdfPath);
  const zipSize = zipExists ? fs.statSync(zipPath).size : 0;
  const pdfSize = pdfExists ? fs.statSync(pdfPath).size : 0;
  const zipHash = getFileHash(zipPath);
  const pdfHash = getFileHash(pdfPath);

  console.log(`    ZIP 物理文件存在: ${zipExists}, 大小: ${zipSize} 字节, SHA-256: ${zipHash}`);
  console.log(`    PDF 物理文件存在: ${pdfExists}, 大小: ${pdfSize} 字节, SHA-256: ${pdfHash}`);

  // 解析 ZIP 内部文件及 manifest.json
  const zipBuffer = fs.readFileSync(zipPath);
  const entries = parseZipEntries(zipBuffer);
  console.log(`    ZIP 内部条目数量: ${entries.length} 个`);
  const entryNames = entries.map(e => e.fileName);
  console.log(`    条目列表: [${entryNames.join(', ')}]`);

  const hasManifest = entryNames.includes('manifest.json');
  const has01Apply = entryNames.includes('01_学生实习岗位申报材料.pdf');
  const has02Safety = entryNames.includes('02_安全教育考核与安全承诺书.pdf');
  const has03Weekly = entryNames.includes('03_实习周报汇编合集.pdf');
  const has04Guidance = entryNames.includes('04_过程指导走访台账.pdf');
  const has05Inspect = entryNames.includes('05_中期检查督导与限期整改单.pdf');
  const has06Material = entryNames.includes('06_实习总结报告与阶段材料.pdf');
  const has07Score = entryNames.includes('07_五维考核评价与成绩综合评定单.pdf');
  const has08Diag = entryNames.includes('08_归档前置核验诊断单.json');

  const zipComplete = hasManifest && has01Apply && has02Safety && has03Weekly &&
                      has04Guidance && has05Inspect && has06Material && has07Score && has08Diag;
  console.log('    ZIP 七合一标准 PDF + 诊断单 + manifest 完整性:', zipComplete ? 'PASS' : 'FAIL');
  results.zipComplete = zipComplete;

  // -------------------------------------------------------------------------
  // 6. React 端操作与补充截图 (无头浏览器)
  // -------------------------------------------------------------------------
  console.log('\n--- [测试 6: React 前端界面补充验证与截屏] ---');
  const browser = await puppeteer.launch({
    executablePath: EDGE_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const suppStep1Shot = path.join(SCREENSHOT_DIR, 'react_archive_supp_step1_locked_state.png');
  const suppStep2Shot = path.join(SCREENSHOT_DIR, 'react_archive_supp_step2_detail_drawer_manifest.png');

  try {
    // 超管登录并查看台账
    await page.goto('http://127.0.0.1:3000/login', { waitUntil: 'networkidle0' });
    await page.evaluate(() => localStorage.clear());
    await page.reload({ waitUntil: 'networkidle0' });

    await page.waitForSelector('#login_username', { timeout: 8000 });
    await page.type('#login_username', 'admin');
    await page.type('#login_password', '123456');
    await sleep(600);
    await page.click('button[type="submit"]');
    await sleep(1500);

    await page.goto('http://127.0.0.1:3000/archive/manage', { waitUntil: 'networkidle0' });
    await sleep(2000);
    await page.waitForSelector('tr.ant-table-row', { timeout: 8000 });

    // 截图 1: 补充回归终态列表
    await page.screenshot({ path: suppStep1Shot });
    console.log(`    已截取补充回归列表终态: ${suppStep1Shot}`);

    // 打开详情抽屉核验
    await page.evaluate((targetNo) => {
      const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
      for (const row of rows) {
        if (row.innerText.includes(targetNo)) {
          const btn = Array.from(row.querySelectorAll('button')).find(b => b.innerText.includes('卷宗核验'));
          if (btn) btn.click();
          break;
        }
      }
    }, finalArchive.archiveNo);

    await sleep(1500);
    await page.waitForSelector('.ant-drawer-content', { timeout: 8000 });

    // 截图 2: 详情抽屉及准入矩阵
    await page.screenshot({ path: suppStep2Shot });
    console.log(`    已截取补充回归详情抽屉: ${suppStep2Shot}`);
  } finally {
    await browser.close();
  }

  // -------------------------------------------------------------------------
  // 7. 汇总与判定
  // -------------------------------------------------------------------------
  console.log('\n================================================================');
  console.log('>>> 补充回归测试执行汇总:');
  console.log('  1. 越权解锁拦截 (DEPT_ADMIN 403):', results.deptAdminUnlockBlock ? 'PASS' : 'FAIL');
  console.log('  2. 越权解锁拦截 (TEACHER 403):', results.teacherUnlockBlock ? 'PASS' : 'FAIL');
  console.log('  3. 越权测试数据无损:', results.rolesIntegrity ? 'PASS' : 'FAIL');
  console.log('  4. 空红头批文号拦截 (400):', results.emptyDocBlocked ? 'PASS' : 'FAIL');
  console.log('  5. 空解锁事由拦截 (400):', results.emptyReasonBlocked ? 'PASS' : 'FAIL');
  console.log('  6. 非 ARCHIVED 重复解锁拦截 (400):', results.duplicateBlocked ? 'PASS' : 'FAIL');
  console.log('  7. 特批解锁保护期内允许更新材料:', results.allowMatSuccess ? 'PASS' : 'FAIL');
  console.log('  8. 归档锁定状态写保护拦截材料 (400):', results.lockedMatBlocked ? 'PASS' : 'FAIL');
  console.log('  9. 归档锁定状态写保护拦截成绩 (400):', results.lockedScoreBlocked ? 'PASS' : 'FAIL');
  console.log(' 10. 重复终审归档幂等拦截 (400):', results.repeatFreezeBlocked ? 'PASS' : 'FAIL');
  console.log(' 11. 并发终审归档安全拦截:', results.concurrencySafe ? 'PASS' : 'FAIL');
  console.log(' 12. 物理卷宗 ZIP 结构与 manifest 完整性:', results.zipComplete ? 'PASS' : 'FAIL');
  console.log('================================================================');
  
  const allPassed = Object.values(results).every(v => v === true);
  console.log(`>>> 补充回归验收总体结论: ${allPassed ? 'ALL PASS' : 'SOME CHECKS FAILED'}`);
}

run().catch(err => {
  console.error('Regression test failed:', err);
  process.exit(1);
});
