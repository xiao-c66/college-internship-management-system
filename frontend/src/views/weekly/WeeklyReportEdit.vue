<template>
  <div class="weekly-edit-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">
            实习周报填报与编辑 (API-058)
            <span v-if="existingReport" class="version-badge">版本 v{{ existingReport.version }}</span>
          </h2>
          <p class="page-subtitle">
            所属任务: <strong>{{ taskInfo?.taskName || '加载中...' }}</strong> |
            当前填报: <strong>第 {{ weekNumber }} 周</strong> |
            统计周期: {{ periodRange }} |
            截止时间: <span class="deadline-highlight">{{ deadlineTimeStr }}</span>
          </p>
        </div>
        <div class="header-action">
          <el-button :icon="Back" @click="handleBack">返回列表</el-button>
          <el-button
            v-if="existingReport?.historyList && existingReport.historyList.length > 0"
            :icon="Document"
            type="info"
            @click="historyDrawerVisible = true"
          >
            修改流转历史 ({{ existingReport.historyList.length }})
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 退回修改提示横幅 -->
    <el-alert
      v-if="existingReport?.status === 'RETURNED'"
      type="error"
      show-icon
      :closable="false"
      class="return-alert"
      title="此周报已被指导教师退回修改！"
    >
      <template #default>
        <div class="return-alert-body">
          <div class="return-opinion">
            <strong>指导教师退回批语/修改要求：</strong>
            <span>{{ existingReport.reviewComment || '未填写具体原因' }}</span>
          </div>
          <div v-if="existingReport.reviewAnnotations" class="return-opinion">
            <strong>逐条批注细节：</strong>
            <span>{{ existingReport.reviewAnnotations }}</span>
          </div>
          <p class="return-tip">
            请根据指导教师提出的意见与修改要求认真修正，修改完成后点击下方「正式提交批阅」重新流转。
          </p>
        </div>
      </template>
    </el-alert>

    <!-- 已经正式提交或已批阅的只读提示 -->
    <el-alert
      v-else-if="existingReport?.status === 'SUBMITTED' || existingReport?.status === 'REVIEWED'"
      type="info"
      show-icon
      :closable="false"
      class="readonly-alert"
      :title="existingReport.status === 'SUBMITTED' ? '周报已正式提交，正在等待指导教师批阅中' : '周报已批阅完成，成绩为 ' + existingReport.score + ' 分'"
      description="正式提交或已批阅的周报不可直接修改。如需修改，须由指导教师执行退回操作。"
    />

    <!-- 四段式周报表单 -->
    <el-card shadow="never" class="form-card" v-loading="loading">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        :disabled="isReadOnly"
      >
        <!-- 1. 本周主要工作内容 -->
        <div class="form-section">
          <div class="section-header">
            <span class="section-num">1</span>
            <div class="section-title-group">
              <h4 class="section-main-title">本周主要工作内容与岗位任务 <span class="required-star">*</span></h4>
              <span class="section-desc">详细记录本周实际参与的项目、完成的工作事项、业务流程及具体岗位职责</span>
            </div>
            <span class="char-count" :class="{ 'char-valid': (formData.workContent?.length || 0) > 0 }">
              {{ formData.workContent?.length || 0 }} / 2000 字
            </span>
          </div>
          <el-form-item prop="workContent">
            <el-input
              v-model="formData.workContent"
              type="textarea"
              :rows="5"
              maxlength="2000"
              show-word-limit
              placeholder="请输入本周主要工作内容与岗位任务..."
            />
          </el-form-item>
        </div>

        <!-- 2. 工作心得体会 -->
        <div class="form-section">
          <div class="section-header">
            <span class="section-num">2</span>
            <div class="section-title-group">
              <h4 class="section-main-title">工作心得体会与阶段收获 <span class="required-star">*</span></h4>
              <span class="section-desc">总结本周在专业理论与岗位技能结合、职场沟通、职业素养等方面的感悟与收获</span>
            </div>
            <span class="char-count" :class="{ 'char-valid': (formData.workSummary?.length || 0) > 0 }">
              {{ formData.workSummary?.length || 0 }} / 2000 字
            </span>
          </div>
          <el-form-item prop="workSummary">
            <el-input
              v-model="formData.workSummary"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="请输入工作心得体会与阶段收获..."
            />
          </el-form-item>
        </div>

        <!-- 3. 遇到的问题与解决措施 -->
        <div class="form-section">
          <div class="section-header">
            <span class="section-num">3</span>
            <div class="section-title-group">
              <h4 class="section-main-title">遇到的主要问题与解决措施 <span class="required-star">*</span></h4>
              <span class="section-desc">客观陈述实习过程中遇到的技术瓶颈、环境不适或业务疑难，以及采取的解决路径</span>
            </div>
            <span class="char-count" :class="{ 'char-valid': (formData.problemEncountered?.length || 0) > 0 }">
              {{ formData.problemEncountered?.length || 0 }} / 2000 字
            </span>
          </div>
          <el-form-item prop="problemEncountered">
            <el-input
              v-model="formData.problemEncountered"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="请输入遇到的主要问题与解决措施..."
            />
          </el-form-item>
        </div>

        <!-- 4. 下周工作计划 -->
        <div class="form-section">
          <div class="section-header">
            <span class="section-num">4</span>
            <div class="section-title-group">
              <h4 class="section-main-title">下周工作计划与改进设想 <span class="required-star">*</span></h4>
              <span class="section-desc">梳理规划下周拟推进的岗位任务、学习目标与自我提升安排</span>
            </div>
            <span class="char-count" :class="{ 'char-valid': (formData.nextWeekPlan?.length || 0) > 0 }">
              {{ formData.nextWeekPlan?.length || 0 }} / 2000 字
            </span>
          </div>
          <el-form-item prop="nextWeekPlan">
            <el-input
              v-model="formData.nextWeekPlan"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="请输入下周工作计划与改进设想..."
            />
          </el-form-item>
        </div>

        <!-- 5. 凭证/附件链接 -->
        <div class="form-section attachment-section">
          <div class="section-header">
            <span class="section-num">5</span>
            <div class="section-title-group">
              <h4 class="section-main-title">佐证材料/附件文档链接 (选填)</h4>
              <span class="section-desc">支持输入包含 http/https 的凭证文件网络链接（支持 jpg, jpeg, png, pdf 等格式）</span>
            </div>
          </div>
          <el-form-item prop="attachmentUrl">
            <el-input
              v-model="formData.attachmentUrl"
              placeholder="https://example.com/files/weekly_evidence.pdf"
              clearable
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </div>

        <!-- 底部提交操作按钮 -->
        <div class="form-actions" v-if="!isReadOnly">
          <el-button
            size="large"
            :loading="submitting"
            @click="handleSaveDraft"
          >
            保存为草稿 (DRAFT)
          </el-button>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            @click="handleSubmitFormal"
          >
            正式提交批阅 (SUBMIT)
          </el-button>
          <el-button size="large" @click="handleBack">取消返回</el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 历史快照版本抽屉 -->
    <el-drawer
      v-model="historyDrawerVisible"
      title="周报修改与流转历史版本"
      size="560px"
      destroy-on-close
    >
      <div v-if="existingReport?.historyList" class="history-drawer-body">
        <el-timeline>
          <el-timeline-item
            v-for="hist in existingReport.historyList"
            :key="hist.id"
            :timestamp="hist.operateTime"
            :type="hist.action === 'APPROVE' ? 'success' : (hist.action === 'RETURN' ? 'danger' : 'primary')"
          >
            <div class="hist-entry">
              <div class="hist-header">
                <el-tag size="small" :type="hist.action === 'APPROVE' ? 'success' : (hist.action === 'RETURN' ? 'danger' : 'primary')">
                  {{ hist.action }}
                </el-tag>
                <span class="hist-user">{{ hist.operatorName }} ({{ hist.operatorRole }})</span>
                <span class="hist-v">v{{ hist.version }}</span>
              </div>
              <div v-if="hist.returnReason" class="hist-msg-return">
                <strong>退回理由:</strong> {{ hist.returnReason }}
              </div>
              <div v-if="hist.reviewComment" class="hist-msg-review">
                <strong>批阅评语:</strong> {{ hist.reviewComment }}
                <span v-if="hist.score !== null && hist.score !== undefined"> (得分: {{ hist.score }} 分)</span>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Back, Document, Link } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus';
