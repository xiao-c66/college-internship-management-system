<template>
  <div class="notice-drawer-trigger">
    <!-- 顶栏铃铛与未读数字红点 -->
    <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
      <el-button
        circle
        class="bell-btn"
        :icon="Bell"
        @click="openDrawer"
        title="系统通知公告"
      />
    </el-badge>

    <!-- 侧边通知抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="系统教学通知与公告"
      size="420px"
      direction="rtl"
      :destroy-on-close="false"
    >
      <div class="drawer-header-tab">
        <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
          <el-radio-button label="ALL">全部 ({{ noticeList.length }})</el-radio-button>
          <el-radio-button label="UNREAD">未读 ({{ unreadCount }})</el-radio-button>
        </el-radio-group>
        <el-button link type="primary" size="small" :icon="Refresh" @click="fetchNotices">
          刷新
        </el-button>
      </div>

      <div class="notice-list-scroll" v-loading="loading">
        <div v-if="filteredNotices.length === 0" class="empty-state">
          <el-empty :description="activeTab === 'UNREAD' ? '暂无未读通知' : '暂无通知公告'" :image-size="80" />
        </div>

        <div
          v-for="item in filteredNotices"
          :key="item.id"
          class="notice-card"
          :class="{ 'is-unread': !item.isRead }"
          @click="showDetail(item)"
        >
          <div class="card-top">
            <el-tag
              size="small"
              :type="item.noticeType === 'ANNOUNCE' ? 'danger' : 'primary'"
              effect="light"
            >
              {{ item.noticeType === 'ANNOUNCE' ? '公告' : '通知' }}
            </el-tag>
            <span class="card-time">{{ item.publishTime }}</span>
          </div>

          <div class="card-title">
            <span v-if="!item.isRead" class="unread-dot"></span>
            {{ item.noticeTitle }}
          </div>

          <div class="card-meta">
            <span>发布人：{{ item.publisherName }}</span>
            <span v-if="item.targetScope === 'ALL'">全校</span>
            <span v-else>本院系</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="selectedNotice?.noticeTitle || '通知详情'"
      width="560px"
      append-to-body
    >
      <div v-if="selectedNotice" class="notice-detail-dialog">
        <div class="dialog-meta">
          <el-tag size="small" :type="selectedNotice.noticeType === 'ANNOUNCE' ? 'danger' : 'primary'">
            {{ selectedNotice.noticeType === 'ANNOUNCE' ? '全校公告' : '教学通知' }}
          </el-tag>
          <span>发布人：{{ selectedNotice.publisherName }}</span>
          <span>时间：{{ selectedNotice.publishTime }}</span>
        </div>
        <el-divider />

        <!-- DOMPurify 净化后的富文本 HTML 内容 -->
        <div class="dialog-content-html" v-html="sanitizedContent"></div>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Bell, Refresh } from '@element-plus/icons-vue';
import DOMPurify from 'dompurify';
import { SysNoticeVO, getNotices, getNoticeDetail } from '@/api/notice';

const drawerVisible = ref(false);
const detailDialogVisible = ref(false);
const loading = ref(false);
const activeTab = ref<'ALL' | 'UNREAD'>('ALL');
const noticeList = ref<SysNoticeVO[]>([]);
const selectedNotice = ref<SysNoticeVO | null>(null);

const unreadCount = computed(() => {
  return noticeList.value.filter(n => !n.isRead && n.status === 1).length;
});

const filteredNotices = computed(() => {
  if (activeTab.value === 'UNREAD') {
    return noticeList.value.filter(n => !n.isRead && n.status === 1);
  }
  return noticeList.value.filter(n => n.status === 1);
});

const sanitizedContent = computed(() => {
  if (!selectedNotice.value?.noticeContent) return '';
  return DOMPurify.sanitize(selectedNotice.value.noticeContent);
});

onMounted(() => {
  fetchNotices();
});

const fetchNotices = async () => {
  loading.value = true;
  try {
    const res: any = await getNotices({ status: 1 });
    if (res.code === 200) {
      noticeList.value = res.data || [];
    }
  } catch (ignored) {
  } finally {
    loading.value = false;
  }
};

const openDrawer = () => {
  drawerVisible.value = true;
  fetchNotices();
};

const handleTabChange = () => {
};

const showDetail = async (item: SysNoticeVO) => {
  try {
    const res: any = await getNoticeDetail(item.id);
    if (res.code === 200) {
      selectedNotice.value = res.data;
      item.isRead = true;
      item.readTime = res.data.readTime;
      detailDialogVisible.value = true;
    }
  } catch (error: any) {
    selectedNotice.value = item;
    detailDialogVisible.value = true;
  }
};
</script>

<style scoped>
.notice-drawer-trigger {
  display: inline-flex;
  align-items: center;
  margin-right: 14px;
}

.bell-badge :deep(.el-badge__content) {
  top: 4px;
  right: 6px;
}

.bell-btn {
  background-color: rgba(255, 255, 255, 0.9);
  border: 1px solid #e4e7ed;
  color: #606266;
  font-size: 16px;
  transition: all 0.2s ease;
}

.bell-btn:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background-color: #ecf5ff;
}

.drawer-header-tab {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.notice-list-scroll {
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

.empty-state {
  padding: 40px 0;
}

.notice-card {
  padding: 12px 14px;
  border-radius: 6px;
  background-color: #fcfcfc;
  border: 1px solid #ebeef5;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.notice-card:hover {
  border-color: #c6e2ff;
  background-color: #f0f7ff;
}

.notice-card.is-unread {
  background-color: #fff;
  border-left: 3px solid #409eff;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.card-time {
  font-size: 12px;
  color: #909399;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  line-height: 1.4;
}

.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #f56c6c;
  margin-right: 6px;
  flex-shrink: 0;
}

.card-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
}

.notice-detail-dialog {
  padding: 4px 10px;
}

.dialog-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.dialog-content-html {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  min-height: 100px;
  max-height: 400px;
  overflow-y: auto;
}
</style>
