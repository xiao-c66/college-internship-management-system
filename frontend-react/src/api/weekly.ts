import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface WeeklyReportSaveDTO {
  taskId: number;
  weekNumber: number;
  action: 'DRAFT' | 'SUBMIT';
  workContent?: string;
  workSummary?: string;
  problemEncountered?: string;
  nextWeekPlan?: string;
  attachmentUrl?: string;
}

export interface WeeklyReportReviewDTO {
  action: 'APPROVE' | 'RETURN';
  score?: number;
  reviewComment?: string;
  reviewAnnotations?: string;
}

export interface WeeklyReportVO {
  id: number;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNumber?: string;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  deptId?: number;
  deptName?: string;
  weekNumber: number;
  startDate?: string;
  endDate?: string;
  deadlineTime?: string;
  status: 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'RETURNED';
  isOverdue?: number;
  overdueDays?: number;
  submitTime?: string;
  score?: number;
  reviewComment?: string;
  version: number;
  updateTime?: string;
}

export interface WeeklyReportHistory {
  id: number;
  reportId: number;
  version: number;
  workContent?: string;
  workSummary?: string;
  problemEncountered?: string;
  nextWeekPlan?: string;
  attachmentUrl?: string;
  submitTime?: string;
  score?: number;
  reviewComment?: string;
  reviewerName?: string;
  createTime?: string;
}

export interface WeeklyReportDetailVO extends WeeklyReportVO {
  workContent?: string;
  workSummary?: string;
  problemEncountered?: string;
  nextWeekPlan?: string;
  attachmentUrl?: string;
  reviewAnnotations?: string;
  reviewerId?: number;
  reviewerName?: string;
  reviewTime?: string;
  createTime?: string;
  historyList?: WeeklyReportHistory[];
}

export interface WeeklyWeekStatVO {
  weekNumber: number;
  expectedCount: number;
  submittedCount: number;
  reviewedCount: number;
  overdueCount: number;
  submitRate: number;
}

export interface WeeklyMonitorSummaryVO {
  taskId: number;
  taskName?: string;
  deptId?: number;
  deptName?: string;
  totalStudents: number;
  totalExpectedReports: number;
  totalSubmittedReports: number;
  totalOnTimeReports: number;
  totalOverdueReports: number;
  totalReviewedReports: number;
  totalPendingReports: number;
  submissionRate: number;
  reviewRate: number;
  onTimeRate: number;
  weekStats?: WeeklyWeekStatVO[];
}

export interface GuidanceCreateDTO {
  taskId: number;
  studentId: number;
  guidanceDate: string; // yyyy-MM-dd HH:mm:ss
  guidanceType: 'PHONE' | 'ONLINE' | 'ONSITE' | 'EMAIL_OTHER';
  contentSummary: string;
  location?: string;
  followupActions?: string;
  attachmentUrl?: string;
}

export interface GuidanceFeedbackDTO {
  studentFeedback: string;
}

export interface GuidanceRecordVO {
  id: number;
  taskId: number;
  taskName?: string;
  teacherId: number;
  teacherName?: string;
  studentId: number;
  studentName?: string;
  studentNumber?: string;
  className?: string;
  companyName?: string;
  deptId?: number;
  deptName?: string;
  guidanceDate: string;
  guidanceType: 'PHONE' | 'ONLINE' | 'ONSITE' | 'EMAIL_OTHER';
  contentSummary: string;
  studentFeedback?: string;
  feedbackTime?: string;
  feedbackStatus: 'UNCONFIRMED' | 'CONFIRMED';
  followupActions?: string;
  location?: string;
  attachmentUrl?: string;
  createTime?: string;
}

// 1. 查询周报列表 (API-056)
export const listWeeklyReports = (params?: { taskId?: number; studentId?: number; status?: string }) => {
  return request.get<any, ApiResult<WeeklyReportVO[]>>('/internship/weekly-reports', { params });
};

// 2. 查询周报详情及版本快照 (API-057)
export const getWeeklyReportDetail = (id: number) => {
  return request.get<any, ApiResult<WeeklyReportDetailVO>>(`/internship/weekly-reports/${id}`);
};

// 3. 学生撰写/暂存草稿/正式提交周报 (API-058)
export const saveOrSubmitWeeklyReport = (data: WeeklyReportSaveDTO) => {
  return request.post<any, ApiResult<WeeklyReportVO>>('/internship/weekly-reports', data);
};

// 4. 指导教师批阅或退回周报 (API-059)
export const reviewWeeklyReport = (id: number, data: WeeklyReportReviewDTO) => {
  return request.post<any, ApiResult<WeeklyReportDetailVO>>(`/internship/weekly-reports/${id}/review`, data);
};

// 5. 周报过程管理与提交率监控看板
export const getWeeklyMonitorSummary = (params?: { taskId?: number; deptId?: number }) => {
  return request.get<any, ApiResult<WeeklyMonitorSummaryVO>>('/internship/weekly-reports/monitor', { params });
};

// 6. 登记过程指导/实地走访记录 (API-065)
export const createGuidanceRecord = (data: GuidanceCreateDTO) => {
  return request.post<any, ApiResult<GuidanceRecordVO>>('/internship/guidances', data);
};

// 7. 查询指导记录列表 (API-066)
export const listGuidanceRecords = (params?: {
  taskId?: number;
  studentId?: number;
  guidanceType?: string;
  startDate?: string;
  endDate?: string;
}) => {
  return request.get<any, ApiResult<GuidanceRecordVO[]>>('/internship/guidances', { params });
};

// 8. 学生确认指导记录并提交在岗反馈 (API-067)
export const submitGuidanceFeedback = (id: number, data: GuidanceFeedbackDTO) => {
  return request.put<any, ApiResult<GuidanceRecordVO>>(`/internship/guidances/${id}/feedback`, data);
};
