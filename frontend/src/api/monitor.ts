import request from '@/utils/request';

export interface ServerMonitorVO {
  cpuCores: number;
  systemCpuLoad: number;
  jvmTotalMemoryMB: number;
  jvmFreeMemoryMB: number;
  jvmUsedMemoryMB: number;
  jvmMaxMemoryMB: number;
  jvmVersion: string;
  jvmHome: string;
  upTimeSeconds: number;
  osName: string;
  osArch: string;
  totalDiskSpaceGB: number;
  freeDiskSpaceGB: number;
  usedDiskSpaceGB: number;
  status: string;
  timestamp: string;
}

export interface CacheMonitorVO {
  cacheName: string;
  hitCount: number;
  missCount: number;
  hitRate: number;
  evictionCount: number;
  estimatedSize: number;
  maxSize: number;
  expireAfterWriteMinutes: number;
}

export interface SlowCallRecordVO {
  id: number;
  uri: string;
  method: string;
  costMs: number;
  timestamp: string;
  operatorName: string;
  clientIp: string;
  paramSummary?: string;
}

export interface SlowSqlMonitorVO {
  thresholdMs: number;
  ringBufferSize: number;
  sampleCount: number;
  p95CostMs: number;
  p99CostMs: number;
  minCostMs: number;
  maxCostMs: number;
  avgCostMs: number;
  qps: number;
  slowCalls: SlowCallRecordVO[];
}

export interface SysLoginLogVO {
  id: number;
  operatorName: string;
  operatorId: number;
  businessType: string;
  operIp: string;
  operUrl: string;
  operParam: string;
  jsonResult: string;
  status: number;
  errorMsg?: string;
  operTime: string;
}

export interface SysOperationLogVO {
  id: number;
  title: string;
  businessType: string;
  method: string;
  requestMethod: string;
  operatorId: number;
  operatorName: string;
  deptId?: number;
  deptName?: string;
  operUrl: string;
  operIp: string;
  operParam: string;
  jsonResult: string;
  costMs?: number;
  status: number;
  errorMsg?: string;
  operTime: string;
}

export interface SysActiveTokenVO {
  userId: number;
  username: string;
  realName: string;
  userType: string;
  roleCode: string;
  deptId?: number;
  deptName?: string;
  tokenVersion: number;
  lastLoginTime?: string;
  lastLoginIp?: string;
  status: number;
}

/**
 * 获取服务器与JVM运行指标 (API-116)
 */
export function getServerMetrics() {
  return request.get<any, { code: number; message: string; data: ServerMonitorVO }>('/system/monitor/server');
}

/**
 * 获取有界Caffeine缓存状态 (API-117)
 */
export function getCacheMetrics() {
  return request.get<any, { code: number; message: string; data: CacheMonitorVO }>('/system/monitor/cache');
}

/**
 * 手动清空系统缓存 (API-118)
 */
export function clearCache(cacheName?: string) {
  return request.post<any, { code: number; message: string; data: null }>('/system/monitor/cache/clear', null, {
    params: { cacheName }
  });
}

/**
 * 获取慢接口与慢查询度量看板 (API-119)
 */
export function getSlowSqlMetrics() {
  return request.get<any, { code: number; message: string; data: SlowSqlMonitorVO }>('/system/monitor/slow-sql');
}

/**
 * 查询登录安全审计日志 (API-120)
 */
export function getLoginLogs(params?: {
  pageNum?: number;
  pageSize?: number;
  username?: string;
  status?: number;
}) {
  return request.get<any, { code: number; message: string; data: SysLoginLogVO[] }>('/system/logs/login', { params });
}

/**
 * 查询高危业务操作日志 (API-121)
 */
export function getOperationLogs(params?: {
  pageNum?: number;
  pageSize?: number;
  businessType?: string;
  status?: number;
}) {
  return request.get<any, { code: number; message: string; data: SysOperationLogVO[] }>('/system/logs/operation', { params });
}

/**
 * 查询在线活跃会话与Token列表 (API-122)
 */
export function getActiveTokens() {
  return request.get<any, { code: number; message: string; data: SysActiveTokenVO[] }>('/system/security/tokens');
}

/**
 * 强制踢下线指定用户Token (API-122)
 */
export function kickToken(userId: number) {
  return request.post<any, { code: number; message: string; data: null }>(`/system/security/tokens/${userId}/kick`);
}
