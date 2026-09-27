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
  Switch,
  Timeline,
  Statistic,
  Alert,
  Tooltip,
  Badge
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  AlertOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  EyeOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  AuditOutlined,
  SearchOutlined,
  SettingOutlined,
  SyncOutlined,
  UserSwitchOutlined,
  FileTextOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getWarnTickets,
  getWarnTicketDetail,
  getWarnRules,
  updateWarnRule,
  toggleWarnRule,
  triggerScan,
  dispatchWarnTicket,
  submitWarnFeedback,
  handleWarnTicket,
  WarnTicketVO,
  WarnRuleVO,
  WarnProcessHistoryVO
} from '../../api/warn';
import { getTaskList, TaskItem } from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;
const { TextArea } = Input;

// 预警等级标签
const getWarnLevelTag = (level: string) => {
  switch (level) {
    case 'RED':
      return <Tag color="error">红色高危</Tag>;
    case 'ORANGE':
      return <Tag color="warning">橙色中危</Tag>;
    case 'YELLOW':
      return <Tag color="gold">黄色轻度</Tag>;
    default:
      return <Tag>{level}</Tag>;
  }
};

// 工单状态标签
const getTicketStatusTag = (status: string) => {
  switch (status) {
    case 'TRIGGERED':
      return <Tag color="error">已触发</Tag>;
    case 'DISPATCHED':
      return <Tag color="processing">已派单</Tag>;
    case 'PROCESSING':
      return <Tag color="cyan">跟进处置中</Tag>;
    case 'PENDING_REVIEW':
      return <Tag color="purple">待复核闭环</Tag>;
    case 'CLOSED':
      return <Tag color="success">已核实闭环</Tag>;
    case 'FALSE_ALARM_CLOSED':
      return <Tag color="default">已误报释放</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
};

export const WarnPage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';
  const isDeptAdmin = userType === 'DEPT_ADMIN';
  const isAdmin = userType === 'SYS_ADMIN';

  // 1. 任务批次与筛选
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [filterTaskId, setFilterTaskId] = useState<number | undefined>(undefined);
  const [filterWarnLevel, setFilterWarnLevel] = useState<string | undefined>(undefined);
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);
  const [filterUpgraded, setFilterUpgraded] = useState<number | undefined>(undefined);

  // 2. 工单列表与加载
  const [tickets, setTickets] = useState<WarnTicketVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanCooldown, setScanCooldown] = useState(0);

  // 3. 预警规则库
  const [rules, setRules] = useState<WarnRuleVO[]>([]);
  const [ruleDrawerVisible, setRuleDrawerVisible] = useState(false);
  const [editRuleModalVisible, setEditRuleModalVisible] = useState(false);
  const [selectedRule, setSelectedRule] = useState<WarnRuleVO | null>(null);
  const [ruleForm] = Form.useForm();
  const [ruleSubmitting, setRuleSubmitting] = useState(false);

  // 4. 工单详情抽屉
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<WarnTicketVO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 5. 学生申辩弹窗
  const [feedbackModalVisible, setFeedbackModalVisible] = useState(false);
  const [feedbackTicket, setFeedbackTicket] = useState<WarnTicketVO | null>(null);
  const [feedbackForm] = Form.useForm();
  const [feedbackLoading, setFeedbackLoading] = useState(false);

  // 6. 处置与销号弹窗 (教师/管理员)
  const [handleModalVisible, setHandleModalVisible] = useState(false);
  const [handleTicketItem, setHandleTicketItem] = useState<WarnTicketVO | null>(null);
  const [handleForm] = Form.useForm();
  const [handleLoading, setHandleLoading] = useState(false);

  // 7. 派发/转派弹窗
  const [dispatchModalVisible, setDispatchModalVisible] = useState(false);
  const [dispatchTicketItem, setDispatchTicketItem] = useState<WarnTicketVO | null>(null);
  const [dispatchForm] = Form.useForm();
  const [dispatchLoading, setDispatchLoading] = useState(false);

  // 初始加载任务
  useEffect(() => {
    const fetchTasks = async () => {
      try {
        const res = await getTaskList();
        if (res.code === 200 && res.data) {
          setTasks(res.data);
        }
      } catch {}
    };
    fetchTasks();
  }, []);

  // 加载工单数据
  const loadTickets = async () => {
    setLoading(true);
    try {
      const res = await getWarnTickets({
        taskId: filterTaskId,
        warnLevel: filterWarnLevel,
        status: filterStatus,
        isUpgraded: filterUpgraded
      });
      if (res.code === 200 && res.data) {
        setTickets(res.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTickets();
  }, [filterTaskId, filterWarnLevel, filterStatus, filterUpgraded]);

  // 加载预警规则
  const loadRules = async () => {
    try {
      const res = await getWarnRules();
      if (res.code === 200 && res.data) {
        setRules(res.data);
      }
    } catch {}
  };

  useEffect(() => {
    loadRules();
  }, []);

  // 倒计时计时器
  useEffect(() => {
    if (scanCooldown <= 0) return;
    const timer = setInterval(() => {
      setScanCooldown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [scanCooldown]);

  // 触发全量巡检扫描
  const handleTriggerScan = async () => {
    if (scanning || scanCooldown > 0) return;
    setScanning(true);
    try {
      const res = await triggerScan(filterTaskId);
      if (res.code === 200 && res.data) {
        message.success(
          `全盘异常扫描完成！扫描学生 ${res.data.scannedStudents} 人，新增工单 ${res.data.newTickets} 笔，超时升级 ${res.data.upgradedCount} 笔`
        );
        setScanCooldown(10);
        loadTickets();
      }
    } catch (err: any) {
      if (err?.response?.data?.message) {
        message.error(err.response.data.message);
      }
    } finally {
      setScanning(false);
    }
  };

  // 查看工单详情 (含证据链与流转历史)
  const handleOpenDetail = async (ticket: WarnTicketVO) => {
    setSelectedTicket(ticket);
    setDetailDrawerVisible(true);
    setDetailLoading(true);
    try {
      const res = await getWarnTicketDetail(ticket.id);
      if (res.code === 200 && res.data) {
        setSelectedTicket(res.data);
      }
    } catch {}
    finally {
      setDetailLoading(false);
    }
  };

  // 学生提交申辩
  const handleSubmitFeedback = async () => {
    if (!feedbackTicket) return;
    try {
      const values = await feedbackForm.validateFields();
      setFeedbackLoading(true);
      const res = await submitWarnFeedback(feedbackTicket.id, {
        studentFeedback: values.studentFeedback,
        attachmentUrl: values.attachmentUrl
      });
      if (res.code === 200) {
        message.success('预警申辩事实说明已成功提交');
        setFeedbackModalVisible(false);
        feedbackForm.resetFields();
        loadTickets();
      }
    } catch {}
    finally {
      setFeedbackLoading(false);
    }
  };

  // 教师/管理员处置工单
  const handleProcessTicket = async () => {
    if (!handleTicketItem) return;
    try {
      const values = await handleForm.validateFields();
      setHandleLoading(true);
      const res = await handleWarnTicket(handleTicketItem.id, {
        action: values.action,
        teacherInvestigation: values.teacherInvestigation,
        handlingMeasures: values.handlingMeasures,
        attachmentUrl: values.attachmentUrl
      });
      if (res.code === 200) {
        message.success('预警工单处置与闭环已完成');
        setHandleModalVisible(false);
        handleForm.resetFields();
        loadTickets();
      }
    } catch {}
    finally {
      setHandleLoading(false);
    }
  };

  // 派发/转派责任人
  const handleDispatchTicket = async () => {
    if (!dispatchTicketItem) return;
    try {
      const values = await dispatchForm.validateFields();
      setDispatchLoading(true);
      const res = await dispatchWarnTicket(dispatchTicketItem.id, {
        assigneeId: values.assigneeId,
        assigneeRole: values.assigneeRole,
        remark: values.remark
      });
      if (res.code === 200) {
        message.success('预警工单已成功转派新责任人');
        setDispatchModalVisible(false);
        dispatchForm.resetFields();
        loadTickets();
      }
    } catch {}
    finally {
      setDispatchLoading(false);
    }
  };

  // 规则启用切换
  const handleToggleRule = async (ruleId: number) => {
    try {
      const res = await toggleWarnRule(ruleId);
      if (res.code === 200) {
        message.success('预警规则状态切换成功');
        loadRules();
      }
    } catch {}
  };

  // 规则更新
  const handleUpdateRule = async () => {
    if (!selectedRule) return;
    try {
      const values = await ruleForm.validateFields();
      setRuleSubmitting(true);
      const res = await updateWarnRule(selectedRule.id, {
        ruleName: values.ruleName,
        warnLevel: values.warnLevel,
        thresholdParamsJson: values.thresholdParamsJson,
        dispatchedRole: values.dispatchedRole,
        handlingTimeoutDays: values.handlingTimeoutDays,
        description: values.description
      });
      if (res.code === 200) {
        message.success('规则配置已更新');
        setEditRuleModalVisible(false);
        loadRules();
      }
    } catch {}
    finally {
      setRuleSubmitting(false);
    }
  };

  // 动态指标统计
  const statTotal = tickets.length;
  const statActive = tickets.filter(
    (t) => t.status === 'TRIGGERED' || t.status === 'DISPATCHED' || t.status === 'PROCESSING' || t.status === 'PENDING_REVIEW'
  ).length;
  const statYellow = tickets.filter((t) => t.warnLevel === 'YELLOW').length;
  const statOrange = tickets.filter((t) => t.warnLevel === 'ORANGE').length;
  const statRed = tickets.filter((t) => t.warnLevel === 'RED').length;
  const statUpgraded = tickets.filter((t) => t.isUpgraded === 1).length;

  // 表格列配置
  const ticketColumns: ColumnsType<WarnTicketVO> = [
    {
      title: '工单号',
      dataIndex: 'ticketNo',
      key: 'ticketNo',
      width: 150,
      render: (no) => <Text code>{no}</Text>
    },
    {
      title: '预警等级',
      dataIndex: 'warnLevel',
      key: 'warnLevel',
      width: 110,
      render: (level) => getWarnLevelTag(level)
    },
    {
      title: '预警概要标题',
      dataIndex: 'warnTitle',
      key: 'warnTitle',
      minWidth: 200,
      render: (t, r) => (
        <div>
          <Text strong>{t}</Text>
          {r.isUpgraded === 1 && (
            <Tag color="purple" style={{ marginLeft: 6 }}>
              院系升级督办
            </Tag>
          )}
        </div>
      )
    },
    {
      title: '受预警学生',
      key: 'student',
      width: 140,
      render: (_, r) => (
        <span>
          <strong>{r.studentName}</strong>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {r.studentNo || r.className}
          </Text>
        </span>
      )
    },
    {
      title: '负责导师',
      dataIndex: 'teacherName',
      key: 'teacherName',
      width: 110,
      render: (t) => t || '-'
    },
    {
      title: '当前责任人',
      key: 'assignee',
      width: 140,
      render: (_, r) => (
        <span>
          {r.currentAssigneeName || '待指派'}
          <br />
          <Tag color="blue" style={{ fontSize: 11 }}>
            {r.currentAssigneeRole === 'TEACHER' ? '指导导师' : r.currentAssigneeRole === 'DEPT_ADMIN' ? '院系主管' : r.currentAssigneeRole}
          </Tag>
        </span>
      )
    },
    {
      title: '工单状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (st) => getTicketStatusTag(st)
    },
    {
      title: '触发时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, record) => {
        const isClosed = record.status === 'CLOSED' || record.status === 'FALSE_ALARM_CLOSED';
        return (
          <Space size="small" wrap>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleOpenDetail(record)}
            >
              工单详情
            </Button>

            {/* 学生在线申辩 */}
            {isStudent && !isClosed && (
              <Button
                type="primary"
                size="small"
                onClick={() => {
                  setFeedbackTicket(record);
                  feedbackForm.setFieldsValue({
                    studentFeedback: record.studentFeedback || '',
                    attachmentUrl: ''
                  });
                  setFeedbackModalVisible(true);
                }}
              >
                {record.studentFeedback ? '补充申辩' : '填写申辩'}
              </Button>
            )}

            {/* 教师/管理员处置与闭环 */}
            {(isTeacher || isDeptAdmin || isAdmin) && !isClosed && (
              <Button
                type="primary"
                size="small"
                icon={<AuditOutlined />}
                onClick={() => {
                  setHandleTicketItem(record);
                  handleForm.setFieldsValue({
                    action: 'CLOSED',
                    teacherInvestigation: record.teacherInvestigation || '',
                    handlingMeasures: record.handlingMeasures || ''
                  });
                  setHandleModalVisible(true);
                }}
              >
                处置销号
              </Button>
            )}

            {/* 管理员/导师转派 */}
            {(isTeacher || isDeptAdmin || isAdmin) && !isClosed && (
              <Button
                type="default"
                size="small"
                icon={<UserSwitchOutlined />}
                onClick={() => {
                  setDispatchTicketItem(record);
                  dispatchForm.resetFields();
                  setDispatchModalVisible(true);
                }}
              >
                转派
              </Button>
            )}
          </Space>
        );
      }
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 顶部标题与操作栏 */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              风险预警监控与工单协同中心
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              涵盖红橙黄三级风险感知、全盘异常扫描、责任人派发转派、学生在线申辩与销号闭环
            </Paragraph>
          </div>

          <Space wrap>
            {/* 非学生角色允许触发全盘扫描 */}
            {!isStudent && (
              <Button
                type="primary"
                danger
                icon={<AlertOutlined />}
                loading={scanning}
                disabled={scanCooldown > 0}
                onClick={handleTriggerScan}
              >
                {scanCooldown > 0 ? `${scanCooldown}s 后可重新巡检` : '全盘异常巡检扫描'}
              </Button>
            )}

            <Button
              icon={<SettingOutlined />}
              onClick={() => {
                loadRules();
                setRuleDrawerVisible(true);
              }}
            >
              查看预警规则库
            </Button>

            <Button icon={<ReloadOutlined />} onClick={loadTickets} loading={loading}>
              刷新
            </Button>
          </Space>
        </div>
      </Card>

      {/* 核心多维指标概览卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#fafafa' }}>
            <Statistic title="累计预警工单" value={statTotal} valueStyle={{ fontWeight: 'bold' }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#e6f4ff' }}>
            <Statistic
              title="待处置·流转中"
              value={statActive}
              valueStyle={{ color: '#1677ff', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#fffbe6' }}>
            <Statistic
              title="黄色轻度风险"
              value={statYellow}
              valueStyle={{ color: '#faad14', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#fff7e6' }}>
            <Statistic
              title="橙色中度风险"
              value={statOrange}
              valueStyle={{ color: '#fa8c16', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#fff1f0' }}>
            <Statistic
              title="红色高危风险"
              value={statRed}
              valueStyle={{ color: '#f5222d', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small" style={{ textAlign: 'center', backgroundColor: '#f9f0ff' }}>
            <Statistic
              title="超时升级院系"
              value={statUpgraded}
              valueStyle={{ color: '#722ed1', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 筛选与检索栏 */}
      <Card size="small">
        <Space wrap size="middle">
          <Space>
            <Text>实习任务：</Text>
            <Select
              style={{ width: 220 }}
              value={filterTaskId}
              placeholder="全部实习任务"
              allowClear
              onChange={(val) => setFilterTaskId(val)}
              options={tasks.map((t) => ({
                label: t.taskName,
                value: t.id
              }))}
            />
          </Space>

          <Space>
            <Text>预警等级：</Text>
            <Select
              style={{ width: 140 }}
              value={filterWarnLevel}
              placeholder="全部等级"
              allowClear
              onChange={(val) => setFilterWarnLevel(val)}
              options={[
                { label: '红色高危', value: 'RED' },
                { label: '橙色中危', value: 'ORANGE' },
                { label: '黄色轻度', value: 'YELLOW' }
              ]}
            />
          </Space>

          <Space>
            <Text>工单状态：</Text>
            <Select
              style={{ width: 160 }}
              value={filterStatus}
              placeholder="全部状态"
              allowClear
              onChange={(val) => setFilterStatus(val)}
              options={[
                { label: '已触发 (TRIGGERED)', value: 'TRIGGERED' },
                { label: '已派单 (DISPATCHED)', value: 'DISPATCHED' },
                { label: '跟进处置中 (PROCESSING)', value: 'PROCESSING' },
                { label: '已核实闭环 (CLOSED)', value: 'CLOSED' },
                { label: '已误报释放 (FALSE_ALARM)', value: 'FALSE_ALARM_CLOSED' }
              ]}
            />
          </Space>

          <Space>
            <Text>升级督办：</Text>
            <Select
              style={{ width: 120 }}
              value={filterUpgraded}
              placeholder="全部"
              allowClear
              onChange={(val) => setFilterUpgraded(val)}
              options={[
                { label: '已升级', value: 1 },
                { label: '未升级', value: 0 }
              ]}
            />
          </Space>

          <Button
            onClick={() => {
              setFilterWarnLevel(undefined);
              setFilterStatus(undefined);
              setFilterUpgraded(undefined);
            }}
          >
            重置筛选
          </Button>
        </Space>
      </Card>

      {/* 工单数据列表表格 */}
      <Card>
        <Table
          columns={ticketColumns}
          dataSource={tickets}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `共 ${total} 笔预警协同工单`
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* 1. 工单全生命周期详情抽屉 */}
      <Drawer
        title={
          <span>
            <AlertOutlined style={{ color: '#f5222d', marginRight: 8 }} />
            预警工单全生命周期证据链详情 ({selectedTicket?.ticketNo})
          </span>
        }
        width={720}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
      >
        {selectedTicket && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {selectedTicket.isUpgraded === 1 && (
              <Alert
                message="该工单因处理逾期已升级至院系督办"
                description={`升级原因：${selectedTicket.upgradeReason || '处置超时'} (升级时间: ${selectedTicket.upgradedTime || '-'})`}
                type="error"
                showIcon
              />
            )}

            <Descriptions title="工单基本概况" bordered size="small" column={2}>
              <Descriptions.Item label="工单编号">{selectedTicket.ticketNo}</Descriptions.Item>
              <Descriptions.Item label="预警等级">{getWarnLevelTag(selectedTicket.warnLevel)}</Descriptions.Item>
              <Descriptions.Item label="关联规则">{selectedTicket.ruleName || selectedTicket.ruleCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="规则版本">v{selectedTicket.ruleVersion || 1}</Descriptions.Item>
              <Descriptions.Item label="受预警学生">{selectedTicket.studentName} ({selectedTicket.studentNo || selectedTicket.className})</Descriptions.Item>
              <Descriptions.Item label="指导教师">{selectedTicket.teacherName || '-'}</Descriptions.Item>
              <Descriptions.Item label="当前责任人">{selectedTicket.currentAssigneeName} ({selectedTicket.currentAssigneeRole})</Descriptions.Item>
              <Descriptions.Item label="工单状态">{getTicketStatusTag(selectedTicket.status)}</Descriptions.Item>
              <Descriptions.Item label="预警概要" span={2}>
                <strong>{selectedTicket.warnTitle}</strong>
              </Descriptions.Item>
            </Descriptions>

            <Descriptions title="触发客观证据快照 (API-087)" bordered size="small" column={1}>
              <Descriptions.Item label="快照参数 JSON">
                <pre
                  style={{
                    backgroundColor: '#f5f5f5',
                    padding: 8,
                    borderRadius: 4,
                    fontSize: 12,
                    maxHeight: 180,
                    overflow: 'auto',
                    margin: 0
                  }}
                >
                  {selectedTicket.evidenceSnapshotJson || '无结构化快照数据'}
                </pre>
              </Descriptions.Item>
            </Descriptions>

            <Descriptions title="师生协同调查与成效" bordered size="small" column={1}>
              <Descriptions.Item label="学生事实申辩 (API-089)">
                {selectedTicket.studentFeedback ? (
                  <div>
                    <Paragraph style={{ margin: 0 }}>{selectedTicket.studentFeedback}</Paragraph>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      申辩时间：{selectedTicket.studentFeedbackTime || '-'}
                    </Text>
                  </div>
                ) : (
                  <Text type="secondary">尚未提交在线申辩说明</Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="导师调查核实">
                {selectedTicket.teacherInvestigation || <Text type="secondary">待核实调查</Text>}
              </Descriptions.Item>
              <Descriptions.Item label="处置整改举措">
                {selectedTicket.handlingMeasures || <Text type="secondary">待登记具体防范措施</Text>}
              </Descriptions.Item>
              <Descriptions.Item label="闭环销号时间">
                {selectedTicket.closedTime ? `${selectedTicket.closedTime} (销号人: ${selectedTicket.closedByName || '管理员'})` : '尚未销号'}
              </Descriptions.Item>
            </Descriptions>

            {/* 流转审计痕迹 Timeline */}
            <div>
              <Title level={5} style={{ marginBottom: 16 }}>
                全过程流转审计证据链 (API-087)
              </Title>
              {selectedTicket.processHistory && selectedTicket.processHistory.length > 0 ? (
                <Timeline
                  items={selectedTicket.processHistory.map((h: WarnProcessHistoryVO) => ({
                    color: h.action === 'TIMEOUT_UPGRADE' ? 'red' : h.action === 'CLOSED' ? 'green' : 'blue',
                    children: (
                      <div>
                        <div>
                          <strong>{h.action}</strong> · {h.operatorName} ({h.operatorRole})
                        </div>
                        <div style={{ fontSize: 12, color: '#888' }}>{h.operateTime}</div>
                        <div style={{ marginTop: 4 }}>{h.contentRemark}</div>
                      </div>
                    )
                  }))}
                />
              ) : (
                <Text type="secondary">暂无额外流转动作日志</Text>
              )}
            </div>
          </div>
        )}
      </Drawer>

      {/* 2. 学生提交在线申辩弹窗 */}
      <Modal
        title="提交预警事实申辩说明 (API-089)"
        open={feedbackModalVisible}
        onCancel={() => setFeedbackModalVisible(false)}
        onOk={handleSubmitFeedback}
        confirmLoading={feedbackLoading}
        okText="提交申辩"
        destroyOnClose
      >
        <Form form={feedbackForm} layout="vertical" style={{ marginTop: 16 }}>
          <Alert
            message="申辩须知"
            description="请客观、如实说明导致此项预警的具体事实依据（如因病请假、校外参赛、系统打卡信号偏移等），该记录将永久归档至预警流转证据链中。"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
          <Form.Item
            name="studentFeedback"
            label="申辩事实与理由说明"
            rules={[{ required: true, message: '请详细阐述事实理由' }]}
          >
            <TextArea
              rows={4}
              placeholder="请输入具体情况说明，字数建议不少于20字..."
              maxLength={500}
              showCount
            />
          </Form.Item>
          <Form.Item name="attachmentUrl" label="佐证凭证材料链接 (可选)">
            <Input placeholder="可填入已上传的证明照片或文档链接，如 /uploads/proof.pdf" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 3. 教师/管理员处置销号弹窗 */}
      <Modal
        title="预警工单处置与闭环销号 (API-090)"
        open={handleModalVisible}
        onCancel={() => setHandleModalVisible(false)}
        onOk={handleProcessTicket}
        confirmLoading={handleLoading}
        okText="执行处置"
        destroyOnClose
      >
        <Form form={handleForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="action"
            label="处置决议与流转动作"
            rules={[{ required: true, message: '请选择处置决议' }]}
          >
            <Radio.Group>
              <Radio value="CLOSED">
                <Tag color="success">核实闭环销号</Tag>
              </Radio>
              <Radio value="PROCESSING">
                <Tag color="processing">跟进调查中</Tag>
              </Radio>
              <Radio value="FALSE_ALARM_CLOSED">
                <Tag color="default">确属误报释放</Tag>
              </Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="teacherInvestigation"
            label="负责教师/主管核实调查情况"
            rules={[{ required: true, message: '请登记核实调查情况' }]}
          >
            <TextArea rows={3} placeholder="例：已与学生和企业带教师傅电话核实，学生实际正常在岗..." />
          </Form.Item>
          <Form.Item
            name="handlingMeasures"
            label="后续防范与整改举措"
            rules={[{ required: true, message: '请填报处置措施' }]}
          >
            <TextArea rows={3} placeholder="例：已督促学生补卡并补交周报，后续每周二定期追踪..." />
          </Form.Item>
          <Form.Item name="attachmentUrl" label="处置附件链接 (可选)">
            <Input placeholder="/uploads/warn_handle.pdf" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 4. 派发/转派责任人弹窗 */}
      <Modal
        title="预警工单指派 / 转派 (API-088)"
        open={dispatchModalVisible}
        onCancel={() => setDispatchModalVisible(false)}
        onOk={handleDispatchTicket}
        confirmLoading={dispatchLoading}
        okText="确认转派"
        destroyOnClose
      >
        <Form form={dispatchForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="assigneeId"
            label="新责任人用户 ID"
            rules={[{ required: true, message: '请输入责任人ID' }]}
          >
            <InputNumber style={{ width: '100%' }} placeholder="例：3 (教师) 或 2 (院系管理员)" />
          </Form.Item>
          <Form.Item
            name="assigneeRole"
            label="新责任人角色"
            rules={[{ required: true, message: '请选择责任人角色' }]}
          >
            <Radio.Group>
              <Radio value="TEACHER">指导教师</Radio>
              <Radio value="DEPT_ADMIN">院系管理员</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="remark" label="转派说明原因">
            <TextArea rows={3} placeholder="说明转派背景（如更换负责导师、院系集中督查等）..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 5. 预警规则库查看与编辑抽屉 */}
      <Drawer
        title={
          <span>
            <SettingOutlined style={{ marginRight: 8 }} />
            全景异常预警规则配置库 (API-082 ~ API-084)
          </span>
        }
        width={800}
        open={ruleDrawerVisible}
        onClose={() => setRuleDrawerVisible(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Alert
            message="预警规则配置说明"
            description="全盘扫描巡检将按已启用的规则执行动态比对。超级管理员可修改阈值参数及处置超时天数，其他角色仅供查阅。"
            type="info"
            showIcon
          />

          <Table
            dataSource={rules}
            rowKey="id"
            pagination={false}
            columns={[
              {
                title: '规则编码',
                dataIndex: 'ruleCode',
                key: 'ruleCode',
                width: 140,
                render: (c) => <Text code>{c}</Text>
              },
              {
                title: '规则名称',
                dataIndex: 'ruleName',
                key: 'ruleName',
                width: 180,
                render: (n) => <strong>{n}</strong>
              },
              {
                title: '风险等级',
                dataIndex: 'warnLevel',
                key: 'warnLevel',
                width: 110,
                render: (lvl) => getWarnLevelTag(lvl)
              },
              {
                title: '派发角色',
                dataIndex: 'dispatchedRole',
                key: 'dispatchedRole',
                width: 100,
                render: (r) => <Tag color="blue">{r}</Tag>
              },
              {
                title: '超时(天)',
                dataIndex: 'handlingTimeoutDays',
                key: 'handlingTimeoutDays',
                width: 90
              },
              {
                title: '状态',
                dataIndex: 'isEnabled',
                key: 'isEnabled',
                width: 90,
                render: (en, r) => (
                  <Switch
                    checked={en === 1}
                    disabled={!isAdmin}
                    onChange={() => handleToggleRule(r.id)}
                  />
                )
              },
              {
                title: '操作',
                key: 'action',
                width: 100,
                render: (_, record) => (
                  <Button
                    type="link"
                    size="small"
                    disabled={!isAdmin}
                    onClick={() => {
                      setSelectedRule(record);
                      ruleForm.setFieldsValue({
                        ruleName: record.ruleName,
                        warnLevel: record.warnLevel,
                        thresholdParamsJson: record.thresholdParamsJson,
                        dispatchedRole: record.dispatchedRole,
                        handlingTimeoutDays: record.handlingTimeoutDays,
                        description: record.description
                      });
                      setEditRuleModalVisible(true);
                    }}
                  >
                    编辑参数
                  </Button>
                )
              }
            ]}
          />
        </div>
      </Drawer>

      {/* 6. 超级管理员编辑预警规则弹窗 */}
      <Modal
        title={`更新预警规则配置: ${selectedRule?.ruleCode}`}
        open={editRuleModalVisible}
        onCancel={() => setEditRuleModalVisible(false)}
        onOk={handleUpdateRule}
        confirmLoading={ruleSubmitting}
        okText="保存规则"
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="warnLevel"
            label="预警级别"
            rules={[{ required: true, message: '请选择预警级别' }]}
          >
            <Radio.Group>
              <Radio value="YELLOW">黄色轻度</Radio>
              <Radio value="ORANGE">橙色中危</Radio>
              <Radio value="RED">红色高危</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="handlingTimeoutDays"
            label="超时升级处置天数 (天)"
            rules={[{ required: true, message: '请输入超时天数' }]}
          >
            <InputNumber min={1} max={30} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="thresholdParamsJson"
            label="阈值参数 (JSON 格式)"
            rules={[{ required: true, message: '请输入有效 JSON' }]}
          >
            <TextArea rows={4} />
          </Form.Item>
          <Form.Item name="description" label="规则逻辑说明">
            <TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
