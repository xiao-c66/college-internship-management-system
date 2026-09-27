<template>
  <div class="internship-apply-container" v-loading="loading">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">学生校外实习申报 (APPLY-001 ~ APPLY-009)</h2>
          <p class="page-subtitle">录入落实的实习单位、岗位、地址、起止时间与协议凭证，完成指导教师与院系双级审批</p>
        </div>
        <div class="header-badge" v-if="taskId > 0">
          <el-tag :type="getStatusTagType(apply?.applyStatus)" size="large" effect="dark">
            当前状态: {{ formatApplyStatus(apply?.applyStatus) }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 无可用生效任务时展示空状态 -->
    <el-card shadow="never" class="empty-card" v-if="!taskId || taskId <= 0" style="margin-top: 16px; text-align: center; padding: 40px 0;">
      <el-empty description="当前暂无可用的生效实习批次任务，请联系院系教学负责人确认任务发布状态" />
    </el-card>

    <template v-else>
      <!-- APPLY-009 审核生效强锁定提示栏 -->
      <el-alert
        v-if="isApprovedLocked"
        type="warning"
        show-icon
        :closable="false"
        class="locked-alert"
      >
        <template #title>
          <strong>🔒 实习信息已经审核生效 (APPROVED)，主数据已物理锁定只读 (APPLY-009)</strong>
        </template>
        <template #default>
          <div>
            您的实习申请已完成指导教师初审与二级院系终审复核。单位、岗位、地址、联系人和起止时间变动禁止直接普通修改。
            如需修改单位或重大变动，请前往实习变更模块提交正式变更申请！
          </div>
          <div class="locked-action mt-2">
            <el-button type="warning" size="small" @click="handleExceptionChange">
              发起实习变更申请 (前往变更审批)
            </el-button>
          </div>
        </template>
      </el-alert>

      <!-- 审核被退回修改提示栏 -->
      <el-alert
        v-if="isRejected"
        type="error"
        show-icon
        :closable="false"
        class="rejected-alert"
      >
        <template #title>
          <strong>⚠️ 您的实习申报被退回修改</strong>
        </template>
        <template #default>
          <div>
            <strong>退回意见：</strong> {{ latestRejectOpinion || '请根据教师或院系审核要求重新修改后再次提交。' }}
          </div>
        </template>
      </el-alert>

    <el-row :gutter="20" style="margin-top: 16px">
      <!-- 左侧：实习申报表单 -->
      <el-col :span="16">
        <el-card shadow="never" class="form-card">
          <template #header>
            <div class="card-header-title">
              <strong>📝 实习申报详细信息填报</strong>
              <el-tag v-if="isApprovedLocked" type="danger" size="small" class="ml-2">只读模式 (禁止覆盖)</el-tag>
            </div>
          </template>

          <el-form
            :model="form"
            :rules="formRules"
            ref="formRef"
            label-width="130px"
            label-position="right"
            :disabled="isApprovedLocked"
          >
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="实习用人单位" prop="companyName">
                  <el-input v-model="form.companyName" placeholder="请填写企业营业执照法定全称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="实习岗位名称" prop="jobPosition">
                  <el-input v-model="form.jobPosition" placeholder="如 Java后端开发实习生" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="岗位工作详细地址" prop="jobAddress">
              <el-input v-model="form.jobAddress" placeholder="省/市/区/街道及楼宇门牌号" />
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="单位联系人" prop="companyContactPerson">
                  <el-input v-model="form.companyContactPerson" placeholder="HR或业务导师姓名" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人电话" prop="companyContactPhone">
                  <el-input v-model="form.companyContactPhone" placeholder="手机号或带区号座机" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人邮箱" prop="companyContactEmail">
                  <el-input v-model="form.companyContactEmail" placeholder="企业工作邮箱" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="14">
                <el-form-item label="实习起止时间" prop="dateRange">
                  <el-date-picker
                    v-model="form.dateRange"
                    type="daterange"
                    range-separator="至"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="10">
                <el-form-item label="实习组织模式" prop="internshipMode">
                  <el-select v-model="form.internshipMode" placeholder="请选择实习组织模式" style="width: 100%">
                    <el-option value="DISTRIBUTED" label="分散实习 (自主落实)" />
                    <el-option value="CENTRALIZED" label="集中实习 (基地组织)" />
                    <el-option value="HYBRID" label="混合实习模式" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="主要职责与内容" prop="jobDuties">
              <el-input
                v-model="form.jobDuties"
                type="textarea"
                :rows="4"
                placeholder="详细说明在单位从事的主要技术任务、业务职责与安全规范遵守情况..."
              />
            </el-form-item>

            <el-form-item label="三方协议盖章件" prop="agreementFileUrl">
              <el-input v-model="form.agreementFileUrl" placeholder="如 /uploads/agreements/2021003011_agreement.pdf" />
            </el-form-item>

            <!-- 操作按钮区域：审核生效时完全隐藏草稿和提交按钮 (APPLY-009) -->
            <div class="form-actions" v-if="!isApprovedLocked">
              <el-button :loading="saveDraftLoading" @click="handleSaveDraft">暂存为草稿</el-button>
              <el-button type="primary" size="large" :loading="submitLoading" @click="handleSubmit">
                正式提交实习申报 (进入双级审批)
              </el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：审核流转历史轨迹与快照 (REVIEW-006) -->
      <el-col :span="8">
        <el-card shadow="never" class="history-card">
          <template #header>
            <div class="card-header-title"><strong>📜 审批流转轨迹与历史记录</strong></div>
          </template>

          <div v-if="!apply?.auditHistories || apply.auditHistories.length === 0" class="no-history">
            <el-empty description="暂无审批流转记录，提交后将显示流转轨迹" :image-size="80" />
          </div>

          <el-timeline v-else>
            <el-timeline-item
              v-for="h in apply.auditHistories"
              :key="h.id"
              :type="getHistoryType(h.auditAction)"
              :timestamp="h.auditTime"
              placement="top"
            >
              <div class="history-item">
                <div class="history-title">
                  <strong>{{ formatNodeName(h.nodeName) }}</strong>
                  <el-tag size="small" :type="h.auditAction === 'APPROVED' ? 'success' : 'danger'" class="ml-2">
                    {{ h.auditAction === 'APPROVED' ? '通过' : '退回修改' }}
                  </el-tag>
                </div>
                <div class="history-auditor">
                  审核人: {{ h.auditorName }} ({{ h.auditorRole }})
                </div>
                <div class="history-opinion">
                  意见: {{ h.auditOpinion }}
                </div>
                <div v-if="h.snapshotData" class="history-snapshot-btn">
                  <el-button link type="primary" size="small" @click="viewSnapshot(h.snapshotData)">
                    查看当时表单快照
                  </el-button>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
    </template>

    <!-- 快照查看弹窗 -->
    <el-dialog v-model="snapshotDialogVisible" title="审批节点数据快照记录" width="600px">
      <pre class="snapshot-json">{{ parsedSnapshot }}</pre>
      <template #footer>
        <el-button @click="snapshotDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getTaskList,
  getMyApply,
  saveApplyDraft,
  submitApply,
  ApplyItem
} from '@/api';

