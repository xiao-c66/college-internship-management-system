import React, { useState, useEffect, useMemo } from 'react';
import {
  Card,
  Table,
  Button,
  Tag,
  Space,
  Form,
  Select,
  Input,
  Modal,
  DatePicker,
  InputNumber,
  Row,
  Col,
  message,
  Popconfirm,
  Typography,
  Alert
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  EditOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  TeamOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  getTaskList,
  createTask,
  updateTask,
  publishTask,
  deleteTask,
  TaskItem,
  TaskCreateOrUpdateParams
} from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph } = Typography;
const { RangePicker } = DatePicker;

export const TaskListPage: React.FC = () => {
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);

  // 对话框相关
  const [dialogVisible, setDialogVisible] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [form] = Form.useForm();

  const navigate = useNavigate();
  const { userType, hasRole } = useAuthStore();
  const canManage = hasRole(['DEPT_ADMIN', 'SYS_ADMIN']);

  const loadTasks = async () => {
    setLoading(true);
    try {
      const res = await getTaskList({ status: filterStatus });
      if (res.code === 200 && res.data) {
        setTasks(res.data);
      }
    } catch {
      // 错误由全局拦截器提示
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTasks();
  }, [filterStatus]);

  // 监听权重变化以计算权重总和
  const weights = Form.useWatch([], form);
  const calculatedWeightSum = useMemo(() => {
    if (!weights) return 0;
    const w1 = Number(weights.weightEnterprise || 0);
    const w2 = Number(weights.weightTeacherProcess || 0);
    const w3 = Number(weights.weightWeeklyReport || 0);
    const w4 = Number(weights.weightStageMaterial || 0);
    const w5 = Number(weights.weightSummary || 0);
    return Math.round((w1 + w2 + w3 + w4 + w5) * 100) / 100;
  }, [weights]);

  const isWeightValid = Math.abs(calculatedWeightSum - 100) < 0.01;

  const handleOpenCreate = () => {
    setIsEdit(false);
    setEditId(null);
    form.resetFields();
    form.setFieldsValue({
      academicYear: '2025-2026',
      semester: 2,
      internshipMode: 'DISTRIBUTED',
      weeklyFrequency: 'WEEKLY',
      safetyPassingScore: 80,
      safetyMaxAttempts: 5,
      weightEnterprise: 20,
      weightTeacherProcess: 20,
      weightWeeklyReport: 20,
      weightStageMaterial: 20,
      weightSummary: 20
    });
    setDialogVisible(true);
  };

  const handleOpenEdit = (task: TaskItem) => {
    setIsEdit(true);
    setEditId(task.id);
    form.setFieldsValue({
      taskCode: task.taskCode,
      taskName: task.taskName,
      academicYear: task.academicYear,
      semester: task.semester,
      internshipMode: task.internshipMode,
      dateRange: [dayjs(task.startDate), dayjs(task.endDate)],
      weeklyFrequency: task.weeklyFrequency,
      safetyPassingScore: task.safetyPassingScore,
      safetyMaxAttempts: task.safetyMaxAttempts,
      weightEnterprise: task.weightEnterprise,
      weightTeacherProcess: task.weightTeacherProcess,
      weightWeeklyReport: task.weightWeeklyReport,
      weightStageMaterial: task.weightStageMaterial,
      weightSummary: task.weightSummary
    });
    setDialogVisible(true);
  };

  const handlePublish = async (id: number) => {
    try {
      const res = await publishTask(id);
      if (res.code === 200) {
        message.success('实习批次任务已正式发布生效');
        loadTasks();
      }
    } catch {}
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await deleteTask(id);
      if (res.code === 200) {
        message.success('草稿任务已成功删除');
        loadTasks();
      }
    } catch {}
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (!isWeightValid) {
        message.error(`五项评价权重之和必须严格等于 100.00% (当前为 ${calculatedWeightSum}%)`);
        return;
      }
      setSubmitLoading(true);
      const params: TaskCreateOrUpdateParams = {
        taskCode: values.taskCode,
        taskName: values.taskName,
        academicYear: values.academicYear,
        semester: values.semester,
        internshipMode: values.internshipMode,
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        endDate: values.dateRange[1].format('YYYY-MM-DD'),
        weeklyFrequency: values.weeklyFrequency,
        safetyPassingScore: values.safetyPassingScore,
        safetyMaxAttempts: values.safetyMaxAttempts,
        weightEnterprise: values.weightEnterprise,
        weightTeacherProcess: values.weightTeacherProcess,
        weightWeeklyReport: values.weightWeeklyReport,
        weightStageMaterial: values.weightStageMaterial,
        weightSummary: values.weightSummary
      };

      if (isEdit && editId) {
        await updateTask(editId, params);
        message.success('实习任务修改成功');
      } else {
        await createTask(params);
        message.success('实习任务创建成功');
      }
      setDialogVisible(false);
      loadTasks();
    } catch (err: any) {
      // 验证未通过或请求错误
    } finally {
      setSubmitLoading(false);
    }
  };

  const formatStatus = (status: string) => {
    switch (status) {
      case 'DRAFT': return <Tag color="default">草稿 (DRAFT)</Tag>;
      case 'PUBLISHED': return <Tag color="processing">已发布 (PUBLISHED)</Tag>;
      case 'IN_PROGRESS': return <Tag color="success">进行中 (IN_PROGRESS)</Tag>;
      case 'ENDED': return <Tag color="error">已结束 (ENDED)</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  const columns: ColumnsType<TaskItem> = [
    {
      title: '任务编码',
      dataIndex: 'taskCode',
      key: 'taskCode',
      width: 150
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName',
      ellipsis: true
    },
    {
      title: '所属院系',
      dataIndex: 'deptName',
      key: 'deptName',
      width: 170
    },
    {
      title: '学年学期',
      key: 'term',
      width: 140,
      render: (_, record) => `${record.academicYear} 第${record.semester}学期`
    },
    {
      title: '组织模式',
      dataIndex: 'internshipMode',
      key: 'internshipMode',
      width: 110,
      render: (val) => (
        <Tag color={val === 'CENTRALIZED' ? 'blue' : 'cyan'}>
          {val === 'CENTRALIZED' ? '集中实习' : '分散顶岗'}
        </Tag>
      )
    },
    {
      title: '起止周期',
      key: 'period',
      width: 210,
      render: (_, record) => `${record.startDate} ~ ${record.endDate}`
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status) => formatStatus(status)
    },
    {
      title: '学生数',
      dataIndex: 'studentCount',
      key: 'studentCount',
      width: 80,
      render: (count) => count ?? 0
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/tasks/${record.id}`)}
          >
            详情
          </Button>

          {canManage && (
            <Button
              type="link"
              size="small"
              icon={<TeamOutlined />}
              onClick={() => navigate(`/tasks/${record.id}?tab=students`)}
            >
              分配导师
            </Button>
          )}

          {canManage && record.status === 'DRAFT' && (
            <>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleOpenEdit(record)}
              >
                编辑
              </Button>
              <Popconfirm
                title="确定要正式发布该实习批次任务吗？"
                description="发布后将根据圈定专业班级自动纳入学生，允许学生查看并填报实习申请。"
                onConfirm={() => handlePublish(record.id)}
                okText="发布"
                cancelText="取消"
              >
                <Button type="link" size="small" icon={<CheckCircleOutlined />} style={{ color: '#52c41a' }}>
                  发布
                </Button>
              </Popconfirm>
              <Popconfirm
                title="确定要删除该草稿任务吗？"
                onConfirm={() => handleDelete(record.id)}
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              实习批次任务管理
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              制定与管理各届顶岗实习任务批次、成绩权重规范与学生圈定名单 (TASK-001 ~ TASK-011)
            </Paragraph>
          </div>
          {canManage && (
            <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenCreate}>
              创建实习任务批次
            </Button>
          )}
        </div>
      </Card>

      <Card>
        <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
          <Col>
            <Space>
              <span>任务状态:</span>
              <Select
                value={filterStatus}
                placeholder="全部状态"
                allowClear
                style={{ width: 160 }}
                onChange={(val) => setFilterStatus(val)}
                options={[
                  { label: '全部状态', value: '' },
                  { label: '草稿 (DRAFT)', value: 'DRAFT' },
                  { label: '已发布 (PUBLISHED)', value: 'PUBLISHED' },
                  { label: '进行中 (IN_PROGRESS)', value: 'IN_PROGRESS' },
                  { label: '已结束 (ENDED)', value: 'ENDED' }
                ]}
              />
              <Button type="primary" icon={<SearchOutlined />} onClick={loadTasks}>
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={() => { setFilterStatus(undefined); loadTasks(); }}>
                重置
              </Button>
            </Space>
          </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={tasks}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 个任务批次` }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* 创建 / 编辑模态框 */}
      <Modal
        title={isEdit ? '编辑实习任务批次' : '创建新实习任务批次'}
        open={dialogVisible}
        width={780}
        onCancel={() => setDialogVisible(false)}
        onOk={handleSubmit}
        confirmLoading={submitLoading}
        okText={isEdit ? '保存修改' : '立即创建'}
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="taskCode"
                label="任务编码"
                rules={[{ required: true, message: '请输入唯一任务编码' }]}
              >
                <Input placeholder="例如 TASK2026CS03" disabled={isEdit} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="taskName"
                label="任务名称"
                rules={[{ required: true, message: '请输入任务全称' }]}
              >
                <Input placeholder="例如 2026届计算机科学顶岗实习" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="academicYear"
                label="所属学年"
                rules={[{ required: true, message: '请输入学年' }]}
              >
                <Input placeholder="例如 2025-2026" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="semester"
                label="所属学期"
                rules={[{ required: true, message: '请选择学期' }]}
              >
                <Select
                  options={[
                    { label: '第 1 学期', value: 1 },
                    { label: '第 2 学期', value: 2 }
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="internshipMode"
                label="实习组织模式"
                rules={[{ required: true, message: '请选择组织模式' }]}
              >
                <Select
                  options={[
                    { label: '集中实习 (CENTRALIZED)', value: 'CENTRALIZED' },
                    { label: '分散顶岗 (DISTRIBUTED)', value: 'DISTRIBUTED' }
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="dateRange"
                label="实习起止周期"
                rules={[{ required: true, message: '请选择实习起止时间' }]}
              >
                <RangePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="weeklyFrequency"
                label="周报提交频次"
                rules={[{ required: true, message: '请选择提交频次' }]}
              >
                <Select
                  options={[
                    { label: '每周一次', value: 'WEEKLY' },
                    { label: '双周一次', value: 'BIWEEKLY' }
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="safetyPassingScore"
                label="安全及格线"
                rules={[{ required: true, message: '配置及格分' }]}
              >
                <InputNumber min={60} max={100} style={{ width: '100%' }} addonAfter="分" />
              </Form.Item>
            </Col>
          </Row>

          <Alert
            type={isWeightValid ? 'success' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            message={
              <Space>
                <strong>五项成绩评价权重设置 (要求合计严格等于 100.00%):</strong>
                <span>
                  当前合计: <strong style={{ color: isWeightValid ? '#52c41a' : '#ff4d4f' }}>{calculatedWeightSum}%</strong>
                  {!isWeightValid && ` (差额: ${(100 - calculatedWeightSum).toFixed(2)}%)`}
                </span>
              </Space>
            }
          />

          <Row gutter={12}>
            <Col span={4} offset={2}>
              <Form.Item name="weightEnterprise" label="企业评价" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="weightTeacherProcess" label="教师过程" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="weightWeeklyReport" label="周报评定" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="weightStageMaterial" label="阶段材料" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="weightSummary" label="实习总结" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};
