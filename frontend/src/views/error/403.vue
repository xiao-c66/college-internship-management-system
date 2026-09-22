<template>
  <div class="error-page">
    <div class="error-box academic-card">
      <div class="error-code">403</div>
      <h2 class="error-title">抱歉，您无权访问该资源</h2>
      <p class="error-desc">您当前角色的数据管辖范围不包含所请求的教务数据或管理菜单。</p>
      <div class="actions">
        <router-link to="/dashboard/student">
          <el-button type="primary">返回系统工作台</el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 403 权限拦截缺省页面
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.error-page {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $bg-color;
}

.error-box {
  text-align: center;
  padding: 48px 60px;
  max-width: 480px;

  .error-code {
    font-size: 72px;
    font-weight: 800;
    color: $warning-color;
    line-height: 1;
    margin-bottom: 16px;
  }

  .error-title {
    font-size: 20px;
    color: $text-primary;
    margin: 0 0 10px 0;
  }

  .error-desc {
    font-size: 13px;
    color: $text-secondary;
    margin: 0 0 24px 0;
    line-height: 1.6;
  }
}
</style>
