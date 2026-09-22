<template>
  <div class="score-manage-container">
    <div class="page-header">
      <div class="header-left">
        <h2>五维综合成绩评定与申诉管理</h2>
        <p class="subtitle">企业评价、过程指导、周报质量、阶段材料与总结报告五维加权汇算与仲裁闭环</p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="isDeptAdmin || isAdmin"
          type="primary"
          :icon="Promotion"
          @click="openBatchPublishDialog"
        >
          发布成绩公示
        </el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 学生端视图：本人成绩单与申诉 -->
    <div v-if="isStudent" class="student-score-view">
      <el-card shadow="hover" class="my-score-card" v-loading="myScoreLoading">
        <template #header>
          <div class="card-header-flex">
            <span class="card-title">我的实习综合评定成绩</span>
            <el-tag :type="getStatusTagType(myScore?.status || '')">
              {{ formatStatus(myScore?.status || 'NOT_SUBMITTED') }}
            </el-tag>
          </div>
        </template>

        <div v-if="myScore && myScore.status !== 'DRAFT'" class="score-details">
          <div class="score-hero">
            <div class="final-score-box">
              <div class="score-num">{{ myScore.finalScore?.toFixed(2) ?? '-' }}</div>
              <div class="score-label">综合评定总分</div>
            </div>
            <div class="level-box">
              <el-tag size="large" :type="getLevelTagType(myScore.scoreLevel || '')" effect="dark">
                {{ formatScoreLevel(myScore.scoreLevel) }}
              </el-tag>
              <div class="level-label">等级评定</div>
            </div>
          </div>

          <el-divider>五维单项考核得分构成</el-divider>

          <el-row :gutter="16" class="dimensions-row">
            <el-col :span="4" :offset="2">
              <div class="dim-box">
                <div class="dim-name">企业指导考核</div>
                <div class="dim-val">{{ myScore.enterpriseScore?.toFixed(1) ?? '-' }}</div>
                <div class="dim-weight">权重: {{ getRuleWeight('enterprise') }}%</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="dim-box">
                <div class="dim-name">过程指导台账</div>
                <div class="dim-val">{{ myScore.processScore?.toFixed(1) ?? '-' }}</div>
                <div class="dim-weight">权重: {{ getRuleWeight('process') }}%</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="dim-box">
                <div class="dim-name">周报质量平均</div>
                <div class="dim-val">{{ myScore.weeklyScore?.toFixed(1) ?? '-' }}</div>
                <div class="dim-weight">权重: {{ getRuleWeight('weekly') }}%</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="dim-box">
                <div class="dim-name">阶段任务材料</div>
                <div class="dim-val">{{ myScore.materialScore?.toFixed(1) ?? '-' }}</div>
                <div class="dim-weight">权重: {{ getRuleWeight('material') }}%</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="dim-box">
                <div class="dim-name">实习总结报告</div>
                <div class="dim-val">{{ myScore.summaryScore?.toFixed(1) ?? '-' }}</div>
                <div class="dim-weight">权重: {{ getRuleWeight('summary') }}%</div>
              </div>
            </el-col>
          </el-row>

          <div v-if="myScore.evaluationComment" class="comment-box">
            <strong>导师综合评语：</strong>
            <p>{{ myScore.evaluationComment }}</p>
          </div>

          <div class="publicity-info" v-if="myScore.publicityStartTime">
            <el-alert
              :title="`公示期：${myScore.publicityStartTime} 至 ${myScore.publicityEndTime || '无'}`"
              type="info"
              show-icon
              :closable="false"
            />
          </div>

          <!-- 申诉操作栏 -->
          <div class="appeal-actions" v-if="canStudentAppeal(myScore)">
            <el-button type="danger" plain :icon="Edit" @click="openAppealDialog">
              对成绩有异议？发起成绩复核申诉
            </el-button>
          </div>

          <!-- 申诉历史痕迹 -->
          <div v-if="myScore.auditHistory && myScore.auditHistory.length > 0" class="audit-timeline">
            <h4 class="history-title">成绩申诉与复核轨迹</h4>
            <el-timeline>
              <el-timeline-item
                v-for="item in myScore.auditHistory"
                :key="item.id"
                :timestamp="item.operateTime"
                :type="item.action.includes('PASS') ? 'success' : 'primary'"
              >
                <div><strong>{{ formatAuditAction(item.action) }}</strong> （经办人：{{ item.auditUserName }}）</div>
                <div v-if="item.appealReason" class="sub-text">申诉理由: {{ item.appealReason }}</div>
                <div v-if="item.auditComment" class="sub-text">处理意见: {{ item.auditComment }}</div>
                <div v-if="item.approvalDocNo" class="sub-text text-danger">批文号: {{ item.approvalDocNo }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>

        <el-empty v-else description="指导教师尚未完成最终综合评定或成绩尚未公示" />
      </el-card>
    </div>

    <!-- 教师 / 管理员端视图：台账与录入 -->
    <div v-else class="admin-score-view">
      <!-- 搜索筛选 -->
      <el-card shadow="never" class="filter-card">
        <el-form :inline="true" :model="filters">
          <el-form-item label="实习任务">
            <el-select v-model="filters.taskId" placeholder="选择任务" clearable style="width: 200px" @change="loadData">
              <el-option
                v-for="task in taskOptions"
                :key="task.id"
                :label="task.taskName"
                :value="task.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="等级评定">
            <el-select v-model="filters.scoreLevel" placeholder="全部等级" clearable style="width: 130px" @change="loadData">
              <el-option label="优秀 (>=90)" value="EXCELLENT" />
              <el-option label="良好 (80-89)" value="GOOD" />
              <el-option label="中等 (70-79)" value="MEDIUM" />
              <el-option label="及格 (60-69)" value="PASS" />
              <el-option label="不及格 (<60)" value="FAIL" />
            </el-select>
          </el-form-item>
          <el-form-item label="成绩状态">
            <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="loadData">
              <el-option label="暂存草稿" value="DRAFT" />
              <el-option label="待院系审核" value="PENDING_AUDIT" />
              <el-option label="待公示" value="PENDING_PUBLICITY" />
              <el-option label="公示中" value="PUBLICITY" />
              <el-option label="已封存发布" value="PUBLISHED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 成绩表格 -->
      <el-card shadow="never" class="table-card">
        <el-table :data="scoreList" v-loading="loading" stripe border style="width: 100%">
          <el-table-column prop="studentName" label="学生姓名" width="120">
            <template #default="{ row }">
              <div><strong>{{ row.studentName }}</strong></div>
              <div class="sub-text">{{ row.studentNo }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="deptName" label="院系" width="130" show-overflow-tooltip />
          <el-table-column prop="teacherName" label="指导教师" width="110">
            <template #default="{ row }">{{ row.teacherName || '-' }}</template>
          </el-table-column>
          <el-table-column label="企业评价" width="95" align="center">
            <template #default="{ row }">{{ row.enterpriseScore?.toFixed(1) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="过程指导" width="95" align="center">
            <template #default="{ row }">{{ row.processScore?.toFixed(1) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="周报均分" width="95" align="center">
            <template #default="{ row }">{{ row.weeklyScore?.toFixed(1) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="阶段材料" width="95" align="center">
            <template #default="{ row }">{{ row.materialScore?.toFixed(1) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="总结报告" width="95" align="center">
            <template #default="{ row }">{{ row.summaryScore?.toFixed(1) ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="综合总分" width="105" align="center">
            <template #default="{ row }">
              <strong style="color: #409eff; font-size: 15px;">
                {{ row.finalScore?.toFixed(2) ?? '-' }}
              </strong>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.scoreLevel" :type="getLevelTagType(row.scoreLevel)" size="small">
                {{ formatScoreLevel(row.scoreLevel) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.status)">
                {{ formatStatus(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetailDialog(row)">
                详情
              </el-button>
              <el-button
                v-if="canTeacherEdit(row)"
                link
                type="warning"
                size="small"
                @click="openScoreEntryDialog(row)"
              >
                评定录入
              </el-button>
              <el-button
                v-if="canDeptAudit(row)"
                link
                type="success"
                size="small"
                @click="handleAudit(row)"
              >
                审核通过
              </el-button>
              <el-button
                v-if="canArbitrate(row)"
                link
                type="danger"
                size="small"
                @click="openArbitrateDialog(row)"
              >
                申诉仲裁
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 教师成绩评定录入弹窗 -->
    <el-dialog v-model="entryDialogVisible" title="实习综合成绩五维评定录入" width="600px">
      <el-form :model="entryForm" label-width="120px" :rules="entryRules" ref="entryFormRef">
        <el-alert
          title="根据学院培养方案，系统自动加权算分。材料分与总结分需提前批阅归档。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-form-item label="企业指导评价" prop="enterpriseScore">
          <el-input-number v-model="entryForm.enterpriseScore" :min="0" :max="100" :precision="1" />
          <span class="field-hint">百分制 (0~100)</span>
        </el-form-item>
        <el-form-item label="企业评语凭据" prop="enterpriseEvaluationUrl">
          <el-input v-model="entryForm.enterpriseEvaluationUrl" placeholder="企业评价表PDF/图片URL (需HTTPS)" />
        </el-form-item>
        <el-form-item label="过程指导得分" prop="processScore">
          <el-input-number v-model="entryForm.processScore" :min="0" :max="100" :precision="1" />
          <span class="field-hint">指导记录折算得分</span>
        </el-form-item>
        <el-form-item label="周报质量均分" prop="weeklyScore">
          <el-input-number v-model="entryForm.weeklyScore" :min="0" :max="100" :precision="1" />
          <span class="field-hint">批阅周报平均成绩</span>
        </el-form-item>
        <el-form-item label="阶段材料得分" prop="materialScore">
          <el-input-number v-model="entryForm.materialScore" :min="0" :max="100" :precision="1" />
          <span class="field-hint">三阶段任务提交评分</span>
        </el-form-item>
        <el-form-item label="实习总结报告" prop="summaryScore">
          <el-input-number v-model="entryForm.summaryScore" :min="0" :max="100" :precision="1" />
          <span class="field-hint">总结报告评阅得分</span>
        </el-form-item>
        <el-form-item label="综合考核评语" prop="evaluationComment">
          <el-input
            v-model="entryForm.evaluationComment"
            type="textarea"
            :rows="3"
            placeholder="请填写对该生实习全过程的综合评价与指导意见"
          />
        </el-form-item>
        <el-form-item label="提交模式">
          <el-switch
            v-model="entryForm.submitToDept"
            active-text="直接提交院系审核"
            inactive-text="仅暂存为草稿"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="entrySubmitting" @click="submitScoreEntry">
          确认保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 成绩详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="实习综合评定成绩详情与审计" width="650px">
      <div v-if="currentScore" class="detail-dialog-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生姓名">{{ currentScore.studentName }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ currentScore.studentNo }}</el-descriptions-item>
          <el-descriptions-item label="所属院系">{{ currentScore.deptName }}</el-descriptions-item>
          <el-descriptions-item label="指导教师">{{ currentScore.teacherName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="综合总分">
            <strong style="color: #409eff; font-size: 16px;">{{ currentScore.finalScore?.toFixed(2) ?? '-' }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="评定等级">
            <el-tag :type="getLevelTagType(currentScore.scoreLevel || '')">
              {{ formatScoreLevel(currentScore.scoreLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前状态" :span="2">
            <el-tag :type="getStatusTagType(currentScore.status)">
              {{ formatStatus(currentScore.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">五维成绩明细</h4>
        <el-descriptions :column="5" direction="vertical" border>
          <el-descriptions-item label="企业指导">{{ currentScore.enterpriseScore?.toFixed(1) ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="过程指导">{{ currentScore.processScore?.toFixed(1) ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="周报质量">{{ currentScore.weeklyScore?.toFixed(1) ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="阶段材料">{{ currentScore.materialScore?.toFixed(1) ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="总结报告">{{ currentScore.summaryScore?.toFixed(1) ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="currentScore.evaluationComment" style="margin-top: 14px;">
          <strong>导师评语：</strong>
          <p class="comment-text">{{ currentScore.evaluationComment }}</p>
        </div>

        <div v-if="currentScore.enterpriseEvaluationUrl" style="margin-top: 8px;">
          <strong>企业凭证：</strong>
          <el-link type="primary" :href="currentScore.enterpriseEvaluationUrl" target="_blank">
            查看企业评分佐证附件
          </el-link>
        </div>

        <h4 class="section-title" v-if="currentScore.auditHistory && currentScore.auditHistory.length > 0">
          申诉复核与调分审计快照
        </h4>
        <el-timeline v-if="currentScore.auditHistory && currentScore.auditHistory.length > 0">
          <el-timeline-item
            v-for="h in currentScore.auditHistory"
            :key="h.id"
            :timestamp="h.operateTime"
            type="primary"
          >
            <div><strong>{{ formatAuditAction(h.action) }}</strong> （操作人: {{ h.auditUserName }}）</div>
            <div v-if="h.approvalDocNo" class="sub-text text-danger">红头批文号: {{ h.approvalDocNo }}</div>
            <div v-if="h.auditComment" class="sub-text">处理意见: {{ h.auditComment }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>

    <!-- 学生发起成绩复核申诉弹窗 -->
    <el-dialog v-model="appealDialogVisible" title="发起实习成绩复核申诉" width="520px">
      <el-form :model="appealForm" label-width="100px" :rules="appealRules" ref="appealFormRef">
        <el-alert
          title="每位学生针对本次综合成绩仅有一次申诉机会，请审慎填写申诉原因并提供有效佐证。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-form-item label="申诉理由" prop="appealReason">
          <el-input
            v-model="appealForm.appealReason"
            type="textarea"
            :rows="4"
            placeholder="请详细阐述评分存在异议的具体环节（如周报均分核算遗漏、企业评分误差等）"
          />
        </el-form-item>
        <el-form-item label="佐证附件" prop="appealAttachmentUrl">
          <el-input v-model="appealForm.appealAttachmentUrl" placeholder="佐证材料URL (需HTTPS)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="appealSubmitting" @click="submitAppealAction">
          提交申诉申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 院系负责人成绩申诉仲裁与调分弹窗 -->
    <el-dialog v-model="arbitrateDialogVisible" title="院系级成绩申诉复核与仲裁" width="600px">
      <el-form :model="arbitrateForm" label-width="120px" :rules="arbitrateRules" ref="arbitrateFormRef">
        <el-form-item label="仲裁裁定" prop="action">
          <el-radio-group v-model="arbitrateForm.action">
            <el-radio label="PASS">申诉成立，准予调分</el-radio>
            <el-radio label="REJECT">申诉驳回，维持原判</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="红头批文号" prop="approvalDocNo" v-if="arbitrateForm.action === 'PASS'">
          <el-input v-model="arbitrateForm.approvalDocNo" placeholder="如: 计通院发[2026]18号成绩更正批复" />
        </el-form-item>
        <el-form-item label="仲裁审核意见" prop="auditComment">
          <el-input
            v-model="arbitrateForm.auditComment"
            type="textarea"
            :rows="3"
            placeholder="请填写仲裁委员会或教学主管复议结论意见"
          />
        </el-form-item>

        <template v-if="arbitrateForm.action === 'PASS'">
          <el-divider>仲裁更正五维得分</el-divider>
          <el-form-item label="更正企业得分">
            <el-input-number v-model="arbitrateForm.enterpriseScore" :min="0" :max="100" :precision="1" />
          </el-form-item>
          <el-form-item label="更正过程得分">
            <el-input-number v-model="arbitrateForm.processScore" :min="0" :max="100" :precision="1" />
          </el-form-item>
          <el-form-item label="更正周报得分">
            <el-input-number v-model="arbitrateForm.weeklyScore" :min="0" :max="100" :precision="1" />
          </el-form-item>
          <el-form-item label="更正材料得分">
            <el-input-number v-model="arbitrateForm.materialScore" :min="0" :max="100" :precision="1" />
          </el-form-item>
          <el-form-item label="更正总结得分">
            <el-input-number v-model="arbitrateForm.summaryScore" :min="0" :max="100" :precision="1" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="arbitrateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="arbitrateSubmitting" @click="submitArbitrateAction">
          确认提交仲裁决定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus';
import { Promotion, Refresh, Search, Edit } from '@element-plus/icons-vue';
import {
  getScoreList,
  getMyScore,
  submitScore,
  auditScore,
  publishScores,
  submitAppeal,
  arbitrateAppeal,
  ScoreSummaryVO
} from '@/api/phase7';
import request from '@/utils/request';

// 用户身份
const currentUserId = Number(localStorage.getItem('userId') || '0');
const userType = localStorage.getItem('userType') || '';
const isStudent = computed(() => userType === 'STUDENT');
const isTeacher = computed(() => userType === 'TEACHER');
const isDeptAdmin = computed(() => userType === 'DEPT_ADMIN');
const isAdmin = computed(() => userType === 'SYS_ADMIN');

// 列表与状态
const loading = ref(false);
const scoreList = ref<ScoreSummaryVO[]>([]);
const taskOptions = ref<{ id: number; taskName: string }[]>([]);

// 学生自身成绩
const myScoreLoading = ref(false);
const myScore = ref<ScoreSummaryVO | null>(null);

// 筛选条件
const filters = reactive({
  taskId: undefined as number | undefined,
  scoreLevel: undefined as string | undefined,
  status: undefined as string | undefined
});

// 成绩评定录入弹窗
const entryDialogVisible = ref(false);
const entrySubmitting = ref(false);
const entryFormRef = ref<FormInstance>();
const entryForm = reactive({
  taskId: 0,
  studentId: 0,
  enterpriseScore: 85,
  processScore: 90,
  weeklyScore: 88,
  materialScore: 86,
  summaryScore: 85,
  evaluationComment: '',
  enterpriseEvaluationUrl: '',
  submitToDept: true
});
const entryRules = {
  enterpriseScore: [{ required: true, message: '请录入企业评价分', trigger: 'blur' }],
  processScore: [{ required: true, message: '请录入过程指导分', trigger: 'blur' }],
  weeklyScore: [{ required: true, message: '请录入周报得分', trigger: 'blur' }],
  materialScore: [{ required: true, message: '请录入阶段材料得分', trigger: 'blur' }],
  summaryScore: [{ required: true, message: '请录入总结报告得分', trigger: 'blur' }]
};

// 详情抽屉
const detailDialogVisible = ref(false);
const currentScore = ref<ScoreSummaryVO | null>(null);

// 学生申诉弹窗
const appealDialogVisible = ref(false);
const appealSubmitting = ref(false);
const appealFormRef = ref<FormInstance>();
const appealForm = reactive({
  scoreId: 0,
  appealReason: '',
  appealAttachmentUrl: ''
});
const appealRules = {
  appealReason: [{ required: true, message: '请填写申诉理由', trigger: 'blur' }]
};

// 院系仲裁弹窗
const arbitrateDialogVisible = ref(false);
const arbitrateSubmitting = ref(false);
const arbitrateFormRef = ref<FormInstance>();
const arbitrateForm = reactive({
  scoreId: 0,
  action: 'PASS' as 'PASS' | 'REJECT',
  auditComment: '',
  approvalDocNo: '',
  enterpriseScore: undefined as number | undefined,
  processScore: undefined as number | undefined,
  weeklyScore: undefined as number | undefined,
  materialScore: undefined as number | undefined,
  summaryScore: undefined as number | undefined
});
const arbitrateRules = {
  action: [{ required: true, message: '请选择仲裁结论', trigger: 'change' }],
  auditComment: [{ required: true, message: '请填写仲裁意见', trigger: 'blur' }],
  approvalDocNo: [{ required: true, message: '调分必须具备红头批文号', trigger: 'blur' }]
};

onMounted(() => {
  loadTasks();
  if (isStudent.value) {
    loadStudentScore();
  } else {
    loadData();
  }
});

const loadTasks = async () => {
  try {
    const res: any = await request.get('/tasks', { params: { size: 100 } });
    if (res.code === 200 && res.data) {
      taskOptions.value = (res.data.records || res.data).map((t: any) => ({
        id: t.id,
        taskName: t.taskName
      }));
      if (isStudent.value && taskOptions.value.length > 0 && !filters.taskId) {
        filters.taskId = taskOptions.value[0].id;
        loadStudentScore();
      }
    }
  } catch (e) {
    console.error('加载任务失败', e);
  }
};

const loadStudentScore = async () => {
  if (!filters.taskId && taskOptions.value.length > 0) {
    filters.taskId = taskOptions.value[0].id;
  }
  if (!filters.taskId) return;
  myScoreLoading.value = true;
  try {
    const res = await getMyScore(filters.taskId);
    if (res.code === 200) {
      myScore.value = res.data;
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载个人成绩失败');
  } finally {
    myScoreLoading.value = false;
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getScoreList(filters);
    if (res.code === 200) {
      scoreList.value = res.data || [];
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载成绩列表失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.taskId = undefined;
  filters.scoreLevel = undefined;
  filters.status = undefined;
  loadData();
};

const openBatchPublishDialog = () => {
  if (!filters.taskId) {
    ElMessage.warning('请先在筛选条件中选择要批量发布公示的实习任务');
    return;
  }
  ElMessageBox.confirm(
    '发布成绩公示后，所有通过院系审核的成绩将对学生全量开放，并进入为期7天的法定公示申诉期。确认执行批量公示？',
    '发布公示确认',
    {
      confirmButtonText: '立即发布公示',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await publishScores(filters.taskId!);
      if (res.code === 200) {
        ElMessage.success('实习成绩已批量进入公示期！');
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '发布公示失败');
    }
  });
};

const canTeacherEdit = (row: ScoreSummaryVO) => {
  if (row.status === 'PUBLISHED') return false;
  if (isAdmin.value) return true;
  if (isTeacher.value && row.teacherId === currentUserId && (row.status === 'DRAFT' || row.status === 'PENDING_AUDIT')) return true;
  return false;
};

const canDeptAudit = (row: ScoreSummaryVO) => {
  if (row.status === 'PENDING_AUDIT' && (isDeptAdmin.value || isAdmin.value)) return true;
  return false;
};

const canArbitrate = (row: ScoreSummaryVO) => {
  if (row.status === 'PUBLICITY' && (isDeptAdmin.value || isAdmin.value)) return true;
  return false;
};

const canStudentAppeal = (score: ScoreSummaryVO) => {
  if (score.status === 'PUBLICITY' || score.status === 'PUBLISHED') return true;
  return false;
};

const openScoreEntryDialog = (row: ScoreSummaryVO) => {
  entryForm.taskId = row.taskId;
  entryForm.studentId = row.studentId;
  entryForm.enterpriseScore = row.enterpriseScore ?? 85;
  entryForm.processScore = row.processScore ?? 85;
  entryForm.weeklyScore = row.weeklyScore ?? 85;
  entryForm.materialScore = row.materialScore ?? 85;
  entryForm.summaryScore = row.summaryScore ?? 85;
  entryForm.evaluationComment = row.evaluationComment || '';
  entryForm.enterpriseEvaluationUrl = row.enterpriseEvaluationUrl || '';
  entryForm.submitToDept = true;
  entryDialogVisible.value = true;
};

const submitScoreEntry = async () => {
  if (!entryFormRef.value) return;
  await entryFormRef.value.validate(async (valid) => {
    if (!valid) return;
    entrySubmitting.value = true;
    try {
      const res = await submitScore({
        taskId: entryForm.taskId,
        studentId: entryForm.studentId,
        enterpriseScore: entryForm.enterpriseScore,
        processScore: entryForm.processScore,
        weeklyScore: entryForm.weeklyScore,
        materialScore: entryForm.materialScore,
        summaryScore: entryForm.summaryScore,
        evaluationComment: entryForm.evaluationComment,
        enterpriseEvaluationUrl: entryForm.enterpriseEvaluationUrl,
        submitToDept: entryForm.submitToDept
      });
      if (res.code === 200) {
        ElMessage.success('五维成绩综合评定保存成功');
        entryDialogVisible.value = false;
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '评定保存失败');
    } finally {
      entrySubmitting.value = false;
    }
  });
};

const handleAudit = async (row: ScoreSummaryVO) => {
  try {
    const res = await auditScore(row.id);
    if (res.code === 200) {
      ElMessage.success('该生实习成绩审核通过');
      loadData();
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '审核失败');
  }
};

const openDetailDialog = (row: ScoreSummaryVO) => {
  currentScore.value = row;
  detailDialogVisible.value = true;
};

const openAppealDialog = () => {
  if (!myScore.value) return;
  appealForm.scoreId = myScore.value.id;
  appealForm.appealReason = '';
  appealForm.appealAttachmentUrl = '';
  appealDialogVisible.value = true;
};

const submitAppealAction = async () => {
  if (!appealFormRef.value) return;
  await appealFormRef.value.validate(async (valid) => {
    if (!valid) return;
    appealSubmitting.value = true;
    try {
      const res = await submitAppeal({
        scoreId: appealForm.scoreId,
        appealReason: appealForm.appealReason,
        appealAttachmentUrl: appealForm.appealAttachmentUrl
      });
      if (res.code === 200) {
        ElMessage.success('成绩复核申诉已成功提交至院系教学督导组');
        appealDialogVisible.value = false;
        loadStudentScore();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '申诉提交失败');
    } finally {
      appealSubmitting.value = false;
    }
  });
};

const openArbitrateDialog = (row: ScoreSummaryVO) => {
  arbitrateForm.scoreId = row.id;
  arbitrateForm.action = 'PASS';
  arbitrateForm.auditComment = '';
  arbitrateForm.approvalDocNo = '';
  arbitrateForm.enterpriseScore = row.enterpriseScore;
  arbitrateForm.processScore = row.processScore;
  arbitrateForm.weeklyScore = row.weeklyScore;
  arbitrateForm.materialScore = row.materialScore;
  arbitrateForm.summaryScore = row.summaryScore;
  arbitrateDialogVisible.value = true;
};

const submitArbitrateAction = async () => {
  if (!arbitrateFormRef.value) return;
  await arbitrateFormRef.value.validate(async (valid) => {
    if (!valid) return;
    arbitrateSubmitting.value = true;
    try {
      const res = await arbitrateAppeal(arbitrateForm.scoreId, {
        action: arbitrateForm.action,
        auditComment: arbitrateForm.auditComment,
        approvalDocNo: arbitrateForm.approvalDocNo,
        enterpriseScore: arbitrateForm.enterpriseScore,
        processScore: arbitrateForm.processScore,
        weeklyScore: arbitrateForm.weeklyScore,
        materialScore: arbitrateForm.materialScore,
        summaryScore: arbitrateForm.summaryScore
      });
      if (res.code === 200) {
        ElMessage.success('申诉复核仲裁决定已执行并存证');
        arbitrateDialogVisible.value = false;
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '仲裁提交失败');
    } finally {
      arbitrateSubmitting.value = false;
    }
  });
};

// 格式化辅助
const getRuleWeight = (dim: string) => {
  if (!myScore.value?.gradeRuleSnapshotJson) {
    if (dim === 'enterprise') return 20;
    if (dim === 'process') return 20;
    if (dim === 'weekly') return 20;
    if (dim === 'material') return 20;
    if (dim === 'summary') return 20;
    return 20;
  }
  try {
    const parsed = JSON.parse(myScore.value.gradeRuleSnapshotJson);
    if (dim === 'enterprise') return parsed.enterpriseWeight ?? 20;
    if (dim === 'process') return parsed.processWeight ?? 20;
    if (dim === 'weekly') return parsed.weeklyWeight ?? 20;
    if (dim === 'material') return parsed.materialWeight ?? 20;
    if (dim === 'summary') return parsed.summaryWeight ?? 20;
  } catch (e) {}
  return 20;
};

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'DRAFT': return 'info';
    case 'PENDING_AUDIT': return 'warning';
    case 'PENDING_PUBLICITY': return 'primary';
    case 'PUBLICITY': return 'danger';
    case 'PUBLISHED': return 'success';
    default: return 'info';
  }
};

const formatStatus = (status: string) => {
  switch (status) {
    case 'DRAFT': return '暂存草稿';
    case 'PENDING_AUDIT': return '待院系终审';
    case 'PENDING_PUBLICITY': return '待公示';
    case 'PUBLICITY': return '公示申诉期';
    case 'PUBLISHED': return '已归档发布';
    case 'NOT_SUBMITTED': return '未录入';
    default: return status;
  }
};

const getLevelTagType = (level: string) => {
  switch (level) {
    case 'EXCELLENT': return 'success';
    case 'GOOD': return 'primary';
    case 'MEDIUM': return 'warning';
    case 'PASS': return 'info';
    case 'FAIL': return 'danger';
    default: return 'info';
  }
};

const formatScoreLevel = (level?: string) => {
  switch (level) {
    case 'EXCELLENT': return '优秀';
    case 'GOOD': return '良好';
    case 'MEDIUM': return '中等';
    case 'PASS': return '及格';
    case 'FAIL': return '不及格';
    default: return level || '-';
  }
};

const formatAuditAction = (action: string) => {
  switch (action) {
    case 'APPEAL_APPLY': return '学生提出成绩复核申诉';
    case 'APPEAL_PASS': return '仲裁通过（准予更正调分）';
    case 'APPEAL_REJECT': return '仲裁驳回（维持原评定）';
    case 'SPECIAL_MODIFY': return '特批调分更正';
    default: return action;
  }
};
</script>

<style scoped>
.score-manage-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 600;
  color: #1f2937;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
}

.score-hero {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 60px;
  padding: 24px 0;
}

.final-score-box {
  text-align: center;
}

.score-num {
  font-size: 52px;
  font-weight: 800;
  color: #409eff;
  line-height: 1;
}

.score-label {
  font-size: 14px;
  color: #606266;
  margin-top: 8px;
}

.level-box {
  text-align: center;
}

.level-label {
  font-size: 14px;
  color: #606266;
  margin-top: 8px;
}

.dim-box {
  text-align: center;
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px 8px;
  border: 1px solid #e2e8f0;
}

.dim-name {
  font-size: 13px;
  color: #4b5563;
}

.dim-val {
  font-size: 24px;
  font-weight: bold;
  color: #1f2937;
  margin: 6px 0;
}

.dim-weight {
  font-size: 12px;
  color: #9ca3af;
}

.comment-box {
  margin-top: 24px;
  background: #f8fafc;
  border-radius: 6px;
  padding: 14px;
  border: 1px solid #e2e8f0;
}

.publicity-info {
  margin-top: 16px;
}

.appeal-actions {
  margin-top: 20px;
  text-align: center;
}

.audit-timeline {
  margin-top: 30px;
}

.history-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #374151;
}

.filter-card, .table-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 18px 0 10px 0;
  color: #303133;
}
</style>
