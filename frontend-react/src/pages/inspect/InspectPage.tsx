import React, { useState, useEffect } from 'react';
import {
  Card,
  Row,
  Col,
  Tag,
  Button,
  Space,
  Select,
  Table,
  Tabs,
  Form,
  Input,
  InputNumber,
  Radio,
  Drawer,
  Modal,
  Popconfirm,
  Descriptions,
  Typography,
  message,
  Divider,
  DatePicker,
  Empty
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  FileSearchOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  AuditOutlined,
  EyeOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  FileDoneOutlined,
  ClockCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getInspectPlans,
  createInspectPlan,
  executeSampling,
  getInspectionList,
  submitInspection,
  getRectificationList,
  createRectification,
  submitRectification,
  reviewRectification,
  closeRectification,
  InspectPlanVO,
  InspectionVO,
  RectifyVO
} from '../../api/inspect';
import { getTaskList, TaskItem } from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;
const { RangePicker } = DatePicker;

// 督导检查状态标签
const getInspectionStatusTag = (status: string) => {
  switch (status) {
    case 'PENDING_INSPECT':
      return <Tag color="warning">待督导检查</Tag>;
    case 'INSPECTED':
      return <Tag color="processing">已检查合格</Tag>;
    case 'RECTIFIED':
      return <Tag color="error">已下达整改</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
};

// 整改流转状态标签
const getRectifyStatusTag = (status: string) => {
  switch (status) {
    case 'PENDING_SUBMIT':
      return <Tag color="warning">待学生提交报告</Tag>;
    case 'PENDING_REVIEW':
      return <Tag color="processing">已提报·待导师复核</Tag>;
    case 'CLOSED':
      return <Tag color="success">已闭环销号</Tag>;
    case 'REJECTED':
      return <Tag color="error">复核不合格·重改</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
};

export const InspectPage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';
  const isDeptAdmin = userType === 'DEPT_ADMIN';
  const isAdmin = userType === 'SYS_ADMIN';

  // 1. 任务批次状态
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  // 2. 方案与检查记录状态
  const [plans, setPlans] = useState<InspectPlanVO[]>([]);
  const [filterPlanId, setFilterPlanId] = useState<number | undefined>(undefined);
  const [inspections, setInspections] = useState<InspectionVO[]>([]);
  const [createPlanModalVisible, setCreatePlanModalVisible] = useState(false);
  const [planForm] = Form.useForm();
  const [planLoading, setPlanLoading] = useState(false);

  // 3. 检查记录录入/查看状态
  const [submitInspectModalVisible, setSubmitInspectModalVisible] = useState(false);
  const [inspectRecordForSubmit, setInspectRecordForSubmit] = useState<InspectionVO | null>(null);
  const [inspectSubmitForm] = Form.useForm();
  const [inspectSubmitLoading, setInspectSubmitLoading] = useState(false);
  const [inspectDetailDrawerVisible, setInspectDetailDrawerVisible] = useState(false);
  const [selectedInspection, setSelectedInspection] = useState<InspectionVO | null>(null);

  // 4. 整改通知与流转状态
  const [rectifications, setRectifications] = useState<RectifyVO[]>([]);
  const [createRectifyModalVisible, setCreateRectifyModalVisible] = useState(false);
  const [rectifyForm] = Form.useForm();
  const [rectifyLoading, setRectifyLoading] = useState(false);
  const [rectifySubmitModalVisible, setRectifySubmitModalVisible] = useState(false);
  const [selectedRectify, setSelectedRectify] = useState<RectifyVO | null>(null);
  const [studentRectifyForm] = Form.useForm();
  const [studentRectifyLoading, setStudentRectifyLoading] = useState(false);
  const [reviewModalVisible, setReviewModalVisible] = useState(false);
  const [teacherReviewForm] = Form.useForm();
  const [teacherReviewLoading, setTeacherReviewLoading] = useState(false);
  const [rectifyDetailDrawerVisible, setRectifyDetailDrawerVisible] = useState(false);

  // 初始加载任务批次
  useEffect(() => {
    const fetchTasks = async () => {
      try {
        const res = await getTaskList();
        if (res.code === 200 && res.data) {
          setTasks(res.data);
          const activeTask = res.data.find((t) => t.status === 'PUBLISHED') || res.data[0];
          if (activeTask) {
            setSelectedTaskId(activeTask.id);
          }
        }
      } catch {}
    };
    fetchTasks();
  }, []);

  // 加载检查方案与记录
  const loadPlansAndInspections = async (taskId?: number, planId?: number) => {
    setLoading(true);
    try {
      const [plansRes, inspRes, rectRes] = await Promise.all([
        getInspectPlans(taskId ? { taskId } : {}),
        getInspectionList({ ...(taskId ? { taskId } : {}), ...(planId ? { planId } : {}) }),
        getRectificationList(taskId ? { taskId } : {})
      ]);
      if (plansRes.code === 200 && plansRes.data) {
        setPlans(plansRes.data);
      }
      if (inspRes.code === 200 && inspRes.data) {
        setInspections(inspRes.data);
      }
      if (rectRes.code === 200 && rectRes.data) {
        setRectifications(rectRes.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPlansAndInspections(selectedTaskId, filterPlanId);
  }, [selectedTaskId, filterPlanId]);

  // 管理员创建方案
  const handleCreatePlan = async () => {
    if (!selectedTaskId) return;
    try {
      const values = await planForm.validateFields();
      setPlanLoading(true);
      const res = await createInspectPlan({
        taskId: selectedTaskId,
        planName: values.planName,
        samplingMode: values.samplingMode,
        samplingRatio: values.samplingRatio,
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        endDate: values.dateRange[1].format('YYYY-MM-DD'),
        expertGroup: values.expertGroup,
        remark: values.remark
      });
      if (res.code === 200) {
        message.success('督导检查方案编制成功');
        setCreatePlanModalVisible(false);
        planForm.resetFields();
        loadPlansAndInspections(selectedTaskId);
      }
    } catch {}
    finally {
      setPlanLoading(false);
    }
  };

  // 管理员执行抽样生成名单
  const handleExecuteSampling = async (planId: number) => {
    try {
      const res = await executeSampling(planId);
      if (res.code === 200) {
        message.success(`成功触发抽样，共生成 ${res.data} 名受检学生名单`);
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
  };

  // 督导录入检查记录
  const handleSubmitInspect = async () => {
    if (!inspectRecordForSubmit) return;
    try {
      const values = await inspectSubmitForm.validateFields();
      setInspectSubmitLoading(true);
      const res = await submitInspection({
        planId: inspectRecordForSubmit.planId,
        studentId: inspectRecordForSubmit.studentId,
        inspectionType: values.inspectionType,
        inspectionDate: values.inspectionDate ? values.inspectionDate.format('YYYY-MM-DDTHH:mm:ss') : dayjs().format('YYYY-MM-DDTHH:mm:ss'),
        companySituation: values.companySituation,
        studentPerformance: values.studentPerformance,
        guidanceFulfillment: values.guidanceFulfillment,
        score: values.score,
        hasProblem: values.hasProblem,
        problemDesc: values.problemDesc,
        attachmentUrl: values.attachmentUrl || '/uploads/inspections/sample_eval.pdf'
      });
      if (res.code === 200) {
        message.success('督导检查记录录入成功');
        setSubmitInspectModalVisible(false);
        inspectSubmitForm.resetFields();
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
    finally {
      setInspectSubmitLoading(false);
    }
  };

  // 下达限期整改通知
  const handleCreateRectification = async () => {
    if (!selectedInspection) return;
    try {
      const values = await rectifyForm.validateFields();
      setRectifyLoading(true);
      const res = await createRectification({
        inspectionId: selectedInspection.id,
        rectifyRequirements: values.rectifyRequirements,
        deadlineDate: values.deadlineDate.format('YYYY-MM-DD')
      });
      if (res.code === 200) {
        message.success('限期整改通知书已正式下达至学生工作台');
        setCreateRectifyModalVisible(false);
        rectifyForm.resetFields();
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
    finally {
      setRectifyLoading(false);
    }
  };

  // 学生提交整改报告
  const handleSubmitRectify = async () => {
    if (!selectedRectify) return;
    try {
      const values = await studentRectifyForm.validateFields();
      setStudentRectifyLoading(true);
      const res = await submitRectification(selectedRectify.id, {
        studentExplanation: values.studentExplanation,
        evidenceAttachmentUrl: values.evidenceAttachmentUrl || '/uploads/rectify/report_done.pdf'
      });
      if (res.code === 200) {
        message.success('整改落实报告提交成功，进入导师复核流程');
        setRectifySubmitModalVisible(false);
        studentRectifyForm.resetFields();
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
    finally {
      setStudentRectifyLoading(false);
    }
  };

  // 教师复核整改
  const handleReviewRectify = async () => {
    if (!selectedRectify) return;
    try {
      const values = await teacherReviewForm.validateFields();
      setTeacherReviewLoading(true);
      const res = await reviewRectification(selectedRectify.id, {
        action: values.action,
        reviewComment: values.reviewComment
      });
      if (res.code === 200) {
        message.success(values.action === 'PASSED' ? '整改成效复核通过，进入院系终审销号环节' : '复核未通过，已退回学生重新整改');
        setReviewModalVisible(false);
        teacherReviewForm.resetFields();
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
    finally {
      setTeacherReviewLoading(false);
    }
  };

  // 院系终审闭环销号
  const handleCloseRectify = async (id: number) => {
    try {
      const res = await closeRectification(id);
      if (res.code === 200) {
        message.success('整改工单已终审销号，完成全生命周期闭环');
        loadPlansAndInspections(selectedTaskId, filterPlanId);
      }
    } catch {}
  };

  // 检查列表列定义
  const inspectColumns: ColumnsType<InspectionVO> = [
    {
      title: '抽样批次',
      dataIndex: 'samplingBatchNo',
      key: 'samplingBatchNo',
      width: 130
    },
    {
      title: '受检学生',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 120,
      render: (n) => <strong>{n || '学生'}</strong>
    },
    {
      title: '学号',
      key: 'studentNo',
      width: 130,
      render: (_, r) => r.studentNumber || r.studentNo || '-'
    },
    {
      title: '所属班级',
      dataIndex: 'className',
      key: 'className',
      width: 130,
      render: (c) => c || '-'
    },
    {
      title: '指导教师',
      dataIndex: 'teacherName',
      key: 'teacherName',
      width: 110,
      render: (t) => t || '-'
    },
    {
      title: '检查形式',
      dataIndex: 'inspectionType',
      key: 'inspectionType',
      width: 110,
      render: (t) => <Tag color="blue">{t === 'ONSITE' ? '实地走访' : t === 'ONLINE' ? '线上核查' : t}</Tag>
    },
    {
      title: '督导评分',
      dataIndex: 'score',
      key: 'score',
      width: 100,
      render: (s) => (s !== null && s !== undefined ? <strong style={{ color: '#1677ff' }}>{s} 分</strong> : '-')
    },
    {
      title: '突出问题',
      dataIndex: 'hasProblem',
      key: 'hasProblem',
      width: 110,
      render: (p) => (p === 1 ? <Tag color="error">存在问题</Tag> : <Tag color="success">正常合格</Tag>)
    },
    {
      title: '督导状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (st) => getInspectionStatusTag(st)
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedInspection(record);
              setInspectDetailDrawerVisible(true);
            }}
          >
            查看详情
          </Button>

          {/* 录入督导 (仅限待检查状态) */}
          {(isTeacher || isDeptAdmin || isAdmin) && record.status === 'PENDING_INSPECT' && (
            <Button
              type="primary"
              size="small"
              icon={<AuditOutlined />}
              onClick={() => {
                setInspectRecordForSubmit(record);
                inspectSubmitForm.resetFields();
                setSubmitInspectModalVisible(true);
              }}
            >
              录入督导
            </Button>
          )}

          {/* 下达整改 (存在问题且未整改) */}
          {(isTeacher || isDeptAdmin || isAdmin) && record.hasProblem === 1 && record.status === 'INSPECTED' && (
            <Button
              type="primary"
              danger
              size="small"
              onClick={() => {
                setSelectedInspection(record);
                rectifyForm.resetFields();
                setCreateRectifyModalVisible(true);
              }}
            >
              下达整改
            </Button>
          )}
        </Space>
      )
    }
  ];

  // 整改列表列定义
  const rectifyColumns: ColumnsType<RectifyVO> = [
    {
      title: '整改单号',
      dataIndex: 'id',
      key: 'id',
      width: 90
    },
    {
      title: '受整改学生',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 120,
      render: (n) => <strong>{n || '学生'}</strong>
    },
    {
      title: '学号',
      key: 'studentNo',
      width: 130,
      render: (_, r) => r.studentNumber || r.studentNo || '-'
    },
    {
      title: '负责教师',
      dataIndex: 'teacherName',
      key: 'teacherName',
      width: 110,
      render: (t) => t || '-'
    },
    {
      title: '整改要求明细',
      dataIndex: 'rectifyRequirements',
      key: 'rectifyRequirements',
      ellipsis: true
    },
    {
      title: '整改截止日期',
      dataIndex: 'deadlineDate',
      key: 'deadlineDate',
      width: 140,
      render: (d, r) => (
        <span>
          {d}
          {r.isOverdue === 1 && <Tag color="error" style={{ marginLeft: 4 }}>逾期</Tag>}
        </span>
      )
    },
    {
      title: '整改流转状态',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (st) => getRectifyStatusTag(st)
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedRectify(record);
              setRectifyDetailDrawerVisible(true);
            }}
          >
            查看详情
          </Button>

          {/* 学生填报整改反馈 */}
          {isStudent && (record.status === 'PENDING_SUBMIT' || record.status === 'REJECTED') && (
            <Button
              type="primary"
              size="small"
              onClick={() => {
                setSelectedRectify(record);
                studentRectifyForm.resetFields();
                setRectifySubmitModalVisible(true);
              }}
            >
              填报成效
            </Button>
          )}

          {/* 教师复核 */}
          {(isTeacher || isAdmin) && record.status === 'PENDING_REVIEW' && (
            <Button
              type="primary"
              size="small"
              onClick={() => {
                setSelectedRectify(record);
                teacherReviewForm.resetFields();
                setReviewModalVisible(true);
              }}
            >
              教师复核
            </Button>
          )}

          {/* 院系终审销号闭环 */}
          {(isDeptAdmin || isAdmin) && record.status === 'PENDING_REVIEW' && (
            <Popconfirm
              title="确认予以闭环销号吗？"
              description="销号后整改工单将完成归档，不可再撤回。"
              onConfirm={() => handleCloseRectify(record.id)}
            >
              <Button type="primary" size="small" style={{ backgroundColor: '#52c41a' }}>
                销号闭环
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 顶部标题与批次选择 */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              中期检查督导与限期整改中心
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              涵盖中期教学抽样方案制定、现场督导记录录入、限期整改下达与闭环销号全流程
            </Paragraph>
          </div>

          <Space>
            <Text strong>当前实习批次：</Text>
            <Select
              style={{ width: 320 }}
              value={selectedTaskId}
              onChange={(val) => setSelectedTaskId(val)}
              options={tasks.map((t) => ({
                label: `${t.taskName} (${t.taskCode})`,
                value: t.id
              }))}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadPlansAndInspections(selectedTaskId, filterPlanId)}
            >
              刷新
            </Button>
          </Space>
        </div>
      </Card>

      {/* 核心双标签页 */}
      <Card>
        <Tabs
          defaultActiveKey="inspect"
          items={[
            {
              key: 'inspect',
              label: (
                <span>
                  <FileSearchOutlined />
                  中期检查督导 (API-074~077)
                </span>
              ),
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* 工具栏 */}
                  <Row justify="space-between" align="middle">
                    <Col>
                      {(isDeptAdmin || isAdmin) && (
                        <Button
                          type="primary"
                          icon={<PlusOutlined />}
                          onClick={() => {
                            planForm.resetFields();
                            setCreatePlanModalVisible(true);
                          }}
                        >
                          编制督导检查方案
                        </Button>
                      )}
                    </Col>
                    <Col>
                      <Space>
                        <Text>方案筛选：</Text>
                        <Select
                          style={{ width: 260 }}
                          value={filterPlanId}
                          placeholder="全部检查方案"
                          allowClear
                          onChange={(val) => setFilterPlanId(val)}
                          options={plans.map((p) => ({
                            label: p.planName,
                            value: p.id
                          }))}
                        />
                      </Space>
                    </Col>
                  </Row>

                  {/* 方案卡片摘要 */}
                  {plans.length > 0 && (
                    <Row gutter={[16, 16]}>
                      {plans.map((p) => (
                        <Col xs={24} md={12} lg={8} key={p.id}>
                          <Card
                            size="small"
                            title={<strong>{p.planName}</strong>}
                            extra={<Tag color={p.status === 'PUBLISHED' ? 'success' : 'default'}>{p.status === 'PUBLISHED' ? '已发布抽样' : '草稿'}</Tag>}
                            style={{ backgroundColor: '#fafafa' }}
                          >
                            <div style={{ fontSize: 13, lineHeight: '22px' }}>
                              <div>抽样模式: {p.samplingMode === 'RANDOM_RATIO' ? `随机抽取 (${p.samplingRatio || 100}%)` : '建制班级整选'}</div>
                              <div>检查周期: {p.startDate} ~ {p.endDate}</div>
                              <div>督导专家组: {p.expertGroup || '院系教学督导组'}</div>
                            </div>
                            {(isDeptAdmin || isAdmin) && p.status !== 'COMPLETED' && (
                              <div style={{ textAlign: 'right', marginTop: 8 }}>
                                <Button
                                  type="primary"
                                  size="small"
                                  onClick={() => handleExecuteSampling(p.id)}
                                >
                                  执行按比例抽样
                                </Button>
                              </div>
                            )}
                          </Card>
                        </Col>
                      ))}
                    </Row>
                  )}

                  {/* 检查明细数据表格 */}
                  <Table
                    columns={inspectColumns}
                    dataSource={inspections}
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条督导检查记录` }}
                    scroll={{ x: 1200 }}
                  />
                </div>
              )
            },
            {
              key: 'rectify',
              label: (
                <span>
                  <SafetyCertificateOutlined />
                  限期整改通知与闭环 (API-078~081)
                </span>
              ),
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <Table
                    columns={rectifyColumns}
                    dataSource={rectifications}
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔整改工单` }}
                    scroll={{ x: 1200 }}
                  />
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* 编制方案 Modal */}
      <Modal
        title="编制中期教学检查督导方案 (API-074)"
        open={createPlanModalVisible}
        onCancel={() => setCreatePlanModalVisible(false)}
        onOk={handleCreatePlan}
        confirmLoading={planLoading}
        destroyOnClose
        width={640}
      >
        <Form form={planForm} layout="vertical" style={{ marginTop: 16 }} initialValues={{ samplingMode: 'RANDOM_RATIO', samplingRatio: 30 }}>
          <Form.Item name="planName" label="检查方案全称" rules={[{ required: true, message: '请输入检查方案全称' }]}>
            <Input placeholder="如 2026届顶岗实习中期教学质量专项检查方案" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="samplingMode" label="抽样抽查机制" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '按比例随机抽取 (RANDOM_RATIO)', value: 'RANDOM_RATIO' },
                    { label: '按班级全量选定 (CLASS_SELECT)', value: 'CLASS_SELECT' }
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="samplingRatio" label="抽样比例 (%)" rules={[{ required: true }]}>
                <InputNumber min={1} max={100} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="dateRange" label="检查开展起止时间" rules={[{ required: true, message: '请选择起止时间' }]}>
            <RangePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="expertGroup" label="督导专家组成员">
            <Input placeholder="如 院督导组组长、教学院长、专业负责人" />
          </Form.Item>

          <Form.Item name="remark" label="检查工作要点备注">
            <Input.TextArea rows={2} placeholder="重点核查学生到岗率、安全保障及指导教师日常走访履职情况..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 录入督导检查记录 Modal */}
      <Modal
        title="录入中期督导检查记录 (API-076)"
        open={submitInspectModalVisible}
        onCancel={() => setSubmitInspectModalVisible(false)}
        onOk={handleSubmitInspect}
        confirmLoading={inspectSubmitLoading}
        destroyOnClose
        width={640}
      >
        <Form form={inspectSubmitForm} layout="vertical" style={{ marginTop: 16 }} initialValues={{ inspectionType: 'ONSITE', score: 85, hasProblem: 0 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="inspectionType" label="检查形式" rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '现场走访 (ONSITE)', value: 'ONSITE' },
                    { label: '线上连线 (ONLINE)', value: 'ONLINE' },
                    { label: '电话抽查 (PHONE)', value: 'PHONE' }
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="score" label="督导综合评分 (0-100)" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="companySituation" label="企业走访与现场安全状况">
            <Input.TextArea rows={2} placeholder="工作场地环境、劳保用品配备、带教导师指导配备情况..." />
          </Form.Item>

          <Form.Item name="studentPerformance" label="学生在岗工作与学习表现">
            <Input.TextArea rows={2} placeholder="出勤率、业务掌握熟练度、专业对口吻合度..." />
          </Form.Item>

          <Form.Item name="hasProblem" label="是否存在突出问题">
            <Radio.Group>
              <Radio value={0}><Tag color="success">正常达标</Tag></Radio>
              <Radio value={1}><Tag color="error">存在问题 (需整改)</Tag></Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.hasProblem !== cur.hasProblem}
          >
            {({ getFieldValue }) =>
              getFieldValue('hasProblem') === 1 ? (
                <Form.Item name="problemDesc" label="突出问题详述" rules={[{ required: true, message: '请详述发现的问题' }]}>
                  <Input.TextArea rows={2} placeholder="如：未按规范佩戴防护手套、周报提交严重滞后等..." />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      {/* 下达整改通知 Modal */}
      <Modal
        title="下达限期整改通知书 (API-078)"
        open={createRectifyModalVisible}
        onCancel={() => setCreateRectifyModalVisible(false)}
        onOk={handleCreateRectification}
        confirmLoading={rectifyLoading}
        destroyOnClose
      >
        <Form form={rectifyForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="rectifyRequirements"
            label="整改要求明细"
            rules={[{ required: true, message: '请输入整改要求明细' }]}
          >
            <Input.TextArea rows={4} placeholder="清晰罗列须整改的具体事项、合规标准与验收准则..." />
          </Form.Item>

          <Form.Item
            name="deadlineDate"
            label="限期整改截止日期"
            rules={[{ required: true, message: '请选择整改截止日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 学生填报整改成效 Modal */}
      <Modal
        title="填报整改落实成效 (API-079)"
        open={rectifySubmitModalVisible}
        onCancel={() => setRectifySubmitModalVisible(false)}
        onOk={handleSubmitRectify}
        confirmLoading={studentRectifyLoading}
        destroyOnClose
      >
        <Form form={studentRectifyForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="studentExplanation"
            label="整改落实举措与自查说明"
            rules={[{ required: true, message: '请输入整改落实说明' }]}
          >
            <Input.TextArea rows={4} placeholder="详细阐述针对督导发现问题所采取的具体整改措施与落实情况..." />
          </Form.Item>

          <Form.Item name="evidenceAttachmentUrl" label="整改成效佐证材料URL">
            <Input placeholder="如 /uploads/rectify/evidence_photo.jpg" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 教师复核 Modal */}
      <Modal
        title="教师复核整改成效 (API-080)"
        open={reviewModalVisible}
        onCancel={() => setReviewModalVisible(false)}
        onOk={handleReviewRectify}
        confirmLoading={teacherReviewLoading}
        destroyOnClose
      >
        <Form form={teacherReviewForm} layout="vertical" style={{ marginTop: 16 }} initialValues={{ action: 'PASSED' }}>
          <Form.Item name="action" label="复核结论" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="PASSED"><Tag color="success">复核合格 (呈送院系销号)</Tag></Radio>
              <Radio value="REJECTED"><Tag color="error">复核不合格 (退回继续整改)</Tag></Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="reviewComment"
            label="教师复核评价意见"
            rules={[{ required: true, message: '请输入复核意见' }]}
          >
            <Input.TextArea rows={3} placeholder="详述对整改落实情况的现场或线上核查评价..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 检查详情 Drawer */}
      <Drawer
        title="督导检查详情"
        width={600}
        open={inspectDetailDrawerVisible}
        onClose={() => setInspectDetailDrawerVisible(false)}
        destroyOnClose
      >
        {selectedInspection && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="抽样批次">{selectedInspection.samplingBatchNo}</Descriptions.Item>
            <Descriptions.Item label="受检学生">{selectedInspection.studentName} ({selectedInspection.studentNumber || selectedInspection.studentNo})</Descriptions.Item>
            <Descriptions.Item label="所属班级">{selectedInspection.className || '-'}</Descriptions.Item>
            <Descriptions.Item label="指导教师">{selectedInspection.teacherName || '-'}</Descriptions.Item>
            <Descriptions.Item label="督导检查形式">{selectedInspection.inspectionType}</Descriptions.Item>
            <Descriptions.Item label="综合得分">{selectedInspection.score !== null && selectedInspection.score !== undefined ? `${selectedInspection.score} 分` : '待评分'}</Descriptions.Item>
            <Descriptions.Item label="问题状况">{selectedInspection.hasProblem === 1 ? '存在突出问题' : '正常合格'}</Descriptions.Item>
            {selectedInspection.problemDesc && (
              <Descriptions.Item label="问题详述">{selectedInspection.problemDesc}</Descriptions.Item>
            )}
            <Descriptions.Item label="企业安全现场">{selectedInspection.companySituation || '无记录'}</Descriptions.Item>
            <Descriptions.Item label="学生工作表现">{selectedInspection.studentPerformance || '无记录'}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>

      {/* 整改详情 Drawer */}
      <Drawer
        title="限期整改工单全生命周期追踪"
        width={640}
        open={rectifyDetailDrawerVisible}
        onClose={() => setRectifyDetailDrawerVisible(false)}
        destroyOnClose
      >
        {selectedRectify && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="整改单号">{selectedRectify.id}</Descriptions.Item>
              <Descriptions.Item label="受整改学生">{selectedRectify.studentName} ({selectedRectify.studentNumber || selectedRectify.studentNo})</Descriptions.Item>
              <Descriptions.Item label="负责导师">{selectedRectify.teacherName || '-'}</Descriptions.Item>
              <Descriptions.Item label="整改截止日期">{selectedRectify.deadlineDate}</Descriptions.Item>
              <Descriptions.Item label="整改要求明细">{selectedRectify.rectifyRequirements}</Descriptions.Item>
              <Descriptions.Item label="当前流转状态">{getRectifyStatusTag(selectedRectify.status)}</Descriptions.Item>
              <Descriptions.Item label="学生落实举措">{selectedRectify.studentExplanation || '待学生填报'}</Descriptions.Item>
              <Descriptions.Item label="佐证凭证">{selectedRectify.evidenceAttachmentUrl || '无上传凭证'}</Descriptions.Item>
              <Descriptions.Item label="导师复核批语">{selectedRectify.reviewComment || '待复核'}</Descriptions.Item>
              <Descriptions.Item label="销号闭环时间">{selectedRectify.closeTime || '未销号'}</Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Drawer>
    </div>
  );
};
