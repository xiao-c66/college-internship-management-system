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

### 前端依赖与运行
```bash
# 进入前端目录
cd D:\devlop\IDEA\college-internship-management-system\frontend

# 安装依赖
npm.cmd install

# 生产环境打包验证
npm.cmd run build

# 启动开发热更新服务器
npm.cmd run dev
```

---

## 3. 后续迭代优化事项台账 (Pending Optimization Backlog)

经阶段5前端浏览器真实交互验收复核，以下 3 项事项已正式登记归档，列为系统后续优化迭代任务，本阶段保持源码基线不变：

| 事项编号 | 归属模块 | 优化事项说明 | 改进建议与预期效果 | 登记状态 |
| :---: | :--- | :--- | :--- | :---: |
| **OPT-001** | **安全测试**<br>(`SafetyExam.vue`) | **安全考试无试题时禁用提交按钮** | 当学生因未读完规程资料被前置 400 阻断未取得试题（`questions.length === 0`）时，动态禁用交卷按钮（`:disabled="questions.length === 0"`）或展示待准入空状态卡片，避免出现空试卷点击交卷的无意义交互。 | **已登记入库**<br>(后续迭代实现) |
| **OPT-002** | **实习申报**<br>(`InternshipApply.vue`) | **优化实习申报页面标签换行** | 在 1440px 分辨率下，微调表单 `label-width`（如扩展至 `140px`）或调整栅格比例，消除“岗位工作详细地址”中“址”字的折行现象，使联系人三项输入框在宽屏下更加舒展美观。 | **已登记入库**<br>(后续视觉微调) |
| **OPT-003** | **数据库/运维**<br>(MySQL/演示数据) | **统一演示数据和生产数据的 `utf8mb4` 字符集** | 对演示与生产数据库执行 `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4`，统一部署与 seed 脚本编码标准，彻底解决历史遗留的院系/班级中文名称在页面渲染为问号（乱码）的问题。 | **已登记入库**<br>(部署脚本优化) |

