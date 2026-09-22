<template>
  <div class="diagnostic-page">
    <div class="page-header academic-card">
      <h2>系统基础环境与连通性诊断 <el-tag size="small" type="warning">开发运维专用</el-tag></h2>
      <p class="desc">本页面仅供本地开发环境诊断 Spring Boot 3 后端服务、Vite 前端、接口文档与连通性使用。</p>
    </div>

    <!-- 后端连通性实时探测卡片 -->
    <div class="academic-card health-box">
      <div class="box-header">
        <span class="title">后端 Spring Boot 3 探针状态</span>
        <el-button type="primary" size="small" :loading="checking" @click="probeBackend">
          重新探测服务连通性
        </el-button>
      </div>

      <div v-if="probeResult" class="result-box">
        <el-alert
          :title="`探针回显正常 [HTTP 200 OK]：${probeResult.message}`"
          type="success"
          :closable="false"
          show-icon
        />
        <div class="result-grid">
          <div><strong>服务标识：</strong>{{ probeResult.data?.systemName }}</div>
          <div><strong>骨架版本：</strong>{{ probeResult.data?.version }}</div>
          <div><strong>当前阶段：</strong>{{ probeResult.data?.currentStage }}</div>
          <div><strong>Java 运行时：</strong>{{ probeResult.data?.javaVersion }}</div>
          <div><strong>Spring Boot 版本：</strong>{{ probeResult.data?.springBootVersion }}</div>
          <div><strong>时间戳：</strong>{{ probeResult.data?.timestamp }}</div>
        </div>
      </div>
      <div v-else class="empty-hint">
        <el-tag type="info">尚未执行探针检测或后端未启动</el-tag>
      </div>
    </div>

    <!-- 开发文档与工具链接 -->
    <div class="academic-card tools-box">
      <div class="box-title">开发辅助工具与文档导航</div>
      <div class="tools-list">
        <div class="tool-item">
          <strong>Knife4j 接口文档：</strong>
          <a href="http://localhost:8080/doc.html" target="_blank">http://localhost:8080/doc.html</a>
          <span class="sub">(后端启动后访问，基于 OpenAPI 3)</span>
        </div>
        <div class="tool-item">
          <strong>后端健康探针端点：</strong>
          <a href="http://localhost:8080/api/v1/health" target="_blank">http://localhost:8080/api/v1/health</a>
        </div>
        <div class="tool-item">
          <strong>模拟验证码端点：</strong>
          <a href="http://localhost:8080/api/v1/auth/captcha" target="_blank">http://localhost:8080/api/v1/auth/captcha</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import service from '@/utils/request';

const checking = ref(false);
const probeResult = ref<any>(null);

const probeBackend = async () => {
  checking.value = true;
  try {
    const res: any = await service.get('/health');
    probeResult.value = res;
  } catch (e) {
    console.error('探针探测失败', e);
  } finally {
    checking.value = false;
  }
};

onMounted(() => {
  probeBackend();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.page-header {
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
}

.box-header {
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

.result-box {
  margin-top: 12px;

  .result-grid {
    margin-top: 16px;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 12px;
    font-size: 13px;
    color: $text-secondary;
  }
}

.empty-hint {
  padding: 16px 0;
}

.box-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 12px;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .tool-item {
    font-size: 13px;
    color: $text-secondary;

    .sub {
      color: $text-muted;
      margin-left: 8px;
    }
  }
}
</style>
