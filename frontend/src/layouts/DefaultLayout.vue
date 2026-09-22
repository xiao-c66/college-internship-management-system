<template>
  <div class="layout-container">
    <!-- 顶部导航栏 -->
    <header class="layout-header">
      <div class="header-left">
        <div class="logo-box">
          <el-icon><School /></el-icon>
        </div>
        <div class="title-group">
          <span class="main-title">高校实习全过程管理系统</span>
          <span class="sub-title">College Internship Management System</span>
        </div>
      </div>

      <div class="header-right">
        <el-tag :type="roleTagType" effect="plain" class="role-badge">
          {{ roleDisplayName }}
        </el-tag>
        <span class="dept-badge" v-if="userStore.deptName">
          {{ userStore.deptName }}
        </span>
        <span class="user-greeting">欢迎，<strong>{{ userStore.realName || userStore.username }}</strong></span>
        <el-button type="danger" link @click="handleLogout" :loading="logoutLoading">
          <el-icon><SwitchButton /></el-icon> 退出登录
        </el-button>
      </div>
    </header>

    <!-- 下方主体：侧边栏 + 内容区 -->
    <div class="layout-body">
      <aside class="layout-sidebar">
        <el-menu
          :default-active="activeRoute"
          class="sidebar-menu"
          router
        >
          <div class="menu-group-title">当前角色工作台</div>

          <el-menu-item
            v-if="hasRole('STUDENT') || isAdmin"
            index="/dashboard/student"
          >
            <el-icon><User /></el-icon>
            <template #title>学生端工作台</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || isAdmin"
            index="/dashboard/teacher"
          >
            <el-icon><Reading /></el-icon>
            <template #title>指导教师工作台</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('DEPT_ADMIN') || isAdmin"
            index="/dashboard/dept"
          >
            <el-icon><Management /></el-icon>
            <template #title>院系负责人工作台</template>
          </el-menu-item>

          <el-menu-item
            v-if="isAdmin"
            index="/dashboard/admin"
          >
            <el-icon><Setting /></el-icon>
            <template #title>学校管理员工作台</template>
          </el-menu-item>

          <div class="menu-divider"></div>
          <div class="menu-group-title">阶段5 业务功能模块</div>

          <el-menu-item
            v-if="hasRole('DEPT_ADMIN') || isAdmin || hasRole('TEACHER')"
            index="/task"
          >
            <el-icon><List /></el-icon>
            <template #title>实习任务管理</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('DEPT_ADMIN') || isAdmin"
            index="/safety/manage"
          >
            <el-icon><DocumentChecked /></el-icon>
            <template #title>安全教育与题库配置</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('STUDENT') || isAdmin"
            index="/safety/study"
          >
            <el-icon><Checked /></el-icon>
            <template #title>安全学习与承诺签署</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('STUDENT') || isAdmin"
            index="/apply"
          >
            <el-icon><EditPen /></el-icon>
            <template #title>实习申报填报 (APPLY-009)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/audit"
          >
            <el-icon><Stamp /></el-icon>
            <template #title>实习申报审批中心</template>
          </el-menu-item>

          <div class="menu-divider"></div>
          <div class="menu-group-title">阶段6 过程管理与周报批阅</div>

          <el-menu-item
            v-if="hasRole('STUDENT') || isAdmin"
            index="/weekly/my"
          >
            <el-icon><Notebook /></el-icon>
            <template #title>我的实习周报 (API-056)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/weekly/review"
          >
            <el-icon><ChatDotSquare /></el-icon>
            <template #title>周报审阅工作台 (API-059)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('DEPT_ADMIN') || isAdmin || hasRole('TEACHER')"
            index="/weekly/monitor"
          >
            <el-icon><DataAnalysis /></el-icon>
            <template #title>周报监控看板</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || hasRole('STUDENT') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/guidance/manage"
          >
            <el-icon><Guide /></el-icon>
            <template #title>过程指导走访台账 (API-066)</template>
          </el-menu-item>

          <div class="menu-divider"></div>
          <div class="menu-group-title">阶段7 评定、预警与电子归档</div>

          <el-menu-item
            v-if="hasRole('STUDENT') || hasRole('TEACHER') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/material/manage"
          >
            <el-icon><Files /></el-icon>
            <template #title>阶段材料与总结 (API-060~064)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || hasRole('STUDENT') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/inspect/rectify"
          >
            <el-icon><DocumentChecked /></el-icon>
            <template #title>中期检查与整改 (API-074~081)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('STUDENT') || isAdmin"
            index="/warn/student"
          >
            <el-icon><Bell /></el-icon>
            <template #title>学生预警中心 (API-089)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('TEACHER') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/warn/tickets"
          >
            <el-icon><Warning /></el-icon>
            <template #title>预警工单中心 (API-084~090)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('STUDENT') || hasRole('TEACHER') || hasRole('DEPT_ADMIN') || isAdmin"
            index="/score/manage"
          >
            <el-icon><Medal /></el-icon>
            <template #title>五维成绩评定 (API-091~097)</template>
          </el-menu-item>

          <el-menu-item
            v-if="hasRole('DEPT_ADMIN') || isAdmin"
            index="/archive/manage"
          >
            <el-icon><FolderChecked /></el-icon>
            <template #title>电子档案归档 (API-098~102)</template>
          </el-menu-item>

          <div class="menu-divider"></div>
          <div class="menu-group-title">系统工具与安全</div>

          <el-menu-item index="/dev-diag">
            <el-icon><Monitor /></el-icon>
            <template #title>开发与环境诊断</template>
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="layout-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  School,
  User,
  Reading,
  Management,
  Setting,
  Monitor,
  SwitchButton,
  List,
  DocumentChecked,
  Checked,
  EditPen,
  Stamp,
  Notebook,
  ChatDotSquare,
  DataAnalysis,
  Guide,
  Files,
  Warning,
  Bell,
  Medal,
  FolderChecked
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/store/modules/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const logoutLoading = ref(false);

