import request from '../utils/request';

export interface ArchiveCheckItemVO {
  code: string;
  name: string;
  passed: boolean;
  detail: string;
  blockReason?: string;
}

export interface ArchivePrecheckVO {
  studentId: number;
  studentName: string;
  studentNo: string;
  taskId: number;
  passed: boolean;
  passedCount: number;
  totalCount: number;
  checkItems: ArchiveCheckItemVO[];
}

export interface ArchiveVO {
  id: number;
  archiveNo: string;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  deptId: number;
  deptName?: string;
  academicYear: string;
  checkMatrixJson: string;
  archiveBundleUrl: string;
  archivePdfUrl: string;
  archivedUserId: number;
  archivedUserName?: string;
  archivedTime: string;
  status: 'ARCHIVED' | 'SPECIAL_UNLOCKED';
  specialUnlockReason?: string;
  specialDocNo?: string;
  unlockedBy?: number;
  unlockedByName?: string;
  unlockedTime?: string;
  unlockExpireTime?: string;
  version: number;
}

export interface ArchiveUnlockDTO {
  specialUnlockReason: string;
  specialDocNo: string;
}

export function precheckArchive(taskId: number, studentId: number) {
  return request.get<any, { code: number; data: ArchivePrecheckVO; message?: string }>(`/archives/precheck`, {
    params: { taskId, studentId }
  });
}

export function freezeArchive(taskId: number, studentId: number) {
  return request.post<any, { code: number; data: number; message: string }>(`/archives/freeze`, null, {
    params: { taskId, studentId }
  });
}

export function getArchiveList(params?: {
  taskId?: number;
  deptId?: number;
  academicYear?: string;
  status?: string;
}) {
  return request.get<any, { code: number; data: ArchiveVO[]; message?: string }>(`/archives`, { params });
}

export function getArchiveDetail(id: number) {
  return request.get<any, { code: number; data: ArchiveVO; message?: string }>(`/archives/${id}`);
}

export function exportArchiveBundle(id: number) {
  return request.get(`/archives/${id}/export`, {
    responseType: 'blob'
  });
}

export function unlockArchive(id: number, data: ArchiveUnlockDTO) {
  return request.post<any, { code: number; message: string }>(`/archives/${id}/unlock`, data);
}

export function getTasks(params?: { page?: number; size?: number }) {
  return request.get<any>(`/tasks`, { params });
}
