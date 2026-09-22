<template>
  <div class="audit-center-container">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">实习申报审批中心 (REVIEW-001 ~ REVIEW-006)</h2>
          <p class="page-subtitle">指导教师初审与二级院系终审复核工作台 | 退回修改意见强制不少于5个字符且完整留痕</p>
        </div>
        <div class="header-badge">
          <el-tag :type="roleBadgeType" size="large" effect="plain">
            当前权限: {{ roleTitle }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 筛选过滤栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="申报状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 180px" @change="loadList">
            <el-option label="全部" value="" />
            <el-option label="待教师初审 (SUBMITTED)" value="SUBMITTED" />
            <el-option label="教师初审通过 (TEACHER_APPROVED)" value="TEACHER_APPROVED" />
            <el-option label="教师初审退回 (TEACHER_REJECTED)" value="TEACHER_REJECTED" />
            <el-option label="终审通过已锁定 (APPROVED)" value="APPROVED" />
            <el-option label="院系终审退回 (DEPT_REJECTED)" value="DEPT_REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 申报待审与记录列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="applies" v-loading="loading" stripe style="width: 100%" empty-text="当前暂无可审核的实习申报单据">
        <el-table-column prop="id" label="申报ID" width="80" />
        <el-table-column prop="studentName" label="学生姓名" width="130">
          <template #default="{ row }">
            <strong>{{ row.studentName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="studentNumber" label="学号" width="130" />
        <el-table-column prop="className" label="所属班级" width="140" />
        <el-table-column prop="companyName" label="实习用人单位" min-width="180" show-overflow-tooltip />
        <el-table-column prop="jobPosition" label="实习岗位" width="150" show-overflow-tooltip />
        <el-table-column label="实习起止" width="200">
          <template #default="{ row }">
            {{ row.startDate }} ~ {{ row.endDate }}
          </template>
        </el-table-column>
        <el-table-column label="当前状态" width="140">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.applyStatus)">
              {{ formatStatus(row.applyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              v-if="canAuditRow(row)"
              @click="openAuditDrawer(row)"
            >
              执行审核
            </el-button>
            <el-button
              type="info"
              link
              size="small"
              v-else
              @click="openAuditDrawer(row, true)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审核操作抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="isViewOnly ? '实习申报详细内容与审批历史' : '实习申报业务审批流转'"
      size="620px"
      destroy-on-close
    >
      <div v-if="selectedApply" class="drawer-content">
        <!-- 申报基本资料 -->
        <h4 class="section-title">学生及申报企业基础信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="学生姓名">{{ selectedApply.studentName }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ selectedApply.studentNumber }}</el-descriptions-item>
          <el-descriptions-item label="所属院系">{{ selectedApply.deptName }}</el-descriptions-item>
          <el-descriptions-item label="所属班级">{{ selectedApply.className }}</el-descriptions-item>
          <el-descriptions-item label="用人单位" :span="2">{{ selectedApply.companyName }}</el-descriptions-item>
          <el-descriptions-item label="实习岗位">{{ selectedApply.jobPosition }}</el-descriptions-item>
          <el-descriptions-item label="组织模式">{{ selectedApply.internshipMode }}</el-descriptions-item>
          <el-descriptions-item label="工作地点" :span="2">{{ selectedApply.jobAddress }}</el-descriptions-item>
          <el-descriptions-item label="单位联系人">{{ selectedApply.companyContactPerson }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ selectedApply.companyContactPhone }}</el-descriptions-item>
          <el-descriptions-item label="实习起止" :span="2">{{ selectedApply.startDate }} 至 {{ selectedApply.endDate }}</el-descriptions-item>
          <el-descriptions-item label="工作职责" :span="2">{{ selectedApply.jobDuties || '无' }}</el-descriptions-item>
          <el-descriptions-item label="三方协议" :span="2">
            <el-link v-if="selectedApply.agreementFileUrl" type="primary" :href="selectedApply.agreementFileUrl" target="_blank">
              📄 查看盖章协议文件
            </el-link>
            <span v-else>未上传</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 审核操作表单 (仅在具备审核权限时展示) -->
        <div v-if="!isViewOnly" class="audit-action-box mt-4">
          <h4 class="section-title">审核处理意见</h4>
          <el-form label-position="top">
            <el-form-item label="审核决定" required>
              <el-radio-group v-model="auditAction">
                <el-radio label="APPROVED">审核通过 (APPROVED)</el-radio>
                <el-radio label="REJECTED">退回修改 (REJECTED)</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item
              :label="auditAction === 'REJECTED' ? '退回修改原因 (强制不少于5个字符 - REVIEW-003)' : '审核意见备注'"
              required
            >
              <el-input
                v-model="auditOpinion"
                type="textarea"
                :rows="4"
                :placeholder="auditAction === 'REJECTED' ? '请详细说明材料不合规项或修改要求（不得少于5个字）...' : '如：同意该生校外实习申报'"
              />
              <div v-if="auditAction === 'REJECTED'" class="opinion-char-count" :class="{ error: isOpinionTooShort }">
                当前字符数：{{ auditOpinion.trim().length }} / 5 字及以上
                <span v-if="isOpinionTooShort" class="error-tip">✖ 退回必须填写至少5个字符原因！</span>
                <span v-else class="ok-tip">✔ 符合要求</span>
              </div>
            </el-form-item>

            <div class="submit-action">
              <el-button
                type="primary"
                size="large"
                style="width: 100%"
                :loading="auditSubmitLoading"
                :disabled="auditAction === 'REJECTED' && isOpinionTooShort"
                @click="handleSubmitAudit"
              >
                确认提交审核流转结果
              </el-button>
            </div>
          </el-form>
        </div>

        <!-- 历史审批流转轨迹 -->
        <div class="history-section mt-4">
          <h4 class="section-title">审批轨迹历史记录 (完整留痕快照)</h4>
          <div v-if="!selectedApply.auditHistories || selectedApply.auditHistories.length === 0" class="text-gray-400">
            暂无流转历史
          </div>
          <el-timeline v-else>
            <el-timeline-item
              v-for="h in selectedApply.auditHistories"
              :key="h.id"
              :type="h.auditAction === 'APPROVED' ? 'success' : 'danger'"
              :timestamp="h.auditTime"
            >
              <div class="timeline-title">
                <strong>{{ h.nodeName === 'TEACHER_AUDIT' ? '教师初审' : '院系终审' }}</strong>
                <el-tag size="small" :type="h.auditAction === 'APPROVED' ? 'success' : 'danger'" class="ml-2">
                  {{ h.auditAction === 'APPROVED' ? '通过' : '退回' }}
                </el-tag>
              </div>
              <div class="timeline-auditor">审核人：{{ h.auditorName }} ({{ h.auditorRole }})</div>
              <div class="timeline-opinion">意见：{{ h.auditOpinion }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Search, Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/modules/user';
import { listApplies, auditApply, ApplyItem } from '@/api';

const userStore = useUserStore();

const loading = ref(false);
const auditSubmitLoading = ref(false);
const applies = ref<ApplyItem[]>([]);
const filterStatus = ref('');

const drawerVisible = ref(false);
const isViewOnly = ref(false);
const selectedApply = ref<ApplyItem | null>(null);

const auditAction = ref<'APPROVED' | 'REJECTED'>('APPROVED');
const auditOpinion = ref('同意该生校外实习申报');

const roleTitle = computed(() => {
  switch (userStore.userType) {
    case 'TEACHER': return '指导教师 (承担教师初审)';
    case 'DEPT_ADMIN': return '院系实习负责人 (承担终审复核)';
    case 'SYS_ADMIN': return '学校管理员 (全校终审管理)';
    default: return userStore.userType;
  }
});

const roleBadgeType = computed(() => {
  return userStore.userType === 'TEACHER' ? 'primary' : 'warning';
});

const isOpinionTooShort = computed(() => {
  return auditOpinion.value.trim().length < 5;
});

const loadList = async () => {
  loading.value = true;
  try {
    const res = await listApplies({
      status: filterStatus.value || undefined
    });
    applies.value = res.data || [];
  } finally {
    loading.value = false;
  }
};

const resetFilter = () => {
  filterStatus.value = '';
  loadList();
};

const canAuditRow = (row: ApplyItem) => {
  const role = userStore.userType;
  if (role === 'TEACHER') {
    return row.applyStatus === 'SUBMITTED';
  }
  if (role === 'DEPT_ADMIN' || role === 'SYS_ADMIN') {
    return row.applyStatus === 'TEACHER_APPROVED';
  }
  return false;
};

const openAuditDrawer = (row: ApplyItem, viewOnly = false) => {
  selectedApply.value = row;
  isViewOnly.value = viewOnly;
  auditAction.value = 'APPROVED';
  auditOpinion.value = userStore.userType === 'TEACHER'
    ? '指导教师初审通过，同意申报'
    : '二级院系终审复核通过，同意其实习安排';
  drawerVisible.value = true;
};

const handleSubmitAudit = async () => {
  if (!selectedApply.value) return;

  if (auditAction.value === 'REJECTED' && isOpinionTooShort.value) {
    ElMessage.error('审核退回原因必须填写且不得少于5个字符！');
    return;
  }

  auditSubmitLoading.value = true;
  try {
    await auditApply(selectedApply.value.id, {
      action: auditAction.value,
      opinion: auditOpinion.value.trim()
    });
    ElMessage.success('审核处理完成！');
    drawerVisible.value = false;
    loadList();
  } finally {
    auditSubmitLoading.value = false;
  }
};

const formatStatus = (status?: string) => {
  switch (status) {
    case 'DRAFT': return '草稿';
    case 'SUBMITTED': return '待教师初审';
    case 'TEACHER_APPROVED': return '待院系终审';
    case 'TEACHER_REJECTED': return '教师初审退回';
    case 'APPROVED': return '终审通过 (已生效)';
    case 'DEPT_REJECTED': return '院系终审退回';
    default: return status || '-';
  }
};

const getStatusTagType = (status?: string) => {
  switch (status) {
    case 'APPROVED': return 'success';
    case 'SUBMITTED':
    case 'TEACHER_APPROVED': return 'warning';
    case 'TEACHER_REJECTED':
    case 'DEPT_REJECTED': return 'danger';
    default: return 'info';
  }
};

onMounted(() => {
  loadList();
});
</script>

<style scoped lang="scss">
.audit-center-container {
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

  .filter-card, .table-card {
    margin-bottom: 16px;
    border-radius: 8px;
  }

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin: 16px 0 10px 0;
    padding-left: 8px;
    border-left: 3px solid #409eff;
  }

  .audit-action-box {
    background: #fbfbfc;
    padding: 16px;
    border: 1px solid #ebeef5;
    border-radius: 6px;

    .opinion-char-count {
      font-size: 12px;
      margin-top: 6px;
      color: #909399;

      &.error { color: #f56c6c; }
      .error-tip { font-weight: 600; margin-left: 8px; }
      .ok-tip { color: #67c23a; font-weight: 600; margin-left: 8px; }
    }
  }

  .mt-4 { margin-top: 20px; }

  .timeline-title {
    font-size: 14px;
    margin-bottom: 4px;
  }

  .timeline-auditor {
    font-size: 12px;
    color: #606266;
    margin-bottom: 4px;
  }

  .timeline-opinion {
    font-size: 13px;
    color: #303133;
    background: #f8fafc;
    padding: 6px 10px;
    border-radius: 4px;
  }
}
</style>
