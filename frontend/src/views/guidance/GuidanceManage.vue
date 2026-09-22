<template>
  <div class="guidance-manage-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">实习过程指导走访台账 (API-065 ~ API-067)</h2>
          <p class="page-subtitle">
            教师线上连线与现场实地走访全留痕 | 学生受指导确认（CAS事务条件更新防并发）| EasyExcel 台账导出
          </p>
        </div>
        <div class="header-action">
          <el-button
            v-if="canCreateGuidance"
            type="primary"
            :icon="Plus"
            @click="openCreateDialog"
          >
            登记指导记录 (API-065)
          </el-button>
          <el-button
            v-if="canExport"
            type="success"
            :icon="Download"
            :loading="exporting"
            @click="handleExportExcel"
          >
            导出指导台账 Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 筛选过滤栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="实习任务">
          <el-select
            v-model="filterTaskId"
            placeholder="请选择实习任务"
            style="width: 240px"
            @change="handleTaskChange"
          >
            <el-option
              v-for="task in taskList"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指导形式">
          <el-select v-model="filterGuidanceType" placeholder="全部形式" clearable style="width: 150px" @change="loadList">
            <el-option label="全部形式" value="" />
            <el-option label="现场实地走访 (ONSITE)" value="ONSITE" />
            <el-option label="线上视频连线 (ONLINE)" value="ONLINE" />
            <el-option label="电话指导沟通 (PHONE)" value="PHONE" />
            <el-option label="邮件及其他 (EMAIL_OTHER)" value="EMAIL_OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="指导日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
            @change="handleDateRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 指导台账列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="guidanceList"
        v-loading="loading"
        stripe
        style="width: 100%"
        empty-text="当前暂无指导走访记录"
      >
        <el-table-column prop="guidanceDate" label="指导日期" width="120" sortable />
        <el-table-column label="指导形式" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getGuidanceTypeTag(row.guidanceType)">
              {{ formatGuidanceType(row.guidanceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="指导教师" width="120" />
        <el-table-column prop="studentName" label="学生姓名" width="120">
          <template #default="{ row }">
            <strong>{{ row.studentName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="studentNumber" label="学号" width="130" />
        <el-table-column prop="className" label="班级" width="130" show-overflow-tooltip />
        <el-table-column prop="companyName" label="实习企业" min-width="160" show-overflow-tooltip />
        <el-table-column prop="location" label="指导地点" width="140" show-overflow-tooltip />
        <el-table-column prop="contentSummary" label="指导内容纪要" min-width="200" show-overflow-tooltip />
        <el-table-column label="凭证" width="80" align="center">
          <template #default="{ row }">
            <el-link
              v-if="row.attachmentUrl"
              :href="row.attachmentUrl"
              target="_blank"
              type="primary"
              :underline="false"
            >
              查看
            </el-link>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="学生反馈状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="row.feedbackStatus === 'CONFIRMED' ? 'success' : 'warning'" size="small">
              {{ row.feedbackStatus === 'CONFIRMED' ? '已确认反馈' : '待确认反馈' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <!-- 学生确认按钮: 仅本人且未确认时 -->
            <el-button
              v-if="canSubmitFeedback(row)"
              type="warning"
              size="small"
              @click="openFeedbackDialog(row)"
            >
              确认反馈
            </el-button>
            <el-button
              type="info"
              link
              size="small"
              @click="openDetailDrawer(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 登记指导记录弹窗 (API-065) -->
    <el-dialog
      v-model="createDialogVisible"
      title="登记过程指导走访纪要 (API-065)"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
      >
        <el-form-item label="实习任务" prop="taskId">
          <el-select
            v-model="createForm.taskId"
            placeholder="请选择任务"
            style="width: 100%"
            @change="handleCreateTaskChange"
          >
            <el-option
              v-for="task in taskList"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="指导学生" prop="studentId">
          <el-select
            v-model="createForm.studentId"
            placeholder="请选择被指导学生"
            filterable
            style="width: 100%"
            :loading="studentsLoading"
          >
            <el-option
              v-for="stu in taskStudents"
              :key="stu.studentId"
              :label="`${stu.studentName} (${stu.studentNumber}) - ${stu.className || ''}`"
              :value="stu.studentId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="指导时间" prop="guidanceDate">
          <el-date-picker
            v-model="createForm.guidanceDate"
            type="datetime"
            placeholder="选择指导开展时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="指导形式" prop="guidanceType">
          <el-radio-group v-model="createForm.guidanceType">
            <el-radio-button label="ONSITE">现场实地走访</el-radio-button>
            <el-radio-button label="ONLINE">线上连线</el-radio-button>
            <el-radio-button label="PHONE">电话沟通</el-radio-button>
            <el-radio-button label="EMAIL_OTHER">邮件及其他</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="指导地点">
          <el-input
            v-model="createForm.location"
            placeholder="例如：杭州市高新技术软件园B座三层工位 / 腾讯会议号"
          />
        </el-form-item>

        <el-form-item label="指导纪要" prop="contentSummary">
          <el-input
            v-model="createForm.contentSummary"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请详细记录对学生的业务指导、岗位答疑、工作作风考察及沟通纪要..."
          />
        </el-form-item>

        <el-form-item label="跟进事项">
          <el-input
            v-model="createForm.followupActions"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="针对本次指导发现的问题需跟进协调的事项或学生后续改进目标（选填）..."
          />
        </el-form-item>

        <el-form-item label="佐证链接" prop="attachmentUrl">
          <el-input
            v-model="createForm.attachmentUrl"
            placeholder="现场照片/走访签到表等凭证网络链接（支持 jpg, jpeg, png, pdf）"
            clearable
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleConfirmCreate">
          确认登记
        </el-button>
      </template>
    </el-dialog>

    <!-- 学生反馈确认弹窗 (API-067) -->
    <el-dialog
      v-model="feedbackDialogVisible"
      title="学生指导确认与反馈 (API-067)"
      width="520px"
      destroy-on-close
    >
      <div v-if="currentRecordForFeedback" class="feedback-dialog-content">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="指导反馈确认规则"
          description="确认后反馈时间与内容即行固化留存，不可重复提交或撤回。系统将使用 CAS 条件更新校验状态。"
          style="margin-bottom: 14px"
        />

        <el-descriptions :column="1" border size="small" style="margin-bottom: 14px">
          <el-descriptions-item label="指导教师">{{ currentRecordForFeedback.teacherName }}</el-descriptions-item>
          <el-descriptions-item label="指导日期">{{ currentRecordForFeedback.guidanceDate }} ({{ formatGuidanceType(currentRecordForFeedback.guidanceType) }})</el-descriptions-item>
          <el-descriptions-item label="指导纪要">{{ currentRecordForFeedback.contentSummary }}</el-descriptions-item>
        </el-descriptions>

        <el-form ref="feedbackFormRef" :model="feedbackForm" :rules="feedbackRules" label-position="top">
          <el-form-item label="学生反馈确认意见" prop="studentFeedback">
            <el-input
              v-model="feedbackForm.studentFeedback"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请输入您对指导教师指导内容的受指导确认与建议（不少于5字）..."
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="handleConfirmFeedback">
          确认提交反馈 (CAS)
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="过程指导走访纪要明细"
      size="560px"
      destroy-on-close
    >
      <div v-if="selectedDetail" class="detail-content">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="指导日期">{{ selectedDetail.guidanceDate }}</el-descriptions-item>
          <el-descriptions-item label="指导形式">
            <el-tag :type="getGuidanceTypeTag(selectedDetail.guidanceType)">
              {{ formatGuidanceType(selectedDetail.guidanceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="指导教师">{{ selectedDetail.teacherName }}</el-descriptions-item>
          <el-descriptions-item label="被指导学生">{{ selectedDetail.studentName }} ({{ selectedDetail.studentNumber }})</el-descriptions-item>
          <el-descriptions-item label="行政班级">{{ selectedDetail.className || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实习企业">{{ selectedDetail.companyName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指导地点" :span="2">{{ selectedDetail.location || '线上 / 校外' }}</el-descriptions-item>
        </el-descriptions>

        <div class="desc-card">
          <h5 class="desc-title">指导纪要内容</h5>
          <div class="desc-body">{{ selectedDetail.contentSummary }}</div>
        </div>

        <div v-if="selectedDetail.followupActions" class="desc-card">
          <h5 class="desc-title">改进建议与跟进要求</h5>
          <div class="desc-body">{{ selectedDetail.followupActions }}</div>
        </div>

        <div v-if="selectedDetail.attachmentUrl" class="desc-card">
          <h5 class="desc-title">佐证凭证文档</h5>
          <div class="desc-body">
            <a :href="selectedDetail.attachmentUrl" target="_blank" rel="noopener noreferrer">
              {{ selectedDetail.attachmentUrl }}
            </a>
          </div>
        </div>

        <div class="desc-card feedback-block" :class="{ 'confirmed': selectedDetail.feedbackStatus === 'CONFIRMED' }">
          <h5 class="desc-title">
            学生受指导反馈 ({{ selectedDetail.feedbackStatus === 'CONFIRMED' ? '已确认' : '未确认' }})
          </h5>
          <div v-if="selectedDetail.feedbackStatus === 'CONFIRMED'">
            <p class="fb-text">{{ selectedDetail.studentFeedback }}</p>
            <span class="fb-time">确认时间: {{ selectedDetail.feedbackTime || '-' }}</span>
          </div>
          <div v-else class="text-muted">
            学生尚未提交受指导反馈确认
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { Plus, Download, Search, Refresh } from '@element-plus/icons-vue';
import { ElMessage, FormInstance, FormRules } from 'element-plus';
import {
  getTaskList,
  getSafetyStudents,
  getGuidanceList,
  createGuidanceRecord,
  submitGuidanceFeedback,
  exportGuidanceExcel,
  TaskItem,
  StudentSafetyProgress,
  GuidanceRecordItem
} from '@/api';
import { useUserStore } from '@/store/modules/user';

const userStore = useUserStore();

const loading = ref(false);
const exporting = ref(false);
const taskList = ref<TaskItem[]>([]);
const filterTaskId = ref<number | undefined>(undefined);
const filterGuidanceType = ref<string>('');
const dateRange = ref<[string, string] | null>(null);

const guidanceList = ref<GuidanceRecordItem[]>([]);

// 新建指导记录
const createDialogVisible = ref(false);
const createSubmitting = ref(false);
const createFormRef = ref<FormInstance>();
const studentsLoading = ref(false);
const taskStudents = ref<StudentSafetyProgress[]>([]);

const formatCurrentDateTime = () => {
  const now = new Date();
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
};

const createForm = reactive({
  taskId: undefined as number | undefined,
  studentId: undefined as number | undefined,
  guidanceDate: formatCurrentDateTime(),
  guidanceType: 'ONSITE',
  location: '',
  contentSummary: '',
  followupActions: '',
  attachmentUrl: ''
});

// 学生反馈确认
const feedbackDialogVisible = ref(false);
const feedbackSubmitting = ref(false);
const feedbackFormRef = ref<FormInstance>();
const currentRecordForFeedback = ref<GuidanceRecordItem | null>(null);
const feedbackForm = reactive({
  studentFeedback: ''
});

// 详情抽屉
const detailDrawerVisible = ref(false);
const selectedDetail = ref<GuidanceRecordItem | null>(null);

const canCreateGuidance = computed(() => {
  // API-065 前端创建按钮仅对 TEACHER 显示
  return userStore.userType === 'TEACHER';
});

const canExport = computed(() => {
  // 学生不可导出全部台账 (需求明确学生导出返回403)
  return ['TEACHER', 'DEPT_ADMIN', 'SYS_ADMIN'].includes(userStore.userType);
});

const canSubmitFeedback = (row: GuidanceRecordItem) => {
  if (userStore.userType !== 'STUDENT') return false;
  return row.feedbackStatus === 'UNCONFIRMED' && row.studentId === userStore.userId;
};

const validateMinLength5 = (_rule: any, value: string, callback: any) => {
  if (!value || value.trim().length < 5) {
    callback(new Error('反馈意见不少于5个字符'));
  } else {
    callback();
  }
};

const validateAttachmentUrl = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback();
    return;
  }
  const trimmed = value.trim();
  if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) {
    callback(new Error('附件须以 http:// 或 https:// 开头'));
    return;
  }
  const extMatch = trimmed.match(/\.(jpg|jpeg|png|pdf)(\?.*)?$/i);
  if (!extMatch) {
    callback(new Error('凭证格式仅支持 jpg, jpeg, png, pdf'));
    return;
  }
  callback();
};

const createRules = reactive<FormRules>({
  taskId: [{ required: true, message: '请选择实习任务', trigger: 'change' }],
  studentId: [{ required: true, message: '请选择被指导学生', trigger: 'change' }],
  guidanceDate: [{ required: true, message: '请选择指导时间', trigger: 'change' }],
  guidanceType: [{ required: true, message: '请选择指导形式', trigger: 'change' }],
  contentSummary: [
    { required: true, message: '请填写指导纪要', trigger: 'blur' }
  ],
  attachmentUrl: [{ validator: validateAttachmentUrl, trigger: 'blur' }]
});

const feedbackRules = reactive<FormRules>({
  studentFeedback: [
    { required: true, message: '请填写反馈意见', trigger: 'blur' },
    { validator: validateMinLength5, trigger: 'blur' }
  ]
});

const loadTasks = async () => {
  try {
    const res = await getTaskList();
    taskList.value = res.data || [];
    if (taskList.value.length > 0 && !filterTaskId.value) {
      filterTaskId.value = taskList.value[0].id;
      await loadList();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载任务列表失败');
  }
};

const loadList = async () => {
  if (!filterTaskId.value) return;
  loading.value = true;
  try {
    const res = await getGuidanceList({
      taskId: filterTaskId.value,
      guidanceType: filterGuidanceType.value || undefined,
      startDate: dateRange.value ? dateRange.value[0] : undefined,
      endDate: dateRange.value ? dateRange.value[1] : undefined
    });
    guidanceList.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取指导走访台账失败');
  } finally {
    loading.value = false;
  }
};

const handleTaskChange = () => {
  loadList();
};

const handleDateRangeChange = () => {
  loadList();
};

const resetFilter = () => {
  filterGuidanceType.value = '';
  dateRange.value = null;
  loadList();
};

const openCreateDialog = async () => {
  createForm.taskId = filterTaskId.value;
  createForm.studentId = undefined;
  createForm.guidanceDate = formatCurrentDateTime();
  createForm.guidanceType = 'ONSITE';
  createForm.location = '';
  createForm.contentSummary = '';
  createForm.followupActions = '';
  createForm.attachmentUrl = '';

  createDialogVisible.value = true;
  if (createForm.taskId) {
    await fetchTaskStudents(createForm.taskId);
  }
};

const handleCreateTaskChange = (val: number) => {
  createForm.studentId = undefined;
  fetchTaskStudents(val);
};

const fetchTaskStudents = async (taskId: number) => {
  studentsLoading.value = true;
  try {
    const res = await getSafetyStudents({ taskId });
    taskStudents.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '加载指导学生列表失败');
  } finally {
    studentsLoading.value = false;
  }
};

const handleConfirmCreate = async () => {
  if (!createFormRef.value) return;
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return;

    createSubmitting.value = true;
    try {
      await createGuidanceRecord({
        taskId: createForm.taskId!,
        studentId: createForm.studentId!,
        guidanceDate: createForm.guidanceDate,
        guidanceType: createForm.guidanceType,
        location: createForm.location?.trim() || undefined,
        contentSummary: createForm.contentSummary.trim(),
        followupActions: createForm.followupActions?.trim() || undefined,
        attachmentUrl: createForm.attachmentUrl?.trim() || undefined
      });

      ElMessage.success('指导走访纪要登记成功！');
      createDialogVisible.value = false;
      await loadList();
    } catch (err: any) {
      ElMessage.error(err.message || '登记失败');
    } finally {
      createSubmitting.value = false;
    }
  });
};

const openFeedbackDialog = (row: GuidanceRecordItem) => {
  currentRecordForFeedback.value = row;
  feedbackForm.studentFeedback = '';
  feedbackDialogVisible.value = true;
};

const handleConfirmFeedback = async () => {
  if (!feedbackFormRef.value || !currentRecordForFeedback.value) return;
  await feedbackFormRef.value.validate(async (valid) => {
    if (!valid) return;

    feedbackSubmitting.value = true;
    try {
      await submitGuidanceFeedback(currentRecordForFeedback.value!.id, {
        studentFeedback: feedbackForm.studentFeedback.trim()
      });
      ElMessage.success('指导反馈确认成功！');
      feedbackDialogVisible.value = false;
      await loadList();
    } catch (err: any) {
      ElMessage.error(err.message || '反馈确认失败');
    } finally {
      feedbackSubmitting.value = false;
    }
  });
};

const handleExportExcel = async () => {
  if (!filterTaskId.value) {
    ElMessage.warning('请先选择实习任务');
    return;
  }
  exporting.value = true;
  try {
    const res = await exportGuidanceExcel({
      taskId: filterTaskId.value,
      guidanceType: filterGuidanceType.value || undefined,
      startDate: dateRange.value ? dateRange.value[0] : undefined,
      endDate: dateRange.value ? dateRange.value[1] : undefined
    });

    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = `实习指导走访台账_${new Date().toISOString().slice(0, 10)}.xlsx`;
    link.click();
    window.URL.revokeObjectURL(downloadUrl);
    ElMessage.success('指导台账 Excel 导出成功！');
  } catch (err: any) {
    ElMessage.error(err.message || '导出 Excel 失败');
  } finally {
    exporting.value = false;
  }
};

const openDetailDrawer = (row: GuidanceRecordItem) => {
  selectedDetail.value = row;
  detailDrawerVisible.value = true;
};

const formatGuidanceType = (type: string) => {
  switch (type) {
    case 'ONSITE': return '现场实地走访';
    case 'ONLINE': return '线上连线';
    case 'PHONE': return '电话沟通';
    case 'EMAIL_OTHER': return '邮件及其他';
    default: return type;
  }
};

const getGuidanceTypeTag = (type: string) => {
  switch (type) {
    case 'ONSITE': return 'danger';
    case 'ONLINE': return 'primary';
    case 'PHONE': return 'warning';
    case 'EMAIL_OTHER': return 'info';
    default: return 'info';
  }
};

onMounted(() => {
  loadTasks();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.guidance-manage-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card {
  background-color: $card-bg;
  border-radius: $radius-card;
  .header-flex {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 6px 0;
  }
  .page-subtitle {
    font-size: 13px;
    color: $text-secondary;
    margin: 0;
  }
  .header-action {
    display: flex;
    gap: 10px;
  }
}

.filter-card {
  background-color: $card-bg;
  border-radius: $radius-card;
  .filter-form {
    margin-bottom: -18px;
  }
}

.table-card {
  background-color: $card-bg;
  border-radius: $radius-card;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.desc-card {
  background-color: #fafbfc;
  border: 1px solid $border-color-light;
  border-radius: $radius-base;
  padding: 12px 14px;

  .desc-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 6px 0;
  }

  .desc-body {
    font-size: 13px;
    line-height: 1.5;
    color: $text-secondary;
    white-space: pre-wrap;
    a {
      color: $primary-color;
      word-break: break-all;
    }
  }
}

.feedback-block {
  &.confirmed {
    background-color: $success-light-bg;
    border-color: #b7eb8f;
  }

  .fb-text {
    font-size: 13px;
    color: $text-primary;
    line-height: 1.5;
    margin: 0 0 6px 0;
  }

  .fb-time {
    font-size: 11px;
    color: $text-muted;
  }
}
</style>
