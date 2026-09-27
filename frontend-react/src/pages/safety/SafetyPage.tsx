import React, { useState, useEffect } from 'react';
import {
  Card,
  Row,
  Col,
  Tag,
  Button,
  Space,
  Select,
  Steps,
  Table,
  Input,
  Modal,
  Form,
  Typography,
  Alert,
  Progress,
  message,
  Divider,
  Popconfirm
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SafetyCertificateOutlined,
  BookOutlined,
  FormOutlined,
  CheckCircleOutlined,
  BellOutlined,
  SearchOutlined,
  ReloadOutlined,
  FileProtectOutlined,
  UserOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  listMaterials,
  markMaterialRead,
  getSafetyStatus,
  getDeptSafetyStatistics,
  remindStudents,
  getStudentsSafetyProgress,
  signCommitment,
  SafetyMaterialItem,
  SafetyStatusVO,
  StudentSafetyProgressVO
} from '../../api/safety';
import { getTaskList, TaskItem } from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const SafetyPage: React.FC = () => {
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  const { user, userType, hasRole } = useAuthStore();
  const navigate = useNavigate();
  const isStudent = userType === 'STUDENT';

  // 学生端特有状态
  const [safetyStatus, setSafetyStatus] = useState<SafetyStatusVO | null>(null);
  const [materials, setMaterials] = useState<SafetyMaterialItem[]>([]);
  const [signModalVisible, setSignModalVisible] = useState(false);
  const [signForm] = Form.useForm();
  const [signLoading, setSignLoading] = useState(false);

  // 管理端特有状态
  const [stats, setStats] = useState<any>(null);
  const [students, setStudents] = useState<StudentSafetyProgressVO[]>([]);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [remindLoading, setRemindLoading] = useState(false);

  // 1. 初始化任务列表
  useEffect(() => {
    const initTasks = async () => {
      try {
        const res = await getTaskList();
        if (res.code === 200 && res.data && res.data.length > 0) {
          setTasks(res.data);
          setSelectedTaskId(res.data[0].id);
        }
      } catch {}
    };
    initTasks();
  }, []);

  // 2. 加载学生视图数据
  const loadStudentData = async (taskId: number) => {
    setLoading(true);
    try {
      const [statusRes, matRes] = await Promise.all([
        getSafetyStatus(taskId),
        listMaterials(taskId)
      ]);
      if (statusRes.code === 200) setSafetyStatus(statusRes.data);
      if (matRes.code === 200) setMaterials(matRes.data);
    } catch {}
    finally {
      setLoading(false);
    }
  };

  // 3. 加载管理/教师视图数据
  const loadManageData = async (taskId?: number) => {
    setLoading(true);
    try {
      const [statsRes, studentsRes] = await Promise.all([
        getDeptSafetyStatistics(taskId),
        getStudentsSafetyProgress({
          taskId,
          status: statusFilter,
          keyword: searchKeyword || undefined
        })
      ]);
      if (statsRes.code === 200) setStats(statsRes.data);
      if (studentsRes.code === 200) setStudents(studentsRes.data);
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!selectedTaskId) return;
    if (isStudent) {
      loadStudentData(selectedTaskId);
    } else {
      loadManageData(selectedTaskId);
    }
  }, [selectedTaskId, isStudent, statusFilter]);

  // 学生标记已读
  const handleReadMaterial = async (matId: number) => {
    if (!selectedTaskId) return;
    try {
      await markMaterialRead(matId, selectedTaskId);
      message.success('已完成该安全规约阅读');
      loadStudentData(selectedTaskId);
    } catch {}
  };

  // 学生签署承诺书
  const handleSignCommitment = async () => {
    if (!selectedTaskId) return;
    try {
      const values = await signForm.validateFields();
      setSignLoading(true);
      await signCommitment({
        taskId: selectedTaskId,
        signerName: values.signerName,
        emergencyContact: values.emergencyContact,
        emergencyPhone: values.emergencyPhone,
        insuranceFileUrl: values.insuranceFileUrl || '/uploads/insurance/sample_policy.pdf'
      });
      message.success('安全承诺书已成功在线签署并上链备案');
      setSignModalVisible(false);
      loadStudentData(selectedTaskId);
    } catch {}
    finally {
      setSignLoading(false);
    }
  };

  // 一键催办未达标学生
  const handleRemindStudents = async (studentId?: number) => {
    if (!selectedTaskId) return;
    setRemindLoading(true);
    try {
      const res = await remindStudents(selectedTaskId, studentId);
      if (res.code === 200) {
        message.success(studentId ? '已向该学生发送安全教育考核催办通知' : '已向全部未达标学生批量发送催办通知');
      }
    } catch {}
    finally {
      setRemindLoading(false);
    }
  };

  const getStepCurrent = (code?: string) => {
    switch (code) {
      case 'NOT_STARTED': return 0;
      case 'STUDYING': return 1;
      case 'PENDING_TEST': return 2;
      case 'PASSED': return 3;
      case 'COMPLETED': return 4;
      default: return 0;
    }
  };

  // 管理端表格列定义
  const columns: ColumnsType<StudentSafetyProgressVO> = [
    {
      title: '学号',
      dataIndex: 'studentNumber',
      key: 'studentNumber',
      width: 140
    },
    {
      title: '学生姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 160,
      render: (name) => <strong>{name}</strong>
    },
    {
      title: '班级',
      dataIndex: 'className',
      key: 'className',
      width: 180,
      render: (c) => c || '未绑定班级'
    },
    {
      title: '规约学习进度',
      dataIndex: 'materialProgress',
      key: 'materialProgress',
      width: 150,
      render: (p) => <Tag color="blue">{p}</Tag>
    },
    {
      title: '最高成绩',
      dataIndex: 'examScore',
      key: 'examScore',
      width: 110,
      render: (score, record) => (
        <span style={{ fontWeight: 'bold', color: record.isPassed ? '#52c41a' : '#ff4d4f' }}>
          {score} 分
        </span>
      )
    },
    {
      title: '考核轮次',
      dataIndex: 'examAttempts',
      key: 'examAttempts',
      width: 100,
      render: (a) => `第 ${a} 次`
    },
    {
      title: '承诺书',
      dataIndex: 'isCommitmentSigned',
      key: 'isCommitmentSigned',
      width: 120,
      render: (signed) => (
        signed ? <Tag color="success">已签署</Tag> : <Tag color="error">未签署</Tag>
      )
    },
    {
      title: '当前状态',
      dataIndex: 'statusText',
      key: 'statusText',
      width: 130,
      render: (text, record) => (
        <Tag color={record.statusCode === 'COMPLETED' ? 'green' : 'orange'}>
          {text}
        </Tag>
      )
    },
    {
      title: '催办提醒',
      key: 'action',
      width: 110,
      fixed: 'right',
      render: (_, record) => (
        record.statusCode !== 'COMPLETED' ? (
          <Button
            type="link"
            size="small"
            icon={<BellOutlined />}
            onClick={() => handleRemindStudents(record.studentId)}
          >
            催办
          </Button>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>已达标</Text>
        )
      )
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 头部标题与任务切换 */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              安全教育与准入体系
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              五阶段闭环准入考核：规约学习 → 在线考核 → 及格达标 → 承诺签署 → 资格准入 (SAFE-001 ~ SAFE-009)
            </Paragraph>
          </div>
          <Space>
            <span>当前实习批次:</span>
            <Select
              style={{ width: 280 }}
              value={selectedTaskId}
              onChange={(val) => setSelectedTaskId(val)}
              options={tasks.map((t) => ({ label: `${t.taskName} (${t.taskCode})`, value: t.id }))}
            />
          </Space>
        </div>
      </Card>

      {/* ======================= 学生端视图 ======================= */}
      {isStudent ? (
        <>
          {/* 准入五阶段进度条 */}
          <Card>
            <Steps
              current={getStepCurrent(safetyStatus?.statusCode)}
              items={[
                { title: '未开始', description: '等待进入学习' },
                { title: '安全学习中', description: '阅读全部规约材料' },
                { title: '待考准入测试', description: '客观题在线考核' },
                { title: '测试已达标', description: '及格分>=80分' },
                { title: '准入已达标', description: '已签署安全承诺书' }
              ]}
            />
          </Card>

          {/* 准入状态总览 */}
          <Card>
            <Row gutter={[20, 20]} align="middle">
              <Col xs={24} md={16}>
                <Space direction="vertical" size="small">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Title level={4} style={{ margin: 0 }}>
                      准入总评：{safetyStatus?.statusDesc}
                    </Title>
                    <Tag color={safetyStatus?.statusCode === 'COMPLETED' ? 'success' : 'processing'}>
                      {safetyStatus?.statusCode}
                    </Tag>
                  </div>
                  <Paragraph type="secondary">
                    安全材料阅读: {safetyStatus?.materialsRead || 0} / {safetyStatus?.materialsTotal || 0} 篇 |
                    测试最高成绩: <strong style={{ color: '#1677ff' }}>{safetyStatus?.highestScore ?? 0} 分</strong> (及格线: {safetyStatus?.passingScore ?? 80} 分) |
                    承诺书签署: {safetyStatus?.isCommitmentSigned ? '已完成' : '待签署'}
                  </Paragraph>
                </Space>
              </Col>
              <Col xs={24} md={8} style={{ textAlign: 'right' }}>
                <Space>
                  <Button
                    type="primary"
                    size="large"
                    icon={<FormOutlined />}
                    onClick={() => navigate(`/safety/exam?taskId=${selectedTaskId}`)}
                  >
                    {safetyStatus?.isPassed ? '查看考核试卷 (已达标)' : '进入在线安全考核'}
                  </Button>
                  {!safetyStatus?.isCommitmentSigned && (
                    <Button
                      size="large"
                      icon={<FileProtectOutlined />}
                      onClick={() => {
                        signForm.setFieldsValue({
                          signerName: user?.realName || user?.username,
                          emergencyContact: '监护人/辅导员',
                          emergencyPhone: '13800000000'
                        });
                        setSignModalVisible(true);
                      }}
                    >
                      签署承诺书
                    </Button>
                  )}
                </Space>
              </Col>
            </Row>
          </Card>

          {/* 安全规约材料清单 */}
          <Card title={<span><BookOutlined /> 安全教育规约与法规模块</span>}>
            <Row gutter={[16, 16]}>
              {materials.map((mat, idx) => (
                <Col xs={24} md={12} key={mat.id}>
                  <Card size="small" hoverable style={{ height: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div>
                        <div style={{ fontWeight: 'bold', fontSize: 15, marginBottom: 8 }}>
                          {idx + 1}. {mat.title}
                        </div>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          分类: {mat.contentType === 'REGULATION' ? '校外安全制度' : '防护规范指引'}
                        </Text>
                      </div>
                      <Button
                        type="primary"
                        ghost
                        size="small"
                        icon={<CheckCircleOutlined />}
                        onClick={() => handleReadMaterial(mat.id)}
                      >
                        标记已读
                      </Button>
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>

          {/* 签署安全承诺书模态框 */}
          <Modal
            title="在线签署高校学生校外实习安全责任承诺书"
            open={signModalVisible}
            onCancel={() => setSignModalVisible(false)}
            onOk={handleSignCommitment}
            confirmLoading={signLoading}
            okText="本人已认真阅读并确认签署"
            cancelText="取消"
            destroyOnClose
          >
            <Alert
              message="法律提示：签署本承诺书代表您已知悉校外实习安全操作规程，并承诺严格遵守用人单位及学校的各项纪律与应急规程。"
              type="warning"
              showIcon
              style={{ marginTop: 12, marginBottom: 16 }}
            />
            <Form form={signForm} layout="vertical">
              <Form.Item
                name="signerName"
                label="承诺签署人真实姓名"
                rules={[{ required: true, message: '请输入真实姓名' }]}
              >
                <Input placeholder="输入学生本人真实姓名" />
              </Form.Item>
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="emergencyContact"
                    label="紧急联系人"
                    rules={[{ required: true, message: '请输入紧急联系人' }]}
                  >
                    <Input placeholder="例如：父亲/辅导员" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="emergencyPhone"
                    label="紧急联系电话"
                    rules={[{ required: true, message: '请输入有效电话' }]}
                  >
                    <Input placeholder="11位手机号码" />
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          </Modal>
        </>
      ) : (
        /* ======================= 教师与管理端视图 ======================= */
        <>
          {/* 达标率与统计看板 */}
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Text type="secondary">圈定学生总人数</Text>
                <Title level={3} style={{ margin: '8px 0 0 0', color: '#1677ff' }}>
                  {stats?.totalStudents || students.length} 人
                </Title>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Text type="secondary">安全考核已及格率</Text>
                <Title level={3} style={{ margin: '8px 0 0 0', color: '#52c41a' }}>
                  {stats?.passRate || '100%'}
                </Title>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Text type="secondary">安全准入全达标率 (含承诺书)</Text>
                <Title level={3} style={{ margin: '8px 0 0 0', color: '#722ed1' }}>
                  {stats?.completionRate || '100%'}
                </Title>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Text type="secondary">未完成考核人数</Text>
                <Title level={3} style={{ margin: '8px 0 0 0', color: '#ff4d4f' }}>
                  {stats?.uncompletedStudents ?? 0} 人
                </Title>
              </Card>
            </Col>
          </Row>

          {/* 学生进度监控表格 */}
          <Card>
            <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
              <Col>
                <Space>
                  <span>考核阶段状态:</span>
                  <Select
                    value={statusFilter}
                    placeholder="全部状态"
                    allowClear
                    style={{ width: 170 }}
                    onChange={(val) => setStatusFilter(val)}
                    options={[
                      { label: '全部状态', value: '' },
                      { label: '未开始 (NOT_STARTED)', value: 'NOT_STARTED' },
                      { label: '规约学习中 (STUDYING)', value: 'STUDYING' },
                      { label: '待考准入 (PENDING_TEST)', value: 'PENDING_TEST' },
                      { label: '测试已达标 (PASSED)', value: 'PASSED' },
                      { label: '完全达标 (COMPLETED)', value: 'COMPLETED' }
                    ]}
                  />
                  <Input
                    placeholder="学号 / 姓名检索"
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                    onPressEnter={() => loadManageData(selectedTaskId)}
                    style={{ width: 180 }}
                  />
                  <Button type="primary" icon={<SearchOutlined />} onClick={() => loadManageData(selectedTaskId)}>
                    查询
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={() => { setStatusFilter(undefined); setSearchKeyword(''); loadManageData(selectedTaskId); }}>
                    重置
                  </Button>
                </Space>
              </Col>
              <Col>
                <Popconfirm
                  title="确认向当前批次所有未达标学生批量发送催办提醒吗？"
                  onConfirm={() => handleRemindStudents()}
                  okText="发送催办"
                  cancelText="取消"
                >
                  <Button type="primary" danger icon={<BellOutlined />} loading={remindLoading}>
                    一键催办未达标学生
                  </Button>
                </Popconfirm>
              </Col>
            </Row>

            <Table
              columns={columns}
              dataSource={students}
              rowKey="studentId"
              loading={loading}
              pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 名学生进度记录` }}
              scroll={{ x: 1200 }}
            />
          </Card>
        </>
      )}
    </div>
  );
};
