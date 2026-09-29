import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface UserManageVO {
  id: number;
  username: string;
  realName: string;
  userType: 'STUDENT' | 'TEACHER' | 'DEPT_ADMIN' | 'SYS_ADMIN';
  userNumber: string;
  phone?: string;
  email?: string;
  deptId?: number;
  deptName?: string;
  majorId?: number;
  majorName?: string;
  classId?: number;
  className?: string;
  status: number; // 1: 正常, 0: 停用, 2: 待首次改密
  createTime: string;
}

export interface UserPageResult {
  records: UserManageVO[];
  total: number;
  size: number;
  current: number;
}

export interface UserImportRowVO {
  rowNum: number;
  userNumber: string;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  deptName?: string;
  majorName?: string;
  className?: string;
  isValid: boolean;
  errorMessage?: string;
}

export interface UserImportPreviewVO {
  previewToken?: string;
  totalCount: number;
  validCount: number;
  invalidCount: number;
  previewRows: UserImportRowVO[];
}

export interface UserImportCredentialVO {
  rowNum: number;
  userNumber: string;
  username: string;
  realName: string;
  temporaryPassword: string;
  status: string;
}

export interface UserImportResultVO {
  totalCount: number;
  successCount: number;
  failureCount: number;
  credentials: UserImportCredentialVO[];
}

export interface ResetPasswordResultVO {
  userId: number;
  username: string;
  realName: string;
  temporaryPassword: string;
}

// 分页查询用户账号
export function getUserPageApi(params: any) {
  return request.get<any, ApiResult<UserPageResult>>('/users', { params });
}

// 下载导入模板
export function downloadTemplateApi(userType: string) {
  return request.get(`/users/import-template?userType=${userType}`, {
    responseType: 'blob'
  });
}

// 预览解析 Excel/CSV 文件
export function importPreviewApi(file: File, userType: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('userType', userType);
  return request.post<any, ApiResult<UserImportPreviewVO>>('/users/import-preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

// 确认执行导入
export function importExecuteApi(data: { userType: string; previewToken?: string; rows?: any[] }) {
  return request.post<any, ApiResult<UserImportResultVO>>('/users/import-execute', data);
}

// 管理员重置密码
export function resetPasswordApi(userId: number) {
  return request.post<any, ApiResult<ResetPasswordResultVO>>(`/users/${userId}/reset-password`);
}

// 切换账号状态
export function toggleUserStatusApi(userId: number) {
  return request.post<any, ApiResult<null>>(`/users/${userId}/toggle-status`);
}

// 用户修改密码 (首次登录改密或日常修改)
export function changePasswordApi(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return request.post<any, ApiResult<null>>('/users/change-password', data);
}

// 忘记密码：发送安全验证码
export function sendForgotCodeApi(data: { username: string; target: string }) {
  return request.post<any, ApiResult<null>>('/users/forgot-password/send-code', data);
}

// 忘记密码：凭验证码重置密码
export function resetForgotPasswordApi(data: {
  username: string;
  target: string;
  code: string;
  newPassword: string;
  confirmPassword: string;
}) {
  return request.post<any, ApiResult<null>>('/users/forgot-password/verify-and-reset', data);
}
