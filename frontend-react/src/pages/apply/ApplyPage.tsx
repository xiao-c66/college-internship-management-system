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
  DatePicker,
  Radio,
  Drawer,
  Timeline,
  Descriptions,
  Alert,
  Empty,
  Typography,
  message,
  Divider,
  Modal,
  Tabs,
  Badge
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  FileTextOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SearchOutlined,
  ReloadOutlined,
  LockOutlined,
  AuditOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SendOutlined,
  SaveOutlined,
  EyeOutlined,
  SwapOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getMyApply,
  getApplyById,
  saveDraft,
  submitApply,
  listApplies,
  auditApply,
  getAuditHistories,
  ApplyVO,
  AuditHistoryVO
} from '../../api/apply';
import {
  listApplyChangesApi,
  getActiveApplyChangeApi,
  ApplyChangeVO
} from '../../api/applyChange';
import { ApplyChangeModal } from './ApplyChangeModal';
import { ApplyChangeAuditDrawer, getChangeStatusTag } from './ApplyChangeAuditDrawer';
import { getTaskList, TaskItem } from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;
const { RangePicker } = DatePicker;

// 状态标签映射
export const getApplyStatusTag = (status?: string) => {
  switch (status) {
    case 'DRAFT':
      return <Tag color="default">草稿待提交</Tag>;
    case 'SUBMITTED':
      return <Tag color="processing">待导师初审</Tag>;
    case 'TEACHER_APPROVED':
      return <Tag color="cyan">初审通过·待院系终审</Tag>;
    case 'TEACHER_REJECTED':
      return <Tag color="error">导师初审退回</Tag>;
    case 'APPROVED':
      return <Tag color="success">终审通过·已锁定 (APPLY-009)</Tag>;
    case 'DEPT_REJECTED':
      return <Tag color="error">院系终审退回</Tag>;
    default:
      return <Tag>{status || '未申报'}</Tag>;
  }
};

