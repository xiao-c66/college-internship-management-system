import request from '@/utils/request';

// ==========================================
// 1. 实习批次任务 API (TASK-001 ~ TASK-011)
// ==========================================

export interface TaskItem {
  id: number;
  taskCode: string;
  taskName: string;
  deptId: number;
  deptName?: string;
  academicYear: string;
  semester: number;
  internshipMode: string;
  startDate: string;
  endDate: string;
  weightEnterprise: number;
  weightTeacherProcess: number;
  weightWeeklyReport: number;
  weightStageMaterial: number;
  weightSummary: number;
  materialChecklist?: string;
  weeklyFrequency: string;
  weeklyDeadlineDay: number;
  safetyPassingScore: number;
  safetyMaxAttempts: number;
  status: string;
  majorNames?: string[];
  classNames?: string[];
  enrolledStudentsCount?: number;
}

export function getTaskList(params?: { deptId?: number; status?: string }) {
  return request.get<any, { code: number; data: TaskItem[] }>('/tasks', { params });
}

export function getTaskDetail(id: number) {
  return request.get<any, { code: number; data: TaskItem }>('/tasks/' + id);
}

export function createTask(data: any) {
  return request.post<any, { code: number; data: TaskItem }>('/tasks', data);
}

export function updateTask(id: number, data: any) {
  return request.put<any, { code: number; data: TaskItem }>('/tasks/' + id, data);
}

export function publishTask(id: number) {
  return request.post<any, { code: number; message: string }>('/tasks/' + id + '/publish');
}

export interface TaskStudentItem {
  id: number;
  taskId: number;
  studentId: number;
  studentNumber: string;
  studentName: string;
  classId?: number;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  safetyStatus: string;
}

export interface TeacherSimpleItem {
  id: number;
  userNumber: string;
  realName: string;
  deptId?: number;
  deptName?: string;
  assignedStudentsCount: number;
}

export function getTaskStudents(taskId: number, params?: { classId?: number; teacherId?: number; keyword?: string }) {
  return request.get<any, { code: number; data: TaskStudentItem[] }>('/tasks/' + taskId + '/students', { params });
}

export function getAvailableTeachers(taskId: number) {
  return request.get<any, { code: number; data: TeacherSimpleItem[] }>('/tasks/' + taskId + '/teachers');
}

export function assignTeacher(taskId: number, data: { teacherId: number; studentIds: number[] }) {
  return request.post<any, { code: number; message: string }>('/tasks/' + taskId + '/assign-teacher', data);
}

// ==========================================
// 2. 安全教育与准入 API (SAFE-001 ~ SAFE-009)
// ==========================================

export interface SafetyMaterial {
  id: number;
  taskId?: number;
  title: string;
  contentType: string;
  contentBody?: string;
  fileUrl?: string;
  sortOrder: number;
  status: number;
}

export interface QuestionOption {
  key: string;
  text: string;
}

export interface SafetyQuestion {
  id: number;
  taskId?: number;
  questionType: string;
  stem: string;
  options: QuestionOption[];
  score: number;
  analysis?: string;
  sortOrder?: number;
  status?: number;
  correctAnswer?: string;
}

export interface ExamAnswerItem {
  questionId: number;
  studentAnswer: string;
}

export interface ExamResult {
  attemptId: number;
  attemptNo: number;
  totalScore: number;
  passingScore: number;
  isPassed: number;
  resultDesc: string;
  submitTime: string;
}

export interface SafetyStatus {
  taskId: number;
  studentId: number;
  statusCode: 'NOT_STARTED' | 'STUDYING' | 'PENDING_TEST' | 'PASSED' | 'COMPLETED';
  statusDesc: string;
  materialsTotal?: number;
  materialsRead?: number;
  readMaterialIds?: string;
  studyStartTime?: string;
  studyCompleteTime?: string;
  isPassed: number;
  highestScore?: number;
  passingScore?: number;
  examAttempts: number;
  maxAttempts?: number;
  isCommitmentSigned: number;
  signTime?: string;
  insuranceFileUrl?: string;
}

export interface StudentSafetyProgress {
  studentId: number;
  studentNumber: string;
  studentName: string;
  deptId?: number;
  deptName?: string;
  classId?: number;
  className?: string;
  materialProgress: string;
  materialsRead: number;
  materialsTotal: number;
  studyStartTime?: string;
  studyCompleteTime?: string;
  examScore?: number;
  examAttempts: number;
  isPassed: number;
  isCommitmentSigned: number;
  signTime?: string;
  insuranceFileUrl?: string;
  statusCode: string;
  statusText: string;
  statusTag: string;
}

