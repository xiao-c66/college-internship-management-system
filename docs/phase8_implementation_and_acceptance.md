# 高校实习全过程管理系统 - 阶段8《系统优化与本地隔离容器部署演练》实施与验收归档记录

> [!IMPORTANT]
> **文档性质与版本基线声明**：
> - **本文档性质**：本文档为基于阶段 8 代码归档（Git Commit: `52d29b2`）、文档归档（Git Commit: `e0ab8a3`，Tag: `v8.0.0-phase8-sealed`）、85 项自动化测试、**9 项前端真实浏览器端到端全绿验收**与**本地隔离 Docker 编排工程产物**整理的定稿实施规范与验收记录。
> - **阶段边界定义（红线锁定）**：
>   - **阶段 8** 准确定义为**《系统优化与本地隔离容器部署演练》**，包含后端性能监控与安全审计、系统运维、通知管理、测试数据隔离、**前端真实浏览器端到端验收（已完成）**以及**本地隔离容器部署演练 Docker/Nginx（配置已就绪，受宿主机权限限制）**。严禁将 Docker 演练擅自更改编号为阶段 9；
>   - **阶段 9** 严格定义为**《毕业设计答辩材料整理》**。在阶段 8 的 Docker 部署演练与浏览器人工验收全面闭环前，阶段 9 严格保持暂停，不得直接启动实施。

---

## 一、 阶段 8 实施与验收成果概览

| 检查维度 | 状态判定 | 真实工程证据与说明 |
| :--- | :---: | :--- |
| **RESTful 接口** | **【已完成】** | 20 个阶段 8 新增接口（`API-103 ~ API-122`）全部交付；与阶段 3/5 历史接口（`API-123 ~ API-125`）严格闭环于 125 规约上限，**绝无 `API-126` 及之后接口**。 |
| **专项与全量测试** | **【已完成】** | 20 项阶段 8 专项测试（`TEST-P8-01 ~ TEST-P8-20`）全部通过；全系统全量 **85 / 85 项测试全部通过（0 Failure, 0 Error, 0 Skip，耗时 29.8s）**。 |
| **物理数据表** | **【已完成】** | 全系统严格维持 **37 张物理表**（5 张必做支撑表，复用 `sys_operation_log`，无物理性能表，零 DDL）。 |
| **测试隔离整改** | **【已完成】** | 对 Phase5/6/7/Auth 测试引入专用测试数据隔离与基线快照比对断言；`application-test.yml` 彻底移除硬编码默认密码，强制使用环境变量 `${DB_PASSWORD}`；引入 `TestDatabaseSanityChecker` 强校验测试库连接。 |
| **正式库零写入** | **【已完成】** | 正式数据库 `internship_db` 严格只读保护，37 表、55 任务、315 选课、1 卷宗、1 成绩、0 预警、4 配置、69 用户、7905 日志（最大 ID 8003）、学生 ID=4 `token_version=57` 与 `update_time=2026-09-22 23:43:38` **100% 吻合，绝对零写入**。 |
| **前端生产构建** | **【已完成】** | `npm run build` 耗时 5.27s，`vue-tsc --noEmit` **0 错误**，顺利生成 `dist/index.html` 与全部 Chunk 产物。 |
| **前端真实浏览器验收** | **【已完成】** | 采用 Edge Headless + Puppeteer 真实启动前后端服务，执行 9 大端到端流程全部通过（**控制台致命错误 0**，留存 13 张高清交互截图）。 |
| **本地隔离容器部署编排** | **【配置就绪】** | `backend/Dockerfile`、`frontend/Dockerfile`、`docker-compose.yml`、`nginx.conf` 均已完整编写并校验（`docker compose config -q` **0 错误**），独立端口 3308，独立卷 `internship_docker_data`，独立网络 `internship_net`，仅连接 `internship_db_test`。 |

---

## 二、 阶段 8 五大切片落地与接口矩阵

### 2.1 切片 1：动态运维配置模块 (`sys_config`)
- **交付接口**：
  - `GET /api/v1/system/configs` (`API-103`)：超管只读查阅运维配置；
  - `PUT /api/v1/system/configs/{key}` (`API-104`)：动态修改配置，更新前快照写入操作审计并同步逐出 Caffeine 缓存；
  - `POST /api/v1/system/configs/{key}/reset` (`API-105`)：一键出厂重置为 `application.yml` 默认值。
- **白名单运维参数（严格限定 4 项，不动态覆盖阶段 6/7 业务参数）**：
  1. `system.slow-sql-threshold-ms` (默认 500ms)
  2. `system.backup.retention-days` (默认 30天)
  3. `system.backup.rate-limit-seconds` (默认 30秒)
  4. `system.job.execution-timeout-seconds` (默认 60秒)
- **对应测试**：`TEST-P8-01 ~ TEST-P8-05`。

### 2.2 切片 2：受限白名单定时任务 (`sys_job`)
- **交付接口**：
  - `GET /api/v1/system/jobs` (`API-106`)：只读查询白名单定时任务；
  - `POST /api/v1/system/jobs/{id}/trigger` (`API-107`)：受管 Bean 手动触发单次调度并记录审计；
  - `PUT /api/v1/system/jobs/{id}/toggle` (`API-108`)：启停定时任务。