const activeRoute = computed(() => route.path);

const isAdmin = computed(() => userStore.userType === 'SYS_ADMIN');

const hasRole = (role: string) => {
  return userStore.userType === role;
};

const roleDisplayName = computed(() => {
  switch (userStore.userType) {
    case 'STUDENT': return '学生角色 [STUDENT]';
    case 'TEACHER': return '指导教师 [TEACHER]';
    case 'DEPT_ADMIN': return '院系负责人 [DEPT_ADMIN]';
    case 'SYS_ADMIN': return '学校管理员 [SYS_ADMIN]';
    default: return userStore.userType || '教务用户';
  }
});

const roleTagType = computed(() => {
  switch (userStore.userType) {
    case 'STUDENT': return 'success';
    case 'TEACHER': return 'primary';
    case 'DEPT_ADMIN': return 'warning';
    case 'SYS_ADMIN': return 'danger';
    default: return 'info';
  }
});

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要安全退出当前登录账号吗？', '退出确认', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    });

    logoutLoading.value = true;
    await userStore.logout();
    ElMessage.success('已安全退出登录');
    router.push('/login');
  } catch {
    // 取消退出
  } finally {
    logoutLoading.value = false;
  }
};

onMounted(() => {
  // 如果已登录但缺少详细信息，尝试获取个人资料
  if (userStore.token && !userStore.realName) {
    userStore.getUserInfo().catch(() => {});
  }
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.layout-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: $bg-color;
}

.layout-header {
  height: 56px;
  background-color: $card-bg;
  border-bottom: 1px solid $border-color-light;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.04);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;

  .logo-box {
    width: 34px;
    height: 34px;
    background-color: $primary-light-bg;
    color: $primary-color;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
  }

  .title-group {
    display: flex;
    flex-direction: column;

    .main-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
    }

    .sub-title {
      font-size: 11px;
      color: $text-muted;
    }
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;

  .role-badge {
    font-weight: 500;
  }

  .dept-badge {
    font-size: 12px;
    color: $text-secondary;
    background-color: #f0f2f5;
    padding: 2px 8px;
    border-radius: 4px;
  }

  .user-greeting {
    font-size: 13px;
    color: $text-secondary;

    strong {
      color: $text-primary;
    }
  }
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-sidebar {
  width: 220px;
  background-color: $card-bg;
  border-right: 1px solid $border-color-light;
  overflow-y: auto;

  .sidebar-menu {
    border-right: none;
  }

  .menu-group-title {
    font-size: 11px;
    color: $text-muted;
    padding: 14px 16px 6px;
    letter-spacing: 0.5px;
  }

  .menu-divider {
    height: 1px;
    background-color: $border-color-light;
    margin: 8px 16px;
  }
}

.layout-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
</style>
