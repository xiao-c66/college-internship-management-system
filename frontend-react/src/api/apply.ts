import request from '../utils/request';

export interface ApplyDTO {
  id?: number;
  taskId: number;
  companyName: string;
  jobPosition: string;
  jobAddress: string;
  companyContactPerson: string;
  companyContactPhone: string;
  companyContactEmail?: string;
  startDate: string;
  endDate: string;
  internshipMode: string;
  jobDuties?: string;
  agreementFileUrl?: string;
}

export interface AuditDTO {
  action: 'APPROVED' | 'REJECTED';
  opinion?: string;
}

export interface AuditHistoryVO {
  id: number;
  applyId: number;
  nodeName: string;
  nodeDesc?: string;
  auditorId: number;
  auditorName: string;
  auditorRole: string;
  auditAction: string;
  auditOpinion: string;
  snapshotData?: string;
  auditTime: string;
}

export interface ApplyVO {
  id: number;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentNumber: string;
  studentName: string;
  deptId: number;
  deptName?: string;
  majorId?: number;
  majorName?: string;
  classId?: number;
  className?: string;
  companyName: string;
  jobPosition: string;
  jobAddress: string;
  companyContactPerson: string;
  companyContactPhone: string;
  companyContactEmail?: string;
  startDate: string;
  endDate: string;
  internshipMode: string;
  jobDuties?: string;
  agreementFileUrl?: string;
  applyStatus: string;
  statusDesc?: string;
  isLocked: number;
  canEdit?: boolean;
  canSubmit?: boolean;
  createTime?: string;
  updateTime?: string;
  auditHistories?: AuditHistoryVO[];
}

export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// 暂存实习申报草稿 (APPLY-006)
export const saveDraft = (data: ApplyDTO): Promise<ApiResponse<ApplyVO>> => {
  return request.post('/applies/draft', data);
};

// 正式提交实习申报 (APPLY-006)
export const submitApply = (data: ApplyDTO): Promise<ApiResponse<ApplyVO>> => {
  return request.post('/applies/submit', data);
};

// 修改实习申报 (APPLY-009 规则校验)
export const updateApply = (id: number, data: ApplyDTO): Promise<ApiResponse<ApplyVO>> => {
  return request.put(`/applies/${id}`, data);
};

// 学生获取当前任务申报
export const getMyApply = (taskId: number): Promise<ApiResponse<ApplyVO>> => {
  return request.get('/applies/my', { params: { taskId } });
};

// 获取指定申报详情
export const getApplyById = (id: number): Promise<ApiResponse<ApplyVO>> => {
  return request.get(`/applies/${id}`);
};

// 查询待审/已审申请列表 (教师/院系负责人)
export const listApplies = (params?: { taskId?: number; status?: string }): Promise<ApiResponse<ApplyVO[]>> => {
  return request.get('/applies', { params });
};

// 实习申报审核流转 (初审与终审)
export const auditApply = (id: number, data: AuditDTO): Promise<ApiResponse<void>> => {
  return request.post(`/applies/${id}/audit`, data);
};

// 获取申报审批流转历史轨迹 (REVIEW-006)
export const getAuditHistories = (id: number): Promise<ApiResponse<AuditHistoryVO[]>> => {
  return request.get(`/applies/${id}/history`);
};