- **安全与生命周期规约**：
  - 任务编码严格限定为白名单枚举：`WARN_SCAN_JOB`、`ARCHIVE_EXPIRE_RECLOCK_JOB`、`BACKUP_CLEANUP_JOB`；严禁反射与任意方法调用；
  - `ARCHIVE_EXPIRE_RECLOCK_JOB` 严格仅限扫描 `status = 'SPECIAL_UNLOCKED'` 且已逾期的单条卷宗恢复重锁为 `ARCHIVED`，绝不修改其他正常历史卷宗。
- **对应测试**：`TEST-P8-06 ~ TEST-P8-09`。

### 2.3 切片 3：受控热备份管理 (`sys_backup_record`)
- **交付接口**：
  - `GET /api/v1/system/backup/records` (`API-109`)：查阅热备历史元数据；
  - `POST /api/v1/system/backup/execute` (`API-110`)：手动触发热备，30s 冷却防抖，参数数组隔离密码防命令行泄露；
  - `GET /api/v1/system/backup/download/{id}` (`API-111`)：`SafePathValidator` 规范化防跨目录穿越流式安全下载。
- **对应测试**：`TEST-P8-10 ~ TEST-P8-12`。

### 2.4 切片 4：教学通知公告中心 (`sys_notice`, `sys_notice_read`)
- **交付接口**：
  - `GET /api/v1/system/notices` (`API-112`)：可见通知列表，包含用户已读状态与院系隔离；
  - `GET /api/v1/system/notices/{id}` (`API-113`)：查阅详情，自动向 `sys_notice_read` 记已读；
  - `POST /api/v1/system/notices` (`API-114`)：人工发布，`dedup_key` 唯一键防重，服务端 Jsoup Safelist XSS 深度过滤；
  - `PUT /api/v1/system/notices/{id}` (`API-115`)：修改/撤回通知，越权 403 严格拦截。
- **业务规约**：先行限定为人工发布模式，禁止模糊标题内容去重，暂不开启自动轮询。
- **对应测试**：`TEST-P8-13 ~ TEST-P8-14`。

### 2.5 切片 5：性能监控与安全审计 (基于内存 RingBuffer 与 `sys_operation_log` 复用)
- **交付接口**：
  - `GET /api/v1/system/monitor/server` (`API-116`)：CPU 核心/负载、JVM 堆/非堆、磁盘与运行时间；
  - `GET /api/v1/system/monitor/cache` (`API-117`)：本地 Caffeine 缓存命中率与大小监控；
  - `POST /api/v1/system/monitor/cache/clear` (`API-118`)：超管手动逐出缓存，写入审计留痕；
  - `GET /api/v1/system/monitor/slow-sql` (`API-119`)：纯内存有界 RingBuffer 滑动窗口（最多 1000 样本、500 慢记录、24h 留存），最近秩算法计算 P95/P99 与 QPS；
  - `GET /api/v1/system/logs/login` (`API-120`)：复用 `sys_operation_log` 查询登录/注销安全审计；
  - `GET /api/v1/system/logs/operation` (`API-121`)：高危操作审计，DEPT_ADMIN 关联院系过滤并排除系统调度（`operator_id != 0`），IP 掩码脱敏与异常堆栈屏蔽；
  - `GET /api/v1/system/security/tokens` & `POST /tokens/{userId}/kick` (`API-122`)：在线会话监控；目标用户 `token_version` 原子自增导致旧 JWT 立即失效（401 拦截）。
- **对应测试**：`TEST-P8-15 ~ TEST-P8-20`。

---

## 三、 前端真实浏览器端到端验收结果 (9/9 PASS, 0 错误)

验收脚本使用 `puppeteer-core` 驱动真实 Edge 内核，后端连接专用测试数据库 `internship_db_test`，全流程模拟真实用户行为，测试步骤与结果如下：

