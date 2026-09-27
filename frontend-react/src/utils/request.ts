import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import type { ApiResult } from '../types/auth';

// 创建 Axios 实例
const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
});

// 请求拦截器：自动注入 Bearer JWT Token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：业务状态码与 401 凭证失效处理
request.interceptors.response.use(
  (response: AxiosResponse) => {
    // 二进制流（文件下载、导出 ZIP 等）直接放行
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response;
    }

    const res: ApiResult = response.data;
    // 业务状态码校验 (200 为正常)
    if (res.code && res.code !== 200) {
      if (res.code === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('userType');
        message.error(res.message || '登录凭证已失效，请重新登录');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      } else {
        message.error(res.message || '系统业务请求异常');
      }
      return Promise.reject(new Error(res.message || 'Error'));
    }
    return res as any;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      localStorage.removeItem('userType');
      message.error('认证凭证已过期或账号已在其他终端登出，请重新登录');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    } else if (error.response?.status === 403) {
      message.error(error.response?.data?.message || '您无权执行此操作或访问该范围数据 (403)');
    } else if (error.response?.status === 404) {
      message.error('请求的目标资源不存在 (404)');
    } else if (error.response?.status >= 500) {
      message.error(error.response?.data?.message || '后端服务异常，请稍后重试 (500)');
    } else {
      message.error(error.message || '网络连接异常，请检查网络');
    }
    return Promise.reject(error);
  }
);

export default request;
