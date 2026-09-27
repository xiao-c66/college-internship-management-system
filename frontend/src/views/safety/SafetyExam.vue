<template>
  <div class="safety-exam-container" v-loading="loading">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <el-button link :icon="Back" @click="goBack">返回安全学习主页</el-button>
          <h2 class="page-title">实习安全教育与准入在线测试 (SAFE-004 ~ SAFE-006)</h2>
          <p class="page-subtitle">系统从题库中抽取试题并脱敏标准答案，单选与判断题即时自动判分</p>
        </div>
        <div class="header-status" v-if="examResult">
          <el-tag :type="examResult.isPassed ? 'success' : 'danger'" size="large" effect="dark">
            {{ examResult.isPassed ? '测试合格 (通过)' : '未达标 (需重测)' }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 无生效任务空状态 -->
    <el-card shadow="never" class="empty-card" v-if="!taskId">
      <el-empty description="当前暂无可用的生效实习批次任务，请联系院系教学负责人确认任务发布状态" />
    </el-card>

    <!-- 考试结果反馈卡片 (交卷后展示) -->
    <el-card shadow="never" class="result-card" v-else-if="examResult">
      <el-result
        :icon="examResult.isPassed ? 'success' : 'warning'"
        :title="examResult.resultDesc"
        :sub-title="`本次得分：${examResult.totalScore} 分 (及格线: ${examResult.passingScore} 分) | 轮次: 第 ${examResult.attemptNo} 次作答`"
      >
        <template #extra>
          <el-button type="primary" size="large" @click="goToStudy">
            {{ examResult.isPassed ? '前往签署安全承诺书 (SAFE-007)' : '返回复习安全规程并重测' }}
          </el-button>
        </template>
      </el-result>
    </el-card>

    <!-- 在线答题区域 -->
    <el-card shadow="never" class="paper-card" v-else>
      <div class="paper-header">
        <div class="paper-title"><strong>📝 实习安全教育准入考核试卷</strong></div>
        <div class="paper-meta">共 {{ questions.length }} 题 (题库全量题目随机乱序洗牌) | 卷面总分 {{ totalPossibleScore }} 分</div>
      </div>

      <el-form label-position="top" @submit.prevent>
        <div
          v-for="(q, index) in questions"
          :key="q.id"
          class="question-item"
        >
          <div class="question-stem">
            <span class="q-index">{{ index + 1 }}.</span>
            <el-tag size="small" :type="getTypeTag(q.questionType)" class="mr-2">
              {{ formatType(q.questionType) }}
            </el-tag>
            <span class="q-text">{{ q.stem }}</span>
            <span class="q-score">({{ q.score }} 分)</span>
          </div>

          <!-- 单选与判断 -->
          <div class="question-options" v-if="q.questionType === 'SINGLE_CHOICE' || q.questionType === 'JUDGMENT'">
            <el-radio-group v-model="answers[q.id]">
              <div v-for="opt in q.options" :key="opt.key" class="option-row">
                <el-radio :value="opt.key" :label="opt.key">
                  <span class="opt-key">{{ opt.key }}.</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </el-radio>
              </div>
            </el-radio-group>
          </div>

          <!-- 多选 -->
          <div class="question-options" v-else-if="q.questionType === 'MULTIPLE_CHOICE'">
            <el-checkbox-group v-model="multiAnswers[q.id]">
              <div v-for="opt in q.options" :key="opt.key" class="option-row">
                <el-checkbox :value="opt.key" :label="opt.key">
                  <span class="opt-key">{{ opt.key }}.</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
        </div>

        <div class="submit-footer">
          <el-button
            type="primary"
            size="large"
            native-type="button"
            :loading="submitLoading"
            @click.prevent="handleSubmitExam"
          >
            完成作答并提交试卷 (即时判分)
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Back } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getTaskList,
  getExamPaper,
  submitExam,
  getSafetyStatus,
  SafetyQuestion,
  ExamResult,
  ExamAnswerItem
} from '@/api';

const route = useRoute();
const router = useRouter();

const taskId = ref<number>(Number(route.query.taskId) || 0);
const loading = ref(false);
const submitLoading = ref(false);
const questions = ref<SafetyQuestion[]>([]);
const examResult = ref<ExamResult | null>(null);

const answers = reactive<Record<number, string>>({});
const multiAnswers = reactive<Record<number, string[]>>({});

const totalPossibleScore = computed(() => {
  return questions.value.reduce((sum, q) => sum + (Number(q.score) || 0), 0);
});

const resolveTaskId = async (): Promise<number> => {
  if (taskId.value > 0) return taskId.value;
  try {
    const res = await getTaskList({ status: 'PUBLISHED' });
    if (res.data && res.data.length > 0) {
      taskId.value = res.data[0].id;
      return taskId.value;
    }
  } catch (e) {
    console.error('获取生效任务失败', e);
  }
  taskId.value = 0;
  return 0;
};