const route = useRoute();
const taskId = ref<number>(Number(route.query.taskId) || 0);
const loading = ref(false);
const saveDraftLoading = ref(false);
const submitLoading = ref(false);
const apply = ref<ApplyItem | null>(null);
const formRef = ref<FormInstance>();

const snapshotDialogVisible = ref(false);
const parsedSnapshot = ref('');

const form = reactive({
  companyName: '',
  jobPosition: '',
  jobAddress: '',
  companyContactPerson: '',
  companyContactPhone: '',
  companyContactEmail: '',
  dateRange: [] as string[],
  internshipMode: '',
  jobDuties: '',
  agreementFileUrl: ''
});

const formRules = {
  companyName: [{ required: true, message: '请填写实习用人单位法定全称', trigger: 'blur' }],
  jobPosition: [{ required: true, message: '请填写实习岗位', trigger: 'blur' }],
  jobAddress: [{ required: true, message: '请填写工作地点详细地址', trigger: 'blur' }],
  companyContactPerson: [{ required: true, message: '请填写单位联系人', trigger: 'blur' }],
  companyContactPhone: [{ required: true, message: '请填写联系电话', trigger: 'blur' }],
  dateRange: [{ required: true, message: '请选择实习起止时间', trigger: 'change' }],
  internshipMode: [{ required: true, message: '请选择实习组织模式', trigger: 'change' }]
};

