<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header academic-card">
      <div class="header-info">
        <h2>学生端工作台 <el-tag size="small" type="success">真实业务已对接</el-tag></h2>
        <p class="desc">学生专属视图：聚焦实习全流程导航、阶段申报与周报提交流水。</p>
      </div>
      <div class="student-meta" v-if="summaryData">
        <el-tag type="info">学号：{{ summaryData.metrics?.userNumber || '未绑定' }}</el-tag>
        <el-tag type="primary">{{ summaryData.deptName || '未分配院系' }}</el-tag>
        <el-tag type="success">{{ summaryData.metrics?.className || '未分配行政班' }}</el-tag>
      </div>
    </div>

    <!-- 实习全生命周期导航步骤条 -->
    <div class="lifecycle-card academic-card">
      <div class="section-title">实习教学全生命周期进度</div>
      <el-steps :active="currentStepIndex" finish-status="success" align-center class="custom-steps">
        <el-step title="任务发布" description="已开放申报" />
        <el-step title="安全教育" :description="safetyStatusDesc" />
        <el-step title="导师分配" :description="teacherStatusDesc" />
        <el-step title="实习申报" :description="internshipStatusDesc" />
        <el-step title="周报提交" :description="weeklyStatusDesc" />
        <el-step title="中期检查" description="巡查抽检与整改" />
        <el-step title="材料归卷" description="总结报告与鉴定表" />
        <el-step title="成绩评定" description="五维加权评定" />
        <el-step title="电子归档" description="前置核验终审锁定" />
      </el-steps>
    </div>

    <!-- 核心业务待办三栏布局 -->
    <div class="card-grid">
      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">今日待办与任务提醒</span>
          <el-badge :value="todoItems.length" class="badge-item" />
        </div>
        <div class="todo-list">
          <div
            v-for="(item, idx) in todoItems"
            :key="idx"
            class="todo-item clickable-todo"
            @click="handleNavigate(item.path)"
          >
            <el-tag size="small" :type="item.tagType">{{ item.type }}</el-tag>
            <span class="text">{{ item.text }}</span>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
          <div v-if="todoItems.length === 0" style="color: #67c23a; font-size: 13px; padding: 8px 0;">
            ✓ 当前暂无待处理紧急待办，实习教学运转正常
          </div>
        </div>
      </div>

      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">周报撰写与批阅追踪</span>
          <span class="sub">已提交 {{ submittedWeeklyCount }} / 计划 {{ expectedWeeklyCount }} 周</span>
        </div>
        <el-progress
          :percentage="weeklyPercentage"
          :stroke-width="12"
          :status="weeklyPercentage >= 100 ? 'success' : 'primary'"
        />
        <p class="progress-desc">{{ weeklyProgressText }}</p>
        <div style="margin-top: 10px; text-align: right;">
          <el-button link type="primary" size="small" @click="handleNavigate('/weekly/my')">前往我的周报流水 →</el-button>
        </div>
      </div>

      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">学生基本教务学籍信息</span>
        </div>
        <div class="student-info">
          <p><strong>学生姓名：</strong>{{ summaryData?.realName || userStore.realName }}</p>
          <p><strong>所属专业：</strong>{{ summaryData?.metrics?.majorName || '暂未绑定专业' }}</p>
          <p><strong>行政班级：</strong>{{ summaryData?.metrics?.className || '暂未分配班级' }}</p>
          <p><strong>过程预警：</strong>
            <el-tag size="small" :type="(summaryData?.metrics?.warningCount || 0) > 0 ? 'danger' : 'success'">
              {{ (summaryData?.metrics?.warningCount || 0) > 0 ? `存在 ${summaryData.metrics.warningCount} 项预警工单` : '暂无预警风险 (0)' }}
            </el-tag>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight } from '@element-plus/icons-vue';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';
import { getWeeklyReportList, getSafetyStatus, getTaskList, WeeklyReportItem } from '@/api';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const summaryData = ref<any>(null);
const weeklyList = ref<WeeklyReportItem[]>([]);
const currentSafetyStatus = ref<any>(null);

const submittedWeeklyCount = computed(() => {
  return weeklyList.value.filter(w => w.status === 'SUBMITTED' || w.status === 'REVIEWED').length;
});

const reviewedWeeklyCount = computed(() => {
  return weeklyList.value.filter(w => w.status === 'REVIEWED').length;
});

const expectedWeeklyCount = ref(12);

const weeklyPercentage = computed(() => {
  if (expectedWeeklyCount.value <= 0) return 0;
  return Math.min(100, Math.round((submittedWeeklyCount.value / expectedWeeklyCount.value) * 100));
});

const weeklyProgressText = computed(() => {
  if (submittedWeeklyCount.value === 0) {
    const applySt = summaryData.value?.metrics?.internshipStatus;
    return applySt === 'APPROVED' ? '实习申报已生效，请开始第一周周报撰写' : '待实习申报审核生效后开启周报流水';
  }
  return `已提交 ${submittedWeeklyCount.value} 周，已批阅通过 ${reviewedWeeklyCount.value} 篇`;
});

const safetyStatusDesc = computed(() => {
  const status = currentSafetyStatus.value?.statusCode || summaryData.value?.metrics?.safetyEduStatus;
  if (status === 'COMPLETED') return '已完成承诺签署';
  if (status === 'PASSED') return '已通过安全测试';
  if (status === 'STUDYING') return '规程学习中';
  return '未开始 (待准入)';
});

