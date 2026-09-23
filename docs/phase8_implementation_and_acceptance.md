# 高校实习全过程管理系统 - 阶段8《系统优化与本地隔离容器部署演练》实施与验收归档记录

> [!IMPORTANT]
> **文档性质与版本基线声明**：
> - **本文档性质**：本文档为基于已提交归档实现（Git Commit: `52d29b2`，Tag: `v8.0.0-phase8-sealed`）与 85 项自动化回归测试真实证据整理的阶段 8 正式实施与验收归档记录。
> - **文档来源说明**：阶段 8 开发实施期间曾以内部方案工件推进，本文件为代码仓库内正式纳管的定稿实施规范、技术契约与验收基线文档，作为后续验收与复核的唯一依据。
> - **阶段边界定义（红线锁定）**：
>   - **阶段 8** 准确定义为**《系统优化与本地隔离容器部署演练》**，包含后端性能监控与安全审计、系统运维、通知管理、测试数据隔离、**前端浏览器人工验收（待开展）**以及**本地隔离容器部署演练 Docker/Nginx（待实施）**。严禁将 Docker 演练擅自更改编号为阶段 9；
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
| **源码提交归档** | **【已完成】** | 全部 77 个新增及修改文件已归档至 Git 独立提交 `52d29b2`，打上 Release 标签 `v8.0.0-phase8-sealed`，工作树绝对干净。 |
| **前端浏览器人工验收** | **【未完成】** | `ServerMonitor.vue`、`NoticeManage.vue`、`NoticeDrawer.vue` 尚未在真实浏览器运行环境下进行人工视觉截屏与交互验收（属于阶段 8 待验收项）。 |
| **本地隔离容器部署演练** | **【未实施】** | 宿主机尚未编写 `Dockerfile`、`docker-compose.yml` 及 `nginx.conf`，容器环境从未拉起（属于阶段 8 待实施项）。 |

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

## 三、 数据库 37 张物理表架构全景

阶段 8 通过复用 `sys_operation_log`（承载登录安全审计、高危操作审计与调度日志）并采用纯内存 RingBuffer 替代物理性能表，全系统物理表由阶段 7 的 32 张精简扩充 5 张必做支撑表，**严格闭环于 37 张物理表**（DDL 详见 `docs/sql/schema_37tables.sql`）：

1. **基础组织与用户 (7 张)**: `base_department`, `base_major`, `base_class`, `sys_role`, `sys_user`, `sys_user_role`, `sys_operation_log`
2. **任务配置与安全准入 (11 张)**: `internship_task`, `internship_task_major`, `internship_task_class`, `internship_task_student`, `safety_material_item`, `safety_test_question`, `safety_exam_attempt`, `safety_exam_answer_detail`, `safety_commitment_sign`, `internship_apply`, `apply_audit_history`
3. **周报与过程指导 (3 张)**: `internship_weekly_report`, `internship_guidance_record`, `weekly_student_feedback`
4. **质控、预警、成绩与归档 (11 张)**: `midterm_inspection_plan`, `midterm_inspection`, `midterm_rectification`, `student_material_item`, `student_material_version`, `warn_rule`, `warn_ticket`, `warn_handle_history`, `score_summary`, `score_appeal`, `internship_archive`
5. **阶段 8 运维支撑 (5 张)**: `sys_config`, `sys_job`, `sys_backup_record`, `sys_notice`, `sys_notice_read`

---

## 四、 全量 85 项自动化测试验证基准

Surefire 原始报告记录（`backend/target/surefire-reports/`）：
```
com.college.internship.AuthIntegrationTest          : 8 tests, 0 failures, 0 errors, 0 skipped
com.college.internship.InternshipApplicationTests   : 1 tests, 0 failures, 0 errors, 0 skipped
com.college.internship.Phase5IntegrationTest        : 14 tests, 0 failures, 0 errors, 0 skipped
com.college.internship.Phase6IntegrationTest        : 18 tests, 0 failures, 0 errors, 0 skipped
com.college.internship.Phase7IntegrationTest        : 24 tests, 0 failures, 0 errors, 0 skipped
com.college.internship.Phase8IntegrationTest        : 20 tests, 0 failures, 0 errors, 0 skipped
-------------------------------------------------------------------------------------------------
【全量汇总】: 85 tests run, 0 Failures, 0 Errors, 0 Skipped (100% 通过率，耗时 29.8s)
```

---

## 五、 阶段 8 尚待开展的工作与下一步指引

### 1. 待开展验收项
1. **前端运维管理界面真实浏览器人工视觉与交互验收【未完成】**：
   - 包含：`ServerMonitor.vue` 各 Tab 页面渲染与一键清理缓存/踢人弹窗、`NoticeManage.vue` 富文本发布与 XSS 过滤预览、`NoticeDrawer.vue` 顶栏未读铃铛抽屉；
   - 需在真实浏览器环境中启动服务，执行人工验证并留存截图证据。
2. **本地隔离容器部署演练 (Docker/Nginx)【未实施】**：
   - 包含：多阶段 `Dockerfile` 编制（后端 Temurin JRE-17、前端 Nginx-alpine）、`docker-compose.yml` 隔离编排（宿主机 3308 映射端口、数据卷物理隔离、阶段 7 备份只读挂载）、`nginx.conf` 路由反代配置；
   - 需在 Windows 宿主机上执行本地容器拉起演练与无损销毁核验。

### 2. 阶段边界与下一步执行顺序
- **下一步必须执行的任务**：**阶段 8 本地隔离容器部署演练（及前端浏览器人工验收）**；
- **绝对禁止的操作**：禁止跳过阶段 8 部署演练直接进入阶段 9；只有当阶段 8 的 Docker 容器部署演练在本地隔离环境下验证成功后，方可正式开启阶段 9《毕业设计答辩材料整理》。
