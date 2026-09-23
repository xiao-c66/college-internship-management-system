<template>
  <div class="server-monitor-container">
    <div class="page-header">
      <div class="header-left">
        <h2>系统监控与安全审计控制台</h2>
        <p class="subtitle">服务器硬件/JVM负载、Caffeine有界缓存命中率、接口P95/P99慢调用度量及在线会话Token管控</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refreshCurrentTab">
          刷新指标
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="monitor-tabs" @tab-change="handleTabChange">
      <!-- 选项卡 1: 服务器与 JVM 监控 (API-116) -->
      <el-tab-pane label="服务器与JVM监控 (API-116)" name="server">
        <div v-loading="loading">
          <el-row :gutter="20" class="metric-row">
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">系统状态</div>
                <div class="metric-value text-success">
                  <el-tag type="success" size="large" effect="dark">{{ serverData?.status || 'UP' }}</el-tag>
                </div>
                <div class="metric-sub">运行时间: {{ formatUptime(serverData?.upTimeSeconds) }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">CPU 核心数与负载</div>
                <div class="metric-value font-mono">{{ serverData?.cpuCores || '-' }} 核</div>
                <div class="metric-sub">
                  CPU 负载率: <strong>{{ serverData?.systemCpuLoad != null ? serverData.systemCpuLoad + '%' : 'N/A' }}</strong>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">JVM 内存占用</div>
                <div class="metric-value font-mono text-primary">
                  {{ serverData?.jvmUsedMemoryMB || 0 }} / {{ serverData?.jvmTotalMemoryMB || 0 }} MB
                </div>
                <el-progress
                  :percentage="calcJvmPercent(serverData)"
                  :status="calcJvmPercent(serverData) > 85 ? 'exception' : 'success'"
                  :stroke-width="8"
                  style="margin-top: 8px"
                />
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">磁盘空间使用</div>
                <div class="metric-value font-mono">
                  {{ serverData?.usedDiskSpaceGB || 0 }} / {{ serverData?.totalDiskSpaceGB || 0 }} GB
                </div>
                <el-progress
                  :percentage="calcDiskPercent(serverData)"
                  :status="calcDiskPercent(serverData) > 90 ? 'exception' : 'warning'"
                  :stroke-width="8"
                  style="margin-top: 8px"
                />
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="detail-card" style="margin-top: 20px">
            <template #header>
              <strong>运行时环境详细参数</strong>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="操作系统名称">{{ serverData?.osName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="系统架构">{{ serverData?.osArch || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Java 运行版本">{{ serverData?.jvmVersion || '-' }}</el-descriptions-item>
              <el-descriptions-item label="JVM 最大可用内存">{{ serverData?.jvmMaxMemoryMB || '-' }} MB</el-descriptions-item>
              <el-descriptions-item label="JVM 空闲内存">{{ serverData?.jvmFreeMemoryMB || '-' }} MB</el-descriptions-item>
              <el-descriptions-item label="磁盘剩余可用空间">{{ serverData?.freeDiskSpaceGB || '-' }} GB</el-descriptions-item>
              <el-descriptions-item label="Java 安装根目录" :span="2">
                <code class="path-code">{{ serverData?.jvmHome || '-' }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="指标采样时间" :span="2">
                {{ formatDateTime(serverData?.timestamp) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 选项卡 2: Caffeine 有界单机缓存 (API-117 & API-118) -->
      <el-tab-pane label="本地缓存监控 (API-117 & API-118)" name="cache">
        <div v-loading="loading">
          <div class="cache-actions" style="margin-bottom: 20px">
            <el-alert
              title="阶段8 遵循有界单机缓存规约：最大对象上限 1000，写入后 30 分钟过期，严禁无界内存驻留。"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 16px"
            />
            <el-button type="danger" :icon="Delete" :loading="clearingCache" @click="handleClearCache">
              一键清空本地 Caffeine 缓存 (API-118)
            </el-button>
          </div>

          <el-row :gutter="20">
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">缓存命中率</div>
                <div class="metric-value text-success font-mono">
                  {{ (cacheData ? (cacheData.hitRate * 100).toFixed(2) : 0) }}%
                </div>
                <div class="metric-sub">命中数: {{ cacheData?.hitCount || 0 }} / 未命中: {{ cacheData?.missCount || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">当前预估对象数</div>
                <div class="metric-value text-primary font-mono">{{ cacheData?.estimatedSize || 0 }} 项</div>
                <div class="metric-sub">最大容量配额: {{ cacheData?.maxSize || 1000 }} 项</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">缓存驱逐总次数</div>
                <div class="metric-value font-mono">{{ cacheData?.evictionCount || 0 }} 次</div>
                <div class="metric-sub">LRU/容量淘汰计数</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">写入后过期时效</div>
                <div class="metric-value font-mono">{{ cacheData?.expireAfterWriteMinutes || 30 }} 分钟</div>
                <div class="metric-sub">自动失效回收</div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- 选项卡 3: 接口耗时与慢SQL度量看板 (API-119) -->
      <el-tab-pane label="接口耗时与慢调用分析 (API-119)" name="slow">
        <div v-loading="loading">
          <el-row :gutter="20" class="metric-row">
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">P95 响应耗时</div>
                <div class="metric-value font-mono" :class="slowData && slowData.p95CostMs > 500 ? 'text-danger' : 'text-primary'">
                  {{ slowData?.p95CostMs || 0 }} ms
                </div>
                <div class="metric-sub">95% 请求低于该值</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">P99 响应耗时</div>
                <div class="metric-value font-mono" :class="slowData && slowData.p99CostMs > 500 ? 'text-danger' : 'text-primary'">
                  {{ slowData?.p99CostMs || 0 }} ms
                </div>
                <div class="metric-sub">99% 请求低于该值</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">平均响应耗时</div>
                <div class="metric-value font-mono">{{ slowData?.avgCostMs || 0 }} ms</div>
                <div class="metric-sub">最小: {{ slowData?.minCostMs || 0 }} / 最大: {{ slowData?.maxCostMs || 0 }}</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">实时吞吐量 QPS</div>
                <div class="metric-value font-mono text-success">{{ slowData?.qps || 0 }} req/s</div>
                <div class="metric-sub">滑动窗口估算</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">环形采样样本数</div>
                <div class="metric-value font-mono">{{ slowData?.sampleCount || 0 }} / {{ slowData?.ringBufferSize || 1000 }}</div>
                <div class="metric-sub">严格有界内存RingBuffer</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover" class="metric-card">
                <div class="metric-title">慢调用判定阈值</div>
                <div class="metric-value font-mono text-warning">{{ slowData?.thresholdMs || 500 }} ms</div>
                <div class="metric-sub">动态配置白名单控制</div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="detail-card" style="margin-top: 20px">
            <template #header>
              <div class="card-header-flex">
                <strong>最近慢调用与慢查询流水 (保留上限 500 条)</strong>
                <el-tag type="danger" size="small">耗时 ≥ {{ slowData?.thresholdMs || 500 }} ms</el-tag>
              </div>
            </template>
            <el-table :data="slowData?.slowCalls || []" stripe border style="width: 100%">
              <el-table-column prop="id" label="#" width="60" align="center" />
              <el-table-column prop="uri" label="请求 URI" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="font-mono">{{ row.uri }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="method" label="方法" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.method }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="costMs" label="耗时" width="110" align="center">
                <template #default="{ row }">
                  <el-tag type="danger" effect="dark" class="font-mono">{{ row.costMs }} ms</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="operatorName" label="操作人员" width="120" align="center" />
              <el-table-column prop="clientIp" label="客户端 IP" width="130" align="center" />
              <el-table-column prop="timestamp" label="发生时间" width="170" align="center">
                <template #default="{ row }">
                  {{ formatDateTime(row.timestamp) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 选项卡 4: 安全审计与在线会话管理 (API-120 ~ API-122) -->
      <el-tab-pane label="安全审计与在线会话 (API-120 ~ API-122)" name="security">
        <el-tabs v-model="securitySubTab" class="sub-tabs">
          <!-- 子选项卡 4.1: 在线会话与Token管理 (API-122) -->
          <el-tab-pane label="在线活跃会话与Token踢出 (API-122)" name="tokens">
            <div v-loading="loading">
              <el-alert
                title="超管强制踢下线机制：原子递增目标用户的 token_version，历史签发的 JWT 令牌在下一次请求时将被 JwtAuthenticationFilter 即时拦截返回 401 Unauthorized。"
                type="warning"
                :closable="false"
                show-icon
                style="margin-bottom: 16px"
              />
              <el-table :data="tokenList" stripe border style="width: 100%">
                <el-table-column prop="userId" label="用户ID" width="80" align="center" />
                <el-table-column prop="username" label="用户名" width="130">
                  <template #default="{ row }">
                    <strong>{{ row.username }}</strong>
                  </template>
                </el-table-column>
                <el-table-column prop="realName" label="真实姓名" width="120" />
                <el-table-column prop="userType" label="用户类型" width="120" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="getUserTypeTag(row.userType)">{{ row.userType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="deptName" label="所属院系" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.deptName || '全校/校级管理' }}
                  </template>
                </el-table-column>
                <el-table-column prop="tokenVersion" label="Token版本" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag type="info" class="font-mono">v{{ row.tokenVersion }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="lastLoginTime" label="最近登录时间" width="170" align="center">
                  <template #default="{ row }">
                    {{ row.lastLoginTime ? formatDateTime(row.lastLoginTime) : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="lastLoginIp" label="登录IP" width="130" align="center">
                  <template #default="{ row }">
                    {{ row.lastLoginIp || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      type="danger"
                      size="small"
                      :disabled="row.userId === 1 || row.userId === currentUserId"
                      @click="handleKickToken(row)"
                    >
                      强制踢下线
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 子选项卡 4.2: 登录安全审计检索 (API-120) -->
          <el-tab-pane label="登录安全审计 (API-120)" name="loginLogs">
            <div v-loading="loading">
              <el-form :inline="true" style="margin-bottom: 12px">
                <el-form-item label="账号检索">
                  <el-input v-model="loginLogFilters.username" placeholder="输入用户名" clearable style="width: 160px" />
                </el-form-item>
                <el-form-item label="登录状态">
                  <el-select v-model="loginLogFilters.status" placeholder="全部" clearable style="width: 120px">
                    <el-option label="成功" :value="1" />
                    <el-option label="失败" :value="0" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :icon="Search" @click="fetchLoginLogs">查询</el-button>
                </el-form-item>
              </el-form>

              <el-table :data="loginLogs" stripe border style="width: 100%">
                <el-table-column prop="id" label="ID" width="70" align="center" />
                <el-table-column prop="operatorName" label="登录账号" width="130" />
                <el-table-column prop="businessType" label="类型" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.businessType === 'LOGIN' ? 'primary' : 'info'">{{ row.businessType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operIp" label="客户端IP" width="130" align="center" />
                <el-table-column prop="status" label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                      {{ row.status === 1 ? '成功' : '失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="errorMsg" label="失败简因" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span :class="row.status === 0 ? 'text-danger' : ''">{{ row.errorMsg || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="operTime" label="操作时间" width="170" align="center">
                  <template #default="{ row }">
                    {{ formatDateTime(row.operTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 子选项卡 4.3: 全盘业务操作审计 (API-121) -->
          <el-tab-pane label="全盘高危操作审计 (API-121)" name="operLogs">
            <div v-loading="loading">
              <el-table :data="operationLogs" stripe border style="width: 100%">
                <el-table-column prop="id" label="ID" width="70" align="center" />
                <el-table-column prop="title" label="模块/操作" width="160" show-overflow-tooltip />
                <el-table-column prop="businessType" label="业务类型" width="120" align="center">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.businessType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operatorName" label="操作人员" width="120" align="center" />
                <el-table-column prop="deptName" label="所属院系" width="130" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.deptName || '系统任务/校级' }}
                  </template>
                </el-table-column>
                <el-table-column prop="operIp" label="操作IP" width="140" align="center" />
                <el-table-column prop="costMs" label="耗时" width="100" align="center">
                  <template #default="{ row }">
                    <span v-if="row.costMs != null" class="font-mono">{{ row.costMs }} ms</span>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                      {{ row.status === 1 ? '成功' : '失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operTime" label="操作时间" width="170" align="center">
                  <template #default="{ row }">
                    {{ formatDateTime(row.operTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Delete, Search } from '@element-plus/icons-vue';
import {
  getServerMetrics,
  getCacheMetrics,
  clearCache,
  getSlowSqlMetrics,
  getLoginLogs,
  getOperationLogs,
  getActiveTokens,
  kickToken,
  ServerMonitorVO,
  CacheMonitorVO,
  SlowSqlMonitorVO,
  SysLoginLogVO,
  SysOperationLogVO,
  SysActiveTokenVO
} from '@/api/monitor';

const activeTab = ref('server');
const securitySubTab = ref('tokens');
const loading = ref(false);
const clearingCache = ref(false);

const serverData = ref<ServerMonitorVO | null>(null);
const cacheData = ref<CacheMonitorVO | null>(null);
const slowData = ref<SlowSqlMonitorVO | null>(null);
const tokenList = ref<SysActiveTokenVO[]>([]);
const loginLogs = ref<SysLoginLogVO[]>([]);
const operationLogs = ref<SysOperationLogVO[]>([]);

const loginLogFilters = ref({
  username: '',
  status: undefined as number | undefined
});

const currentUserId = ref(Number(localStorage.getItem('userId') || '0'));

onMounted(() => {
  fetchServerData();
});

const handleTabChange = (tabName: any) => {
  if (tabName === 'server') fetchServerData();
  else if (tabName === 'cache') fetchCacheData();
  else if (tabName === 'slow') fetchSlowData();
  else if (tabName === 'security') fetchSecurityData();
};

const refreshCurrentTab = () => {
  handleTabChange(activeTab.value);
};

const fetchServerData = async () => {
  loading.value = true;
  try {
    const res: any = await getServerMetrics();
    if (res.code === 200) {
      serverData.value = res.data;
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取服务器监控指标失败');
  } finally {
    loading.value = false;
  }
};

const fetchCacheData = async () => {
  loading.value = true;
  try {
    const res: any = await getCacheMetrics();
    if (res.code === 200) {
      cacheData.value = res.data;
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取Caffeine缓存指标失败');
  } finally {
    loading.value = false;
  }
};

const handleClearCache = async () => {
  try {
    await ElMessageBox.confirm('确认清空本地全部 Caffeine 缓存？此操作将使已缓存的系统配置立即重新加载。', '清空缓存确认', {
      type: 'warning',
      confirmButtonText: '确认清空',
      cancelButtonText: '取消'
    });
    clearingCache.value = true;
    const res: any = await clearCache();
    if (res.code === 200) {
      ElMessage.success('本地 Caffeine 缓存已成功清空！');
      fetchCacheData();
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '清空缓存失败');
    }
  } finally {
    clearingCache.value = false;
  }
};

const fetchSlowData = async () => {
  loading.value = true;
  try {
    const res: any = await getSlowSqlMetrics();
    if (res.code === 200) {
      slowData.value = res.data;
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取慢调用度量看板失败');
  } finally {
    loading.value = false;
  }
};

const fetchSecurityData = async () => {
  fetchTokens();
  fetchLoginLogs();
  fetchOperationLogs();
};

const fetchTokens = async () => {
  loading.value = true;
  try {
    const res: any = await getActiveTokens();
    if (res.code === 200) {
      tokenList.value = res.data || [];
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取在线会话列表失败');
  } finally {
    loading.value = false;
  }
};

const handleKickToken = async (row: SysActiveTokenVO) => {
  try {
    await ElMessageBox.confirm(
      `确定强制踢下线用户 [${row.username} - ${row.realName}]？踢出后其历史签发的所有 JWT 令牌将即时失效返回 401。`,
      '强制踢下线确认',
      {
        type: 'warning',
        confirmButtonText: '确认踢出',
        cancelButtonText: '取消'
      }
    );
    const res: any = await kickToken(row.userId);
    if (res.code === 200) {
      ElMessage.success(`用户 [${row.username}] 会话已被强制踢下线！`);
      fetchTokens();
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '强制踢下线失败');
    }
  }
};

const fetchLoginLogs = async () => {
  try {
    const res: any = await getLoginLogs({
      username: loginLogFilters.value.username || undefined,
      status: loginLogFilters.value.status
    });
    if (res.code === 200) {
      loginLogs.value = res.data || [];
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取登录审计流水失败');
  }
};

const fetchOperationLogs = async () => {
  try {
    const res: any = await getOperationLogs();
    if (res.code === 200) {
      operationLogs.value = res.data || [];
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取操作审计流水失败');
  }
};

const calcJvmPercent = (data: ServerMonitorVO | null) => {
  if (!data || !data.jvmTotalMemoryMB) return 0;
  return Math.min(100, Math.round((data.jvmUsedMemoryMB / data.jvmTotalMemoryMB) * 100));
};

const calcDiskPercent = (data: ServerMonitorVO | null) => {
  if (!data || !data.totalDiskSpaceGB) return 0;
  return Math.min(100, Math.round((data.usedDiskSpaceGB / data.totalDiskSpaceGB) * 100));
};

const formatUptime = (seconds?: number) => {
  if (!seconds) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}小时 ${m}分 ${s}秒`;
};

const formatDateTime = (val?: string) => {
  if (!val) return '-';
  return val.replace('T', ' ').substring(0, 19);
};

const getUserTypeTag = (type: string) => {
  if (type === 'SYS_ADMIN') return 'danger';
  if (type === 'DEPT_ADMIN') return 'warning';
  if (type === 'TEACHER') return 'primary';
  return 'info';
};
</script>

<style scoped>
.server-monitor-container {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0 0 6px 0;
  font-size: 22px;
  color: #303133;
}
.subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.metric-row {
  margin-bottom: 16px;
}
.metric-card {
  text-align: center;
  border-radius: 8px;
}
.metric-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 6px;
}
.metric-sub {
  font-size: 12px;
  color: #909399;
}
.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.text-success {
  color: #67c23a;
}
.text-primary {
  color: #409eff;
}
.text-danger {
  color: #f56c6c;
}
.text-warning {
  color: #e6a23c;
}
.text-muted {
  color: #c0c4cc;
}
.path-code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}
.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
