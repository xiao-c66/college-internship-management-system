import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface MaterialChecklistItem {
  materialCode: string;
  materialName: string;
  materialType: 'VOUCHER_FILE' | 'REPORT_TEXT' | 'HYBRID';
  required: boolean;
  minContentLength?: number;
}

export interface MaterialItemVO {
  id?: number;
  taskId: number;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  materialCode: string;
  materialName: string;
  materialType: string;
  required: boolean;
  contentText?: string;
  attachmentUrl?: string;
  fileName?: string;
  fileSize?: number;
  version: number;
  status: 'UNSUBMITTED' | 'SUBMITTED' | 'APPROVED' | 'RETURNED';
  submitTime?: string;
  auditTeacherId?: number;
  auditTeacherName?: string;
  auditScore?: number;
  auditComment?: string;
  auditTime?: string;
  minContentLength?: number;
}

export interface MaterialVersionVO {
  id: number;
  materialId: number;
  version: number;
  contentText?: string;
  attachmentUrl?: string;
  fileName?: string;
  submitTime?: string;
  auditTeacherId?: number;
  auditTeacherName?: string;
  auditScore?: number;
  auditComment?: string;
  auditTime?: string;
  status: string;
  createdAt: string;
}

export interface MaterialSubmitDTO {
  taskId: number;
  materialCode: string;
  contentText?: string;
  attachmentUrl?: string;
  fileName?: string;
  fileSize?: number;
}

export interface MaterialAuditDTO {
  action: 'APPROVED' | 'RETURNED';
  auditScore?: number;
  auditComment?: string;
}

export function getMaterialChecklist(taskId: number) {
  return request.get<any, ApiResult<MaterialChecklistItem[]>>('/internship/materials/checklist', {
    params: { taskId }
  });
}

export function getMaterialItems(taskId: number, studentId?: number) {
  return request.get<any, ApiResult<MaterialItemVO[]>>('/internship/materials', {
    params: { taskId, studentId }
  });
}

export function getMaterialDetail(id: number) {
  return request.get<any, ApiResult<MaterialItemVO>>(`/internship/materials/${id}`);
}

export function submitMaterial(data: MaterialSubmitDTO) {
  return request.post<any, ApiResult<number>>('/internship/materials', data);
}

export function auditMaterial(id: number, data: MaterialAuditDTO) {
  return request.post<any, ApiResult<void>>(`/internship/materials/${id}/audit`, data);
}

export function getMaterialVersions(id: number) {
  return request.get<any, ApiResult<MaterialVersionVO[]>>(`/internship/materials/${id}/versions`);
}
