# 高校实习全过程管理系统 - 阶段验收与归档记录

## 一、 里程碑阶段状态总览

- **阶段1至阶段7**：**【已验收、已封板 (ACCEPTED & FROZEN, Tag: `v7.0.0-phase7-sealed`)】**。
- **阶段8《系统优化与本地隔离容器部署演练》**：
  - **核心功能开发与全量自动化测试**：**【已完成、已归档 (Commit: `52d29b2`, Tag: `v8.0.0-phase8-sealed`)】**（20 个接口落地、全量 85/85 项测试全通、测试隔离整改落地、37 张表零 DDL、正式库绝对零写入）；
  - **前端真实浏览器端到端验收**：**【已完成】**（9 大端到端流程全通，控制台致命错误 0，留存 13 张高分辨率全景交互截图，测试通知数据零残留清理）；
  - **本地隔离容器部署演练 (Docker/Nginx)**：**【配置与编排就绪】**（完成 `backend/Dockerfile`、`frontend/Dockerfile`、`docker-compose.yml`、`nginx.conf` 编制，`docker compose config -q` 0 错误；已明确宿主机 Windows 服务启动权限现状及演练 Runbook，严格限制在 3308 独立端口与独立卷）。
- **阶段9《毕业设计答辩材料整理》**：**【暂停，未启动】**。
- **前端视觉升级**：按既定流程延期至答辩材料就绪后执行。

---

## 二、 阶段 8 实施与验证结果

### 1. 范围与契约闭环
- **接口范围**：`API-103 ~ API-122`（共 20 个阶段 8 新增接口），与阶段 3/5 历史接口（`API-123 ~ API-125`）严格闭环于 125 规约上限，**绝无 `API-126` 及之后接口**。
- **物理数据表**：全系统严格维持 **37 张物理表**（5 张必做支撑表，复用 `sys_operation_log`，无物理性能表，零 DDL）。
- **测试隔离整改**：对 Phase5/6/7/Auth 测试引入专用测试数据隔离与基线快照比对断言；`application-test.yml` 彻底移除硬编码默认密码，强制使用环境变量 `${DB_PASSWORD}`；引入 `TestDatabaseSanityChecker` 强校验测试库连接。

### 2. 自动化测试回归结果 (85/85 全绿)
执行 `mvn.cmd test`（Surefire 原始报告验证记录）：
- `AuthIntegrationTest`：8 项通过 (13.79s)；
- `InternshipApplicationTests`：1 项通过 (1.44s)；
- `Phase5IntegrationTest`：14 项通过 (1.82s)；
- `Phase6IntegrationTest`：18 项通过 (2.56s)；
- `Phase7IntegrationTest`：24 项通过 (3.64s)；
- `Phase8IntegrationTest`：20 项通过 (3.49s)；
- **全量汇总**：**85 项全部通过，Failures 0、Errors 0、Skipped 0**，`BUILD SUCCESS`。

### 3. 前端真实浏览器端到端验收结果 (9/9 全绿)
执行 `node frontend/verify_phase8_browser.mjs`（基于 Edge Headless + Puppeteer）：
1. `NoticeManage` 发布新通知 (API-114)：PASS
2. `NoticeManage` 详情查阅与XSS防御渲染 (API-113)：PASS
3. `NoticeManage` 撤回通知 (API-115 status=0)：PASS
4. `NoticeManage` 重新发布通知 (API-115 status=1)：PASS
5. `ServerMonitor` 服务器与JVM指标监控 (API-116)：PASS
6. `ServerMonitor` 本地缓存监控与清空 (API-117/118)：PASS
7. `ServerMonitor` 慢调用与P95/P99度量看板 (API-119)：PASS
8. `ServerMonitor` 在线会话与登录/操作安全审计 (API-120~122)：PASS
9. `NoticeDrawer` 学生端铃铛未读红点与富文本净化查阅：PASS
- 控制台致命错误：**0**；
- 截图归档：13 张完整流程截图存于 `.gemini/antigravity/brain/.../screenshots/` 及 `frontend/test_screenshots_phase8/`。

### 4. 本地 Docker 部署编排工程产物
- `backend/Dockerfile`：多阶段构建，基于 Temurin 17 JRE 并预装 `fonts-noto-cjk` 字体与 `curl` 探活探针；
- `frontend/Dockerfile`：多阶段构建，Node.js 20 编译配合 Nginx 1.25 Alpine 部署静态资源；
- `docker-compose.yml`：独立桥接网络 `internship_net`，MySQL 3308 端口映射与独立命名卷 `internship_docker_data`，初始化测试数据库 `internship_db_test`；
- `nginx.conf`：配置 `/api/` 反向代理至后端容器与 `/health` 探针；
- 配置文件语法校验：`docker compose config -q` 退出码 0，无任何告警。

### 5. 数据库只读快照与零污染验证
- **正式数据库 (`internship_db`)**：37 表、55 任务、315 选课、1 卷宗、1 成绩、0 预警、4 配置、69 用户、7905 日志（最大 ID 8003）、学生 ID=4 `token_version=57` 与 `update_time=2026-09-22 23:43:38` **100% 保持一致，绝对零写入**。
- **测试数据库 (`internship_db_test`)**：37 表、4 项参数基准、3 项白名单调度基准、通知与阅读记录在测试结束后清理归零、审计日志合规留痕。

---

## 三、 历史阶段 7 验收成果基线回顾

- **阶段 7 业务代码**：`ArchiveService`, `ScoreService`, `WarnService`, `MaterialService`, `InspectService`, `RectifyService` 及其 Controller 与 Entity 100% 保持只读零修改；
- **阶段 7 归档物理包**: `ARC20252026_student.zip` (1,001,953 字节，SHA-256 完整性摘要与解压后实测哈希 100% 吻合)；
- **阶段 7 浏览器端到端验收**: 13 张高分辨率截图存档于 `.gemini/antigravity/brain/.../screenshots/`，控制台错误数 0。

---

## 四、 边界与下一步计划

1. **已封板归档内容**：阶段 8 代码归档于 Commit `52d29b2`，文档归档于 Commit `e0ab8a3`，版本标签为 `v8.0.0-phase8-sealed`。
2. **阶段 8 验收达成情况**：前端真实浏览器端到端验收已闭环，本地 Docker 部署方案与全套编排文件均已就绪。
3. **阶段 9 状态**：保持暂停，等待下一步启动指令。
