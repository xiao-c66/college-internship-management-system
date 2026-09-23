# 高校实习全过程管理系统 (College Internship Management System)

> 基于 **Spring Boot 3 + Java 17** 与 **Vue 3 + TypeScript + Vite + Element Plus** 构建的高校教务与学工全流程实习教学管理平台。

---

## 目录结构 (Repository Structure)

```
college-internship-management-system/
├── backend/                             # 后端服务工程 (Spring Boot 3 + Java 17 + Maven)
│   ├── src/main/java/com/college/internship/
│   │   ├── common/                      # 统一结果包装 Result、全局异常处理与业务枚举
│   │   ├── config/                      # 安全配置 SecurityConfig、OpenApiConfig
│   │   ├── controller/                  # 控制层（AuthController, DashboardController, HealthController）
│   │   ├── dto/                         # 数据传输对象（LoginDTO 等）
│   │   ├── entity/                      # 物理表实体（7张基础物理表实体与 BaseEntity）
│   │   ├── enums/                       # 枚举定义（UserTypeEnum 等）
│   │   ├── mapper/                      # MyBatis-Plus 数据访问接口
│   │   ├── security/                    # JWT 令牌签发解析、版本校验过滤器与安全处理器
│   │   ├── service/                     # 业务服务层接口与实现（AuthService, DashboardService, LogService）
│   │   ├── vo/                          # 视图表现对象（LoginVO, UserInfoVO, DashboardSummaryVO 等）
│   │   └── InternshipApplication.java  # 后端启动主类（@MapperScan 接入持久层）
│   ├── src/main/resources/              # 配置文件 application.yml, application-dev.yml
│   └── pom.xml                          # Maven 依赖与构建配置
├── frontend/                            # 前端工程 (Vue 3 + TypeScript + Vite + Element Plus)
│   ├── src/
│   │   ├── assets/                      # 静态资源
│   │   ├── components/                  # 公共业务组件
│   │   ├── layouts/                     # 页面布局容器（DefaultLayout 真实用户资料与退出）
│   │   ├── router/                      # Vue Router 4 路由与未登录/越权 403 路由守卫
│   │   ├── store/                       # Pinia 状态管理模块（useUserStore 真实会话持久化）
│   │   ├── styles/                      # 主题样式变量与全局拂晓蓝设计体系
│   │   ├── utils/                       # Axios 封装（Token 自动挂载与 401 拦截）
│   │   ├── views/                       # 视图组件（LoginView 真实联调、4类角色工作台）
│   │   ├── App.vue                      # 根组件
│   │   └── main.ts                      # 前端入口文件
│   ├── package.json                     # 前端依赖配置
│   └── vite.config.ts                   # Vite 构建与反向代理配置
├── docs/                                # 项目技术规范与数据库初始化脚本
│   ├── architecture.md                  # 系统架构与模块设计规约
│   ├── api_spec.md                      # RESTful 接口规范与健康探测
│   ├── development_guide.md             # 开发者本地快速启动与调试指南
│   └── sql/
│       └── init_phase4.sql              # 阶段4基础数据库初始化脚本（7张表可重复执行）
├── .gitignore                           # Git 忽略配置
└── README.md                            # 项目根说明文档
```

---

## 系统物理数据表架构 (Database Tables - 37张表全景闭环)

