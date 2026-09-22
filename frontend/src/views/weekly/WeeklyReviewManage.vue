<template>
  <div class="weekly-review-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">周报审阅工作台 (API-059)</h2>
          <p class="page-subtitle">指导教师线上审阅、逐条批注与评定打分 | 严肃退回机制强制留痕版本快照</p>
        </div>
        <div class="header-badge">
          <el-tag type="primary" size="large" effect="plain">
            待批阅周报: <strong>{{ pendingCount }}</strong> 份
          </el-tag>
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
            style="width: 260px"
            @change="loadReports"
          >
            <el-option
              v-for="task in taskList"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审阅状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 160px" @change="loadReports">
            <el-option label="全部" value="" />
            <el-option label="待批阅 (SUBMITTED)" value="SUBMITTED" />
            <el-option label="已批阅 (REVIEWED)" value="REVIEWED" />
            <el-option label="已退回 (RETURNED)" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生搜索">
          <el-input
            v-model="keyword"
            placeholder="输入姓名 / 学号"
            clearable
            style="width: 180px"
            @keyup.enter="loadReports"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadReports">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 待批阅与历史报告表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="filteredReports"
        v-loading="loading"
        stripe
        style="width: 100%"
        empty-text="当前暂无符合条件的周报单据"
      >
        <el-table-column label="周次" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="primary">第 {{ row.weekNumber }} 周</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="学生姓名" width="130">
          <template #default="{ row }">
            <strong>{{ row.studentName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="studentNumber" label="学号" width="130" />
        <el-table-column prop="className" label="所属班级" width="140" show-overflow-tooltip />
        <el-table-column label="本周周期" width="190">
          <template #default="{ row }">
            <span class="date-text">{{ row.startDate }} ~ {{ row.endDate }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="165" />
        <el-table-column label="时效判定" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isOverdue === 1" type="danger" size="small" effect="plain">
              逾期 {{ row.overdueDays }} 天
            </el-tag>
            <el-tag v-else-if="row.status !== 'DRAFT'" type="success" size="small" effect="plain">
              按时提交
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成绩得分" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined" class="score-text">
              <strong>{{ row.score }}</strong> 分
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'SUBMITTED'"
              type="primary"
              size="small"
              @click="openReviewDrawer(row)"
            >
              批阅周报
            </el-button>
            <el-button
              v-else
              type="info"
              link
              size="small"
              @click="openReviewDrawer(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 批阅操作双栏抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`第 ${selectedReport?.weekNumber} 周 实习周报批阅与评定 - ${selectedReport?.studentName}`"
      size="860px"
      destroy-on-close
    >
      <div v-if="selectedReport" class="review-drawer-content" v-loading="drawerLoading">
        <div class="drawer-layout-grid">
          <!-- 左侧：学生周报正文与附件 -->
          <div class="report-content-pane">
            <div class="pane-title-box">
              <h4 class="pane-title">学生提交报告内容 (v{{ selectedReport.version }})</h4>
              <el-tag :type="selectedReport.isOverdue === 1 ? 'danger' : 'success'" size="small">
                {{ selectedReport.isOverdue === 1 ? `逾期 ${selectedReport.overdueDays} 天提交` : '按时提交' }}
              </el-tag>
            </div>

            <div class="content-card">
              <div class="content-header">一、本周主要工作内容与岗位任务</div>
              <div class="content-body">{{ selectedReport.workContent || '未填写' }}</div>
            </div>

            <div class="content-card">
              <div class="content-header">二、工作心得体会与阶段收获</div>
              <div class="content-body">{{ selectedReport.workSummary || '未填写' }}</div>
            </div>

            <div class="content-card">
              <div class="content-header">三、遇到的主要问题与解决措施</div>
              <div class="content-body">{{ selectedReport.problemEncountered || '未填写' }}</div>
            </div>

            <div class="content-card">
              <div class="content-header">四、下周工作计划与改进设想</div>
              <div class="content-body">{{ selectedReport.nextWeekPlan || '未填写' }}</div>
            </div>

            <div class="content-card" v-if="selectedReport.attachmentUrl">
              <div class="content-header">五、附件佐证材料</div>
              <div class="content-body attachment-link">
                <el-icon><Link /></el-icon>
                <a :href="selectedReport.attachmentUrl" target="_blank" rel="noopener noreferrer">
                  {{ selectedReport.attachmentUrl }}
                </a>
              </div>
            </div>

            <!-- 修改流转历史快照 (如果存在) -->
            <div class="history-section" v-if="selectedReport.historyList && selectedReport.historyList.length > 0">
              <h5 class="history-title">历史版本与流转快照 (共 {{ selectedReport.historyList.length }} 条)</h5>
              <el-timeline>
                <el-timeline-item
                  v-for="h in selectedReport.historyList"
                  :key="h.id"
                  :timestamp="h.operateTime"
                  :type="h.action === 'APPROVE' ? 'success' : (h.action === 'RETURN' ? 'danger' : 'primary')"
                >
                  <div class="h-item">
                    <span class="h-act">{{ h.action }}</span> by <strong>{{ h.operatorName }}</strong> ({{ h.operatorRole }})
                    <div v-if="h.returnReason" class="h-reason">退回原因: {{ h.returnReason }}</div>
                    <div v-if="h.reviewComment" class="h-comm">评语: {{ h.reviewComment }}</div>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>

          <!-- 右侧：批阅评分与退回控制台 -->
          <div class="review-action-pane">
            <h4 class="pane-title">指导教师审阅意见与评分</h4>

            <!-- 如果周报已经是 REVIEWED 或 RETURNED，且不是再次批阅 -->
            <div v-if="selectedReport.status === 'REVIEWED'" class="reviewed-info-box">
              <el-result icon="success" title="此周报已批阅完成" :sub-title="`最终得分：${selectedReport.score} 分`">
                <template #extra>
                  <div class="prev-review-detail">
                    <p><strong>批阅教师:</strong> {{ selectedReport.reviewerName }}</p>
                    <p><strong>批阅时间:</strong> {{ selectedReport.reviewTime }}</p>
                    <p><strong>指导批语:</strong> {{ selectedReport.reviewComment }}</p>
                    <p v-if="selectedReport.reviewAnnotations"><strong>改进批注:</strong> {{ selectedReport.reviewAnnotations }}</p>
                  </div>
                </template>
              </el-result>
            </div>

            <div v-else-if="selectedReport.status === 'RETURNED'" class="returned-info-box">
              <el-result icon="warning" title="此周报当前已被退回" sub-title="正在等待学生修改并重新提交">
                <template #extra>
                  <div class="prev-review-detail">
                    <p><strong>退回批语:</strong> {{ selectedReport.reviewComment }}</p>
                    <p v-if="selectedReport.reviewAnnotations"><strong>批注意见:</strong> {{ selectedReport.reviewAnnotations }}</p>
                  </div>
                </template>
              </el-result>
            </div>

            <!-- 待批阅表单 (SUBMITTED 或重新批阅) -->
            <div v-else class="review-form-box">
              <el-form :model="reviewForm" label-position="top">
                <el-form-item label="周报成绩评分 (0 ~ 100 分)" required>
                  <el-input-number
                    v-model="reviewForm.score"
                    :min="0"
                    :max="100"
                    :step="1"
                    style="width: 100%"
                    placeholder="请输入 0-100 的成绩得分"
                  />
                  <span class="field-hint">注意：审核通过时必须给出成绩得分；退回修改时不记录成绩，评语及退回原因由系统按后端配置统一校验。</span>
                </el-form-item>

                <el-form-item label="总体指导批语 / 退回修改原因" required>
                  <el-input
                    v-model="reviewForm.reviewComment"
                    type="textarea"
                    :rows="4"
                    maxlength="500"
                    show-word-limit
                    placeholder="请输入对学生周报的专业指导评语；若执行退回修改，必须在此详细注明具体的退回原因与整改要求..."
                  />
                </el-form-item>

                <el-form-item label="逐条批注与专业改进建议 (选填)">
                  <el-input
                    v-model="reviewForm.reviewAnnotations"
                    type="textarea"
                    :rows="3"
                    maxlength="500"
                    show-word-limit
                    placeholder="可针对工作内容中的具体细节给出逐段批注与改进意见..."
                  />
                </el-form-item>

                <div class="review-btn-group">
                  <el-button
                    type="primary"
                    size="large"
                    :loading="reviewSubmitting"
                    @click="handleApprove"
                  >
                    审核通过并评分 (APPROVE)
                  </el-button>
                  <el-button
                    type="danger"
                    size="large"
                    :loading="reviewSubmitting"
                    @click="handleReturn"
                  >
                    退回学生修改 (RETURN)
                  </el-button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { Search, Refresh, Link } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getTaskList,
  getWeeklyReportList,
  getWeeklyReportDetail,
  reviewWeeklyReport,
  TaskItem,
  WeeklyReportItem,
  WeeklyReportDetailItem
} from '@/api';

const loading = ref(false);
const taskList = ref<TaskItem[]>([]);
const filterTaskId = ref<number | undefined>(undefined);
const filterStatus = ref<string>('SUBMITTED');
const keyword = ref<string>('');

const reports = ref<WeeklyReportItem[]>([]);

// 抽屉相关
const drawerVisible = ref(false);
const drawerLoading = ref(false);
const selectedReport = ref<WeeklyReportDetailItem | null>(null);
const reviewSubmitting = ref(false);

const reviewForm = reactive({
  score: 85,
  reviewComment: '',
  reviewAnnotations: ''
});

const filteredReports = computed(() => {
  if (!keyword.value.trim()) return reports.value;
  const kw = keyword.value.trim().toLowerCase();
  return reports.value.filter(
    (r) =>
      (r.studentName && r.studentName.toLowerCase().includes(kw)) ||
      (r.studentNumber && r.studentNumber.toLowerCase().includes(kw))
  );
});

const pendingCount = computed(() => {
  return reports.value.filter((r) => r.status === 'SUBMITTED').length;
});

const loadTasks = async () => {
  try {
    const res = await getTaskList();
    taskList.value = res.data || [];
    if (taskList.value.length > 0 && !filterTaskId.value) {
      filterTaskId.value = taskList.value[0].id;
      await loadReports();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载实习任务失败');
  }
};

const loadReports = async () => {
  if (!filterTaskId.value) return;
  loading.value = true;
  try {
    const res = await getWeeklyReportList({
      taskId: filterTaskId.value,
      status: filterStatus.value || undefined
    });
    reports.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取待审周报列表失败');
  } finally {
    loading.value = false;
  }
};

const resetFilter = () => {
  filterStatus.value = 'SUBMITTED';
  keyword.value = '';
  loadReports();
};

const openReviewDrawer = async (row: WeeklyReportItem) => {
  drawerVisible.value = true;
  drawerLoading.value = true;
  selectedReport.value = null;

  // 重置表单
  reviewForm.score = 85;
  reviewForm.reviewComment = '';
  reviewForm.reviewAnnotations = '';

  try {
    const res = await getWeeklyReportDetail(row.id);
    selectedReport.value = res.data;
    if (res.data.score !== undefined && res.data.score !== null) {
      reviewForm.score = res.data.score;
    }
    if (res.data.reviewComment) {
      reviewForm.reviewComment = res.data.reviewComment;
    }
    if (res.data.reviewAnnotations) {
      reviewForm.reviewAnnotations = res.data.reviewAnnotations;
    }
  } catch (err: any) {
    ElMessage.error(err.message || '获取周报详情失败');
  } finally {
    drawerLoading.value = false;
  }
};

const handleApprove = async () => {
  if (!selectedReport.value) return;
  if (reviewForm.score === undefined || reviewForm.score === null || reviewForm.score < 0 || reviewForm.score > 100) {
    ElMessage.warning('请给出 0 ~ 100 分之间的合理周报成绩');
    return;
  }
  if (!reviewForm.reviewComment || !reviewForm.reviewComment.trim()) {
    ElMessage.warning('请输入导师指导评语');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认将该周报评定为 ${reviewForm.score} 分并审核通过吗？REVIEWED 为最终状态，通过后不可再次退回。`,
      '审核通过确认',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'success' }
    );

    reviewSubmitting.value = true;
    await reviewWeeklyReport(selectedReport.value.id, {
      action: 'APPROVE',
      score: reviewForm.score,
      reviewComment: reviewForm.reviewComment.trim(),
      reviewAnnotations: reviewForm.reviewAnnotations?.trim() || undefined
    });

    ElMessage.success('周报批阅完成！');
    drawerVisible.value = false;
    await loadReports();
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '周报批阅失败');
    }
  } finally {
    reviewSubmitting.value = false;
  }
};

const handleReturn = async () => {
  if (!selectedReport.value) return;
  if (!reviewForm.reviewComment || !reviewForm.reviewComment.trim()) {
    ElMessage.warning('退回修改时必须在评语栏说明具体的退回原因与整改要求');
    return;
  }

  try {
    await ElMessageBox.confirm(
      '确定将该周报退回给学生修改吗？退回后状态将置为 RETURNED，系统将留存当前版本快照并记录周报退回状态提示与审计日志。',
      '退回修改确认',
      { confirmButtonText: '确认退回', cancelButtonText: '取消', type: 'warning' }
    );

    reviewSubmitting.value = true;
    await reviewWeeklyReport(selectedReport.value.id, {
      action: 'RETURN',
      reviewComment: reviewForm.reviewComment.trim(),
      reviewAnnotations: reviewForm.reviewAnnotations?.trim() || undefined
    });

    ElMessage.success('已退回学生修改！');
    drawerVisible.value = false;
    await loadReports();
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '退回操作失败');
    }
  } finally {
    reviewSubmitting.value = false;
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

onMounted(() => {
  loadTasks();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.weekly-review-container {
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

.table-card {
  background-color: $card-bg;
  border-radius: $radius-card;
}

.date-text {
  font-family: monospace;
  font-size: 12px;
  color: $text-secondary;
}

.score-text {
  color: $primary-active;
  font-size: 14px;
}

.review-drawer-content {
  display: flex;
  flex-direction: column;
}

.drawer-layout-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.report-content-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-right: 1px solid $border-color-light;
  padding-right: 20px;
}

.review-action-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pane-title-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pane-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.content-card {
  background-color: #fafbfc;
  border: 1px solid $border-color-light;
  border-radius: $radius-base;
  padding: 10px 14px;

  .content-header {
    font-size: 12px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6px;
  }

  .content-body {
    font-size: 13px;
    line-height: 1.5;
    color: $text-secondary;
    white-space: pre-wrap;
  }

  .attachment-link {
    display: flex;
    align-items: center;
    gap: 6px;
    a {
      color: $primary-color;
      word-break: break-all;
    }
  }
}

.field-hint {
  font-size: 11px;
  color: $text-muted;
  margin-top: 4px;
  display: block;
}

.review-btn-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
  button {
    width: 100%;
  }
}

.prev-review-detail {
  text-align: left;
  background-color: #fafafa;
  padding: 12px;
  border-radius: 4px;
  p {
    margin: 4px 0;
    font-size: 12px;
    color: $text-secondary;
  }
}

.history-section {
  margin-top: 10px;
  .history-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 8px;
  }
  .h-item {
    font-size: 12px;
    .h-act {
      font-weight: bold;
    }
    .h-reason {
      color: $danger-color;
      margin-top: 2px;
    }
    .h-comm {
      color: $text-secondary;
      margin-top: 2px;
    }
  }
}
</style>
