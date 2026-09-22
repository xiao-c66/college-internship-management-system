<template>
  <div class="safety-study-container" v-loading="loading">
    <el-card shadow="never" class="header-card">
      <div class="header-flex">
        <div>
          <h2 class="page-title">实习安全教育与准入学习</h2>
          <p class="page-subtitle">【推荐导引】建议在实习申报前完成安全规程学习、人身意外险凭证备案与安全责任知晓，筑牢实习安全屏障 (SAFE-007 / SAFE-011为推荐项)</p>
        </div>
        <div class="header-badge">
          <el-tag :type="getStatusTagType(safetyStatus?.statusCode)" size="large" effect="dark">
            当前阶段: {{ safetyStatus?.statusDesc || '未开始' }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 无生效任务空状态卡片 -->
    <el-card shadow="never" class="empty-card" v-if="!taskId">
      <el-empty description="当前暂无可用的生效实习批次任务，请联系院系教学负责人确认任务发布状态" />
    </el-card>

    <div v-else>
      <!-- 五阶段全流程指示器 -->
      <el-card shadow="never" class="steps-card">
        <el-steps :active="getStepIndex(safetyStatus?.statusCode)" finish-status="success" align-center>
          <el-step title="未开始" description="NOT_STARTED 准备阶段" />
          <el-step title="学习中" description="STUDYING 查阅规程" />
          <el-step title="待测试" description="PENDING_TEST 具备答题条件" />
          <el-step title="已通过测试" description="PASSED 及格达标" />
          <el-step title="已完成" description="COMPLETED 承诺签署备案" />
        </el-steps>
      </el-card>

    <el-row :gutter="20" style="margin-top: 16px">
      <!-- 左侧：安全教育资料清单 -->
      <el-col :span="14">
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header-title" style="display: flex; justify-content: space-between; align-items: center;">
              <div>
                <strong>📖 必学安全教育规程与防范手册</strong>
                <el-tag type="success" size="small" style="margin-left: 8px">
                  已学 {{ safetyStatus?.materialsRead || 0 }} / {{ safetyStatus?.materialsTotal || materials.length }} 篇
                </el-tag>
              </div>
              <div v-if="safetyStatus?.studyCompleteTime" style="font-size: 12px; color: #67c23a;">
                ✓ 规程已全部学完
              </div>
            </div>
          </template>

          <el-collapse v-model="activeCollapse" @change="handleCollapseChange">
            <el-collapse-item
              v-for="(item, idx) in materials"
              :key="item.id"
              :name="item.id"
            >
              <template #title>
                <div class="collapse-title" style="display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 12px;">
                  <div>
                    <el-tag size="small" :type="item.taskId ? 'warning' : 'primary'" class="mr-2">
                      {{ item.taskId ? '任务专属' : '全校通用' }}
                    </el-tag>
                    <span>{{ idx + 1 }}. {{ item.title }}</span>
                  </div>
                  <el-tag v-if="isMaterialRead(item.id)" type="success" size="small">已阅读</el-tag>
                  <el-tag v-else type="info" size="small">未阅读</el-tag>
                </div>
              </template>
              <div class="material-content-box">
                <p>{{ item.contentBody || '请查阅相关安全合规要求文件。' }}</p>
                <div v-if="item.fileUrl" class="mt-2">
                  <el-link type="primary" :href="item.fileUrl" target="_blank">📄 附件材料查阅下载</el-link>
                </div>
                <div style="margin-top: 10px; text-align: right;">
                  <el-button v-if="!isMaterialRead(item.id)" type="primary" size="small" plain @click.stop="confirmRead(item.id)">
                    标记此篇已阅读 (驱动状态流转)
                  </el-button>
                  <span v-else style="color: #67c23a; font-size: 12px;">已完成此篇规程学习</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div class="action-footer">
            <el-button type="primary" size="large" @click="goToExam">
              {{ safetyStatus?.isPassed ? '重新测试 / 查看试卷' : '进入在线安全教育准入考试 (SAFE-004)' }}
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：安全承诺书与保险备案 -->
      <el-col :span="10">
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header-title">
              <strong>✍️ 实习安全承诺书签署与保险凭据 (SAFE-007)</strong>
            </div>
          </template>

          <div class="commitment-text-box">
            <h4>高校校外实习安全责任承诺书</h4>
            <p>
              本人系计算机学院在籍学生，知悉校外实习的相关纪律、劳动法律常识与防范意外要则。本人郑重承诺：
            </p>
            <ol>
              <li>在校外实习期间，严格遵守国家法律法规及实习用人单位安全管理规章；</li>
              <li>注意交通通勤、生产作业安全，坚决防范电信诈骗、传销及非法集资陷阱；</li>
              <li>未经学校及导师审批批准，严禁擅自脱岗、跨区域移动或私自更换实习企业；</li>
              <li>如遇人身意外侵害、工伤或突发不可抗力，第一时间向校内指导教师报告。</li>
            </ol>
          </div>

          <div class="sign-form">
            <el-form label-position="top">
              <el-form-item label="意外伤害保险保单号 / 凭据文件地址">
                <el-input
                  v-model="insuranceUrl"
                  placeholder="如 /uploads/insurance/2026_policy.pdf 或填保单号"
                  :disabled="safetyStatus?.isCommitmentSigned === 1"
                />
              </el-form-item>

              <el-form-item>
                <el-checkbox
                  v-model="agreeChecked"
                  :disabled="safetyStatus?.isCommitmentSigned === 1"
                >
                  本人已逐条认真阅读并完全自愿遵守上述安全责任条款
                </el-checkbox>
              </el-form-item>

              <div v-if="safetyStatus?.isCommitmentSigned === 1" class="signed-success-banner">
                <el-result
                  icon="success"
                  title="安全承诺书已完成有效电子签署"
                  :sub-title="'签署备案时间: ' + (safetyStatus?.signTime || '已备案')"
                />
              </div>

              <el-button
                v-else
                type="success"
                size="large"
                style="width: 100%"
                :disabled="!agreeChecked"
                :loading="signLoading"
                @click="handleSignCommitment"
              >
                确认签署安全责任承诺书 (电子留存)
              </el-button>
            </el-form>
          </div>
        </el-card>

        <!-- 准入就绪指引 -->
        <el-card shadow="never" class="content-card mt-3" v-if="safetyStatus?.statusCode === 'COMPLETED'">
          <div class="ready-banner">
            <el-icon color="#67c23a" :size="24"><Check /></el-icon>
            <div class="ready-text">
              <div class="ready-title">安全教育规程与防范学习已完备</div>
              <div class="ready-sub">安全教育规程学习与安全承诺签署已就绪，建议前往填写实习申报。</div>
            </div>
          </div>
          <el-button type="primary" style="width: 100%; margin-top: 12px" @click="goToApply">
            前往填写实习申报 (APPLY-001)
          </el-button>
        </el-card>
      </el-col>
    </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Check } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import {
  getTaskList,
  getSafetyMaterials,
  getSafetyStatus,
  signCommitment,
  markMaterialRead,
  SafetyMaterial,
  SafetyStatus
} from '@/api';