import {
  getTaskDetail,
  getWeeklyReportList,
  getWeeklyReportDetail,
  saveOrSubmitWeeklyReport,
  TaskItem,
  WeeklyReportDetailItem
} from '@/api';

const route = useRoute();
const router = useRouter();

const taskId = ref<number>(Number(route.params.taskId));
const weekNumber = ref<number>(Number(route.params.weekNo));

const loading = ref(false);
const submitting = ref(false);
const historyDrawerVisible = ref(false);

const taskInfo = ref<TaskItem | null>(null);
const existingReport = ref<WeeklyReportDetailItem | null>(null);

const formRef = ref<FormInstance>();
const formData = reactive({
  workContent: '',
  workSummary: '',
  problemEncountered: '',
  nextWeekPlan: '',
  attachmentUrl: ''
});

const isReadOnly = computed(() => {
  if (!existingReport.value) return false;
  return existingReport.value.status === 'SUBMITTED' || existingReport.value.status === 'REVIEWED';
});

const periodRange = computed(() => {
  if (existingReport.value?.startDate && existingReport.value?.endDate) {
    return `${existingReport.value.startDate} ~ ${existingReport.value.endDate}`;
  }
  return '按批次周期计算';
});

const deadlineTimeStr = computed(() => {
  return existingReport.value?.deadlineTime || '周日 23:59:59 (北京时间)';
});

