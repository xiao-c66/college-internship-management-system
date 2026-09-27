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
  Timeline,
  Descriptions,
  Progress,
  Typography,
  message,
  Divider,
  Statistic,
  Empty,
  DatePicker
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CalendarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  AuditOutlined,
  EyeOutlined,
  PlusOutlined,
  BarChartOutlined,
  TeamOutlined,
  EnvironmentOutlined,
  PhoneOutlined,
  VideoCameraOutlined,
  MailOutlined,
  HistoryOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  listWeeklyReports,
  getWeeklyReportDetail,
  saveOrSubmitWeeklyReport,
  reviewWeeklyReport,
  getWeeklyMonitorSummary,
  createGuidanceRecord,
  listGuidanceRecords,
  submitGuidanceFeedback,
  WeeklyReportVO,
  WeeklyReportDetailVO,
  WeeklyMonitorSummaryVO,
  GuidanceRecordVO
} from '../../api/weekly';
import { getTaskList, getTaskStudents, TaskItem, TaskStudentItem } from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

// 周报状态标签
const getReportStatusTag = (status: string, isOverdue?: number) => {
  switch (status) {
    case 'DRAFT':
      return <Tag color="default">草稿暂存</Tag>;
    case 'SUBMITTED':
      return (
        <Space size={4}>
          <Tag color="processing">待批阅</Tag>
          {isOverdue === 1 && <Tag color="error">逾期提交</Tag>}
        </Space>
      );
    case 'REVIEWED':
      return <Tag color="success">已批阅归档</Tag>;
    case 'RETURNED':
      return <Tag color="warning">退回整改</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
};

// 指导类型标签
const getGuidanceTypeTag = (type: string) => {
  switch (type) {
    case 'ONSITE':
      return <Tag icon={<EnvironmentOutlined />} color="green">实地走访</Tag>;
    case 'ONLINE':
      return <Tag icon={<VideoCameraOutlined />} color="blue">线上连线</Tag>;
    case 'PHONE':
      return <Tag icon={<PhoneOutlined />} color="cyan">电话抽查</Tag>;
    case 'EMAIL_OTHER':
      return <Tag icon={<MailOutlined />} color="purple">邮件/其他</Tag>;
    default:
      return <Tag>{type}</Tag>;
  }
};

export const WeeklyPage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';
  const isDeptAdmin = userType === 'DEPT_ADMIN';
  const isAdmin = userType === 'SYS_ADMIN';

  // 1. 任务批次状态
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  // 2. 周报列表与详情状态
  const [reports, setReports] = useState<WeeklyReportVO[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedReport, setSelectedReport] = useState<WeeklyReportDetailVO | null>(null);

  // 3. 周报编辑 Drawer 状态
  const [editDrawerVisible, setEditDrawerVisible] = useState(false);
  const [editForm] = Form.useForm();
  const [saveLoading, setSaveLoading] = useState(false);

  // 4. 导师批阅表单状态
  const [reviewForm] = Form.useForm();
  const [reviewLoading, setReviewLoading] = useState(false);

  // 5. 过程指导状态
  const [guidances, setGuidances] = useState<GuidanceRecordVO[]>([]);
  const [guidanceTypeFilter, setGuidanceTypeFilter] = useState<string>('');
  const [guidanceModalVisible, setGuidanceModalVisible] = useState(false);
  const [guidanceForm] = Form.useForm();
  const [guidanceLoading, setGuidanceLoading] = useState(false);
  const [feedbackModalVisible, setFeedbackModalVisible] = useState(false);
  const [feedbackRecordId, setFeedbackRecordId] = useState<number | null>(null);
  const [feedbackForm] = Form.useForm();
  const [feedbackLoading, setFeedbackLoading] = useState(false);
  const [mentoredStudents, setMentoredStudents] = useState<TaskStudentItem[]>([]);

  // 6. 监控大盘数据
  const [monitorSummary, setMonitorSummary] = useState<WeeklyMonitorSummaryVO | null>(null);

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

  // 加载周报列表
  const loadReports = async (taskId?: number, status?: string) => {
    if (!taskId) return;
    setLoading(true);
    try {
      const res = await listWeeklyReports({
        taskId,
        status: status || undefined
      });
      if (res.code === 200 && res.data) {
        setReports(res.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  // 加载指导台账
  const loadGuidances = async (taskId?: number, type?: string) => {
    if (!taskId) return;
    try {
      const res = await listGuidanceRecords({
        taskId,
        guidanceType: type || undefined
      });
      if (res.code === 200 && res.data) {
        setGuidances(res.data);
      }
    } catch {}
  };

  // 加载监控大盘
  const loadMonitorData = async (taskId?: number) => {
    if (!taskId) return;
    try {
      const res = await getWeeklyMonitorSummary({ taskId });
      if (res.code === 200 && res.data) {
        setMonitorSummary(res.data);
      }
    } catch {}
  };

  // 统一加载当前任务所有视图数据
  useEffect(() => {
    if (!selectedTaskId) return;
    loadReports(selectedTaskId, statusFilter);
    loadGuidances(selectedTaskId, guidanceTypeFilter);
    if (!isStudent) {
      loadMonitorData(selectedTaskId);
    }
    // 教师端加载圈定带教学生名单
    if (isTeacher) {
      getTaskStudents(selectedTaskId).then((res) => {
        if (res.code === 200 && res.data) {
          setMentoredStudents(res.data);
        }
      }).catch(() => {});
    }
  }, [selectedTaskId, isStudent, isTeacher]);

  // 查看周报详情
  const handleOpenDetail = async (record: WeeklyReportVO) => {
    try {
      const res = await getWeeklyReportDetail(record.id);
      if (res.code === 200 && res.data) {
        setSelectedReport(res.data);
        setDetailDrawerVisible(true);
        reviewForm.resetFields();
        reviewForm.setFieldsValue({ action: 'APPROVE', score: 90, reviewComment: '本周实习技术任务按计划顺利推进，总结认真规范。' });
      }
    } catch {}
  };

  // 学生新建/修改周报
  const handleOpenEdit = (existing?: WeeklyReportVO) => {
    editForm.resetFields();
    if (existing) {
      editForm.setFieldsValue({
        weekNumber: existing.weekNumber,
        workContent: (existing as any).workContent || '负责微服务核心业务接口编写与单元测试开发',
        workSummary: (existing as any).workSummary || '熟悉了Spring Boot业务研发规约，完成阶段技术指标',
        problemEncountered: (existing as any).problemEncountered || '暂无重大技术阻碍',
        nextWeekPlan: (existing as any).nextWeekPlan || '跟进联调测试与导师日常指导要求',
        attachmentUrl: (existing as any).attachmentUrl || ''
      });
    } else {
      const nextWeek = reports.length + 1;
      editForm.setFieldsValue({
        weekNumber: nextWeek,
        workContent: '',
        workSummary: '',
        problemEncountered: '',
        nextWeekPlan: ''
      });
    }
    setEditDrawerVisible(true);
  };

  // 学生保存/提交周报
  const handleSaveReport = async (action: 'DRAFT' | 'SUBMIT') => {
    if (!selectedTaskId) return;
    try {
      const values = await editForm.validateFields();
      setSaveLoading(true);
      const res = await saveOrSubmitWeeklyReport({
        taskId: selectedTaskId,
        weekNumber: values.weekNumber,
        action,
        workContent: values.workContent,
        workSummary: values.workSummary,
        problemEncountered: values.problemEncountered,
        nextWeekPlan: values.nextWeekPlan,
        attachmentUrl: values.attachmentUrl || '/uploads/weekly/report_attachment.pdf'
      });
      if (res.code === 200) {
        message.success(action === 'DRAFT' ? '周报草稿暂存成功' : '周报已正式提交导师批阅');
        setEditDrawerVisible(false);
        loadReports(selectedTaskId, statusFilter);
      }
    } catch {}
    finally {
      setSaveLoading(false);
    }
  };

  // 导师执行批阅/退回
  const handleExecuteReview = async () => {
    if (!selectedReport) return;
    try {
      const values = await reviewForm.validateFields();
      setReviewLoading(true);
      const res = await reviewWeeklyReport(selectedReport.id, {
        action: values.action,
        score: values.score,
        reviewComment: values.reviewComment
      });
      if (res.code === 200) {
        message.success(values.action === 'APPROVE' ? '周报批阅打分成功' : '周报已退回学生整改');
        setDetailDrawerVisible(false);
        loadReports(selectedTaskId, statusFilter);
        loadMonitorData(selectedTaskId);
      }
    } catch {}
    finally {
      setReviewLoading(false);
    }
  };

  // 导师登记指导台账
  const handleCreateGuidance = async () => {
    if (!selectedTaskId) return;
    try {
      const values = await guidanceForm.validateFields();
      setGuidanceLoading(true);
      const res = await createGuidanceRecord({
        taskId: selectedTaskId,
        studentId: values.studentId,
        guidanceDate: values.guidanceDate ? values.guidanceDate.format('YYYY-MM-DD HH:mm:ss') : dayjs().format('YYYY-MM-DD HH:mm:ss'),
        guidanceType: values.guidanceType,
        location: values.location || '单位现场/实训机房',
        contentSummary: values.contentSummary,
        followupActions: values.followupActions || '要求学生加强岗位安全防范，按时提交高质量周报'
      });
      if (res.code === 200) {
        message.success('过程指导与走访台账登记成功');
        setGuidanceModalVisible(false);
        guidanceForm.resetFields();
        loadGuidances(selectedTaskId, guidanceTypeFilter);
      }
    } catch {}
    finally {
      setGuidanceLoading(false);
    }
  };

  // 学生提交在岗反馈
  const handleSubmitFeedback = async () => {
    if (!feedbackRecordId) return;
    try {
      const values = await feedbackForm.validateFields();
      setFeedbackLoading(true);
      const res = await submitGuidanceFeedback(feedbackRecordId, {
        studentFeedback: values.studentFeedback
      });
      if (res.code === 200) {
        message.success('在岗确认反馈提交成功，已完成双向确认闭环');
        setFeedbackModalVisible(false);
        feedbackForm.resetFields();
        loadGuidances(selectedTaskId, guidanceTypeFilter);
      }
    } catch {}
    finally {
      setFeedbackLoading(false);
    }
  };

  // 周报表格列定义
  const reportColumns: ColumnsType<WeeklyReportVO> = [
    {
      title: '周次',
      dataIndex: 'weekNumber',
      key: 'weekNumber',
      width: 100,
      render: (w) => <strong style={{ color: '#1677ff' }}>第 {w} 周</strong>
    },
    ...(!isStudent ? [
      {
        title: '学生姓名',
        dataIndex: 'studentName',
        key: 'studentName',
        width: 120,
        render: (name: string) => <strong>{name}</strong>
      },
      {
        title: '学号',
        dataIndex: 'studentNumber',
        key: 'studentNumber',
        width: 130
      },
      {
        title: '所属班级',
        dataIndex: 'className',
        key: 'className',
        width: 140
      }
    ] : []),
    {
      title: '申报状态',
      key: 'status',
      width: 140,
      render: (_, r) => getReportStatusTag(r.status, r.isOverdue)
    },
    {
      title: '成绩得分',
      dataIndex: 'score',
      key: 'score',
      width: 100,
      render: (s) => (s !== null && s !== undefined ? <strong style={{ color: '#52c41a' }}>{s} 分</strong> : '-')
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      key: 'submitTime',
      width: 180,
      render: (t) => t || '-'
    },
    {
      title: '批阅导师/意见',
      key: 'review',
      ellipsis: true,
      render: (_, r) => r.reviewComment ? (
        <span>{r.teacherName ? `[${r.teacherName}] ` : ''}{r.reviewComment}</span>
      ) : '-'
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (v) => <Tag>v{v}</Tag>
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {isTeacher && record.status === 'SUBMITTED' ? (
            <Button
              type="primary"
              size="small"
              icon={<AuditOutlined />}
              onClick={() => handleOpenDetail(record)}
            >
              审阅批改
            </Button>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleOpenDetail(record)}
            >
              查看详情
            </Button>
          )}
          {isStudent && (record.status === 'DRAFT' || record.status === 'RETURNED') && (
            <Button
              type="primary"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenEdit(record)}
            >
              修改重报
            </Button>
          )}
        </Space>
      )
    }
  ];

  // 指导台账表格列定义
  const guidanceColumns: ColumnsType<GuidanceRecordVO> = [
    {
      title: '指导时间',
      dataIndex: 'guidanceDate',
      key: 'guidanceDate',
      width: 170
    },
    ...(!isStudent ? [
      {
        title: '受访学生',
        dataIndex: 'studentName',
        key: 'studentName',
        width: 120,
        render: (n: string) => <strong>{n}</strong>
      },
      {
        title: '学号',
        dataIndex: 'studentNumber',
        key: 'studentNumber',
        width: 130
      }
    ] : [
      {
        title: '指导教师',
        dataIndex: 'teacherName',
        key: 'teacherName',
        width: 120,
        render: (n: string) => <strong>{n}</strong>
      }
    ]),
    {
      title: '指导形式',
      dataIndex: 'guidanceType',
      key: 'guidanceType',
      width: 130,
      render: (t) => getGuidanceTypeTag(t)
    },
    {
      title: '交流地点',
      dataIndex: 'location',
      key: 'location',
      width: 150,
      ellipsis: true
    },
    {
      title: '交流指导要点',
      dataIndex: 'contentSummary',
      key: 'contentSummary',
      ellipsis: true
    },
    {
      title: '在岗反馈状态',
      key: 'feedback',
      width: 140,
      render: (_, r) => (
        r.feedbackStatus === 'CONFIRMED' ? (
          <Tag color="success">学生已确认反馈</Tag>
        ) : (
          <Tag color="warning">待学生在岗反馈</Tag>
        )
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        isStudent && record.feedbackStatus === 'UNCONFIRMED' ? (
          <Button
            type="primary"
            size="small"
            onClick={() => {
              setFeedbackRecordId(record.id);
              feedbackForm.resetFields();
              setFeedbackModalVisible(true);
            }}
          >
            在岗确认反馈
          </Button>
        ) : (
          <Button
            type="link"
            size="small"
            onClick={() => {
              Modal.info({
                title: '过程指导与走访详细记录',
                width: 600,
                content: (
                  <Descriptions bordered column={1} size="small" style={{ marginTop: 16 }}>
                    <Descriptions.Item label="指导时间">{record.guidanceDate}</Descriptions.Item>
                    <Descriptions.Item label="指导形式">{record.guidanceType}</Descriptions.Item>
                    <Descriptions.Item label="指导地点">{record.location || '-'}</Descriptions.Item>
                    <Descriptions.Item label="交流要点">{record.contentSummary}</Descriptions.Item>
                    <Descriptions.Item label="跟进要求">{record.followupActions || '-'}</Descriptions.Item>
                    <Descriptions.Item label="学生在岗反馈">{record.studentFeedback || '暂未反馈'}</Descriptions.Item>
                  </Descriptions>
                )
              });
            }}
          >
            查看详情
          </Button>
        )
      )
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 顶部标题与任务批次切换 */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              实习周报与过程指导全流程工作台
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              覆盖学生周报多版本流转与快照、导师逐份审阅打分、实地走访台账与院系提交率大盘
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
          </Space>
        </div>
      </Card>

      {/* 核心业务标签页 */}
      <Card>
        <Tabs
          defaultActiveKey="reports"
          items={[
            {
              key: 'reports',
              label: (
                <span>
                  <CalendarOutlined />
                  周报提报与审阅流转
                </span>
              ),
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* 筛选与操作栏 */}
                  <Row justify="space-between" align="middle">
                    <Col>
                      <Space>
                        <Text>状态筛选：</Text>
                        <Select
                          style={{ width: 180 }}
                          value={statusFilter}
                          onChange={(val) => {
                            setStatusFilter(val);
                            loadReports(selectedTaskId, val);
                          }}
                          options={[
                            { label: '全部状态', value: '' },
                            { label: '草稿暂存 (DRAFT)', value: 'DRAFT' },
                            { label: '待导师批阅 (SUBMITTED)', value: 'SUBMITTED' },
                            { label: '已批阅归档 (REVIEWED)', value: 'REVIEWED' },
                            { label: '退回整改 (RETURNED)', value: 'RETURNED' }
                          ]}
                        />
                        <Button
                          icon={<ReloadOutlined />}
                          onClick={() => {
                            setStatusFilter('');
                            loadReports(selectedTaskId, '');
                          }}
                        >
                          刷新
                        </Button>
                      </Space>
                    </Col>
                    <Col>
                      {isStudent && (
                        <Button
                          type="primary"
                          icon={<PlusOutlined />}
                          onClick={() => handleOpenEdit()}
                        >
                          撰写本周新周报
                        </Button>
                      )}
                    </Col>
                  </Row>

                  {/* 周报列表表格 */}
                  <Table
                    columns={reportColumns}
                    dataSource={reports}
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 篇周报记录` }}
                    scroll={{ x: 1100 }}
                  />
                </div>
              )
            },
            {
              key: 'guidance',
              label: (
                <span>
                  <TeamOutlined />
                  过程指导与走访台账
                </span>
              ),
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <Row justify="space-between" align="middle">
                    <Col>
                      <Space>
                        <Text>指导形式：</Text>
                        <Select
                          style={{ width: 180 }}
                          value={guidanceTypeFilter}
                          onChange={(val) => {
                            setGuidanceTypeFilter(val);
                            loadGuidances(selectedTaskId, val);
                          }}
                          options={[
                            { label: '全部形式', value: '' },
                            { label: '实地走访 (ONSITE)', value: 'ONSITE' },
                            { label: '线上连线 (ONLINE)', value: 'ONLINE' },
                            { label: '电话抽查 (PHONE)', value: 'PHONE' },
                            { label: '邮件/其他 (EMAIL_OTHER)', value: 'EMAIL_OTHER' }
                          ]}
                        />
                      </Space>
                    </Col>
                    <Col>
                      {isTeacher && (
                        <Button
                          type="primary"
                          icon={<PlusOutlined />}
                          onClick={() => {
                            guidanceForm.resetFields();
                            setGuidanceModalVisible(true);
                          }}
                        >
                          登记过程指导/走访记录
                        </Button>
                      )}
                    </Col>
                  </Row>

                  <Table
                    columns={guidanceColumns}
                    dataSource={guidances}
                    rowKey="id"
                    pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条过程指导记录` }}
                    scroll={{ x: 1000 }}
                  />
                </div>
              )
            },
            ...(!isStudent ? [
              {
                key: 'monitor',
                label: (
                  <span>
                    <BarChartOutlined />
                    周报过程管理监控看板
                  </span>
                ),
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                    {/* 指标大卡片 */}
                    <Row gutter={[16, 16]}>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="圈定学生总数" value={monitorSummary?.totalStudents || 0} suffix="人" />
                        </Card>
                      </Col>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="累计应交周报" value={monitorSummary?.totalExpectedReports || 0} suffix="篇" />
                        </Card>
                      </Col>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="实际已交周报" value={monitorSummary?.totalSubmittedReports || 0} suffix="篇" valueStyle={{ color: '#1677ff' }} />
                        </Card>
                      </Col>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="按期提交周报" value={monitorSummary?.totalOnTimeReports || 0} suffix="篇" valueStyle={{ color: '#52c41a' }} />
                        </Card>
                      </Col>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="导师已批阅" value={monitorSummary?.totalReviewedReports || 0} suffix="篇" valueStyle={{ color: '#722ed1' }} />
                        </Card>
                      </Col>
                      <Col xs={12} sm={8} lg={4}>
                        <Card size="small" style={{ backgroundColor: '#fafafa' }}>
                          <Statistic title="待批阅周报" value={monitorSummary?.totalPendingReports || 0} suffix="篇" valueStyle={{ color: '#faad14' }} />
                        </Card>
                      </Col>
                    </Row>

                    {/* 进度条统计 */}
                    <Row gutter={[20, 20]}>
                      <Col xs={24} md={8}>
                        <Card title="周报总体提交率" size="small">
                          <Progress
                            type="circle"
                            percent={Number(monitorSummary?.submissionRate || 0)}
                            format={(p) => `${p}%`}
                            strokeColor="#1677ff"
                          />
                        </Card>
                      </Col>
                      <Col xs={24} md={8}>
                        <Card title="导师审阅批改率" size="small">
                          <Progress
                            type="circle"
                            percent={Number(monitorSummary?.reviewRate || 0)}
                            format={(p) => `${p}%`}
                            strokeColor="#722ed1"
                          />
                        </Card>
                      </Col>
                      <Col xs={24} md={8}>
                        <Card title="按期提报合格率" size="small">
                          <Progress
                            type="circle"
                            percent={Number(monitorSummary?.onTimeRate || 0)}
                            format={(p) => `${p}%`}
                            strokeColor="#52c41a"
                          />
                        </Card>
                      </Col>
                    </Row>

                    {/* 各周次明细表格 */}
                    {monitorSummary?.weekStats && monitorSummary.weekStats.length > 0 && (
                      <Card title="各周次提交与按期率明细看板" size="small">
                        <Table
                          dataSource={monitorSummary.weekStats}
                          rowKey="weekNumber"
                          pagination={false}
                          columns={[
                            { title: '周次序号', dataIndex: 'weekNumber', key: 'weekNumber', render: (w) => `第 ${w} 周` },
                            { title: '应交数', dataIndex: 'expectedCount', key: 'expectedCount' },
                            { title: '实交数', dataIndex: 'submittedCount', key: 'submittedCount' },
                            { title: '已批阅数', dataIndex: 'reviewedCount', key: 'reviewedCount' },
                            { title: '逾期数', dataIndex: 'overdueCount', key: 'overdueCount', render: (c) => c > 0 ? <Text type="danger">{c}</Text> : c },
                            { title: '提报率', dataIndex: 'submitRate', key: 'submitRate', render: (r) => `${r}%` }
                          ]}
                        />
                      </Card>
                    )}
                  </div>
                )
              }
            ] : [])
          ]}
        />
      </Card>

      {/* 周报详情与批阅抽屉 */}
      <Drawer
        title={
          selectedReport
            ? `${selectedReport.studentName || '学生'} - 第 ${selectedReport.weekNumber} 周实习周报详情 (v${selectedReport.version})`
            : '周报详情'
        }
        width={720}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
        destroyOnClose
      >
        {selectedReport && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* 顶部状态与成绩 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>{getReportStatusTag(selectedReport.status, selectedReport.isOverdue)}</div>
              {selectedReport.score !== null && selectedReport.score !== undefined && (
                <div style={{ fontSize: 18 }}>
                  成绩：<strong style={{ color: '#52c41a', fontSize: 24 }}>{selectedReport.score}</strong> 分
                </div>
              )}
            </div>

            {/* 周报正文四大要素 */}
            <Card size="small" title="📝 周报正文四大要素">
              <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="本周工作内容">
                  <div style={{ whiteSpace: 'pre-wrap' }}>{selectedReport.workContent || '无记录'}</div>
                </Descriptions.Item>
                <Descriptions.Item label="工作总结与专业收获">
                  <div style={{ whiteSpace: 'pre-wrap' }}>{selectedReport.workSummary || '无记录'}</div>
                </Descriptions.Item>
                <Descriptions.Item label="遇到问题及解决途径">
                  <div style={{ whiteSpace: 'pre-wrap' }}>{selectedReport.problemEncountered || '无重大技术问题'}</div>
                </Descriptions.Item>
                <Descriptions.Item label="下周工作计划">
                  <div style={{ whiteSpace: 'pre-wrap' }}>{selectedReport.nextWeekPlan || '按计划推进'}</div>
                </Descriptions.Item>
                {selectedReport.attachmentUrl && (
                  <Descriptions.Item label="相关附件">
                    <a href={selectedReport.attachmentUrl} target="_blank" rel="noreferrer">
                      查看/下载随周报提交附件凭证
                    </a>
                  </Descriptions.Item>
                )}
              </Descriptions>
            </Card>

            {/* 导师批阅评语卡片 */}
            {selectedReport.reviewComment && (
              <Card size="small" title="👨‍🏫 指导教师审阅批语">
                <Paragraph>{selectedReport.reviewComment}</Paragraph>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  批阅人: {selectedReport.reviewerName || selectedReport.teacherName} | 批阅时间: {selectedReport.reviewTime || '-'}
                </Text>
              </Card>
            )}

            {/* 版本快照流转时间轴 */}
            {selectedReport.historyList && selectedReport.historyList.length > 0 && (
              <Card size="small" title={<span><HistoryOutlined /> 历史版本快照归档</span>}>
                <Timeline
                  items={selectedReport.historyList.map((h) => ({
                    children: (
                      <div>
                        <strong>版本 v{h.version}</strong> - {h.submitTime || h.createTime}
                        <div style={{ fontSize: 13, color: '#595959', marginTop: 4 }}>
                          工作摘要: {h.workSummary?.slice(0, 60)}...
                        </div>
                        {h.reviewComment && (
                          <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>
                            历史批语: {h.reviewComment} (得分: {h.score ?? '-'})
                          </div>
                        )}
                      </div>
                    )
                  }))}
                />
              </Card>
            )}

            {/* 导师审阅操作表单 */}
            {isTeacher && selectedReport.status === 'SUBMITTED' && (
              <Card size="small" title={<span><AuditOutlined /> 审阅批改操作</span>} style={{ backgroundColor: '#fafafa' }}>
                <Form form={reviewForm} layout="vertical">
                  <Form.Item name="action" label="审阅决定" rules={[{ required: true }]}>
                    <Radio.Group>
                      <Radio value="APPROVE"><Tag color="success">批阅通过并打分</Tag></Radio>
                      <Radio value="RETURN"><Tag color="warning">退回学生整改</Tag></Radio>
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, cur) => prev.action !== cur.action}
                  >
                    {({ getFieldValue }) =>
                      getFieldValue('action') === 'APPROVE' ? (
                        <Form.Item
                          name="score"
                          label="周报评分 (0.00 - 100.00 分)"
                          rules={[{ required: true, message: '请录入周报成绩评分' }]}
                        >
                          <InputNumber min={0} max={100} precision={1} style={{ width: 160 }} />
                        </Form.Item>
                      ) : null
                    }
                  </Form.Item>

                  <Form.Item
                    name="reviewComment"
                    label="审阅意见 / 退回修改要求"
                    rules={[{ required: true, message: '请录入审阅评语或修改要求' }]}
                  >
                    <Input.TextArea rows={3} placeholder="请录入细致指导意见..." />
                  </Form.Item>

                  <div style={{ textAlign: 'right' }}>
                    <Space>
                      <Button onClick={() => setDetailDrawerVisible(false)}>取消</Button>
                      <Button type="primary" loading={reviewLoading} onClick={handleExecuteReview}>
                        提交批阅结果
                      </Button>
                    </Space>
                  </div>
                </Form>
              </Card>
            )}
          </div>
        )}
      </Drawer>

      {/* 学生周报撰写/修改抽屉 */}
      <Drawer
        title="撰写/修改实习周报"
        width={680}
        open={editDrawerVisible}
        onClose={() => setEditDrawerVisible(false)}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="weekNumber"
            label="所属实习周次"
            rules={[{ required: true, message: '请选择实习周次' }]}
          >
            <Select
              options={Array.from({ length: 20 }, (_, i) => ({
                label: `第 ${i + 1} 周`,
                value: i + 1
              }))}
            />
          </Form.Item>

          <Form.Item
            name="workContent"
            label="本周主要技术与业务工作内容"
            rules={[{ required: true, message: '请详细阐述本周主要工作内容' }]}
          >
            <Input.TextArea rows={3} placeholder="详细记录在实习岗位从事的核心业务与代码研发工作..." />
          </Form.Item>

          <Form.Item
            name="workSummary"
            label="本周工作总结与专业收获"
            rules={[{ required: true, message: '请撰写个人专业收获与技术总结' }]}
          >
            <Input.TextArea rows={3} placeholder="总结在企业规范、团队协作和工程技术方面的成长与收获..." />
          </Form.Item>

          <Form.Item name="problemEncountered" label="遇到问题及解决途径">
            <Input.TextArea rows={2} placeholder="记录遇到的工程疑难、安全风险及通过查阅资料或向导师请教的解决经过..." />
          </Form.Item>

          <Form.Item name="nextWeekPlan" label="下周工作计划">
            <Input.TextArea rows={2} placeholder="规划下周工作重心与专业技能提升目标..." />
          </Form.Item>

          <Form.Item name="attachmentUrl" label="工作成果佐证材料/附件URL">
            <Input placeholder="如 /uploads/weekly/week2_notes.pdf" />
          </Form.Item>

          <div style={{ textAlign: 'right', marginTop: 24 }}>
            <Space>
              <Button loading={saveLoading} onClick={() => handleSaveReport('DRAFT')}>
                暂存为草稿
              </Button>
              <Button type="primary" loading={saveLoading} onClick={() => handleSaveReport('SUBMIT')}>
                正式提交周报
              </Button>
            </Space>
          </div>
        </Form>
      </Drawer>

      {/* 导师登记指导台账 Modal */}
      <Modal
        title="登记过程指导与走访记录 (API-065)"
        open={guidanceModalVisible}
        onCancel={() => setGuidanceModalVisible(false)}
        onOk={handleCreateGuidance}
        confirmLoading={guidanceLoading}
        destroyOnClose
      >
        <Form form={guidanceForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="studentId"
            label="受访/指导学生"
            rules={[{ required: true, message: '请选择指导学生' }]}
          >
            <Select
              placeholder="选择负责指导的学生"
              options={mentoredStudents.map((s) => ({
                label: `${s.studentName} (${s.studentNumber}) - ${s.className || ''}`,
                value: s.studentId
              }))}
            />
          </Form.Item>

          <Form.Item
            name="guidanceType"
            label="指导形式"
            rules={[{ required: true, message: '请选择指导形式' }]}
            initialValue="ONSITE"
          >
            <Select
              options={[
                { label: '实地走访 (ONSITE)', value: 'ONSITE' },
                { label: '线上连线 (ONLINE)', value: 'ONLINE' },
                { label: '电话抽查 (PHONE)', value: 'PHONE' },
                { label: '邮件/其他 (EMAIL_OTHER)', value: 'EMAIL_OTHER' }
              ]}
            />
          </Form.Item>

          <Form.Item
            name="guidanceDate"
            label="指导开展时间"
            rules={[{ required: true, message: '请选择指导时间' }]}
          >
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="location" label="实地走访地点 / 会议室">
            <Input placeholder="用人单位办公大楼/车间/腾讯会议等" />
          </Form.Item>

          <Form.Item
            name="contentSummary"
            label="交流指导内容要点"
            rules={[{ required: true, message: '请录入交流指导要点' }]}
          >
            <Input.TextArea rows={3} placeholder="重点了解学生出勤纪律、劳动保护、实习收获及思想动态..." />
          </Form.Item>

          <Form.Item name="followupActions" label="后续跟进要求">
            <Input placeholder="督促完善技术周报、落实岗位防护措施等" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 学生在岗确认反馈 Modal */}
      <Modal
        title="学生在岗确认反馈 (API-067)"
        open={feedbackModalVisible}
        onCancel={() => setFeedbackModalVisible(false)}
        onOk={handleSubmitFeedback}
        confirmLoading={feedbackLoading}
        destroyOnClose
      >
        <Form form={feedbackForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="studentFeedback"
            label="在岗实际情况与学习体会"
            rules={[{ required: true, message: '请录入在岗确认反馈内容' }]}
          >
            <Input.TextArea
              rows={4}
              placeholder="确认导师指导交流属实，反馈目前岗位实习进展、安全防护落实情况及自我收获..."
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
