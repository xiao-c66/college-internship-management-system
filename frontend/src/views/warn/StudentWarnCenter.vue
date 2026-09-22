<template>
  <div class="student-warn-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">学生过程预警与申辩中心</span>
            <el-tag type="warning" size="small">API-089</el-tag>
          </div>
          <div class="header-right">
            <el-button type="primary" :icon="Refresh" @click="fetchTickets" :loading="loading">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="异常预警提醒说明"
        type="warning"
        description="系统会根据实习出勤、指导频率、周报提交等全流程业务指标自动触发黄/橙/红三级预警。若收到预警提示，请及时联系指导教师，并可在本页面填写申辩说明，提交后将作为导师核实及销号闭环的重要依据。"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      />

      <el-table :data="ticketList" border stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="ticketNo" label="预警工单号" width="160" />
        <el-table-column prop="warnLevel" label="预警级别" width="110">
          <template #default="{ row }">
            <el-tag :type="getWarnLevelTag(row.warnLevel)" effect="dark">
              {{ getWarnLevelText(row.warnLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warnTitle" label="预警概要标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="currentAssigneeName" label="当前处置责任人" width="140">
          <template #default="{ row }">
            {{ row.currentAssigneeName || '指导教师' }} ({{ row.currentAssigneeRole === 'TEACHER' ? '导师' : '院系' }})
          </template>
        </el-table-column>
        <el-table-column prop="status" label="工单状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="触发时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              v-if="row.status !== 'CLOSED' && row.status !== 'FALSE_ALARM_CLOSED'"
              @click="openFeedbackDialog(row)"
            >
              {{ row.studentFeedback ? '补充申辩' : '填写申辩' }}
            </el-button>
            <el-button
              size="small"
              type="info"
              plain
              @click="openDetailDialog(row)"
            >
              工单详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 申辩说明提交弹窗 -->
    <el-dialog v-model="feedbackDialogVisible" title="填写预警情况申辩说明" width="560px" destroy-on-close>
      <el-form :model="feedbackForm" label-width="100px">
        <el-form-item label="预警单号">
          <span>{{ activeTicket?.ticketNo }}</span>
        </el-form-item>
        <el-form-item label="预警标题">
          <span>{{ activeTicket?.warnTitle }}</span>
        </el-form-item>
        <el-form-item label="申辩情况" required>
          <el-input
            type="textarea"
            v-model="feedbackForm.studentFeedback"
            :rows="5"
            placeholder="请客观如实说明导致此项预警的具体原因（如请假、出差、调休、单位特殊安排等）..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitFeedback">
          提交申辩说明
        </el-button>
      </template>
    </el-dialog>

    <!-- 工单详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="预警工单全生命周期详情" width="650px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工单编号">{{ activeTicket?.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="预警级别">
          <el-tag :type="getWarnLevelTag(activeTicket?.warnLevel || '')">
            {{ getWarnLevelText(activeTicket?.warnLevel || '') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预警标题" :span="2">{{ activeTicket?.warnTitle }}</el-descriptions-item>
        <el-descriptions-item label="工单状态">{{ getStatusText(activeTicket?.status || '') }}</el-descriptions-item>
        <el-descriptions-item label="触发时间">{{ formatTime(activeTicket?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="我的申辩" :span="2">
          <div class="feedback-text">{{ activeTicket?.studentFeedback || '尚未填写申辩说明' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="导师调查说明" :span="2" v-if="activeTicket?.teacherInvestigation">
          <div class="invest-text">{{ activeTicket?.teacherInvestigation }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="干预措施" :span="2" v-if="activeTicket?.handlingMeasures">
          {{ activeTicket?.handlingMeasures }}
        </el-descriptions-item>
        <el-descriptions-item label="销号时间" :span="2" v-if="activeTicket?.closedTime">
          {{ formatTime(activeTicket?.closedTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { getWarnTickets, feedbackWarnTicket, WarnTicketVO } from '@/api/phase7';

const loading = ref(false);
const ticketList = ref<WarnTicketVO[]>([]);

const feedbackDialogVisible = ref(false);
const submitting = ref(false);
const activeTicket = ref<WarnTicketVO | null>(null);
const feedbackForm = ref({
  studentFeedback: ''
});

const detailDialogVisible = ref(false);

function getWarnLevelText(level: string) {
  switch (level) {
    case 'YELLOW': return '黄色预警';
    case 'ORANGE': return '橙色预警';
    case 'RED': return '红色预警';
    default: return level;
  }
}

function getWarnLevelTag(level: string) {
  switch (level) {
    case 'YELLOW': return 'warning';
    case 'ORANGE': return 'danger';
    case 'RED': return 'danger';
    default: return 'info';
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'TRIGGERED': return '已触发待派单';
    case 'DISPATCHED': return '已派单待处置';
    case 'PROCESSING': return '调查核实中';
    case 'PENDING_REVIEW': return '待审核闭环';
    case 'CLOSED': return '已销号闭环';
    case 'FALSE_ALARM_CLOSED': return '已判定误报销号';
    default: return status;
  }
}

function getStatusTag(status: string) {
  switch (status) {
    case 'TRIGGERED':
    case 'DISPATCHED': return 'danger';
    case 'PROCESSING': return 'warning';
    case 'PENDING_REVIEW': return 'info';
    case 'CLOSED':
    case 'FALSE_ALARM_CLOSED': return 'success';
    default: return 'info';
  }
}

function formatTime(str?: string) {
  if (!str) return '-';
  return str.replace('T', ' ').substring(0, 19);
}

async function fetchTickets() {
  loading.value = true;
  try {
    const res = await getWarnTickets();
    ticketList.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取预警工单失败');
  } finally {
    loading.value = false;
  }
}

function openFeedbackDialog(ticket: WarnTicketVO) {
  activeTicket.value = ticket;
  feedbackForm.value.studentFeedback = ticket.studentFeedback || '';
  feedbackDialogVisible.value = true;
}

function openDetailDialog(ticket: WarnTicketVO) {
  activeTicket.value = ticket;
  detailDialogVisible.value = true;
}

async function handleSubmitFeedback() {
  if (!activeTicket.value) return;
  if (!feedbackForm.value.studentFeedback.trim()) {
    ElMessage.warning('请输入申辩说明');
    return;
  }

  submitting.value = true;
  try {
    await feedbackWarnTicket(activeTicket.value.id, {
      studentFeedback: feedbackForm.value.studentFeedback
    });
    ElMessage.success('申辩说明提交成功');
    feedbackDialogVisible.value = false;
    await fetchTickets();
  } catch (err: any) {
    ElMessage.error(err.message || '提交失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  fetchTickets();
});
</script>

<style scoped>
.student-warn-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left .title {
  font-size: 18px;
  font-weight: 600;
  margin-right: 12px;
}
.feedback-text {
  background: #fdf6ec;
  padding: 8px 12px;
  border-radius: 4px;
  color: #e6a23c;
}
.invest-text {
  background: #f0f9eb;
  padding: 8px 12px;
  border-radius: 4px;
  color: #67c23a;
}
</style>
