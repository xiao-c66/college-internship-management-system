import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface ScoreAuditHistoryVO {
  id: number;
  scoreId: number;
  taskId: number;
  studentId: number;
  action: 'APPEAL_APPLY' | 'APPEAL_PASS' | 'APPEAL_REJECT' | 'SPECIAL_MODIFY';
  appealReason?: string;
  appealAttachmentUrl?: string;
  oldScoreSnapshot?: string;
  newScoreSnapshot?: string;
  auditUserId?: number;
  auditUserName?: string;
  auditComment?: string;
  approvalDocNo?: string;
  operateTime: string;
}

export interface ScoreSummaryVO {
  id: number;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  teacherId: number;
  teacherName?: string;
  deptId: number;
  deptName?: string;
  enterpriseScore?: number;
  processScore?: number;
  weeklyScore?: number;
  materialScore?: number;
  summaryScore?: number;
  finalScore?: number;
  scoreLevel?: 'EXCELLENT' | 'GOOD' | 'MEDIUM' | 'PASS' | 'FAIL';
  gradeRuleSnapshotJson?: string;
  evaluationComment?: string;
  enterpriseEvaluationUrl?: string;
  status: 'DRAFT' | 'PENDING_AUDIT' | 'PENDING_PUBLICITY' | 'PUBLICITY' | 'PUBLISHED';
  publicityStartTime?: string;
  publicityEndTime?: string;
  confirmedTeacherTime?: string;
  auditedDeptUserId?: number;
  auditedDeptUserName?: string;
  auditedDeptTime?: string;
  version: number;
  createdAt: string;
  auditHistory?: ScoreAuditHistoryVO[];
}

export interface ScoreSubmitDTO {
  taskId: number;
  studentId: number;
  enterpriseScore: number;
  processScore: number;
  weeklyScore: number;
  materialScore: number;
  summaryScore: number;
  evaluationComment?: string;
  enterpriseEvaluationUrl?: string;
  submitToDept?: boolean;
}

export interface ScoreAppealDTO {
  scoreId: number;
  appealReason: string;
  appealAttachmentUrl?: string;
}

export interface ScoreArbitrateDTO {
  action: 'PASS' | 'REJECT';
  auditComment: string;
  approvalDocNo?: string;
  enterpriseScore?: number;
  processScore?: number;
  weeklyScore?: number;
  materialScore?: number;
  summaryScore?: number;
}

export function getScoreList(params?: {
  taskId?: number;
  deptId?: number;
  majorId?: number;
  classId?: number;
  scoreLevel?: string;
  status?: string;
}) {
  return request.get<any, ApiResult<ScoreSummaryVO[]>>('/score/summaries', { params });
}

export function getScoreDetail(id: number) {
  return request.get<any, ApiResult<ScoreSummaryVO>>(`/score/summaries/${id}`);
}

export function getMyScore(taskId: number) {
  return request.get<any, ApiResult<ScoreSummaryVO>>('/score/my', {
    params: { taskId }
  });
}

export function submitScore(data: ScoreSubmitDTO) {
  return request.post<any, ApiResult<number>>('/score/summaries', data);
}

export function auditScore(id: number) {
  return request.post<any, ApiResult<void>>(`/score/summaries/${id}/audit`);
}

export function publishScores(taskId: number) {
  return request.post<any, ApiResult<void>>(`/score/tasks/${taskId}/publicity`);
}

export function submitAppeal(data: ScoreAppealDTO) {
  return request.post<any, ApiResult<number>>('/score/appeals', data);
}

export function arbitrateAppeal(id: number, data: ScoreArbitrateDTO) {
  return request.post<any, ApiResult<void>>(`/score/appeals/${id}/arbitrate`, data);
}
