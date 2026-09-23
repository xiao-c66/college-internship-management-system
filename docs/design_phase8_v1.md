# 高校实习全过程管理系统 - 阶段8《系统优化与全量部署验收》实施方案设计（第1版·历史待审草案）

> [!NOTE]
> **文档历史定位与定稿实施对齐说明**：
> - **本文档性质**：本文档为阶段8启动前编制的**第1版待审草案**，记录了早期的设想（如规划10张支撑表与42张表上限、sys_dict_type字典表、多级Redis缓存等）；
> - **后续优化与定稿演进**：在后续实际评审与切片实施中，技术方案做出了定点架构优化（将物理支撑表精简为5张必做表、全系统物理表闭环于37张、取消物理性能表并改用纯内存有界RingBuffer、取消Redis并采用有界单机Caffeine缓存）；
> - **最终实施与验收记录**：阶段8实际落地代码（Commit `52d29b2` / Tag `v8.0.0-phase8-sealed`）与85项自动化测试的最终技术契约及验收结果，请以 [docs/phase8_implementation_and_acceptance.md](file:///d:/devlop/IDEA/college-internship-management-system/docs/phase8_implementation_and_acceptance.md) 为准。

---

## 一、 阶段8 功能范围与核心设计目标

### 1.1 阶段定位与承接关系
本阶段为《高校实习全过程管理系统》的第8阶段——**《系统优化与全量部署验收》**。
- **前序基线**：阶段 1 至阶段 7 已完成全业务开发并通过全量验收（包含 32 张物理表、65 项后端集成测试通过、前端 Vite 生产构建 0 错误、真实 Chrome 浏览器 13 个核心页面 0 控制台错误全量通过、归档 ZIP 物理包 SHA-256 100% 吻合），已正式标记为**【已验收、已封板】**；
- **核心使命**：对已完成业务逻辑的系统进行生产级性能深度调优、多级缓存架构落地、高并发慢查询索引覆盖、系统运维与配置外置化、全盘操作日志与登录安全审计、全栈 Docker 容器化编排打包与多角色全量部署验收，为最终的“阶段9：毕业设计答辩材料整理”奠定坚如磐石的系统工程底座。

### 1.2 核心功能范围矩阵
| 功能模块 | 业务定位与核心技术目标 | 承载形式 | 状态标记 |
| :--- | :--- | :--- | :---: |
| **1. 多级缓存与配置外置** | 字典常量、系统核心阈值、等第规则、用户信息本地 Caffeine + Redis 缓存；系统全局运行参数动态读取与维护（支持无重启热刷新）。 | 架构优化 + 支撑模块 | 待审查、待实施 |
| **2. 数据库性能与索引覆盖** | 全盘执行计划（`EXPLAIN`）分析，高频关联表（任务、周报、预警、成绩）建立覆盖索引；长效消除文件排序（Using filesort）与临时表（Using temporary）。 | SQL 性能调优 | 待审查、待实施 |
| **3. 安全审计与高危拦截** | 登录鉴权日志（IP、归属地、终端 UA、认证结果留痕）；特批解锁、调分裁决、批量指派、重置密码等高危操作 100% 审计埋点；防暴力破解与 Token 动态踢出。 | 安全加固 | 待审查、待实施 |
| **4. 定时调度与自动化运维** | Spring `@Scheduled` 自动化调度引擎；定时全盘异常预警扫描（每日凌晨）、特批解锁卷宗 24 小时到期自动重锁闭环；定时任务启停与调度日志。 | 后台引擎 | 待审查、待实施 |
| **5. 容灾备份与健康监控** | 系统热备指令触发、备份文件元数据管理与流式安全导出；集成 Spring Boot Actuator 与 JVM/服务器资源/慢接口指标可视化。 | 系统支撑 | 待审查、待实施 |
| **6. 全局通知与站内信中心** | 支持全校/院系级通知公告发布；学生与教师端消息提醒与已读状态记录（解决预警/催办消息端侧触达）。 | 业务协同 | 待审查、待实施 |
| **7. 生产级容器化编排** | 多阶段 Dockerfile 瘦身打包（后端 JRE-slim、前端 Nginx-alpine）、`docker-compose.yml` 全栈一键启动、Nginx 生产安全配置与反向代理。 | 部署实施 | 待审查、待实施 |
| **8. 全量端到端验收** | 62 个全角色账号真实并发压测、冷热启动探针检测、灾难恢复还原演练、全量自动化测试覆盖。 | 验收测试 | 待审查、待实施 |

---

## 二、 阶段8 数据表规划（10 张系统支撑表，总表数闭环至 42 张）

为满足系统参数外置、数据字典管理、登录审计、定时任务调度、容灾备份与通知提醒需求，阶段 8 规划新增 10 张系统运维与支撑表。**物理表总数由当前的 32 张完整收敛至规划上限 42 张**。

### 2.1 数据表清单一览
| 序号 | 物理表名 | 中文表名 | 规划用途与约束规范 | 状态标记 |
| :---: | :--- | :--- | :--- | :---: |
| 1 | `sys_dict_type` | 系统字典类型表 | 统一管理系统字典编码、名称、状态，主键 `id`，`dict_type` 唯一索引 | 待审查、待实施 |
| 2 | `sys_dict_data` | 系统字典数据表 | 字典明细项、排序、回显样式，联合唯一索引 `uk_type_value` | 待审查、待实施 |
| 3 | `sys_config` | 系统全局参数表 | 系统运行级动态参数（预警阈值、最大上传、导出限流等），`config_key` 唯一索引 | 待审查、待实施 |
| 4 | `sys_login_log` | 用户登录安全审计表 | 记录登录账号、IP 地址、登录地点、浏览器/操作系统 UA、状态与提示 | 待审查、待实施 |
| 5 | `sys_job` | 系统定时任务配置表 | 任务名称、调用目标字符串、Cron 表达式、执行策略、并发状态 | 待审查、待实施 |
| 6 | `sys_job_log` | 定时任务调度日志表 | 任务调度执行轨迹、开始与耗时毫秒、执行状态、异常堆栈快照 | 待审查、待实施 |
| 7 | `sys_notice` | 系统全局通知公告表 | 通知标题、类型（通知/公告/提醒）、HTML 正文、发布人、生效时间 | 待审查、待实施 |
| 8 | `sys_notice_read` | 通知公告用户已读表 | 记录用户对具体通知的阅读时间戳，联合唯一索引 `uk_notice_user` | 待审查、待实施 |
| 9 | `sys_backup_record` | 数据库备份记录表 | 记录热备份文件路径、字节大小、包含表数、备份方式、SHA-256校验摘要 | 待审查、待实施 |
| 10 | `sys_performance_metric` | 核心接口性能指标表 | 记录各核心 API 的吞吐量、P95/P99 响应延迟、慢调用分布，支持运维监控 | 待审查、待实施 |

### 2.2 规划 DDL 规约草案（仅供审查，严禁执行）
```sql
-- ================================================================================================
-- 阶段8 物理数据表 DDL 规划草案 (待审查，严禁在数据库中执行)
-- ================================================================================================

-- 1. 系统字典类型表
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典主键ID',
  `dict_name` VARCHAR(100) NOT NULL COMMENT '字典名称',
  `dict_type` VARCHAR(100) NOT NULL COMMENT '字典类型编码',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-停用, 1-正常)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `created_by` VARCHAR(64) DEFAULT NULL,
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典类型表';

-- 2. 系统字典数据表
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典明细ID',
  `dict_type` VARCHAR(100) NOT NULL COMMENT '所属字典类型',
  `dict_label` VARCHAR(100) NOT NULL COMMENT '字典标签展示名',
  `dict_value` VARCHAR(100) NOT NULL COMMENT '字典键值',
  `dict_sort` INT NOT NULL DEFAULT 0 COMMENT '排序优先级',
  `css_class` VARCHAR(100) DEFAULT NULL COMMENT '样式属性 (Element Plus Tag类型)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-停用, 1-正常)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_value` (`dict_type`, `dict_value`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典数据表';

-- 3. 系统全局参数配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '参数主键ID',
  `config_name` VARCHAR(100) NOT NULL COMMENT '参数名称',
  `config_key` VARCHAR(100) NOT NULL COMMENT '参数键名',
  `config_value` TEXT NOT NULL COMMENT '参数键值',
  `is_system` TINYINT NOT NULL DEFAULT 1 COMMENT '系统内置 (0-否, 1-是不可删)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局参数配置表';

-- 4. 用户登录安全审计表
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '访问日志ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `ip_address` VARCHAR(128) NOT NULL COMMENT '登录IP地址',
  `login_location` VARCHAR(255) DEFAULT '内网IP' COMMENT '登录物理地点',
  `browser` VARCHAR(100) DEFAULT NULL COMMENT '浏览器类型',
  `os` VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  `status` TINYINT NOT NULL COMMENT '登录状态 (0-失败, 1-成功)',
  `message` VARCHAR(255) DEFAULT NULL COMMENT '提示消息/失败原因',
  `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`username`, `login_time`),
  KEY `idx_ip` (`ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录安全审计表';

-- 5. 系统定时任务配置表
CREATE TABLE IF NOT EXISTS `sys_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
  `job_group` VARCHAR(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` VARCHAR(500) NOT NULL COMMENT '调用目标字符串 (Bean.方法)',
  `cron_expression` VARCHAR(255) NOT NULL COMMENT 'Cron执行表达式',
  `misfire_policy` TINYINT NOT NULL DEFAULT 1 COMMENT '错失执行策略 (1-立即执行, 2-放弃)',
  `concurrent` TINYINT NOT NULL DEFAULT 0 COMMENT '是否并发 (0-禁止, 1-允许)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-暂停, 1-正常)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统定时任务配置表';

-- 6. 系统定时任务调度日志表
CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `job_id` BIGINT NOT NULL COMMENT '任务ID',
  `job_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
  `invoke_target` VARCHAR(500) NOT NULL COMMENT '调用目标',
  `job_message` VARCHAR(500) DEFAULT NULL COMMENT '日志信息',
  `status` TINYINT NOT NULL COMMENT '执行状态 (0-失败, 1-成功)',
  `exception_info` TEXT DEFAULT NULL COMMENT '异常信息堆栈',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `cost_ms` BIGINT NOT NULL COMMENT '耗时(毫秒)',
  PRIMARY KEY (`id`),
  KEY `idx_job_time` (`job_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统定时任务调度日志表';

-- 7. 系统全局通知公告表
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `notice_type` VARCHAR(32) NOT NULL COMMENT '类型 (NOTICE-通知, ANNOUNCE-公告, REMIND-提醒)',
  `notice_content` LONGTEXT NOT NULL COMMENT '公告正文 (HTML/Markdown)',
  `target_scope` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '发布范围 (ALL-全校, DEPT-本系, ROLE-指定角色)',
  `target_dept_id` BIGINT DEFAULT NULL COMMENT '限定院系ID',
  `target_role` VARCHAR(64) DEFAULT NULL COMMENT '限定角色',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (0-关闭, 1-正常发布)',
  `publisher_id` BIGINT NOT NULL COMMENT '发布人ID',
  `publisher_name` VARCHAR(64) NOT NULL COMMENT '发布人姓名',
  `publish_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_scope` (`target_scope`, `target_dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局通知公告表';

-- 8. 通知公告用户已读标记表
CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notice_id` BIGINT NOT NULL COMMENT '公告ID',
  `user_id` BIGINT NOT NULL COMMENT '阅读用户ID',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告用户已读标记表';

-- 9. 数据库备份与容灾快照元数据表
CREATE TABLE IF NOT EXISTS `sys_backup_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '备份ID',
  `backup_file_name` VARCHAR(255) NOT NULL COMMENT '备份文件名',
  `backup_file_path` VARCHAR(500) NOT NULL COMMENT '物理存储绝对路径',
  `file_size_bytes` BIGINT NOT NULL COMMENT '文件字节大小',
  `table_count` INT NOT NULL COMMENT '包含数据表数',
  `backup_mode` VARCHAR(32) NOT NULL DEFAULT 'FULL' COMMENT '备份模式 (FULL-全量, DML_ONLY-仅数据)',
  `sha256_digest` VARCHAR(64) NOT NULL COMMENT '备份文件SHA-256摘要',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态 (SUCCESS-成功, FAILED-失败)',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败错误信息',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID (0为系统定时触发)',
  `backup_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份完成时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库备份记录表';

-- 10. 核心接口性能指标与慢调用统计表
CREATE TABLE IF NOT EXISTS `sys_performance_metric` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '统计ID',
  `api_code` VARCHAR(32) NOT NULL COMMENT '接口编号 (如API-098)',
  `api_path` VARCHAR(255) NOT NULL COMMENT '接口URL路径',
  `http_method` VARCHAR(16) NOT NULL COMMENT 'HTTP请求方法',
  `call_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计调用次数',
  `avg_cost_ms` DOUBLE NOT NULL DEFAULT 0 COMMENT '平均耗时(毫秒)',
  `p95_cost_ms` DOUBLE NOT NULL DEFAULT 0 COMMENT 'P95耗时(毫秒)',
  `p99_cost_ms` DOUBLE NOT NULL DEFAULT 0 COMMENT 'P99耗时(毫秒)',
  `max_cost_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '最大单次耗时(毫秒)',
  `stat_date` DATE NOT NULL COMMENT '统计归档日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_date` (`api_code`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核心接口性能指标表';
```

---

## 三、 RESTful 接口规范（API-103 ~ API-125，闭环 125 个全量接口）

本方案规划 23 个系统优化、参数管理、日志审计、任务调度与通知交互接口，编号承接阶段 7（截止于 API-102），完整覆盖至 **API-125**，**绝不新增超出 125 范围的非规约接口**。

| 接口编号 | 请求方法 | 接口路径 | 授权角色 | 业务说明与核心边界规约 | 状态标记 |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **API-103** | `GET` | `/api/v1/system/configs` | `SYS_ADMIN` | 查询系统全局参数列表：支持键名/参数名模糊搜索与分类筛选 | 待审查、待实施 |
| **API-104** | `PUT` | `/api/v1/system/configs/{key}` | `SYS_ADMIN` | 修改系统参数：校验格式有效性，同步刷新本地内存/Redis 缓存 | 待审查、待实施 |
| **API-105** | `GET` | `/api/v1/system/dicts/types` | 登录用户 | 查询字典类型列表与明细：优先走 Caffeine 缓存，支持全量预加载 | 待审查、待实施 |
| **API-106** | `POST` | `/api/v1/system/dicts/data` | `SYS_ADMIN` | 新增/修改字典明细项：防重复校验，原子触发字典本地缓存逐出 | 待审查、待实施 |
| **API-107** | `GET` | `/api/v1/system/logs/login` | `SYS_ADMIN` | 分页检索登录安全审计日志：支持用户名、IP、时间范围与状态过滤 | 待审查、待实施 |
| **API-108** | `GET` | `/api/v1/system/jobs` | `SYS_ADMIN` | 查询定时任务配置列表：展示 Cron 表达式、当前状态与下次执行时间 | 待审查、待实施 |
| **API-109** | `POST` | `/api/v1/system/jobs/{id}/trigger` | `SYS_ADMIN` | 手动单次触发定时调度：异步提交至任务线程池，记录独立调度日志 | 待审查、待实施 |
| **API-110** | `PUT` | `/api/v1/system/jobs/{id}/toggle` | `SYS_ADMIN` | 启动/挂起定时任务：动态变更调度器注册状态，写入操作审计 | 待审查、待实施 |
| **API-111** | `GET` | `/api/v1/system/jobs/{id}/logs` | `SYS_ADMIN` | 分页查询指定定时任务的历史调度日志与执行耗时轨迹 | 待审查、待实施 |
| **API-112** | `GET` | `/api/v1/system/backup/records` | `SYS_ADMIN` | 检索数据库备份历史记录：展示文件路径、大小、表数与 SHA-256 摘要 | 待审查、待实施 |
| **API-113** | `POST` | `/api/v1/system/backup/execute` | `SYS_ADMIN` | 手动触发数据库热备份：防并发抖动（30秒冷却），执行完整性校验与存盘 | 待审查、待实施 |
| **API-114** | `GET` | `/api/v1/system/backup/download/{id}` | `SYS_ADMIN` | 安全下载数据库备份文件：校验文件存在性与路径穿越防护，流式下载 | 待审查、待实施 |
| **API-115** | `GET` | `/api/v1/system/notices` | 登录用户 | 分页获取当前角色可见的通知公告列表：自动附加当前用户已读状态 | 待审查、待实施 |
| **API-116** | `GET` | `/api/v1/system/notices/{id}` | 登录用户 | 获取通知公告详情：标记当前用户阅读，并记录已读时间戳 | 待审查、待实施 |
| **API-117** | `POST` | `/api/v1/system/notices` | `DEPT_ADMIN`, `SYS_ADMIN` | 发布通知公告：院系管理员仅限发本系，校级管理员可全校广播 | 待审查、待实施 |
| **API-118** | `PUT` | `/api/v1/system/notices/{id}` | `DEPT_ADMIN`, `SYS_ADMIN` | 修改/撤回通知公告：非发布人或非超管禁止篡改（403 拦截） | 待审查、待实施 |
| **API-119** | `GET` | `/api/v1/system/monitor/server` | `SYS_ADMIN` | 获取服务器硬件资源与 JVM 运行时监控指标（CPU、内存、JVM、磁盘） | 待审查、待实施 |
| **API-120** | `GET` | `/api/v1/system/monitor/cache` | `SYS_ADMIN` | 获取多级缓存命中率、键值总数、内存占用与命中统计 | 待审查、待实施 |
| **API-121** | `POST` | `/api/v1/system/monitor/cache/clear` | `SYS_ADMIN` | 手动清理指定业务前缀缓存：支持按前缀或全局清空，防击穿保护 | 待审查、待实施 |
| **API-122** | `GET` | `/api/v1/system/monitor/slow-sql` | `SYS_ADMIN` | 慢查询与接口性能排名看板：展示耗时 >500ms 的慢接口与慢 SQL 频次 | 待审查、待实施 |
| **API-123** | `GET` | `/api/v1/system/logs/operation` | `SYS_ADMIN`, `DEPT_ADMIN` | 操作审计日志检索（阶段5已对接写，阶段8补齐读与筛选导出） | 待审查、待实施 |
| **API-124** | `GET` | `/api/v1/system/actuator/health` | 登录用户/探针 | 综合健康探测：探活 MySQL 连接池、磁盘可用容量与调度器状态 | 待审查、待实施 |
| **API-125** | `GET` | `/api/v1/system/security/tokens` | `SYS_ADMIN` | 在线活跃会话管理：查看活跃 Token 列表，支持超管强制踢下线 | 待审查、待实施 |

---

## 四、 前端页面与交互规划（系统运维与监控视图）

### 4.1 页面组件与视图规划
阶段 8 新增 5 个运维与监控专用视图，以及 1 个消息通知抽屉组件：

| 组件文件 | 对应路由路径 | 授权角色 | 视图核心职责与功能特性 | 状态标记 |
| :--- | :--- | :--- | :--- | :---: |
| `SystemConfig.vue` | `/admin/system/config` | `SYS_ADMIN` | 系统运行参数与性能配置维护；一键刷新配置缓存；参数重置与备份 | 待审查、待实施 |
| `DictManage.vue` | `/admin/system/dict` | `SYS_ADMIN` | 字典类型与键值列表管理；Element Tag 颜色回显预览；字典缓存清除 | 待审查、待实施 |
| `JobManage.vue` | `/admin/system/jobs` | `SYS_ADMIN` | 定时任务启停控制、Cron 表达式生成与即时触发、历史调度日志弹窗 | 待审查、待实施 |
| `BackupManage.vue` | `/admin/system/backup` | `SYS_ADMIN` | 数据库全量热备份触发、备份记录清单、SHA-256 核验、备份安全下载 | 待审查、待实施 |
| `ServerMonitor.vue` | `/admin/system/monitor` | `SYS_ADMIN` | 服务器 CPU/内存仪表盘、JVM 堆内存折线图、接口 P95 耗时与慢 SQL 分析 | 待审查、待实施 |
| `NoticeDrawer.vue` | 顶栏集成组件 | 全员可见 | 导航栏通知铃铛与抽屉：实时未读公告红点徽标、未读公告列表、点击标记已读 | 待审查、待实施 |

### 4.2 路由集成规约（遵守现有路由守卫架构）
- 路由挂载在 `frontend/src/router/index.ts` 的 `DefaultLayout` 子路由中；
- 严格遵循现行 `router.beforeEach` 鉴权与角色的守卫逻辑（非 `SYS_ADMIN` 角色访问自动重定向至 `/403`）；
- 保留并延续既有的 Vue Router `alias` 别名兼容策略。

---

## 五、 角色权限与数据安全隔离矩阵（RBAC）

| 功能模块 / 接口端点 | 学生 (STUDENT) | 教师 (TEACHER) | 院系管理员 (DEPT_ADMIN) | 系统管理员 (SYS_ADMIN) | 越权与隔离防护策略 |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **系统参数维护 (API-103~104)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 读写 | 后端 Spring Security 注解严格校验 `hasRole('SYS_ADMIN')` |
| **字典数据管理 (API-105~106)** | 仅只读字典 | 仅只读字典 | 仅只读字典 | ✅ 完整维护 | 修改/新增字典严格限制超管 |
| **登录安全审计 (API-107)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 读与检索 | 涉及敏感 IP 与认证轨迹，严禁下级角色查验 |
| **定时调度管理 (API-108~111)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 完整控制 | 严防未授权人员触发批量扫描或更改 Cron 表达式 |
| **容灾备份管理 (API-112~114)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 完整控制 | 备份文件下载加防穿越白名单与超管凭证硬校验 |
| **通知公告发布 (API-117~118)** | ❌ 403 | ❌ 403 | ✅ 仅限本院 | ✅ 全校广播 | 院系管理员只允许发布 `target_scope = 'DEPT'` 且绑定本系 |
| **通知公告查阅 (API-115~116)** | ✅ 可见范围 | ✅ 可见范围 | ✅ 可见范围 | ✅ 全量可见 | 按角色、院系多维数据范围过滤，未读状态按用户隔离 |
| **服务器性能监控 (API-119~122)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 读与清缓存 | 监控看板涉及服务器内网指标，严格限制超管访问 |
| **在线会话管理 (API-125)** | ❌ 403 | ❌ 403 | ❌ 403 | ✅ 强退下线 | 仅超管具备使他人 Token 强行失效权限 |

---

## 六、 阶段8 测试矩阵（TEST-P8-01 ~ TEST-P8-20 全量标记为待实施）

阶段 8 将创建专项集成测试类 `Phase8IntegrationTest.java`，共规划 20 项核心自动化测试。**全部严格标记为“待审查、待实施”**。

| 测试编号 | 测试用例名称与断言目标 | 验证范围与核心断言依据 | 当前标记 |
| :---: | :--- | :--- | :---: |
| **TEST-P8-01** | 系统参数配置读取与缓存热加载验证 | 验证 `API-103/104` 读写，更新后本地缓存同步失效，无需重启立即生效 | 待审查、待实施 |
| **TEST-P8-02** | 字典类型与数据明细多级缓存性能断言 | 验证 `API-105` 连续高频调用命中本地缓存，响应耗时 < 5ms | 待审查、待实施 |
| **TEST-P8-03** | 字典增改防重与非法键值阻断测试 | 重复 `dict_value` 插入触发 400 校验失败；非法入参阻断拦截 | 待审查、待实施 |
| **TEST-P8-04** | 登录审计日志真实入库与 IP 归属记录 | 模拟账号成功与密码错误登录，验证 `sys_login_log` 真实留痕与状态 | 待审查、待实施 |
| **TEST-P8-05** | 高危操作审计埋点全覆盖断言 (API-123) | 验证特批解锁、调分裁决、指派教师触发时自动写入 `sys_operation_log` | 待审查、待实施 |
| **TEST-P8-06** | 定时任务调度器加载与 Cron 校验 | 验证 `sys_job` 配置解析、Cron 格式校验；非法 Cron 返回 400 拦截 | 待审查、待实施 |
| **TEST-P8-07** | 手动触发定时任务调度与日志轨迹闭环 | 验证 `API-109` 触发单次执行，`sys_job_log` 成功记录开始时间与耗时 | 待审查、待实施 |
| **TEST-P8-08** | 预警定时扫描引擎自动化联动执行测试 | 定时调度触发预警全盘扫描，验证异常工单自动生成并释放去重锁 | 待审查、待实施 |
| **TEST-P8-09** | 特批解锁卷宗 24 小时到期自动复冻引擎 | 模拟解锁卷宗逾期，调度引擎自动将其状态重置为 `FROZEN` 并锁死 | 待审查、待实施 |
| **TEST-P8-10** | 数据库全量热备份触发与文件生成校验 | 验证 `API-113` 备份输出，生成有效 SQL，记录进入 `sys_backup_record` | 待审查、待实施 |
| **TEST-P8-11** | 备份文件 SHA-256 完整性摘要与原子性 | 备份完成即时校验 SHA-256 散列，验证未损坏，防半写入机制有效 | 待审查、待实施 |
| **TEST-P8-12** | 备份文件防路径穿越安全下载测试 | 传入 `../../` 等恶意文件名时返回 400/403 阻断，白名单校验有效 | 待审查、待实施 |
| **TEST-P8-13** | 通知公告全校/院系级定向广播与过滤 | 院系管理员仅发本院；学生仅拉取匹配本院及全校公共公告 | 待审查、待实施 |
| **TEST-P8-14** | 通知公告已读状态记录与并发防重 | 学生点击阅读，`sys_notice_read` 唯一索引拦截重复记录，状态准确 | 待审查、待实施 |
| **TEST-P8-15** | 服务器健康与硬件指标探针采集断言 | 验证 `API-119/124` 返回 JVM 内存、线程数、磁盘余量，指标无异常 | 待审查、待实施 |
| **TEST-P8-16** | 慢 SQL 与接口性能耗时统计埋点测试 | 模拟高耗时调用，验证 `sys_performance_metric` 统计命中与耗时记录 | 待审查、待实施 |
| **TEST-P8-17** | 在线活跃会话查询与强制踢下线测试 | 超管调用 `API-125` 注销目标用户 Token，被踢用户再次请求返回 401 | 待审查、待实施 |
| **TEST-P8-18** | 阶段8全部接口越权与安全拦截矩阵 (403) | 学生与教师尝试调用系统配置/任务/备份接口，全部断言返回 403 | 待审查、待实施 |
| **TEST-P8-19** | 历史全量 65 项测试向后兼容性回归 | 执行全部历史测试，断言 65 项无一失败（0 Failure, 0 Error, 0 Skip） | 待审查、待实施 |
| **TEST-P8-20** | 前端生产构建与 TypeScript 编译零错误 | 执行生产打包 `npm run build`，断言 0 TS 错误且产物结构完整 | 待审查、待实施 |

---

## 七、 全量部署架构与容器化编排方案

### 7.1 多阶段 Docker 构建规约
1. **后端 Dockerfile (`backend/Dockerfile`)**：
   - 阶段 1：`maven:3.9-eclipse-temurin-17-alpine` 缓存依赖并执行快速编译打包；
   - 阶段 2：`eclipse-temurin:17-jre-alpine` 最小化运行镜像（体积控制在 200MB 以内），非 root 用户运行，内置中文字体与健康探针。
2. **前端 Dockerfile (`frontend/Dockerfile`)**：
   - 阶段 1：`node:20-alpine` 快速构建 Vite 生产静态资源产物；
   - 阶段 2：`nginx:1.25-alpine` 静态托管，内置 Gzip 压缩、SPA 路由转发（`try_files $uri $uri/ /index.html`）与安全标头。

### 7.2 Docker Compose 一键编排草案 (`docker-compose.yml`)
- 服务包含：`db` (MySQL 8.0)、`backend` (Spring Boot 3.2 服务)、`frontend` (Nginx 前端服务)；
- 环境变量统一管理（`.env` 文件隔离，零敏感信息硬编码）；
- 容器间网络健康依赖（`depends_on: db: condition: service_healthy`），确保开箱即用、一键拉起。

---

## 八、 阶段边界与执行红线

1. **与阶段7的严格边界**：
   - 阶段7代码、数据模型与业务规则为不可篡改的基石；
   - 阶段8的所有系统支撑表均为非破坏性纯新增（`CREATE TABLE IF NOT EXISTS`），绝不修改或删除任何既有表字段；
   - 绝不调整阶段7已通过的前置诊断、五维成绩加权公式或归档 PDF 模版。
2. **与阶段9的严格边界**：
   - 阶段8聚焦于“系统性能优化、高可用运维与容器化部署验收”；
   - 所有答辩演示材料、系统设计论文配图、答辩 PPT 与用户手册统一归入“阶段9：毕业设计答辩材料整理”执行。
3. **下一步执行红线**：
   - **方案输出后立即停下，保持只读**；
   - 未经人工审查、反馈与明确批准指令，绝不进入编码实施，绝不执行数据库建表，绝不拉起容器部署。
