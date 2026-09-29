import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface ApplyChangeHistoryVO {
  id: number;
  changeId: number;
  nodeName: string;
  operatorId: number;
  operatorName: string;
  operatorRole: string;
  auditAction: 'SUBMIT' | 'APPROVE' | 'REJECT';
  auditOpinion: string;
  snapshotStatus: string;
  createTime: string;
}

export interface ApplyChangeVO {
  id: number;
  applyId: number;
  taskId: number;
  studentId: number;
  studentNumber: string;
  studentName: string;
  deptId: number;
  teacherId?: number;
  teacherName?: string;

  // 原信息快照
  origCompanyName: string;
  origJobPosition: string;
  origJobAddress: string;
  origContactPerson: string;
  origContactPhone: string;
  origContactEmail?: string;
  origStartDate: string;
  origEndDate: string;
  origInternshipMode: string;
  origJobDuties?: string;
  origAgreementFileUrl?: string;

  // 拟变更的新信息
  newCompanyName: string;
  newJobPosition: string;
  newJobAddress: string;
  newContactPerson: string;
  newContactPhone: string;
  newContactEmail?: string;
  newStartDate: string;
  newEndDate: string;
  newInternshipMode: string;
  newJobDuties?: string;
  newAgreementFileUrl?: string;

  // 变更事由与补充材料
  changeReason: string;
  proofFileUrl?: string;

  // 状态机: PENDING_TEACHER, PENDING_DEPT, APPROVED, REJECTED
  changeStatus: 'PENDING_TEACHER' | 'PENDING_DEPT' | 'APPROVED' | 'REJECTED';
  currentStep: 'TEACHER_INITIAL' | 'DEPT_FINAL' | 'FINISHED';
  createTime: string;
  updateTime: string;
  histories?: ApplyChangeHistoryVO[];
}

export interface ApplyChangeDTO {
  applyId: number;
  newCompanyName: string;
  newJobPosition: string;
  newJobAddress: string;
  newContactPerson: string;
  newContactPhone: string;
  newContactEmail?: string;
  newStartDate: string;
  newEndDate: string;
  newInternshipMode: string;
  newJobDuties?: string;
  newAgreementFileUrl?: string;
  changeReason: string;
  proofFileUrl?: string;
}

export interface ApplyChangeAuditDTO {
  auditAction: 'APPROVE' | 'REJECT';
  auditOpinion: string;
}

// 提交实习重大变更申请
export function submitApplyChangeApi(data: ApplyChangeDTO) {
  return request.post<any, ApiResult<ApplyChangeVO>>('/applies/changes', data);
}

// 指导教师初审
export function teacherAuditApplyChangeApi(id: number, data: ApplyChangeAuditDTO) {
  return request.post<any, ApiResult<ApplyChangeVO>>(`/applies/changes/${id}/teacher-audit`, data);
}

// 院系管理员终审
export function deptAuditApplyChangeApi(id: number, data: ApplyChangeAuditDTO) {
  return request.post<any, ApiResult<ApplyChangeVO>>(`/applies/changes/${id}/dept-audit`, data);
}

// 获取变更单详情
export function getApplyChangeDetailApi(id: number) {
  return request.get<any, ApiResult<ApplyChangeVO>>(`/applies/changes/${id}`);
}

// 列表查询变更单
export function listApplyChangesApi(params?: { applyId?: number; taskId?: number; status?: string }) {
  return request.get<any, ApiResult<ApplyChangeVO[]>>('/applies/changes', { params });
}

// 获取当前申报记录的活跃/处理中变更
export function getActiveApplyChangeApi(applyId: number) {
  return request.get<any, ApiResult<ApplyChangeVO | null>>(`/applies/${applyId}/active-change`);
}
