import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface WarnRuleVO {
  id: number;
  ruleCode: string;
  ruleName: string;
  anomalyCategory: string;
  warnLevel: 'YELLOW' | 'ORANGE' | 'RED';
  thresholdParamsJson: string;
  dispatchedRole: string;
  handlingTimeoutDays: number;
  isEnabled: number;
  version: number;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WarnProcessHistoryVO {
  id: number;
  ticketId: number;
  action: string;
  operatorId: number;
  operatorName: string;
  operatorRole: string;
  contentRemark: string;
  attachmentUrl?: string;
  operateTime: string;
}

export interface WarnTicketVO {
  id: number;
  ticketNo: string;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  deptId: number;
  deptName?: string;
  ruleId: number;
  ruleCode?: string;
  ruleName?: string;
  ruleVersion?: number;
  warnLevel: 'YELLOW' | 'ORANGE' | 'RED';
  warnTitle: string;
  evidenceSnapshotJson?: string;
  status: 'TRIGGERED' | 'DISPATCHED' | 'PROCESSING' | 'PENDING_REVIEW' | 'CLOSED' | 'FALSE_ALARM_CLOSED';
  isUpgraded: number;
  upgradedTime?: string;
  upgradeReason?: string;
  currentAssigneeId?: number;
  currentAssigneeName?: string;
  currentAssigneeRole?: string;
  studentFeedback?: string;
  studentFeedbackTime?: string;
  teacherInvestigation?: string;
  handlingMeasures?: string;
  closedTime?: string;
  closedBy?: number;
  closedByName?: string;
  createdAt: string;
  processHistory?: WarnProcessHistoryVO[];
}

export interface WarnScanResult {
  scannedStudents: number;
  newTickets: number;
  upgradedCount: number;
}

export interface WarnFeedbackDTO {
  studentFeedback: string;
  attachmentUrl?: string;
}

export interface WarnHandleDTO {
  action: 'PROCESSING' | 'CLOSED' | 'FALSE_ALARM_CLOSED';
  teacherInvestigation?: string;
  handlingMeasures?: string;
  attachmentUrl?: string;
}

export interface WarnDispatchDTO {
  assigneeId: number;
  assigneeRole: string;
  remark?: string;
}

export interface WarnRuleUpdateDTO {
  ruleName: string;
  warnLevel: string;
  thresholdParamsJson: string;
  dispatchedRole?: string;
  handlingTimeoutDays: number;
  description?: string;
}

// 查询预警规则配置列表 (API-082)
export const getWarnRules = () => {
  return request.get<any, ApiResult<WarnRuleVO[]>>('/warn/rules');
};

// 更新预警规则参数 (API-083)
export const updateWarnRule = (id: number, data: WarnRuleUpdateDTO) => {
  return request.put<any, ApiResult<void>>(`/warn/rules/${id}`, data);
};

// 启用/停用预警规则 (API-084)
export const toggleWarnRule = (id: number) => {
  return request.put<any, ApiResult<void>>(`/warn/rules/${id}/toggle`);
};

// 手动触发全盘异常扫描 (API-085)
export const triggerScan = (taskId?: number) => {
  return request.post<any, ApiResult<WarnScanResult>>('/warn/scan', null, {
    params: taskId ? { taskId } : {}
  });
};

// 预警工单分页检索 (API-086)
export const getWarnTickets = (params?: {
  taskId?: number;
  warnLevel?: string;
  status?: string;
  isUpgraded?: number;
}) => {
  return request.get<any, ApiResult<WarnTicketVO[]>>('/warn/tickets', { params });
};

// 预警工单详情与证据链 (API-087)
export const getWarnTicketDetail = (id: number) => {
  return request.get<any, ApiResult<WarnTicketVO>>(`/warn/tickets/${id}`);
};

// 预警工单派发/转派 (API-088)
export const dispatchWarnTicket = (id: number, data: WarnDispatchDTO) => {
  return request.post<any, ApiResult<void>>(`/warn/tickets/${id}/dispatch`, data);
};

// 学生提交在线申辩说明 (API-089)
export const submitWarnFeedback = (id: number, data: WarnFeedbackDTO) => {
  return request.post<any, ApiResult<void>>(`/warn/tickets/${id}/feedback`, data);
};

// 预警处置与闭环销号 (API-090)
export const handleWarnTicket = (id: number, data: WarnHandleDTO) => {
  return request.post<any, ApiResult<void>>(`/warn/tickets/${id}/handle`, data);
};
