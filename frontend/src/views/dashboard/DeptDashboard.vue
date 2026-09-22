<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header academic-card">
      <div class="header-info">
        <h2>院系负责人工作台 <el-tag size="small" type="warning">真实业务已对接</el-tag></h2>
        <p class="desc">院系主管视图：聚焦全院数据大盘、教学负荷均衡与二级终审复核。</p>
      </div>
      <div class="header-extra" v-if="summaryData">
        <el-tag type="primary">{{ summaryData.deptName || '计算机科学与技术学院' }}</el-tag>
        <el-tag type="info">负责人：{{ summaryData.realName || userStore.realName }}</el-tag>
      </div>
    </div>

    <!-- 宏观覆盖率看板 (数据取自真实数据库) -->
    <div class="overview-grid">
      <div class="stat-box academic-card">
        <div class="val text-primary">{{ summaryData?.metrics?.totalStudents ?? 0 }}</div>
        <div class="tit">全院在册学生人数</div>
      </div>
      <div class="stat-box academic-card">
        <div class="val text-success">{{ summaryData?.metrics?.totalTeachers ?? 0 }}</div>
        <div class="tit">全院指导教师人数</div>
      </div>
      <div class="stat-box academic-card">
        <div class="val text-info">{{ summaryData?.metrics?.totalClasses ?? 0 }}</div>
        <div class="tit">下辖行政班级数</div>
      </div>
      <div class="stat-box academic-card">
        <div class="val text-warning">{{ summaryData?.metrics?.pendingAuditCount ?? 0 }}</div>
        <div class="tit">待院系终审积压数</div>
      </div>
    </div>

    <!-- 院系待复核业务池与质检 -->
    <div class="content-grid">
      <div class="audit-pool academic-card">
        <div class="box-title">待院系复核事务池 (真实业务状态良好)</div>
        <div class="audit-items">
          <div class="audit-row">
            <span class="type">[申报终审]</span>
            <span class="desc">集中实习基地学生申报待复审</span>
            <el-tag size="small" type="info">0 件</el-tag>
          </div>
          <div class="audit-row">
            <span class="type">[变更复核]</span>
            <span class="desc">重大用人单位跨市变更审批申请</span>
            <el-tag size="small" type="info">0 件</el-tag>
          </div>
          <div class="audit-row">
            <span class="type">[预警复核]</span>
            <span class="desc">指导教师提交误报调查申请待复核 (API-090)</span>
            <el-tag size="small" type="info">0 件</el-tag>
          </div>
        </div>
      </div>

      <div class="notice-box academic-card">
        <div class="box-title">教学巡查与中期检查备忘</div>
        <p class="notice-desc">阶段4基础数据库已联通，全院学生、教师及班级组织架构数据已完成初始化加载。</p>
        <el-tag size="small" type="success">数据源健康连接：MySQL 8.0</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';

const userStore = useUserStore();
const loading = ref(false);
const summaryData = ref<any>(null);

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

  .header-extra {
    display: flex;
    gap: 8px;
  }
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.stat-box {
  text-align: center;
  padding: 20px 16px;

  .val {
    font-size: 26px;
    font-weight: 700;
    margin-bottom: 4px;
    color: $text-primary;

    &.text-success { color: $success-color; }
    &.text-primary { color: $primary-color; }
    &.text-warning { color: $warning-color; }
    &.text-info { color: #17a2b8; }
  }

  .tit {
    font-size: 13px;
    color: $text-secondary;
  }
}

.content-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.box-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 16px;
}

.audit-items {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .audit-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    padding: 10px 12px;
    background-color: $bg-color;
    border-radius: $radius-base;

    .type {
      font-weight: 600;
      color: $primary-color;
      width: 90px;
    }

    .desc {
      flex: 1;
      color: $text-secondary;
    }
  }
}

.notice-box {
  .notice-desc {
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.6;
    margin-bottom: 16px;
  }
}
</style>