系统严格按照增量切片与规范闭环原则推进，当前物理数据表总数**精确闭环为 37 张物理表**（DDL 完整脚本详见 [`docs/sql/schema_37tables.sql`](file:///d:/devlop/IDEA/college-internship-management-system/docs/sql/schema_37tables.sql)），零多余物理表、零冗余性能表：

### 1. 基础组织与系统核心表 (阶段4，共7张)
`base_department` (院系), `base_major` (专业), `base_class` (班级), `sys_role` (角色), `sys_user` (用户), `sys_user_role` (用户角色), `sys_operation_log` (全盘操作与安全审计日志).

### 2. 实习任务与安全准入表 (阶段5，共11张)
`internship_task` (任务批次主表), `internship_task_major` (任务圈定专业), `internship_task_class` (任务圈定班级), `internship_task_student` (圈定学生与导师绑定), `safety_material_item` (规程资料), `safety_test_question` (客观题库), `safety_exam_attempt` (测试作答记录), `safety_exam_answer_detail` (逐题明细), `safety_commitment_sign` (安全责任知晓承诺书), `internship_apply` (实习申报主表·APPLY-009强锁定), `apply_audit_history` (审批流转历史快照).

### 3. 周报填报与过程指导表 (阶段6，共3张)
`internship_weekly_report` (实习周报主表与版本轨迹), `internship_guidance_record` (导师过程指导台账), `weekly_student_feedback` (学生针对性反馈确认与锁定).

### 4. 中期巡检、预警、成绩评定与电子归档表 (阶段7，共11张)
`midterm_inspection_plan` (检查方案与抽检配置), `midterm_inspection` (中期巡检记录), `midterm_rectification` (巡检整改单), `student_material_item` (阶段性过程材料), `student_material_version` (材料版本轨迹), `warn_rule` (异常预警规则快照), `warn_ticket` (预警工单), `warn_handle_history` (预警协同流转历史), `score_summary` (五维总评成绩汇总), `score_appeal` (成绩申诉与仲裁), `internship_archive` (电子归档卷宗与特批解锁).

### 5. 系统运维、调度、备份与通知表 (阶段8新增，共5张)
`sys_config` (系统运维参数配置), `sys_job` (受限白名单定时任务), `sys_backup_record` (受控热备份元数据与SHA-256校验), `sys_notice` (全局教学通知公告·dedup_key业务防重), `sys_notice_read` (通知用户阅读状态记录).

---

## 演示账号与权限矩阵 [DEMO ONLY]

系统内置 4 类核心业务角色演示账号，所有演示账号初始密码均为 `123456`，在数据库中均以标准 BCrypt 密文散列存储，严禁向生产提交真实凭据：

| 角色类型 | 演示登录名 | 演示姓名 | 初始密码 [DEMO] | 访问与工作台路由 | 权限职责说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **学生 (STUDENT)** | `student` | 张晓峰 [DEMO] | `123456` | `/dashboard/student` | 实习全生命周期导航、周报与安全教育状态追踪 |
| **指导教师 (TEACHER)** | `teacher` | 李教授 [DEMO] | `123456` | `/dashboard/teacher` | 负责学生基数汇总、周报打分、安全教育催办 |
| **院系负责人 (DEPT_ADMIN)** | `deptadmin` | 计算机负责人 [DEMO] | `123456` | `/dashboard/dept` | 全院师生与班级组织架构统计、二级终审复核 |
| **学校管理员 (SYS_ADMIN)** | `admin` | 全校管理员 [DEMO] | `123456` | `/dashboard/admin` | 全校用户/院系/专业大盘监管、审计日志监控 |

> **安全注意**：演示账号仅用于本地开发、教学演示与测试验证，生产环境部署必须通过运维引导重置所有默认账号与凭证。

---

## 环境变量配置说明 (Environment Variables)

后端数据库账号密码、JWT 签名密钥及文件存储配置强制采用环境变量注入，避免硬编码：

| 环境变量名 | 默认缺省值 (本地开发) | 说明 |
| :--- | :--- | :--- |
| `DB_HOST` | `localhost` | MySQL 主机地址 |
| `DB_PORT` | `3306` | MySQL 服务端口 |
| `DB_NAME` | `internship_db` | 数据库名称 |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | *(空)* | 数据库密码（运行时注入，如 `$env:DB_PASSWORD="123456"`） |
| `JWT_SECRET_KEY` | *(自动兜底开发密钥)* | JWT 签名 HMAC 密钥（至少 256 位） |
| `JWT_EXPIRATION_SECONDS` | `7200` | Token 有效时长（默认 2 小时） |

---

## 快速启动与验证 (Quick Start)

### 1. 数据库初始化

```bash
# 导入阶段4基础数据库脚本 (包含7张基础表与演示数据)
mysql -h localhost -u root -p123456 --default-character-set=utf8mb4 < docs/sql/init_phase4.sql
```

### 2. 后端服务编译与运行

```bash
cd backend
# 运行单元与集成测试 (覆盖4类角色登录、Token注销失效、401拦截等)
mvn clean test

# 启动 Spring Boot 后端服务
$env:DB_PASSWORD="123456"
mvn spring-boot:run
```
- 后端服务地址：`http://localhost:8080`
- 健康检查接口：`http://localhost:8080/api/v1/health`
- Knife4j 接口文档：`http://localhost:8080/doc.html`

### 3. 前端工程编译与运行

```bash
cd frontend
# 生产打包校验
npm.cmd run build

# 启动前端开发调试服务
npm.cmd run dev -- --port 3000
```
- 前端访问地址：`http://localhost:3000`

---

## 阶段规划 (Project Milestones)

- [x] **阶段1**：需求深度剖析与规格定义（195项全量冻结）
- [x] **阶段2**：系统架构设计与详细设计（42张表、125个API、36条路由、视觉规范）
- [x] **阶段3**：前后端工程骨架创建与基础环境配置（零硬编码敏感项）
- [x] **阶段4**：基础数据库、真实登录认证与角色工作台联调（7张基础表全端闭环）
- [x] **阶段5**：实习任务管理、安全教育配置、安全准入和实习申报审核核心业务（11张业务表+7大页面+14项专项集成测试全通，系统测试总计23项全通）
- [x] **阶段6**：周报填报、导师批阅、过程指导台账与学生反馈模块开发（41项历史回归基线）
- [x] **阶段7**：中期检查、过程预警、五维成绩评定与电子归档锁定模块开发（24项专项测试，累计65项全量通过，封板标签 `v7.0.0-phase7-sealed`）
- [ ] **阶段8**：系统优化与本地隔离容器部署演练
  - [x] 阶段8核心开发与自动化回归（API-103~122、20项专项测试、85项全量回归全绿、测试隔离整改、37表架构闭环，已归档于 Commit `52d29b2` / Tag `v8.0.0-phase8-sealed`）
  - [ ] 前端运维视图真实浏览器人工视觉与交互验收（待开展）
  - [ ] 本地隔离容器部署演练（Dockerfile / docker-compose.yml / Nginx 反代配置及容器隔离运行，待实施）
- [ ] **阶段9**：毕业设计答辩材料整理（待阶段8容器部署演练与浏览器人工验收全面闭环后方可启动）
