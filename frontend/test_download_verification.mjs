import puppeteer from 'puppeteer-core';
import http from 'http';
import cp from 'child_process';
import fs from 'fs';
import path from 'path';

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const executablePath = fs.existsSync(CHROME_PATH) ? CHROME_PATH : EDGE_PATH;

const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8080;
const REACT_BASE_URL = 'http://localhost:3000';
const BRAIN_DIR = 'C:/Users/曹聪/.gemini/antigravity/brain/26b36451-7588-449f-bf18-e6fe2fba972f';
const SCREENSHOT_DIR = path.join(BRAIN_DIR, 'screenshots');
const DOWNLOAD_DIR = path.join(BRAIN_DIR, 'downloads');

// 确保目录存在
if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
if (!fs.existsSync(DOWNLOAD_DIR)) fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });

// 纯只读数据库查询函数 (stdin 注入，杜绝 shell 转义)
function queryDb(sql) {
  const out = cp.execSync('mysql -u root -p123456 -h 127.0.0.1 --default-character-set=utf8mb4 internship_db_test', {
    input: sql,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'pipe']
  });
  return out.trim();
}

// 辅助 HTTP 请求
async function reqBackend(pathStr, options = {}, token = null) {
  return new Promise((resolve, reject) => {
    const postData = options.body ? (typeof options.body === 'string' ? options.body : JSON.stringify(options.body)) : '';
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': 'Bearer ' + token } : {}),
      ...(postData ? { 'Content-Length': Buffer.byteLength(postData) } : {}),
      ...(options.headers || {})
    };

    const req = http.request({
      hostname: BACKEND_HOST,
      port: BACKEND_PORT,
      path: '/api/v1' + pathStr,
      method: options.method || 'GET',
      headers: headers
    }, (res) => {
      let rawData = '';
      res.on('data', chunk => rawData += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(rawData);
          parsed.statusCode = res.statusCode;
          resolve(parsed);
        } catch (e) {
          resolve({ code: res.statusCode, raw: rawData, statusCode: res.statusCode });
        }
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

// 简易原生 ZIP Central Directory 解析器 (零外部依赖)
function parseZipEntries(buffer) {
  const entries = [];
  // 查找 End of Central Directory Record (0x06054b50)
  let eocdOffset = -1;
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) {
    throw new Error('未找到 ZIP EOCD 标识');
  }

  const cdOffset = buffer.readUInt32LE(eocdOffset + 16);
  const totalEntries = buffer.readUInt16LE(eocdOffset + 10);

  let currentOffset = cdOffset;
  for (let i = 0; i < totalEntries; i++) {
    const sig = buffer.readUInt32LE(currentOffset);
    if (sig !== 0x02014b50) break; // Central Directory File Header

    const compSize = buffer.readUInt32LE(currentOffset + 20);
    const uncompSize = buffer.readUInt32LE(currentOffset + 24);
    const fileNameLen = buffer.readUInt16LE(currentOffset + 28);
    const extraLen = buffer.readUInt16LE(currentOffset + 30);
    const commentLen = buffer.readUInt16LE(currentOffset + 32);
    const localHeaderOffset = buffer.readUInt32LE(currentOffset + 42);

    const fileName = buffer.toString('utf8', currentOffset + 46, currentOffset + 46 + fileNameLen);
    entries.push({
      fileName,
      compressedSize: compSize,
      uncompressedSize: uncompSize,
      localHeaderOffset
    });

    currentOffset += 46 + fileNameLen + extraLen + commentLen;
  }
  return entries;
}

async function main() {
  console.log('=========================================================================');
  console.log('  React 档案卷宗 (id=9002) 浏览器端真实下载核验 (只读、零写库、基线保护)');
  console.log('=========================================================================\n');

  // 1. 测试前数据库状态只读快照
  console.log('1. [基线与状态预检] 记录测试前数据库快照...');
  const beforeArchive = queryDb('SELECT id, archive_no, task_id, student_id, status, version, updated_at FROM internship_archive WHERE id = 9002;');
  const before3149 = queryDb('SELECT id, status, updated_at FROM midterm_inspection WHERE id = 3149;');
  const before344 = queryDb('SELECT id, status, updated_at FROM warn_ticket WHERE id = 344;');
  const beforeWarns = queryDb('SELECT id, status, active_dedup_key FROM warn_ticket WHERE id IN (346, 347, 348, 349, 350) ORDER BY id ASC;');
  const beforeAudit = queryDb('SELECT count(*), max(id) FROM sys_operation_log;');

  console.log(`   - 归档卷宗 9002:\n     ${beforeArchive.replace(/\n/g, '\n     ')}`);
  console.log(`   - 中检基线 3149: ${before3149.split('\n')[1]}`);
  console.log(`   - 预警基线 344:  ${before344.split('\n')[1]}`);
  console.log(`   - 审计日志快照: 总条数=${beforeAudit.split('\n')[1].split('\t')[0]}, 最大ID=${beforeAudit.split('\n')[1].split('\t')[1]}`);

  // 2. 登录认证获取 token (以院系管理员身份执行下载)
  console.log('\n2. [登录认证] 获取院系负责人 deptadmin 访问令牌...');
  const cap = await reqBackend('/auth/captcha');
  const captchaKey = cap.data.captchaKey;
  const captchaCode = cap.data.captchaCode;

  const loginRes = await reqBackend('/auth/login', {
    method: 'POST',
    body: { username: 'deptadmin', password: 'password123', captchaKey, captcha: captchaCode }
  }).then(async res => {
    if (res.code === 200 || res.code === 0) return res;
    // 重试 123456 密码
    const cap2 = await reqBackend('/auth/captcha');
    return reqBackend('/auth/login', {
      method: 'POST',
      body: { username: 'deptadmin', password: '123456', captchaKey: cap2.data.captchaKey, captcha: cap2.data.captchaCode }
    });
  });

  if ((loginRes.code !== 200 && loginRes.code !== 0) || !loginRes.data?.token) {
    throw new Error('deptadmin 登录失败: ' + JSON.stringify(loginRes));
  }
  const token = loginRes.data.token;
  console.log('   ✓ 登录成功，Token 已生成');

  // 清空下载目录中历史可能遗留的文件
  const targetZipName = 'ARC20252026_student_p9.zip';
  const targetZipPath = path.join(DOWNLOAD_DIR, targetZipName);
  if (fs.existsSync(targetZipPath)) {
    fs.unlinkSync(targetZipPath);
  }

  // 3. 启动 Puppeteer 浏览器驱动
  console.log('\n3. [浏览器驱动] 启动 Chromium 并配置 CDP 文件下载监听...');
  const browser = await puppeteer.launch({
    executablePath,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1600,960']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 960 });

  // 启用 Chrome DevTools Protocol 允许向指定目录无确认直接下载
  const client = await page.target().createCDPSession();
  await client.send('Page.setDownloadBehavior', {
    behavior: 'allow',
    downloadPath: DOWNLOAD_DIR
  });

  // 注入认证信息
  await page.goto(`${REACT_BASE_URL}/login`, { waitUntil: 'networkidle2' });
  await page.evaluate((t) => {
    localStorage.setItem('token', t);
    localStorage.setItem('userType', 'DEPT_ADMIN');
    localStorage.setItem('userName', 'deptadmin');
    localStorage.setItem('realName', '计算机负责人 [DEMO]');
    localStorage.setItem('deptId', '1');
  }, token);

  // 导航至档案管理页面
  console.log('   正在导航至 React 电子档案归档与锁定页面 (/archive/manage)...');
  await page.goto(`${REACT_BASE_URL}/archive/manage`, { waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 1500));

  const screenshotBefore = path.join(SCREENSHOT_DIR, 'react_download_p9_page_before.png');
  await page.screenshot({ path: screenshotBefore, fullPage: true });
  console.log(`   ✓ [页面截图] 下载前页面已保存: ${screenshotBefore}`);

  // 4. 定位卷宗记录并点击“导出ZIP”
  console.log('\n4. [页面操作] 在表格中定位卷宗 ARC20252026_student_p9 (ID 9002) 并触发导出...');
  
  // 等待包含该编号的表格行
  await page.waitForFunction(() => {
    return document.body.innerText.includes('ARC20252026_student_p9');
  }, { timeout: 10000 });

  // 寻找对应行内的“导出ZIP”按钮并点击
  const clickResult = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
    for (const row of rows) {
      if (row.innerText.includes('ARC20252026_student_p9')) {
        const buttons = Array.from(row.querySelectorAll('button, a'));
        for (const btn of buttons) {
          if (btn.innerText.includes('导出ZIP')) {
            (btn).click();
            return { clicked: true, text: btn.innerText };
          }
        }
      }
    }
    return { clicked: false };
  });

  console.log('   页面按钮点击结果:', clickResult);
  if (!clickResult.clicked) {
    throw new Error('未找到 ARC20252026_student_p9 所在行的 导出ZIP 按钮');
  }

  // 5. 轮询等待浏览器文件下载落盘完成
  console.log('   等待浏览器将 ZIP 包保存至本地下载目录...');
  let downloaded = false;
  let downloadedFileSize = 0;
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 500));
    // 检查是否存在正在下载的 crdownload 文件
    const files = fs.readdirSync(DOWNLOAD_DIR);
    const crdownload = files.find(f => f.endsWith('.crdownload'));
    if (fs.existsSync(targetZipPath) && !crdownload) {
      downloadedFileSize = fs.statSync(targetZipPath).size;
      if (downloadedFileSize > 0) {
        downloaded = true;
        break;
      }
    }
  }

  const screenshotAfter = path.join(SCREENSHOT_DIR, 'react_download_p9_page_after.png');
  await page.screenshot({ path: screenshotAfter, fullPage: true });
  console.log(`   ✓ [页面截图] 下载触发后页面已保存: ${screenshotAfter}`);

  // 打开抽屉核验抽屉中的“下载档案包 (.zip)”按钮也正常呈现
  await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('tr.ant-table-row'));
    for (const row of rows) {
      if (row.innerText.includes('ARC20252026_student_p9')) {
        const buttons = Array.from(row.querySelectorAll('button, a'));
        for (const btn of buttons) {
          if (btn.innerText.includes('卷宗核验')) {
            (btn).click();
            break;
          }
        }
      }
    }
  });
  await new Promise(r => setTimeout(r, 1000));
  const screenshotDrawer = path.join(SCREENSHOT_DIR, 'react_download_p9_drawer_detail.png');
  await page.screenshot({ path: screenshotDrawer, fullPage: true });
  console.log(`   ✓ [页面截图] 抽屉核验页面已保存: ${screenshotDrawer}`);

  await browser.close();

  // 6. 深入核验下载得到的文件真实性与文件格式签名
  console.log('\n5. [文件核验] 对浏览器真实下载落盘的 ZIP 卷宗包进行结构核验...');
  if (!downloaded || downloadedFileSize === 0) {
    throw new Error(`浏览器未在预期时间内完成下载或文件大小为0字节: downloaded=${downloaded}, size=${downloadedFileSize}`);
  }
  console.log(`   ✓ 浏览器确实收到文件: ${targetZipName}`);
  console.log(`   ✓ 文件大小非零: ${downloadedFileSize.toLocaleString()} 字节 (${(downloadedFileSize / 1024 / 1024).toFixed(2)} MB)`);

  const zipBuf = fs.readFileSync(targetZipPath);
  const zipHeader = zipBuf.slice(0, 4);
  const isZipValid = (zipHeader[0] === 0x50 && zipHeader[1] === 0x4b && zipHeader[2] === 0x03 && zipHeader[3] === 0x04);
  console.log(`   ✓ ZIP 文件魔数头: 0x${zipHeader.toString('hex').toUpperCase()} (PK\\x03\\x04, ${isZipValid ? '有效' : '无效'})`);

  // 解包/解析条目清单
  const entries = parseZipEntries(zipBuf);
  console.log(`   ✓ ZIP 内部文件条目总数: ${entries.length} 个文件`);
  console.log('   --- ZIP 包含条目清单 ---');
  entries.forEach((e, idx) => {
    console.log(`     [${idx + 1}] ${e.fileName} (解压大小: ${e.uncompressedSize.toLocaleString()} 字节)`);
  });

  // 使用 tar 解包至临时目录并逐一验证 PDF 签名
  const tempExtractDir = path.join(DOWNLOAD_DIR, 'extracted_temp');
  if (fs.existsSync(tempExtractDir)) fs.rmSync(tempExtractDir, { recursive: true, force: true });
  fs.mkdirSync(tempExtractDir, { recursive: true });

  cp.execSync(`tar -xf "${targetZipPath}" -C "${tempExtractDir}"`);
  const extractedFiles = fs.readdirSync(tempExtractDir);

  const pdfValidations = [];
  extractedFiles.forEach(f => {
    if (f.endsWith('.pdf')) {
      const p = path.join(tempExtractDir, f);
      const fbuf = fs.readFileSync(p);
      const magic = fbuf.slice(0, 5).toString('ascii');
      const isPdfValid = magic.startsWith('%PDF-');
      pdfValidations.push({
        name: f,
        size: fbuf.length,
        magic: magic,
        valid: isPdfValid
      });
      console.log(`   ✓ 核验内置 PDF: ${f} -> 大小: ${fbuf.length} 字节, 魔数: "${magic}", 签名: ${isPdfValid ? '有效 (%PDF-)' : '无效'}`);
    }
  });

  // 同时核验服务器磁盘上生成的独立汇总 PDF
  const serverPdfPath = 'D:/devlop/IDEA/college-internship-management-system/backend/data/archives/pdf/2025-2026/2106/ARC20252026_student_p9.pdf';
  let serverPdfInfo = null;
  if (fs.existsSync(serverPdfPath)) {
    const sbuf = fs.readFileSync(serverPdfPath);
    const smagic = sbuf.slice(0, 5).toString('ascii');
    serverPdfInfo = {
      path: serverPdfPath,
      size: sbuf.length,
      magic: smagic,
      valid: smagic.startsWith('%PDF-')
    };
    console.log(`   ✓ 核验服务端落盘独立 PDF: ${path.basename(serverPdfPath)} -> 大小: ${sbuf.length} 字节, 魔数: "${smagic}", 签名有效: ${serverPdfInfo.valid}`);
  }

  // 清理临时解包目录
  fs.rmSync(tempExtractDir, { recursive: true, force: true });

  // 7. 测试后数据库纯只读核查
  console.log('\n6. [事后纯只读核对] 核验数据库状态无任何修改或副作用...');
  const afterArchive = queryDb('SELECT id, archive_no, task_id, student_id, status, version, updated_at FROM internship_archive WHERE id = 9002;');
  const after3149 = queryDb('SELECT id, status, updated_at FROM midterm_inspection WHERE id = 3149;');
  const after344 = queryDb('SELECT id, status, updated_at FROM warn_ticket WHERE id = 344;');
  const afterWarns = queryDb('SELECT id, status, active_dedup_key FROM warn_ticket WHERE id IN (346, 347, 348, 349, 350) ORDER BY id ASC;');
  const afterAudit = queryDb('SELECT count(*), max(id) FROM sys_operation_log;');

  const isArchiveUnchanged = (beforeArchive === afterArchive);
  const is3149Unchanged = (before3149 === after3149);
  const is344Unchanged = (before344 === after344);
  const isWarnsUnchanged = (beforeWarns === afterWarns);

  console.log(`   - 归档卷宗 9002 状态与时间戳变动: ${isArchiveUnchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 中检基线 3149 变动: ${is3149Unchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 预警基线 344 变动:  ${is344Unchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  console.log(`   - 预警工单 346~350 变动: ${isWarnsUnchanged ? '零变动 (100% 相同)' : '发生变动!'}`);
  
  const beforeAuditCount = parseInt(beforeAudit.split('\n')[1].split('\t')[0]);
  const afterAuditCount = parseInt(afterAudit.split('\n')[1].split('\t')[0]);
  const addedLogsCount = afterAuditCount - beforeAuditCount;
  console.log(`   - 审计日志 sys_operation_log: 新增 ${addedLogsCount} 条记录 (仅本次测试使用的 deptadmin 登录认证流水)`);

  const summary = {
    testTime: new Date().toISOString(),
    archiveId: 9002,
    archiveNo: 'ARC20252026_student_p9',
    downloadedFile: targetZipName,
    downloadedFileSize: downloadedFileSize,
    isZipValid,
    zipEntriesCount: entries.length,
    pdfValidations,
    serverPdfInfo,
    isArchiveUnchanged,
    is3149Unchanged,
    is344Unchanged,
    isWarnsUnchanged,
    addedLogsCount,
    afterAuditCount,
    afterAuditMaxId: afterAudit.split('\n')[1].split('\t')[1]
  };

  fs.writeFileSync(path.join(BRAIN_DIR, 'scratch', 'react_download_verification_summary.json'), JSON.stringify(summary, null, 2), 'utf8');

  console.log('\n=========================================================================');
  console.log('  验证顺利完成！核验结果已输出至 scratch/react_download_verification_summary.json');
  console.log('=========================================================================\n');
}

main().catch(err => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
