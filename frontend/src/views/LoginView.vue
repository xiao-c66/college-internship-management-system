<template>
  <div class="login-container">
    <div class="login-card">
      <div class="card-header">
        <div class="logo-circle">
          <el-icon><School /></el-icon>
        </div>
        <h1 class="title">高校实习全过程管理系统</h1>
        <p class="subtitle">College Internship Management System</p>
        <el-tag type="info" size="small" class="stage-tag">真实认证联调环境</el-tag>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入学号/工号/管理账号"
            prefix-icon="User"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入登录密码"
            prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item prop="captcha">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captcha"
              placeholder="请输入右侧验证码"
              prefix-icon="Key"
              size="large"
              maxlength="6"
            />
            <div
              class="captcha-img"
              :class="{ loading: captchaLoading }"
              @click="fetchCaptcha"
              title="点击刷新验证码"
            >
              <span>{{ captchaText || '----' }}</span>
            </div>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          class="submit-btn"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          {{ loading ? '正在安全认证...' : '立即登录' }}
        </el-button>
      </el-form>

      <!-- 快速填充演示账号 -->
      <div class="demo-accounts-box">
        <div class="demo-title">
          <span>演示账号一键填入 (密码均为 123456)</span>
        </div>
        <div class="demo-buttons">
          <el-button size="small" plain @click="fillDemoAccount('student')">学生 (张晓峰)</el-button>
          <el-button size="small" plain @click="fillDemoAccount('teacher')">教师 (李教授)</el-button>
          <el-button size="small" plain @click="fillDemoAccount('deptadmin')">院系 (负责人)</el-button>
          <el-button size="small" plain type="danger" @click="fillDemoAccount('admin')">校级管理员</el-button>
        </div>
      </div>

      <div class="card-footer">
        <span class="tip">安全提示：支持学生、指导教师、院系负责人与系统管理员多角色</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { School } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useRouter, useRoute } from 'vue-router';
import service from '@/utils/request';
import { useUserStore } from '@/store/modules/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const loginFormRef = ref<FormInstance>();
const loading = ref(false);
const captchaLoading = ref(false);
const captchaText = ref('');
const captchaKey = ref('');

const loginForm = reactive({
  username: 'student',
  password: '',
  captcha: ''
});

const loginRules = reactive<FormRules>({
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
});

const fetchCaptcha = async () => {
  captchaLoading.value = true;
  try {
    const res: any = await service.get('/auth/captcha');
    if (res.code === 200 && res.data) {
      captchaText.value = res.data.captchaCode;
      captchaKey.value = res.data.captchaKey;
      loginForm.captcha = res.data.captchaCode; // 便捷自动预填当前有效验证码
    }
  } catch (err) {
    console.error('获取验证码失败', err);
    ElMessage.error('无法连接后端服务获取验证码');
  } finally {
    captchaLoading.value = false;
  }
};

const fillDemoAccount = (role: string) => {
  loginForm.username = role;
  loginForm.password = '123456';
  loginForm.captcha = captchaText.value;
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return;

    loading.value = true;
    try {
      const res: any = await service.post('/auth/login', {
        username: loginForm.username.trim(),
        password: loginForm.password,
        captcha: loginForm.captcha.trim(),
        captchaKey: captchaKey.value
      });

      if (res.code === 200 && res.data) {
        userStore.setLoginInfo(res.data);
        ElMessage.success(`欢迎登录，${res.data.realName}`);

        // 检查是否有重定向目标
        const redirect = route.query.redirect as string;
        if (redirect && !redirect.startsWith('/login')) {
          router.push(redirect);
          return;
        }

        // 根据角色跳转到对应工作台
        const userType = res.data.userType;
        if (userType === 'STUDENT') {
          router.push('/dashboard/student');
        } else if (userType === 'TEACHER') {
          router.push('/dashboard/teacher');
        } else if (userType === 'DEPT_ADMIN') {
          router.push('/dashboard/dept');
        } else if (userType === 'SYS_ADMIN') {
          router.push('/dashboard/admin');
        } else {
          router.push('/dashboard/student');
        }
      }
    } catch (err: any) {
      // 登录失败后刷新验证码
      await fetchCaptcha();
    } finally {
      loading.value = false;
    }
  });
};

onMounted(() => {
  fetchCaptcha();
});
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $bg-color;
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background-color: $card-bg;
  border: 1px solid $border-color-light;
  border-radius: $radius-card;
  box-shadow: $shadow-float;
  padding: 36px 32px;
}

.card-header {
  text-align: center;
  margin-bottom: 24px;

  .logo-circle {
    width: 48px;
    height: 48px;
    margin: 0 auto 10px;
    background-color: $primary-light-bg;
    color: $primary-color;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
  }

  .title {
    font-size: 19px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 4px 0;
  }

  .subtitle {
    font-size: 11px;
    color: $text-muted;
    margin: 0 0 10px 0;
  }

  .stage-tag {
    font-size: 11px;
    font-weight: 500;
  }
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;

  .captcha-img {
    width: 120px;
    height: 40px;
    background-color: #eef2f8;
    border: 1px solid $border-color;
    border-radius: $radius-base;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 3px;
    color: $primary-color;
    cursor: pointer;
    user-select: none;
    transition: background-color 0.2s;

    &:hover {
      background-color: #e2eaf5;
    }

    &.loading {
      opacity: 0.6;
    }
  }
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
}

.demo-accounts-box {
  margin-top: 24px;
  padding: 12px;
  background-color: $bg-color;
  border-radius: $radius-base;
  border: 1px dashed $border-color;

  .demo-title {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: 8px;
    text-align: center;
  }

  .demo-buttons {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;

    .el-button {
      margin: 0;
      font-size: 12px;
    }
  }
}

.card-footer {
  margin-top: 20px;
  display: flex;
  justify-content: center;

  .tip {
    font-size: 11px;
    color: $text-muted;
  }
}
</style>