const validateAttachmentUrl = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback();
    return;
  }
  const trimmed = value.trim();
  if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) {
    callback(new Error('附件链接须以 http:// 或 https:// 开头'));
    return;
  }
  const extMatch = trimmed.match(/\.(jpg|jpeg|png|pdf)(\?.*)?$/i);
  if (!extMatch) {
    callback(new Error('凭证格式仅支持 jpg, jpeg, png, pdf'));
    return;
  }
  callback();
};

const formRules = reactive<FormRules>({
  workContent: [
    { required: true, message: '请填写本周主要工作内容', trigger: 'blur' }
  ],
  workSummary: [
    { required: true, message: '请填写工作心得体会与阶段收获', trigger: 'blur' }
  ],
  problemEncountered: [
    { required: true, message: '请陈述遇到的问题与解决对策', trigger: 'blur' }
  ],
  nextWeekPlan: [
    { required: true, message: '请规划下周工作计划', trigger: 'blur' }
  ],
  attachmentUrl: [
    { validator: validateAttachmentUrl, trigger: 'blur' }
  ]
});

const loadInitialData = async () => {
  loading.value = true;
  try {
    // 1. 获取任务信息
    if (taskId.value) {
      const tRes = await getTaskDetail(taskId.value);
      taskInfo.value = tRes.data;
    }

    // 2. 获取已有周报列表定位当前周次
    const rRes = await getWeeklyReportList({ taskId: taskId.value });
    const currentWeekItem = rRes.data?.find((r) => r.weekNumber === weekNumber.value);

    if (currentWeekItem) {
      // 查询完整详情 (带历史快照)
      const detailRes = await getWeeklyReportDetail(currentWeekItem.id);
      existingReport.value = detailRes.data;
      formData.workContent = detailRes.data.workContent || '';
      formData.workSummary = detailRes.data.workSummary || '';
      formData.problemEncountered = detailRes.data.problemEncountered || '';
      formData.nextWeekPlan = detailRes.data.nextWeekPlan || '';
      formData.attachmentUrl = detailRes.data.attachmentUrl || '';
    }
  } catch (err: any) {
    ElMessage.error(err.message || '加载周报数据失败');
  } finally {
    loading.value = false;
  }
};

