import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { LoginPage } from '../pages/login/LoginPage';
import { MainLayout } from '../layouts/MainLayout';
import { RequireAuth } from '../components/RequireAuth';
import { RoleGuard } from '../components/RoleGuard';
import { StudentDashboard } from '../pages/dashboards/StudentDashboard';
import { TeacherDashboard } from '../pages/dashboards/TeacherDashboard';
import { DeptDashboard } from '../pages/dashboards/DeptDashboard';
import { AdminDashboard } from '../pages/dashboards/AdminDashboard';
import { ModulePlaceholder } from '../pages/business/ModulePlaceholder';
import { TaskListPage } from '../pages/task/TaskListPage';
import { TaskDetailPage } from '../pages/task/TaskDetailPage';
import { SafetyPage } from '../pages/safety/SafetyPage';
import { SafetyExamPage } from '../pages/safety/SafetyExamPage';
import { ApplyPage } from '../pages/apply/ApplyPage';
import { WeeklyPage } from '../pages/weekly/WeeklyPage';
import { InspectPage } from '../pages/inspect/InspectPage';
import { NotFoundPage } from '../pages/error/NotFoundPage';
import { useAuthStore } from '../store/useAuthStore';

// 首页智能角色重定向组件
const DashboardRedirect: React.FC = () => {
  const { userType } = useAuthStore();
  if (userType === 'STUDENT') return <Navigate to="/dashboard/student" replace />;
  if (userType === 'TEACHER') return <Navigate to="/dashboard/teacher" replace />;
  if (userType === 'DEPT_ADMIN') return <Navigate to="/dashboard/dept" replace />;
  if (userType === 'SYS_ADMIN') return <Navigate to="/dashboard/admin" replace />;
  return <Navigate to="/login" replace />;
};

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <MainLayout />
      </RequireAuth>
    ),
    children: [
      {
        index: true,
        element: <DashboardRedirect />
      },
      {
        path: 'dashboard',
        element: <DashboardRedirect />
      },
      {
        path: 'dashboard/student',
        element: (
          <RoleGuard allowedRoles={['STUDENT', 'SYS_ADMIN']}>
            <StudentDashboard />
          </RoleGuard>
        )
      },
      {
        path: 'dashboard/teacher',
        element: (
          <RoleGuard allowedRoles={['TEACHER', 'SYS_ADMIN']}>
            <TeacherDashboard />
          </RoleGuard>
        )
      },
      {
        path: 'dashboard/dept',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <DeptDashboard />
          </RoleGuard>
        )
      },
      {
        path: 'dashboard/admin',
        element: (
          <RoleGuard allowedRoles={['SYS_ADMIN']}>
            <AdminDashboard />
          </RoleGuard>
        )
      },
      // 实习任务管理模块 (阶段 2)
      {
        path: 'task',
        element: <TaskListPage />
      },
      {
        path: 'task/detail/:id',
        element: <TaskDetailPage />
      },
      {
        path: 'tasks',
        element: <TaskListPage />
      },
      {
        path: 'tasks/:id',
        element: <TaskDetailPage />
      },
      // 安全教育与准入模块 (阶段 2)
      {
        path: 'safety',
        element: <SafetyPage />
      },
      {
        path: 'safety/exam',
        element: <SafetyExamPage />
      },
      // 实习申报与审核模块 (阶段 2)
      {
        path: 'apply',
        element: <ApplyPage />
      },
      {
        path: 'applies',
        element: <ApplyPage />
      },
      // 周报与过程指导模块 (阶段 3)
      {
        path: 'weekly',
        element: <WeeklyPage />
      },
      {
        path: 'weekly-reports',
        element: <WeeklyPage />
      },
      // 中期检查与整改模块 (阶段 3)
      {
        path: 'inspect',
        element: <InspectPage />
      },
      {
        path: 'inspections',
        element: <InspectPage />
      },
      {
        path: 'warn',
        element: (
          <ModulePlaceholder
            title="风险预警中心"
            phase="迁移规划：阶段 3"
            description="覆盖四色规则扫描触发、预警工单指派、处置与申诉流转。"
          />
        )
      },
      {
        path: 'score',
        element: (
          <ModulePlaceholder
            title="五维成绩与归档"
            phase="迁移规划：阶段 4"
            description="覆盖学生自评、企业评分、教师打分、成绩公示及电子档案打包导出。"
          />
        )
      },
      {
        path: '*',
        element: <NotFoundPage />
      }
    ]
  },
  {
    path: '*',
    element: <NotFoundPage />
  }
]);