export const ApplyPage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';
  const isDeptAdmin = userType === 'DEPT_ADMIN';
  const isAdmin = userType === 'SYS_ADMIN';

  // 公共状态
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  // 学生端特有状态
  const [studentApply, setStudentApply] = useState<ApplyVO | null>(null);
  const [studentForm] = Form.useForm();
  const [saveLoading, setSaveLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);

  // 管理端/教师端列表特有状态
  const [applies, setApplies] = useState<ApplyVO[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedApply, setSelectedApply] = useState<ApplyVO | null>(null);
  const [drawerHistories, setDrawerHistories] = useState<AuditHistoryVO[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditForm] = Form.useForm();

  // 实习重大变更状态
  const [activeChange, setActiveChange] = useState<ApplyChangeVO | null>(null);
  const [changeModalVisible, setChangeModalVisible] = useState(false);
  const [changeAuditDrawerVisible, setChangeAuditDrawerVisible] = useState(false);
  const [selectedChange, setSelectedChange] = useState<ApplyChangeVO | null>(null);

  // 教师/管理员重大变更Tab状态
  const [manageTab, setManageTab] = useState<'applies' | 'changes'>('applies');
  const [changeList, setChangeList] = useState<ApplyChangeVO[]>([]);
  const [changeFilterStatus, setChangeFilterStatus] = useState<string>('');
  const [changeLoading, setChangeLoading] = useState(false);

  const loadActiveChange = async (applyId: number) => {
    try {
      const res: any = await getActiveApplyChangeApi(applyId);
      if (res.code === 200) {
        setActiveChange(res.data || null);
      }
    } catch {}
  };

  const loadChangeList = async (taskId?: number, status?: string) => {
    setChangeLoading(true);
    try {
      const res: any = await listApplyChangesApi({
        taskId: taskId || selectedTaskId,
        status: status !== undefined ? status : changeFilterStatus
      });
      if (res.code === 200 && res.data) {
        setChangeList(res.data);
      }
    } catch (err: any) {
      message.error(err.message || '加载变更申请列表失败');
    } finally {
      setChangeLoading(false);
    }
  };

  // 1. 初始化加载任务批次列表
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

  // 2. 加载学生自身申报
  const loadStudentApply = async (taskId: number) => {
    setLoading(true);
    try {
      const res = await getMyApply(taskId);
      if (res.code === 200 && res.data) {
        setStudentApply(res.data);
        studentForm.setFieldsValue({
          companyName: res.data.companyName,
          jobPosition: res.data.jobPosition,
          jobAddress: res.data.jobAddress,
          companyContactPerson: res.data.companyContactPerson,
          companyContactPhone: res.data.companyContactPhone,
          companyContactEmail: res.data.companyContactEmail,
          internshipMode: res.data.internshipMode || 'DISTRIBUTED',
          jobDuties: res.data.jobDuties,
          agreementFileUrl: res.data.agreementFileUrl,
          dateRange: res.data.startDate && res.data.endDate
            ? [dayjs(res.data.startDate), dayjs(res.data.endDate)]
            : undefined
        });
        if (res.data.isLocked === 1 || res.data.applyStatus === 'APPROVED') {
          loadActiveChange(res.data.id);
        }
      } else {
        setStudentApply(null);
        studentForm.resetFields();
      }
    } catch {
      setStudentApply(null);
      studentForm.resetFields();
    } finally {
      setLoading(false);
    }
  };

  // 3. 加载管理/教师端审批单据列表
  const loadManageApplies = async (taskId?: number, status?: string) => {
    setLoading(true);
    try {
      const res = await listApplies({
        taskId,
        status: status || undefined
      });
      if (res.code === 200 && res.data) {
        setApplies(res.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!selectedTaskId) return;
    if (isStudent) {
      loadStudentApply(selectedTaskId);
    } else {
      if (manageTab === 'applies') {
        loadManageApplies(selectedTaskId, filterStatus);
      } else {
        loadChangeList(selectedTaskId, changeFilterStatus);
      }
    }
  }, [selectedTaskId, isStudent, manageTab]);

  // 学生暂存草稿
  const handleSaveDraft = async () => {
    if (!selectedTaskId) return;
    try {
      const values = await studentForm.validateFields();
      setSaveLoading(true);
      const payload = {
        taskId: selectedTaskId,
        companyName: values.companyName,
        jobPosition: values.jobPosition,
        jobAddress: values.jobAddress,
        companyContactPerson: values.companyContactPerson,
        companyContactPhone: values.companyContactPhone,
        companyContactEmail: values.companyContactEmail,
        internshipMode: values.internshipMode,
        jobDuties: values.jobDuties,
        agreementFileUrl: values.agreementFileUrl || '/uploads/agreements/sample.pdf',
        startDate: values.dateRange ? values.dateRange[0].format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
        endDate: values.dateRange ? values.dateRange[1].format('YYYY-MM-DD') : dayjs().add(90, 'day').format('YYYY-MM-DD')
      };

      const res = await saveDraft(payload);
      if (res.code === 200) {
        message.success('申报草稿暂存成功 (APPLY-006)');
        loadStudentApply(selectedTaskId);
      }
    } catch {}
    finally {
      setSaveLoading(false);
    }
  };

  // 学生正式提交
  const handleSubmitApply = async () => {
    if (!selectedTaskId) return;
    try {
      const values = await studentForm.validateFields();
      setSubmitLoading(true);
      const payload = {
        taskId: selectedTaskId,
        companyName: values.companyName,
        jobPosition: values.jobPosition,
        jobAddress: values.jobAddress,
        companyContactPerson: values.companyContactPerson,
        companyContactPhone: values.companyContactPhone,
        companyContactEmail: values.companyContactEmail,
        internshipMode: values.internshipMode,
        jobDuties: values.jobDuties,
        agreementFileUrl: values.agreementFileUrl || '/uploads/agreements/sample.pdf',
        startDate: values.dateRange ? values.dateRange[0].format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
        endDate: values.dateRange ? values.dateRange[1].format('YYYY-MM-DD') : dayjs().add(90, 'day').format('YYYY-MM-DD')
      };

      const res = await submitApply(payload);
      if (res.code === 200) {
        message.success('实习申报已正式提交，进入指导教师初审队列');
        loadStudentApply(selectedTaskId);
      }
    } catch {}
    finally {
      setSubmitLoading(false);
    }
  };

  // 打开审核抽屉
  const handleOpenDrawer = async (record: ApplyVO) => {
    setSelectedApply(record);
    setDrawerVisible(true);
    auditForm.resetFields();
    auditForm.setFieldsValue({ action: 'APPROVED', opinion: '同意申报，符合专业实习与安全要求。' });
    try {
      const [detailRes, histRes] = await Promise.all([
        getApplyById(record.id),
        getAuditHistories(record.id)
      ]);
      if (detailRes.code === 200 && detailRes.data) {
        setSelectedApply(detailRes.data);
      }
      if (histRes.code === 200 && histRes.data) {
        setDrawerHistories(histRes.data);
      }
    } catch {}
  };

  // 提交审核操作
  const handleExecuteAudit = async () => {
    if (!selectedApply) return;
    try {
      const values = await auditForm.validateFields();
      if (values.action === 'REJECTED' && (!values.opinion || values.opinion.trim().length < 5)) {
        message.error('退回修改时，退回原因必须不少于 5 个字符！');
        return;
      }
      setAuditLoading(true);
      const res = await auditApply(selectedApply.id, {
        action: values.action,
        opinion: values.opinion
      });
      if (res.code === 200) {
        message.success('审核流转处理完成');
        setDrawerVisible(false);
        loadManageApplies(selectedTaskId, filterStatus);
      }
    } catch {}
    finally {
      setAuditLoading(false);
    }
  };

  // 检查是否具备当前行审核权限
  const canAuditRecord = (row: ApplyVO) => {
    if (isTeacher && row.applyStatus === 'SUBMITTED') return true;
    if ((isDeptAdmin || isAdmin) && row.applyStatus === 'TEACHER_APPROVED') return true;
    return false;
  };

  const isApprovedLocked = studentApply?.applyStatus === 'APPROVED' || studentApply?.isLocked === 1;
  const isRejected = studentApply?.applyStatus === 'TEACHER_REJECTED' || studentApply?.applyStatus === 'DEPT_REJECTED';

  // 审批中心表格列定义
  const columns: ColumnsType<ApplyVO> = [
    {
      title: '申报ID',
      dataIndex: 'id',
      key: 'id',
      width: 90
    },
    {
      title: '学生姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 120,
      render: (name) => <strong>{name}</strong>
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
    },
    {
      title: '实习用人单位',
      dataIndex: 'companyName',
      key: 'companyName',
      ellipsis: true
    },
    {
      title: '实习岗位',
      dataIndex: 'jobPosition',
      key: 'jobPosition',
      width: 150
    },
    {
      title: '实习起止日期',
      key: 'dates',
      width: 220,
      render: (_, r) => `${r.startDate || '-'} ~ ${r.endDate || '-'}`
    },
    {
      title: '申报状态',
      dataIndex: 'applyStatus',
      key: 'applyStatus',
      width: 170,
      render: (status) => getApplyStatusTag(status)
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type={canAuditRecord(record) ? 'primary' : 'link'}
          size="small"
          icon={canAuditRecord(record) ? <AuditOutlined /> : <EyeOutlined />}
          onClick={() => handleOpenDrawer(record)}
        >
          {canAuditRecord(record) ? '执行审核' : '查看详情'}
        </Button>
      )
    }
  ];

  // 实习重大变更单据表格列定义
  const changeColumns: ColumnsType<ApplyChangeVO> = [
    {
      title: '变更单号',
      dataIndex: 'id',
      key: 'id',
      width: 90,
      render: (id) => <Text code>#{id}</Text>
    },
    {
      title: '学生信息',
      key: 'student',
      width: 140,
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 'bold' }}>{record.studentName}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.studentNumber}</Text>
        </div>
      )
    },
    {
      title: '实习重大变更走向',
      key: 'transition',
      width: 280,
      render: (_, record) => (
        <div>
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>
            原单位：<span style={{ textDecoration: 'line-through' }}>{record.origCompanyName}</span>
          </div>
          <div style={{ fontWeight: 'bold', color: '#1890ff', fontSize: 13 }}>
            拟变更为：{record.newCompanyName}
          </div>
          <Tag color="blue" style={{ marginTop: 2 }}>{record.newJobPosition}</Tag>
        </div>
      )
    },
    {
      title: '变更事由',
      dataIndex: 'changeReason',
      key: 'changeReason',
      ellipsis: true,
      width: 220
    },
    {
      title: '审批状态',
      dataIndex: 'changeStatus',
      key: 'changeStatus',
      width: 130,
      render: (status) => getChangeStatusTag(status)
    },
    {
      title: '申请时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 130,
      render: (_, record) => {
        const needTeacherAudit = (userType === 'TEACHER' || userType === 'SYS_ADMIN') && record.changeStatus === 'PENDING_TEACHER';
        const needDeptAudit = (userType === 'DEPT_ADMIN' || userType === 'SYS_ADMIN') && record.changeStatus === 'PENDING_DEPT';
        const highlight = needTeacherAudit || needDeptAudit;

        return (
          <Button
            size="small"
            type={highlight ? 'primary' : 'default'}
            icon={highlight ? <AuditOutlined /> : <EyeOutlined />}
            onClick={() => {
              setSelectedChange(record);
              setChangeAuditDrawerVisible(true);
            }}
          >
            {highlight ? '执行审核' : '查看详情'}
          </Button>
        );
      }
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 顶部标题与任务批次切换 */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {isStudent ? '学生校外实习申报 (APPLY-001 ~ APPLY-009)' : '实习申报审批中心 (REVIEW-001 ~ REVIEW-006)'}
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              {isStudent
                ? '录入落实的实习单位、岗位、地址、起止时间与协议凭证，完成导师与院系双级审批'
                : '指导教师行级初审与二级院系终审复核工作台 | 退回修改意见强制不少于5个字符且完整留痕'}
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
            {isStudent && (
              <span style={{ marginLeft: 8 }}>
                {getApplyStatusTag(studentApply?.applyStatus)}
              </span>
            )}
          </Space>
        </div>
      </Card>

      {/* ======================= 学生端视图 ======================= */}
      {isStudent && (
        <>
          {/* APPLY-009 审核生效锁定提示与重大信息变更入口 */}
          {isApprovedLocked && (
            <Card style={{ marginBottom: 20, borderColor: '#faad14', backgroundColor: '#fffbe6' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <LockOutlined style={{ fontSize: 24, color: '#faad14', marginTop: 2 }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 'bold', fontSize: 15, color: '#d48806', marginBottom: 4 }}>
                    🔒 实习信息已经审核生效 (APPROVED)，主数据已物理锁定只读 (APPLY-009)
                  </div>
                  <Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 13 }}>
                    您的实习申请已顺利完成指导教师初审与二级院系终审复核。单位、岗位、地址、联系人和起止时间变动禁止普通修改。
                    如遇实习单位变更或协议重大变动，请在此发起正式变更申请！
                  </Paragraph>

                  {/* 变更申请状态展示 */}
                  {activeChange ? (
                    <div style={{ backgroundColor: '#ffffff', padding: 12, borderRadius: 6, border: '1px solid #ffe58f', marginTop: 8 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <Space>
                          <Text strong>📋 当前实习重大变更单 #{activeChange.id}</Text>
                          {getChangeStatusTag(activeChange.changeStatus)}
                        </Space>
                        <Button
                          size="small"
                          type="primary"
                          ghost
                          icon={<EyeOutlined />}
                          onClick={() => {
                            setSelectedChange(activeChange);
                            setChangeAuditDrawerVisible(true);
                          }}
                        >
                          查看变更流转进度
                        </Button>
                      </div>
                      <Text type="secondary" style={{ fontSize: 13, display: 'block' }}>
                        拟变更单位：<Text strong>{activeChange.newCompanyName}</Text>（岗位：{activeChange.newJobPosition}）
                      </Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        变更事由：{activeChange.changeReason} | 提交时间：{activeChange.createTime}
                      </Text>

                      {(activeChange.changeStatus === 'REJECTED' || activeChange.changeStatus === 'APPROVED') && (
                        <div style={{ marginTop: 8 }}>
                          <Button
                            type="dashed"
                            size="small"
                            icon={<SwapOutlined />}
                            onClick={() => setChangeModalVisible(true)}
                          >
                            再次发起新的重大变更申请
                          </Button>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div style={{ marginTop: 8 }}>
                      <Button
                        type="primary"
                        icon={<SwapOutlined />}
                        onClick={() => setChangeModalVisible(true)}
                      >
                        发起实习重大信息变更申请
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            </Card>
          )}

          {/* 退回修改提示 */}
          {isRejected && (
            <Alert
              type="error"
              showIcon
              icon={<CloseCircleOutlined />}
              message="⚠️ 您的实习申报被退回修改"
              description="请查看右侧流转轨迹中的退回意见，修改完善申报信息后重新提交审核。"
            />
          )}

          <Row gutter={[20, 20]}>
            {/* 左侧：申报表单 */}
            <Col xs={24} lg={15}>
              <Card
                title={
                  <Space>
                    <FileTextOutlined />
                    <span>实习申报详细信息填报</span>
                    {isApprovedLocked && <Tag color="error">已锁定只读 (APPLY-009)</Tag>}
                  </Space>
                }
              >
                <Form
                  form={studentForm}
                  layout="vertical"
                  disabled={isApprovedLocked}
                  initialValues={{
                    internshipMode: 'DISTRIBUTED'
                  }}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="companyName"
                        label="实习用人单位"
                        rules={[{ required: true, message: '请填写企业营业执照法定全称' }]}
                      >
                        <Input placeholder="企业工商登记法定全称" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="jobPosition"
                        label="实习岗位名称"
                        rules={[{ required: true, message: '请输入实习岗位名称' }]}
                      >
                        <Input placeholder="如 Java后端开发实习生" />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Form.Item
                    name="jobAddress"
                    label="岗位工作详细地址"
                    rules={[{ required: true, message: '请输入工作详细地址' }]}
                  >
                    <Input placeholder="省/市/区/街道及楼宇门牌号" />
                  </Form.Item>

                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="companyContactPerson"
                        label="单位联系人/导师"
                        rules={[{ required: true, message: '请输入单位联系人姓名' }]}
                      >
                        <Input placeholder="企业带教或HR姓名" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="companyContactPhone"
                        label="联系人电话"
                        rules={[{ required: true, message: '请输入联系人电话' }]}
                      >
                        <Input placeholder="手机号或带区号座机" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="companyContactEmail" label="联系人邮箱">
                        <Input placeholder="企业工作邮箱" />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Row gutter={16}>
                    <Col xs={24} md={14}>
                      <Form.Item
                        name="dateRange"
                        label="实习起止时间"
                        rules={[{ required: true, message: '请选择实习起止时间' }]}
                      >
                        <RangePicker style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={10}>
                      <Form.Item
                        name="internshipMode"
                        label="实习组织模式"
                        rules={[{ required: true, message: '请选择实习组织模式' }]}
                      >
                        <Select
                          options={[
                            { label: '分散实习 (自主落实)', value: 'DISTRIBUTED' },
                            { label: '集中实习 (基地组织)', value: 'CENTRALIZED' },
                            { label: '混合实习模式', value: 'HYBRID' }
                          ]}
                        />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Form.Item name="jobDuties" label="主要职责与技术任务">
                    <Input.TextArea
                      rows={3}
                      placeholder="详细说明在实习单位从事的技术任务、岗位职责与安全防护要求..."
                    />
                  </Form.Item>

                  <Form.Item name="agreementFileUrl" label="三方协议盖章件/录用证明">
                    <Input placeholder="如 /uploads/agreements/sample_agreement.pdf" />
                  </Form.Item>

                  {!isApprovedLocked && (
                    <div style={{ textAlign: 'right', marginTop: 16 }}>
                      <Space>
                        <Button
                          icon={<SaveOutlined />}
                          loading={saveLoading}
                          onClick={handleSaveDraft}
                        >
                          暂存为草稿 (APPLY-006)
                        </Button>
                        <Button
                          type="primary"
                          icon={<SendOutlined />}
                          loading={submitLoading}
                          onClick={handleSubmitApply}
                        >
                          正式提交实习申报
                        </Button>
                      </Space>
                    </div>
                  )}
                </Form>
              </Card>
            </Col>

            {/* 右侧：审批流转轨迹与历史记录 (REVIEW-006) */}
            <Col xs={24} lg={9}>
              <Card
                title={
                  <Space>
                    <ClockCircleOutlined />
                    <span>审批流转轨迹与历史记录 (REVIEW-006)</span>
                  </Space>
                }
              >
                {studentApply?.auditHistories && studentApply.auditHistories.length > 0 ? (
                  <Timeline
                    items={studentApply.auditHistories.map((h) => ({
                      color: h.auditAction === 'APPROVED' ? 'green' : 'red',
                      children: (
                        <div style={{ marginBottom: 12 }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <strong>{h.nodeName === 'TEACHER_AUDIT' ? '导师初审' : '院系终审'}</strong>
                            <Tag color={h.auditAction === 'APPROVED' ? 'success' : 'error'}>
                              {h.auditAction === 'APPROVED' ? '审核通过' : '退回修改'}
                            </Tag>
                          </div>
                          <div style={{ fontSize: 13, color: '#8c8c8c', marginTop: 4 }}>
                            审核人: {h.auditorName} ({h.auditorRole})
                          </div>
                          <div style={{ fontSize: 13, marginTop: 4 }}>
                            意见: {h.auditOpinion || '无'}
                          </div>
                          <div style={{ fontSize: 12, color: '#bfbfbf', marginTop: 2 }}>
                            时间: {h.auditTime}
                          </div>
                        </div>
                      )
                    }))}
                  />
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无流转记录，提交申报后将在此展示流转轨迹"
                  />
                )}
              </Card>
            </Col>
          </Row>
        </>
      )}

      {/* ======================= 管理端/教师端审批中心 ======================= */}
      {!isStudent && (
        <>
          <Card>
            <Tabs
              activeKey={manageTab}
              onChange={(k) => setManageTab(k as any)}
              items={[
                {
                  key: 'applies',
                  label: '实习申报初审/终审',
                  children: (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                      {/* 筛选过滤卡片 */}
                      <Card size="small">
                        <Row gutter={[16, 16]} align="middle">
                          <Col xs={24} md={8}>
                            <Space>
                              <Text>申报状态：</Text>
                              <Select
                                style={{ width: 220 }}
                                value={filterStatus}
                                onChange={(val) => {
                                  setFilterStatus(val);
                                  loadManageApplies(selectedTaskId, val);
                                }}
                                options={[
                                  { label: '全部状态', value: '' },
                                  { label: '待导师初审 (SUBMITTED)', value: 'SUBMITTED' },
                                  { label: '初审通过·待终审 (TEACHER_APPROVED)', value: 'TEACHER_APPROVED' },
                                  { label: '导师初审退回 (TEACHER_REJECTED)', value: 'TEACHER_REJECTED' },
                                  { label: '终审通过已锁定 (APPROVED)', value: 'APPROVED' },
                                  { label: '院系终审退回 (DEPT_REJECTED)', value: 'DEPT_REJECTED' }
                                ]}
                              />
                            </Space>
                          </Col>
                          <Col xs={24} md={16} style={{ textAlign: 'right' }}>
                            <Space>
                              <Button
                                type="primary"
                                icon={<SearchOutlined />}
                                onClick={() => loadManageApplies(selectedTaskId, filterStatus)}
                              >
                                查询单据
                              </Button>
                              <Button
                                icon={<ReloadOutlined />}
                                onClick={() => {
                                  setFilterStatus('');
                                  loadManageApplies(selectedTaskId, '');
                                }}
                              >
                                重置
                              </Button>
                            </Space>
                          </Col>
                        </Row>
                      </Card>

                      {/* 单据数据表格 */}
                      <Table
                        columns={columns}
                        dataSource={applies}
                        rowKey="id"
                        loading={loading}
                        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔申报记录` }}
                        scroll={{ x: 1200 }}
                      />
                    </div>
                  )
                },
                {
                  key: 'changes',
                  label: (
                    <span>
                      <SwapOutlined style={{ marginRight: 6 }} />
                      实习重大信息变更审核
                      {changeList.filter(c =>
                        (userType === 'TEACHER' && c.changeStatus === 'PENDING_TEACHER') ||
                        (userType === 'DEPT_ADMIN' && c.changeStatus === 'PENDING_DEPT')
                      ).length > 0 && (
                        <Badge
                          count={
                            changeList.filter(c =>
                              (userType === 'TEACHER' && c.changeStatus === 'PENDING_TEACHER') ||
                              (userType === 'DEPT_ADMIN' && c.changeStatus === 'PENDING_DEPT')
                            ).length
                          }
                          style={{ marginLeft: 8 }}
                        />
                      )}
                    </span>
                  ),
                  children: (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                      {/* 变更单筛选过滤卡片 */}
                      <Card size="small">
                        <Row gutter={[16, 16]} align="middle">
                          <Col xs={24} md={8}>
                            <Space>
                              <Text>变更状态：</Text>
                              <Select
                                style={{ width: 240 }}
                                value={changeFilterStatus}
                                onChange={(val) => {
                                  setChangeFilterStatus(val);
                                  loadChangeList(selectedTaskId, val);
                                }}
                                options={[
                                  { label: '全部状态', value: '' },
                                  { label: '待教师初审 (PENDING_TEACHER)', value: 'PENDING_TEACHER' },
                                  { label: '待院系终审 (PENDING_DEPT)', value: 'PENDING_DEPT' },
                                  { label: '终审通过已生效 (APPROVED)', value: 'APPROVED' },
                                  { label: '审核已驳回 (REJECTED)', value: 'REJECTED' }
                                ]}
                              />
                            </Space>
                          </Col>
                          <Col xs={24} md={16} style={{ textAlign: 'right' }}>
                            <Space>
                              <Button
                                type="primary"
                                icon={<SearchOutlined />}
                                onClick={() => loadChangeList(selectedTaskId, changeFilterStatus)}
                              >
                                查询变更
                              </Button>
                              <Button
                                icon={<ReloadOutlined />}
                                onClick={() => {
                                  setChangeFilterStatus('');
                                  loadChangeList(selectedTaskId, '');
                                }}
                              >
                                重置
                              </Button>
                            </Space>
                          </Col>
                        </Row>
                      </Card>

                      {/* 变更单据表格 */}
                      <Table
                        columns={changeColumns}
                        dataSource={changeList}
                        rowKey="id"
                        loading={changeLoading}
                        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔变更申请` }}
                        scroll={{ x: 1200 }}
                      />
                    </div>
                  )
                }
              ]}
            />
          </Card>

          {/* 审核/查看抽屉 */}
          <Drawer
            title={
              selectedApply && canAuditRecord(selectedApply)
                ? '实习申报业务审批流转'
                : '实习申报详细内容与审批历史'
            }
            width={720}
            open={drawerVisible}
            onClose={() => setDrawerVisible(false)}
            destroyOnClose
          >
            {selectedApply && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                {/* 状态徽标 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Title level={5} style={{ margin: 0 }}>
                    {selectedApply.studentName} 的实习申报单据
                  </Title>
                  <div>{getApplyStatusTag(selectedApply.applyStatus)}</div>
                </div>

                {/* 学生与企业基本信息 */}
                <Descriptions title="学生与申报企业基础信息" bordered size="small" column={2}>
                  <Descriptions.Item label="学生姓名">{selectedApply.studentName}</Descriptions.Item>
                  <Descriptions.Item label="学号">{selectedApply.studentNumber}</Descriptions.Item>
                  <Descriptions.Item label="所属院系">{selectedApply.deptName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="所属班级">{selectedApply.className || '-'}</Descriptions.Item>
                  <Descriptions.Item label="实习单位" span={2}>
                    <strong>{selectedApply.companyName}</strong>
                  </Descriptions.Item>
                  <Descriptions.Item label="实习岗位">{selectedApply.jobPosition}</Descriptions.Item>
                  <Descriptions.Item label="组织模式">{selectedApply.internshipMode}</Descriptions.Item>
                  <Descriptions.Item label="工作详细地址" span={2}>
                    {selectedApply.jobAddress}
                  </Descriptions.Item>
                  <Descriptions.Item label="企业联系人">{selectedApply.companyContactPerson}</Descriptions.Item>
                  <Descriptions.Item label="联系人电话">{selectedApply.companyContactPhone}</Descriptions.Item>
                  <Descriptions.Item label="起止日期" span={2}>
                    {selectedApply.startDate} 至 {selectedApply.endDate}
                  </Descriptions.Item>
                  <Descriptions.Item label="主要职责" span={2}>
                    {selectedApply.jobDuties || '无特别说明'}
                  </Descriptions.Item>
                  <Descriptions.Item label="协议文件" span={2}>
                    {selectedApply.agreementFileUrl || '无上传凭证'}
                  </Descriptions.Item>
                </Descriptions>

                <Divider style={{ margin: '12px 0' }} />

                {/* 审批流转历史 */}
                <div>
                  <Title level={5}>历史流转记录 (REVIEW-006)</Title>
                  {drawerHistories.length > 0 ? (
                    <Timeline
                      items={drawerHistories.map((h) => ({
                        color: h.auditAction === 'APPROVED' ? 'green' : 'red',
                        children: (
                          <div style={{ marginBottom: 10 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                              <strong>{h.nodeName === 'TEACHER_AUDIT' ? '导师初审' : '院系终审'}</strong>
                              <Tag color={h.auditAction === 'APPROVED' ? 'success' : 'error'}>
                                {h.auditAction === 'APPROVED' ? '通过' : '退回修改'}
                              </Tag>
                            </div>
                            <div style={{ fontSize: 13, color: '#8c8c8c' }}>
                              审核人: {h.auditorName} ({h.auditorRole}) | 时间: {h.auditTime}
                            </div>
                            <div style={{ fontSize: 13, marginTop: 4 }}>
                              意见: {h.auditOpinion}
                            </div>
                          </div>
                        )
                      }))}
                    />
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批流转记录" />
                  )}
                </div>

                {/* 审核操作表单 (如果具备审核权限) */}
                {canAuditRecord(selectedApply) && (
                  <>
                    <Divider style={{ margin: '12px 0' }} />
                    <Card
                      size="small"
                      title={<span><AuditOutlined /> 执行本级审批操作</span>}
                      style={{ backgroundColor: '#fafafa' }}
                    >
                      <Form form={auditForm} layout="vertical">
                        <Form.Item
                          name="action"
                          label="审核决定"
                          rules={[{ required: true, message: '请选择审核决定' }]}
                        >
                          <Radio.Group>
                            <Radio value="APPROVED">
                              <Tag color="success">审核通过 (同意)</Tag>
                            </Radio>
                            <Radio value="REJECTED">
                              <Tag color="error">退回修改 (需不少于5字退回原因)</Tag>
                            </Radio>
                          </Radio.Group>
                        </Form.Item>

                        <Form.Item
                          name="opinion"
                          label="审核意见与批语"
                          rules={[
                            ({ getFieldValue }) => ({
                              validator(_, value) {
                                if (getFieldValue('action') === 'REJECTED') {
                                  if (!value || value.trim().length < 5) {
                                    return Promise.reject(new Error('退回修改时，退回原因必须不少于 5 个字符！'));
                                  }
                                }
                                return Promise.resolve();
                              }
                            })
                          ]}
                        >
                          <Input.TextArea rows={3} placeholder="请填写审核批语，退回时必须详述修改要求（不少于5字）..." />
                        </Form.Item>

                        <div style={{ textAlign: 'right' }}>
                          <Space>
                            <Button onClick={() => setDrawerVisible(false)}>取消</Button>
                            <Button
                              type="primary"
                              loading={auditLoading}
                              onClick={handleExecuteAudit}
                            >
                              提交审批决定
                            </Button>
                          </Space>
                        </div>
                      </Form>
                    </Card>
                  </>
                )}
              </div>
            )}
          </Drawer>
        </>
      )}

      {/* 实习重大变更申请模态框 (学生端) */}
      <ApplyChangeModal
        visible={changeModalVisible}
        onCancel={() => setChangeModalVisible(false)}
        onSuccess={() => {
          setChangeModalVisible(false);
          if (studentApply) {
            loadActiveChange(studentApply.id);
          }
        }}
        originalApply={studentApply}
      />

      {/* 实习重大变更审核/详情抽屉 (教师/院系管理端) */}
      <ApplyChangeAuditDrawer
        visible={changeAuditDrawerVisible}
        onClose={() => setChangeAuditDrawerVisible(false)}
        onSuccess={() => {
          setChangeAuditDrawerVisible(false);
          if (isStudent && studentApply) {
            loadActiveChange(studentApply.id);
          } else {
            loadChangeList(selectedTaskId, changeFilterStatus);
          }
        }}
        changeData={selectedChange}
      />
    </div>
  );
};
