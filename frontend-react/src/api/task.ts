import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface TaskItem {
  id: number;
  taskCode: string;
  taskName: string;
  deptId: number;
  deptName?: string;
  academicYear: string;
  semester: number;
  internshipMode: string;
  startDate: string;
  endDate: string;
  weightEnterprise: number;
  weightTeacherProcess: number;
  weightWeeklyReport: number;
  weightStageMaterial: number;
  weightSummary: number;
  materialChecklist?: string;
  weeklyFrequency: string;
  weeklyDeadlineDay?: number;
  safetyPassingScore: number;
  safetyMaxAttempts: number;
  status: string;
  majorNames?: string[];
  classNames?: string[];
  majorIds?: number[];
  classIds?: number[];
  studentCount?: number;
  createTime?: string;
}

export interface TaskStudentItem {
  id: number;
  taskId: number;
  studentId: number;
  studentNumber: string;
  studentName: string;
  classId?: number;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  safetyStatus: string;
}

export interface TeacherSimpleItem {
  id: number;
  userNumber: string;
  realName: string;
  deptId?: number;
  deptName?: string;
  assignedStudentsCount: number;
}

export interface TaskCreateOrUpdateParams {
  taskCode?: string;
  taskName: string;
  academicYear: string;
  semester: number;
  internshipMode: string;
  startDate: string;
  endDate: string;
  weightEnterprise: number;
  weightTeacherProcess: number;
  weightWeeklyReport: number;
  weightStageMaterial: number;
  weightSummary: number;
  weeklyFrequency: string;
  safetyPassingScore: number;
  safetyMaxAttempts: number;
  deptId?: number;
}

// 1. 获取任务列表 (分角色过滤)
export function getTaskList(params?: { deptId?: number; status?: string }) {
  return request.get<any, ApiResult<TaskItem[]>>('/tasks', { params });
}

// 2. 获取任务详情
export function getTaskDetail(id: number) {
  return request.get<any, ApiResult<TaskItem>>(`/tasks/${id}`);
}

// 3. 创建实习任务
export function createTask(data: TaskCreateOrUpdateParams) {
  return request.post<any, ApiResult<TaskItem>>('/tasks', data);
}

// 4. 更新实习任务
export function updateTask(id: number, data: TaskCreateOrUpdateParams) {
  return request.put<any, ApiResult<TaskItem>>(`/tasks/${id}`, data);
}

// 5. 正式发布实习任务
export function publishTask(id: number) {
  return request.post<any, ApiResult<null>>(`/tasks/${id}/publish`);
}

// 6. 删除草稿实习任务
export function deleteTask(id: number) {
  return request.delete<any, ApiResult<null>>(`/tasks/${id}`);
}

// 7. 查询任务圈定学生与导师分配名单
export function getTaskStudents(id: number, params?: { classId?: number; teacherId?: number; keyword?: string }) {
  return request.get<any, ApiResult<TaskStudentItem[]>>(`/tasks/${id}/students`, { params });
}

// 8. 获取任务所在学院可用指导教师
export function getAvailableTeachers(id: number) {
  return request.get<any, ApiResult<TeacherSimpleItem[]>>(`/tasks/${id}/teachers`);
}

// 9. 指派指导教师
export function assignTeacher(id: number, data: { teacherId: number; studentIds: number[] }) {
  return request.post<any, ApiResult<null>>(`/tasks/${id}/assign-teacher`, data);
}
