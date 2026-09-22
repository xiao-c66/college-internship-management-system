import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '用户登录 - 高校实习全过程管理系统' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: () => {
      const userType = localStorage.getItem('userType');
      if (userType === 'STUDENT') return '/dashboard/student';
      if (userType === 'TEACHER') return '/dashboard/teacher';
      if (userType === 'DEPT_ADMIN') return '/dashboard/dept';
      if (userType === 'SYS_ADMIN') return '/dashboard/admin';
      return '/login';
    },
    children: [
      {
        path: 'dashboard/student',
        name: 'StudentDashboard',
        component: () => import('@/views/dashboard/StudentDashboard.vue'),
        meta: { title: '学生端工作台 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'dashboard/teacher',
        name: 'TeacherDashboard',
        component: () => import('@/views/dashboard/TeacherDashboard.vue'),
        meta: { title: '指导教师工作台 - 高校实习全过程管理系统', roles: ['TEACHER', 'SYS_ADMIN'] }
      },
      {
        path: 'dashboard/dept',
        name: 'DeptDashboard',
        component: () => import('@/views/dashboard/DeptDashboard.vue'),
        meta: { title: '院系负责人工作台 - 高校实习全过程管理系统', roles: ['DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'dashboard/admin',
        name: 'AdminDashboard',
        component: () => import('@/views/dashboard/AdminDashboard.vue'),
        meta: { title: '学校管理员工作台 - 高校实习全过程管理系统', roles: ['SYS_ADMIN'] }
      },
      {
        path: 'task',
        name: 'TaskManage',
        component: () => import('@/views/task/TaskManage.vue'),
        meta: { title: '实习任务管理 - 高校实习全过程管理系统', roles: ['DEPT_ADMIN', 'SYS_ADMIN', 'TEACHER'] }
      },
      {
        path: 'task/detail/:id',
        name: 'TaskDetail',
        component: () => import('@/views/task/TaskDetail.vue'),
        meta: { title: '实习任务详情 - 高校实习全过程管理系统' }
      },
      {
        path: 'safety/manage',
        name: 'SafetyManage',
        component: () => import('@/views/safety/SafetyManage.vue'),
        meta: { title: '安全教育与准入配置 - 高校实习全过程管理系统', roles: ['DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'safety/study',
        name: 'SafetyStudy',
        component: () => import('@/views/safety/SafetyStudy.vue'),
        meta: { title: '安全学习与承诺签署 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'safety/exam',
        name: 'SafetyExam',
        component: () => import('@/views/safety/SafetyExam.vue'),
        meta: { title: '安全准入在线测试 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'apply',
        name: 'InternshipApply',
        component: () => import('@/views/apply/InternshipApply.vue'),
        meta: { title: '学生实习申报 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'audit',
        name: 'AuditCenter',
        component: () => import('@/views/audit/AuditCenter.vue'),
        meta: { title: '实习申报审核中心 - 高校实习全过程管理系统', roles: ['TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'weekly/my',
        name: 'WeeklyReportList',
        component: () => import('@/views/weekly/WeeklyReportList.vue'),
        meta: { title: '我的实习周报 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'weekly/edit/:taskId/:weekNo',
        name: 'WeeklyReportEdit',
        component: () => import('@/views/weekly/WeeklyReportEdit.vue'),
        meta: { title: '实习周报填报与编辑 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'weekly/review',
        name: 'WeeklyReviewManage',
        component: () => import('@/views/weekly/WeeklyReviewManage.vue'),
        meta: { title: '周报审阅工作台 - 高校实习全过程管理系统', roles: ['TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'weekly/monitor',
        name: 'WeeklyMonitor',
        component: () => import('@/views/weekly/WeeklyMonitor.vue'),
        meta: { title: '实习过程周报监控看板 - 高校实习全过程管理系统', roles: ['DEPT_ADMIN', 'SYS_ADMIN', 'TEACHER'] }
      },
      {
        path: 'guidance/manage',
        name: 'GuidanceManage',
        component: () => import('@/views/guidance/GuidanceManage.vue'),
        meta: { title: '过程指导走访台账 - 高校实习全过程管理系统', roles: ['TEACHER', 'STUDENT', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      // ==========================================
      // 阶段7 业务路由 (API-060~064, API-074~102)
      // ==========================================
      {
        path: 'material/manage',
        name: 'MaterialManage',
        alias: [
          'internship/materials',
          'stage-materials',
          'summary/manage',
          '/student/process/materials',
          'student/process/materials',
          '/teacher/audit/materials',
          'teacher/audit/materials'
        ],
        component: () => import('@/views/material/MaterialManage.vue'),
        meta: { title: '阶段材料与总结报告 - 高校实习全过程管理系统', roles: ['STUDENT', 'TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'inspect/rectify',
        name: 'InspectRectify',
        alias: [
          'midterm/inspect',
          'rectify/manage',
          '/teacher/supervision/inspect',
          'teacher/supervision/inspect',
          '/dept/quality/inspections',
          'dept/quality/inspections',
          '/student/completion/rectify',
          'student/completion/rectify'
        ],
        component: () => import('@/views/inspect/InspectRectify.vue'),
        meta: { title: '中期检查与限期整改 - 高校实习全过程管理系统', roles: ['TEACHER', 'STUDENT', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'warn/student',
        name: 'StudentWarnCenter',
        alias: ['student/warn', 'student/warnings', '/student/warnings'],
        component: () => import('@/views/warn/StudentWarnCenter.vue'),
        meta: { title: '过程预警与学生申辩 - 高校实习全过程管理系统', roles: ['STUDENT', 'SYS_ADMIN'] }
      },
      {
        path: 'warn/tickets',
        name: 'WarnTicketCenter',
        alias: [
          'warn/manage',
          'warn/center',
          '/dept/quality/warnings',
          'dept/quality/warnings',
          '/teacher/warn/tickets',
          'teacher/warn/tickets'
        ],
        component: () => import('@/views/warn/WarnTicketCenter.vue'),
        meta: { title: '预警工单协同中心 - 高校实习全过程管理系统', roles: ['TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'score/manage',
        name: 'ScoreManage',
        alias: [
          'grade/manage',
          'grade/eval',
          'score/appeal',
          '/teacher/score/evaluate',
          'teacher/score/evaluate',
          '/student/completion/score',
          'student/completion/score',
          '/dept/decision/score-appeals',
          'dept/decision/score-appeals'
        ],
        component: () => import('@/views/score/ScoreManage.vue'),
        meta: { title: '五维成绩评定与申诉 - 高校实习全过程管理系统', roles: ['STUDENT', 'TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'archive/manage',
        name: 'ArchiveManage',
        alias: [
          'archive/freeze',
          'archive/lock',
          'archive/center',
          '/dept/archive/management',
          'dept/archive/management',
          '/student/completion/archive',
          'student/completion/archive',
          '/admin/archives/all',
          'admin/archives/all'
        ],
        component: () => import('@/views/archive/ArchiveManage.vue'),
        meta: { title: '电子档案归档与锁定 - 高校实习全过程管理系统', roles: ['STUDENT', 'TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'] }
      },
      {
        path: 'dev-diag',
        name: 'DevDiagnostic',
        component: () => import('@/views/diagnostic/DevDiagnosticView.vue'),
        meta: { title: '开发与环境诊断 - 高校实习全过程管理系统' }
      },
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '403 无权限 - 高校实习全过程管理系统' }
      },
      {
        path: '404',
        name: 'NotFound',
        component: () => import('@/views/error/404.vue'),
        meta: { title: '404 页面未找到 - 高校实习全过程管理系统' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 白名单路径 (无需登录)
const whiteList = ['/login', '/404'];

router.beforeEach((to, _from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title as string;
  }

  const token = localStorage.getItem('token');
  const userType = localStorage.getItem('userType');

  // 1. 未登录拦截：非白名单页面重定向至 /login
  if (!token) {
    if (whiteList.includes(to.path)) {
      next();
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`);
    }
    return;
  }

  // 2. 已登录状态下访问 /login，直接按角色路由到对应工作台
  if (to.path === '/login') {
    if (userType === 'STUDENT') next('/dashboard/student');
    else if (userType === 'TEACHER') next('/dashboard/teacher');
    else if (userType === 'DEPT_ADMIN') next('/dashboard/dept');
    else if (userType === 'SYS_ADMIN') next('/dashboard/admin');
    else next('/dashboard/student');
    return;
  }

  // 3. 角色权限拦截：检查路由所要求的角色范围
  const allowedRoles = to.meta.roles as string[] | undefined;
  if (allowedRoles && userType && !allowedRoles.includes(userType)) {
    next('/403');
    return;
  }

  next();
});

export default router;