const route = useRoute();
const router = useRouter();

const taskId = ref<number>(Number(route.query.taskId) || 0);
const loading = ref(false);
const signLoading = ref(false);
const materials = ref<SafetyMaterial[]>([]);
const safetyStatus = ref<SafetyStatus | null>(null);
const activeCollapse = ref<number[]>([]);
const agreeChecked = ref(false);
const insuranceUrl = ref('/uploads/insurance/demo_policy.pdf');

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

const readIds = computed<number[]>(() => {
  if (!safetyStatus.value?.readMaterialIds) return [];
  try {
    return JSON.parse(safetyStatus.value.readMaterialIds);
  } catch {
    return [];
  }
});

const isMaterialRead = (id: number) => {
  return readIds.value.includes(id);
};

const handleCollapseChange = async (activeNames: any) => {
  const currentIds = Array.isArray(activeNames) ? activeNames : [activeNames];
  for (const id of currentIds) {
    if (id && !isMaterialRead(Number(id))) {
      await confirmRead(Number(id));
    }
  }
};

const confirmRead = async (materialId: number) => {
  if (!taskId.value) return;
  try {
    await markMaterialRead(taskId.value, materialId);
    const statusRes = await getSafetyStatus(taskId.value);
    safetyStatus.value = statusRes.data;
  } catch (e) {
    console.error('更新阅读进度失败', e);
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const tid = await resolveTaskId();
    if (!tid || tid <= 0) {
      materials.value = [];
      safetyStatus.value = null;
      return;
    }
    const [matRes, statusRes] = await Promise.all([
      getSafetyMaterials({ taskId: tid }),
      getSafetyStatus(tid)
    ]);
    materials.value = matRes.data || [];
    safetyStatus.value = statusRes.data;
    if (safetyStatus.value?.isCommitmentSigned) {
      agreeChecked.value = true;
    }
    if (materials.value.length > 0) {
      activeCollapse.value = [materials.value[0].id];
    }
  } finally {
    loading.value = false;
  }
};

