import request from '../utils/request';
import type { ApiResult, LoginResult, SysUser } from '../types/auth';

export interface LoginParams {
  username: string;
  password?: string;
  captcha?: string;
  captchaKey?: string;
}

export interface CaptchaData {
  captchaKey: string;
  captchaBase64?: string;
  text?: string;
}

// 登录接口
export function loginApi(data: LoginParams) {
  return request.post<any, ApiResult<LoginResult>>('/auth/login', data);
}

// 登出接口
export function logoutApi() {
  return request.post<any, ApiResult<null>>('/auth/logout');
}

// 验证码获取
export function getCaptchaApi() {
  return request.get<any, ApiResult<CaptchaData>>('/auth/captcha');
}

// 当前登录用户详情
export function getCurrentUserApi() {
  return request.get<any, ApiResult<SysUser>>('/auth/me');
}
