import React, { useState, useEffect, useMemo } from 'react';
import {
  Card,
  Table,
  Button,
  Form,
  Select,
  Input,
  InputNumber,
  Tag,
  Space,
  Modal,
  Descriptions,
  Timeline,
  Row,
  Col,
  Alert,
  Divider,
  Empty,
  Switch,
  Radio,
  Typography,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  SendOutlined,
  EditOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  AuditOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';
import { getTaskList, type TaskItem } from '../../api/task';
import {
  getScoreList,
  getMyScore,
  getScoreDetail,
  submitScore,
  auditScore,
  publishScores,
  submitAppeal,
  arbitrateAppeal,
  type ScoreSummaryVO,
  type ScoreAuditHistoryVO
} from '../../api/score';

const { Title, Text, Paragraph } = Typography;

export const ScorePage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const currentUserId = user?.userId || 0;

  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';
  const isDeptAdmin = userType === 'DEPT_ADMIN';
  const isAdmin = userType === 'SYS_ADMIN';

  // 基础数据与任务列表
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [scoreList, setScoreList] = useState<ScoreSummaryVO[]>([]);

  // 学生个人成绩
  const [studentScore, setStudentScore] = useState<ScoreSummaryVO | null>(null);
  const [studentScoreLoading, setStudentScoreLoading] = useState(false);
  const [selectedStudentTaskId, setSelectedStudentTaskId] = useState<number | undefined>(undefined);

  // 筛选表单
  const [filterForm] = Form.useForm();

  // 弹窗状态
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [currentScore, setCurrentScore] = useState<ScoreSummaryVO | null>(null);

  // 教师成绩评定录入弹窗
  const [entryModalOpen, setEntryModalOpen] = useState(false);
  const [entrySubmitting, setEntrySubmitting] = useState(false);
  const [entryForm] = Form.useForm();
  const [activeEntryRow, setActiveEntryRow] = useState<ScoreSummaryVO | null>(null);

  // 学生申诉弹窗
  const [appealModalOpen, setAppealModalOpen] = useState(false);
  const [appealSubmitting, setAppealSubmitting] = useState(false);
  const [appealForm] = Form.useForm();

  // 院系仲裁弹窗
  const [arbitrateModalOpen, setArbitrateModalOpen] = useState(false);
  const [arbitrateSubmitting, setArbitrateSubmitting] = useState(false);
  const [arbitrateLoading, setArbitrateLoading] = useState(false);
  const [arbitrateForm] = Form.useForm();
  const [arbitrateActionType, setArbitrateActionType] = useState<'PASS' | 'REJECT'>('PASS');
  const [activeArbitrateRow, setActiveArbitrateRow] = useState<ScoreSummaryVO | null>(null);
  const [activeAppeal, setActiveAppeal] = useState<ScoreAuditHistoryVO | null>(null);

  // 初始化加载
  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    try {
      const res = await getTaskList();
      if (res.code === 200 && res.data) {
        const records = Array.isArray(res.data) ? res.data : (res.data as any).records || [];
        setTasks(records);
        if (records.length > 0) {
          if (isStudent) {
            setSelectedStudentTaskId(records[0].id);
            loadMyScoreData(records[0].id);
          } else {
            loadScoresData();
          }
        }
      }
    } catch (e: any) {
      console.error('加载任务列表失败', e);
    }
  };

  const loadMyScoreData = async (taskId?: number) => {
    const tid = taskId || selectedStudentTaskId;
    if (!tid) return;
    setStudentScoreLoading(true);
    try {
      const res = await getMyScore(tid);
      if (res.code === 200) {
        setStudentScore(res.data);
      }
    } catch (e: any) {
      message.error(e?.message || '加载个人实习成绩失败');
    } finally {
      setStudentScoreLoading(false);
    }
  };

  const loadScoresData = async () => {
    setLoading(true);
    try {
      const values = filterForm.getFieldsValue();
      const res = await getScoreList({
        taskId: values.taskId,
        scoreLevel: values.scoreLevel,
        status: values.status
      });
      if (res.code === 200) {
        setScoreList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '加载成绩列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleResetFilters = () => {
    filterForm.resetFields();
    loadScoresData();
  };

  // 批量发布成绩公示
  const handleBatchPublish = () => {
    const taskId = filterForm.getFieldValue('taskId');
    if (!taskId) {
      message.warning('请先在上方筛选条件中选择要批量发布公示的实习任务');
      return;
    }
    const currentTaskName = tasks.find(t => t.id === taskId)?.taskName || `任务ID: ${taskId}`;
    Modal.confirm({
      title: '发布实习成绩全院公示确认',
      icon: <ExclamationCircleOutlined style={{ color: '#faad14' }} />,
      content: `确定为任务【${currentTaskName}】批量发布成绩公示？发布后，所有通过审核的综合评定将全量对学生开放，并启动法定为期7天的成绩复核申诉期。`,
      okText: '立即发布公示',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await publishScores(taskId);
          if (res.code === 200) {
            message.success('该实习任务成绩已成功批量发布公示！');
            loadScoresData();
          }
        } catch (e: any) {
          message.error(e?.message || '发布公示失败');
        }
      }
    });
  };

  // 权限与按钮状态判断
  const canTeacherEdit = (row: ScoreSummaryVO) => {
    if (row.status === 'PUBLISHED') return false;
    if (isAdmin) return true;
    if (isTeacher && row.teacherId === currentUserId && (row.status === 'DRAFT' || row.status === 'PENDING_AUDIT')) {
      return true;
    }
    return false;
  };

  const canDeptAudit = (row: ScoreSummaryVO) => {
    if (row.status === 'PENDING_AUDIT' && (isDeptAdmin || isAdmin)) return true;
    return false;
  };

  const canArbitrate = (row: ScoreSummaryVO) => {
    if (row.status === 'PUBLICITY' && (isDeptAdmin || isAdmin)) return true;
    return false;
  };

  const canStudentAppeal = (score: ScoreSummaryVO | null) => {
    if (!score) return false;
    if (score.status !== 'PUBLICITY') return false;
    if (score.auditHistory && score.auditHistory.some(h => h.action === 'APPEAL_APPLY')) {
      return false;
    }
    return true;
  };

  // 教师录入弹窗
  const handleOpenEntryModal = (row: ScoreSummaryVO) => {
    setActiveEntryRow(row);
    entryForm.setFieldsValue({
      enterpriseScore: row.enterpriseScore ?? 85,
      processScore: row.processScore ?? 85,
      weeklyScore: row.weeklyScore ?? 85,
      materialScore: row.materialScore ?? 85,
      summaryScore: row.summaryScore ?? 85,
      evaluationComment: row.evaluationComment || '',
      enterpriseEvaluationUrl: row.enterpriseEvaluationUrl || '',
      submitToDept: true
    });
    setEntryModalOpen(true);
  };

  const handleScoreEntrySubmit = async () => {
    try {
      const values = await entryForm.validateFields();
      if (!activeEntryRow) return;
      setEntrySubmitting(true);
      const res = await submitScore({
        taskId: activeEntryRow.taskId,
        studentId: activeEntryRow.studentId,
        enterpriseScore: values.enterpriseScore,
        processScore: values.processScore,
        weeklyScore: values.weeklyScore,
        materialScore: values.materialScore,
        summaryScore: values.summaryScore,
        evaluationComment: values.evaluationComment,
        enterpriseEvaluationUrl: values.enterpriseEvaluationUrl,
        submitToDept: values.submitToDept
      });
      if (res.code === 200) {
        message.success('五维成绩综合评定保存成功！');
        setEntryModalOpen(false);
        loadScoresData();
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '评定保存失败');
    } finally {
      setEntrySubmitting(false);
    }
  };

  // 审核通过
  const handleAuditApprove = (row: ScoreSummaryVO) => {
    Modal.confirm({
      title: '审核通过确认',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: `确认审核通过学生【${row.studentName}】的五维综合评定成绩（总分: ${row.finalScore?.toFixed(2)}）？`,
      okText: '确认通过',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await auditScore(row.id);
          if (res.code === 200) {
            message.success('该生实习成绩审核通过！');
            loadScoresData();
          }
        } catch (e: any) {
          message.error(e?.message || '审核失败');
        }
      }
    });
  };

  // 仲裁弹窗
  const handleOpenArbitrateModal = async (row: ScoreSummaryVO) => {
    setActiveArbitrateRow(row);
    setArbitrateActionType('PASS');
    setActiveAppeal(null);
    arbitrateForm.setFieldsValue({
      action: 'PASS',
      approvalDocNo: '',
      auditComment: '',
      enterpriseScore: row.enterpriseScore ?? 85,
      processScore: row.processScore ?? 85,
      weeklyScore: row.weeklyScore ?? 85,
      materialScore: row.materialScore ?? 85,
      summaryScore: row.summaryScore ?? 85
    });

    try {
      setArbitrateLoading(true);
      const detailRes = await getScoreDetail(row.id);
      if (detailRes.code === 200 && detailRes.data) {
        const histories = detailRes.data.auditHistory || [];
        const applyRecords = histories.filter(h => h.action === 'APPEAL_APPLY');
        if (applyRecords.length === 0) {
          message.warning(`学生【${row.studentName}】尚未提交成绩复核申诉申请`);
          return;
        }
        const latestApply = applyRecords[applyRecords.length - 1];
        const applyIndex = histories.indexOf(latestApply);
        const resolvedAfter = histories.slice(applyIndex + 1).find(
          h => h.action === 'APPEAL_PASS' || h.action === 'APPEAL_REJECT'
        );
        if (resolvedAfter) {
          message.info(
            `学生【${row.studentName}】的申诉已完成裁决（${resolvedAfter.action === 'APPEAL_PASS' ? '准予调分' : '申诉驳回'}）`
          );
          return;
        }
        setActiveAppeal(latestApply);
        setArbitrateModalOpen(true);
      } else {
        message.error('获取成绩详情失败');
      }
    } catch (e: any) {
      message.error(e?.message || '读取申诉记录失败');
    } finally {
      setArbitrateLoading(false);
    }
  };

  const handleArbitrateSubmit = async () => {
    try {
      const values = await arbitrateForm.validateFields();
      if (!activeArbitrateRow) return;
      if (!activeAppeal) {
        message.error('未找到有效的申诉申请记录');
        return;
      }
      setArbitrateSubmitting(true);
      const res = await arbitrateAppeal(activeAppeal.id, {
        action: values.action,
        approvalDocNo: values.action === 'PASS' ? values.approvalDocNo : undefined,
        auditComment: values.auditComment,
        enterpriseScore: values.action === 'PASS' ? values.enterpriseScore : undefined,
        processScore: values.action === 'PASS' ? values.processScore : undefined,
        weeklyScore: values.action === 'PASS' ? values.weeklyScore : undefined,
        materialScore: values.action === 'PASS' ? values.materialScore : undefined,
        summaryScore: values.action === 'PASS' ? values.summaryScore : undefined
      });
      if (res.code === 200) {
        message.success(values.action === 'PASS' ? '申诉复核仲裁决定已执行并完成区块链审计存证！' : '成绩申诉已驳回，维持原评定成绩！');
        setArbitrateModalOpen(false);
        setActiveAppeal(null);
        loadScoresData();
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '仲裁提交失败');
    } finally {
      setArbitrateSubmitting(false);
    }
  };

  // 学生申诉
  const handleOpenAppealModal = () => {
    if (!studentScore) return;
    appealForm.setFieldsValue({
      appealReason: '',
      appealAttachmentUrl: ''
    });
    setAppealModalOpen(true);
  };

  const handleAppealSubmit = async () => {
    try {
      const values = await appealForm.validateFields();
      if (!studentScore) return;
      setAppealSubmitting(true);
      const res = await submitAppeal({
        scoreId: studentScore.id,
        appealReason: values.appealReason,
        appealAttachmentUrl: values.appealAttachmentUrl
      });
      if (res.code === 200) {
        message.success('成绩复核申诉已成功提交至院系教学督导组！');
        setAppealModalOpen(false);
        loadMyScoreData();
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '申诉提交失败');
    } finally {
      setAppealSubmitting(false);
    }
  };

  // 格式化与权重计算
  const getRuleWeight = (dim: string, snapshotJson?: string) => {
    if (!snapshotJson) return 20;
    try {
      const parsed = JSON.parse(snapshotJson);
      if (dim === 'enterprise') return parsed.enterpriseWeight ?? 20;
      if (dim === 'process') return parsed.processWeight ?? 20;
      if (dim === 'weekly') return parsed.weeklyWeight ?? 20;
      if (dim === 'material') return parsed.materialWeight ?? 20;
      if (dim === 'summary') return parsed.summaryWeight ?? 20;
    } catch (e) {}
    return 20;
  };

  const formatStatusTag = (status?: string) => {
    switch (status) {
      case 'DRAFT': return <Tag color="default">暂存草稿</Tag>;
      case 'PENDING_AUDIT': return <Tag color="warning">待院系终审</Tag>;
      case 'PENDING_PUBLICITY': return <Tag color="processing">待公示</Tag>;
      case 'PUBLICITY': return <Tag color="error">公示申诉期</Tag>;
      case 'PUBLISHED': return <Tag color="success">已归档发布</Tag>;
      default: return <Tag color="default">{status || '未录入'}</Tag>;
    }
  };

  const formatLevelTag = (level?: string) => {
    switch (level) {
      case 'EXCELLENT': return <Tag color="green">优秀</Tag>;
      case 'GOOD': return <Tag color="blue">良好</Tag>;
      case 'MEDIUM': return <Tag color="orange">中等</Tag>;
      case 'PASS': return <Tag color="cyan">及格</Tag>;
      case 'FAIL': return <Tag color="red">不及格</Tag>;
      default: return <span>-</span>;
    }
  };

  const formatAuditAction = (action: string) => {
    switch (action) {
      case 'APPEAL_APPLY': return '学生提出成绩复核申诉';
      case 'APPEAL_PASS': return '仲裁通过（准予更正调分）';
      case 'APPEAL_REJECT': return '仲裁驳回（维持原评定）';
      case 'SPECIAL_MODIFY': return '特批调分更正';
      default: return action;
    }
  };

  // 表格定义
  const columns: ColumnsType<ScoreSummaryVO> = [
    {
      title: '学生信息',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 140,
      render: (_, r) => (
        <div>
          <div><Text strong>{r.studentName}</Text></div>
          <div><Text type="secondary" style={{ fontSize: 12 }}>{r.studentNo}</Text></div>
        </div>
      )
    },
    {
      title: '所属院系',
      dataIndex: 'deptName',
      key: 'deptName',
      width: 130,
      ellipsis: true
    },
    {
      title: '指导教师',
      dataIndex: 'teacherName',
      key: 'teacherName',
      width: 110,
      render: v => v || '-'
    },
    {
      title: '企业评价',
      dataIndex: 'enterpriseScore',
      key: 'enterpriseScore',
      width: 90,
      align: 'center',
      render: v => v?.toFixed(1) ?? '-'
    },
    {
      title: '过程指导',
      dataIndex: 'processScore',
      key: 'processScore',
      width: 90,
      align: 'center',
      render: v => v?.toFixed(1) ?? '-'
    },
    {
      title: '周报均分',
      dataIndex: 'weeklyScore',
      key: 'weeklyScore',
      width: 90,
      align: 'center',
      render: v => v?.toFixed(1) ?? '-'
    },
    {
      title: '阶段材料',
      dataIndex: 'materialScore',
      key: 'materialScore',
      width: 90,
      align: 'center',
      render: v => v?.toFixed(1) ?? '-'
    },
    {
      title: '总结报告',
      dataIndex: 'summaryScore',
      key: 'summaryScore',
      width: 90,
      align: 'center',
      render: v => v?.toFixed(1) ?? '-'
    },
    {
      title: '综合总分',
      dataIndex: 'finalScore',
      key: 'finalScore',
      width: 100,
      align: 'center',
      render: v => v != null ? <Text strong style={{ color: '#1677ff', fontSize: 15 }}>{v.toFixed(2)}</Text> : '-'
    },
    {
      title: '等级',
      dataIndex: 'scoreLevel',
      key: 'scoreLevel',
      width: 85,
      align: 'center',
      render: v => formatLevelTag(v)
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 115,
      align: 'center',
      render: v => formatStatusTag(v)
    },
    {
      title: '操作',
      key: 'action',
      width: 210,
      fixed: 'right',
      align: 'center',
      render: (_, row) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={async () => {
              setCurrentScore(row);
              setDetailModalOpen(true);
              try {
                const res = await getScoreDetail(row.id);
                if (res.code === 200 && res.data) {
                  setCurrentScore(res.data);
                }
              } catch (e) {}
            }}
          >
            详情
          </Button>
          {canTeacherEdit(row) && (
            <Button
              type="link"
              size="small"
              style={{ color: '#fa8c16' }}
              onClick={() => handleOpenEntryModal(row)}
            >
              评定录入
            </Button>
          )}
          {canDeptAudit(row) && (
            <Button
              type="link"
              size="small"
              style={{ color: '#52c41a' }}
              onClick={() => handleAuditApprove(row)}
            >
              审核通过
            </Button>
          )}
          {canArbitrate(row) && (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => handleOpenArbitrateModal(row)}
            >
              申诉仲裁
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* 头部标题区 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <Title level={4} style={{ margin: '0 0 4px 0' }}>五维综合成绩评定与申诉管理</Title>
          <Text type="secondary">
            企业评价、过程指导、周报质量、阶段材料与总结报告五维加权汇算与仲裁闭环
          </Text>
        </div>
        <Space>
          {(isDeptAdmin || isAdmin) && (
            <Button type="primary" icon={<SendOutlined />} onClick={handleBatchPublish}>
              发布成绩公示
            </Button>
          )}
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              if (isStudent) loadMyScoreData();
              else loadScoresData();
            }}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 学生端视图 */}
      {isStudent ? (
        <Card loading={studentScoreLoading} style={{ borderRadius: 8 }}>
          {tasks.length > 1 && (
            <div style={{ marginBottom: 16 }}>
              <Space>
                <Text strong>选择所属实习任务：</Text>
                <Select
                  value={selectedStudentTaskId}
                  style={{ width: 320 }}
                  options={tasks.map(t => ({ label: t.taskName, value: t.id }))}
                  onChange={val => {
                    setSelectedStudentTaskId(val);
                    loadMyScoreData(val);
                  }}
                />
              </Space>
            </div>
          )}

          {studentScore && studentScore.status !== 'DRAFT' ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <Title level={5} style={{ margin: 0 }}>我的实习综合评定成绩单</Title>
                {formatStatusTag(studentScore.status)}
              </div>

              {/* 核心总分与等级展示 */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  gap: 80,
                  padding: '24px 0',
                  background: '#f9fafb',
                  borderRadius: 8,
                  marginBottom: 24
                }}
              >
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 52, fontWeight: 800, color: '#1677ff', lineHeight: 1 }}>
                    {studentScore.finalScore?.toFixed(2) ?? '-'}
                  </div>
                  <div style={{ fontSize: 14, color: '#666', marginTop: 8 }}>综合评定总分</div>
                </div>
                <div style={{ textAlign: 'center' }}>
                  <div style={{ transform: 'scale(1.3)', display: 'inline-block' }}>
                    {formatLevelTag(studentScore.scoreLevel)}
                  </div>
                  <div style={{ fontSize: 14, color: '#666', marginTop: 12 }}>等级评定</div>
                </div>
              </div>

              <Divider>五维单项考核得分构成</Divider>

              {/* 五维卡片 */}
              <Row gutter={16}>
                {[
                  { name: '企业指导考核', val: studentScore.enterpriseScore, key: 'enterprise' },
                  { name: '过程指导台账', val: studentScore.processScore, key: 'process' },
                  { name: '周报质量平均', val: studentScore.weeklyScore, key: 'weekly' },
                  { name: '阶段任务材料', val: studentScore.materialScore, key: 'material' },
                  { name: '实习总结报告', val: studentScore.summaryScore, key: 'summary' }
                ].map(dim => (
                  <Col span={4} key={dim.key} style={{ minWidth: 160 }}>
                    <Card size="small" style={{ textAlign: 'center', background: '#fafafa' }}>
                      <Text type="secondary" style={{ fontSize: 13 }}>{dim.name}</Text>
                      <div style={{ fontSize: 24, fontWeight: 'bold', color: '#1f2937', margin: '8px 0' }}>
                        {dim.val?.toFixed(1) ?? '-'}
                      </div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        权重: {getRuleWeight(dim.key, studentScore.gradeRuleSnapshotJson)}%
                      </Text>
                    </Card>
                  </Col>
                ))}
              </Row>

              {/* 导师评语 */}
              {studentScore.evaluationComment && (
                <Card size="small" style={{ marginTop: 24, background: '#f8fafc' }}>
                  <Text strong>导师综合评语：</Text>
                  <Paragraph style={{ margin: '8px 0 0 0' }}>{studentScore.evaluationComment}</Paragraph>
                </Card>
              )}

              {/* 公示期提示 */}
              {studentScore.publicityStartTime && (
                <div style={{ marginTop: 16 }}>
                  <Alert
                    type="info"
                    showIcon
                    message={`公示申诉期：${studentScore.publicityStartTime} 至 ${studentScore.publicityEndTime || '无'}`}
                    description="公示期间若对综合评分或五维算分存在异议，可在该有效窗口期内发起一次成绩复核申诉。"
                  />
                </div>
              )}

              {/* 申诉操作栏 */}
              {canStudentAppeal(studentScore) && (
                <div style={{ textAlign: 'center', marginTop: 24 }}>
                  <Button type="primary" danger icon={<EditOutlined />} onClick={handleOpenAppealModal}>
                    对成绩有异议？发起成绩复核申诉
                  </Button>
                </div>
              )}

              {/* 申诉与复核历史痕迹 */}
              {studentScore.auditHistory && studentScore.auditHistory.length > 0 && (
                <div style={{ marginTop: 32 }}>
                  <Title level={5} style={{ marginBottom: 16 }}>成绩申诉与复核轨迹</Title>
                  <Timeline
                    items={studentScore.auditHistory.map(item => ({
                      color: item.action.includes('PASS') ? 'green' : (item.action.includes('REJECT') ? 'red' : 'blue'),
                      children: (
                        <div>
                          <div>
                            <Text strong>{formatAuditAction(item.action)}</Text>
                            <Text type="secondary" style={{ marginLeft: 8 }}>
                              （经办人：{item.auditUserName || '-'}，时间：{item.operateTime}）
                            </Text>
                          </div>
                          {item.appealReason && <div><Text type="secondary">申诉理由：{item.appealReason}</Text></div>}
                          {item.auditComment && <div><Text type="secondary">处理意见：{item.auditComment}</Text></div>}
                          {item.approvalDocNo && (
                            <div><Text type="danger">批文文号：{item.approvalDocNo}</Text></div>
                          )}
                        </div>
                      )
                    }))}
                  />
                </div>
              )}
            </div>
          ) : (
            <Empty description="指导教师尚未完成最终综合评定或成绩尚未进入公示期" />
          )}
        </Card>
      ) : (
        /* 教师 / 管理员端视图 */
        <div>
          {/* 筛选栏 */}
          <Card style={{ marginBottom: 16, borderRadius: 8 }}>
            <Form form={filterForm} layout="inline">
              <Form.Item name="taskId" label="实习任务">
                <Select
                  placeholder="选择实习任务"
                  allowClear
                  style={{ width: 220 }}
                  options={tasks.map(t => ({ label: t.taskName, value: t.id }))}
                  onChange={loadScoresData}
                />
              </Form.Item>
              <Form.Item name="scoreLevel" label="等级评定">
                <Select
                  placeholder="全部等级"
                  allowClear
                  style={{ width: 140 }}
                  options={[
                    { label: '优秀 (>=90)', value: 'EXCELLENT' },
                    { label: '良好 (80-89)', value: 'GOOD' },
                    { label: '中等 (70-79)', value: 'MEDIUM' },
                    { label: '及格 (60-69)', value: 'PASS' },
                    { label: '不及格 (<60)', value: 'FAIL' }
                  ]}
                  onChange={loadScoresData}
                />
              </Form.Item>
              <Form.Item name="status" label="成绩状态">
                <Select
                  placeholder="全部状态"
                  allowClear
                  style={{ width: 150 }}
                  options={[
                    { label: '暂存草稿', value: 'DRAFT' },
                    { label: '待院系终审', value: 'PENDING_AUDIT' },
                    { label: '待公示', value: 'PENDING_PUBLICITY' },
                    { label: '公示申诉期', value: 'PUBLICITY' },
                    { label: '已归档发布', value: 'PUBLISHED' }
                  ]}
                  onChange={loadScoresData}
                />
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" icon={<SearchOutlined />} onClick={loadScoresData}>
                    查询
                  </Button>
                  <Button onClick={handleResetFilters}>重置</Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>

          {/* 表格区 */}
          <Card style={{ borderRadius: 8 }}>
            <Table
              columns={columns}
              dataSource={scoreList}
              rowKey="id"
              loading={loading}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 1200 }}
            />
          </Card>
        </div>
      )}

      {/* 成绩详情弹窗 */}
      <Modal
        title="实习综合评定成绩详情与审计"
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalOpen(false)}>
            关闭
          </Button>
        ]}
        width={680}
      >
        {currentScore && (
          <div>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="学生姓名">{currentScore.studentName}</Descriptions.Item>
              <Descriptions.Item label="学号">{currentScore.studentNo}</Descriptions.Item>
              <Descriptions.Item label="所属院系">{currentScore.deptName}</Descriptions.Item>
              <Descriptions.Item label="指导教师">{currentScore.teacherName || '-'}</Descriptions.Item>
              <Descriptions.Item label="综合总分">
                <Text strong style={{ color: '#1677ff', fontSize: 16 }}>
                  {currentScore.finalScore?.toFixed(2) ?? '-'}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="评定等级">
                {formatLevelTag(currentScore.scoreLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="当前状态" span={2}>
                {formatStatusTag(currentScore.status)}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ margin: '16px 0 12px 0' }}>五维得分构成</Divider>
            <Descriptions bordered column={5} size="small">
              <Descriptions.Item label="企业指导">{currentScore.enterpriseScore?.toFixed(1) ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="过程指导">{currentScore.processScore?.toFixed(1) ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="周报均分">{currentScore.weeklyScore?.toFixed(1) ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="阶段材料">{currentScore.materialScore?.toFixed(1) ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="总结报告">{currentScore.summaryScore?.toFixed(1) ?? '-'}</Descriptions.Item>
            </Descriptions>

            {currentScore.evaluationComment && (
              <div style={{ marginTop: 14 }}>
                <Text strong>导师综合评语：</Text>
                <div style={{ marginTop: 4, background: '#fafafa', padding: 8, borderRadius: 4 }}>
                  {currentScore.evaluationComment}
                </div>
              </div>
            )}

            {currentScore.enterpriseEvaluationUrl && (
              <div style={{ marginTop: 10 }}>
                <Text strong>企业评定佐证凭据：</Text>
                <a href={currentScore.enterpriseEvaluationUrl} target="_blank" rel="noreferrer" style={{ marginLeft: 8 }}>
                  查看企业评价附件
                </a>
              </div>
            )}

            {currentScore.auditHistory && currentScore.auditHistory.length > 0 && (
              <div style={{ marginTop: 20 }}>
                <Divider orientation="left" style={{ margin: '16px 0 12px 0' }}>申诉复核与调分审计快照</Divider>
                <Timeline
                  items={currentScore.auditHistory.map(h => ({
                    color: h.action.includes('PASS') ? 'green' : (h.action.includes('REJECT') ? 'red' : 'blue'),
                    children: (
                      <div>
                        <div>
                          <Text strong>{formatAuditAction(h.action)}</Text>
                          <Text type="secondary" style={{ marginLeft: 8 }}>
                            （操作人: {h.auditUserName || '-'}，时间: {h.operateTime}）
                          </Text>
                        </div>
                        {h.approvalDocNo && <div><Text type="danger">红头批文号: {h.approvalDocNo}</Text></div>}
                        {h.auditComment && <div><Text type="secondary">处理意见: {h.auditComment}</Text></div>}
                      </div>
                    )
                  }))}
                />
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* 教师评定录入弹窗 */}
      <Modal
        title="实习综合成绩五维评定录入"
        open={entryModalOpen}
        onCancel={() => setEntryModalOpen(false)}
        onOk={handleScoreEntrySubmit}
        confirmLoading={entrySubmitting}
        okText="确认保存评定"
        cancelText="取消"
        width={600}
      >
        <Alert
          type="info"
          showIcon
          message="根据学院培养方案，系统自动加权算分。周报均分、材料分与总结分建议提前批阅归档。"
          style={{ marginBottom: 16 }}
        />
        <Form form={entryForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="enterpriseScore"
                label="企业指导评价分"
                rules={[{ required: true, message: '请录入企业评价分' }]}
              >
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="processScore"
                label="过程指导得分"
                rules={[{ required: true, message: '请录入过程指导分' }]}
              >
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="weeklyScore"
                label="周报质量均分"
                rules={[{ required: true, message: '请录入周报得分' }]}
              >
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="materialScore"
                label="阶段材料得分"
                rules={[{ required: true, message: '请录入材料得分' }]}
              >
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="summaryScore"
                label="实习总结报告得分"
                rules={[{ required: true, message: '请录入总结得分' }]}
              >
                <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="enterpriseEvaluationUrl" label="企业评语凭据URL">
            <Input placeholder="企业评价表PDF/扫描件URL (需HTTPS)" />
          </Form.Item>

          <Form.Item name="evaluationComment" label="导师综合评语">
            <Input.TextArea rows={3} placeholder="请填写对该生实习全过程的综合评价与指导意见" />
          </Form.Item>

          <Form.Item name="submitToDept" label="提交模式" valuePropName="checked">
            <Switch checkedChildren="直接提交院系审核" unCheckedChildren="仅暂存为草稿" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 学生申诉弹窗 */}
      <Modal
        title="发起实习成绩复核申诉"
        open={appealModalOpen}
        onCancel={() => setAppealModalOpen(false)}
        onOk={handleAppealSubmit}
        confirmLoading={appealSubmitting}
        okText="提交申诉申请"
        cancelText="取消"
        width={520}
      >
        <Alert
          type="warning"
          showIcon
          message="每位学生针对本次综合成绩仅有一次申诉机会，请审慎填写申诉原因并提供有效佐证材料。"
          style={{ marginBottom: 16 }}
        />
        <Form form={appealForm} layout="vertical">
          <Form.Item
            name="appealReason"
            label="申诉理由"
            rules={[{ required: true, message: '请详细填写申诉理由' }]}
          >
            <Input.TextArea
              rows={4}
              placeholder="请详细阐述评分存在异议的具体环节（如周报均分核算遗漏、企业评分误差等）"
            />
          </Form.Item>
          <Form.Item name="appealAttachmentUrl" label="佐证凭据URL">
            <Input placeholder="佐证材料URL (需HTTPS)" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 院系负责人成绩申诉仲裁与调分弹窗 */}
      <Modal
        title="院系级成绩申诉复核与仲裁"
        open={arbitrateModalOpen}
        onCancel={() => setArbitrateModalOpen(false)}
        onOk={handleArbitrateSubmit}
        confirmLoading={arbitrateSubmitting || arbitrateLoading}
        okText="确认提交仲裁决定"
        cancelText="取消"
        width={600}
      >
        {activeAppeal && (
          <Alert
            type="info"
            showIcon
            message={`学生【${activeArbitrateRow?.studentName || ''}】成绩申诉申请`}
            description={
              <div style={{ marginTop: 6 }}>
                <div><Text strong>申诉理由：</Text>{activeAppeal.appealReason}</div>
                {activeAppeal.appealAttachmentUrl && (
                  <div style={{ marginTop: 4 }}>
                    <Text strong>佐证凭据：</Text>
                    <a href={activeAppeal.appealAttachmentUrl} target="_blank" rel="noreferrer">
                      查看佐证附件
                    </a>
                  </div>
                )}
                <div style={{ marginTop: 4, fontSize: 12, color: '#888' }}>
                  申请时间：{activeAppeal.operateTime}
                </div>
              </div>
            }
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={arbitrateForm} layout="vertical">
          <Form.Item name="action" label="仲裁裁定" rules={[{ required: true }]}>
            <Radio.Group onChange={e => setArbitrateActionType(e.target.value)}>
              <Radio value="PASS">申诉成立，准予调分</Radio>
              <Radio value="REJECT">申诉驳回，维持原判</Radio>
            </Radio.Group>
          </Form.Item>

          {arbitrateActionType === 'PASS' && (
            <Form.Item
              name="approvalDocNo"
              label="红头批文号"
              rules={[{ required: true, message: '调分更正必须具备红头批文号' }]}
            >
              <Input placeholder="如: 计通院发[2026]18号成绩更正批复" />
            </Form.Item>
          )}

          <Form.Item
            name="auditComment"
            label="仲裁审核意见"
            rules={[{ required: true, message: '请填写仲裁意见' }]}
          >
            <Input.TextArea rows={3} placeholder="请填写仲裁委员会或教学主管复议结论意见" />
          </Form.Item>

          {arbitrateActionType === 'PASS' && (
            <div>
              <Divider orientation="left">仲裁更正五维得分</Divider>
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="enterpriseScore" label="更正企业得分">
                    <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="processScore" label="更正过程得分">
                    <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="weeklyScore" label="更正周报得分">
                    <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="materialScore" label="更正材料得分">
                    <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="summaryScore" label="更正总结得分">
                    <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
};
