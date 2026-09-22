<template>
  <div class="material-manage-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">阶段材料与实习总结报告</span>
            <el-tag type="info" size="small">API-060 ~ API-064</el-tag>
          </div>
          <div class="header-right">
            <el-select
              v-model="selectedTaskId"
              placeholder="选择实习任务批次"
              style="width: 280px"
              @change="handleTaskChange"
            >
              <el-option
                v-for="task in taskOptions"
                :key="task.id"
                :label="task.taskName"
                :value="task.id"
              />
            </el-select>
            <el-input
              v-if="!isStudent"
              v-model.number="selectedStudentId"
              placeholder="输入学生ID查验"
              style="width: 150px; margin-right: 12px"
              clearable
              @change="fetchMaterials"
            />
            <el-button type="primary" :icon="Refresh" @click="fetchMaterials" :loading="loading">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 提示栏 -->
      <el-alert
        title="阶段材料提报规范说明"
        type="info"
        description="学生在各阶段须提报三方协议、入职通知书、岗位安全记录、中期进展总结及毕业实习总结报告（总结报告字数动态强约束，默认需达到1500字）。提报后自动留存版本快照，支持导师查验评分与追溯。"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      />

      <!-- 学生端展示卡片网格 -->
      <div v-if="isStudent" v-loading="loading">
        <el-row :gutter="20">
          <el-col
            :xs="24"
            :sm="12"
            :md="8"
            v-for="item in materialList"
            :key="item.materialCode"
            style="margin-bottom: 20px"
          >
            <el-card shadow="hover" class="material-card" :class="item.status.toLowerCase()">
              <div class="mat-header">
                <div class="mat-title-box">
                  <span class="mat-name">{{ item.materialName }}</span>
                  <el-tag v-if="item.required" size="small" type="danger">必填</el-tag>
                </div>
                <el-tag :type="getStatusTag(item.status)" size="small">
                  {{ getStatusText(item.status) }}
                </el-tag>
              </div>

              <div class="mat-info">
                <p><strong>材料形态:</strong> {{ getTypeText(item.materialType) }}</p>
                <p><strong>当前版本:</strong> v{{ item.version || 1 }}</p>
                <p v-if="item.minContentLength && item.minContentLength > 0">
                  <strong>字数门槛:</strong> 不少于 {{ item.minContentLength }} 字
                </p>
                <p v-if="item.submitTime"><strong>提交时刻:</strong> {{ formatTime(item.submitTime) }}</p>
                <p v-if="item.auditScore !== null && item.auditScore !== undefined">
                  <strong>查验得分:</strong>
                  <span class="score-highlight">{{ item.auditScore }} 分</span>
                </p>
                <p v-if="item.auditComment" class="audit-comment">
                  <strong>导师评语:</strong> {{ item.auditComment }}
                </p>
              </div>

              <div class="mat-actions">
                <el-button
                  type="primary"
                  size="small"
                  @click="openSubmitDialog(item)"
                  :disabled="item.status === 'APPROVED'"
                >
                  {{ item.status === 'UNSUBMITTED' ? '提报材料' : '重提新版' }}
                </el-button>
                <el-button
                  type="info"
                  size="small"
                  plain
                  @click="openVersionHistory(item)"
                  v-if="item.id"
                >
                  版本追溯
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  link
                  v-if="item.attachmentUrl"
                  @click="viewAttachment(item.attachmentUrl)"
                >
                  查看凭证
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 教师/院系/管理员端表格 -->
      <div v-else v-loading="loading">
        <el-table :data="materialList" border stripe style="width: 100%">
          <el-table-column prop="studentName" label="学生姓名" width="110" />
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="materialName" label="材料名称" min-width="160" />
          <el-table-column prop="materialType" label="形态" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ getTypeText(row.materialType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80">
            <template #default="{ row }">v{{ row.version }}</template>
          </el-table-column>
          <el-table-column prop="status" label="查验状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submitTime" label="提报时间" width="160">
            <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
          </el-table-column>
          <el-table-column prop="auditScore" label="考评分" width="90">
            <template #default="{ row }">
              <span v-if="row.auditScore !== null && row.auditScore !== undefined" style="color: #67c23a; font-weight: bold;">
                {{ row.auditScore }}
              </span>
              <span v-else style="color: #909399;">未评</span>
            </template>
          </el-table-column>
          <el-table-column prop="auditTeacherName" label="查验导师" width="100" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                v-if="canAudit(row)"
                @click="openAuditDialog(row)"
              >
                查验打分
              </el-button>
              <el-button
                size="small"
                type="info"
                plain
                @click="openVersionHistory(row)"
                v-if="row.id"
              >
                版本快照
              </el-button>
              <el-button
                size="small"
                type="success"
                link
                v-if="row.attachmentUrl"
                @click="viewAttachment(row.attachmentUrl)"
              >
                凭证
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 材料提报弹窗 (学生) -->
    <el-dialog
      v-model="submitDialogVisible"
      :title="`提报材料: ${activeItem?.materialName} (v${(activeItem?.version || 0) + 1})`"
      width="680px"
      destroy-on-close
    >
      <el-form :model="submitForm" label-width="110px" :rules="submitRules" ref="submitFormRef">
        <el-form-item label="材料编码">
          <el-input :value="activeItem?.materialCode" disabled />
        </el-form-item>

        <el-form-item
          label="凭据佐证URL"
          prop="attachmentUrl"
          v-if="activeItem?.materialType === 'VOUCHER_FILE' || activeItem?.materialType === 'HYBRID'"
        >
          <el-input
            v-model="submitForm.attachmentUrl"
            placeholder="请输入凭证网络链接 (例如 https://oss.college.edu.cn/vouchers/...)"
          />
          <span class="sub-hint">仅支持标准HTTP/HTTPS协议，禁止内网敏感IP及回环地址</span>
        </el-form-item>

        <el-form-item
          label="凭据文件名"
          v-if="activeItem?.materialType === 'VOUCHER_FILE' || activeItem?.materialType === 'HYBRID'"
        >
          <el-input v-model="submitForm.fileName" placeholder="选填，如 三方协议盖章件_2026.pdf" />
        </el-form-item>

        <el-form-item
          label="报告正文"
          prop="contentText"
          v-if="activeItem?.materialType === 'REPORT_TEXT' || activeItem?.materialType === 'HYBRID'"
        >
          <el-input
            type="textarea"
            v-model="submitForm.contentText"
            :rows="12"
            placeholder="请输入正文内容..."
          />
          <div class="word-counter" :class="{ 'error-len': currentContentLength < minLengthRequirement }">
            当前字数: <strong>{{ currentContentLength }}</strong> 字 / 最低要求: {{ minLengthRequirement }} 字
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitMaterial">
          确认提交提报
        </el-button>
      </template>
    </el-dialog>

    <!-- 材料查验打分弹窗 (教师) -->
    <el-dialog
      v-model="auditDialogVisible"
      title="导师查验材料与考评分数评定"
      width="560px"
      destroy-on-close
    >
      <el-form :model="auditForm" label-width="100px">
        <el-form-item label="学生信息">
          <span>{{ activeItem?.studentName }} ({{ activeItem?.studentNo }})</span>
        </el-form-item>
        <el-form-item label="材料名称">
          <span>{{ activeItem?.materialName }} (v{{ activeItem?.version }})</span>
        </el-form-item>
        <el-form-item label="查验结论" required>
          <el-radio-group v-model="auditForm.action">
            <el-radio label="APPROVED">查验通过</el-radio>
            <el-radio label="RETURNED">退回修改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="考评分数" v-if="auditForm.action === 'APPROVED'" required>
          <el-input-number
            v-model="auditForm.auditScore"
            :min="0"
            :max="100"
            :precision="2"
            :step="1"
            placeholder="0-100分"
          />
          <span style="margin-left: 10px; color: #909399;">百分制 (0.00 ~ 100.00)</span>
        </el-form-item>
        <el-form-item label="指导评语" required>
          <el-input
            type="textarea"
            v-model="auditForm.auditComment"
            :rows="4"
            placeholder="请输入查验审核意见，若退回请详细说明修改要求..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="handleAuditMaterial">
          确认查验结果
        </el-button>
      </template>
    </el-dialog>

    <!-- 版本快照与追溯弹窗 -->
    <el-dialog
      v-model="historyDialogVisible"
      :title="`版本快照追溯: ${activeItem?.materialName}`"
      width="780px"
      destroy-on-close
    >
      <div v-loading="historyLoading">
        <el-timeline v-if="versionHistoryList.length > 0">
          <el-timeline-item
            v-for="ver in versionHistoryList"
            :key="ver.id"
            :timestamp="formatTime(ver.submitTime || ver.createdAt)"
            placement="top"
          >
            <el-card>
              <h4>版本 v{{ ver.version }} 状态: <el-tag size="small" :type="getStatusTag(ver.status)">{{ getStatusText(ver.status) }}</el-tag></h4>
              <p v-if="ver.auditScore !== null && ver.auditScore !== undefined">
                <strong>评分:</strong> <span class="score-highlight">{{ ver.auditScore }} 分</span>
                <span v-if="ver.auditTeacherName"> (审核教师: {{ ver.auditTeacherName }})</span>
              </p>
              <p v-if="ver.auditComment"><strong>查验评语:</strong> {{ ver.auditComment }}</p>
              <p v-if="ver.attachmentUrl">
                <strong>凭据链接:</strong>
                <a :href="ver.attachmentUrl" target="_blank">{{ ver.fileName || ver.attachmentUrl }}</a>
              </p>
              <div v-if="ver.contentText" class="content-preview">
                <strong>正文内容 ({{ ver.contentText.length }}字):</strong>
                <div class="text-box">{{ ver.contentText }}</div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无历史版本快照" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { getTaskList, TaskItem } from '@/api/index';
import {
  getMaterialItems,
  getMaterialVersions,
  submitMaterial,
  auditMaterial,
  MaterialItemVO,
  MaterialVersionVO
} from '@/api/phase7';

const userType = computed(() => localStorage.getItem('userType') || '');
const isStudent = computed(() => userType.value === 'STUDENT');
const isTeacher = computed(() => userType.value === 'TEACHER');

const selectedTaskId = ref<number | null>(null);
const selectedStudentId = ref<number | undefined>(undefined);
const taskOptions = ref<TaskItem[]>([]);
const materialList = ref<MaterialItemVO[]>([]);
const loading = ref(false);

const submitDialogVisible = ref(false);
const submitting = ref(false);
const submitFormRef = ref();
const activeItem = ref<MaterialItemVO | null>(null);

const submitForm = ref({
  attachmentUrl: '',
  fileName: '',
  contentText: ''
});

const currentContentLength = computed(() => {
  return submitForm.value.contentText ? submitForm.value.contentText.trim().length : 0;
});

const minLengthRequirement = computed(() => {
  if (activeItem.value?.materialCode === 'SUMMARY_REPORT') return 1500;
  if (activeItem.value?.materialCode === 'MIDTERM_SUMMARY') return 500;
  return 0;
});

const submitRules = {
  contentText: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        const len = value ? value.trim().length : 0;
        if (minLengthRequirement.value > 0 && len < minLengthRequirement.value) {
          callback(new Error(`正文字数不足，最低要求 ${minLengthRequirement.value} 字 (当前 ${len} 字)`));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
};

const auditDialogVisible = ref(false);
const auditing = ref(false);
const auditForm = ref({
  action: 'APPROVED' as 'APPROVED' | 'RETURNED',
  auditScore: 90.0,
  auditComment: ''
});

const historyDialogVisible = ref(false);
const historyLoading = ref(false);
const versionHistoryList = ref<MaterialVersionVO[]>([]);

function getStatusText(status: string) {
  switch (status) {
    case 'UNSUBMITTED': return '未提交';
    case 'SUBMITTED': return '待查验';
    case 'APPROVED': return '查验合格';
    case 'RETURNED': return '退回重修';
    default: return status;
  }
}

function getStatusTag(status: string) {
  switch (status) {
    case 'UNSUBMITTED': return 'info';
    case 'SUBMITTED': return 'warning';
    case 'APPROVED': return 'success';
    case 'RETURNED': return 'danger';
    default: return 'info';
  }
}

function getTypeText(type: string) {
  switch (type) {
    case 'VOUCHER_FILE': return '凭证附件';
    case 'REPORT_TEXT': return '长文本报告';
    case 'HYBRID': return '图文混合';
    default: return type;
  }
}

function formatTime(str?: string) {
  if (!str) return '-';
  return str.replace('T', ' ').substring(0, 19);
}

function canAudit(row: MaterialItemVO) {
  return (isTeacher.value || !isStudent.value) && (row.status === 'SUBMITTED' || row.status === 'RETURNED');
}

function viewAttachment(url: string) {
  window.open(url, '_blank');
}

async function loadTasks() {
  try {
    const res = await getTaskList();
    if (res.data && res.data.length > 0) {
      taskOptions.value = res.data;
      selectedTaskId.value = res.data[0].id;
      await fetchMaterials();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载实习任务列表失败');
  }
}

async function handleTaskChange() {
  await fetchMaterials();
}

async function fetchMaterials() {
  if (!selectedTaskId.value) return;
  if (!isStudent.value && !selectedStudentId.value) {
    materialList.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await getMaterialItems(selectedTaskId.value, selectedStudentId.value);
    materialList.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取阶段材料列表失败');
  } finally {
    loading.value = false;
  }
}

function openSubmitDialog(item: MaterialItemVO) {
  activeItem.value = item;
  submitForm.value = {
    attachmentUrl: item.attachmentUrl || '',
    fileName: item.fileName || '',
    contentText: item.contentText || ''
  };
  submitDialogVisible.value = true;
}

async function handleSubmitMaterial() {
  if (!activeItem.value || !selectedTaskId.value) return;
  if (submitFormRef.value) {
    await submitFormRef.value.validate();
  }

  submitting.value = true;
  try {
    await submitMaterial({
      taskId: selectedTaskId.value,
      materialCode: activeItem.value.materialCode,
      contentText: submitForm.value.contentText,
      attachmentUrl: submitForm.value.attachmentUrl,
      fileName: submitForm.value.fileName
    });
    ElMessage.success('材料提报成功');
    submitDialogVisible.value = false;
    await fetchMaterials();
  } catch (err: any) {
    ElMessage.error(err.message || '提报失败');
  } finally {
    submitting.value = false;
  }
}

function openAuditDialog(item: MaterialItemVO) {
  activeItem.value = item;
  auditForm.value = {
    action: 'APPROVED',
    auditScore: item.auditScore || 90.0,
    auditComment: item.auditComment || ''
  };
  auditDialogVisible.value = true;
}

async function handleAuditMaterial() {
  if (!activeItem.value?.id) return;
  if (!auditForm.value.auditComment.trim()) {
    ElMessage.warning('请填写查验指导评语');
    return;
  }
  auditing.value = true;
  try {
    await auditMaterial(activeItem.value.id, {
      action: auditForm.value.action,
      auditScore: auditForm.value.action === 'APPROVED' ? auditForm.value.auditScore : undefined,
      auditComment: auditForm.value.auditComment
    });
    ElMessage.success('查验打分完成');
    auditDialogVisible.value = false;
    await fetchMaterials();
  } catch (err: any) {
    ElMessage.error(err.message || '查验操作失败');
  } finally {
    auditing.value = false;
  }
}

async function openVersionHistory(item: MaterialItemVO) {
  if (!item.id) return;
  activeItem.value = item;
  historyDialogVisible.value = true;
  historyLoading.value = true;
  try {
    const res = await getMaterialVersions(item.id);
    versionHistoryList.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || '获取历史版本快照失败');
  } finally {
    historyLoading.value = false;
  }
}

onMounted(() => {
  loadTasks();
});
</script>

<style scoped>
.material-manage-container {
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
.header-right {
  display: flex;
  gap: 12px;
}
.material-card {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.mat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 10px;
  margin-bottom: 12px;
}
.mat-title-box {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mat-name {
  font-size: 16px;
  font-weight: bold;
}
.mat-info p {
  margin: 6px 0;
  font-size: 13px;
  color: #606266;
}
.score-highlight {
  color: #67c23a;
  font-size: 15px;
  font-weight: bold;
}
.audit-comment {
  background-color: #fdf6ec;
  padding: 6px 8px;
  border-radius: 4px;
  color: #e6a23c;
}
.mat-actions {
  margin-top: 15px;
  border-top: 1px solid #f0f2f5;
  padding-top: 10px;
  display: flex;
  gap: 8px;
}
.sub-hint {
  font-size: 12px;
  color: #909399;
}
.word-counter {
  margin-top: 6px;
  font-size: 12px;
  color: #67c23a;
}
.error-len {
  color: #f56c6c;
}
.content-preview {
  margin-top: 10px;
}
.text-box {
  background: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 140px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: 13px;
  color: #303133;
}
</style>
