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
        <div class="box-title">待院系复核事务池 (点击直接前往审核闭环)</div>
        <div class="audit-items">
          <div class="audit-row clickable-row" @click="handleNavigate('/audit')">
            <span class="type">[申报终审]</span>
            <span class="desc">学生校外实习申报待院系二级终审批复</span>
            <el-tag size="small" :type="pendingApplyCount > 0 ? 'warning' : 'success'">
              {{ pendingApplyCount }} 件
            </el-tag>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
          <div class="audit-row clickable-row" @click="handleNavigate('/warn/tickets')">
            <span class="type">[预警复核]</span>
            <span class="desc">异常预警工单处置审核与院系升级督办</span>
            <el-tag size="small" :type="pendingWarnCount > 0 ? 'danger' : 'success'">
              {{ pendingWarnCount }} 件
            </el-tag>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
          <div class="audit-row clickable-row" @click="handleNavigate('/inspect/rectify')">
            <span class="type">[整改终审]</span>
            <span class="desc">中期督导限期整改通知与学生整改销号终审</span>
            <el-tag size="small" :type="pendingRectifyCount > 0 ? 'warning' : 'success'">
              {{ pendingRectifyCount }} 件
            </el-tag>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="notice-box academic-card">
        <div class="box-title">教学巡查与管理备忘</div>
        <p class="notice-desc">全院学生、教师及班级组织架构数据已联通，实习各阶段业务已受行级权限隔离保护。</p>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          <el-button type="primary" plain size="small" @click="handleNavigate('/task')">管理实习批次任务 →</el-button>
          <el-button type="warning" plain size="small" @click="handleNavigate('/safety/manage')">配置安全教育与题库 →</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight } from '@element-plus/icons-vue';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';
import { listApplies } from '@/api';
import { getWarnTickets } from '@/api/phase7';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const summaryData = ref<any>(null);
const pendingApplyCount = ref(0);
const pendingWarnCount = ref(0);
const pendingRectifyCount = ref(0);

const handleNavigate = (path: string) => {
  if (path) router.push(path);
};

const fetchSummary = async () => {
  loading.value = true;
  try {
    const res: any = await service.get('/dashboard/summary');
    if (res.code === 200 && res.data) {
      summaryData.value = res.data;
    }

    // 动态拉取待终审申报与预警工单
    const [appliesRes, warnRes] = await Promise.allSettled([
      listApplies({ status: 'PENDING_DEPT' }),
      getWarnTickets({ status: 'PENDING_REVIEW' })
    ]);

    if (appliesRes.status === 'fulfilled' && appliesRes.value?.data) {
      pendingApplyCount.value = appliesRes.value.data.length;
    }
    if (warnRes.status === 'fulfilled' && warnRes.value?.data) {
      pendingWarnCount.value = warnRes.value.data.length;
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
    transition: all 0.2s ease;

    &.clickable-row {
      cursor: pointer;
      &:hover {
        background-color: #ecf5ff;
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);

        .arrow-icon {
          color: $primary-color;
          transform: translateX(4px);
        }
      }
    }

    .arrow-icon {
      margin-left: 8px;
      color: #c0c4cc;
      font-size: 14px;
      transition: all 0.2s ease;
    }

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
