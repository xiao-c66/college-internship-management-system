<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header academic-card">
      <div class="header-info">
        <h2>指导教师工作台 <el-tag size="small" type="primary">真实业务已对接</el-tag></h2>
        <p class="desc">教师专属视图：聚焦待办审批、周报批阅与负责学生安全及异常预警督办。</p>
      </div>
      <div class="header-extra" v-if="summaryData">
        <el-tag type="info">教师姓名：{{ summaryData.realName || userStore.realName }}</el-tag>
        <el-tag type="primary">{{ summaryData.deptName || '计算机科学与技术学院' }}</el-tag>
      </div>
    </div>

    <!-- 教师核心指标四宫格卡片 (数据取自真实数据库) -->
    <div class="metric-grid">
      <div class="metric-card academic-card">
        <div class="num">{{ summaryData?.metrics?.assignedStudentCount ?? 0 }}</div>
        <div class="label">本院负责学生基数</div>
        <div class="sub">基于系统用户库真实统计</div>
      </div>
      <div class="metric-card academic-card warning">
        <div class="num">{{ summaryData?.metrics?.pendingReviewCount ?? 0 }}</div>
        <div class="label">待初审实习申报</div>
        <div class="sub">暂无积压待审单据</div>
      </div>
      <div class="metric-card academic-card info">
        <div class="num">{{ summaryData?.metrics?.safetyCompletedRate || '0%' }}</div>
        <div class="label">安全教育达标率</div>
        <div class="sub">对应 SAFE-008 进度监控</div>
      </div>
      <div class="metric-card academic-card danger">
        <div class="num">{{ summaryData?.metrics?.activeWarningCount ?? 0 }}</div>
        <div class="label">活跃预警工单数</div>
        <div class="sub">状态良好，暂无失联红线</div>
      </div>
    </div>

    <!-- 负责学生安全教育监控面板 (对应 SAFE-008 & API-125) -->
    <div class="safe-panel academic-card">
      <div class="panel-header">
        <div>
          <span class="title">负责学生安全教育学习与考试监控 (SAFE-008 & API-125)</span>
          <span class="sub-tip" style="margin-left: 12px; font-size: 13px; color: #909399;">
            共 {{ studentList.length }} 名学生 (未达标 {{ incompleteCount }} 人)
          </span>
        </div>
        <div class="header-actions" style="display: flex; align-items: center; flex-wrap: wrap; gap: 8px;">
          <el-select
            v-model="selectedTaskId"
            placeholder="选择实习批次任务"
            size="small"
            style="width: 210px"
            @change="fetchSafetyStudents"
          >
            <el-option
              v-for="t in tasks"
              :key="t.id"
              :label="t.taskName"
              :value="t.id"
            />
          </el-select>
          <el-radio-group v-model="filterStatus" size="small" @change="fetchSafetyStudents">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="INCOMPLETE">未达标</el-radio-button>
            <el-radio-button label="STUDYING">学习中</el-radio-button>
            <el-radio-button label="PENDING_TEST">待测试</el-radio-button>
            <el-radio-button label="COMPLETED">已完成</el-radio-button>
          </el-radio-group>
          <el-button
            type="warning"
            size="small"
            plain
            :loading="remindLoading"
            :disabled="incompleteCount === 0"
            @click="handleRemindAll"
          >
            一键催办未达标学生 (API-123)
          </el-button>
        </div>
      </div>

      <el-table :data="studentList" stripe style="width: 100%; margin-top: 12px" v-loading="studentsLoading">
        <el-table-column prop="studentNumber" label="学号" width="130" />
        <el-table-column prop="studentName" label="姓名" width="110" />
        <el-table-column prop="className" label="班级" width="150" />
        <el-table-column prop="materialProgress" label="规程学习进度" width="130" />
        <el-table-column prop="examScore" label="考试最高分" width="110">
          <template #default="{ row }">
            <span v-if="row.examScore !== null && row.examScore !== undefined" :style="{ color: row.isPassed ? '#67c23a' : '#f56c6c', fontWeight: 'bold' }">
              {{ row.examScore }} 分
            </span>
            <span v-else style="color: #909399;">未参加</span>
          </template>
        </el-table-column>
        <el-table-column prop="isCommitmentSigned" label="安全承诺书" width="110">
          <template #default="{ row }">
            <el-tag :type="row.isCommitmentSigned ? 'success' : 'info'" size="small">
              {{ row.isCommitmentSigned ? '已签署' : '未签署' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="安全教育状态" width="130">
          <template #default="{ row }">
            <el-tag :type="row.statusTag" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.statusCode !== 'COMPLETED'"
              type="warning"
              link
              size="small"
              @click="handleRemindSingle(row)"
            >
              单发催办
            </el-button>
            <el-button
              type="primary"
              link
              size="small"
              @click="showDetail(row)"
            >
              详情凭据
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 凭据详情对话框 -->
    <el-dialog v-model="detailVisible" title="学生安全教育与签署凭据详情" width="550px">
      <div v-if="currentStudent" style="line-height: 1.8;">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="学生姓名">{{ currentStudent.studentName }} ({{ currentStudent.studentNumber }})</el-descriptions-item>
          <el-descriptions-item label="所属班级">{{ currentStudent.className || '未分班' }}</el-descriptions-item>
          <el-descriptions-item label="规程阅读">{{ currentStudent.materialProgress }}</el-descriptions-item>
          <el-descriptions-item label="首次学习时间">{{ currentStudent.studyStartTime || '尚未开始' }}</el-descriptions-item>
          <el-descriptions-item label="规程读完时间">{{ currentStudent.studyCompleteTime || '尚未完成' }}</el-descriptions-item>
          <el-descriptions-item label="考试成绩">{{ currentStudent.examScore !== null ? currentStudent.examScore + ' 分 (' + (currentStudent.isPassed ? '及格' : '不及格') + ')' : '未测试' }}</el-descriptions-item>
          <el-descriptions-item label="考试尝试次数">{{ currentStudent.examAttempts }} 次</el-descriptions-item>
          <el-descriptions-item label="承诺签署时间">{{ currentStudent.signTime || '未签署' }}</el-descriptions-item>
          <el-descriptions-item label="保险凭证地址">{{ currentStudent.insuranceFileUrl || '未上传' }}</el-descriptions-item>
          <el-descriptions-item label="当前主状态">
            <el-tag :type="currentStudent.statusTag">{{ currentStudent.statusText }} ({{ currentStudent.statusCode }})</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';
import {
  getTaskList,
  getSafetyStudents,
  remindSafetyStudents,
  TaskItem,
  StudentSafetyProgress
} from '@/api';

const userStore = useUserStore();
const loading = ref(false);
const studentsLoading = ref(false);
const remindLoading = ref(false);
const summaryData = ref<any>(null);

const tasks = ref<TaskItem[]>([]);
const selectedTaskId = ref<number | undefined>(undefined);
const filterStatus = ref('ALL');
const studentList = ref<StudentSafetyProgress[]>([]);
const detailVisible = ref(false);
const currentStudent = ref<StudentSafetyProgress | null>(null);

const incompleteCount = computed(() => {
  return studentList.value.filter(s => s.statusCode !== 'COMPLETED').length;
});

const loadTasks = async () => {
  try {
    const res = await getTaskList();
    tasks.value = res.data || [];
    if (tasks.value.length > 0) {
      const pub = tasks.value.find(t => t.status === 'PUBLISHED') || tasks.value[0];
      selectedTaskId.value = pub.id;
    }
  } catch (e) {
    console.error('获取实习任务批次失败', e);
  }
};

const fetchSummary = async () => {
  loading.value = true;
  try {
    const res: any = await service.get('/dashboard/summary');
    if (res.code === 200 && res.data) {
      summaryData.value = res.data;
    }
  } catch (e) {
    console.error('获取工作台数据失败', e);
  } finally {
    loading.value = false;
  }
};

const fetchSafetyStudents = async () => {
  if (!selectedTaskId.value) {
    studentList.value = [];
    return;
  }
  studentsLoading.value = true;
  try {
    const res = await getSafetyStudents({
      taskId: selectedTaskId.value,
      status: filterStatus.value
    });
    if (res.code === 200) {
      studentList.value = res.data || [];
    }
  } catch (e) {
    console.error('获取学生安全进度失败', e);
  } finally {
    studentsLoading.value = false;
  }
};

const handleRemindAll = async () => {
  if (!selectedTaskId.value) {
    ElMessage.warning('请先选择实习批次任务');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确定向当前任务全部未达标学生发起催办，并将催办指令记录至系统安全审计日志吗？',
      '一键催办提醒 (SAFE-008)',
      { confirmButtonText: '确认催办并记录审计日志', cancelButtonText: '取消', type: 'warning' }
    );
    remindLoading.value = true;
    const res = await remindSafetyStudents(selectedTaskId.value);
    if (res.code === 200) {
      ElMessage.success(res.data?.noticeMessage || '催办指令已成功记录至系统安全审计日志');
      await fetchSafetyStudents();
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '催办失败');
    }
  } finally {
    remindLoading.value = false;
  }
};

