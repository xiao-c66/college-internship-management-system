<template>
  <div class="inspect-rectify-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">中期检查督导与限期整改中心</span>
            <el-tag type="info" size="small">API-074 ~ API-081</el-tag>
          </div>
          <div class="header-right">
            <el-select
              v-model="selectedTaskId"
              placeholder="选择实习任务批次"
              style="width: 280px"
              @change="handleTaskChange"
            >
              <el-option
                v-for="task in taskOptions"
                :key="task.id"
                :label="task.taskName"
                :value="task.id"
              />
            </el-select>
            <el-button type="primary" :icon="Refresh" @click="refreshAll" :loading="loading">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 标签页 1: 督导检查方案与执行记录 -->
        <el-tab-pane label="中期检查督导 (API-074~077)" name="inspect">
          <div class="tab-toolbar">
            <div class="toolbar-left">
              <el-button
                type="primary"
                :icon="Plus"
                v-if="isDeptAdmin || isAdmin"
                @click="openCreatePlanDialog"
              >
                编制督导检查方案
              </el-button>
            </div>
            <div class="toolbar-right">
              <el-select
                v-model="filterPlanId"
                placeholder="筛选检查方案"
                clearable
                style="width: 220px"
                @change="fetchInspections"
              >
                <el-option
                  v-for="p in planList"
                  :key="p.id"
                  :label="p.planName"
                  :value="p.id"
                />
              </el-select>
            </div>
          </div>

          <!-- 方案列表摘要卡片 -->
          <div class="plan-summary-list" v-if="planList.length > 0">
            <el-row :gutter="16">
              <el-col :span="8" v-for="p in planList" :key="p.id" style="margin-bottom: 12px">
                <el-card shadow="hover" class="plan-card">
                  <div class="plan-card-header">
                    <span class="plan-name">{{ p.planName }}</span>
                    <el-tag :type="p.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                      {{ p.status === 'PUBLISHED' ? '已发布抽样' : '草稿' }}
                    </el-tag>
                  </div>
                  <div class="plan-card-body">
                    <p>抽样模式: {{ p.samplingMode === 'RANDOM_RATIO' ? `比例随机 (${p.samplingRatio}%)` : '整建制班级' }}</p>
                    <p>督导期: {{ p.startDate }} ~ {{ p.endDate }}</p>
                    <p>已抽查: <strong>{{ p.inspectedCount || 0 }}</strong> / 抽样总量: <strong>{{ p.sampledCount || 0 }}</strong></p>
                  </div>
                  <div class="plan-card-actions" v-if="(isDeptAdmin || isAdmin) && p.status === 'DRAFT'">
                    <el-button size="small" type="success" @click="handlePublishPlan(p.id)">
                      正式发布并触发抽样
                    </el-button>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>

          <!-- 检查明细表 -->
          <el-table :data="inspectionList" border stripe v-loading="loading" style="width: 100%; margin-top: 15px">
            <el-table-column prop="samplingBatchNo" label="抽样批次" width="120" />
            <el-table-column prop="studentName" label="受检学生" width="110" />
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="teacherName" label="指导教师" width="110" />
            <el-table-column prop="inspectionType" label="检查形式" width="110">
              <template #default="{ row }">
                <el-tag size="small">{{ formatInspectType(row.inspectionType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="督导打分" width="100">
              <template #default="{ row }">
                <span v-if="row.score !== null && row.score !== undefined" style="font-weight: bold; color: #409eff;">
                  {{ row.score }} 分
                </span>
                <span v-else style="color: #909399;">待打分</span>
              </template>
            </el-table-column>
            <el-table-column prop="hasProblem" label="突出问题" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.hasProblem === 1" type="danger" size="small">存在问题</el-tag>
                <el-tag v-else type="success" size="small">正常合格</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="督导状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getInspectStatusTag(row.status)">{{ getInspectStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  type="primary"
                  v-if="(isTeacher || isDeptAdmin || isAdmin) && row.status === 'PENDING_INSPECT'"
                  @click="openInspectSubmitDialog(row)"
                >
                  录入督导
                </el-button>
                <el-button
                  size="small"
                  type="info"
                  plain
                  @click="openInspectDetailDialog(row)"
                  v-if="row.status !== 'PENDING_INSPECT'"
                >
                  查看记录
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 标签页 2: 限期整改通知与闭环销号 -->
        <el-tab-pane label="限期整改通知与闭环 (API-078~081)" name="rectify">
          <el-table :data="rectifyList" border stripe v-loading="loading" style="width: 100%">
            <el-table-column prop="id" label="整改单号" width="90" />
            <el-table-column prop="studentName" label="受整改学生" width="110" />
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="teacherName" label="负责教师" width="110" />
            <el-table-column prop="rectifyRequirements" label="整改要求明细" min-width="180" show-overflow-tooltip />
            <el-table-column prop="deadlineDate" label="整改截止日期" width="130">
              <template #default="{ row }">
                <span :style="{ color: row.isOverdue === 1 ? '#f56c6c' : '' }">
                  {{ row.deadlineDate }}
                  <el-tag v-if="row.isOverdue === 1" type="danger" size="small">逾期</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="整改流转状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getRectifyStatusTag(row.status)">{{ getRectifyStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <!-- 学生提交整改举措 -->
                <el-button
                  size="small"
                  type="warning"
                  v-if="isStudent && (row.status === 'PENDING_SUBMIT' || row.status === 'REJECTED')"
                  @click="openRectifySubmitDialog(row)"
                >
                  填报整改成效
                </el-button>
                <!-- 教师复核 -->
                <el-button
                  size="small"
                  type="primary"
                  v-if="(isTeacher || isAdmin) && row.status === 'PENDING_REVIEW'"
                  @click="openRectifyReviewDialog(row)"
                >
                  教师复核
                </el-button>
                <!-- 院系闭环销号 -->
                <el-button
                  size="small"
                  type="success"
                  v-if="(isDeptAdmin || isAdmin) && row.status === 'PENDING_REVIEW'"
                  @click="handleCloseRectify(row.id)"
                >
                  终审销号闭环
                </el-button>
                <el-button
                  size="small"
                  type="info"
                  link
                  v-if="row.evidenceAttachmentUrl"
                  @click="viewAttachment(row.evidenceAttachmentUrl)"
                >
                  整改凭证
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 方案编制弹窗 (院系负责人/管理员) -->
    <el-dialog v-model="createPlanDialogVisible" title="编制中期检查方案" width="600px" destroy-on-close>
      <el-form :model="planForm" label-width="110px" :rules="planRules" ref="planFormRef">
        <el-form-item label="方案名称" prop="planName">
          <el-input v-model="planForm.planName" placeholder="例如 2025届计算机专业中期督导方案" />
        </el-form-item>
        <el-form-item label="抽样模式" prop="samplingMode">
          <el-radio-group v-model="planForm.samplingMode">
            <el-radio label="RANDOM_RATIO">按比例随机抽样</el-radio>
            <el-radio label="CLASS_SELECT">整建制班级选择</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽样比例(%)" prop="samplingRatio" v-if="planForm.samplingMode === 'RANDOM_RATIO'">
          <el-input-number v-model="planForm.samplingRatio" :min="1" :max="100" :step="5" />
          <span style="margin-left: 10px; color: #909399;">门槛: 1% ~ 100%</span>
        </el-form-item>
        <el-form-item label="检查起止" required>
          <el-date-picker
            v-model="planForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="启动日期"
            end-placeholder="截止日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="专家组成员">
          <el-input v-model="planForm.expertGroup" placeholder="输入督导专家姓名..." />
        </el-form-item>
        <el-form-item label="要求与说明">
          <el-input type="textarea" v-model="planForm.remark" :rows="3" placeholder="督导要求..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createPlanDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="planSubmitting" @click="handleCreatePlan">
          确认编制保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 录入督导记录弹窗 -->
    <el-dialog v-model="inspectSubmitDialogVisible" title="录入中期督导检查记录与打分" width="620px" destroy-on-close>
      <el-form :model="inspectForm" label-width="110px">
        <el-form-item label="受检学生">
          <span>{{ activeInspection?.studentName }} ({{ activeInspection?.studentNo }})</span>
        </el-form-item>
        <el-form-item label="检查形式" required>
          <el-select v-model="inspectForm.inspectionType" style="width: 100%">
            <el-option label="实地现场走访" value="ONSITE" />
            <el-option label="网络视频连线" value="ONLINE" />
            <el-option label="电话访谈问询" value="PHONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业环境考查">
          <el-input type="textarea" v-model="inspectForm.companySituation" :rows="2" placeholder="考察工作岗位与企业真实环境..." />
        </el-form-item>
        <el-form-item label="学生在岗表现">
          <el-input type="textarea" v-model="inspectForm.studentPerformance" :rows="2" placeholder="考察学生出勤与专业技能掌握..." />
        </el-form-item>
        <el-form-item label="督导考评分" required>
          <el-input-number v-model="inspectForm.score" :min="0" :max="100" :precision="2" />
        </el-form-item>
        <el-form-item label="突出问题">
          <el-switch v-model="inspectForm.hasProblem" active-text="发现突出问题需要限期整改" inactive-text="正常无问题" />
        </el-form-item>
        <template v-if="inspectForm.hasProblem">
          <el-form-item label="问题明细描述" required>
            <el-input type="textarea" v-model="inspectForm.problemDesc" :rows="3" placeholder="详细记录发现的违规或不足问题..." />
          </el-form-item>
          <el-form-item label="整改要求">
            <el-input type="textarea" v-model="inspectForm.rectifyRequirements" :rows="2" placeholder="明确提出整改目标与达标准则..." />
          </el-form-item>
          <el-form-item label="限期天数">
            <el-input-number v-model="inspectForm.rectifyDeadlineDays" :min="1" :max="30" />
            <span style="margin-left: 10px; color: #909399;">天内必须整改闭环</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="inspectSubmitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="inspectSubmitting" @click="handleSubmitInspection">
          确认提交督导记录
        </el-button>
      </template>
    </el-dialog>

    <!-- 学生填报整改成效弹窗 -->
    <el-dialog v-model="rectifySubmitDialogVisible" title="填报限期整改举措与成效凭据" width="560px" destroy-on-close>
      <el-form :model="rectifyForm" label-width="110px">
        <el-form-item label="整改要求">
          <div class="text-box">{{ activeRectify?.rectifyRequirements }}</div>
        </el-form-item>
        <el-form-item label="整改举措说明" required>
          <el-input type="textarea" v-model="rectifyForm.rectifyMeasures" :rows="4" placeholder="详细陈述已落实的纠正举措与改进成效..." />
        </el-form-item>
        <el-form-item label="佐证文件URL">
          <el-input v-model="rectifyForm.evidenceAttachmentUrl" placeholder="整改凭据照片/PDF链接..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifySubmitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rectifySubmitting" @click="handleSubmitRectify">
          确认提交复核
        </el-button>
      </template>
    </el-dialog>

    <!-- 教师复核整改弹窗 -->
    <el-dialog v-model="rectifyReviewDialogVisible" title="教师复核整改成效" width="560px" destroy-on-close>
      <el-form :model="rectifyReviewForm" label-width="100px">
        <el-form-item label="复核判定" required>
          <el-radio-group v-model="rectifyReviewForm.action">
            <el-radio label="PASS">整改成效合格</el-radio>
            <el-radio label="REJECT">成效不达标退回重修</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核评语" required>
          <el-input type="textarea" v-model="rectifyReviewForm.reviewComment" :rows="4" placeholder="填写复核意见与成效评价..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifyReviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rectifyReviewing" @click="handleReviewRectify">
          提交复核
        </el-button>
      </template>
    </el-dialog>

    <!-- 督导记录详情弹窗 -->
    <el-dialog v-model="inspectDetailDialogVisible" title="中期督导检查记录详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="受检学生">{{ activeInspection?.studentName }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ activeInspection?.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="指导教师">{{ activeInspection?.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="督导打分">
          <span style="color: #67c23a; font-weight: bold;">{{ activeInspection?.score }} 分</span>
        </el-descriptions-item>
        <el-descriptions-item label="检查形式">{{ formatInspectType(activeInspection?.inspectionType || '') }}</el-descriptions-item>
        <el-descriptions-item label="检查时间">{{ activeInspection?.inspectionDate }}</el-descriptions-item>
        <el-descriptions-item label="企业情况" :span="2">{{ activeInspection?.companySituation || '无' }}</el-descriptions-item>
        <el-descriptions-item label="学生表现" :span="2">{{ activeInspection?.studentPerformance || '无' }}</el-descriptions-item>
        <el-descriptions-item label="突出问题" :span="2">
          <el-tag v-if="activeInspection?.hasProblem === 1" type="danger">存在问题</el-tag>
          <el-tag v-else type="success">正常无问题</el-tag>
          <div v-if="activeInspection?.hasProblem === 1" style="margin-top: 6px;">{{ activeInspection?.problemDesc }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh, Plus } from '@element-plus/icons-vue';
import { getTaskList, TaskItem } from '@/api/index';
import {
  getInspectPlans,
  createInspectPlan,
  publishInspectPlan,
  getInspectionList,
  submitInspection,
  getRectifyList,
  submitRectify,
  reviewRectify,
  closeRectify,
  InspectPlanVO,
  MidtermInspectionVO,
  RectificationVO
} from '@/api/phase7';

const userType = computed(() => localStorage.getItem('userType') || '');
const isStudent = computed(() => userType.value === 'STUDENT');
const isTeacher = computed(() => userType.value === 'TEACHER');
const isDeptAdmin = computed(() => userType.value === 'DEPT_ADMIN');
const isAdmin = computed(() => userType.value === 'SYS_ADMIN');

const selectedTaskId = ref<number | null>(null);
const taskOptions = ref<TaskItem[]>([]);
const activeTab = ref('inspect');
const loading = ref(false);

const filterPlanId = ref<number | undefined>(undefined);
const planList = ref<InspectPlanVO[]>([]);
const inspectionList = ref<MidtermInspectionVO[]>([]);
const rectifyList = ref<RectificationVO[]>([]);

// 方案编制
const createPlanDialogVisible = ref(false);
const planSubmitting = ref(false);
const planFormRef = ref();
const planForm = ref({
  planName: '',
  samplingMode: 'RANDOM_RATIO',
  samplingRatio: 30.0,
  dateRange: [] as string[],
  expertGroup: '',
  remark: ''
});

const planRules = {
  planName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }]
};

// 督导记录录入
const inspectSubmitDialogVisible = ref(false);
const inspectSubmitting = ref(false);
const activeInspection = ref<MidtermInspectionVO | null>(null);
const inspectForm = ref({
  inspectionType: 'ONSITE',
  companySituation: '',
  studentPerformance: '',
  guidanceFulfillment: '',
  score: 88.0,
  hasProblem: false,
  problemDesc: '',
  rectifyRequirements: '',
  rectifyDeadlineDays: 7
});

const inspectDetailDialogVisible = ref(false);

// 整改填报
const rectifySubmitDialogVisible = ref(false);
const rectifySubmitting = ref(false);
const activeRectify = ref<RectificationVO | null>(null);
const rectifyForm = ref({
  rectifyMeasures: '',
  evidenceAttachmentUrl: ''
});

// 教师复核
const rectifyReviewDialogVisible = ref(false);
const rectifyReviewing = ref(false);
const rectifyReviewForm = ref({
  action: 'PASS' as 'PASS' | 'REJECT',
  reviewComment: ''
});

function formatInspectType(type: string) {
  switch (type) {
    case 'ONSITE': return '实地走访';
    case 'ONLINE': return '网络视频';
    case 'PHONE': return '电话问询';
    default: return type;
  }
}

function getInspectStatusText(status: string) {
  switch (status) {
    case 'PENDING_INSPECT': return '待检查';
    case 'INSPECTED': return '已完成';
    case 'PENDING_RECTIFY': return '待整改';
    case 'RECTIFIED': return '整改闭环';
    default: return status;
  }
}

function getInspectStatusTag(status: string) {
  switch (status) {
    case 'PENDING_INSPECT': return 'info';
    case 'INSPECTED': return 'success';
    case 'PENDING_RECTIFY': return 'danger';
    case 'RECTIFIED': return 'warning';
    default: return 'info';
  }
}

function getRectifyStatusText(status: string) {
  switch (status) {
    case 'PENDING_SUBMIT': return '待填报整改';
    case 'PENDING_REVIEW': return '待复核/闭环';
    case 'REJECTED': return '复核退回';
    case 'CLOSED': return '已销号闭环';
    default: return status;
  }
}

function getRectifyStatusTag(status: string) {
  switch (status) {
    case 'PENDING_SUBMIT': return 'danger';
    case 'PENDING_REVIEW': return 'warning';
    case 'REJECTED': return 'danger';
    case 'CLOSED': return 'success';
    default: return 'info';
  }
}

function viewAttachment(url: string) {
  window.open(url, '_blank');
}

async function loadTasks() {
  try {
    const res = await getTaskList();
    if (res.data && res.data.length > 0) {
      taskOptions.value = res.data;
      selectedTaskId.value = res.data[0].id;
      await refreshAll();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载任务失败');
  }
}

async function handleTaskChange() {
  await refreshAll();
}

async function refreshAll() {
  if (!selectedTaskId.value) return;
  loading.value = true;
  try {
    await Promise.all([fetchPlans(), fetchInspections(), fetchRectifications()]);
  } finally {
    loading.value = false;
  }
}

async function fetchPlans() {
  if (!selectedTaskId.value) return;
  const res = await getInspectPlans({ taskId: selectedTaskId.value });
  planList.value = res.data || [];
}

async function fetchInspections() {
  if (!selectedTaskId.value) return;
  const res = await getInspectionList({
    taskId: selectedTaskId.value,
    planId: filterPlanId.value
  });
  inspectionList.value = res.data || [];
}

async function fetchRectifications() {
  if (!selectedTaskId.value) return;
  const res = await getRectifyList({ taskId: selectedTaskId.value });
  rectifyList.value = res.data || [];
}

function openCreatePlanDialog() {
  planForm.value = {
    planName: '',
    samplingMode: 'RANDOM_RATIO',
    samplingRatio: 30.0,
    dateRange: [],
    expertGroup: '',
    remark: ''
  };
  createPlanDialogVisible.value = true;
}

async function handleCreatePlan() {
  if (!selectedTaskId.value) return;
  if (!planForm.value.planName.trim()) {
    ElMessage.warning('请输入方案名称');
    return;
  }
  if (!planForm.value.dateRange || planForm.value.dateRange.length < 2) {
    ElMessage.warning('请选择方案起止日期');
    return;
  }

  planSubmitting.value = true;
  try {
    await createInspectPlan({
      taskId: selectedTaskId.value,
      planName: planForm.value.planName,
      samplingMode: planForm.value.samplingMode,
      samplingRatio: planForm.value.samplingRatio,
      startDate: planForm.value.dateRange[0],
      endDate: planForm.value.dateRange[1],
      expertGroup: planForm.value.expertGroup,
      remark: planForm.value.remark
    });
    ElMessage.success('督导方案编制成功');
    createPlanDialogVisible.value = false;
    await fetchPlans();
  } catch (err: any) {
    ElMessage.error(err.message || '方案编制失败');
  } finally {
    planSubmitting.value = false;
  }
}

async function handlePublishPlan(id: number) {
  try {
    await publishInspectPlan(id);
    ElMessage.success('方案已正式发布，抽样名单已就绪');
    await refreshAll();
  } catch (err: any) {
    ElMessage.error(err.message || '发布方案失败');
  }
}

function openInspectSubmitDialog(row: MidtermInspectionVO) {
  activeInspection.value = row;
  inspectForm.value = {
    inspectionType: 'ONSITE',
    companySituation: '',
    studentPerformance: '',
    guidanceFulfillment: '',
    score: 88.0,
    hasProblem: false,
    problemDesc: '',
    rectifyRequirements: '',
    rectifyDeadlineDays: 7
  };
  inspectSubmitDialogVisible.value = true;
}

function openInspectDetailDialog(row: MidtermInspectionVO) {
  activeInspection.value = row;
  inspectDetailDialogVisible.value = true;
}

async function handleSubmitInspection() {
  if (!activeInspection.value) return;
  inspectSubmitting.value = true;
  try {
    await submitInspection(activeInspection.value.id, {
      inspectionType: inspectForm.value.inspectionType,
      companySituation: inspectForm.value.companySituation,
      studentPerformance: inspectForm.value.studentPerformance,
      guidanceFulfillment: inspectForm.value.guidanceFulfillment,
      score: inspectForm.value.score,
      hasProblem: inspectForm.value.hasProblem,
      problemDesc: inspectForm.value.problemDesc,
      rectifyRequirements: inspectForm.value.rectifyRequirements,
      rectifyDeadlineDays: inspectForm.value.rectifyDeadlineDays
    });
    ElMessage.success('督导检查记录录入成功');
    inspectSubmitDialogVisible.value = false;
    await refreshAll();
  } catch (err: any) {
    ElMessage.error(err.message || '提交督导记录失败');
  } finally {
    inspectSubmitting.value = false;
  }
}

function openRectifySubmitDialog(row: RectificationVO) {
  activeRectify.value = row;
  rectifyForm.value = {
    rectifyMeasures: row.rectifyMeasures || '',
    evidenceAttachmentUrl: row.evidenceAttachmentUrl || ''
  };
  rectifySubmitDialogVisible.value = true;
}

async function handleSubmitRectify() {
  if (!activeRectify.value) return;
  if (!rectifyForm.value.rectifyMeasures.trim()) {
    ElMessage.warning('请填写整改举措说明');
    return;
  }
  rectifySubmitting.value = true;
  try {
    await submitRectify(activeRectify.value.id, {
      rectifyMeasures: rectifyForm.value.rectifyMeasures,
      evidenceAttachmentUrl: rectifyForm.value.evidenceAttachmentUrl
    });
    ElMessage.success('整改落实成效已提交，等待教师复核');
    rectifySubmitDialogVisible.value = false;
    await fetchRectifications();
  } catch (err: any) {
    ElMessage.error(err.message || '提交失败');
  } finally {
    rectifySubmitting.value = false;
  }
}

function openRectifyReviewDialog(row: RectificationVO) {
  activeRectify.value = row;
  rectifyReviewForm.value = {
    action: 'PASS',
    reviewComment: ''
  };
  rectifyReviewDialogVisible.value = true;
}

async function handleReviewRectify() {
  if (!activeRectify.value) return;
  if (!rectifyReviewForm.value.reviewComment.trim()) {
    ElMessage.warning('请填写复核评价');
    return;
  }
  rectifyReviewing.value = true;
  try {
    await reviewRectify(activeRectify.value.id, {
      action: rectifyReviewForm.value.action,
      reviewComment: rectifyReviewForm.value.reviewComment
    });
    ElMessage.success('整改复核完成');
    rectifyReviewDialogVisible.value = false;
    await fetchRectifications();
  } catch (err: any) {
    ElMessage.error(err.message || '复核操作失败');
  } finally {
    rectifyReviewing.value = false;
  }
}

async function handleCloseRectify(id: number) {
  try {
    await closeRectify(id);
    ElMessage.success('整改已终审销号闭环');
    await fetchRectifications();
  } catch (err: any) {
    ElMessage.error(err.message || '销号失败');
  }
}

onMounted(() => {
  loadTasks();
});
</script>

<style scoped>
.inspect-rectify-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left .title {
  font-size: 18px;
  font-weight: 600;
  margin-right: 12px;
}
.header-right {
  display: flex;
  gap: 12px;
}
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.plan-summary-list {
  margin-bottom: 15px;
}
.plan-card {
  border-left: 4px solid #409eff;
}
.plan-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
  margin-bottom: 8px;
}
.plan-card-body p {
  margin: 4px 0;
  font-size: 13px;
  color: #606266;
}
.plan-card-actions {
  margin-top: 10px;
  text-align: right;
}
.text-box {
  background: #f8f9fa;
  padding: 8px 12px;
  border-radius: 4px;
  color: #303133;
}
</style>