const handleSaveDraft = async () => {
  submitting.value = true;
  try {
    await saveOrSubmitWeeklyReport({
      taskId: taskId.value,
      weekNumber: weekNumber.value,
      action: 'DRAFT',
      workContent: formData.workContent,
      workSummary: formData.workSummary,
      problemEncountered: formData.problemEncountered,
      nextWeekPlan: formData.nextWeekPlan,
      attachmentUrl: formData.attachmentUrl?.trim() || undefined
    });
    ElMessage.success('周报已暂存为草稿');
    await loadInitialData();
  } catch (err: any) {
    ElMessage.error(err.message || '保存草稿失败');
  } finally {
    submitting.value = false;
  }
};

const handleSubmitFormal = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      ElMessage.warning('请完整填写四项结构化内容');
      return;
    }

    try {
      await ElMessageBox.confirm(
        '正式提交后将进入指导教师批阅队列，提交后学生不可自行撤回修改。确认正式提交第 ' + weekNumber.value + ' 周周报吗？',
        '正式提交确认',
        {
          confirmButtonText: '确认正式提交',
          cancelButtonText: '再检查一下',
          type: 'warning'
        }
      );

      submitting.value = true;
      await saveOrSubmitWeeklyReport({
        taskId: taskId.value,
        weekNumber: weekNumber.value,
        action: 'SUBMIT',
        workContent: formData.workContent,
        workSummary: formData.workSummary,
        problemEncountered: formData.problemEncountered,
        nextWeekPlan: formData.nextWeekPlan,
        attachmentUrl: formData.attachmentUrl?.trim() || undefined
      });

      ElMessage.success('第 ' + weekNumber.value + ' 周实习周报正式提交成功！');
      router.push('/weekly/my');
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(e.message || '周报提交失败');
      }
    } finally {
      submitting.value = false;
    }
  });
};

const handleBack = () => {
  router.push('/weekly/my');
};

onMounted(() => {
  loadInitialData();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.weekly-edit-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1080px;
  margin: 0 auto;
}

.header-card {
  background-color: $card-bg;
  border-radius: $radius-card;
  .header-flex {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 6px 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .version-badge {
    font-size: 12px;
    background-color: #f0f0f0;
    color: $text-secondary;
    padding: 2px 8px;
    border-radius: 4px;
    font-weight: normal;
  }
  .page-subtitle {
    font-size: 13px;
    color: $text-secondary;
    margin: 0;
  }
  .deadline-highlight {
    color: $danger-color;
    font-weight: 500;
  }
  .header-action {
    display: flex;
    gap: 8px;
  }
}

.return-alert {
  border-radius: $radius-card;
}

.return-alert-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
  .return-opinion {
    font-size: 13px;
    color: $danger-color;
  }
  .return-tip {
    font-size: 12px;
    color: $text-secondary;
    margin: 4px 0 0 0;
  }
}

.readonly-alert {
  border-radius: $radius-card;
}

.form-card {
  background-color: $card-bg;
  border-radius: $radius-card;
  padding: 10px 20px;
}

.form-section {
  background-color: #fafbfc;
  border: 1px solid $border-color-light;
  border-radius: $radius-card;
  padding: 16px 20px;
  margin-bottom: 20px;

  .section-header {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    margin-bottom: 12px;

    .section-num {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background-color: $primary-light-bg;
      color: $primary-color;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: bold;
      font-size: 14px;
      flex-shrink: 0;
    }

    .section-title-group {
      flex: 1;

      .section-main-title {
        margin: 0 0 4px 0;
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
      }

      .required-star {
        color: $danger-color;
      }

      .section-desc {
        font-size: 12px;
        color: $text-muted;
      }
    }

    .char-count {
      font-size: 12px;
      color: $text-muted;
      font-family: monospace;

      &.char-valid {
        color: $success-color;
        font-weight: 600;
      }
    }
  }
}

.attachment-section {
  background-color: #ffffff;
  border: 1px dashed $border-color;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 30px;
  margin-bottom: 10px;
}

.history-drawer-body {
  padding: 10px;
}

.hist-entry {
  background-color: #f7f9fa;
  border-radius: 4px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .hist-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    .hist-user {
      color: $text-secondary;
    }
    .hist-v {
      color: $text-muted;
      margin-left: auto;
    }
  }

  .hist-msg-return {
    font-size: 12px;
    color: $danger-color;
    line-height: 1.4;
  }

  .hist-msg-review {
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.4;
  }
}
</style>
