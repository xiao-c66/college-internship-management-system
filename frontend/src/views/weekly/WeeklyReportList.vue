<template>
  <div class="weekly-list-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">我的实习周报 (API-056 ~ API-058)</h2>
          <p class="page-subtitle">定期填报工作记录、心得体会与下周计划 | 跟踪教师批阅反馈与退回重提全生命周期</p>
        </div>
        <div class="header-action">
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="!selectedTaskId"
            @click="handleCreateReport"
          >
            填报/编辑周报
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 任务选择与状态统计栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="选择实习任务">
          <el-select
            v-model="selectedTaskId"
            placeholder="请选择对应实习任务"
            style="width: 280px"
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
        <el-form-item label="周报状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 150px" @change="loadReports">
            <el-option label="全部" value="" />
            <el-option label="草稿 (DRAFT)" value="DRAFT" />
            <el-option label="待批阅 (SUBMITTED)" value="SUBMITTED" />
            <el-option label="已批阅 (REVIEWED)" value="REVIEWED" />
            <el-option label="已退回需修改 (RETURNED)" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadReports">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 退回重提警示横幅 -->
    <el-alert
      v-if="hasReturnedReports"
      type="warning"
      show-icon
      :closable="false"
      class="notice-alert"
      title="您有周报被指导教师退回，请及时修改并重新提交！"
      description="指导教师已在周报中给出批阅意见或修改要求。请点击对应周次的「修改重提」按钮完成修正并提交。"
    />

    <!-- 数据指标统计卡片 -->
    <el-row :gutter="16" class="stats-row" v-if="selectedTaskId">
      <el-col :span="6">
        <div class="stat-card stat-total">
          <div class="stat-label">已填报周报总数</div>
          <div class="stat-value">{{ reports.length }} <span>份</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-reviewed">
          <div class="stat-label">教师已批阅</div>
          <div class="stat-value text-success">{{ reviewedCount }} <span>份</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-pending">
          <div class="stat-label">等待教师批阅</div>
          <div class="stat-value text-primary">{{ pendingCount }} <span>份</span></div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-returned">
          <div class="stat-label">被退回 / 逾期</div>
          <div class="stat-value text-danger">
            {{ returnedCount }} <span>退回</span> / {{ overdueCount }} <span>逾期</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 周报记录列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="reports"
        v-loading="loading"
        stripe
        style="width: 100%"
        empty-text="当前任务暂无填报记录，请点击右上角「填报/编辑周报」开始填写"
      >
        <el-table-column label="周次" width="100" align="center">
          <template #default="{ row }">
            <el-tag effect="dark" size="small" type="primary">第 {{ row.weekNumber }} 周</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本周周期范围" width="220">
          <template #default="{ row }">
            <span class="date-range">{{ row.startDate }} ~ {{ row.endDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            <span class="deadline-text">{{ row.deadlineTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="周报状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时效判定" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isOverdue === 1" type="danger" effect="plain" size="small">
              逾期 {{ row.overdueDays }} 天
            </el-tag>
            <el-tag v-else-if="row.status !== 'DRAFT'" type="success" effect="plain" size="small">
              按时提交
            </el-tag>
            <span v-else class="text-muted">待提交</span>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="170">
          <template #default="{ row }">
            {{ row.submitTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="批阅成绩" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined" class="score-badge">
              <strong>{{ row.score }}</strong> 分
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前版本" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              type="primary"
              size="small"
              @click="goEdit(row.taskId, row.weekNumber)"
            >
              继续编辑
            </el-button>
            <el-button
              v-else-if="row.status === 'RETURNED'"
              type="danger"
              size="small"
              @click="goEdit(row.taskId, row.weekNumber)"
            >
              修改重提
            </el-button>
            <el-button
              v-else
              type="info"
              link
              size="small"
              @click="openDetail(row.id)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'RETURNED' || row.status === 'REVIEWED'"
              type="primary"
              link
              size="small"
              @click="openDetail(row.id)"
            >
              批注历史
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 选择新建周次弹窗 -->
    <el-dialog v-model="createDialogVisible" title="选择填报周次" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="实习任务">
          <span>{{ currentTask?.taskName }}</span>
        </el-form-item>
        <el-form-item label="选择周次">
          <el-input-number v-model="selectedWeekNumber" :min="1" :max="52" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateReport">进入填报</el-button>
      </template>
    </el-dialog>

    <!-- 周报详细信息与批阅流转抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="`第 ${currentDetail?.weekNumber} 周 实习周报全貌与流转档案`"
      size="680px"
      destroy-on-close
    >
      <div v-if="currentDetail" class="detail-drawer-content" v-loading="detailLoading">
        <div class="drawer-header-meta">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="所属任务">{{ currentDetail.taskName || currentTask?.taskName }}</el-descriptions-item>
            <el-descriptions-item label="周次与周期">
              第 {{ currentDetail.weekNumber }} 周 ({{ currentDetail.startDate }} ~ {{ currentDetail.endDate }})
            </el-descriptions-item>
            <el-descriptions-item label="周报状态">
              <el-tag :type="getStatusTag(currentDetail.status)">
                {{ getStatusLabel(currentDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="截止与提交">
              {{ currentDetail.submitTime ? '提交于 ' + currentDetail.submitTime : '尚未提交' }}
              <el-tag v-if="currentDetail.isOverdue === 1" type="danger" size="small" style="margin-left: 6px">
                逾期 {{ currentDetail.overdueDays }} 天
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="批阅教师" v-if="currentDetail.reviewerName">
              {{ currentDetail.reviewerName }}
            </el-descriptions-item>
            <el-descriptions-item label="最终成绩" v-if="currentDetail.score !== undefined && currentDetail.score !== null">
              <span class="score-highlight">{{ currentDetail.score }} 分</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 教师批阅意见与退回原因 (如果有) -->
        <div v-if="currentDetail.reviewComment || currentDetail.status === 'RETURNED'" class="section-block review-block">
          <h4 class="section-title">
            <span class="block-icon">💬</span>
            指导教师批阅与反馈
          </h4>
          <div class="review-feedback-card" :class="{ 'card-returned': currentDetail.status === 'RETURNED' }">
            <div class="feedback-meta">
              <span>批阅人: <strong>{{ currentDetail.reviewerName || '指导教师' }}</strong></span>
              <span>批阅时间: {{ currentDetail.reviewTime || '-' }}</span>
            </div>
            <div class="feedback-body">
              <div class="comment-line">
                <span class="comment-label">总体批语:</span>
                <p class="comment-content">{{ currentDetail.reviewComment || '暂无详细批语' }}</p>
              </div>
              <div v-if="currentDetail.reviewAnnotations" class="annotation-line">
                <span class="comment-label">逐条批注与改进意见:</span>
                <p class="annotation-content">{{ currentDetail.reviewAnnotations }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 周报四段式正文内容 -->
        <div class="section-block">
          <h4 class="section-title">
            <span class="block-icon">📝</span>
            周报正文结构化内容
          </h4>

          <div class="content-item">
            <div class="content-subtitle">一、本周主要工作内容与岗位任务</div>
            <div class="content-text">{{ currentDetail.workContent || '未填写' }}</div>
          </div>

          <div class="content-item">
            <div class="content-subtitle">二、工作心得体会与阶段收获</div>
            <div class="content-text">{{ currentDetail.workSummary || '未填写' }}</div>
          </div>

          <div class="content-item">
            <div class="content-subtitle">三、遇到的主要问题与解决措施</div>
            <div class="content-text">{{ currentDetail.problemEncountered || '未填写' }}</div>
          </div>

          <div class="content-item">
            <div class="content-subtitle">四、下周工作计划与改进设想</div>
            <div class="content-text">{{ currentDetail.nextWeekPlan || '未填写' }}</div>
          </div>

          <div class="content-item" v-if="currentDetail.attachmentUrl">
            <div class="content-subtitle">五、周报附件与佐证材料</div>
            <div class="content-attachment">
              <el-icon><Document /></el-icon>
              <a :href="currentDetail.attachmentUrl" target="_blank" rel="noopener noreferrer">
                {{ currentDetail.attachmentUrl }}
              </a>
            </div>
          </div>
        </div>

        <!-- 历史修改与退回快照记录 -->
        <div v-if="currentDetail.historyList && currentDetail.historyList.length > 0" class="section-block">
          <h4 class="section-title">
            <span class="block-icon">📜</span>
            版本修改与流转历史 (共 {{ currentDetail.historyList.length }} 条快照)
          </h4>
          <el-timeline class="history-timeline">
            <el-timeline-item
              v-for="hist in currentDetail.historyList"
              :key="hist.id"
              :timestamp="hist.operateTime"
              :type="getHistoryItemType(hist.action)"
            >
              <div class="hist-card">
                <div class="hist-title">
                  <el-tag size="small" :type="getHistoryTagType(hist.action)">{{ hist.action }}</el-tag>
                  <span class="operator">操作人: {{ hist.operatorName }} ({{ hist.operatorRole }})</span>
                  <span class="version-tag">版本 v{{ hist.version }}</span>
                </div>
                <div v-if="hist.returnReason" class="hist-reason">
                  <strong>退回原因:</strong> {{ hist.returnReason }}
                </div>
                <div v-if="hist.reviewComment" class="hist-comment">
                  <strong>批语:</strong> {{ hist.reviewComment }}
                  <span v-if="hist.score !== undefined && hist.score !== null"> (得分: {{ hist.score }} 分)</span>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Search, Refresh, Plus, Document } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import {
  getTaskList,
  getWeeklyReportList,
  getWeeklyReportDetail,
  TaskItem,
  WeeklyReportItem,
  WeeklyReportDetailItem
} from '@/api';

const router = useRouter();

const loading = ref(false);
const taskList = ref<TaskItem[]>([]);
const selectedTaskId = ref<number | undefined>(undefined);
const filterStatus = ref<string>('');
const reports = ref<WeeklyReportItem[]>([]);

// 新建周报弹窗
const createDialogVisible = ref(false);
const selectedWeekNumber = ref<number>(1);

// 详情抽屉
const detailDrawerVisible = ref(false);
const detailLoading = ref(false);
const currentDetail = ref<WeeklyReportDetailItem | null>(null);

const currentTask = computed(() => {
  return taskList.value.find((t) => t.id === selectedTaskId.value);
});

const hasReturnedReports = computed(() => {
  return reports.value.some((r) => r.status === 'RETURNED');
});

const reviewedCount = computed(() => {
  return reports.value.filter((r) => r.status === 'REVIEWED').length;
});

const pendingCount = computed(() => {
  return reports.value.filter((r) => r.status === 'SUBMITTED').length;
});

const returnedCount = computed(() => {
  return reports.value.filter((r) => r.status === 'RETURNED').length;
});

const overdueCount = computed(() => {
  return reports.value.filter((r) => r.isOverdue === 1).length;
});

const loadTasks = async () => {
  try {
    const res = await getTaskList({ status: 'PUBLISHED' });
    if (res.data && res.data.length > 0) {
      taskList.value = res.data;
      if (!selectedTaskId.value) {
        selectedTaskId.value = res.data[0].id;
      }
      await loadReports();
    } else {
      // 尝试获取全部任务
      const allRes = await getTaskList();
      taskList.value = allRes.data || [];
      if (taskList.value.length > 0 && !selectedTaskId.value) {
        selectedTaskId.value = taskList.value[0].id;
      }
      if (selectedTaskId.value) {
        await loadReports();
      }
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载实习任务列表失败');
  }
};

const loadReports = async () => {
  if (!selectedTaskId.value) return;
  loading.value = true;
  try {
    const res = await getWeeklyReportList({
      taskId: selectedTaskId.value,
      status: filterStatus.value || undefined
    });
    reports.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取周报列表失败');
  } finally {
    loading.value = false;
  }
};

const handleTaskChange = () => {
  loadReports();
};

const resetFilter = () => {
  filterStatus.value = '';
  loadReports();
};

const handleCreateReport = () => {
  if (!selectedTaskId.value) {
    ElMessage.warning('请先选择实习任务');
    return;
  }
  // 计算建议的下一周次
  if (reports.value.length > 0) {
    const maxWeek = Math.max(...reports.value.map((r) => r.weekNumber));
    selectedWeekNumber.value = maxWeek + 1;
  } else {
    selectedWeekNumber.value = 1;
  }
  createDialogVisible.value = true;
};

const confirmCreateReport = () => {
  createDialogVisible.value = false;
  goEdit(selectedTaskId.value!, selectedWeekNumber.value);
};

const goEdit = (taskId: number, weekNo: number) => {
  router.push({
    path: `/weekly/edit/${taskId}/${weekNo}`
  });
};

const openDetail = async (id: number) => {
  detailDrawerVisible.value = true;
  detailLoading.value = true;
  currentDetail.value = null;
  try {
    const res = await getWeeklyReportDetail(id);
    currentDetail.value = res.data;
  } catch (err: any) {
    ElMessage.error(err.message || '获取周报详细信息失败');
  } finally {
    detailLoading.value = false;
  }
};

const getStatusTag = (status: string) => {
  switch (status) {
    case 'DRAFT': return 'info';
    case 'SUBMITTED': return 'primary';
    case 'REVIEWED': return 'success';
    case 'RETURNED': return 'danger';
    default: return 'info';
  }
};

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'DRAFT': return '草稿 (DRAFT)';
    case 'SUBMITTED': return '待批阅 (SUBMITTED)';
    case 'REVIEWED': return '已批阅 (REVIEWED)';
    case 'RETURNED': return '已退回 (RETURNED)';
    default: return status;
  }
};

const getHistoryItemType = (action: string) => {
  switch (action) {
    case 'APPROVE': return 'success';
    case 'RETURN': return 'danger';
    case 'SUBMIT': return 'primary';
    default: return 'info';
  }
};

const getHistoryTagType = (action: string) => {
  switch (action) {
    case 'APPROVE': return 'success';
    case 'RETURN': return 'danger';
    case 'SUBMIT': return 'primary';
    default: return 'info';
  }
};

onMounted(() => {
  loadTasks();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.weekly-list-container {
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
}

.filter-card {
  background-color: $card-bg;
  border-radius: $radius-card;
  .filter-form {
    margin-bottom: -18px;
  }
}

.notice-alert {
  border-radius: $radius-card;
}

.stats-row {
  margin-bottom: 0;
}

.stat-card {
  background-color: $card-bg;
  border: 1px solid $border-color-light;
  border-radius: $radius-card;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .stat-label {
    font-size: 12px;
    color: $text-secondary;
  }

  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: $text-primary;
    span {
      font-size: 13px;
      font-weight: normal;
      color: $text-muted;
    }
  }

  .text-success { color: $success-color; }
  .text-primary { color: $primary-color; }
  .text-danger { color: $danger-color; }
}

.table-card {
  background-color: $card-bg;
  border-radius: $radius-card;
}

.date-range {
  font-family: monospace;
  font-size: 12px;
  color: $text-secondary;
}

.deadline-text {
  font-family: monospace;
  font-size: 12px;
  color: $text-secondary;
}

.score-badge {
  color: $primary-active;
  strong {
    font-size: 15px;
  }
}

.detail-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.drawer-header-meta {
  background-color: #fafafa;
  padding: 12px;
  border-radius: $radius-card;
}

.score-highlight {
  font-size: 16px;
  font-weight: bold;
  color: $primary-active;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 6px;
    border-bottom: 1px solid $border-color-light;
    padding-bottom: 8px;

    .block-icon {
      font-size: 16px;
    }
  }
}

.review-feedback-card {
  background-color: $primary-light-bg;
  border-left: 4px solid $primary-color;
  border-radius: 4px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;

  &.card-returned {
    background-color: $danger-light-bg;
    border-left-color: $danger-color;
  }

  .feedback-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: $text-secondary;
  }

  .feedback-body {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .comment-label {
      font-size: 12px;
      font-weight: bold;
      color: $text-primary;
      display: block;
      margin-bottom: 4px;
    }

    .comment-content {
      margin: 0;
      font-size: 13px;
      line-height: 1.6;
      color: $text-primary;
      white-space: pre-wrap;
    }

    .annotation-content {
      margin: 0;
      font-size: 12px;
      line-height: 1.5;
      color: $text-secondary;
      background-color: rgba(255, 255, 255, 0.7);
      padding: 8px;
      border-radius: 4px;
      white-space: pre-wrap;
    }
  }
}

.content-item {
  background-color: #fafbfc;
  border: 1px solid $border-color-light;
  border-radius: $radius-base;
  padding: 12px 14px;

  .content-subtitle {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 8px;
  }

  .content-text {
    font-size: 13px;
    line-height: 1.6;
    color: $text-secondary;
    white-space: pre-wrap;
  }

  .content-attachment {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    a {
      color: $primary-color;
      text-decoration: underline;
      word-break: break-all;
    }
  }
}

.history-timeline {
  padding-left: 6px;
}

.hist-card {
  background-color: #f9f9f9;
  border-radius: 4px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .hist-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    .operator {
      color: $text-secondary;
    }
    .version-tag {
      color: $text-muted;
      margin-left: auto;
    }
  }

  .hist-reason {
    font-size: 12px;
    color: $danger-color;
  }

  .hist-comment {
    font-size: 12px;
    color: $text-secondary;
  }
}
</style>