const loadPaper = async () => {
  loading.value = true;
  try {
    const tid = await resolveTaskId();
    if (!tid || tid <= 0) {
      questions.value = [];
      return;
    }
    const res = await getExamPaper(tid);
    const rawList = res.data || [];
    questions.value = rawList.map(q => {
      let parsedOptions = q.options;
      if (typeof q.options === 'string') {
        try {
          parsedOptions = JSON.parse(q.options);
        } catch {
          parsedOptions = [];
        }
      }
      return {
        ...q,
        options: Array.isArray(parsedOptions) ? parsedOptions : []
      };
    });
    // 初始化默认多选题数组
    questions.value.forEach(q => {
      if (q.questionType === 'MULTIPLE_CHOICE') {
        multiAnswers[q.id] = [];
      }
    });
  } catch (e: any) {
    ElMessage.error(e.message || '获取试卷失败');
  } finally {
    loading.value = false;
  }
};

const handleSubmitExam = async () => {
  // 检查是否全部作答
  const unAnswered = questions.value.some(q => {
    if (q.questionType === 'MULTIPLE_CHOICE') {
      return !multiAnswers[q.id] || multiAnswers[q.id].length === 0;
    }
    return !answers[q.id];
  });

  if (unAnswered) {
    try {
      await ElMessageBox.confirm('您尚有部分试题未作答，确定现在提交试卷吗？未作答题目将判定为0分。', '提交确认', {
        confirmButtonText: '坚持交卷',
        cancelButtonText: '继续作答',
        type: 'warning'
      });
    } catch {
      return;
    }
  }

  submitLoading.value = true;
  try {
    const submitItems: ExamAnswerItem[] = questions.value.map(q => {
      let ansStr = '';
      if (q.questionType === 'MULTIPLE_CHOICE') {
        ansStr = (multiAnswers[q.id] || []).sort().join(',');
      } else {
        ansStr = answers[q.id] || '';
      }
      return {
        questionId: q.id,
        studentAnswer: ansStr
      };
    });

    const res = await submitExam({
      taskId: taskId.value,
      answers: submitItems
    });
    examResult.value = res.data;
    if (res.data && res.data.isPassed) {
      ElMessage.success('恭喜！安全准入考试顺利达标通过！');
    } else {
      ElMessage.warning(`考试成绩未达${res.data?.passingScore || '及格'}分及格线，请复习后重测`);
    }
  } catch (e: any) {
    const errorMsg = e.response?.data?.message || e.message || '';
    if (errorMsg.includes('已通过')) {
      ElMessage.info('系统核验：您此前已顺利通过本次安全准入测试，已为您同步达标记录');
      try {
        const statusRes = await getSafetyStatus(taskId.value);
        if (statusRes.data) {
          examResult.value = {
            attemptId: 0,
            attemptNo: statusRes.data.examAttempts || 1,
            totalScore: statusRes.data.highestScore ?? 100,
            passingScore: statusRes.data.passingScore ?? 80,
            isPassed: statusRes.data.isPassed ?? 1,
            resultDesc: '安全准入考核达标 (已通过)',
            submitTime: statusRes.data.studyCompleteTime
          } as any;
          return;
        }
      } catch (statusErr) {
        console.error('获取安全状态失败', statusErr);
      }
    }
    ElMessage.error(errorMsg || '交卷失败');
  } finally {
    submitLoading.value = false;
  }
};

const goBack = () => {
  router.push({ path: '/safety/study', query: { taskId: taskId.value } });
};

const goToStudy = () => {
  router.push({ path: '/safety/study', query: { taskId: taskId.value } });
};

const formatType = (type: string) => {
  switch (type) {
    case 'SINGLE_CHOICE': return '单选题';
    case 'MULTIPLE_CHOICE': return '多选题';
    case 'JUDGMENT': return '判断题';
    default: return type;
  }
};

const getTypeTag = (type: string) => {
  switch (type) {
    case 'SINGLE_CHOICE': return 'primary';
    case 'MULTIPLE_CHOICE': return 'warning';
    case 'JUDGMENT': return 'success';
    default: return 'info';
  }
};

onMounted(() => {
  loadPaper();
});
</script>

<style scoped lang="scss">
.safety-exam-container {
  padding: 20px;

  .header-card {
    margin-bottom: 16px;
    border-radius: 8px;

    .header-flex {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .page-title {
        font-size: 20px;
        font-weight: 600;
        margin: 6px 0;
        color: #1f2d3d;
      }

      .page-subtitle {
        font-size: 13px;
        color: #8492a6;
        margin: 0;
      }
    }
  }

  .result-card {
    border-radius: 8px;
    padding: 20px;
  }

  .paper-card {
    border-radius: 8px;

    .paper-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 14px;
      border-bottom: 1px solid #ebeef5;
      margin-bottom: 20px;

      .paper-title {
        font-size: 16px;
        color: #303133;
      }

      .paper-meta {
        font-size: 13px;
        color: #909399;
      }
    }

    .question-item {
      padding: 16px;
      background: #fbfbfc;
      border: 1px solid #edf2f7;
      border-radius: 6px;
      margin-bottom: 16px;

      .question-stem {
        font-size: 15px;
        color: #2d3748;
        line-height: 1.6;
        margin-bottom: 12px;

        .q-index {
          font-weight: 700;
          margin-right: 6px;
        }

        .q-score {
          font-size: 13px;
          color: #e6a23c;
          margin-left: 8px;
        }
      }

      .question-options {
        padding-left: 20px;

        .option-row {
          margin: 8px 0;

          .opt-key {
            font-weight: 600;
            margin-right: 6px;
          }
        }
      }
    }

    .submit-footer {
      text-align: center;
      margin-top: 24px;
      padding: 16px;
    }
  }
}
</style>
