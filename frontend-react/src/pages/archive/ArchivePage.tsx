import React, { useState, useEffect } from 'react';
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
  Drawer,
  Descriptions,
  Alert,
  Typography,
  Tooltip,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  SearchOutlined,
  DownloadOutlined,
  LockOutlined,
  UnlockOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileDoneOutlined,
  KeyOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';
import { getTaskList, type TaskItem } from '../../api/task';
import {
  getArchiveList,
  precheckArchive,
  freezeArchive,
  unlockArchive,
  exportArchiveBundle,
  type ArchiveVO,
  type ArchivePrecheckVO,
  type ArchiveCheckItemVO
} from '../../api/archive';

const { Title, Text, Paragraph } = Typography;

export const ArchivePage: React.FC = () => {
  const { userType, hasRole } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isAdmin = userType === 'SYS_ADMIN';

  // 列表与状态
  const [loading, setLoading] = useState(false);
  const [archiveList, setArchiveList] = useState<ArchiveVO[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [exportingId, setExportingId] = useState<number | null>(null);

  // 筛选表单
  const [filterForm] = Form.useForm();

  // 9项前置条件诊断核验弹窗
  const [precheckModalVisible, setPrecheckModalVisible] = useState(false);
  const [precheckForm] = Form.useForm();
  const [diagnosing, setDiagnosing] = useState(false);
  const [freezing, setFreezing] = useState(false);
  const [precheckResult, setPrecheckResult] = useState<ArchivePrecheckVO | null>(null);

  // 详情抽屉
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [currentArchive, setCurrentArchive] = useState<ArchiveVO | null>(null);

  // 特批解锁弹窗
  const [unlockModalVisible, setUnlockModalVisible] = useState(false);
  const [unlockForm] = Form.useForm();
  const [unlockSubmitting, setUnlockSubmitting] = useState(false);
  const [selectedArchiveId, setSelectedArchiveId] = useState<number | null>(null);

  // 初始化加载任务列表与档案卷宗列表
  useEffect(() => {
    loadTasks();
    loadData();
  }, []);

  const loadTasks = async () => {
    try {
      const res: any = await getTaskList();
      const taskArr = Array.isArray(res?.data) ? res.data : (res?.data?.records || []);
      setTasks(taskArr);
      if (taskArr.length > 0) {
        precheckForm.setFieldValue('taskId', taskArr[0].id);
      }
    } catch (e) {
      console.error('加载任务失败', e);
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const values = filterForm.getFieldsValue();
      const res: any = await getArchiveList({
        taskId: values.taskId || undefined,
        status: values.status || undefined
      });
      if (res && (res.code === 200 || Array.isArray(res.data))) {
        setArchiveList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '加载归档卷宗列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleResetFilters = () => {
    filterForm.resetFields();
    loadData();
  };

  // 诊断核验
  const handleOpenPrecheck = () => {
    setPrecheckResult(null);
    if (tasks.length > 0 && !precheckForm.getFieldValue('taskId')) {
      precheckForm.setFieldValue('taskId', tasks[0].id);
    }
    if (!precheckForm.getFieldValue('studentId')) {
      precheckForm.setFieldValue('studentId', 1);
    }
    setPrecheckModalVisible(true);
  };

  const runPrecheck = async () => {
    try {
      const values = await precheckForm.validateFields();
      setDiagnosing(true);
      const res: any = await precheckArchive(values.taskId, values.studentId);
      if (res && res.code === 200) {
        setPrecheckResult(res.data);
      } else {
        message.error(res?.message || '诊断核验返回异常');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '前置诊断核验失败');
    } finally {
      setDiagnosing(false);
    }
  };

  const executeFreezeArchive = async () => {
    const values = precheckForm.getFieldsValue();
    if (!values.taskId || !values.studentId) return;
    setFreezing(true);
    try {
      const res: any = await freezeArchive(values.taskId, values.studentId);
      if (res && (res.code === 200 || typeof res.data === 'number')) {
        message.success('实习电子档案已生成并执行全局只读写保护锁定！');
        setPrecheckModalVisible(false);
        loadData();
      } else {
        message.error(res?.message || '终审归档锁定失败');
      }
    } catch (e: any) {
      message.error(e?.message || '终审归档锁定失败');
    } finally {
      setFreezing(false);
    }
  };

  // 查看详情
  const handleOpenDetail = (record: ArchiveVO) => {
    setCurrentArchive(record);
    setDetailDrawerVisible(true);
  };

  // 导出 ZIP
  const handleExportZip = async (record: ArchiveVO) => {
    setExportingId(record.id);
    try {
      const res: any = await exportArchiveBundle(record.id);
      const blobData = res?.data ?? res;
      const blob = blobData instanceof Blob ? blobData : new Blob([blobData], { type: 'application/zip' });
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = `${record.archiveNo || 'archive'}.zip`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(link.href);
      message.success('电子档案压缩包导出成功');
    } catch (e: any) {
      message.error(e?.message || '导出归档包失败');
    } finally {
      setExportingId(null);
    }
  };

  // 打开特批解锁
  const handleOpenUnlock = (record: ArchiveVO) => {
    setSelectedArchiveId(record.id);
    unlockForm.resetFields();
    setUnlockModalVisible(true);
  };

  const submitUnlock = async () => {
    if (!selectedArchiveId) return;
    try {
      const values = await unlockForm.validateFields();
      setUnlockSubmitting(true);
      const res: any = await unlockArchive(selectedArchiveId, {
        specialDocNo: values.specialDocNo,
        specialUnlockReason: values.specialUnlockReason
      });
      if (res && (res.code === 200 || !res.code)) {
        message.success('特批解锁成功，编辑窗口已开启（48小时后自动复锁）');
        setUnlockModalVisible(false);
        loadData();
      } else {
        message.error(res?.message || '特批解锁操作失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '特批解锁操作失败');
    } finally {
      setUnlockSubmitting(false);
    }
  };

  const formatJson = (jsonStr: string) => {
    if (!jsonStr) return '无矩阵数据';
    try {
      const parsed = JSON.parse(jsonStr);
      return JSON.stringify(parsed, null, 2);
    } catch (e) {
      return jsonStr;
    }
  };

  // 表格列定义
  const columns: ColumnsType<ArchiveVO> = [
    {
      title: '电子档案卷宗编号',
      dataIndex: 'archiveNo',
      key: 'archiveNo',
      width: 180,
      render: (val: string) => (
        <span style={{ fontFamily: 'Consolas, Monaco, monospace', fontWeight: 600 }}>{val}</span>
      )
    },
    {
      title: '归档学生',
      key: 'student',
      width: 140,
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 600 }}>{record.studentName || '-'}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.studentNo}</Text>
        </div>
      )
    },
    {
      title: '所属院系',
      dataIndex: 'deptName',
      key: 'deptName',
      width: 150,
      ellipsis: true,
      render: (text: string) => text || '-'
    },
    {
      title: '学年',
      dataIndex: 'academicYear',
      key: 'academicYear',
      width: 110,
      align: 'center'
    },
    {
      title: '卷宗版本',
      dataIndex: 'version',
      key: 'version',
      width: 90,
      align: 'center',
      render: (version: number) => <Tag color="default">v{version}.0</Tag>
    },
    {
      title: '锁定状态',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      align: 'center',
      render: (status: string) => {
        if (status === 'ARCHIVED') {
          return (
            <Tag color="success" icon={<LockOutlined />}>
              已归档锁定
            </Tag>
          );
        }
        return (
          <Tag color="error" icon={<UnlockOutlined />}>
            特批解锁中
          </Tag>
        );
      }
    },
    {
      title: '归档人',
      dataIndex: 'archivedUserName',
      key: 'archivedUserName',
      width: 120,
      align: 'center',
      render: (text: string) => text || '系统管理员'
    },
    {
      title: '归档封存时间',
      dataIndex: 'archivedTime',
      key: 'archivedTime',
      width: 170
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<FileDoneOutlined />}
            onClick={() => handleOpenDetail(record)}
          >
            卷宗核验
          </Button>
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            loading={exportingId === record.id}
            onClick={() => handleExportZip(record)}
          >
            导出ZIP
          </Button>
          {isAdmin && record.status === 'ARCHIVED' && (
            <Button
              type="link"
              danger
              size="small"
              icon={<KeyOutlined />}
              onClick={() => handleOpenUnlock(record)}
            >
              特批解锁
            </Button>
          )}
        </Space>
      )
    }
  ];

  // 诊断核验项列定义
  const checkItemColumns: ColumnsType<ArchiveCheckItemVO> = [
    {
      title: '指标项',
      dataIndex: 'code',
      key: 'code',
      width: 130
    },
    {
      title: '前置检查要素',
      dataIndex: 'name',
      key: 'name',
      minWidth: 150
    },
    {
      title: '核验结果',
      dataIndex: 'passed',
      key: 'passed',
      width: 100,
      align: 'center',
      render: (passed: boolean) => (
        <Tag color={passed ? 'success' : 'error'} icon={passed ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
          {passed ? '达标' : '未达标'}
        </Tag>
      )
    },
    {
      title: '核验明细',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true
    },
    {
      title: '拦截原因',
      dataIndex: 'blockReason',
      key: 'blockReason',
      ellipsis: true,
      render: (reason: string) => (
        reason ? <span style={{ color: '#ff4d4f' }}>{reason}</span> : <span style={{ color: '#52c41a' }}>-</span>
      )
    }
  ];

  return (
    <div style={{ padding: '24px' }}>
      {/* 页面头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>实习电子档案归档与锁定</Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            9项前置条件智能诊断雷达、全局只读写保护、七合一电子档案PDF/ZIP导出与超管限时特批解锁
          </Text>
        </div>
        <Space>
          {!isStudent && (
            <Button
              type="primary"
              icon={<SafetyCertificateOutlined />}
              onClick={handleOpenPrecheck}
            >
              档案归档前置核验诊断
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            刷新
          </Button>
        </Space>
      </div>

      {/* 筛选卡片 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={filterForm} layout="inline" onFinish={loadData}>
          <Form.Item name="taskId" label="实习任务">
            <Select
              placeholder="全部任务"
              allowClear
              style={{ width: 220 }}
              onChange={loadData}
              options={tasks.map(t => ({ label: t.taskName, value: t.id }))}
            />
          </Form.Item>
          <Form.Item name="status" label="档案状态">
            <Select
              placeholder="全部状态"
              allowClear
              style={{ width: 180 }}
              onChange={loadData}
              options={[
                { label: '已封存锁定 (写保护)', value: 'ARCHIVED' },
                { label: '特批解锁中 (限时可改)', value: 'SPECIAL_UNLOCKED' }
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={handleResetFilters}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {/* 档案台账表格 */}
      <Card size="small">
        <Table<ArchiveVO>
          rowKey="id"
          columns={columns}
          dataSource={archiveList}
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条卷宗记录` }}
          scroll={{ x: 1100 }}
          bordered
        />
      </Card>

      {/* 9项前置条件诊断雷达弹窗 */}
      <Modal
        title="实习档案归档前置9项条件诊断"
        open={precheckModalVisible}
        onCancel={() => setPrecheckModalVisible(false)}
        width={760}
        footer={[
          <Button key="close" onClick={() => setPrecheckModalVisible(false)}>
            关闭
          </Button>,
          <Button
            key="freeze"
            type="primary"
            disabled={!precheckResult || !precheckResult.passed}
            loading={freezing}
            onClick={executeFreezeArchive}
          >
            执行终审归档并锁定
          </Button>
        ]}
      >
        <Form form={precheckForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item
            name="taskId"
            label="实习任务"
            rules={[{ required: true, message: '请选择实习任务' }]}
          >
            <Select
              placeholder="选择任务"
              style={{ width: 240 }}
              options={tasks.map(t => ({ label: t.taskName, value: t.id }))}
            />
          </Form.Item>
          <Form.Item
            name="studentId"
            label="学生用户ID"
            rules={[{ required: true, message: '请输入学生ID' }]}
          >
            <InputNumber min={1} placeholder="学生ID" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<SearchOutlined />}
              loading={diagnosing}
              onClick={runPrecheck}
            >
              开始诊断核验
            </Button>
          </Form.Item>
        </Form>

        {precheckResult && (
          <div style={{ marginTop: 12 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '12px 16px',
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                marginBottom: 14
              }}
            >
              <div>
                候选归档学生：<strong>{precheckResult.studentName}</strong> ({precheckResult.studentNo})
              </div>
              <div>
                <Tag
                  color={precheckResult.passed ? 'success' : 'error'}
                  style={{ fontSize: 13, padding: '4px 10px' }}
                >
                  {precheckResult.passed
                    ? '9项核验全部通过 (准予归档)'
                    : `未通过 (${precheckResult.passedCount}/${precheckResult.totalCount})`}
                </Tag>
              </div>
            </div>

            <Table<ArchiveCheckItemVO>
              rowKey="code"
              columns={checkItemColumns}
              dataSource={precheckResult.checkItems || []}
              pagination={false}
              size="small"
              bordered
            />
          </div>
        )}
      </Modal>

      {/* 档案详情与检查矩阵抽屉 */}
      <Drawer
        title="实习档案电子卷宗核验"
        placement="right"
        width={640}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
        extra={
          currentArchive && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleExportZip(currentArchive)}
            >
              下载档案包 (.zip)
            </Button>
          )
        }
      >
        {currentArchive && (
          <div>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="卷宗编号" span={2}>
                <span style={{ fontFamily: 'Consolas, Monaco, monospace', fontWeight: 600 }}>
                  {currentArchive.archiveNo}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="归档学生">
                {currentArchive.studentName} ({currentArchive.studentNo})
              </Descriptions.Item>
              <Descriptions.Item label="所属院系">
                {currentArchive.deptName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="所属学年">
                {currentArchive.academicYear}
              </Descriptions.Item>
              <Descriptions.Item label="版本与状态">
                <span>v{currentArchive.version}.0 / </span>
                <Tag color={currentArchive.status === 'ARCHIVED' ? 'success' : 'error'}>
                  {currentArchive.status === 'ARCHIVED' ? '已锁定' : '特批解锁'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="归档封存人">
                {currentArchive.archivedUserName || '系统管理员'}
              </Descriptions.Item>
              <Descriptions.Item label="归档时间">
                {currentArchive.archivedTime}
              </Descriptions.Item>
            </Descriptions>

            {currentArchive.status === 'SPECIAL_UNLOCKED' && (
              <div style={{ marginTop: 16 }}>
                <Alert
                  type="warning"
                  showIcon
                  message="本卷宗当前处于特批解锁编辑窗口期"
                  description={
                    <div style={{ marginTop: 6, fontSize: 13, lineHeight: '20px' }}>
                      <div>解锁经办人：{currentArchive.unlockedByName || '-'}</div>
                      <div>红头批文号：<strong>{currentArchive.specialDocNo || '-'}</strong></div>
                      <div>特批原因：{currentArchive.specialUnlockReason || '-'}</div>
                      <div>解锁到期时间：{currentArchive.unlockExpireTime || '-'} (到期自动重新封存)</div>
                    </div>
                  }
                />
              </div>
            )}

            <Title level={5} style={{ marginTop: 20, marginBottom: 8 }}>
              9项准入核验矩阵快照
            </Title>
            <div
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                padding: 12,
                maxHeight: 280,
                overflow: 'auto'
              }}
            >
              <pre
                style={{
                  margin: 0,
                  fontFamily: 'Consolas, Monaco, monospace',
                  fontSize: 12,
                  color: '#334155',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all'
                }}
              >
                {formatJson(currentArchive.checkMatrixJson)}
              </pre>
            </div>
          </div>
        )}
      </Drawer>

      {/* 特批解锁弹窗 */}
      <Modal
        title="超管特批解锁实习电子档案"
        open={unlockModalVisible}
        onCancel={() => setUnlockModalVisible(false)}
        width={540}
        footer={[
          <Button key="cancel" onClick={() => setUnlockModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            danger
            loading={unlockSubmitting}
            onClick={submitUnlock}
          >
            确认特批解锁
          </Button>
        ]}
      >
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="重要警告"
          description="特批解锁属于重大异常修正程序。档案解锁后，该生全部过程材料与成绩将短暂开放编辑，并在48小时后自动重新锁定。所有操作将记入全量审计日志！"
        />
        <Form form={unlockForm} layout="vertical">
          <Form.Item
            name="specialDocNo"
            label="红头批文号"
            rules={[{ required: true, message: '特批解锁必须具备红头批文号' }]}
          >
            <Input placeholder="如: 教务处[2026]33号关于XX学生实习成绩更正特批" />
          </Form.Item>
          <Form.Item
            name="specialUnlockReason"
            label="特批解锁事由"
            rules={[{ required: true, message: '请录入特批解锁事由' }]}
          >
            <Input.TextArea
              rows={4}
              placeholder="请详细录入主管校领导/教务处批示特批原因"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