export interface RemindResult {
  taskId: number;
  remindedCount: number;
  studentNames: string[];
  noticeMessage: string;
}

export function getSafetyMaterials(params?: { taskId?: number }) {
  return request.get<any, { code: number; data: SafetyMaterial[] }>('/safety/materials', { params });
}

export function markMaterialRead(taskId: number, materialId: number) {
  return request.post<any, { code: number; message: string }>(`/safety/materials/${materialId}/read`, null, {
    params: { taskId }
  });
}

export function createSafetyMaterial(data: any) {
  return request.post<any, { code: number; data: SafetyMaterial }>('/safety/materials', data);
}

export function deleteSafetyMaterial(id: number) {
  return request.delete<any, { code: number; message: string }>(`/safety/materials/${id}`);
}

export function getSafetyQuestions(params?: { taskId?: number }) {
  return request.get<any, { code: number; data: SafetyQuestion[] }>('/safety/questions', { params });
}

export function createSafetyQuestion(data: any) {
  return request.post<any, { code: number; data: SafetyQuestion }>('/safety/questions', data);
}

export function deleteSafetyQuestion(id: number) {
  return request.delete<any, { code: number; message: string }>(`/safety/questions/${id}`);
}

export function getExamPaper(taskId: number) {
  return request.get<any, { code: number; data: SafetyQuestion[] }>('/safety/exam/paper', { params: { taskId } });
}

export function submitExam(data: { taskId: number; answers: ExamAnswerItem[] }) {
  return request.post<any, { code: number; data: ExamResult }>('/safety/exam/submit', data);
}

export function signCommitment(data: { taskId: number; insuranceFileUrl?: string }) {
  return request.post<any, { code: number; message: string }>('/safety/commitment/sign', data);
}

export function getSafetyStatus(taskId: number, studentId?: number) {
  return request.get<any, { code: number; data: SafetyStatus }>('/safety/status', {
    params: { taskId, studentId }
  });
}

export function getSafetyStudents(params?: { taskId?: number; status?: string; keyword?: string }) {
  return request.get<any, { code: number; data: StudentSafetyProgress[] }>('/safety/students', { params });
}

export function remindSafetyStudents(taskId: number, studentId?: number) {
  return request.post<any, { code: number; data: RemindResult }>('/safety/remind', null, {
    params: { taskId, studentId }
  });
}

export function getDeptSafetyStatistics(params?: { taskId?: number; deptId?: number }) {
  return request.get<any, { code: number; data: Record<string, any> }>('/safety/statistics', { params });
}

// ==========================================
// 3. 实习申报与审核 API (APPLY-001 ~ APPLY-009, REVIEW-001 ~ REVIEW-006)
// ==========================================

export interface AuditHistoryItem {
  id: number;
  applyId: number;
  nodeName: string;
  auditorId: number;
  auditorName: string;
  auditorRole: string;
  auditAction: string;
  auditOpinion: string;
  snapshotData?: string;
  auditTime: string;
}

export interface ApplyItem {
  id: number;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentNumber: string;
  studentName: string;
  deptId: number;
  deptName?: string;
  majorId: number;
  majorName?: string;
  classId: number;
  className?: string;
  teacherId?: number;
  teacherName?: string;
  applyStatus: string;
  isLocked: number;
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
  submitTime?: string;
  teacherAuditTime?: string;
  teacherOpinion?: string;
  deptAuditTime?: string;
  deptOpinion?: string;
  auditHistories?: AuditHistoryItem[];
}

export function saveApplyDraft(data: any) {
  return request.post<any, { code: number; data: ApplyItem }>('/applies/draft', data);
}

export function submitApply(data: any) {
  return request.post<any, { code: number; data: ApplyItem }>('/applies/submit', data);
}

export function updateApply(id: number, data: any) {
  return request.put<any, { code: number; data: ApplyItem }>('/applies/' + id, data);
}

export function getMyApply(taskId: number) {
  return request.get<any, { code: number; data: ApplyItem }>('/applies/my', { params: { taskId } });
}

export function getApplyById(id: number) {
  return request.get<any, { code: number; data: ApplyItem }>('/applies/' + id);
}

