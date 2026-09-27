import React, { useState, useEffect } from 'react';
import {
  Card,
  Descriptions,
  Tag,
  Button,
  Space,
  Row,
  Col,
  Tabs,
  Table,
  Input,
  Select,
  Modal,
  message,
  Typography,
  Spin,
  Alert
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  UserOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  SearchOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  getTaskDetail,
  getTaskStudents,
  getAvailableTeachers,
  assignTeacher,
  TaskItem,
  TaskStudentItem,
  TeacherSimpleItem
} from '../../api/task';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const TaskDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { userType, hasRole } = useAuthStore();
  const canManage = hasRole(['DEPT_ADMIN', 'SYS_ADMIN']);

  const taskId = Number(id);

  const [task, setTask] = useState<TaskItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') || 'specs');

  // 学生与导师分配相关
  const [students, setStudents] = useState<TaskStudentItem[]>([]);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedStudentIds, setSelectedStudentIds] = useState<number[]>([]);

  // 导师指派弹窗
  const [assignModalVisible, setAssignModalVisible] = useState(false);
  const [assignTargetStudents, setAssignTargetStudents] = useState<number[]>([]);
  const [availableTeachers, setAvailableTeachers] = useState<TeacherSimpleItem[]>([]);
  const [selectedTeacherId, setSelectedTeacherId] = useState<number | undefined>(undefined);
  const [assignLoading, setAssignLoading] = useState(false);

  const loadDetail = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      const res = await getTaskDetail(taskId);
      if (res.code === 200 && res.data) {
        setTask(res.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  const loadStudents = async () => {
    if (!taskId) return;
    setStudentsLoading(true);
    try {
      const res = await getTaskStudents(taskId, { keyword: keyword || undefined });
      if (res.code === 200 && res.data) {
        setStudents(res.data);
      }
    } catch {}
    finally {
      setStudentsLoading(false);
    }
  };

  const loadTeachers = async () => {
    if (!taskId) return;
    try {
      const res = await getAvailableTeachers(taskId);
      if (res.code === 200 && res.data) {
        setAvailableTeachers(res.data);
      }
    } catch {}
  };

  useEffect(() => {
    loadDetail();
  }, [taskId]);

  useEffect(() => {
    if (activeTab === 'students' && canManage) {
      loadStudents();
      loadTeachers();
    }
  }, [activeTab, taskId, canManage]);

  const handleOpenAssignModal = (targetIds: number[]) => {
    setAssignTargetStudents(targetIds);
    setSelectedTeacherId(undefined);
    setAssignModalVisible(true);
    loadTeachers();
  };

  const handleConfirmAssign = async () => {
    if (!selectedTeacherId) {
      message.error('请选择需要指派的指导教师');
      return;
    }
    if (assignTargetStudents.length === 0) {
      message.error('请选择需要分配的学生');
      return;
    }

    setAssignLoading(true);
    try {
      const res = await assignTeacher(taskId, {
        teacherId: selectedTeacherId,
        studentIds: assignTargetStudents
      });
      if (res.code === 200) {
        message.success('指导教师分配成功');
        setAssignModalVisible(false);
        setSelectedStudentIds([]);
        loadStudents();
      }
    } catch {}
    finally {
      setAssignLoading(false);
    }
  };

  const formatStatus = (status?: string) => {
    switch (status) {
      case 'DRAFT': return <Tag color="default">草稿 (DRAFT)</Tag>;
      case 'PUBLISHED': return <Tag color="processing">已发布 (PUBLISHED)</Tag>;
      case 'IN_PROGRESS': return <Tag color="success">进行中 (IN_PROGRESS)</Tag>;
      case 'ENDED': return <Tag color="error">已结束 (ENDED)</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  const studentColumns: ColumnsType<TaskStudentItem> = [
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
      title: '所属班级',
      dataIndex: 'className',
      key: 'className',
      width: 180,
      render: (c) => c || '未绑定班级'
    },
    {
      title: '指导教师',
      key: 'teacherName',
      width: 180,
      render: (_, record) => (
        record.teacherName ? (
          <Tag color="cyan"><UserOutlined /> {record.teacherName}</Tag>
        ) : (
          <Tag color="volcano">待指派导师</Tag>
        )
      )
    },
    {
      title: '安全准入状态',
      dataIndex: 'safetyStatus',
      key: 'safetyStatus',
      width: 160,
      render: (val) => {
        if (val === 'COMPLETED' || val === 'PASSED') {
          return <Tag color="success"><CheckCircleOutlined /> 已签署达标</Tag>;
        }
        return <Tag color="warning"><SafetyCertificateOutlined /> 学习考核中</Tag>;
      }
    },
    ...(canManage ? [
      {
        title: '操作',
        key: 'action',
        width: 120,
        render: (_: any, record: TaskStudentItem) => (
          <Button
            type="link"
            size="small"
            onClick={() => handleOpenAssignModal([record.studentId])}
          >
            调整导师
          </Button>
        )
      }
    ] : [])
  ];

  if (loading || !task) {
    return (
      <Card style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" tip="正在加载实习任务详情..." />
      </Card>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 头部卡片 */}
      <Card>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tasks')}>
            返回任务列表
          </Button>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                {task.taskName}
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0 0' }}>
                任务编码: <Text copyable>{task.taskCode}</Text> | 所属二级院系: {task.deptName}
              </Paragraph>
            </div>
            <div>{formatStatus(task.status)}</div>
          </div>
        </Space>
      </Card>

      {/* 标签切换 */}
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key)}
          items={[
            {
              key: 'specs',
              label: '📋 基本规约与权重',
              children: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                  <Descriptions title="任务基本规约与进度安排" bordered column={2}>
                    <Descriptions.Item label="所属学年">{task.academicYear}</Descriptions.Item>
                    <Descriptions.Item label="所属学期">第 {task.semester} 学期</Descriptions.Item>
                    <Descriptions.Item label="实习组织模式">
                      <Tag color={task.internshipMode === 'CENTRALIZED' ? 'blue' : 'cyan'}>
                        {task.internshipMode === 'CENTRALIZED' ? '集中实习' : '分散顶岗'}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="实习周期">
                      {task.startDate} ~ {task.endDate}
                    </Descriptions.Item>
                    <Descriptions.Item label="周报提交频次">
                      {task.weeklyFrequency === 'WEEKLY' ? '每周周日截止' : '双周一次'}
                    </Descriptions.Item>
                    <Descriptions.Item label="安全教育及格线">
                      {task.safetyPassingScore} 分 (重测上限: {task.safetyMaxAttempts} 次)
                    </Descriptions.Item>
                    <Descriptions.Item label="圈定学生总数" span={2}>
                      <Tag color="green">{task.studentCount ?? 0} 人已圈定</Tag>
                    </Descriptions.Item>
                  </Descriptions>

                  {/* 五项成绩评价权重 */}
                  <div>
                    <Title level={5} style={{ marginBottom: 12 }}>
                      ⚖️ 综合成绩五项构成权重分布 (严格合计 100.00%)
                    </Title>
                    <Row gutter={[16, 16]}>
                      <Col xs={24} sm={12} md={4} offset={2}>
                        <Card style={{ textAlign: 'center', borderColor: '#91caff' }} hoverable>
                          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#1677ff' }}>
                            {task.weightEnterprise}%
                          </div>
                          <Text type="secondary">企业指导评价</Text>
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={4}>
                        <Card style={{ textAlign: 'center', borderColor: '#b7eb8f' }} hoverable>
                          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#52c41a' }}>
                            {task.weightTeacherProcess}%
                          </div>
                          <Text type="secondary">校内教师过程</Text>
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={4}>
                        <Card style={{ textAlign: 'center', borderColor: '#d3adf7' }} hoverable>
                          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#722ed1' }}>
                            {task.weightWeeklyReport}%
                          </div>
                          <Text type="secondary">周报综合评定</Text>
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={4}>
                        <Card style={{ textAlign: 'center', borderColor: '#ffd591' }} hoverable>
                          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#fa8c16' }}>
                            {task.weightStageMaterial}%
                          </div>
                          <Text type="secondary">阶段材料审核</Text>
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={4}>
                        <Card style={{ textAlign: 'center', borderColor: '#ff85c0' }} hoverable>
                          <div style={{ fontSize: 28, fontWeight: 'bold', color: '#eb2f96' }}>
                            {task.weightSummary}%
                          </div>
                          <Text type="secondary">实习总结报告</Text>
                        </Card>
                      </Col>
                    </Row>
                  </div>

                  {/* 覆盖专业与班级 */}
                  <div>
                    <Title level={5} style={{ marginBottom: 12 }}>
                      🎓 覆盖专业与班级范围
                    </Title>
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <div>
                        <Text strong style={{ marginRight: 8 }}>覆盖专业:</Text>
                        {(task.majorNames && task.majorNames.length > 0) ? (
                          task.majorNames.map((m) => <Tag color="blue" key={m}>{m}</Tag>)
                        ) : (
                          <Text type="secondary">本院系全覆盖</Text>
                        )}
                      </div>
                      <div>
                        <Text strong style={{ marginRight: 8 }}>覆盖班级:</Text>
                        {(task.classNames && task.classNames.length > 0) ? (
                          task.classNames.map((c) => <Tag color="cyan" key={c}>{c}</Tag>)
                        ) : (
                          <Text type="secondary">本院系所有毕业班级</Text>
                        )}
                      </div>
                    </Space>
                  </div>
                </div>
              )
            },
            ...(canManage
              ? [
                  {
                    key: 'students',
                    label: '👥 圈定学生与导师名单',
                    children: (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <Row justify="space-between" align="middle">
                          <Col>
                            <Space>
                              <Input
                                placeholder="输入学号或姓名检索"
                                value={keyword}
                                onChange={(e) => setKeyword(e.target.value)}
                                onPressEnter={loadStudents}
                                style={{ width: 220 }}
                              />
                              <Button type="primary" icon={<SearchOutlined />} onClick={loadStudents}>
                                检索学生
                              </Button>
                            </Space>
                          </Col>
                          <Col>
                            <Button
                              type="primary"
                              icon={<TeamOutlined />}
                              disabled={selectedStudentIds.length === 0}
                              onClick={() => handleOpenAssignModal(selectedStudentIds)}
                            >
                              批量分配导师 ({selectedStudentIds.length}人)
                            </Button>
                          </Col>
                        </Row>

                        <Table
                          columns={studentColumns}
                          dataSource={students}
                          rowKey="studentId"
                          loading={studentsLoading}
                          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 名圈定学生` }}
                          rowSelection={{
                            selectedRowKeys: selectedStudentIds,
                            onChange: (keys) => setSelectedStudentIds(keys as number[])
                          }}
                        />
                      </div>
                    )
                  }
                ]
              : [])
          ]}
        />
      </Card>

      {/* 指派指导教师模态框 */}
      <Modal
        title="指派校内指导教师"
        open={assignModalVisible}
        onCancel={() => setAssignModalVisible(false)}
        onOk={handleConfirmAssign}
        confirmLoading={assignLoading}
        okText="确认分配"
        cancelText="取消"
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 12 }} size="middle">
          <Alert
            message={`已选中 ${assignTargetStudents.length} 名学生，请选择本二级院系具有指导资格的教师`}
            type="info"
            showIcon
          />
          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>选择指导教师:</Text>
            <Select
              style={{ width: '100%' }}
              placeholder="请选择指导教师"
              value={selectedTeacherId}
              onChange={(val) => setSelectedTeacherId(val)}
              options={availableTeachers.map((t) => ({
                label: `${t.realName} (${t.userNumber}) - 当前已指导 ${t.assignedStudentsCount} 人`,
                value: t.id
              }))}
            />
          </div>
        </Space>
      </Modal>
    </div>
  );
};
