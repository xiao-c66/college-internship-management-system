<template>
  <div class="task-detail-container" v-loading="loading">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <el-button link :icon="Back" @click="goBack">返回任务列表</el-button>
          <h2 class="page-title">{{ task?.taskName || '实习任务详情' }}</h2>
          <p class="page-subtitle">任务编码: {{ task?.taskCode }} | 所属二级院系: {{ task?.deptName }}</p>
        </div>
        <div class="header-status">
          <el-tag :type="getStatusTag(task?.status)" size="large" effect="dark">
            {{ formatStatus(task?.status) }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="16">
        <!-- 基本属性与执行规约 -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header-title"><strong>📋 任务基本规范与进度安排</strong></div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="所属学年">{{ task?.academicYear }}</el-descriptions-item>
            <el-descriptions-item label="所属学期">第 {{ task?.semester }} 学期</el-descriptions-item>
            <el-descriptions-item label="实习组织模式">
              <el-tag size="small">{{ formatMode(task?.internshipMode) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="实习周期">
              {{ task?.startDate }} ~ {{ task?.endDate }}
            </el-descriptions-item>
            <el-descriptions-item label="周报提交频次">
              {{ task?.weeklyFrequency === 'WEEKLY' ? '每周周日截止' : '双周提交' }}
            </el-descriptions-item>
            <el-descriptions-item label="安全及格标准">
              {{ task?.safetyPassingScore }} 分 (安全测试合格基准，SAFE-011为推荐项)
            </el-descriptions-item>
            <el-descriptions-item label="重测次数上限">
              最多允许 {{ task?.safetyMaxAttempts }} 次线上测试
            </el-descriptions-item>
            <el-descriptions-item label="圈定学生总数">
              <el-tag type="success" size="small">{{ task?.enrolledStudentsCount || 0 }} 人</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 五项成绩评价权重卡片 (TASK-009) -->
        <el-card shadow="never" class="content-card" style="margin-top: 16px">
          <template #header>
            <div class="card-header-title">
              <strong>⚖️ 综合成绩五项构成权重分布 (严格合计 100.00%)</strong>
            </div>
          </template>
          <el-row :gutter="12" class="weights-cards">
            <el-col :span="4" :offset="1">
              <div class="weight-card enterprise">
                <div class="weight-val">{{ task?.weightEnterprise }}%</div>
                <div class="weight-name">企业指导评价</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="weight-card teacher">
                <div class="weight-val">{{ task?.weightTeacherProcess }}%</div>
                <div class="weight-name">校内教师过程</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="weight-card weekly">
                <div class="weight-val">{{ task?.weightWeeklyReport }}%</div>
                <div class="weight-name">周报综合评定</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="weight-card stage">
                <div class="weight-val">{{ task?.weightStageMaterial }}%</div>
                <div class="weight-name">阶段材料审核</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="weight-card summary">
                <div class="weight-val">{{ task?.weightSummary }}%</div>
                <div class="weight-name">实习总结报告</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :span="8">
        <!-- 覆盖专业与班级 -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header-title"><strong>🎓 圈定专业与班级范围</strong></div>
          </template>
          <div class="scope-group">
            <div class="scope-title">覆盖专业：</div>
            <div class="scope-tags">
              <el-tag v-for="m in task?.majorNames" :key="m" class="mr-2 mb-2" type="info">{{ m }}</el-tag>
              <span v-if="!task?.majorNames || task?.majorNames.length === 0" class="empty-tip">未指定特定专业</span>
            </div>
          </div>
          <el-divider style="margin: 12px 0" />
          <div class="scope-group">
            <div class="scope-title">覆盖班级：</div>
            <div class="scope-tags">
              <el-tag v-for="c in task?.classNames" :key="c" class="mr-2 mb-2" type="warning">{{ c }}</el-tag>
              <span v-if="!task?.classNames || task?.classNames.length === 0" class="empty-tip">未指定特定班级</span>
            </div>
          </div>
        </el-card>

        <!-- 材料清单规范 -->
        <el-card shadow="never" class="content-card" style="margin-top: 16px">
          <template #header>
            <div class="card-header-title"><strong>📑 实习材料规范清单</strong></div>
          </template>
          <ul class="material-list">
            <li>1. 校外实习三方安全协议书 (必交)</li>
            <li>2. 企业实习接收函或劳动合同盖章件 (必交)</li>
            <li>3. 人身意外伤害商业保险保单凭据 (必交)</li>
            <li>4. 实习鉴定表与用人单位盖章评价表 (归档前必交)</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <!-- 圈定学生与导师分配管理 (ASSIGN-001 ~ ASSIGN-003) -->
    <el-card shadow="never" class="content-card" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div class="card-header-title">
            <strong>👥 任务圈定学生名单与指导教师分配 (ASSIGN-001 ~ ASSIGN-003)</strong>
          </div>
          <div v-if="canManage">
            <el-button
              type="primary"
              size="small"
              :disabled="selectedStudentIds.length === 0"
              @click="openBatchAssignDialog"
            >
              批量指派指导教师 ({{ selectedStudentIds.length }})
            </el-button>
          </div>
        </div>
      </template>

      <!-- 检索与过滤 -->
      <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
        <el-input
          v-model="studentFilterKeyword"
          placeholder="搜索学号 / 姓名"
          clearable
          style="width: 200px"
          @clear="loadStudents"
          @keyup.enter="loadStudents"
        />
        <el-select
          v-model="studentFilterTeacherId"
          placeholder="指导教师筛选"
          clearable
          style="width: 200px"
          @change="loadStudents"
        >
          <el-option label="全部学生" :value="undefined" />
          <el-option
            v-for="t in availableTeachers"
            :key="t.id"
            :label="`${t.realName} (${t.assignedStudentsCount}人)`"
            :value="t.id"
          />
        </el-select>
        <el-button type="primary" size="small" @click="loadStudents">筛选</el-button>
        <el-button size="small" @click="resetStudentFilter">重置</el-button>
      </div>

      <!-- 学生表格 -->
      <el-table
        :data="taskStudents"
        v-loading="studentsLoading"
        stripe
        @selection-change="handleSelectionChange"
        style="width: 100%"
        empty-text="暂无圈定学生记录"
      >
        <el-table-column type="selection" width="45" v-if="canManage" />
        <el-table-column prop="studentNumber" label="学号" width="130" sortable />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="className" label="所属班级" width="160" />
        <el-table-column label="指导教师" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.teacherName" type="success" size="small">
              {{ row.teacherName }}
            </el-tag>
            <el-tag v-else type="info" size="small">未指派</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="安全教育进度" width="140">
          <template #default="{ row }">
            <el-tag :type="getSafetyStatusTag(row.safetyStatus)" size="small">
              {{ formatSafetyStatus(row.safetyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" v-if="canManage" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openSingleAssignDialog(row)">
              {{ row.teacherName ? '调整导师' : '指派导师' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 分配指导教师对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="assignTargetNames.length > 1 ? `批量指派指导教师 (${assignTargetNames.length}人)` : '指派指导教师'"
      width="500px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="分配对象">
          <div style="max-height: 80px; overflow-y: auto; color: #606266; font-size: 13px;">
            {{ assignTargetNames.join('、') }}
          </div>
        </el-form-item>
        <el-form-item label="选择导师" required>
          <el-select v-model="selectedTeacherId" placeholder="请选择本院指导教师" style="width: 100%">
            <el-option
              v-for="t in availableTeachers"
              :key="t.id"
              :label="`${t.realName} (${t.userNumber}) - 已带 ${t.assignedStudentsCount} 人`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleConfirmAssign">确认指派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Back } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import {
  getTaskDetail,
  getTaskStudents,
  getAvailableTeachers,
  assignTeacher,
  TaskItem,
  TaskStudentItem,
  TeacherSimpleItem
} from '@/api';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const canManage = computed(() => {
  return userStore.userType === 'DEPT_ADMIN' || userStore.userType === 'SYS_ADMIN' || userStore.userType === 'DEPT_LEADER';
});

const task = ref<TaskItem | null>(null);
const loading = ref(false);

// 学生与导师分配状态
const taskStudents = ref<TaskStudentItem[]>([]);
const availableTeachers = ref<TeacherSimpleItem[]>([]);
const studentsLoading = ref(false);
const studentFilterKeyword = ref('');
const studentFilterTeacherId = ref<number | undefined>(undefined);
const selectedStudentIds = ref<number[]>([]);

const assignDialogVisible = ref(false);
const assignSubmitting = ref(false);
const selectedTeacherId = ref<number | undefined>(undefined);
const assignTargetNames = ref<string[]>([]);
const assignTargetIds = ref<number[]>([]);

const loadDetail = async () => {
  const id = Number(route.params.id);
  if (!id) return;
  loading.value = true;
  try {
    const res = await getTaskDetail(id);
    task.value = res.data;
  } finally {
    loading.value = false;
  }
};

const loadTeachers = async () => {
  const id = Number(route.params.id);
  if (!id) return;
  try {
    const res = await getAvailableTeachers(id);
    availableTeachers.value = res.data || [];
  } catch (e) {
    console.error('获取可用教师列表失败', e);
  }
};

const loadStudents = async () => {
  const id = Number(route.params.id);
  if (!id) return;
  studentsLoading.value = true;
  try {
    const res = await getTaskStudents(id, {
      keyword: studentFilterKeyword.value ? studentFilterKeyword.value.trim() : undefined,
      teacherId: studentFilterTeacherId.value
    });
    taskStudents.value = res.data || [];
  } finally {
    studentsLoading.value = false;
  }
};

const resetStudentFilter = () => {
  studentFilterKeyword.value = '';
  studentFilterTeacherId.value = undefined;
  loadStudents();
};

const handleSelectionChange = (rows: TaskStudentItem[]) => {
  selectedStudentIds.value = rows.map(r => r.studentId);
};

const openBatchAssignDialog = () => {
  if (selectedStudentIds.value.length === 0) {
    ElMessage.warning('请先勾选需要分配导师的学生');
    return;
  }
  const selectedStudents = taskStudents.value.filter(s => selectedStudentIds.value.includes(s.studentId));
  assignTargetNames.value = selectedStudents.map(s => s.studentName);
  assignTargetIds.value = selectedStudents.map(s => s.studentId);
  selectedTeacherId.value = undefined;
  assignDialogVisible.value = true;
};

const openSingleAssignDialog = (row: TaskStudentItem) => {
  assignTargetNames.value = [row.studentName];
  assignTargetIds.value = [row.studentId];
  selectedTeacherId.value = row.teacherId;
  assignDialogVisible.value = true;
};

const handleConfirmAssign = async () => {
  if (!selectedTeacherId.value) {
    ElMessage.error('请选择指导教师');
    return;
  }
  const id = Number(route.params.id);
  if (!id) return;
  assignSubmitting.value = true;
  try {
    await assignTeacher(id, {
      teacherId: selectedTeacherId.value,
      studentIds: assignTargetIds.value
    });
    ElMessage.success('指导教师指派/调整成功');
    assignDialogVisible.value = false;
    await Promise.all([loadStudents(), loadTeachers()]);
  } finally {
    assignSubmitting.value = false;
  }
};

const formatSafetyStatus = (status?: string) => {
  switch (status) {
    case 'NOT_STARTED': return '未开始';
    case 'STUDYING': return '学习中';
    case 'PENDING_TEST': return '待测试';
    case 'PASSED': return '已通过测试';
    case 'COMPLETED': return '已签署承诺';
    default: return status || '未开始';
  }
};

const getSafetyStatusTag = (status?: string) => {
  switch (status) {
    case 'COMPLETED': return 'success';
    case 'PASSED': return 'primary';
    case 'PENDING_TEST': return 'warning';
    case 'STUDYING': return 'info';
    default: return 'danger';
  }
};

const goBack = () => {
  router.push('/task');
};

const formatMode = (mode?: string) => {
  switch (mode) {
    case 'DISTRIBUTED': return '分散实习 (自主落实)';
    case 'CENTRALIZED': return '集中实习 (基地组织)';
    case 'HYBRID': return '混合实习模式';
    default: return mode || '-';
  }
};

const formatStatus = (status?: string) => {
  switch (status) {
    case 'DRAFT': return '草稿状态';
    case 'PUBLISHED': return '已正式发布';
    case 'IN_PROGRESS': return '进行中';
    case 'ENDED': return '已结束';
    case 'ARCHIVED': return '已归档';
    default: return status || '-';
  }
};

const getStatusTag = (status?: string) => {
  switch (status) {
    case 'DRAFT': return 'info';
    case 'PUBLISHED': return 'primary';
    case 'IN_PROGRESS': return 'success';
    case 'ENDED': return 'warning';
    default: return 'info';
  }
};

onMounted(() => {
  loadDetail();
  loadTeachers();
  loadStudents();
});
</script>

<style scoped lang="scss">
.task-detail-container {
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

  .content-card {
    border-radius: 8px;

    .card-header-title {
      font-size: 15px;
      color: #303133;
    }
  }

  .weights-cards {
    margin: 10px 0;

    .weight-card {
      text-align: center;
      padding: 16px 8px;
      border-radius: 8px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;

      .weight-val {
        font-size: 22px;
        font-weight: 700;
        color: #1f2d3d;
      }

      .weight-name {
        font-size: 12px;
        color: #64748b;
        margin-top: 6px;
      }

      &.enterprise { border-top: 3px solid #409eff; }
      &.teacher { border-top: 3px solid #67c23a; }
      &.weekly { border-top: 3px solid #e6a23c; }
      &.stage { border-top: 3px solid #f56c6c; }
      &.summary { border-top: 3px solid #909399; }
    }
  }

  .scope-group {
    .scope-title {
      font-size: 13px;
      font-weight: 500;
      color: #475669;
      margin-bottom: 8px;
    }

    .empty-tip {
      font-size: 12px;
      color: #909399;
    }
  }

  .material-list {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: #475669;
    line-height: 2;
  }
}
</style>
