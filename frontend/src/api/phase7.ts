import request from '@/utils/request';

// ==========================================
// 1. 阶段材料与实习总结报告 (API-060 ~ API-064)
// ==========================================

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

export function getMaterialChecklist(taskId: number) {
  return request.get<any, { code: number; data: MaterialChecklistItem[] }>(`/internship/materials/checklist`, {
    params: { taskId }
  });
}

export function getMaterialItems(taskId: number, studentId?: number) {
  return request.get<any, { code: number; data: MaterialItemVO[] }>(`/internship/materials`, {
    params: { taskId, studentId }
  });
}

export function getMaterialDetail(id: number) {
  return request.get<any, { code: number; data: MaterialItemVO }>(`/internship/materials/${id}`);
}

export function submitMaterial(data: {
  taskId: number;
  materialCode: string;
  contentText?: string;
  attachmentUrl?: string;
  fileName?: string;
  fileSize?: number;
}) {
  return request.post<any, { code: number; data: number; message: string }>(`/internship/materials`, data);
}

export function auditMaterial(id: number, data: {
  action: 'APPROVED' | 'RETURNED';
  auditScore?: number;
  auditComment?: string;
}) {
  return request.post<any, { code: number; message: string }>(`/internship/materials/${id}/audit`, data);
}

export function getMaterialVersions(id: number) {
  return request.get<any, { code: number; data: MaterialVersionVO[] }>(`/internship/materials/${id}/versions`);
}

// ==========================================
// 2. 中期检查方案与督导记录 (API-074 ~ API-077)
// ==========================================

export interface InspectPlanVO {
  id: number;
  planName: string;
  taskId: number;
  taskName?: string;
  deptId: number;
  deptName?: string;
  samplingMode: 'RANDOM_RATIO' | 'CLASS_SELECT';
  samplingRatio?: number;
  startDate: string;
  endDate: string;
  expertGroup?: string;
  remark?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'COMPLETED';
  sampledCount: number;
  inspectedCount: number;
  createdBy: number;
  createdByName?: string;
  createdAt: string;
}

export interface MidtermInspectionVO {
  id: number;
  planId: number;
  planName?: string;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  teacherId: number;
  teacherName?: string;
  inspectorId: number;
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
  status: 'PENDING_INSPECT' | 'INSPECTED' | 'PENDING_RECTIFY' | 'RECTIFIED';
  rectificationId?: number;
  rectificationStatus?: string;
}

export function getInspectPlans(params?: { taskId?: number; deptId?: number; status?: string }) {
  return request.get<any, { code: number; data: InspectPlanVO[] }>(`/internship/inspections/plans`, { params });
}

export function createInspectPlan(data: {
  taskId: number;
  planName: string;
  samplingMode?: string;
  samplingRatio?: number;
  selectedClassIds?: number[];
  startDate: string;
  endDate: string;
  expertGroup?: string;
  remark?: string;
}) {
  return request.post<any, { code: number; data: number; message: string }>(`/internship/inspections/plans`, data);
}

export function publishInspectPlan(id: number) {
  return request.post<any, { code: number; message: string }>(`/internship/inspections/plans/${id}/publish`);
}

export function getInspectionList(params?: {
  planId?: number;
  taskId?: number;
  hasProblem?: number;
  status?: string;
}) {
  return request.get<any, { code: number; data: MidtermInspectionVO[] }>(`/internship/inspections`, { params });
}

export function submitInspection(id: number, data: {
  inspectionType: string;
  companySituation?: string;
  studentPerformance?: string;
  guidanceFulfillment?: string;
  score: number;
  attachmentUrl?: string;
  hasProblem: boolean;
  problemDesc?: string;
  rectifyDeadlineDays?: number;
  rectifyRequirements?: string;
}) {
  return request.post<any, { code: number; message: string }>(`/internship/inspections/${id}`, data);
}

// ==========================================
// 3. 限期整改通知与闭环 (API-078 ~ API-081)
// ==========================================