const handleSignCommitment = async () => {
  if (!taskId.value) {
    ElMessage.error('当前无可用实习任务');
    return;
  }
  if (!agreeChecked.value) {
    ElMessage.error('请先勾选已知晓并同意安全责任承诺条款');
    return;
  }
  signLoading.value = true;
  try {
    await signCommitment({
      taskId: taskId.value,
      insuranceFileUrl: insuranceUrl.value
    });
    ElMessage.success('安全责任承诺书签署成功并已备案！');
    await loadData();
  } finally {
    signLoading.value = false;
  }
};

const goToExam = () => {
  router.push({ path: '/safety/exam', query: { taskId: taskId.value } });
};

const goToApply = () => {
  router.push({ path: '/apply', query: { taskId: taskId.value } });
};

const getStepIndex = (statusCode?: string) => {
  switch (statusCode) {
    case 'NOT_STARTED': return 0;
    case 'STUDYING': return 1;
    case 'PENDING_TEST': return 2;
    case 'PASSED': return 3;
    case 'COMPLETED': return 4;
    default: return 0;
  }
};

const getStatusTagType = (statusCode?: string) => {
  switch (statusCode) {
    case 'COMPLETED': return 'success';
    case 'PASSED': return 'primary';
    case 'PENDING_TEST': return 'warning';
    case 'STUDYING': return 'info';
    default: return 'danger';
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped lang="scss">
.safety-study-container {
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

  .steps-card {
    border-radius: 8px;
    padding: 10px 0;
  }

  .content-card {
    border-radius: 8px;

    .card-header-title {
      font-size: 15px;
      color: #303133;
      display: flex;
      align-items: center;
    }
  }

  .collapse-title {
    display: flex;
    align-items: center;
    font-weight: 500;
  }

  .material-content-box {
    font-size: 14px;
    color: #475669;
    line-height: 1.8;
    background: #f8fafc;
    padding: 14px;
    border-radius: 6px;
  }

  .action-footer {
    margin-top: 20px;
    text-align: center;
  }

  .commitment-text-box {
    background: #fdf6ec;
    border: 1px solid #faecd8;
    padding: 14px;
    border-radius: 6px;
    font-size: 13px;
    color: #5c3c00;
    line-height: 1.7;

    h4 {
      text-align: center;
      margin: 0 0 10px 0;
      color: #b88230;
    }

    ol {
      margin: 6px 0 0 0;
      padding-left: 18px;
    }
  }

  .sign-form {
    margin-top: 16px;
  }

  .ready-banner {
    display: flex;
    align-items: center;
    gap: 12px;
    background: #f0f9eb;
    border: 1px solid #e1f3d8;
    padding: 12px;
    border-radius: 6px;

    .ready-title {
      font-weight: 600;
      color: #67c23a;
      font-size: 14px;
    }

    .ready-sub {
      font-size: 12px;
      color: #529b2e;
      margin-top: 4px;
    }
  }

  .mt-3 {
    margin-top: 16px;
  }
}
</style>
