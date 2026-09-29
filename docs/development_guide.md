# 开发者本地启动与调试指南 (Development Guide)

## 1. 软件版本与环境准备

- **JDK**: Oracle JDK 17 (17.0.8) 或同等 LTS 版本；
- **Maven**: 3.9+；
- **Node.js**: v20+ / v24+；
- **npm**: 10+ / 11+；
- **IDE**: IntelliJ IDEA 2023+ 或 VS Code。

---

## 2. 命令行调试指令

### 后端测试与运行
```bash
# 进入后端目录
cd D:\devlop\IDEA\college-internship-management-system\backend

# 执行单元测试与构建
mvn clean test

# 启动本地开发服务
mvn spring-boot:run
```

### 前端工程运行与调试

#### 默认前端 (React 18 + Ant Design 5)
```bash
# 根目录下直接启动 (推荐)
npm.cmd run dev

# 或运行启动脚本
.\start-frontend.bat

# 或进入 React 工程目录
cd D:\devlop\IDEA\college-internship-management-system\frontend-react
npm.cmd run build   # 生产打包验证
npm.cmd run dev     # 启动 Vite 开发热更新服务器 (默认端口 3000)
```
- **默认前端访问网址**：`http://localhost:3000`
- **接口代理**：`/api` 自动代理至后端 `http://127.0.0.1:8080`

#### Vue 3 前端回退版本 (备用方案，源码完整保留)
```bash
# 根目录下启动回退版本
npm.cmd run dev:vue

# 或运行回退脚本
.\start-frontend-vue-fallback.bat

# 或进入 Vue 前端工程目录
cd D:\devlop\IDEA\college-internship-management-system\frontend
npm.cmd run dev     # 启动 Vue 开发服务 (同样监听 3000 端口)
```
- **回退版本访问网址**：`http://localhost:3000`

---

## 3. 后续迭代优化事项台账 (Pending Optimization Backlog)

经阶段5前端浏览器真实交互验收复核，以下 3 项事项已正式登记归档，列为系统后续优化迭代任务，本阶段保持源码基线不变：

| 事项编号 | 归属模块 | 优化事项说明 | 改进建议与预期效果 | 登记状态 |
| :---: | :--- | :--- | :--- | :---: |
| **OPT-001** | **安全测试**<br>(`SafetyExam.vue`) | **安全考试无试题时禁用提交按钮** | 当学生因未读完规程资料被前置 400 阻断未取得试题（`questions.length === 0`）时，动态禁用交卷按钮（`:disabled="questions.length === 0"`）或展示待准入空状态卡片，避免出现空试卷点击交卷的无意义交互。 | **已登记入库**<br>(后续迭代实现) |
| **OPT-002** | **实习申报**<br>(`InternshipApply.vue`) | **优化实习申报页面标签换行** | 在 1440px 分辨率下，微调表单 `label-width`（如扩展至 `140px`）或调整栅格比例，消除“岗位工作详细地址”中“址”字的折行现象，使联系人三项输入框在宽屏下更加舒展美观。 | **已登记入库**<br>(后续视觉微调) |
| **OPT-003** | **数据库/运维**<br>(MySQL/演示数据) | **统一演示数据和生产数据的 `utf8mb4` 字符集** | 对演示与生产数据库执行 `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4`，统一部署与 seed 脚本编码标准，彻底解决历史遗留的院系/班级中文名称在页面渲染为问号（乱码）的问题。 | **已登记入库**<br>(部署脚本优化) |

---

## 4. 账号密码管理与验证码外发通道配置说明

系统采用松耦合的外发适配层（`IVerificationCodeSender`），所有密钥凭据强制通过环境变量注入，杜绝代码内硬编码。

### 4.1 环境变量配置对照表

| 配置项 | 环境变量名 | 说明 | 推荐/默认值 |
| :--- | :--- | :--- | :--- |
| **短信服务商** | `SMS_PROVIDER` | `mock` (开发测试模拟), `aliyun`, `tencent`, `none` (禁用) | `application-dev.yml` 为 `mock`；生产默认为 `none` |
| **短信 AccessKey** | `SMS_ACCESS_KEY` | 云服务商 API 访问凭证 Key | 生产按需注入 |
| **短信 SecretKey** | `SMS_SECRET_KEY` | 云服务商 API 密钥 Secret | 生产按需注入 |
| **短信签名** | `SMS_SIGN_NAME` | 经审核批准的短信签名 | 生产按需注入 |
| **短信模板编码** | `SMS_TEMPLATE_CODE` | 找回密码专用短信模板 ID | 生产按需注入 |
| **邮件服务商** | `EMAIL_PROVIDER` | `mock` (开发测试模拟), `smtp`, `none` (禁用) | `application-dev.yml` 为 `mock`；生产默认为 `none` |
| **SMTP 主机** | `SMTP_HOST` | 邮件服务器地址 (如 `smtp.exmail.qq.com`) | 生产按需注入 |
| **SMTP 端口** | `SMTP_PORT` | 邮件服务器 SSL/TLS 端口 | 默认 `465` |
| **SMTP 用户名** | `SMTP_USERNAME` | 邮件外发认证账号 | 生产按需注入 |
| **SMTP 授权码** | `SMTP_PASSWORD` | 邮件外发独立应用密码/授权码 | 生产按需注入 |
| **发信人地址** | `SMTP_FROM` | 邮件显示的发件人邮箱 | 生产按需注入 |

### 4.2 环境隔离与安全失败规范

1. **开发与离线测试环境 (`dev` / `test`)**：
   - 默认启用 `MockVerificationCodeProvider`；
   - 模拟记录外发任务计数与目标掩码，**日志严禁输出验证码明文**，API 响应绝不回显验证码。
2. **生产环境 (`prod`)**：
   - 当 `SMS_PROVIDER` 或 `EMAIL_PROVIDER` 为 `none`、空或缺失真实凭据时，适配层**立即安全失败（返回 HTTP 503）**，向用户提示“系统外发通道未配置，请联系管理员处理”；
   - **安全红线**：严禁向客户端伪报“发送成功”，严禁在未配置网关时空发验证码。