| 步骤编号 | 验证场景与业务操作 | 状态 | 关键校验点与防御逻辑 | 存盘截图证据 |
| :---: | :--- | :---: | :--- | :--- |
| **[1]** | `NoticeManage` 发布新通知 (API-114) | **PASS** | `dedup_key` 唯一键生成、全校公告广播、实时预览安全过滤 | `phase8_notice_manage_published.png` |
| **[2]** | `NoticeManage` 详情查阅 (API-113) | **PASS** | 自动记录 `is_read = true` 与阅读时间，DOMPurify 净化富文本 | `phase8_notice_detail_admin.png` |
| **[3]** | `NoticeManage` 撤回通知 (API-115) | **PASS** | 状态变更为 `0 (已撤回)`，二重确认交互框 | `phase8_notice_revoked.png` |
| **[4]** | `NoticeManage` 重新发布 (API-115) | **PASS** | 状态恢复为 `1 (正常发布)`，二重确认交互框 | `phase8_notice_republished.png` |
| **[5]** | `ServerMonitor` 服务器/JVM监控 (API-116) | **PASS** | 物理核心、CPU 负载率、JVM 内存水位条、磁盘空间与运行时间 | `phase8_monitor_server_jvm.png` |
| **[6]** | `ServerMonitor` 本地缓存监控与清空 (API-117/118) | **PASS** | 命中率、预估大小、驱逐次数；一键清空弹窗二次确认与成功吐司 | `phase8_monitor_cache.png`<br>`phase8_monitor_cache_cleared.png` |
| **[7]** | `ServerMonitor` 慢调用与 P95/P99 看板 (API-119) | **PASS** | 内存 RingBuffer 滑动窗口采样、P95/P99 耗时计算、慢调用流水表格 | `phase8_monitor_slow_sql.png` |
| **[8]** | `ServerMonitor` 安全审计与在线会话 (API-120~122) | **PASS** | 在线用户 Token 清单、超管/自身踢出防护、登录安全审计流水、高危操作审计流水 | `phase8_monitor_security_tokens.png`<br>`phase8_monitor_login_logs.png`<br>`phase8_monitor_op_logs.png` |
| **[9]** | `NoticeDrawer` 学生端未读红点与抽屉查阅 | **PASS** | 顶栏铃铛实时徽标、通知抽屉卡片渲染、`<script>` 标签彻底净化剔除 | `phase8_notice_drawer_student.png`<br>`phase8_notice_drawer_detail.png` |

> **安全与清理验证**：
> 1. 控制台致命错误数：**0**；
> 2. XSS 恶意脚本注入测试（`<script>alert("xss")</script>`）：经服务端 Jsoup Safelist 与前端 DOMPurify 双重过滤，富文本渲染中恶意脚本被 100% 剥离；
> 3. 验收临时数据清理：测试完毕后执行测试通知物理级清理，`internship_db_test` 中 `sys_notice` 和 `sys_notice_read` 恢复为 0 条；正式库 `internship_db` 维持零写入。

---

## 四、 本地隔离 Docker 部署编排工程规范与演练方案

### 4.1 编排架构与环境现状
- **环境评估**：
  - 宿主机已安装 Docker CLI (`Docker 29.5.2`) 与 Docker Compose (`v5.1.4`)，WSL2 (`docker-desktop`) 运行正常；
  - 宿主机 Windows 服务 `com.docker.service` 需管理员权限（UAC）启动（非特权终端执行 `net start` 报系统错误 5 拒绝访问），据实报告宿主机限制，不假设服务常驻；
  - 编排配置（`docker-compose.yml`）语法通过校验，完全满足独立隔离运行规约。

### 4.2 容器隔离与安全边界规约
1. **网络与端口隔离**：
   - 数据库暴露独立宿主机端口：`${DOCKER_DB_PORT:-3308}:3306`（避免与本地 3306 冲突）；
   - 后端暴露端口：`${DOCKER_BACKEND_PORT:-8088}:8080`；
   - 前端 Nginx 暴露端口：`${DOCKER_FRONTEND_PORT:-8888}:80`；
   - 独立内部桥接网络：`internship_net`。
2. **持久化卷隔离**：
   - 使用独立命名卷 `internship_docker_data`，**严禁自动执行 `docker compose down -v`**。
3. **数据基准隔离**：
   - 仅挂载初始化测试脚本 `./docker/mysql/init/`（包含 37 张表全量结构 `01_schema.sql` 与基础测试基准 `02_seed.sql`）；
   - 严禁导入正式库备份文件。
4. **服务健康探针**：
   - MySQL: `mysqladmin ping -h 127.0.0.1 -u root -p$${MYSQL_ROOT_PASSWORD}`；
   - 后端: `curl -f http://127.0.0.1:8080/api/v1/health`；
   - 前端 Nginx: `wget -qO- http://127.0.0.1/health`。

### 4.3 演练标准操作命令 (Runbook)
```bash
# 1. 复制环境变量模板并填入本地演练密码 (严禁提交敏感密码至 Git)
cp .env.example .env

# 2. 启动 Docker Desktop (以 Windows 管理员权限)
net start com.docker.service

# 3. 校验 Compose 配置
docker compose config -q

# 4. 构建并启动容器集群
docker compose up -d --build

# 5. 查看运行状态与健康探活
docker compose ps

# 6. 验证健康检查接口
curl http://localhost:8888/api/v1/health

# 7. 演练结束停止服务 (严禁使用 -v 参数，防止误删数据卷)
docker compose stop
```

---

## 五、 阶段总结与阶段 9 准入说明

1. **阶段 8 全局工作已达成**：
   - 后端 20 个接口全量交付，全系统 85/85 项自动化测试 100% 通过；
   - 全系统闭环为 37 张物理表，正式库 `internship_db` 100% 保持只读未写；
   - 前端真实浏览器端到端 9 项测试全部顺利通过，13 张交互截图全量存档；
   - 本地隔离 Docker 部署全套编排产物就绪并完成语法核验。
2. **阶段 9 状态**：
   - 阶段 9《毕业设计答辩材料整理》当前保持**【暂停】**状态，待用户指令明确后再行启动。
