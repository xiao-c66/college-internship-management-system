<template>
  <div class="warn-ticket-center-container">
    <div class="page-header">
      <div class="header-left">
        <h2>预警工单协同中心</h2>
        <p class="subtitle">过程风险实时感知、红橙黄分级派发、师生申辩闭环与院系升级流转</p>
      </div>
      <div class="header-actions">
        <el-button
          type="warning"
          :icon="Warning"
          :loading="scanning"
          :disabled="scanCooldown > 0"
          @click="handleTriggerScan"
        >
          <span v-if="scanCooldown > 0">{{ scanCooldown }}s 后可重新扫描</span>
          <span v-else>触发全量扫描巡检</span>
        </el-button>
        <el-button type="info" :icon="Document" @click="ruleDrawerVisible = true">
          查看预警规则库
        </el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 概览统计指标卡片 -->
    <el-row :gutter="16" class="stat-cards" v-if="statistics">
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ statistics.totalTickets }}</div>
          <div class="stat-label">累计预警工单</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card active-card">
          <div class="stat-value text-primary">{{ statistics.activeTickets }}</div>
          <div class="stat-label">待处理/处理中</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card yellow-card">
          <div class="stat-value text-warning">{{ statistics.yellowTickets }}</div>
          <div class="stat-label">黄色低风险预警</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card orange-card">
          <div class="stat-value text-orange">{{ statistics.orangeTickets }}</div>
          <div class="stat-label">橙色中风险预警</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card red-card">
          <div class="stat-value text-danger">{{ statistics.redTickets }}</div>
          <div class="stat-label">红色高危预警</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card upgrade-card">
          <div class="stat-value text-purple">{{ statistics.upgradedTickets }}</div>
          <div class="stat-label">升级院系督办</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 过滤筛选器 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="实习任务">
          <el-select v-model="filters.taskId" placeholder="全部任务" clearable style="width: 200px" @change="loadData">
            <el-option
              v-for="task in taskOptions"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预警等级">
          <el-select v-model="filters.warnLevel" placeholder="全部等级" clearable style="width: 140px" @change="loadData">
            <el-option label="黄色预警" value="YELLOW" />
            <el-option label="橙色预警" value="ORANGE" />
            <el-option label="红色预警" value="RED" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px" @change="loadData">
            <el-option label="已触发" value="TRIGGERED" />
            <el-option label="已派单" value="DISPATCHED" />
            <el-option label="跟进处置中" value="PROCESSING" />
            <el-option label="已核实闭环" value="CLOSED" />
            <el-option label="已误报释放" value="FALSE_ALARM_CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工单列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="ticketList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="ticketNo" label="工单编号" width="160" show-overflow-tooltip />
        <el-table-column label="预警等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.warnLevel)" effect="dark">
              {{ formatLevel(row.warnLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warnTitle" label="预警规则 / 标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="studentName" label="受预警学生" width="120">
          <template #default="{ row }">
            <div><strong>{{ row.studentName }}</strong></div>
            <div class="sub-text">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="指导教师" width="110">
          <template #default="{ row }">
            <span>{{ row.teacherName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="所属院系" width="140" show-overflow-tooltip />
        <el-table-column label="当前处理人" width="130">
          <template #default="{ row }">
            <div>{{ row.currentAssigneeName || '系统' }}</div>
            <div class="sub-text">{{ row.currentAssigneeRole === 'DEPT_ADMIN' ? '院系管理员' : '指导教师' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ formatStatus(row.status) }}
            </el-tag>
            <el-tag v-if="row.isUpgraded === 1" type="danger" size="small" style="margin-top: 4px;">
              已升级
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="触发时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleOpenDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="canHandle(row)"
              link
              type="warning"
              size="small"
              @click="handleOpenProcess(row)"
            >
              处置
            </el-button>
            <el-button
              v-if="canUpgrade(row)"
              link
              type="danger"
              size="small"
              @click="handleOpenUpgrade(row)"
            >
              升级院系
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 工单详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="预警工单全生命周期详情" size="600px">
      <div v-if="currentTicket" class="drawer-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工单编号" :span="2">
            <strong>{{ currentTicket.ticketNo }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="预警等级">
            <el-tag :type="getLevelTagType(currentTicket.warnLevel)" effect="dark">
              {{ formatLevel(currentTicket.warnLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工单状态">
            <el-tag :type="getStatusTagType(currentTicket.status)">
              {{ formatStatus(currentTicket.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="学生信息">
            {{ currentTicket.studentName }} ({{ currentTicket.studentNo }})
          </el-descriptions-item>
          <el-descriptions-item label="指导教师">
            {{ currentTicket.teacherName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="所属院系" :span="2">
            {{ currentTicket.deptName }}
          </el-descriptions-item>
          <el-descriptions-item label="预警规则" :span="2">
            {{ currentTicket.ruleName }} (规则编码: {{ currentTicket.ruleCode }})
          </el-descriptions-item>
          <el-descriptions-item label="触发时间" :span="2">
            {{ currentTicket.createdAt }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">证据快照数据</h4>
        <div class="code-box">
          <pre>{{ formatSnapshot(currentTicket.evidenceSnapshotJson) }}</pre>
        </div>

        <h4 class="section-title">学生申辩与反馈</h4>
        <div class="feedback-box">
          <div v-if="currentTicket.studentFeedback">
            <p class="feedback-text">{{ currentTicket.studentFeedback }}</p>
            <div class="sub-text">反馈时间: {{ currentTicket.studentFeedbackTime }}</div>
          </div>
          <el-empty v-else description="学生暂未提交申辩反馈" :image-size="60" />
        </div>

        <h4 class="section-title" v-if="currentTicket.teacherInvestigation || currentTicket.handlingMeasures">处置结论</h4>
        <div v-if="currentTicket.teacherInvestigation || currentTicket.handlingMeasures" class="handle-box">
          <div v-if="currentTicket.teacherInvestigation">
            <strong>调查核实情况：</strong>
            <p>{{ currentTicket.teacherInvestigation }}</p>
          </div>
          <div v-if="currentTicket.handlingMeasures" style="margin-top: 8px;">
            <strong>跟进处置措施：</strong>
            <p>{{ currentTicket.handlingMeasures }}</p>
          </div>
          <div class="sub-text" v-if="currentTicket.closedTime" style="margin-top: 8px;">
            闭环时间: {{ currentTicket.closedTime }}
          </div>
        </div>

        <div v-if="currentTicket.isUpgraded === 1" class="upgrade-info-box">
          <el-alert
            title="本工单已升级至院系负责人协同督办"
            type="error"
            :description="'升级时间: ' + (currentTicket.upgradedTime || '-') + ' | 原因: ' + (currentTicket.upgradeReason || '超时未处置自动升级')"
            show-icon
            :closable="false"
          />
        </div>
      </div>
    </el-drawer>

    <!-- 处置工单弹窗 -->
    <el-dialog v-model="processDialogVisible" title="预警工单处置与闭环" width="560px">
      <el-form :model="processForm" label-width="120px" :rules="processRules" ref="processFormRef">
        <el-form-item label="处置动作" prop="action">
          <el-radio-group v-model="processForm.action">
            <el-radio label="PROCESSING">继续跟进处理</el-radio>
            <el-radio label="CLOSED">核实无误并闭环</el-radio>
            <el-radio label="FALSE_ALARM_CLOSED">误报释放闭环</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调查核实情况" prop="teacherInvestigation">
          <el-input
            v-model="processForm.teacherInvestigation"
            type="textarea"
            :rows="3"
            placeholder="请详细说明对学生的调查核实情况（如：电话核实、企业走访核查等）"
          />
        </el-form-item>
        <el-form-item label="处置防范措施" prop="handlingMeasures">
          <el-input
            v-model="processForm.handlingMeasures"
            type="textarea"
            :rows="3"
            placeholder="请填写后续跟进或防范措施（如已督促补交周报、调整岗位等）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="processSubmitting" @click="submitProcess">
          确认提交处置
        </el-button>
      </template>
    </el-dialog>

    <!-- 升级院系弹窗 -->
    <el-dialog v-model="upgradeDialogVisible" title="升级工单至院系负责人" width="500px">
      <el-alert
        title="升级后，工单将指派至院系负责人督办，院系负责人可直接进行干预处置。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px;"
      />
      <el-form :model="upgradeForm" label-width="100px" :rules="upgradeRules" ref="upgradeFormRef">
        <el-form-item label="升级原因" prop="upgradeReason">
          <el-input
            v-model="upgradeForm.upgradeReason"
            type="textarea"
            :rows="4"
            placeholder="请详细说明申请升级至院系处置的原因（如多次失联、企业存在劳动纠纷等）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="upgradeDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="upgradeSubmitting" @click="submitUpgrade">
          确认升级
        </el-button>
      </template>
    </el-dialog>

    <!-- 预警规则库抽屉 -->
    <el-drawer v-model="ruleDrawerVisible" title="内置预警规则库及阈值配置" size="680px">
      <el-table :data="ruleList" stripe border>
        <el-table-column prop="ruleCode" label="规则编码" width="140" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column label="等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.warnLevel)" effect="dark">
              {{ formatLevel(row.warnLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dispatchedRole" label="初派角色" width="110">
          <template #default="{ row }">
            {{ row.dispatchedRole === 'DEPT_ADMIN' ? '院系' : '指导教师' }}
          </template>
        </el-table-column>
        <el-table-column prop="handlingTimeoutDays" label="处置时限" width="90" align="center">
          <template #default="{ row }">
            {{ row.handlingTimeoutDays }}天
          </template>
        </el-table-column>
        <el-table-column label="阈值配置" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="sub-text">{{ row.thresholdParamsJson }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage, FormInstance } from 'element-plus';
import { Warning, Document, Refresh, Search } from '@element-plus/icons-vue';
import {
  getWarnTickets,
  getWarnRules,
  triggerScan,
  handleWarnTicket,
  upgradeWarnTicket,
  WarnTicketVO,
  WarnStatisticsVO,
  WarnRuleVO
} from '@/api/phase7';
import request from '@/utils/request';

// 用户身份
const currentUserId = Number(localStorage.getItem('userId') || '0');
const userType = localStorage.getItem('userType') || '';
const isTeacher = computed(() => userType === 'TEACHER');
const isDeptAdmin = computed(() => userType === 'DEPT_ADMIN');
const isAdmin = computed(() => userType === 'SYS_ADMIN');

// 页面数据
const loading = ref(false);
const ticketList = ref<WarnTicketVO[]>([]);
const statistics = computed<WarnStatisticsVO>(() => {
  const list = ticketList.value || [];
  const closed = list.filter(t => t.status === 'CLOSED').length;
  return {
    taskId: filters.taskId || 0,
    totalTickets: list.length,
    activeTickets: list.length - closed,
    closedTickets: closed,
    yellowTickets: list.filter(t => t.warnLevel === 'YELLOW' && t.status !== 'CLOSED').length,
    orangeTickets: list.filter(t => t.warnLevel === 'ORANGE' && t.status !== 'CLOSED').length,
    redTickets: list.filter(t => t.warnLevel === 'RED' && t.status !== 'CLOSED').length,
    upgradedTickets: list.filter(t => t.isUpgraded === 1 && t.status !== 'CLOSED').length
  };
});
const ruleList = ref<WarnRuleVO[]>([]);
const taskOptions = ref<{ id: number; taskName: string }[]>([]);

// 筛选条件
const filters = reactive({
  taskId: undefined as number | undefined,
  warnLevel: undefined as string | undefined,
  status: undefined as string | undefined
});

// 全量扫描巡检与冷却控制
const scanning = ref(false);
const scanCooldown = ref(0);
let cooldownTimer: any = null;

// 详情抽屉
const detailDrawerVisible = ref(false);
const currentTicket = ref<WarnTicketVO | null>(null);

// 规则库抽屉
const ruleDrawerVisible = ref(false);

// 处置弹窗
const processDialogVisible = ref(false);
const processSubmitting = ref(false);
const processFormRef = ref<FormInstance>();
const processForm = reactive({
  ticketId: 0,
  action: 'PROCESSING' as 'PROCESSING' | 'CLOSED' | 'FALSE_ALARM_CLOSED',
  teacherInvestigation: '',
  handlingMeasures: ''
});
const processRules = {
  action: [{ required: true, message: '请选择处置动作', trigger: 'change' }],
  teacherInvestigation: [{ required: true, message: '请填写调查核实情况', trigger: 'blur' }]
};

// 升级弹窗
const upgradeDialogVisible = ref(false);
const upgradeSubmitting = ref(false);
const upgradeFormRef = ref<FormInstance>();
const upgradeForm = reactive({
  ticketId: 0,
  upgradeReason: ''
});
const upgradeRules = {
  upgradeReason: [{ required: true, message: '请填写升级原因', trigger: 'blur' }]
};

onMounted(() => {
  loadTasks();
  loadData();
  loadRules();
});

const loadTasks = async () => {
  try {
    const res: any = await request.get('/tasks', { params: { size: 100 } });
    if (res.code === 200 && res.data) {
      taskOptions.value = (res.data.records || res.data).map((t: any) => ({
        id: t.id,
        taskName: t.taskName
      }));
    }
  } catch (e) {
    console.error('加载任务列表失败', e);
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const ticketsRes = await getWarnTickets(filters);
    if (ticketsRes.code === 200) {
      ticketList.value = ticketsRes.data || [];
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载预警工单数据失败');
  } finally {
    loading.value = false;
  }
};

const loadRules = async () => {
  try {
    const res = await getWarnRules();
    if (res.code === 200) {
      ruleList.value = res.data || [];
    }
  } catch (e) {
    console.error('加载预警规则失败', e);
  }
};

const resetFilters = () => {
  filters.taskId = undefined;
  filters.warnLevel = undefined;
  filters.status = undefined;
  loadData();
};

const handleTriggerScan = async () => {
  if (scanCooldown.value > 0) return;
  scanning.value = true;
  try {
    const res = await triggerScan(filters.taskId);
    if (res.code === 200) {
      ElMessage.success(`预警扫描巡检完成，新增触发 ${res.data} 张预警工单`);
      startCooldown(10);
      loadData();
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '触发扫描失败');
  } finally {
    scanning.value = false;
  }
};

const startCooldown = (seconds: number) => {
  scanCooldown.value = seconds;
  if (cooldownTimer) clearInterval(cooldownTimer);
  cooldownTimer = setInterval(() => {
    scanCooldown.value -= 1;
    if (scanCooldown.value <= 0) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  }, 1000);
};

const handleOpenDetail = (row: WarnTicketVO) => {
  currentTicket.value = row;
  detailDrawerVisible.value = true;
};

const canHandle = (row: WarnTicketVO) => {
  if (row.status === 'CLOSED' || row.status === 'FALSE_ALARM_CLOSED') return false;
  if (isAdmin.value) return true;
  if (isDeptAdmin.value) return true;
  if (isTeacher.value && row.currentAssigneeId === currentUserId) return true;
  return false;
};

const canUpgrade = (row: WarnTicketVO) => {
  if (row.status === 'CLOSED' || row.status === 'FALSE_ALARM_CLOSED') return false;
  if (row.isUpgraded === 1) return false;
  if (isTeacher.value && row.currentAssigneeId === currentUserId) return true;
  return false;
};

const handleOpenProcess = (row: WarnTicketVO) => {
  processForm.ticketId = row.id;
  processForm.action = 'PROCESSING';
  processForm.teacherInvestigation = row.teacherInvestigation || '';
  processForm.handlingMeasures = row.handlingMeasures || '';
  processDialogVisible.value = true;
};

const submitProcess = async () => {
  if (!processFormRef.value) return;
  await processFormRef.value.validate(async (valid) => {
    if (!valid) return;
    processSubmitting.value = true;
    try {
      const res = await handleWarnTicket(processForm.ticketId, {
        action: processForm.action,
        teacherInvestigation: processForm.teacherInvestigation,
        handlingMeasures: processForm.handlingMeasures
      });
      if (res.code === 200) {
        ElMessage.success('工单处置结论提交成功');
        processDialogVisible.value = false;
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '处置提交失败');
    } finally {
      processSubmitting.value = false;
    }
  });
};

const handleOpenUpgrade = (row: WarnTicketVO) => {
  upgradeForm.ticketId = row.id;
  upgradeForm.upgradeReason = '';
  upgradeDialogVisible.value = true;
};

const submitUpgrade = async () => {
  if (!upgradeFormRef.value) return;
  await upgradeFormRef.value.validate(async (valid) => {
    if (!valid) return;
    upgradeSubmitting.value = true;
    try {
      const res = await upgradeWarnTicket(upgradeForm.ticketId, {
        upgradeReason: upgradeForm.upgradeReason
      });
      if (res.code === 200) {
        ElMessage.success('工单已成功升级至院系负责人');
        upgradeDialogVisible.value = false;
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '升级操作失败');
    } finally {
      upgradeSubmitting.value = false;
    }
  });
};

// 样式与文案转换辅助
const getLevelTagType = (level: string) => {
  if (level === 'RED') return 'danger';
  if (level === 'ORANGE') return 'warning';
  return 'info';
};

const formatLevel = (level: string) => {
  if (level === 'RED') return '红色高危';
  if (level === 'ORANGE') return '橙色中危';
  return '黄色预警';
};

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'TRIGGERED': return 'danger';
    case 'DISPATCHED': return 'warning';
    case 'PROCESSING': return 'primary';
    case 'CLOSED': return 'success';
    case 'FALSE_ALARM_CLOSED': return 'info';
    default: return 'info';
  }
};

const formatStatus = (status: string) => {
  switch (status) {
    case 'TRIGGERED': return '已触发';
    case 'DISPATCHED': return '已派单';
    case 'PROCESSING': return '处置中';
    case 'CLOSED': return '核实闭环';
    case 'FALSE_ALARM_CLOSED': return '误报释放';
    default: return status;
  }
};

const formatSnapshot = (snapshotJson: string) => {
  if (!snapshotJson) return '无快照数据';
  try {
    const parsed = JSON.parse(snapshotJson);
    return JSON.stringify(parsed, null, 2);
  } catch (e) {
    return snapshotJson;
  }
};
</script>

<style scoped>
.warn-ticket-center-container {
  padding: 20px;
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
  font-weight: 600;
  color: #1f2937;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  border-radius: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: bold;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
}

.text-primary { color: #409eff; }
.text-warning { color: #e6a23c; }
.text-orange { color: #f97316; }
.text-danger { color: #f56c6c; }
.text-purple { color: #8b5cf6; }

.filter-card, .table-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 10px 0;
  color: #303133;
}

.code-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  max-height: 180px;
  overflow: auto;
}

.code-box pre {
  margin: 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #334155;
}

.feedback-box, .handle-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
}

.feedback-text {
  margin: 0 0 6px 0;
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
}

.upgrade-info-box {
  margin-top: 20px;
}
</style>