export interface RectificationVO {
  id: number;
  inspectionId: number;
  taskId: number;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  teacherId: number;
  teacherName?: string;
  rectifyRequirements: string;
  deadlineDate: string;
  isOverdue: number;
  rectifyMeasures?: string;
  evidenceAttachmentUrl?: string;
  submitTime?: string;
  reviewTeacherId?: number;
  reviewTeacherName?: string;
  reviewComment?: string;
  reviewTime?: string;
  closeDeptUserId?: number;
  closeDeptUserName?: string;
  closeTime?: string;
  status: 'PENDING_SUBMIT' | 'PENDING_REVIEW' | 'REJECTED' | 'CLOSED';
}

export function getRectifyList(params?: {
  taskId?: number;
  studentId?: number;
  teacherId?: number;
  status?: string;
}) {
  return request.get<any, { code: number; data: RectificationVO[] }>(`/internship/rectifications`, { params });
}

export function submitRectify(id: number, data: {
  rectifyMeasures: string;
  evidenceAttachmentUrl?: string;
}) {
  return request.post<any, { code: number; message: string }>(`/internship/rectifications/${id}/submit`, data);
}

export function reviewRectify(id: number, data: {
  action: 'PASS' | 'REJECT';
  reviewComment: string;
}) {
  return request.post<any, { code: number; message: string }>(`/internship/rectifications/${id}/review`, data);
}

export function closeRectify(id: number) {
  return request.post<any, { code: number; message: string }>(`/internship/rectifications/${id}/close`);
}

// ==========================================
// 4. 异常预警与工单处置 (API-082 ~ API-090)
// ==========================================

export interface WarnRuleVO {
  id: number;
  ruleCode: string;
  ruleName: string;
  anomalyCategory: string;
  warnLevel: 'YELLOW' | 'ORANGE' | 'RED';
  thresholdParamsJson: string;
  dispatchedRole: 'TEACHER' | 'DEPT_ADMIN';
  handlingTimeoutDays: number;
  isEnabled: number;
  version: number;
  description?: string;
}

export interface WarnTicketVO {
  id: number;
  ticketNo: string;
  taskId: number;
  taskName?: string;
  studentId: number;
  studentName?: string;
  studentNo?: string;
  teacherId?: number;
  teacherName?: string;
  deptId: number;
  deptName?: string;
  ruleId: number;
  ruleCode?: string;
  ruleName?: string;
  ruleVersion: number;
  warnLevel: 'YELLOW' | 'ORANGE' | 'RED';
  warnTitle: string;
  evidenceSnapshotJson: string;
  status: 'TRIGGERED' | 'DISPATCHED' | 'PROCESSING' | 'PENDING_REVIEW' | 'CLOSED' | 'FALSE_ALARM_CLOSED';
  isUpgraded: number;
  upgradedTime?: string;
  upgradeReason?: string;
  currentAssigneeId: number;
  currentAssigneeName?: string;
  currentAssigneeRole: string;
  dedupKey: string;
  activeDedupKey?: string;
  studentFeedback?: string;
  studentFeedbackTime?: string;
  teacherInvestigation?: string;
  handlingMeasures?: string;
  closedTime?: string;
  closedBy?: number;
  createdAt: string;
  processHistories?: any[];
}

export interface WarnStatisticsVO {
  taskId: number;
  totalTickets: number;
  activeTickets: number;
  closedTickets: number;
  yellowTickets: number;
  orangeTickets: number;
  redTickets: number;
  upgradedTickets: number;
}

export function getWarnRules() {
  return request.get<any, { code: number; data: WarnRuleVO[] }>(`/warn/rules`);
}

export function saveWarnRule(data: Partial<WarnRuleVO>) {
  return request.post<any, { code: number; data: number; message: string }>(`/warn/rules`, data);
}

export function triggerScan(taskId?: number) {
  return request.post<any, { code: number; data: number; message: string }>(`/warn/scan`, null, {
    params: { taskId }
  });
}

export function getWarnTickets(params?: {
  taskId?: number;
  studentId?: number;
  teacherId?: number;
  deptId?: number;
  warnLevel?: string;
  status?: string;
}) {
  return request.get<any, { code: number; data: WarnTicketVO[] }>(`/warn/tickets`, { params });
}