const handleRemindSingle = async (row: StudentSafetyProgress) => {
  if (!selectedTaskId.value) return;
  try {
    const res = await remindSafetyStudents(selectedTaskId.value, row.studentId);
    if (res.code === 200) {
      ElMessage.success(res.data?.noticeMessage || `已向学生 ${row.studentName} 发送催办，催办指令已写入系统安全审计日志`);
      await fetchSafetyStudents();
    }
  } catch (e: any) {
    ElMessage.error(e.message || '催办发送失败');
  }
};

const showDetail = (row: StudentSafetyProgress) => {
  currentStudent.value = row;
  detailVisible.value = true;
};

onMounted(async () => {
  fetchSummary();
  await loadTasks();
  fetchSafetyStudents();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  h2 {
    margin: 0 0 4px 0;
    font-size: 18px;
    color: $text-primary;
  }

  .desc {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;
  }

  .header-extra {
    display: flex;
    gap: 8px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.metric-card {
  text-align: center;
  padding: 20px 16px;

  .num {
    font-size: 28px;
    font-weight: 700;
    color: $primary-color;
    margin-bottom: 4px;
  }

  &.warning .num { color: $warning-color; }
  &.danger .num { color: $danger-color; }
  &.info .num { color: $primary-color; }

  .label {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 4px;
  }

  .sub {
    font-size: 12px;
    color: $text-muted;
  }
}

.safe-panel {
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .title {
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
    }
  }
}
</style>
