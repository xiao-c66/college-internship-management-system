import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface InspectPlanCreateDTO {
  taskId: number;
  deptId?: number;
  planName: string;
  samplingMode?: string; // RANDOM_RATIO, CLASS_SELECT
  samplingRatio?: number;
  startDate: string;
  endDate: string;
  expertGroup?: string;
  remark?: string;
  classIds?: number[];
}

export interface InspectPlanVO {
  id: number;
  taskId: number;
  taskName?: string;
  deptId: number;
  deptName?: string;
  planName: string;
  samplingMode: string;
  samplingRatio?: number;
  startDate: string;
  endDate: string;
  expertGroup?: string;
  remark?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'COMPLETED';
  sampledCount?: number;
  inspectedCount?: number;
  createTime?: string;
}

export interface InspectionSubmitDTO {
  planId: number;
  studentId: number;
  inspectionType?: string;
  inspectionDate?: string;
  companySituation?: string;
  studentPerformance?: string;
  guidanceFulfillment?: string;
  score: number;
  attachmentUrl?: string;
  hasProblem?: number;
  problemDesc?: string;
}

export interface InspectionVO {
  id: number;
  planId: number;
  planName?: string;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  studentNumber?: string;
  className?: string;
  companyName?: string;
  teacherId?: number;
  teacherName?: string;
  inspectorId?: number;
  inspectorName?: string;
  samplingBatchNo: string;
  inspectionType: string;
  inspectionDate?: string;
  companySituation?: string;
  studentPerformance?: string;
  guidanceFulfillment?: string;
  score?: number;
  attachmentUrl?: string;
  hasProblem: number;
  problemDesc?: string;
  status: 'PENDING_INSPECT' | 'INSPECTED' | 'RECTIFIED';
  rectifyId?: number;
  createTime?: string;
}

export interface RectifyCreateDTO {
  inspectionId: number;
  rectifyRequirements: string;
  deadlineDate: string;
}

export interface RectifySubmitDTO {
  studentExplanation: string;
  evidenceAttachmentUrl?: string;
}

export interface RectifyReviewDTO {
  action: 'PASSED' | 'REJECTED';
  reviewComment: string;
}

export interface RectifyVO {
  id: number;
  inspectionId: number;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  studentNumber?: string;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  responsibleUserId?: number;
  rectifyRequirements: string;
  deadlineDate: string;
  isOverdue?: number;
  studentExplanation?: string;
  evidenceAttachmentUrl?: string;
  submitTime?: string;
  reviewTeacherId?: number;
  reviewTeacherName?: string;
  reviewComment?: string;
  reviewTime?: string;
  closeDeptUserId?: number;
  closeDeptUserName?: string;
  closeTime?: string;
  status: 'PENDING_SUBMIT' | 'PENDING_REVIEW' | 'PENDING_CLOSE' | 'CLOSED' | 'REJECTED';
  createTime?: string;
}

// 1. 编制督导检查方案 (API-074)
export const createInspectPlan = (data: InspectPlanCreateDTO) => {
  return request.post<any, ApiResult<number>>('/internship/inspections/plans', data);
};

// 2. 执行抽样名单生成 (API-075)
export const executeSampling = (id: number) => {
  return request.post<any, ApiResult<number>>(`/internship/inspections/plans/${id}/sample`);
};

// 3. 查询检查方案列表
export const getInspectPlans = (params?: { taskId?: number }) => {
  return request.get<any, ApiResult<InspectPlanVO[]>>('/internship/inspections/plans', { params });
};

// 4. 录入督导检查记录 (API-076)
export const submitInspection = (data: InspectionSubmitDTO) => {
  return request.post<any, ApiResult<number>>('/internship/inspections', data);
};

// 5. 查询检查记录列表 (API-077)
export const getInspectionList = (params?: { planId?: number; taskId?: number; status?: string }) => {
  return request.get<any, ApiResult<InspectionVO[]>>('/internship/inspections', { params });
};

// 6. 下达限期整改通知 (API-078)
export const createRectification = (data: RectifyCreateDTO) => {
  return request.post<any, ApiResult<number>>('/internship/rectifications', data);
};

// 7. 学生提交整改报告 (API-079)
export const submitRectification = (id: number, data: RectifySubmitDTO) => {
  return request.post<any, ApiResult<void>>(`/internship/rectifications/${id}/submit`, data);
};

// 8. 教师复核整改成效 (API-080)
export const reviewRectification = (id: number, data: RectifyReviewDTO) => {
  return request.post<any, ApiResult<void>>(`/internship/rectifications/${id}/review`, data);
};

// 9. 院系终审销号闭环 (API-081)
export const closeRectification = (id: number) => {
  return request.post<any, ApiResult<void>>(`/internship/rectifications/${id}/close`);
};

// 10. 查询整改记录列表
export const getRectificationList = (params?: { taskId?: number; status?: string }) => {
  return request.get<any, ApiResult<RectifyVO[]>>('/internship/rectifications', { params });
};
