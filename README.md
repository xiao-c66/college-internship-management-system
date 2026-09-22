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

## 阶段4与阶段5 数据表范围 (Database Tables)

系统严格按照垂直切片原则逐步创建物理表，目前累计就绪 **18 张物理表**（阶段4基础表 7 张 + 阶段5业务表 11 张），其余业务表暂不提前创建：

### 1. 阶段4 基础表 (7张)
| 序号 | 物理表名 | 业务含义 | 核心约束与安全特性 |
| :--- | :--- | :--- | :--- |
| 1 | `base_department` | 二级院系信息表 | `uk_dept_code` 唯一索引，支持逻辑删除 |
| 2 | `base_major` | 专业基础信息表 | 关联院系外键索引 `idx_major_dept_id` |
| 3 | `base_class` | 行政班级信息表 | 联合索引 `idx_class_dept_major` |
| 4 | `sys_role` | 核心系统角色表 | `uk_role_code` 唯一索引（预置4类角色） |
| 5 | `sys_user` | 系统核心用户表 | `uk_username`, `token_version` 原子自增控制 |
| 6 | `sys_user_role` | 用户角色关联表 | 联合唯一索引 `uk_user_role` |
| 7 | `sys_operation_log` | 安全审计日志表 | 记录登录/登出/操作留痕，参数强制脱敏 |

### 2. 阶段5 业务核心表 (11张)
| 序号 | 物理表名 | 业务含义 | 核心业务规则与特性 |
| :--- | :--- | :--- | :--- |
| 8 | `internship_task` | 实习批次任务主表 | 五项权重严格等于100%；发布前必配周报与安全及格分；严禁默认院系 |
| 9 | `internship_task_major` | 任务关联专业表 | 支持按专业圈定任务范围 |
| 10 | `internship_task_class` | 任务关联行政班级表 | 发布时基于班级自动圈定导入学生名单 |
| 11 | `internship_task_student` | 任务圈定学生名单表 | 记录阅读进度/承诺书状态/安全准入状态，含 `teacher_id` 指导教师绑定 |
| 12 | `safety_material_item` | 安全教育规程资料表 | 支持全校通用或任务专有资料发布 |
| 13 | `safety_test_question` | 安全考核客观题库表 | 支持单选与判断题，全库脱敏答案输出 |
| 14 | `safety_exam_attempt` | 学生安全测试作答记录表 | 记录作答得分、及格判定与轮次统计 |
| 15 | `safety_exam_answer_detail`| 答卷逐题作答明细表 | 记录考生选择、系统对错判定与单题得分 |
| 16 | `safety_commitment_sign` | 安全责任知晓承诺书签署表 | 记录学生电子签名、签署网络 IP 与时间戳凭据 |
| 17 | `internship_apply` | 学生校外实习申报主表 | APPLY-009 审核通过强锁定；单位/岗位/时间变动禁普通修改 |
| 18 | `apply_audit_history` | 实习申报审批流转历史表 | 导师初审与院系终审流转留痕；记录退回意见与快照数据 |

> [!NOTE]
> **考试规则正式冻结说明 (SAFE-004)**：
> 当前系统安全准入考试机制确定并冻结为：**任务专属题库与全校通用题库中的全部启用试题全量抽取并在服务端/客户端随机乱序洗牌**。由于当前数据模型未配置题量和题型比例字段，暂不支持按比例抽题。

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

## 阶段5 业务数据表与核心业务范围 (Phase 5 Tables & Business Scope)

阶段5严格遵循增量交付原则，仅在阶段4的 7 张基础物理表之上增加本阶段必需的 **11 张业务表**（当前系统总计 18 张表，绝不提前创建其余 24 张表）：

| 序号 | 物理表名 | 业务含义 | 核心约束与安全特性 |
| :--- | :--- | :--- | :--- |
| 1 | `internship_task` | 实习批次任务主表 | `uk_task_code`，五项评价成绩权重严格等于 100.00% (TASK-009) |
| 2 | `internship_task_major` | 任务关联专业表 | `uk_task_major`，圈定专业范围 |
| 3 | `internship_task_class` | 任务关联班级表 | `uk_task_class`，圈定班级范围 |
| 4 | `internship_task_student` | 任务圈定参与学生名单表 | `uk_task_student`，任务发布时自动同步 |
| 5 | `safety_material_item` | 安全教育学习资料表 | 支持全校通用 (SAFE-001) 与任务专属 (SAFE-002) 隔离 |
| 6 | `safety_test_question` | 安全教育题库表 | 支持单选、多选、判断客观题与标准答案脱敏 (SAFE-003/004) |
| 7 | `safety_exam_attempt` | 学生安全测试交卷记录表 | 记录成绩、是否及格与最大重测次数校验 (SAFE-005) |
| 8 | `safety_exam_answer_detail` | 安全测试逐题作答明细表 | 逐题自动核对与判分留痕 |
| 9 | `safety_commitment_sign` | 安全承诺书签署与保单凭据表 | `uk_task_student_sign`，电子签名时间、IP 与保单留痕 (SAFE-007) |
| 10 | `internship_apply` | 学生实习申报主表 | **APPLY-009 强只读锁定**：终审通过后后端 Service 层一票否决普通写操作并置 `is_locked=1` |
| 11 | `apply_audit_history` | 实习申报审批流转轨迹与快照表 | **退回不少于5字校验**；保存完整表单 JSON 快照，严禁物理删除 |

---

## 阶段规划 (Project Milestones)

- [x] **阶段1**：需求深度剖析与规格定义（195项全量冻结）
- [x] **阶段2**：系统架构设计与详细设计（42张表、125个API、36条路由、视觉规范）
- [x] **阶段3**：前后端工程骨架创建与基础环境配置（零硬编码敏感项）
- [x] **阶段4**：基础数据库、真实登录认证与角色工作台联调（7张基础表全端闭环）
- [x] **阶段5**：实习任务管理、安全教育配置、安全准入和实习申报审核核心业务（11张业务表+7大页面+14项专项集成测试全通，系统测试总计23项全通）
- [x] **阶段6**：周报填报、导师批阅、过程指导台账与学生反馈模块开发（41项历史回归基线）
- [x] **阶段7**：中期检查、过程预警、五维成绩评定与电子归档锁定模块开发（24项专项测试，累计65项全量通过）
- [ ] **阶段8**：系统优化与全量部署验收
- [ ] **阶段9**：毕业设计答辩材料整理
