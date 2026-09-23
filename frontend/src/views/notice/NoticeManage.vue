<template>
  <div class="notice-manage-container">
    <div class="page-header">
      <div class="header-left">
        <h2>教学通知与公共公告管理</h2>
        <p class="subtitle">教学事务通知发布、按院系/全校定向广播、业务防重机制与服务端/客户端双重XSS安全防御</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openPublishDialog">
          发布新通知公告
        </el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 筛选面板 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态筛选">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="loadData">
            <el-option label="正常发布" :value="1" />
            <el-option label="已撤回/关闭" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="公告类型">
          <el-select v-model="filters.noticeType" placeholder="全部类型" clearable style="width: 140px" @change="loadData">
            <el-option label="教学通知 (NOTICE)" value="NOTICE" />
            <el-option label="全校公告 (ANNOUNCE)" value="ANNOUNCE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 通知列表卡片 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="noticeList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="dedupKey" label="业务防重键" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" type="info" class="font-mono">{{ row.dedupKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="noticeTitle" label="公告标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="viewDetail(row)">
              <strong>{{ row.noticeTitle }}</strong>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="noticeType" label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.noticeType === 'ANNOUNCE' ? 'danger' : 'primary'" effect="light">
              {{ row.noticeType === 'ANNOUNCE' ? '全校公告' : '教学通知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetScope" label="发布范围" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.targetScope === 'ALL'" type="success" effect="plain">全校师生</el-tag>
            <el-tag v-else type="warning" effect="plain">本院系 (ID:{{ row.targetDeptId || '-' }})</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常发布' : '已撤回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="120" align="center" />
        <el-table-column prop="publishTime" label="发布时间" width="170" align="center" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">
              查阅详情
            </el-button>
            <el-button link type="warning" size="small" @click="openEditDialog(row)" :disabled="!canModify(row)">
              编辑
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              @click="toggleNoticeStatus(row)"
              :disabled="!canModify(row)"
            >
              {{ row.status === 1 ? '撤回' : '重新发布' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 发布通知弹窗 -->
    <el-dialog v-model="publishDialogVisible" title="人工发布教学通知/公告 (API-114)" width="680px" destroy-on-close>
      <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="110px">
        <el-form-item label="业务防重键" prop="dedupKey">
          <el-input v-model="publishForm.dedupKey" placeholder="如 MANUAL_DEPT1_P8_NOTIFY_2026">
            <template #append>
              <el-button @click="generateDedupKey">自动生成</el-button>
            </template>
          </el-input>
          <div class="form-tip">数据库唯一索引 uk_notice_dedup 物理防重，严禁重复提交</div>
        </el-form-item>

        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="publishForm.noticeTitle" placeholder="请输入通知公告标题" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="公告类型" prop="noticeType">
          <el-radio-group v-model="publishForm.noticeType">
            <el-radio label="NOTICE">教学通知</el-radio>
            <el-radio label="ANNOUNCE">学校公告</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="发布范围" prop="targetScope">
          <el-radio-group v-model="publishForm.targetScope" :disabled="isDeptAdmin">
            <el-radio label="ALL" :disabled="isDeptAdmin">全校师生 (ALL)</el-radio>
            <el-radio label="DEPT">本院系 (DEPT)</el-radio>
          </el-radio-group>
          <div v-if="isDeptAdmin" class="form-tip text-warning">
            院系负责人权限仅允许发布本院系通知 (当前院系ID: {{ userStore.deptId || '-' }})
          </div>
        </el-form-item>

        <el-form-item label="公告正文" prop="noticeContent">
          <el-input
            v-model="publishForm.noticeContent"
            type="textarea"
            :rows="6"
            placeholder="支持HTML富文本内容。系统在服务端与客户端执行 Jsoup / DOMPurify 双重净化拦截 XSS 恶意脚本。"
          />
        </el-form-item>

        <!-- 实时安全预览 -->
        <el-form-item label="实时安全预览">
          <div class="preview-box" v-html="sanitizedPreviewContent"></div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handlePublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 编辑通知弹窗 -->
    <el-dialog v-model="editDialogVisible" title="修改通知公告 (API-115)" width="680px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="editForm.noticeTitle" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-radio-group v-model="editForm.noticeType">
            <el-radio label="NOTICE">教学通知</el-radio>
            <el-radio label="ANNOUNCE">学校公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发布状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio :label="1">正常发布</el-radio>
            <el-radio :label="0">撤回/关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告正文" prop="noticeContent">
          <el-input
            v-model="editForm.noticeContent"
            type="textarea"
            :rows="6"
            placeholder="支持HTML富文本内容"
          />
        </el-form-item>
        <el-form-item label="安全渲染预览">
          <div class="preview-box" v-html="sanitizedEditContent"></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleEditSubmit">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="通知公告详情" size="50%">
      <div v-if="currentNotice" class="detail-container">
        <div class="detail-header">
          <h2 class="detail-title">{{ currentNotice.noticeTitle }}</h2>
          <div class="detail-meta">
            <el-tag :type="currentNotice.noticeType === 'ANNOUNCE' ? 'danger' : 'primary'" size="small">
              {{ currentNotice.noticeType === 'ANNOUNCE' ? '全校公告' : '教学通知' }}
            </el-tag>
            <span class="meta-item">发布人：{{ currentNotice.publisherName }}</span>
            <span class="meta-item">发布时间：{{ currentNotice.publishTime }}</span>
            <span class="meta-item">范围：{{ currentNotice.targetScope === 'ALL' ? '全校' : '院系' }}</span>
          </div>
        </div>

        <el-divider />

        <!-- DOMPurify 防 XSS 安全净化渲染 -->
        <div class="detail-body-html" v-html="sanitizedDetailContent"></div>

        <el-divider />

        <div class="detail-footer">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="业务防重键">{{ currentNotice.dedupKey }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="currentNotice.status === 1 ? 'success' : 'info'" size="small">
                {{ currentNotice.status === 1 ? '正常发布' : '已撤回' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已读状态">
              <el-tag :type="currentNotice.isRead ? 'success' : 'info'" size="small">
                {{ currentNotice.isRead ? '已读' : '未读' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="查阅时间">
              {{ currentNotice.readTime || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import DOMPurify from 'dompurify';
import { useUserStore } from '@/store/modules/user';
import {
  SysNoticeVO,
  getNotices,
  getNoticeDetail,
  createNotice,
  updateNotice
} from '@/api/notice';

const userStore = useUserStore();
const isAdmin = computed(() => userStore.userType === 'SYS_ADMIN');
const isDeptAdmin = computed(() => userStore.userType === 'DEPT_ADMIN');

// 列表与加载
const loading = ref(false);
const noticeList = ref<SysNoticeVO[]>([]);
const filters = reactive({
  status: undefined as number | undefined,
  noticeType: undefined as string | undefined
});

// 发布表单
const publishDialogVisible = ref(false);
const submitting = ref(false);
const publishFormRef = ref<FormInstance>();
const publishForm = reactive({
  dedupKey: '',
  noticeTitle: '',
  noticeType: 'NOTICE',
  targetScope: isDeptAdmin.value ? 'DEPT' : 'ALL',
  noticeContent: ''
});

const publishRules = {
  dedupKey: [{ required: true, message: '请输入业务防重键', trigger: 'blur' }],
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  targetScope: [{ required: true, message: '请选择发布范围', trigger: 'change' }],
  noticeContent: [{ required: true, message: '请输入公告正文', trigger: 'blur' }]
};

// 实时预览 DOMPurify 净化
const sanitizedPreviewContent = computed(() => {
  return DOMPurify.sanitize(publishForm.noticeContent || '<p style="color:#909399;">暂无预览内容</p>');
});

// 编辑表单
const editDialogVisible = ref(false);
const editFormRef = ref<FormInstance>();
const editForm = reactive({
  id: 0,
  noticeTitle: '',
  noticeType: 'NOTICE',
  noticeContent: '',
  status: 1
});

const editRules = {
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  noticeContent: [{ required: true, message: '请输入公告正文', trigger: 'blur' }]
};

const sanitizedEditContent = computed(() => {
  return DOMPurify.sanitize(editForm.noticeContent || '<p style="color:#909399;">暂无预览内容</p>');
});

// 详情抽屉
const detailDrawerVisible = ref(false);
const currentNotice = ref<SysNoticeVO | null>(null);
const sanitizedDetailContent = computed(() => {
  if (!currentNotice.value?.noticeContent) return '';
  return DOMPurify.sanitize(currentNotice.value.noticeContent);
});

onMounted(() => {
  loadData();
});

const loadData = async () => {
  loading.value = true;
  try {
    const res: any = await getNotices({
      status: filters.status,
      noticeType: filters.noticeType
    });
    if (res.code === 200) {
      noticeList.value = res.data || [];
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取通知公告列表失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.status = undefined;
  filters.noticeType = undefined;
  loadData();
};

const canModify = (row: SysNoticeVO) => {
  if (isAdmin.value) return true;
  if (isDeptAdmin.value) {
    if (row.publisherId === userStore.userId) return true;
    if (row.targetScope === 'DEPT' && row.targetDeptId === userStore.deptId) return true;
  }
  return false;
};

const generateDedupKey = () => {
  const prefix = isDeptAdmin.value ? `MANUAL_DEPT${userStore.deptId || '0'}` : 'MANUAL_ALL';
  publishForm.dedupKey = `${prefix}_NOTICE_${Date.now()}`;
};

const openPublishDialog = () => {
  publishForm.dedupKey = '';
  publishForm.noticeTitle = '';
  publishForm.noticeType = 'NOTICE';
  publishForm.targetScope = isDeptAdmin.value ? 'DEPT' : 'ALL';
  publishForm.noticeContent = '';
  generateDedupKey();
  publishDialogVisible.value = true;
};

const handlePublish = async () => {
  if (!publishFormRef.value) return;
  await publishFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      const res: any = await createNotice({
        dedupKey: publishForm.dedupKey.trim(),
        noticeTitle: publishForm.noticeTitle.trim(),
        noticeType: publishForm.noticeType,
        targetScope: isDeptAdmin.value ? 'DEPT' : publishForm.targetScope,
        targetDeptId: isDeptAdmin.value ? (userStore.deptId || 1) : null,
        noticeContent: publishForm.noticeContent
      });
      if (res.code === 200) {
        ElMessage.success('通知公告发布成功');
        publishDialogVisible.value = false;
        loadData();
      }
    } catch (error: any) {
      ElMessage.error(error.message || '发布通知失败');
    } finally {
      submitting.value = false;
    }
  });
};

const openEditDialog = (row: SysNoticeVO) => {
  editForm.id = row.id;
  editForm.noticeTitle = row.noticeTitle;
  editForm.noticeType = row.noticeType;
  editForm.noticeContent = row.noticeContent;
  editForm.status = row.status;
  editDialogVisible.value = true;
};

const handleEditSubmit = async () => {
  if (!editFormRef.value) return;
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      const res: any = await updateNotice(editForm.id, {
        noticeTitle: editForm.noticeTitle.trim(),
        noticeType: editForm.noticeType,
        noticeContent: editForm.noticeContent,
        status: editForm.status
      });
      if (res.code === 200) {
        ElMessage.success('通知公告更新成功');
        editDialogVisible.value = false;
        loadData();
      }
    } catch (error: any) {
      ElMessage.error(error.message || '更新通知失败');
    } finally {
      submitting.value = false;
    }
  });
};

const toggleNoticeStatus = async (row: SysNoticeVO) => {
  const newStatus = row.status === 1 ? 0 : 1;
  const actionText = newStatus === 1 ? '重新发布' : '撤回';
  try {
    await ElMessageBox.confirm(`确认要${actionText}公告【${row.noticeTitle}】吗？`, '操作提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    const res: any = await updateNotice(row.id, { status: newStatus });
    if (res.code === 200) {
      ElMessage.success(`通知已成功${actionText}`);
      loadData();
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}操作失败`);
    }
  }
};

const viewDetail = async (row: SysNoticeVO) => {
  try {
    const res: any = await getNoticeDetail(row.id);
    if (res.code === 200) {
      currentNotice.value = res.data;
      detailDrawerVisible.value = true;
      // 本地状态同步已读
      row.isRead = true;
      row.readTime = res.data.readTime;
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取通知详情失败');
  }
};
</script>

<style scoped>
.notice-manage-container {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 22px;
  color: #1d2129;
  margin: 0 0 6px 0;
}

.subtitle {
  color: #86909c;
  font-size: 13px;
  margin: 0;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.font-mono {
  font-family: 'Consolas', 'Courier New', monospace;
}

.form-tip {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
}

.text-warning {
  color: #e6a23c;
}

.preview-box {
  width: 100%;
  min-height: 80px;
  max-height: 160px;
  overflow-y: auto;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  padding: 10px 14px;
  background-color: #fafafa;
  font-size: 13px;
  line-height: 1.6;
}

.detail-container {
  padding: 10px 20px;
}

.detail-header {
  margin-bottom: 16px;
}

.detail-title {
  font-size: 20px;
  color: #1d2129;
  margin: 0 0 12px 0;
}

.detail-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  color: #86909c;
  font-size: 13px;
}

.detail-body-html {
  padding: 16px 0;
  line-height: 1.8;
  color: #303133;
  font-size: 14px;
  min-height: 160px;
}

.detail-footer {
  margin-top: 20px;
}
</style>
