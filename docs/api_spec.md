# RESTful API 规范与健康探测规约 (API Specification)

## 1. 统一接口响应规范

所有 HTTP 接口均采用 JSON 格式返回，顶层结构统一遵循 `Result<T>` 规约：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1774147089000
}
```

- `code = 200`：业务请求执行成功；
- `code = 400`：客户端参数校验不通过（如缺少必填项、字数少于门槛）；
- `code = 401`：认证凭证无效或过期；
- `code = 403`：权限不足（跨越数据管辖范围或越权操作）；
- `code = 500`：服务端未知内部业务异常。

---

## 2. 阶段3基础连通探测接口

- **请求方法**：`GET`
- **请求路径**：`/api/v1/health`
- **认证要求**：公开访问（无需 Token）
- **响应示例**：
  ```json
  {
    "code": 200,
    "message": "服务运行健康",
    "data": {
      "status": "UP",
      "systemName": "高校实习全过程管理系统",
      "version": "1.0.0-SNAPSHOT",
      "currentStage": "PHASE_3_SKELETON",
      "javaVersion": "17.0.8",
      "springBootVersion": "3.2.3",
      "timestamp": 1774147089123
    }
  }
  ```

---

## 3. 阶段5 核心业务接口与边界规约 (Phase 5 Core Endpoints)

| 接口编号 | 请求方法 | 路径 | 权限要求 | 业务说明与核心边界规约 |
| :--- | :--- | :--- | :--- | :--- |
| **API-001** | `POST` | `/api/v1/tasks` | `DEPT_ADMIN`, `SYS_ADMIN` | 创建实习任务：五项权重严格等于 100%；`SYS_ADMIN` 必须指定 `deptId`，严禁默认院系 |
| **API-002** | `GET` | `/api/v1/tasks` | 登录用户 | 查询实习任务列表：支持状态过滤与院系范围筛选 |
| **API-003** | `GET` | `/api/v1/tasks/{id}` | 登录用户 | 获取任务详情：返回五项权重及安全考试达标基准分 |
| **API-004** | `POST` | `/api/v1/tasks/{id}/publish` | `DEPT_ADMIN`, `SYS_ADMIN` | 正式发布任务：未配置及格分/重测次数/周报频次/截止日时 400 阻断；发布后自动圈定班级学生名单 |
| **API-007** | `GET` | `/api/v1/tasks/{id}/students` | `DEPT_ADMIN`, `SYS_ADMIN` | 查看圈定学生名单：学生与教师访问直接 403；跨院系 403 |
| **API-008** | `GET` | `/api/v1/tasks/{id}/teachers` | `DEPT_ADMIN`, `SYS_ADMIN` | 查看本院可用导师及带生负荷：学生与教师访问直接 403；跨院系 403 |
| **API-009** | `POST` | `/api/v1/tasks/{id}/assign-teacher` | `DEPT_ADMIN`, `SYS_ADMIN` | 批量指派指导教师：包含非名单学生时 400 阻断并回滚事务，禁止静默跳过 |
| **API-031** | `GET` | `/api/v1/safety/materials` | 登录用户 | 获取安全规程资料列表：全校通用与当前任务关联资料 |
| **API-032** | `POST` | `/api/v1/safety/materials/{id}/read` | `STUDENT` | 标记阅读资料：原子更新已读列表与进度，驱动 `NOT_STARTED -> STUDYING -> PENDING_TEST` |
| **API-034** | `GET` | `/api/v1/safety/exam/paper` | `STUDENT` | 获取测试试卷：未学完资料 400 阻断；全库启用试题随机乱序洗牌；标准答案与解析脱敏 |
| **API-035** | `POST` | `/api/v1/safety/exam/submit` | `STUDENT` | 提交安全考试：逐题核验试题状态与任务归属，禁止跨任务试题作答；自动判分入库 |
| **API-036** | `POST` | `/api/v1/safety/commitment/sign` | `STUDENT` | 签署安全知晓承诺书：记录真实签署 IP 与时间戳，状态置为 `COMPLETED` |
| **API-038** | `GET` | `/api/v1/safety/teacher/progress` | `TEACHER`, `DEPT_ADMIN` | 教师查询负责学生安全进度：教师仅能查看分配给本人的学生 |
| **API-039** | `POST` | `/api/v1/safety/remind` | `TEACHER`, `DEPT_ADMIN` | 一键催办：记录催办指令至系统安全审计日志 `sys_operation_log` (API-123) |
| **API-050** | `POST` | `/api/v1/apply/draft` | `STUDENT` | 暂存实习申报草稿：`APPROVED` 终审生效时一票否决 (APPLY-009 拦截 400) |
| **API-051** | `POST` | `/api/v1/apply/submit` | `STUDENT` | 正式提交申报：进入导师初审流程；`APPROVED` 状态拦截 (APPLY-009 拦截 400) |
| **API-052** | `GET` | `/api/v1/apply/my` | `STUDENT` | 获取本人实习申请及双级审批历史快照列表 |
| **API-053** | `POST` | `/api/v1/apply/audit` | `TEACHER`, `DEPT_ADMIN` | 申报审批流转：退回修改必须填写不少于5字意见；终审通过触发 `APPROVED` 强锁定 |

---

## 4. 阶段6 过程管理与周报批阅核心接口 (Phase 6 Core Endpoints)

| 接口编号 | 请求方法 | 路径 | 权限要求 | 业务说明与核心边界规约 |
| :--- | :--- | :--- | :--- | :--- |
| **API-056** | `GET` | `/api/v1/internship/weekly-reports` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 查询周报列表：学生查本人，教师查带教学生，院系查全院；支持学年与状态过滤 |
| **API-057** | `GET` | `/api/v1/internship/weekly-reports/{id}` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 周报详情：非本人/非指导教师返回 403；返回周报内容、状态及历次评语 |
| **API-058** | `POST` | `/api/v1/internship/weekly-reports` | `STUDENT` | 提交/暂存周报：草稿允许反复修改，正式提交生成 SUBMIT 历史快照；按截止时间标记逾期 |
| **API-059** | `POST` | `/api/v1/internship/weekly-reports/{id}/review` | `TEACHER` | 导师审阅周报：评分等级评定；退回时退回理由不少于 10 字，写保护终态不可调 |
| **API-065** | `POST` | `/api/v1/internship/guidances` | `TEACHER` | 登记过程指导记录：走访/网络指导，凭据 URL 校验白名单，非带教学生 403 |
| **API-066** | `GET` | `/api/v1/internship/guidances` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 指导台账列表与导出：支持 `export=excel` 流式导出；学生仅查本人，教师查带教，学生导出 403 |
| **API-067** | `PUT` | `/api/v1/internship/guidances/{id}/feedback` | `STUDENT` | 学生确认与反馈：非本人 403；重复确认或 CAS 失败返回 400；事务条件更新防并发 |

---

## 5. 阶段7 核心业务接口与边界规约 (Phase 7 Core Endpoints: API-060~064, API-074~102)

| 接口编号 | 请求方法 | 路径 | 权限要求 | 业务说明与核心边界规约 |
| :--- | :--- | :--- | :--- | :--- |
| **API-060** | `GET` | `/api/v1/internship/materials` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 获取阶段材料清单与提报要求：返回各材料编码、形态、动态字数门槛及当前提交状态 |
| **API-061** | `GET` | `/api/v1/internship/materials/{id}` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 获取阶段材料详情与规范指引：非本人/非带教教师返回 403 |
| **API-062** | `POST` | `/api/v1/internship/materials` | `STUDENT` | 提报/重提阶段材料：总结报告动态字数校验（不足 `min-summary-length` 抛 400）；版本自增并记快照 |
| **API-063** | `POST` | `/api/v1/internship/materials/{id}/audit` | `TEACHER` | 导师查验阶段材料：核验佐证真实性并赋分（0-100），通过或退回重修；非带教学生 403 |
| **API-064** | `GET` | `/api/v1/internship/materials/{id}/versions` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 材料历史版本比对：返回各版本提交正文、凭据 URL、导师批注与打分差异快照 |
| **API-074** | `POST` | `/api/v1/internship/inspections/plans` | `DEPT_ADMIN` | 编制中期检查方案：抽样比例范围校验（`min-sampling-ratio` ~ `max-sampling-ratio`），超限 400 |
| **API-075** | `POST` | `/api/v1/internship/inspections/plans/{id}/sample` | `DEPT_ADMIN` | 执行抽样名单生成：按比例随机抽样或班级整建制，`uk_plan_student` 物理防重拦截 |
| **API-076** | `POST` | `/api/v1/internship/inspections` | `TEACHER` | 录入督导检查记录：打分与考察评价，非带教学生 403；勾选突出问题必须下达整改 |
| **API-077** | `GET` | `/api/v1/internship/inspections` | `STUDENT`, `TEACHER`, `DEPT_ADMIN` | 查询检查记录列表：学生查本人，教师查带教，院系查全院；支持方案与状态过滤 |
| **API-078** | `POST` | `/api/v1/internship/rectifications` | `TEACHER`, `DEPT_ADMIN` | 下达限期整改通知：整改期限合法性校验（不超过 `max-rectify-days` 天） |
| **API-079** | `POST` | `/api/v1/internship/rectifications/{id}/submit` | `STUDENT` | 学生提交整改报告：陈述字数校验（不低于 `min-rectify-length` 字），佐证 URL 合规校验 |
| **API-080** | `POST` | `/api/v1/internship/rectifications/{id}/review` | `TEACHER` | 教师复核整改成效：填写复核意见，判定整改合格通过或驳回重改 |
| **API-081** | `POST` | `/api/v1/internship/rectifications/{id}/close` | `DEPT_ADMIN` | 院系终审销号闭环：必须经教师复核通过方可销号，未验收尝试直接关闭 400 阻断 |
| **API-082** | `GET` | `/api/v1/warn/rules` | `SYS_ADMIN`, `DEPT_ADMIN` | 查询预警规则配置列表：返回 10 类管理异常规则、参数 JSON、启用状态与阈值 |
| **API-083** | `PUT` | `/api/v1/warn/rules/{id}` | `SYS_ADMIN` | 更新预警规则参数：校验 JSON 语法合法性与阈值合理性，版本号自增；非超管 403 |
| **API-084** | `PUT` | `/api/v1/warn/rules/{id}/toggle` | `SYS_ADMIN` | 启用/停用预警规则：原子切换启用状态，未启用规则扫描引擎跳过判定 |
| **API-085** | `POST` | `/api/v1/warn/scan` | `SYS_ADMIN`, `DEPT_ADMIN`, `TEACHER` | 手动触发全盘异常扫描：10秒防刷限流（429）；学生调用 403；教师仅限扫带教学生 |
| **API-086** | `GET` | `/api/v1/warn/tickets` | 登录用户 | 预警工单分页检索：学生查本人，教师查管辖，院系查全院；支持级别、状态、是否升级过滤 |
| **API-087** | `GET` | `/api/v1/warn/tickets/{id}` | 登录用户 | 预警工单详情与证据链：固化的触发原始快照与流转历史；非本人/非管辖 403 |
| **API-088** | `POST` | `/api/v1/warn/tickets/{id}/dispatch` | `DEPT_ADMIN` | 预警工单派发/转派：调整责任人，写入 `warn_process_history` 审计流水 |
| **API-089** | `POST` | `/api/v1/warn/tickets/{id}/feedback` | `STUDENT` | 学生提交在线申辩说明：必填判空校验，支撑佐证 URL 录入，固化申辩证据链 |
| **API-090** | `POST` | `/api/v1/warn/tickets/{id}/handle` | `TEACHER`, `DEPT_ADMIN` | 预警处置与闭环销号：支持误报关闭（释放 `active_dedup_key`）与措施闭环，已升级工单由院系关闭 |
| **API-091** | `POST` | `/api/v1/score/summaries` | `TEACHER` | 录入/汇算五维成绩：分项全非空校验；自动折算加权总分并应用 SCORE-012 等第规则快照 |
| **API-092** | `GET` | `/api/v1/score/summaries` | `TEACHER`, `DEPT_ADMIN` | 成绩列表检索：支持按任务、专业、班级、等第、状态多维过滤，导出前全量核验 |
| **API-093** | `GET` | `/api/v1/score/summaries/{id}` | 登录用户 | 获取学生五维成绩单：学生仅查本人且非公示期/发布期 403；返回五维雷达图与等第快照 |
| **API-094** | `POST` | `/api/v1/score/summaries/{id}/audit` | `DEPT_ADMIN` | 院系复核实习成绩：审核五维打分公允性，通过后准备进入公示 |
| **API-095** | `POST` | `/api/v1/score/tasks/{taskId}/publicity` | `DEPT_ADMIN` | 批量发布成绩公示：设置公示起止时间（不少于 `min-publicity-days`），公示届满转 `PUBLISHED` |
| **API-096** | `POST` | `/api/v1/score/appeals` | `STUDENT` | 公示期提交成绩申诉：非本人 403；非公示期 400；申诉理由字数校验（不少于 15 字） |
| **API-097** | `POST` | `/api/v1/score/appeals/{id}/arbitrate` | `DEPT_ADMIN` | 成绩申诉裁决与调分：必须输入线下红头批文号 `approvalDocNo`，写入 `sys_operation_log` 审计 |
| **API-098** | `GET` | `/api/v1/archives/precheck` | `DEPT_ADMIN`, `SYS_ADMIN` | 归档 9 项前置硬条件诊断核验：全量诊断并输出红绿灯清单，任意不达标一票否决（400） |
| **API-099** | `POST` | `/api/v1/archives/freeze` | `DEPT_ADMIN`, `SYS_ADMIN` | 执行归档锁定：事务内 9 项全量重检，生成标准 PDF 与 ZIP，开启全局只读写保护 |
| **API-100** | `GET` | `/api/v1/archives` | 登录用户 | 归档卷宗跨学年/院系检索：学生查本人，教师查带教，院系查本院，超管全校统筹 |
| **API-101** | `GET` | `/api/v1/archives/{id}/export` | 登录用户 | 导出归档电子卷宗 ZIP：10秒防刷限流（429），未归档 400，流式读取预存卷宗或动态自愈 |
| **API-102** | `POST` | `/api/v1/archives/{id}/unlock` | `SYS_ADMIN` | 超管特批解锁：非超管 403，强制录入批文号与解锁事由，设置 24 小时到期时限，记录审计日志 |


