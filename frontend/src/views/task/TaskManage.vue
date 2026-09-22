<template>
  <div class="task-manage-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">实习批次任务管理</h2>
          <p class="page-subtitle">制定与管理二级院系各届实习任务批次、成绩权重规范与学生圈定名单 (TASK-001 ~ TASK-011)</p>
        </div>
        <div class="header-actions" v-if="canManage">
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">创建实习任务批次</el-button>
        </div>
      </div>
    </el-card>

    <!-- 筛选过滤栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="任务状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 160px" @change="loadTasks">
            <el-option label="全部" value="" />
            <el-option label="草稿 (DRAFT)" value="DRAFT" />
            <el-option label="已发布 (PUBLISHED)" value="PUBLISHED" />
            <el-option label="进行中 (IN_PROGRESS)" value="IN_PROGRESS" />
            <el-option label="已结束 (ENDED)" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadTasks">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务数据列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tasks" v-loading="loading" stripe style="width: 100%" empty-text="暂无实习批次任务">
        <el-table-column prop="taskCode" label="任务编码" width="160" sortable />
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="deptName" label="所属院系" width="160" />
        <el-table-column label="学年学期" width="140">
          <template #default="{ row }">
            {{ row.academicYear }} 第{{ row.semester }}学期
          </template>
        </el-table-column>
        <el-table-column label="组织模式" width="110">
          <template #default="{ row }">
            <el-tag :type="row.internshipMode === 'CENTRALIZED' ? 'primary' : 'success'" size="small">
              {{ formatMode(row.internshipMode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="起止周期" width="220">
          <template #default="{ row }">
            {{ row.startDate }} ~ {{ row.endDate }}
          </template>
        </el-table-column>
        <el-table-column label="成绩权重合计" width="120">
          <template #default="{ row }">
            <el-tag type="info" size="small">
              {{ (Number(row.weightEnterprise) + Number(row.weightTeacherProcess) + Number(row.weightWeeklyReport) + Number(row.weightStageMaterial) + Number(row.weightSummary)).toFixed(2) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ formatStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row.id)">详情</el-button>
            <el-button link type="primary" size="small" v-if="canManage" @click="viewDetail(row.id)">分配导师</el-button>
            <el-button link type="warning" size="small" v-if="canManage && row.status === 'DRAFT'" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" v-if="canManage && row.status === 'DRAFT'" @click="handlePublish(row)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑实习任务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑实习任务批次' : '创建新实习任务批次'"
      width="780px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="任务编码" prop="taskCode">
              <el-input v-model="form.taskCode" :disabled="isEdit" placeholder="如 TASK2026CS02" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="form.taskName" placeholder="如 2026届软件工程毕业实习" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="学年" prop="academicYear">
              <el-input v-model="form.academicYear" placeholder="如 2025-2026" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学期" prop="semester">
              <el-select v-model="form.semester" placeholder="请选择学期" style="width: 100%">
                <el-option :value="1" label="第一学期" />
                <el-option :value="2" label="第二学期" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实习组织模式" prop="internshipMode">
              <el-select v-model="form.internshipMode" placeholder="请选择组织模式" style="width: 100%">
                <el-option value="DISTRIBUTED" label="分散实习 (自主落实)" />
                <el-option value="CENTRALIZED" label="集中实习 (基地组织)" />
                <el-option value="HYBRID" label="混合模式" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全及格分/重测" prop="safetyPassingScore">
              <el-input-number v-model="form.safetyPassingScore" :min="60" :max="100" placeholder="如 80" style="width: 130px" />
              <span style="margin: 0 8px">分 / 最多</span>
              <el-input-number v-model="form.safetyMaxAttempts" :min="1" :max="10" placeholder="如 3" style="width: 110px" />
              <span style="margin-left: 6px">次</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实习起止日期" prop="dateRange">
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
          <el-col :span="12">
            <el-form-item label="周报提交频次" prop="weeklyFrequency">
              <el-select v-model="form.weeklyFrequency" placeholder="请选择提交频次" style="width: 100%">
                <el-option value="WEEKLY" label="每周提交 (周日截止)" />
                <el-option value="BIWEEKLY" label="双周提交" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">
          <strong>五项成绩评价权重配置 (必须严格等于 100.00% - TASK-009)</strong>
        </el-divider>

        <div class="weight-config-box">
          <el-row :gutter="12">
            <el-col :span="5">
              <el-form-item label="企业评价" label-width="70px">
                <el-input-number v-model="form.weightEnterprise" :precision="2" :step="5" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="教师过程" label-width="70px">
                <el-input-number v-model="form.weightTeacherProcess" :precision="2" :step="5" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="周报综合" label-width="70px">
                <el-input-number v-model="form.weightWeeklyReport" :precision="2" :step="5" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="5">
              <el-form-item label="阶段材料" label-width="70px">
                <el-input-number v-model="form.weightStageMaterial" :precision="2" :step="5" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="实习总结" label-width="70px">
                <el-input-number v-model="form.weightSummary" :precision="2" :step="5" :min="0" :max="100" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <div class="weight-summary-bar" :class="{ valid: isWeightSumValid, invalid: !isWeightSumValid }">
            <span>当前五项权重合计：<strong>{{ calculatedWeightSum.toFixed(2) }}%</strong></span>
            <span v-if="isWeightSumValid" class="valid-tip">✔ 权重比例合规，符合 100.00% 刚性校验</span>
            <span v-else class="invalid-tip">✖ 权重之和必须严格等于 100.00% (差额: {{ (100 - calculatedWeightSum).toFixed(2) }}%)</span>
          </div>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!isWeightSumValid" :loading="submitLoading" @click="handleSaveTask">
            {{ isEdit ? '保存修改' : '立即创建' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, Search, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import { getTaskList, createTask, updateTask, publishTask, TaskItem } from '@/api';

const router = useRouter();
const userStore = useUserStore();

const canManage = computed(() => {
  return userStore.userType === 'DEPT_ADMIN' || userStore.userType === 'SYS_ADMIN';
});

const tasks = ref<TaskItem[]>([]);
const loading = ref(false);
const filterStatus = ref('');

const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const editId = ref<number | null>(null);

const form = reactive({
  taskCode: '',
  taskName: '',
  academicYear: '',
  semester: undefined as number | undefined,
  internshipMode: '',
  dateRange: [] as string[],
  weeklyFrequency: '',
  safetyPassingScore: undefined as number | undefined,
  safetyMaxAttempts: undefined as number | undefined,
  weightEnterprise: undefined as number | undefined,
  weightTeacherProcess: undefined as number | undefined,
  weightWeeklyReport: undefined as number | undefined,
  weightStageMaterial: undefined as number | undefined,
  weightSummary: undefined as number | undefined
});

const rules = {
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  academicYear: [{ required: true, message: '请输入所属学年', trigger: 'blur' }],
  semester: [{ required: true, message: '请选择所属学期', trigger: 'change' }],
  internshipMode: [{ required: true, message: '请选择实习组织模式', trigger: 'change' }],
  dateRange: [{ required: true, message: '请选择实习起止日期', trigger: 'change' }],
  weeklyFrequency: [{ required: true, message: '请选择周报提交频次', trigger: 'change' }],
  safetyPassingScore: [{ required: true, message: '请配置安全考试及格分 (60-100)', trigger: 'blur' }],
  safetyMaxAttempts: [{ required: true, message: '请配置最大允许重测次数 (>=1)', trigger: 'blur' }]
};

const calculatedWeightSum = computed(() => {
  return Number(form.weightEnterprise || 0) +
         Number(form.weightTeacherProcess || 0) +
         Number(form.weightWeeklyReport || 0) +
         Number(form.weightStageMaterial || 0) +
         Number(form.weightSummary || 0);
});

const areWeightsFilled = computed(() => {
  return form.weightEnterprise !== undefined && form.weightEnterprise !== null &&
         form.weightTeacherProcess !== undefined && form.weightTeacherProcess !== null &&
         form.weightWeeklyReport !== undefined && form.weightWeeklyReport !== null &&
         form.weightStageMaterial !== undefined && form.weightStageMaterial !== null &&
         form.weightSummary !== undefined && form.weightSummary !== null;
});

const isWeightSumValid = computed(() => {
  return areWeightsFilled.value && Math.abs(calculatedWeightSum.value - 100) < 0.001;
});

const loadTasks = async () => {
  loading.value = true;
  try {
    const res = await getTaskList({
      status: filterStatus.value || undefined
    });
    tasks.value = res.data || [];
  } catch {
    // 错误在 request.ts 中自动处理
  } finally {
    loading.value = false;
  }
};

const resetFilter = () => {
  filterStatus.value = '';
  loadTasks();
};

const openCreateDialog = () => {
  isEdit.value = false;
  editId.value = null;
  form.taskCode = '';
  form.taskName = '';
  form.academicYear = '';
  form.semester = undefined;
  form.internshipMode = '';
  form.dateRange = [];
  form.weeklyFrequency = '';
  form.safetyPassingScore = undefined;
  form.safetyMaxAttempts = undefined;
  form.weightEnterprise = undefined;
  form.weightTeacherProcess = undefined;
  form.weightWeeklyReport = undefined;
  form.weightStageMaterial = undefined;
  form.weightSummary = undefined;
  dialogVisible.value = true;
};

const openEditDialog = (row: TaskItem) => {
  isEdit.value = true;
  editId.value = row.id;
  form.taskCode = row.taskCode;
  form.taskName = row.taskName;
  form.academicYear = row.academicYear;
  form.semester = row.semester;
  form.internshipMode = row.internshipMode;
  form.dateRange = (row.startDate && row.endDate) ? [row.startDate, row.endDate] : [];
  form.weeklyFrequency = row.weeklyFrequency || '';
  form.safetyPassingScore = row.safetyPassingScore;
  form.safetyMaxAttempts = row.safetyMaxAttempts;
  form.weightEnterprise = row.weightEnterprise;
  form.weightTeacherProcess = row.weightTeacherProcess;
  form.weightWeeklyReport = row.weightWeeklyReport;
  form.weightStageMaterial = row.weightStageMaterial;
  form.weightSummary = row.weightSummary;
  dialogVisible.value = true;
};

const handleSaveTask = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    if (!isWeightSumValid.value) {
      ElMessage.error('五项成绩权重合计必须严格等于 100.00% (TASK-009)');
      return;
    }

    submitLoading.value = true;
    try {
      const payload: any = {
        taskCode: form.taskCode,
        taskName: form.taskName,
        academicYear: form.academicYear,
        semester: form.semester,
        internshipMode: form.internshipMode,
        startDate: form.dateRange[0],
        endDate: form.dateRange[1],
        weightEnterprise: form.weightEnterprise,
        weightTeacherProcess: form.weightTeacherProcess,
        weightWeeklyReport: form.weightWeeklyReport,
        weightStageMaterial: form.weightStageMaterial,
        weightSummary: form.weightSummary,
        weeklyFrequency: form.weeklyFrequency,
        safetyPassingScore: form.safetyPassingScore,
        safetyMaxAttempts: form.safetyMaxAttempts
      };

      if (isEdit.value && editId.value) {
        await updateTask(editId.value, payload);
        ElMessage.success('实习批次任务已更新');
      } else {
        await createTask(payload);
        ElMessage.success('实习批次任务创建成功');
      }
      dialogVisible.value = false;
      loadTasks();
    } catch {
      // 错误自动提示
    } finally {
      submitLoading.value = false;
    }
  });
};

const handlePublish = async (row: TaskItem) => {
  try {
    await ElMessageBox.confirm(
      `确定要正式发布任务【${row.taskName}】吗？发布后系统将自动根据关联班级圈定学生名单并开启安全准入流程。`,
      '发布确认',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        type: 'info'
      }
    );

    await publishTask(row.id);
    ElMessage.success('任务已成功正式发布！');
    loadTasks();
  } catch {
    // 取消
  }
};

const viewDetail = (id: number) => {
  router.push('/task/detail/' + id);
};

const formatMode = (mode: string) => {
  switch (mode) {
    case 'DISTRIBUTED': return '分散实习';
    case 'CENTRALIZED': return '集中实习';
    case 'HYBRID': return '混合实习';
    default: return mode;
  }
};

const formatStatus = (status: string) => {
  switch (status) {
    case 'DRAFT': return '草稿';
    case 'PUBLISHED': return '已发布';
    case 'IN_PROGRESS': return '进行中';
    case 'ENDED': return '已结束';
    case 'ARCHIVED': return '已归档';
    default: return status;
  }
};

const getStatusTag = (status: string) => {
  switch (status) {
    case 'DRAFT': return 'info';
    case 'PUBLISHED': return 'primary';
    case 'IN_PROGRESS': return 'success';
    case 'ENDED': return 'warning';
    case 'ARCHIVED': return '';
    default: return 'info';
  }
};

onMounted(() => {
  loadTasks();
});
</script>

<style scoped lang="scss">
.task-manage-container {
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

  .filter-card {
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .table-card {
    border-radius: 8px;
  }

  .weight-config-box {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 16px 16px 12px 16px;
    margin-top: 10px;

    .weight-summary-bar {
      margin-top: 14px;
      padding: 10px 14px;
      border-radius: 4px;
      display: flex;
      justify-content: space-between;
      font-size: 14px;

      &.valid {
        background-color: #f0f9eb;
        color: #67c23a;
        border: 1px solid #e1f3d8;
      }

      &.invalid {
        background-color: #fef0f0;
        color: #f56c6c;
        border: 1px solid #fde2e2;
      }

      .valid-tip, .invalid-tip {
        font-weight: 500;
      }
    }
  }
}
</style>
