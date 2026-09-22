<template>
  <div class="weekly-monitor-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">实习过程周报监控看板 (API-056 / Monitor)</h2>
          <p class="page-subtitle">院系及指导教师宏观全貌透视 | 实时汇算提交率、审阅率、按时履约率与逾期穿透分析</p>
        </div>
        <div class="header-action">
          <el-button :icon="Refresh" @click="loadData">刷新监控数据</el-button>
        </div>
      </div>
    </el-card>

    <!-- 任务与院系筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="实习任务">
          <el-select
            v-model="selectedTaskId"
            placeholder="请选择实习任务"
            style="width: 280px"
            @change="loadData"
          >
            <el-option
              v-for="task in taskList"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询看板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 关键指标卡片 (Summary KPI) -->
    <div v-loading="summaryLoading" class="kpi-container">
      <el-row :gutter="16">
        <!-- 实习生总数与应交总数 -->
        <el-col :span="6">
          <div class="kpi-card">
            <div class="kpi-top">
              <span class="kpi-title">参与学生与应交报告</span>
              <el-tag size="small" type="info">批次基数</el-tag>
            </div>
            <div class="kpi-nums">
              <div class="num-item">
                <span class="val">{{ summary?.totalStudents || 0 }}</span>
                <span class="lbl">实习学生</span>
              </div>
              <div class="num-divider">/</div>
              <div class="num-item">
                <span class="val">{{ summary?.totalExpectedReports || 0 }}</span>
                <span class="lbl">应收周报</span>
              </div>
            </div>
            <div class="kpi-footer">
              截至本周应交周报累计基准总数
            </div>
          </div>
        </el-col>

        <!-- 提交情况与提交率 -->
        <el-col :span="6">
          <div class="kpi-card">
            <div class="kpi-top">
              <span class="kpi-title">周报实收履约率</span>
              <span class="kpi-rate-text text-primary">{{ summary?.submissionRate || 0 }}%</span>
            </div>
            <div class="kpi-progress">
              <el-progress
                :percentage="summary?.submissionRate || 0"
                :stroke-width="10"
                color="#1890ff"
                :show-text="false"
              />
            </div>
            <div class="kpi-footer">
              已提交: <strong>{{ summary?.totalSubmittedReports || 0 }}</strong> 份 / 待提交草稿: <strong>{{ draftCount }}</strong> 份
            </div>
          </div>
        </el-col>

        <!-- 批阅进度与批阅率 -->
        <el-col :span="6">
          <div class="kpi-card">
            <div class="kpi-top">
              <span class="kpi-title">指导教师审阅进度</span>
              <span class="kpi-rate-text text-success">{{ summary?.reviewRate || 0 }}%</span>
            </div>
            <div class="kpi-progress">
              <el-progress
                :percentage="summary?.reviewRate || 0"
                :stroke-width="10"
                color="#52c41a"
                :show-text="false"
              />
            </div>
            <div class="kpi-footer">
              已批阅: <strong>{{ summary?.totalReviewedReports || 0 }}</strong> 份 / 待批阅: <strong class="text-primary">{{ summary?.totalPendingReports || 0 }}</strong> 份
            </div>
          </div>
        </el-col>

        <!-- 时效履约与逾期分布 -->
        <el-col :span="6">
          <div class="kpi-card">
            <div class="kpi-top">
              <span class="kpi-title">按时交付与逾期</span>
              <span class="kpi-rate-text text-warning">{{ summary?.onTimeRate || 0 }}%</span>
            </div>
            <div class="kpi-progress">
              <el-progress
                :percentage="summary?.onTimeRate || 0"
                :stroke-width="10"
                color="#faad14"
                :show-text="false"
              />
            </div>
            <div class="kpi-footer">
              按时: <strong>{{ summary?.totalOnTimeReports || 0 }}</strong> 份 / 逾期提交: <strong class="text-danger">{{ summary?.totalOverdueReports || 0 }}</strong> 份
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 明细穿透追踪表格 -->
    <el-card shadow="never" class="table-card">
      <div class="table-header-flex">
        <h3 class="table-title">周报填报与审核流转明细穿透清单</h3>
        <div class="table-filters">
          <el-select v-model="filterStatus" placeholder="状态过滤" clearable style="width: 140px" @change="filterLocalReports">
            <el-option label="全部状态" value="" />
            <el-option label="草稿 (DRAFT)" value="DRAFT" />
            <el-option label="待批阅 (SUBMITTED)" value="SUBMITTED" />
            <el-option label="已批阅 (REVIEWED)" value="REVIEWED" />
            <el-option label="已退回 (RETURNED)" value="RETURNED" />
          </el-select>
          <el-select v-model="filterOverdue" placeholder="时效过滤" clearable style="width: 130px" @change="filterLocalReports">
            <el-option label="全部时效" value="" />
            <el-option label="按时提交" value="0" />
            <el-option label="逾期提交" value="1" />
          </el-select>
          <el-input
            v-model="searchKeyword"
            placeholder="学生姓名/学号搜索"
            clearable
            style="width: 170px"
            @input="filterLocalReports"
          />
        </div>
      </div>

      <el-table
        :data="filteredReports"
        v-loading="tableLoading"
        stripe
        style="width: 100%"
        empty-text="暂无周报记录"
      >
        <el-table-column label="周次" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small">第 {{ row.weekNumber }} 周</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="学生姓名" width="120">
          <template #default="{ row }">
            <strong>{{ row.studentName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="studentNumber" label="学号" width="130" />
        <el-table-column prop="className" label="行政班级" width="140" show-overflow-tooltip />
        <el-table-column label="周期起止" width="190">
          <template #default="{ row }">
            <span class="date-mono">{{ row.startDate }} ~ {{ row.endDate }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="165" />
        <el-table-column label="时效判定" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isOverdue === 1" type="danger" size="small">
              逾期 {{ row.overdueDays }} 天
            </el-tag>
            <el-tag v-else-if="row.status !== 'DRAFT'" type="success" size="small">
              按时
            </el-tag>
            <span v-else class="text-muted">待提交</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成绩得分" width="95" align="center">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined" class="score-bold">
              {{ row.score }} 分
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openViewDrawer(row.id)">
              查看全文
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 周报全文抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`第 ${currentDetail?.weekNumber} 周 实习周报明细 - ${currentDetail?.studentName}`"
      size="640px"
      destroy-on-close
    >
      <div v-if="currentDetail" class="detail-drawer-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="学生姓名">{{ currentDetail.studentName }} ({{ currentDetail.studentNumber }})</el-descriptions-item>
          <el-descriptions-item label="所属班级">{{ currentDetail.className }}</el-descriptions-item>
          <el-descriptions-item label="周次与周期">第 {{ currentDetail.weekNumber }} 周 ({{ currentDetail.startDate }} ~ {{ currentDetail.endDate }})</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTag(currentDetail.status)">{{ getStatusLabel(currentDetail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentDetail.submitTime || '尚未提交' }}</el-descriptions-item>
          <el-descriptions-item label="批阅得分">
            {{ currentDetail.score !== null && currentDetail.score !== undefined ? currentDetail.score + ' 分' : '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="currentDetail.reviewComment" class="review-comment-card">
          <div class="c-title">指导教师评语:</div>
          <div class="c-body">{{ currentDetail.reviewComment }}</div>
        </div>

        <div class="content-block">
          <h5>一、本周工作内容</h5>
          <p>{{ currentDetail.workContent || '未填写' }}</p>

          <h5>二、工作心得体会</h5>
          <p>{{ currentDetail.workSummary || '未填写' }}</p>

          <h5>三、遇到的问题与解决对策</h5>
          <p>{{ currentDetail.problemEncountered || '未填写' }}</p>

          <h5>四、下周工作计划</h5>
          <p>{{ currentDetail.nextWeekPlan || '未填写' }}</p>

          <h5 v-if="currentDetail.attachmentUrl">五、附件凭证</h5>
          <p v-if="currentDetail.attachmentUrl">
            <a :href="currentDetail.attachmentUrl" target="_blank" rel="noopener noreferrer">{{ currentDetail.attachmentUrl }}</a>
          </p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Search, Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import {
  getTaskList,
  getWeeklyReportList,
  getWeeklyReportDetail,
  getWeeklyMonitorSummary,
  TaskItem,
  WeeklyReportItem,
  WeeklyReportDetailItem,
  WeeklyMonitorSummary
} from '@/api';

const taskList = ref<TaskItem[]>([]);
const selectedTaskId = ref<number | undefined>(undefined);

const summaryLoading = ref(false);
const summary = ref<WeeklyMonitorSummary | null>(null);

const tableLoading = ref(false);
const allReports = ref<WeeklyReportItem[]>([]);
const filteredReports = ref<WeeklyReportItem[]>([]);

const filterStatus = ref('');
const filterOverdue = ref('');
const searchKeyword = ref('');

// 详情抽屉
const drawerVisible = ref(false);
const currentDetail = ref<WeeklyReportDetailItem | null>(null);

const draftCount = computed(() => {
  return allReports.value.filter((r) => r.status === 'DRAFT').length;
});

const loadTasks = async () => {
  try {
    const res = await getTaskList();
    taskList.value = res.data || [];
    if (taskList.value.length > 0 && !selectedTaskId.value) {
      selectedTaskId.value = taskList.value[0].id;
      await loadData();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载实习任务失败');
  }
};

const loadData = async () => {
  if (!selectedTaskId.value) return;
  await Promise.all([loadSummary(), loadReportList()]);
};

const loadSummary = async () => {
  if (!selectedTaskId.value) return;
  summaryLoading.value = true;
  try {
    const res = await getWeeklyMonitorSummary({ taskId: selectedTaskId.value });
    summary.value = res.data;
  } catch (err: any) {
    ElMessage.error(err.message || '加载周报监控统计汇总失败');
  } finally {
    summaryLoading.value = false;
  }
};

const loadReportList = async () => {
  if (!selectedTaskId.value) return;
  tableLoading.value = true;
  try {
    const res = await getWeeklyReportList({ taskId: selectedTaskId.value });
    allReports.value = res.data || [];
    filterLocalReports();
  } catch (err: any) {
    ElMessage.error(err.message || '加载周报明细列表失败');
  } finally {
    tableLoading.value = false;
  }
};

const filterLocalReports = () => {
  let list = [...allReports.value];

  if (filterStatus.value) {
    list = list.filter((r) => r.status === filterStatus.value);
  }

  if (filterOverdue.value !== '') {
    const isOd = Number(filterOverdue.value);
    list = list.filter((r) => r.isOverdue === isOd);
  }

  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase();
    list = list.filter(
      (r) =>
        (r.studentName && r.studentName.toLowerCase().includes(kw)) ||
        (r.studentNumber && r.studentNumber.toLowerCase().includes(kw))
    );
  }

  filteredReports.value = list;
};

const openViewDrawer = async (id: number) => {
  drawerVisible.value = true;
  currentDetail.value = null;
  try {
    const res = await getWeeklyReportDetail(id);
    currentDetail.value = res.data;
  } catch (err: any) {
    ElMessage.error(err.message || '获取周报详情失败');
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
    case 'DRAFT': return '草稿';
    case 'SUBMITTED': return '待批阅';
    case 'REVIEWED': return '已批阅';
    case 'RETURNED': return '已退回';
    default: return status;
  }
};

onMounted(() => {
  loadTasks();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.weekly-monitor-container {
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

.kpi-container {
  margin-bottom: 0;
}

.kpi-card {
  background-color: $card-bg;
  border: 1px solid $border-color-light;
  border-radius: $radius-card;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 120px;

  .kpi-top {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .kpi-title {
      font-size: 13px;
      font-weight: 600;
      color: $text-secondary;
    }

    .kpi-rate-text {
      font-size: 20px;
      font-weight: 700;
    }
  }

  .kpi-nums {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin: 10px 0;

    .num-item {
      display: flex;
      align-items: baseline;
      gap: 4px;
      .val {
        font-size: 24px;
        font-weight: 700;
        color: $text-primary;
      }
      .lbl {
        font-size: 12px;
        color: $text-muted;
      }
    }

    .num-divider {
      color: $border-color;
      font-size: 18px;
    }
  }

  .kpi-progress {
    margin: 10px 0;
  }

  .kpi-footer {
    font-size: 12px;
    color: $text-muted;
  }

  .text-primary { color: $primary-color; }
  .text-success { color: $success-color; }
  .text-warning { color: $warning-color; }
  .text-danger { color: $danger-color; }
}

.table-card {
  background-color: $card-bg;
  border-radius: $radius-card;
}

.table-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .table-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
  }

  .table-filters {
    display: flex;
    gap: 10px;
  }
}

.date-mono {
  font-family: monospace;
  font-size: 12px;
  color: $text-secondary;
}

.score-bold {
  font-weight: 600;
  color: $primary-active;
}

.detail-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-comment-card {
  background-color: $primary-light-bg;
  border-left: 4px solid $primary-color;
  padding: 10px 14px;
  border-radius: 4px;
  .c-title {
    font-size: 12px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 4px;
  }
  .c-body {
    font-size: 13px;
    color: $text-primary;
    line-height: 1.5;
  }
}

.content-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  h5 {
    margin: 6px 0 2px 0;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.6;
    background-color: #fafafa;
    padding: 8px 12px;
    border-radius: 4px;
    white-space: pre-wrap;
  }
}
</style>
