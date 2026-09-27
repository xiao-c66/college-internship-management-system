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
import { TaskListPage } from '../pages/task/TaskListPage';
import { TaskDetailPage } from '../pages/task/TaskDetailPage';
import { SafetyPage } from '../pages/safety/SafetyPage';
import { SafetyExamPage } from '../pages/safety/SafetyExamPage';
import { ApplyPage } from '../pages/apply/ApplyPage';
import { WeeklyPage } from '../pages/weekly/WeeklyPage';
import { InspectPage } from '../pages/inspect/InspectPage';
import { WarnPage } from '../pages/warn/WarnPage';
import { ScorePage } from '../pages/score/ScorePage';
import { MaterialPage } from '../pages/material/MaterialPage';
import { ArchivePage } from '../pages/archive/ArchivePage';
import { NoticePage } from '../pages/notice/NoticePage';
import { MonitorPage } from '../pages/monitor/MonitorPage';
import { ForbiddenPage } from '../pages/error/ForbiddenPage';
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
        path: 'safety/manage',
        element: <SafetyPage />
      },
      {
        path: 'safety/study',
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
      {
        path: 'audit',
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
      {
        path: 'weekly/my',
        element: <WeeklyPage />
      },
      {
        path: 'weekly/edit/:taskId/:weekNo',
        element: <WeeklyPage />
      },
      {
        path: 'weekly/review',
        element: <WeeklyPage />
      },
      {
        path: 'weekly/monitor',
        element: <WeeklyPage />
      },
      {
        path: 'guidance/manage',
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
        path: 'inspect/rectify',
        element: <InspectPage />
      },
      {
        path: 'midterm/inspect',
        element: <InspectPage />
      },
      {
        path: 'rectify/manage',
        element: <InspectPage />
      },
      {
        path: 'teacher/supervision/inspect',
        element: <InspectPage />
      },
      {
        path: 'dept/quality/inspections',
        element: <InspectPage />
      },
      {
        path: 'student/completion/rectify',
        element: <InspectPage />
      },
      // 风险预警中心模块 (阶段 3)
      {
        path: 'warn',
        element: <WarnPage />
      },
      {
        path: 'warn-tickets',
        element: <WarnPage />
      },
      {
        path: 'warn/student',
        element: <WarnPage />
      },
      {
        path: 'student/warn',
        element: <WarnPage />
      },
      {
        path: 'student/warnings',
        element: <WarnPage />
      },
      {
        path: 'warn/tickets',
        element: <WarnPage />
      },
      {
        path: 'warn/manage',
        element: <WarnPage />
      },
      {
        path: 'warn/center',
        element: <WarnPage />
      },
      {
        path: 'dept/quality/warnings',
        element: <WarnPage />
      },
      {
        path: 'teacher/warn/tickets',
        element: <WarnPage />
      },
      // 五维成绩评定与申诉模块
      {
        path: 'score',
        element: <ScorePage />
      },
      {
        path: 'score/manage',
        element: <ScorePage />
      },
      {
        path: 'grade/manage',
        element: <ScorePage />
      },
      {
        path: 'grade/eval',
        element: <ScorePage />
      },
      {
        path: 'score/appeal',
        element: <ScorePage />
      },
      // 阶段材料与总结报告模块
      {
        path: 'material/manage',
        element: <MaterialPage />
      },
      {
        path: 'materials',
        element: <MaterialPage />
      },
      {
        path: 'internship/materials',
        element: <MaterialPage />
      },
      {
        path: 'stage-materials',
        element: <MaterialPage />
      },
      {
        path: 'summary/manage',
        element: <MaterialPage />
      },
      {
        path: 'student/process/materials',
        element: <MaterialPage />
      },
      {
        path: 'teacher/audit/materials',
        element: <MaterialPage />
      },
      // 电子档案归档与锁定模块
      {
        path: 'archive/manage',
        element: <ArchivePage />
      },
      {
        path: 'archive/freeze',
        element: <ArchivePage />
      },
      {
        path: 'archive/lock',
        element: <ArchivePage />
      },
      {
        path: 'archive/center',
        element: <ArchivePage />
      },
      {
        path: 'dept/archive/management',
        element: <ArchivePage />
      },
      {
        path: 'student/completion/archive',
        element: <ArchivePage />
      },
      {
        path: 'admin/archives/all',
        element: <ArchivePage />
      },
      // 教学通知公告管理模块
      {
        path: 'admin/system/notice',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <NoticePage />
          </RoleGuard>
        )
      },
      {
        path: 'system/notice',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <NoticePage />
          </RoleGuard>
        )
      },
      {
        path: 'notice',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <NoticePage />
          </RoleGuard>
        )
      },
      {
        path: 'notices',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <NoticePage />
          </RoleGuard>
        )
      },
      {
        path: 'notice/manage',
        element: (
          <RoleGuard allowedRoles={['DEPT_ADMIN', 'SYS_ADMIN']}>
            <NoticePage />
          </RoleGuard>
        )
      },
      // 系统监控与安全审计模块 (API-116 ~ API-122)
      {
        path: 'admin/system/monitor',
        element: (
          <RoleGuard allowedRoles={['SYS_ADMIN']}>
            <MonitorPage />
          </RoleGuard>
        )
      },
      {
        path: 'system/monitor',
        element: (
          <RoleGuard allowedRoles={['SYS_ADMIN']}>
            <MonitorPage />
          </RoleGuard>
        )
      },
      {
        path: 'monitor',
        element: (
          <RoleGuard allowedRoles={['SYS_ADMIN']}>
            <MonitorPage />
          </RoleGuard>
        )
      },
      {
        path: '403',
        element: <ForbiddenPage />
      },
      {
        path: '404',
        element: <NotFoundPage />
      },
      {
        path: '*',
        element: <NotFoundPage />
      }
    ]
  },
  {
    path: '/403',
    element: <ForbiddenPage />
  },
  {
    path: '/404',
    element: <NotFoundPage />
  },
  {
    path: '*',
    element: <NotFoundPage />
  }
]);
