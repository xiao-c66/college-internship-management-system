<template>
  <div class="safety-manage-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">安全教育与准入配置管理</h2>
          <p class="page-subtitle">维护全校通用及院系专属安全教育资料、在线测试客观题库与分值标准 (SAFE-001 ~ SAFE-003)</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="tab-card">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Tab 1: 学习资料维护 (SAFE-001 / SAFE-002) -->
        <el-tab-pane label="📚 安全学习资料维护 (SAFE-001/002)" name="materials">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Plus" @click="openCreateMaterialDialog">新增安全资料</el-button>
            <el-button :icon="Refresh" @click="loadMaterials">刷新</el-button>
          </div>

          <el-table :data="materials" v-loading="materialsLoading" stripe style="width: 100%" empty-text="暂无安全学习资料">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="资料标题" min-width="240" show-overflow-tooltip />
            <el-table-column label="适用范围" width="150">
              <template #default="{ row }">
                <el-tag :type="row.taskId ? 'warning' : 'primary'" size="small">
                  {{ row.taskId ? '任务专属 (SAFE-002)' : '全校通用 (SAFE-001)' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="资料类型" width="110">
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ formatContentType(row.contentType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序权重" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewMaterial(row)">查看正文</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteMaterial(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 题库与分值维护 (SAFE-003) -->
        <el-tab-pane label="📝 安全考试题库维护 (SAFE-003)" name="questions">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Plus" @click="openCreateQuestionDialog">添加测试试题</el-button>
            <el-button :icon="Refresh" @click="loadQuestions">刷新</el-button>
          </div>

          <el-table :data="questions" v-loading="questionsLoading" stripe style="width: 100%" empty-text="暂无安全测试试题">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="题型" width="110">
              <template #default="{ row }">
                <el-tag :type="getQuestionTypeTag(row.questionType)" size="small">
                  {{ formatQuestionType(row.questionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stem" label="题干描述" min-width="260" show-overflow-tooltip />
            <el-table-column prop="score" label="分值" width="90">
              <template #default="{ row }">
                <strong>{{ row.score }} 分</strong>
              </template>
            </el-table-column>
            <el-table-column prop="correctAnswer" label="标准答案" width="110">
              <template #default="{ row }">
                <el-tag type="success" size="small">{{ row.correctAnswer || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="analysis" label="题目解析" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewQuestionDetail(row)">查看详情</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteQuestion(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增安全资料对话框 -->
    <el-dialog v-model="materialDialogVisible" title="新增安全教育学习资料" width="650px" destroy-on-close>
      <el-form :model="materialForm" label-width="100px">
        <el-form-item label="资料标题" required>
          <el-input v-model="materialForm.title" placeholder="如 高校实习人身安全与应急救援指南" />
        </el-form-item>
        <el-form-item label="归属范围">
          <el-radio-group v-model="materialForm.scope">
            <el-radio label="GLOBAL" v-if="isAdmin">全校通用资料 (SAFE-001, 限管理员)</el-radio>
            <el-radio label="TASK">任务专属资料 (SAFE-002)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联任务" v-if="materialForm.scope === 'TASK'">
          <el-select v-model="materialForm.taskId" placeholder="选择关联的实习任务" style="width: 100%">
            <el-option v-for="t in taskList" :key="t.id" :label="t.taskName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="资料类型">
          <el-select v-model="materialForm.contentType" style="width: 100%">
            <el-option value="TEXT" label="富文本图文 (TEXT)" />
            <el-option value="PDF" label="PDF 文档 (PDF)" />
            <el-option value="VIDEO" label="视频讲座 (VIDEO)" />
            <el-option value="URL" label="外部合规链接 (URL)" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文/链接内容" required>
          <el-input
            v-model="materialForm.contentBody"
            type="textarea"
            :rows="5"
            placeholder="输入资料正文内容或外链安全规程地址..."
          />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="materialForm.sortOrder" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveMaterial">提交保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增试题对话框 -->
    <el-dialog v-model="questionDialogVisible" title="添加安全准入测试试题 (SAFE-003)" width="680px" destroy-on-close>
      <el-form :model="questionForm" label-width="100px">
        <el-form-item label="关联任务">
          <el-select v-model="questionForm.taskId" placeholder="全校通用试题 (留空) 或选择特定任务" clearable style="width: 100%">
            <el-option label="全校通用试题 (不限任务)" :value="undefined" />
            <el-option v-for="t in taskList" :key="t.id" :label="t.taskName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试题题型" required>
          <el-select v-model="questionForm.questionType" style="width: 100%">
            <el-option value="SINGLE_CHOICE" label="单项选择题" />
            <el-option value="JUDGMENT" label="判断对错题" />
            <el-option value="MULTIPLE_CHOICE" label="多项选择题" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干描述" required>
          <el-input v-model="questionForm.stem" type="textarea" :rows="3" placeholder="请输入明确无歧义的安全规范测试题干..." />
        </el-form-item>
        <!-- 动态选项配置区域 -->
        <el-form-item label="试题选项" required>
          <div style="width: 100%">
            <div v-if="questionForm.questionType === 'JUDGMENT'" style="color: #909399; font-size: 13px; margin-bottom: 8px;">
              判断题固定提供两个标准选项：<strong>TRUE (正确)</strong> 与 <strong>FALSE (错误)</strong>
            </div>
            <div v-else>
              <div v-for="(opt, idx) in questionForm.options" :key="idx" style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                <el-tag effect="plain" style="width: 36px; text-align: center; font-weight: bold;">{{ opt.key }}</el-tag>
                <el-input v-model="opt.text" :placeholder="`请输入选项 ${opt.key} 的具体描述`" style="flex: 1;" />
                <el-button link type="danger" :disabled="questionForm.options.length <= 2" @click="removeOption(idx)">删除</el-button>
              </div>
              <el-button type="primary" link :icon="Plus" @click="addOption" :disabled="questionForm.options.length >= 8">
                + 添加选项
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="试题分值" required>
          <el-input-number v-model="questionForm.score" :min="5" :max="50" :step="5" />
          <span style="margin-left: 8px">分</span>
        </el-form-item>
        <el-form-item label="标准答案" required>
          <el-input v-model="questionForm.correctAnswer" placeholder="单选填选项key如 B；判断填 TRUE/FALSE；多选填如 A,B" />
        </el-form-item>
        <el-form-item label="题目解析">
          <el-input v-model="questionForm.analysis" type="textarea" :rows="2" placeholder="交卷后向学生展示的标准依据与合规解析..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveQuestion">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 查看试题详情对话框 -->
    <el-dialog v-model="questionDetailVisible" title="安全试题详情与解析" width="650px">
      <div v-if="selectedQuestion" class="question-detail-box">
        <div class="detail-row"><strong>题型：</strong><el-tag size="small">{{ formatQuestionType(selectedQuestion.questionType) }}</el-tag></div>
        <div class="detail-row"><strong>分值：</strong>{{ selectedQuestion.score }} 分</div>
        <div class="detail-row"><strong>适用范围：</strong>{{ selectedQuestion.taskId ? '任务专属' : '全校通用' }}</div>
        <div class="detail-row"><strong>题干：</strong><div class="stem-text">{{ selectedQuestion.stem }}</div></div>
        <div class="detail-row"><strong>候选选项：</strong>
          <div v-if="parsedQuestionOptions(selectedQuestion).length > 0" class="options-list">
            <div v-for="op in parsedQuestionOptions(selectedQuestion)" :key="op.key" class="option-item">
              <span class="opt-badge">{{ op.key }}.</span> {{ op.text }}
            </div>
          </div>
          <div v-else style="color: #909399;">无详细选项</div>
        </div>
        <div class="detail-row"><strong>标准答案：</strong><el-tag type="success">{{ selectedQuestion.correctAnswer }}</el-tag></div>
        <div class="detail-row"><strong>法规依据与解析：</strong>
          <div class="analysis-box">{{ selectedQuestion.analysis || '暂无解析' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="questionDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 查看资料正文对话框 -->
    <el-dialog v-model="viewDialogVisible" :title="activeMaterialTitle" width="600px">
      <div class="material-detail-body">{{ activeMaterialBody }}</div>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { Plus, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import {
  getTaskList,
  TaskItem,
  getSafetyMaterials,
  createSafetyMaterial,
  deleteSafetyMaterial,
  getSafetyQuestions,
  createSafetyQuestion,
  deleteSafetyQuestion,
  SafetyMaterial,
  SafetyQuestion,
  QuestionOption
} from '@/api';

const userStore = useUserStore();
const isAdmin = computed(() => userStore.userType === 'SYS_ADMIN');

const activeTab = ref('materials');
const materials = ref<SafetyMaterial[]>([]);
const questions = ref<SafetyQuestion[]>([]);
const taskList = ref<TaskItem[]>([]);
const materialsLoading = ref(false);
const questionsLoading = ref(false);
const submitLoading = ref(false);

const materialDialogVisible = ref(false);
const questionDialogVisible = ref(false);
const viewDialogVisible = ref(false);
const questionDetailVisible = ref(false);
const selectedQuestion = ref<SafetyQuestion | null>(null);
const activeMaterialTitle = ref('');
const activeMaterialBody = ref('');

const materialForm = reactive({
  title: '',
  scope: 'TASK',
  taskId: undefined as number | undefined,
  contentType: 'TEXT',
  contentBody: '',
  sortOrder: 10
});

const questionForm = reactive({
  taskId: undefined as number | undefined,
  questionType: 'SINGLE_CHOICE',
  stem: '',
  options: [
    { key: 'A', text: '' },
    { key: 'B', text: '' },
    { key: 'C', text: '' },
    { key: 'D', text: '' }
  ] as Array<{ key: string; text: string }>,
  score: 10,
  correctAnswer: 'B',
  analysis: ''
});

const loadTasks = async () => {
  try {
    const res = await getTaskList();
    taskList.value = res.data || [];
    if (taskList.value.length > 0 && !materialForm.taskId) {
      materialForm.taskId = taskList.value[0].id;
    }
  } catch (e) {
    console.error('获取任务列表失败', e);
  }
};

const loadMaterials = async () => {
  materialsLoading.value = true;
  try {
    const res = await getSafetyMaterials();
    materials.value = res.data || [];
  } finally {
    materialsLoading.value = false;
  }
};

const loadQuestions = async () => {
  questionsLoading.value = true;
  try {
    const res = await getSafetyQuestions();
    questions.value = res.data || [];
  } finally {
    questionsLoading.value = false;
  }
};

const openCreateMaterialDialog = () => {
  materialForm.title = '';
  materialForm.scope = isAdmin.value ? 'GLOBAL' : 'TASK';
  materialForm.taskId = taskList.value.length > 0 ? taskList.value[0].id : undefined;
  materialForm.contentType = 'TEXT';
  materialForm.contentBody = '';
  materialForm.sortOrder = 10;
  materialDialogVisible.value = true;
};

const handleSaveMaterial = async () => {
  if (!materialForm.title || !materialForm.contentBody) {
    ElMessage.error('请填写资料标题与内容');
    return;
  }
  submitLoading.value = true;
  try {
    await createSafetyMaterial({
      taskId: materialForm.scope === 'GLOBAL' ? undefined : materialForm.taskId,
      title: materialForm.title,
      contentType: materialForm.contentType,
      contentBody: materialForm.contentBody,
      sortOrder: materialForm.sortOrder,
      status: 1
    });
    ElMessage.success('安全资料已成功发布');
    materialDialogVisible.value = false;
    await loadMaterials();
  } finally {
    submitLoading.value = false;
  }
};

const handleDeleteMaterial = (row: SafetyMaterial) => {
  ElMessageBox.confirm(`确定要删除安全资料《${row.title}》吗？删除后学生端将无法学习此项规程。`, '安全资料删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSafetyMaterial(row.id);
      ElMessage.success('安全资料已成功移除');
      await loadMaterials();
    } catch (e: any) {
      ElMessage.error(e?.message || '删除安全资料失败');
    }
  }).catch(() => {});
};

const openCreateQuestionDialog = () => {
  questionForm.taskId = undefined;
  questionForm.questionType = 'SINGLE_CHOICE';
  questionForm.stem = '';
  questionForm.options = [
    { key: 'A', text: '' },
    { key: 'B', text: '' },
    { key: 'C', text: '' },
    { key: 'D', text: '' }
  ];
  questionForm.score = 10;
  questionForm.correctAnswer = 'B';
  questionForm.analysis = '';
  questionDialogVisible.value = true;
};

const addOption = () => {
  const nextChar = String.fromCharCode(65 + questionForm.options.length);
  questionForm.options.push({ key: nextChar, text: '' });
};

const removeOption = (index: number) => {
  questionForm.options.splice(index, 1);
  // 重新排列key
  questionForm.options.forEach((opt, idx) => {
    opt.key = String.fromCharCode(65 + idx);
  });
};

const handleSaveQuestion = async () => {
  if (!questionForm.stem || !questionForm.correctAnswer) {
    ElMessage.error('请填写题干描述与标准答案');
    return;
  }

  let finalOptions: QuestionOption[] = [];
  if (questionForm.questionType === 'JUDGMENT') {
    finalOptions = [
      { key: 'TRUE', text: '正确' },
      { key: 'FALSE', text: '错误' }
    ];
  } else {
    // 校验选项
    for (const opt of questionForm.options) {
      if (!opt.text || opt.text.trim() === '') {
        ElMessage.error(`请填写选项 ${opt.key} 的具体描述`);
        return;
      }
    }
    finalOptions = questionForm.options.map(o => ({ key: o.key, text: o.text.trim() }));
  }

  submitLoading.value = true;
  try {
    await createSafetyQuestion({
      taskId: questionForm.taskId || undefined,
      questionType: questionForm.questionType,
      stem: questionForm.stem.trim(),
      options: JSON.stringify(finalOptions),
      score: questionForm.score,
      correctAnswer: questionForm.correctAnswer.trim().toUpperCase(),
      analysis: questionForm.analysis ? questionForm.analysis.trim() : '',
      sortOrder: 1,
      status: 1
    });
    ElMessage.success('试题已成功添加到题库');
    questionDialogVisible.value = false;
    await loadQuestions();
  } finally {
    submitLoading.value = false;
  }
};

const handleDeleteQuestion = (row: SafetyQuestion) => {
  ElMessageBox.confirm(`确定要从题库中删除该测试试题（ID: ${row.id}）吗？`, '删除试题确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSafetyQuestion(row.id);
      ElMessage.success('试题已从题库中成功删除');
      await loadQuestions();
    } catch (e: any) {
      ElMessage.error(e?.message || '删除试题失败');
    }
  }).catch(() => {});
};

const viewQuestionDetail = (row: SafetyQuestion) => {
  selectedQuestion.value = row;
  questionDetailVisible.value = true;
};

const parsedQuestionOptions = (q: SafetyQuestion): QuestionOption[] => {
  if (!q) return [];
  if (Array.isArray(q.options)) return q.options;
  if (typeof q.options === 'string') {
    try {
      return JSON.parse(q.options);
    } catch {
      return [];
    }
  }
  return [];
};

const viewMaterial = (row: SafetyMaterial) => {
  activeMaterialTitle.value = row.title;
  activeMaterialBody.value = row.contentBody || '暂无详细文本内容';
  viewDialogVisible.value = true;
};

const formatContentType = (type: string) => {
  switch (type) {
    case 'TEXT': return '图文正文';
    case 'PDF': return 'PDF文档';
    case 'VIDEO': return '视频';
    case 'URL': return '外部链接';
    default: return type;
  }
};

const formatQuestionType = (type: string) => {
  switch (type) {
    case 'SINGLE_CHOICE': return '单选题';
    case 'MULTIPLE_CHOICE': return '多选题';
    case 'JUDGMENT': return '判断题';
    default: return type;
  }
};

const getQuestionTypeTag = (type: string) => {
  switch (type) {
    case 'SINGLE_CHOICE': return 'primary';
    case 'MULTIPLE_CHOICE': return 'warning';
    case 'JUDGMENT': return 'success';
    default: return 'info';
  }
};

onMounted(() => {
  loadTasks();
  loadMaterials();
  loadQuestions();
});
</script>

<style scoped lang="scss">
.safety-manage-container {
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
        margin: 0 0 6px 0;
        color: #1f2d3d;
      }

      .page-subtitle {
        font-size: 13px;
        color: #8492a6;
        margin: 0;
      }
    }
  }

  .tab-card {
    border-radius: 8px;

    .tab-toolbar {
      display: flex;
      gap: 12px;
      margin-bottom: 16px;
    }
  }

  .material-detail-body {
    font-size: 14px;
    color: #303133;
    line-height: 1.8;
    white-space: pre-wrap;
    background: #f8fafc;
    padding: 16px;
    border-radius: 6px;
  }

  .question-detail-box {
    font-size: 14px;
    line-height: 1.7;

    .detail-row {
      margin-bottom: 12px;

      strong {
        color: #303133;
      }

      .stem-text {
        margin-top: 4px;
        padding: 10px;
        background: #f8fafc;
        border-radius: 4px;
        color: #2c3e50;
        font-weight: 500;
      }

      .options-list {
        margin-top: 6px;
        display: flex;
        flex-direction: column;
        gap: 6px;

        .option-item {
          padding: 6px 10px;
          background: #fafafa;
          border: 1px solid #ebeef5;
          border-radius: 4px;

          .opt-badge {
            font-weight: bold;
            color: #409eff;
            margin-right: 4px;
          }
        }
      }

      .analysis-box {
        margin-top: 6px;
        padding: 10px;
        background: #f0f9eb;
        border: 1px solid #e1f3d8;
        border-radius: 4px;
        color: #67c23a;
      }
    }
  }
}
</style>