export function listApplies(params?: { taskId?: number; status?: string }) {
  return request.get<any, { code: number; data: ApplyItem[] }>('/applies', { params });
}

export function auditApply(id: number, data: { action: 'APPROVED' | 'REJECTED'; opinion: string }) {
  return request.post<any, { code: number; message: string }>('/applies/' + id + '/audit', data);
}

// ==========================================
// 4. 实习周报 API (API-056 ~ API-059)
// ==========================================

export interface WeeklyReportItem {
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
  startDate: string;
  endDate: string;
  deadlineTime: string;
  status: 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'RETURNED';
  isOverdue: number;
  overdueDays: number;
  submitTime?: string;
  score?: number;
  reviewComment?: string;
  version: number;
  updateTime?: string;
}

export interface WeeklyReportHistoryItem {
  id: number;
  reportId: number;
  taskId: number;
  studentId: number;
  version: number;
  action: string;
  operatorId: number;
  operatorName: string;
  operatorRole: string;
  returnReason?: string;
  score?: number;
  reviewComment?: string;
  snapshotContent: string;
  operateTime: string;
}

export interface WeeklyReportDetailItem extends WeeklyReportItem {
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
  historyList?: WeeklyReportHistoryItem[];
}

export interface WeeklyMonitorSummary {
  taskId: number;
  taskName: string;
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
}

// API-056
export function getWeeklyReportList(params?: { taskId?: number; studentId?: number; status?: string }) {
  return request.get<any, { code: number; data: WeeklyReportItem[] }>('/internship/weekly-reports', { params });
}

// API-057
export function getWeeklyReportDetail(id: number) {
  return request.get<any, { code: number; data: WeeklyReportDetailItem }>('/internship/weekly-reports/' + id);
}

// API-058
export function saveOrSubmitWeeklyReport(data: {
  taskId: number;
  weekNumber: number;
  action: 'DRAFT' | 'SUBMIT';
  workContent?: string;
  workSummary?: string;
  problemEncountered?: string;
  nextWeekPlan?: string;
  attachmentUrl?: string;
}) {
  return request.post<any, { code: number; message: string; data: WeeklyReportItem }>('/internship/weekly-reports', data);
}

// API-059
export function reviewWeeklyReport(id: number, data: {
  action: 'APPROVE' | 'RETURN';
  score?: number;
  reviewComment: string;
  reviewAnnotations?: string;
}) {
  return request.post<any, { code: number; message: string; data: WeeklyReportDetailItem }>('/internship/weekly-reports/' + id + '/review', data);
}

export function getWeeklyMonitorSummary(params?: { taskId?: number; deptId?: number }) {
  return request.get<any, { code: number; data: WeeklyMonitorSummary }>('/internship/weekly-reports/monitor', { params });
}

// ==========================================
// 5. 过程指导走访台账 API (API-065 ~ API-067)
// ==========================================

export interface GuidanceRecordItem {
  id: number;
  taskId: number;
  taskName?: string;
  teacherId: number;
  teacherName: string;
  studentId: number;
  studentName: string;
  studentNumber?: string;
  className?: string;
  companyName?: string;
  deptId: number;
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

// API-065
export function createGuidanceRecord(data: {
  taskId: number;
  studentId: number;
  guidanceDate: string;
  guidanceType: string;
  contentSummary: string;
  location?: string;
  followupActions?: string;
  attachmentUrl?: string;
}) {
  return request.post<any, { code: number; message: string; data: GuidanceRecordItem }>('/internship/guidances', data);
}

// API-066
export function getGuidanceList(params?: {
  taskId?: number;
  studentId?: number;
  guidanceType?: string;
  startDate?: string;
  endDate?: string;
}) {
  return request.get<any, { code: number; data: GuidanceRecordItem[] }>('/internship/guidances', { params });
}

// API-066 Excel 导出 (export=excel)
export function exportGuidanceExcel(params?: {
  taskId?: number;
  studentId?: number;
  guidanceType?: string;
  startDate?: string;
  endDate?: string;
}) {
  return request.get<any, any>('/internship/guidances', {
    params: { ...params, export: 'excel' },
    responseType: 'blob'
  });
}

// API-067
export function submitGuidanceFeedback(id: number, data: { studentFeedback: string }) {
  return request.put<any, { code: number; message: string; data: GuidanceRecordItem }>('/internship/guidances/' + id + '/feedback', data);
}


