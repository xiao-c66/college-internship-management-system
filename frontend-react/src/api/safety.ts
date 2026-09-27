import request from '../utils/request';
import type { ApiResult } from '../types/auth';

export interface SafetyMaterialItem {
  id: number;
  taskId?: number;
  title: string;
  contentType: string;
  contentBody?: string;
  fileUrl?: string;
  sortOrder: number;
  status: number;
  createTime?: string;
}

export interface QuestionOption {
  key: string;
  text: string;
}

export interface QuestionVO {
  id: number;
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'JUDGMENT';
  stem: string;
  options: string | QuestionOption[];
  score: number;
  sortOrder: number;
}

export interface ExamSubmitDTO {
  taskId: number;
  answers: {
    questionId: number;
    studentAnswer: string;
    answer?: string;
  }[];
}

export interface ExamResultVO {
  examRecordId: number;
  taskId: number;
  studentId: number;
  totalScore: number;
  passingScore: number;
  isPassed: boolean;
  attemptNo: number;
  maxAttempts: number;
  resultDesc: string;
  remainingAttempts: number;
}

export interface CommitmentSignDTO {
  taskId: number;
  signerName: string;
  emergencyContact: string;
  emergencyPhone: string;
  insuranceFileUrl?: string;
}

export interface SafetyStatusVO {
  taskId: number;
  studentId: number;
  statusCode: 'NOT_STARTED' | 'STUDYING' | 'PENDING_TEST' | 'PASSED' | 'COMPLETED';
  statusDesc: string;
  materialsTotal: number;
  materialsRead: number;
  readMaterialIds?: string;
  studyStartTime?: string;
  studyCompleteTime?: string;
  examAttempts: number;
  maxAttempts: number;
  highestScore: number;
  passingScore: number;
  isPassed: number;
  isCommitmentSigned: number;
  signTime?: string;
  insuranceFileUrl?: string;
}

export interface StudentSafetyProgressVO {
  studentId: number;
  studentNumber: string;
  studentName: string;
  deptId: number;
  deptName: string;
  classId?: number;
  className?: string;
  materialProgress: string;
  materialsRead: number;
  materialsTotal: number;
  studyStartTime?: string;
  studyCompleteTime?: string;
  examScore: number;
  examAttempts: number;
  isPassed: number;
  isCommitmentSigned: number;
  signTime?: string;
  insuranceFileUrl?: string;
  statusCode: string;
  statusText: string;
  statusTag: string;
}

// 1. 获取安全教育材料
export function listMaterials(taskId?: number) {
  return request.get<any, ApiResult<SafetyMaterialItem[]>>('/safety/materials', { params: { taskId } });
}

// 2. 标记材料已读
export function markMaterialRead(id: number, taskId: number) {
  return request.post<any, ApiResult<null>>(`/safety/materials/${id}/read`, null, { params: { taskId } });
}

// 3. 获取考试试卷
export function getExamPaper(taskId: number) {
  return request.get<any, ApiResult<QuestionVO[]>>('/safety/exam/paper', { params: { taskId } });
}

// 4. 提交在线作答试卷
export function submitExam(data: ExamSubmitDTO) {
  return request.post<any, ApiResult<ExamResultVO>>('/safety/exam/submit', data);
}

// 5. 签署安全责任承诺书
export function signCommitment(data: CommitmentSignDTO) {
  return request.post<any, ApiResult<null>>('/safety/commitment/sign', data);
}

// 6. 获取学生个人五阶段安全准入状态
export function getSafetyStatus(taskId: number, studentId?: number) {
  return request.get<any, ApiResult<SafetyStatusVO>>('/safety/status', { params: { taskId, studentId } });
}

// 7. 院系安全教育完成率监控数据
export function getDeptSafetyStatistics(taskId?: number, deptId?: number) {
  return request.get<any, ApiResult<any>>('/safety/statistics', { params: { taskId, deptId } });
}

// 8. 一键催办未达标学生
export function remindStudents(taskId: number, studentId?: number) {
  return request.post<any, ApiResult<any>>('/safety/remind', null, { params: { taskId, studentId } });
}

// 9. 指导教师与管理员查询学生安全进度
export function getStudentsSafetyProgress(params?: { taskId?: number; status?: string; keyword?: string }) {
  return request.get<any, ApiResult<StudentSafetyProgressVO[]>>('/safety/students', { params });
}
