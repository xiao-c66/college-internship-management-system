export type UserType = 'STUDENT' | 'TEACHER' | 'DEPT_ADMIN' | 'SYS_ADMIN';

export interface SysUser {
  userId: number;
  username: string;
  realName: string;
  userType: UserType;
  roleCode?: string;
  deptId?: number;
  deptName?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  permissions?: string[];
  mustChangePassword?: boolean;
  forcePasswordChange?: boolean;
}

export interface LoginResult {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  realName: string;
  userType: UserType;
  roleCode: string;
  deptId?: number;
  deptName?: string;
  permissions?: string[];
  mustChangePassword?: boolean;
  forcePasswordChange?: boolean;
}

export interface ApiResult<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp?: number;
}