const isApprovedLocked = computed(() => {
  return apply.value?.applyStatus === 'APPROVED' || apply.value?.isLocked === 1;
});

const isRejected = computed(() => {
  return apply.value?.applyStatus === 'TEACHER_REJECTED' || apply.value?.applyStatus === 'DEPT_REJECTED';
});

const latestRejectOpinion = computed(() => {
  if (apply.value?.deptOpinion) return apply.value.deptOpinion;
  if (apply.value?.teacherOpinion) return apply.value.teacherOpinion;
  const histories = apply.value?.auditHistories;
  if (histories && histories.length > 0) {
    const last = histories[0];
    if (last.auditAction === 'REJECTED') return last.auditOpinion;
  }
  return '';
});

const resolveTaskId = async (): Promise<number> => {
  if (taskId.value > 0) return taskId.value;
  try {
    const res = await getTaskList({ status: 'PUBLISHED' });
    if (res.data && res.data.length > 0) {
      taskId.value = res.data[0].id;
      return taskId.value;
    }
  } catch (e) {
    console.error('获取生效任务失败', e);
  }
  taskId.value = 0;
  return 0;
};

const loadApply = async () => {
  loading.value = true;
  try {
    const tid = await resolveTaskId();
    if (!tid || tid <= 0) {
      apply.value = null;
      return;
    }
    const res = await getMyApply(tid);
    apply.value = res.data;
    if (apply.value) {
      form.companyName = apply.value.companyName || '';
      form.jobPosition = apply.value.jobPosition || '';
      form.jobAddress = apply.value.jobAddress || '';
      form.companyContactPerson = apply.value.companyContactPerson || '';
      form.companyContactPhone = apply.value.companyContactPhone || '';
      form.companyContactEmail = apply.value.companyContactEmail || '';
      if (apply.value.startDate && apply.value.endDate) {
        form.dateRange = [apply.value.startDate, apply.value.endDate];
      }
      form.internshipMode = apply.value.internshipMode || '';
      form.jobDuties = apply.value.jobDuties || '';
      form.agreementFileUrl = apply.value.agreementFileUrl || '';
    }
  } catch (e) {
    console.error('加载实习申报失败', e);
  } finally {
    loading.value = false;
  }
};

const handleSaveDraft = async () => {
  if (!taskId.value || taskId.value <= 0) {
    ElMessage.warning('当前暂无可用的生效实习任务，无法暂存');
    return;
  }
  saveDraftLoading.value = true;
  try {
    const payload = buildPayload();
    await saveApplyDraft(payload);
    ElMessage.success('实习申报已暂存为草稿');
    await loadApply();
  } finally {
    saveDraftLoading.value = false;
  }
};

const handleSubmit = async () => {
  if (!taskId.value || taskId.value <= 0) {
    ElMessage.warning('当前暂无可用的生效实习任务，无法提交');
    return;
  }
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    submitLoading.value = true;
    try {
      const payload = buildPayload();
      await submitApply(payload);
      ElMessage.success('实习申报已正式提交，进入指导教师初审环节！');
      await loadApply();
    } finally {
      submitLoading.value = false;
    }
  });
};

