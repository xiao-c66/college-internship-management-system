<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header academic-card">
      <div class="header-info">
        <h2>学校管理员工作台 <el-tag size="small" type="danger">真实业务已对接</el-tag></h2>
        <p class="desc">系统最高权限视图：聚焦全校基础大盘、组织架构管理、审计日志与数据安全运维。</p>
      </div>
      <div class="header-extra" v-if="summaryData">
        <el-tag type="danger">教务处·最高权限</el-tag>
        <el-tag type="info">操作员：{{ summaryData.realName || userStore.realName }}</el-tag>
      </div>
    </div>

    <!-- 全校宏观数据大盘 (数据取自真实数据库 7 张基础表) -->
    <div class="admin-stat-grid">
      <div class="admin-stat-card academic-card">
        <div class="num">{{ summaryData?.metrics?.totalUsers ?? 0 }}</div>
        <div class="label">全校在册用户总数</div>
      </div>
      <div class="admin-stat-card academic-card">
        <div class="num">{{ summaryData?.metrics?.totalDepts ?? 0 }}</div>
        <div class="label">二级院系机构数</div>
      </div>
      <div class="admin-stat-card academic-card">
        <div class="num">{{ summaryData?.metrics?.totalMajors ?? 0 }}</div>
        <div class="label">备案开设专业数</div>
      </div>
      <div class="admin-stat-card academic-card">
        <div class="num">{{ summaryData?.metrics?.totalClasses ?? 0 }}</div>
        <div class="label">行政班级总数</div>
      </div>
    </div>

    <!-- 敏感审计与系统健康流水 -->
    <div class="admin-content-grid">
      <div class="log-card academic-card">
        <div class="box-title">安全操作与审计日志监控 (sys_operation_log)</div>
        <div class="log-stat-row">
          <span>当前系统已记录安全审计日志数：</span>
          <el-tag size="large" type="primary">{{ summaryData?.metrics?.operationLogsCount ?? 0 }} 条</el-tag>
        </div>
        <p class="desc">系统严格记录所有登录、注销、敏感配置变更及越权拦截操作，密码与凭证强制脱敏保护。</p>
        <el-alert
          title="阶段4数据库安全规约：演示账号密码已全量采用BCrypt散列存储，物理删除彻底禁用，核心数据支持逻辑删除与Token版本失效。"
          type="success"
          :closable="false"
          show-icon
        />
      </div>

      <div class="sys-card academic-card">
        <div class="box-title">系统参数与环境概览</div>
        <div class="sys-info">
          <p><strong>基础框架：</strong>Spring Boot 3.2.3 / Java 17 LTS</p>
          <p><strong>前端框架：</strong>Vue 3.4 / Vite 5.1 / Element Plus</p>
          <p><strong>持久层架构：</strong>MyBatis-Plus 3.5.5 / MySQL 8.0</p>
          <p><strong>认证与安全：</strong>Spring Security 6 / JJWT 0.12.5</p>
          <p><strong>会话控制：</strong>原子递增 token_version 全端即时失效</p>
        </div>
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

.admin-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.admin-stat-card {
  text-align: center;
  padding: 22px 16px;

  .num {
    font-size: 28px;
    font-weight: 700;
    color: $primary-color;
    margin-bottom: 4px;
  }

  .label {
    font-size: 13px;
    color: $text-secondary;
  }
}

.admin-content-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.box-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 12px;
}

.log-stat-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  margin-bottom: 12px;
}

.desc {
  font-size: 12px;
  color: $text-muted;
  margin-bottom: 16px;
  line-height: 1.6;
}

.sys-info p {
  margin: 8px 0;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.5;
}
</style>
