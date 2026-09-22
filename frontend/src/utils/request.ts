import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';

// 创建 Axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
});

// 请求拦截器：自动挂载 Bearer Token
service.interceptors.request.use(
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

// 响应拦截器：业务状态码与 401 自动失效处理
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;
    // 如果返回的是二进制流或未包装的直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response;
    }
    // 业务状态码校验 (200 为正常)
    if (res.code && res.code !== 200) {
      if (res.code === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('username');
        localStorage.removeItem('userType');
        ElMessage.error(res.message || '登录凭证已失效，请重新登录');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      } else {
        ElMessage.error(res.message || '系统业务请求异常');
      }
      return Promise.reject(new Error(res.message || 'Error'));
    }
    return res;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      localStorage.removeItem('userType');
      ElMessage.error('认证凭证已过期或账号已在其他终端登出，请重新登录');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    } else {
      const message = error.response?.data?.message || error.message || '网络通信异常，请检查后端服务状态';
      ElMessage.error(message);
    }
    return Promise.reject(error);
  }
);

export default service;