const teacherStatusDesc = computed(() => {
  return summaryData.value?.metrics?.assignedTeacher ? `已分配: ${summaryData.value.metrics.assignedTeacher}` : '待指派导师';
});

const internshipStatusDesc = computed(() => {
  const st = summaryData.value?.metrics?.internshipStatus;
  if (st === 'APPROVED') return '已终审通过';
  if (st === 'PENDING_DEPT') return '待院系终审';
  if (st === 'PENDING_TEACHER') return '待教师初审';
  if (st === 'REJECTED') return '审核退回';
  if (st === 'SUBMITTED') return '已提交审批';
  return '待申报';
});

const weeklyStatusDesc = computed(() => {
  if (submittedWeeklyCount.value > 0) {
    return `已提报 ${submittedWeeklyCount.value} 篇`;
  }
  return '按周填报流水';
});

const currentStepIndex = computed(() => {
  const safeSt = currentSafetyStatus.value?.statusCode || summaryData.value?.metrics?.safetyEduStatus;
  if (safeSt !== 'COMPLETED') {
    return 1; // 停在安全教育
  }
  const applySt = summaryData.value?.metrics?.internshipStatus;
  if (!applySt || applySt === 'DRAFT' || applySt === 'REJECTED') {
    return 3; // 实习申报
  }
  if (applySt === 'SUBMITTED' || applySt === 'PENDING_TEACHER' || applySt === 'PENDING_DEPT') {
    return 3; // 实习申报审批中
  }
  if (applySt === 'APPROVED') {
    if (submittedWeeklyCount.value < expectedWeeklyCount.value) {
      return 4; // 周报撰写中
    }
    return 6; // 总结归卷
  }
  return 1;
});

const todoItems = computed(() => {
  const list: Array<{ type: string; tagType: string; text: string; path: string }> = [];
  const safeSt = currentSafetyStatus.value?.statusCode || summaryData.value?.metrics?.safetyEduStatus;
  if (safeSt !== 'COMPLETED') {
    list.push({
      type: '安全准入',
      tagType: 'danger',
      text: '请完成校外实习安全规程学习、客观题测试及安全责任承诺书签署',
      path: '/safety/study'
    });
  }

  const applySt = summaryData.value?.metrics?.internshipStatus;
  if (safeSt === 'COMPLETED' && (!applySt || applySt === 'DRAFT')) {
    list.push({
      type: '实习申报',
      tagType: 'warning',
      text: '安全准入已达标，请前往填报实习用人单位信息并上传三方协议',
      path: '/apply'
    });
  } else if (applySt === 'REJECTED') {
    list.push({
      type: '申报退回',
      tagType: 'danger',
      text: '您的实习申报被审核退回，请核对导师反馈意见并重新提交',
      path: '/apply'
    });
  }

  if (applySt === 'APPROVED') {
    list.push({
      type: '周报撰写',
      tagType: 'primary',
      text: `校外实习进行中，请按周提交实习周报流水（已提交 ${submittedWeeklyCount.value} 篇）`,
      path: '/weekly/my'
    });
  }

  return list;
});

const handleNavigate = (path: string) => {
  if (path) router.push(path);
};

const fetchDashboardData = async () => {
  loading.value = true;
  try {
    const summaryRes: any = await service.get('/dashboard/summary');
    if (summaryRes.code === 200 && summaryRes.data) {
      summaryData.value = summaryRes.data;
    }

    let activeTaskId = summaryData.value?.metrics?.taskId;
    if (!activeTaskId) {
      try {
        const taskRes = await getTaskList({ status: 'PUBLISHED' });
        if (taskRes.data && taskRes.data.length > 0) {
          activeTaskId = taskRes.data[0].id;
        }
      } catch (err) {
        console.warn('获取任务列表失败', err);
      }
    }

    // 动态拉取安全状态与周报列表
    const [weeklyRes, safetyRes] = await Promise.allSettled([
      getWeeklyReportList(activeTaskId ? { taskId: activeTaskId } : undefined),
      activeTaskId ? getSafetyStatus(activeTaskId) : Promise.reject('No active task')
    ]);

    if (weeklyRes.status === 'fulfilled' && weeklyRes.value?.data) {
      weeklyList.value = weeklyRes.value.data;
    }
    if (safetyRes.status === 'fulfilled' && safetyRes.value?.data) {
      currentSafetyStatus.value = safetyRes.value.data;
    }
  } catch (e) {
    console.error('获取工作台数据失败', e);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchDashboardData();
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

  .student-meta {
    display: flex;
    gap: 8px;
  }
}

.lifecycle-card {
  .section-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 16px;
    color: $text-primary;
  }
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.card-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  font-weight: 600;

  .title {
    font-size: 14px;
    color: $text-primary;
  }

  .sub {
    font-size: 12px;
    color: $text-muted;
    font-weight: normal;
  }
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .todo-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: $text-secondary;
    padding: 6px 8px;
    border-radius: 6px;
    transition: all 0.2s ease;

    &.clickable-todo {
      cursor: pointer;
      &:hover {
        background: #f0f7ff;
        color: #409eff;

        .arrow-icon {
          transform: translateX(4px);
          color: #409eff;
        }
      }
    }

    .text {
      flex: 1;
    }

    .arrow-icon {
      font-size: 14px;
      color: #c0c4cc;
      transition: transform 0.2s ease;
    }
  }
}

.progress-desc {
  font-size: 12px;
  color: $text-muted;
  margin-top: 12px;
}

.student-info p {
  margin: 8px 0;
  font-size: 13px;
  color: $text-secondary;
}
</style>