export function getWarnTicketDetail(id: number) {
  return request.get<any, { code: number; data: WarnTicketVO }>(`/warn/tickets/${id}`);
}

export function handleWarnTicket(id: number, data: {
  action: 'PROCESSING' | 'CLOSED' | 'FALSE_ALARM_CLOSED';
  teacherInvestigation?: string;
  handlingMeasures?: string;
  remark?: string;
}) {
  return request.post<any, { code: number; message: string }>(`/warn/tickets/${id}/handle`, data);
}

export function feedbackWarnTicket(id: number, data: { studentFeedback: string }) {
  return request.post<any, { code: number; message: string }>(`/warn/tickets/${id}/feedback`, data);
}

export function upgradeWarnTicket(id: number, data: { upgradeReason: string }) {
  return request.post<any, { code: number; message: string }>(`/warn/tickets/${id}/upgrade`, data);
}

export function getWarnStatistics(taskId?: number) {
  return request.get<any, { code: number; data: WarnStatisticsVO }>(`/warn/statistics`, {
    params: { taskId }
  });
}

// ==========================================
// 5. 五维成绩评定与申诉 (API-091 ~ API-097)
// ==========================================

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
  auditUserId: number;
  auditUserName: string;
  auditComment: string;
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
  gradeRuleSnapshotJson: string;
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

export function submitScore(data: {
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
}) {
  return request.post<any, { code: number; data: number; message: string }>(`/score/summaries`, data);
}

export function getScoreList(params?: {
  taskId?: number;
  deptId?: number;
  majorId?: number;
  classId?: number;
  scoreLevel?: string;
  status?: string;
}) {
  return request.get<any, { code: number; data: ScoreSummaryVO[] }>(`/score/summaries`, { params });
}

export function getScoreDetail(id: number) {
  return request.get<any, { code: number; data: ScoreSummaryVO }>(`/score/summaries/${id}`);
}

export function getMyScore(taskId: number) {
  return request.get<any, { code: number; data: ScoreSummaryVO }>(`/score/my`, {
    params: { taskId }
  });
}

export function auditScore(id: number) {
  return request.post<any, { code: number; message: string }>(`/score/summaries/${id}/audit`);
}

export function publishScores(taskId: number) {
  return request.post<any, { code: number; message: string }>(`/score/tasks/${taskId}/publicity`);
}

export function submitAppeal(data: {
  scoreId: number;
  appealReason: string;
  appealAttachmentUrl?: string;
}) {
  return request.post<any, { code: number; data: number; message: string }>(`/score/appeals`, data);
}

export function arbitrateAppeal(id: number, data: {
  action: 'PASS' | 'REJECT';
  auditComment: string;
  approvalDocNo?: string;
  enterpriseScore?: number;
  processScore?: number;
  weeklyScore?: number;
  materialScore?: number;
  summaryScore?: number;
}) {
  return request.post<any, { code: number; message: string }>(`/score/appeals/${id}/arbitrate`, data);
}

// ==========================================
// 6. 实习电子卷宗归档与锁定 (API-098 ~ API-102)
// ==========================================

export interface ArchiveCheckItem {
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
  checkItems: ArchiveCheckItem[];
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

export function precheckArchive(taskId: number, studentId: number) {
  return request.get<any, { code: number; data: ArchivePrecheckVO }>(`/archives/precheck`, {
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
  return request.get<any, { code: number; data: ArchiveVO[] }>(`/archives`, { params });
}

export function getArchiveDetail(id: number) {
  return request.get<any, { code: number; data: ArchiveVO }>(`/archives/${id}`);
}

export function exportArchiveBundle(id: number) {
  return request.get(`/archives/${id}/export`, {
    responseType: 'blob'
  });
}

export function unlockArchive(id: number, data: {
  specialUnlockReason: string;
  specialDocNo: string;
}) {
  return request.post<any, { code: number; message: string }>(`/archives/${id}/unlock`, data);
}
