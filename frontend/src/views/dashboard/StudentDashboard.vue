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
      <el-steps :active="1" finish-status="success" align-center class="custom-steps">
        <el-step title="任务发布" description="已开放申报" />
        <el-step title="安全教育" :description="safetyStatusDesc" />
        <el-step title="导师分配" description="待指派指导教师" />
        <el-step title="实习申报" :description="internshipStatusDesc" />
        <el-step title="周报提交" description="按周撰写批阅" />
        <el-step title="中期检查" description="巡查抽检与整改" />
        <el-step title="材料归卷" description="鉴定表与协议" />
        <el-step title="成绩评定" description="五维加权考核" />
        <el-step title="电子归档" description="9项前置校验锁定" />
      </el-steps>
    </div>

    <!-- 核心业务待办三栏布局 -->
    <div class="card-grid">
      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">今日待办与任务提醒</span>
          <el-badge :value="summaryData?.metrics?.pendingTasksCount || 1" class="badge-item" />
        </div>
        <div class="todo-list">
          <div class="todo-item">
            <el-tag size="small" type="warning">安全前置</el-tag>
            <span class="text">请前往完成《安全教育准入承诺书》签署与在线考试</span>
          </div>
          <div class="todo-item">
            <el-tag size="small" type="info">申报提示</el-tag>
            <span class="text">当前实习申报状态：{{ summaryData?.metrics?.internshipStatus || '待申报' }}</span>
          </div>
        </div>
      </div>

      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">周报撰写与批阅追踪</span>
          <span class="sub">已提交 0 / 应提交 12 周</span>
        </div>
        <el-progress :percentage="0" :stroke-width="12" status="warning" />
        <p class="progress-desc">暂无周报记录，待实习申报审核生效后开启周报流水</p>
      </div>

      <div class="col-card academic-card">
        <div class="card-header-bar">
          <span class="title">学生基本教务学籍信息</span>
        </div>
        <div class="student-info">
          <p><strong>学生姓名：</strong>{{ summaryData?.realName || userStore.realName }}</p>
          <p><strong>所属专业：</strong>{{ summaryData?.metrics?.majorName || '软件工程' }}</p>
          <p><strong>行政班级：</strong>{{ summaryData?.metrics?.className || '软件工程2101班' }}</p>
          <p><strong>预警工单：</strong><el-tag size="small" type="success">暂无预警风险 ({{ summaryData?.metrics?.warningCount || 0 }})</el-tag></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';

const userStore = useUserStore();
const loading = ref(false);
const summaryData = ref<any>(null);

const safetyStatusDesc = computed(() => {
  const status = summaryData.value?.metrics?.safetyEduStatus;
  if (status === 'COMPLETED') return '已完成全部要求';
  if (status === 'PASSED') return '已通过测试';
  if (status === 'STUDYING') return '资料学习中';
  return '未开始 (待准入核验)';
});

const internshipStatusDesc = computed(() => {
  return summaryData.value?.metrics?.internshipStatus || '待申报';
});

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

onMounted(() => {
  fetchSummary();
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