const buildPayload = () => {
  return {
    taskId: taskId.value,
    companyName: form.companyName,
    jobPosition: form.jobPosition,
    jobAddress: form.jobAddress,
    companyContactPerson: form.companyContactPerson,
    companyContactPhone: form.companyContactPhone,
    companyContactEmail: form.companyContactEmail,
    startDate: form.dateRange[0],
    endDate: form.dateRange[1],
    internshipMode: form.internshipMode,
    jobDuties: form.jobDuties,
    agreementFileUrl: form.agreementFileUrl
  };
};

const handleExceptionChange = () => {
  ElMessageBox.alert(
    '根据《高校学生校外实习安全与管理规程》，实习申报经院系终审通过后已生效锁定。若确因实习企业发生经营异常、撤岗或不可抗力等特殊原因需变更实习单位，请提前向校内指导教师与学院教学管理科递交《校外实习变更审批表》盖章件，由学院主管退回申请后重新填报。',
    '校外实习单位与岗位重大变更指引',
    {
      confirmButtonText: '已知晓流程',
      type: 'warning'
    }
  );
};

const viewSnapshot = (dataStr?: string) => {
  if (!dataStr) return;
  try {
    parsedSnapshot.value = JSON.stringify(JSON.parse(dataStr), null, 2);
  } catch {
    parsedSnapshot.value = dataStr;
  }
  snapshotDialogVisible.value = true;
};

const formatApplyStatus = (status?: string) => {
  switch (status) {
    case 'DRAFT': return '草稿 (未提交)';
    case 'SUBMITTED': return '已提交 (待教师初审)';
    case 'TEACHER_APPROVED': return '教师初审通过 (待院系终审)';
    case 'TEACHER_REJECTED': return '教师初审退回';
    case 'APPROVED': return '终审通过 (已生效锁定)';
    case 'DEPT_REJECTED': return '院系终审退回';
    default: return status || '未填报';
  }
};

const getStatusTagType = (status?: string) => {
  switch (status) {
    case 'APPROVED': return 'success';
    case 'SUBMITTED':
    case 'TEACHER_APPROVED': return 'warning';
    case 'TEACHER_REJECTED':
    case 'DEPT_REJECTED': return 'danger';
    case 'DRAFT': return 'info';
    default: return 'info';
  }
};

const formatNodeName = (node?: string) => {
  switch (node) {
    case 'TEACHER_AUDIT': return '校内指导教师初审';
    case 'DEPT_AUDIT': return '二级院系终审复核';
    default: return node || '审核节点';
  }
};

const getHistoryType = (action?: string) => {
  return action === 'APPROVED' ? 'success' : 'danger';
};

onMounted(() => {
  loadApply();
});
</script>

<style scoped lang="scss">
.internship-apply-container {
  padding: 20px;

  .header-card {
    margin-bottom: 16px;
    border-radius: 8px;

    .header-flex {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .page-title {
        font-size: 20px;
        font-weight: 600;
        margin: 0 0 6px 0;
        color: #1f2d3d;
      }

      .page-subtitle {
        font-size: 13px;
        color: #8492a6;
        margin: 0;
      }
    }
  }

  .locked-alert {
    margin-bottom: 16px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.8;

    .locked-action {
      margin-top: 8px;
    }
  }

  .rejected-alert {
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .form-card, .history-card {
    border-radius: 8px;

    .card-header-title {
      font-size: 15px;
      color: #303133;
      display: flex;
      align-items: center;
    }
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 16px;
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #ebeef5;
  }

  .history-item {
    font-size: 13px;

    .history-title {
      display: flex;
      align-items: center;
      margin-bottom: 4px;
    }

    .history-auditor {
      color: #606266;
      margin-bottom: 4px;
    }

    .history-opinion {
      color: #303133;
      background: #f8fafc;
      padding: 6px 10px;
      border-radius: 4px;
      margin-bottom: 6px;
    }
  }

  .snapshot-json {
    background: #1e1e1e;
    color: #9cdcfe;
    padding: 14px;
    border-radius: 6px;
    max-height: 400px;
    overflow-y: auto;
    font-size: 12px;
    font-family: monospace;
  }
}
</style>
