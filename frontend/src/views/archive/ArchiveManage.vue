<template>
  <div class="archive-manage-container">
    <div class="page-header">
      <div class="header-left">
        <h2>实习电子档案归档与锁定</h2>
        <p class="subtitle">9项前置条件智能诊断雷达、全局只读写保护、七合一电子档案PDF/ZIP导出与超管限时特批解锁</p>
      </div>
      <div class="header-actions">
        <el-button v-if="!isStudent" type="primary" :icon="DocumentChecked" @click="openPrecheckDialog">
          档案归档前置核验诊断
        </el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 档案筛选 -->
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
        <el-form-item label="档案状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px" @change="loadData">
            <el-option label="已封存锁定 (写保护)" value="ARCHIVED" />
            <el-option label="特批解锁中 (限时可改)" value="SPECIAL_UNLOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 档案卷宗台账 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="archiveList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="archiveNo" label="电子档案卷宗编号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-mono">{{ row.archiveNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="归档学生" width="130">
          <template #default="{ row }">
            <div><strong>{{ row.studentName }}</strong></div>
            <div class="sub-text">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="所属院系" width="140" show-overflow-tooltip />
        <el-table-column prop="academicYear" label="学年" width="110" align="center" />
        <el-table-column label="卷宗版本" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">v{{ row.version }}.0</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="锁定状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ARCHIVED' ? 'success' : 'danger'" effect="dark">
              {{ row.status === 'ARCHIVED' ? '已归档锁定' : '特批解锁中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="archivedUserName" label="归档人" width="110" align="center">
          <template #default="{ row }">
            {{ row.archivedUserName || '系统管理员' }}
          </template>
        </el-table-column>
        <el-table-column prop="archivedTime" label="归档封存时间" width="160" />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleOpenDetail(row)">
              卷宗核验
            </el-button>
            <el-button
              link
              type="success"
              size="small"
              :loading="exportingId === row.id"
              @click="handleExportZip(row)"
            >
              导出ZIP
            </el-button>
            <el-button
              v-if="isAdmin && row.status === 'ARCHIVED'"
              link
              type="danger"
              size="small"
              @click="handleOpenUnlock(row)"
            >
              特批解锁
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 9项前置条件诊断雷达弹窗 -->
    <el-dialog v-model="precheckDialogVisible" title="实习档案归档前置9项条件诊断" width="720px">
      <el-form :inline="true" :model="precheckQuery">
        <el-form-item label="实习任务">
          <el-select v-model="precheckQuery.taskId" placeholder="选择任务" style="width: 220px">
            <el-option
              v-for="task in taskOptions"
              :key="task.id"
              :label="task.taskName"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学生学号/ID">
          <el-input-number v-model="precheckQuery.studentId" :min="1" placeholder="学生用户ID" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="diagnosing" @click="runPrecheck">
            开始诊断核验
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="precheckResult" class="precheck-container" v-loading="diagnosing">
        <div class="result-header">
          <div class="student-info">
            候选归档学生：<strong>{{ precheckResult.studentName }}</strong> ({{ precheckResult.studentNo }})
          </div>
          <div>
            <el-tag
              size="large"
              :type="precheckResult.passed ? 'success' : 'danger'"
              effect="dark"
            >
              {{ precheckResult.passed ? '9项核验全部通过 (准予归档)' : `未通过 (${precheckResult.passedCount}/${precheckResult.totalCount})` }}
            </el-tag>
          </div>
        </div>

        <el-table :data="precheckResult.checkItems" border stripe style="margin-top: 14px;">
          <el-table-column prop="code" label="指标项" width="130" />
          <el-table-column prop="name" label="前置检查要素" min-width="160" />
          <el-table-column label="核验结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'">
                {{ row.passed ? '达标' : '未达标' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="核验明细" min-width="170" show-overflow-tooltip />
          <el-table-column prop="blockReason" label="拦截原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.blockReason" class="text-danger">{{ row.blockReason }}</span>
              <span v-else class="text-success">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="precheckDialogVisible = false">关闭</el-button>
        <el-button
          type="success"
          :disabled="!precheckResult || !precheckResult.passed"
          :loading="freezing"
          @click="executeFreezeArchive"
        >
          执行终审归档并锁定
        </el-button>
      </template>
    </el-dialog>

    <!-- 档案详情与检查矩阵抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="实习档案电子卷宗核验" size="620px">
      <div v-if="currentArchive" class="drawer-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="卷宗编号" :span="2">
            <span class="font-mono">{{ currentArchive.archiveNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="归档学生">
            {{ currentArchive.studentName }} ({{ currentArchive.studentNo }})
          </el-descriptions-item>
          <el-descriptions-item label="所属院系">
            {{ currentArchive.deptName }}
          </el-descriptions-item>
          <el-descriptions-item label="所属学年">
            {{ currentArchive.academicYear }}
          </el-descriptions-item>
          <el-descriptions-item label="版本与状态">
            v{{ currentArchive.version }}.0 /
            <el-tag size="small" :type="currentArchive.status === 'ARCHIVED' ? 'success' : 'danger'">
              {{ currentArchive.status === 'ARCHIVED' ? '锁定' : '特批解锁' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="归档封存人">
            {{ currentArchive.archivedUserName || '系统管理员' }}
          </el-descriptions-item>
          <el-descriptions-item label="归档时间">
            {{ currentArchive.archivedTime }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="currentArchive.status === 'SPECIAL_UNLOCKED'" class="unlock-alert-box">
          <el-alert
            title="本卷宗当前处于特批解锁编辑窗口期"
            type="warning"
            show-icon
            :closable="false"
          >
            <div>解锁经办人：{{ currentArchive.unlockedByName }}</div>
            <div>红头批文号：<strong>{{ currentArchive.specialDocNo }}</strong></div>
            <div>特批原因：{{ currentArchive.specialUnlockReason }}</div>
            <div>解锁到期时间：{{ currentArchive.unlockExpireTime }} (到期自动重新封存)</div>
          </el-alert>
        </div>

        <h4 class="section-title">9项准入核验矩阵快照</h4>
        <div class="code-box">
          <pre>{{ formatJson(currentArchive.checkMatrixJson) }}</pre>
        </div>

        <div class="drawer-actions">
          <el-button type="success" :icon="Download" @click="handleExportZip(currentArchive)">
            下载完整电子档案包 (.zip)
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 特批解锁弹窗 -->
    <el-dialog v-model="unlockDialogVisible" title="超管特批解锁实习电子档案" width="520px">
      <el-alert
        title="重要警告：特批解锁属于重大异常修正程序。档案解锁后，该生全部过程材料与成绩将短暂开放编辑，并在48小时后自动重新锁定。所有操作将记入全量审计日志！"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 16px;"
      />
      <el-form :model="unlockForm" label-width="110px" :rules="unlockRules" ref="unlockFormRef">
        <el-form-item label="红头批文号" prop="specialDocNo">
          <el-input v-model="unlockForm.specialDocNo" placeholder="如: 教务处[2026]33号关于XX学生实习成绩更正特批" />
        </el-form-item>
        <el-form-item label="特批解锁事由" prop="specialUnlockReason">
          <el-input
            v-model="unlockForm.specialUnlockReason"
            type="textarea"
            :rows="3"
            placeholder="请详细录入主管校领导/教务处批示特批原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unlockDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="unlockSubmitting" @click="submitUnlock">
          确认特批解锁
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage, FormInstance } from 'element-plus';
import { DocumentChecked, Refresh, Search, Download } from '@element-plus/icons-vue';
import {
  getArchiveList,
  precheckArchive,
  freezeArchive,
  unlockArchive,
  exportArchiveBundle,
  ArchiveVO,
  ArchivePrecheckVO
} from '@/api/phase7';
import request from '@/utils/request';

// 用户身份
const userType = localStorage.getItem('userType') || '';
const isStudent = computed(() => userType === 'STUDENT');
const isAdmin = computed(() => userType === 'SYS_ADMIN');

// 列表与状态
const loading = ref(false);
const archiveList = ref<ArchiveVO[]>([]);
const taskOptions = ref<{ id: number; taskName: string }[]>([]);
const exportingId = ref<number | null>(null);

// 筛选条件
const filters = reactive({
  taskId: undefined as number | undefined,
  status: undefined as string | undefined
});

// 诊断弹窗
const precheckDialogVisible = ref(false);
const diagnosing = ref(false);
const freezing = ref(false);
const precheckQuery = reactive({
  taskId: undefined as number | undefined,
  studentId: 1
});
const precheckResult = ref<ArchivePrecheckVO | null>(null);

// 详情抽屉
const detailDrawerVisible = ref(false);
const currentArchive = ref<ArchiveVO | null>(null);

// 特批解锁弹窗
const unlockDialogVisible = ref(false);
const unlockSubmitting = ref(false);
const unlockFormRef = ref<FormInstance>();
const unlockForm = reactive({
  archiveId: 0,
  specialDocNo: '',
  specialUnlockReason: ''
});
const unlockRules = {
  specialDocNo: [{ required: true, message: '特批解锁必须具备红头批文号', trigger: 'blur' }],
  specialUnlockReason: [{ required: true, message: '请录入特批解锁事由', trigger: 'blur' }]
};

onMounted(() => {
  loadTasks();
  loadData();
});

const loadTasks = async () => {
  try {
    const res: any = await request.get('/tasks', { params: { size: 100 } });
    if (res.code === 200 && res.data) {
      taskOptions.value = (res.data.records || res.data).map((t: any) => ({
        id: t.id,
        taskName: t.taskName
      }));
      if (taskOptions.value.length > 0 && !precheckQuery.taskId) {
        precheckQuery.taskId = taskOptions.value[0].id;
      }
    }
  } catch (e) {
    console.error('加载任务失败', e);
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getArchiveList(filters);
    if (res.code === 200) {
      archiveList.value = res.data || [];
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载归档卷宗列表失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.taskId = undefined;
  filters.status = undefined;
  loadData();
};

const openPrecheckDialog = () => {
  precheckResult.value = null;
  precheckDialogVisible.value = true;
};

const runPrecheck = async () => {
  if (!precheckQuery.taskId || !precheckQuery.studentId) {
    ElMessage.warning('请指定实习任务及学生ID');
    return;
  }
  diagnosing.value = true;
  try {
    const res = await precheckArchive(precheckQuery.taskId, precheckQuery.studentId);
    if (res.code === 200) {
      precheckResult.value = res.data;
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '前置诊断核验失败');
  } finally {
    diagnosing.value = false;
  }
};

const executeFreezeArchive = async () => {
  if (!precheckQuery.taskId || !precheckQuery.studentId) return;
  freezing.value = true;
  try {
    const res = await freezeArchive(precheckQuery.taskId, precheckQuery.studentId);
    if (res.code === 200) {
      ElMessage.success('实习电子档案已生成并执行全局只读写保护锁定！');
      precheckDialogVisible.value = false;
      loadData();
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '终审归档锁定失败');
  } finally {
    freezing.value = false;
  }
};

const handleOpenDetail = (row: ArchiveVO) => {
  currentArchive.value = row;
  detailDrawerVisible.value = true;
};

const handleExportZip = async (row: ArchiveVO) => {
  exportingId.value = row.id;
  try {
    const res: any = await exportArchiveBundle(row.id);
    // 处理 blob 下载
    const blob = new Blob([res], { type: 'application/zip' });
    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(blob);
    link.download = `${row.archiveNo}.zip`;
    link.click();
    window.URL.revokeObjectURL(link.href);
    ElMessage.success('电子档案压缩包导出成功');
  } catch (e: any) {
    ElMessage.error(e?.message || '导出归档包失败');
  } finally {
    exportingId.value = null;
  }
};

const handleOpenUnlock = (row: ArchiveVO) => {
  unlockForm.archiveId = row.id;
  unlockForm.specialDocNo = '';
  unlockForm.specialUnlockReason = '';
  unlockDialogVisible.value = true;
};

const submitUnlock = async () => {
  if (!unlockFormRef.value) return;
  await unlockFormRef.value.validate(async (valid) => {
    if (!valid) return;
    unlockSubmitting.value = true;
    try {
      const res = await unlockArchive(unlockForm.archiveId, {
        specialDocNo: unlockForm.specialDocNo,
        specialUnlockReason: unlockForm.specialUnlockReason
      });
      if (res.code === 200) {
        ElMessage.success('特批解锁成功，编辑窗口已开启（48小时后自动复锁）');
        unlockDialogVisible.value = false;
        loadData();
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '特批解锁操作失败');
    } finally {
      unlockSubmitting.value = false;
    }
  });
};

const formatJson = (jsonStr: string) => {
  if (!jsonStr) return '无矩阵数据';
  try {
    const parsed = JSON.parse(jsonStr);
    return JSON.stringify(parsed, null, 2);
  } catch (e) {
    return jsonStr;
  }
};
</script>

<style scoped>
.archive-manage-container {
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

.filter-card, .table-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.font-mono {
  font-family: Consolas, Monaco, monospace;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.student-info {
  font-size: 14px;
  color: #334155;
}

.text-danger { color: #f56c6c; }
.text-success { color: #67c23a; }

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
  max-height: 220px;
  overflow: auto;
}

.code-box pre {
  margin: 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #334155;
}

.unlock-alert-box {
  margin-top: 16px;
}

.drawer-actions {
  margin-top: 24px;
  text-align: center;
}
</style>
