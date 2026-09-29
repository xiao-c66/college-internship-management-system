import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Input,
  Select,
  Space,
  Tag,
  Modal,
  Form,
  Upload,
  Alert,
  message,
  Typography,
  Divider,
  Popconfirm,
  Badge,
  Tooltip
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  UserOutlined,
  UploadOutlined,
  DownloadOutlined,
  KeyOutlined,
  SearchOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  CopyOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import {
  getUserPageApi,
  downloadTemplateApi,
  importPreviewApi,
  importExecuteApi,
  resetPasswordApi,
  toggleUserStatusApi,
  changePasswordApi,
  type UserManageVO,
  type UserImportPreviewVO,
  type UserImportRowVO,
  type UserImportCredentialVO
} from '../../api/user';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Text, Paragraph } = Typography;

export const UserManagePage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<UserManageVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 搜索过滤表单
  const [searchKeyword, setSearchKeyword] = useState('');
  const [filterRole, setFilterRole] = useState<string | undefined>(undefined);
  const [filterStatus, setFilterStatus] = useState<number | undefined>(undefined);

  // 批量导入弹窗状态
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importTargetType, setImportTargetType] = useState<'STUDENT' | 'TEACHER'>('STUDENT');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState<UserImportPreviewVO | null>(null);
  const [executingImport, setExecutingImport] = useState(false);

  // 导入凭据结果弹窗
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);
  const [importedCredentials, setImportedCredentials] = useState<UserImportCredentialVO[]>([]);

  // 重置密码展示弹窗
  const [resetResultModalVisible, setResetResultModalVisible] = useState(false);
  const [resetResultInfo, setResetResultInfo] = useState<{ username: string; realName: string; tempPwd: string } | null>(null);

  // 主动修改密码弹窗
  const [changePwdModalVisible, setChangePwdModalVisible] = useState(false);
  const [changePwdForm] = Form.useForm();
  const [submittingChangePwd, setSubmittingChangePwd] = useState(false);

  const fetchUsers = async (page = current, size = pageSize) => {
    setLoading(true);
    try {
      const res = await getUserPageApi({
        current: page,
        size,
        keyword: searchKeyword || undefined,
        userType: filterRole || undefined,
        status: filterStatus !== undefined ? filterStatus : undefined
      });
      if (res && res.data) {
        setData(res.data.records || []);
        setTotal(res.data.total || 0);
      }
    } catch (e: any) {
      message.error(e?.message || '获取用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers(1, pageSize);
  }, []);

  const handleSearch = () => {
    setCurrent(1);
    fetchUsers(1, pageSize);
  };

  const handleResetFilters = () => {
    setSearchKeyword('');
    setFilterRole(undefined);
    setFilterStatus(undefined);
    setCurrent(1);
    fetchUsers(1, pageSize);
  };

  // 模板下载
  const handleDownloadTemplate = async (type: 'STUDENT' | 'TEACHER') => {
    try {
      const res = await downloadTemplateApi(type);
      const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `${type === 'STUDENT' ? '学生' : '教师'}账号批量导入模板.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      message.success('模板下载成功');
    } catch (e: any) {
      message.error('下载模板失败');
    }
  };

  // 打开导入弹窗
  const openImportModal = (type: 'STUDENT' | 'TEACHER') => {
    setImportTargetType(type);
    setSelectedFile(null);
    setPreviewData(null);
    setImportModalVisible(true);
  };

  // 上传文件并解析预览
  const handleUploadAndPreview = async (file: File) => {
    setSelectedFile(file);
    setPreviewLoading(true);
    try {
      const res = await importPreviewApi(file, importTargetType);
      if (res && res.data) {
        setPreviewData(res.data);
        message.success(`解析完成：共 ${res.data.totalCount} 行，其中有效 ${res.data.validCount} 行`);
      }
    } catch (e: any) {
      message.error(e?.message || '文件解析与校验失败');
      setPreviewData(null);
    } finally {
      setPreviewLoading(false);
    }
    return false; // 阻止 antd 自动上传
  };

  // 确认执行导入
  const handleExecuteImport = async () => {
    if (!previewData || previewData.validCount === 0) {
      message.warning('当前无合规待导入数据行');
      return;
    }
    const validRows = previewData.previewRows.filter(r => r.isValid);
    setExecutingImport(true);
    try {
      const res = await importExecuteApi({
        userType: importTargetType,
        previewToken: previewData.previewToken,
        rows: validRows.map(r => ({
          rowNum: r.rowNum,
          userNumber: r.userNumber,
          username: r.username,
          realName: r.realName,
          phone: r.phone,
          email: r.email,
          deptName: r.deptName,
          majorName: r.majorName,
          className: r.className
        }))
      });
      if (res && res.data) {
        message.success(`批量导入成功！新增 ${res.data.successCount} 个账号`);
        setImportModalVisible(false);
        setImportedCredentials(res.data.credentials || []);
        setCredentialModalVisible(true);
        fetchUsers(1, pageSize);
      }
    } catch (e: any) {
      message.error(e?.message || '批量导入执行失败');
    } finally {
      setExecutingImport(false);
    }
  };

  // 重置指定用户密码
  const handleResetPassword = async (record: UserManageVO) => {
    try {
      const res = await resetPasswordApi(record.id);
      if (res && res.data) {
        setResetResultInfo({
          username: res.data.username,
          realName: res.data.realName,
          tempPwd: res.data.temporaryPassword
        });
        setResetResultModalVisible(true);
        fetchUsers();
      }
    } catch (e: any) {
      message.error(e?.message || '重置密码失败');
    }
  };

  // 切换用户启用/停用
  const handleToggleStatus = async (userId: number) => {
    try {
      await toggleUserStatusApi(userId);
      message.success('账号状态已成功切换');
      fetchUsers();
    } catch (e: any) {
      message.error(e?.message || '切换状态失败');
    }
  };

  // 提交修改个人密码
  const handleChangePasswordSubmit = async () => {
    try {
      const values = await changePwdForm.validateFields();
      setSubmittingChangePwd(true);
      await changePasswordApi(values);
      message.success('密码修改成功！历史设备会话已失效，请使用新密码重新登录');
      setChangePwdModalVisible(false);
      changePwdForm.resetFields();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '密码修改失败');
    } finally {
      setSubmittingChangePwd(false);
    }
  };

  // 复制文本辅助函数
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success('密码已复制到剪贴板');
  };

  const columns: ColumnsType<UserManageVO> = [
    {
      title: '学号/工号',
      dataIndex: 'userNumber',
      key: 'userNumber',
      width: 140,
      render: (text) => <Text strong>{text || '-'}</Text>
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 140
    },
    {
      title: '真实姓名',
      dataIndex: 'realName',
      key: 'realName',
      width: 120
    },
    {
      title: '用户身份',
      dataIndex: 'userType',
      key: 'userType',
      width: 130,
      render: (role) => {
        const config: Record<string, { color: string; label: string }> = {
          STUDENT: { color: 'blue', label: '学生' },
          TEACHER: { color: 'green', label: '指导教师' },
          DEPT_ADMIN: { color: 'orange', label: '院系负责人' },
          SYS_ADMIN: { color: 'magenta', label: '系统管理员' }
        };
        const item = config[role] || { color: 'default', label: role };
        return <Tag color={item.color}>{item.label}</Tag>;
      }
    },
    {
      title: '所属组织架构',
      key: 'organization',
      render: (_, r) => {
        const parts = [r.deptName, r.majorName, r.className].filter(Boolean);
        return parts.length > 0 ? parts.join(' / ') : <Text type="secondary">全校/未分配</Text>;
      }
    },
    {
      title: '联系方式',
      key: 'contact',
      width: 200,
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          {r.phone && <Text style={{ fontSize: 12 }}>📱 {r.phone}</Text>}
          {r.email && <Text style={{ fontSize: 12 }}>✉️ {r.email}</Text>}
          {!r.phone && !r.email && <Text type="secondary" style={{ fontSize: 12 }}>未绑定</Text>}
        </Space>
      )
    },
    {
      title: '账号状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => {
        if (status === 1) return <Badge status="success" text="正常活跃" />;
        if (status === 2) return <Badge status="warning" text="待首次改密" />;
        return <Badge status="error" text="停用锁定" />;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (t) => t ? t.replace('T', ' ') : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, r) => (
        <Space direction="horizontal" size={6}>
          <Popconfirm
            title="确认重置该账号密码？"
            description="重置后系统将签发高熵独立临时密码，用户下次登录强制修改，历史所有在线会话将即时失效。"
            onConfirm={() => handleResetPassword(r)}
            okText="确认重置"
            cancelText="取消"
          >
            <Button size="small" type="link" icon={<KeyOutlined />}>
              重置密码
            </Button>
          </Popconfirm>
          <Popconfirm
            title={`确认${r.status === 1 ? '停用' : '启用'}此账号？`}
            description={r.status === 1 ? '停用后该用户将无法登录系统，且所有在线会话即刻踢下线。' : '启用后账号恢复正常登录。'}
            onConfirm={() => handleToggleStatus(r.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button size="small" type="link" danger={r.status === 1}>
              {r.status === 1 ? '停用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  // 导入预览列定义
  const previewColumns: ColumnsType<UserImportRowVO> = [
    { title: '行号', dataIndex: 'rowNum', key: 'rowNum', width: 70 },
    { title: '工号/学号', dataIndex: 'userNumber', key: 'userNumber', width: 120 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '姓名', dataIndex: 'realName', key: 'realName', width: 100 },
    { title: '手机号', dataIndex: 'phone', key: 'phone', width: 120 },
    { title: '所属院系', dataIndex: 'deptName', key: 'deptName', width: 140 },
    ...(importTargetType === 'STUDENT'
      ? [
          { title: '专业', dataIndex: 'majorName', key: 'majorName', width: 120 },
          { title: '班级', dataIndex: 'className', key: 'className', width: 130 }
        ]
      : []),
    {
      title: '校验结果',
      dataIndex: 'isValid',
      key: 'isValid',
      width: 110,
      render: (valid) => (
        valid ? <Tag icon={<CheckCircleOutlined />} color="success">校验通过</Tag>
              : <Tag icon={<CloseCircleOutlined />} color="error">校验不通过</Tag>
      )
    },
    {
      title: '错误原因说明',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      render: (msg) => msg ? <Text type="danger">{msg}</Text> : <Text type="secondary">-</Text>
    }
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              <UserOutlined style={{ marginRight: 8, color: '#1677ff' }} />
              系统账号与批量导入管理
            </Title>
            <Text type="secondary">
              支持 Excel/CSV 批量导入学生与教师、独立高熵临时密码自动签发、首次登录强制改密与全生命周期安全审计
            </Text>
          </div>
          <Space wrap>
            <Button icon={<UploadOutlined />} type="primary" onClick={() => openImportModal('STUDENT')}>
              批量导入学生
            </Button>
            <Button icon={<UploadOutlined />} onClick={() => openImportModal('TEACHER')}>
              批量导入教师
            </Button>
            <Button icon={<KeyOutlined />} onClick={() => setChangePwdModalVisible(true)}>
              修改我的密码
            </Button>
          </Space>
        </div>

        <Divider style={{ margin: '16px 0' }} />

        {/* 筛选过滤行 */}
        <Space wrap size="middle">
          <Input
            placeholder="搜索用户名 / 真实姓名 / 学工号"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onPressEnter={handleSearch}
            style={{ width: 260 }}
            prefix={<SearchOutlined />}
            allowClear
          />
          <Select
            placeholder="账号身份"
            value={filterRole}
            onChange={(val) => setFilterRole(val)}
            style={{ width: 150 }}
            allowClear
            options={[
              { label: '全部身份', value: '' },
              { label: '学生 (STUDENT)', value: 'STUDENT' },
              { label: '指导教师 (TEACHER)', value: 'TEACHER' },
              { label: '院系负责人 (DEPT_ADMIN)', value: 'DEPT_ADMIN' },
              { label: '系统管理员 (SYS_ADMIN)', value: 'SYS_ADMIN' }
            ]}
          />
          <Select
            placeholder="账号状态"
            value={filterStatus}
            onChange={(val) => setFilterStatus(val)}
            style={{ width: 140 }}
            allowClear
            options={[
              { label: '全部状态', value: undefined },
              { label: '正常活跃 (1)', value: 1 },
              { label: '待首次改密 (2)', value: 2 },
              { label: '停用锁定 (0)', value: 0 }
            ]}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleResetFilters}>
            重置
          </Button>
        </Space>
      </Card>

      {/* 用户列表主表格 */}
      <Card>
        <Table<UserManageVO>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 名用户账号`,
            onChange: (p, s) => {
              setCurrent(p);
              setPageSize(s);
              fetchUsers(p, s);
            }
          }}
        />
      </Card>

      {/* 批量导入模态框 */}
      <Modal
        title={`批量导入${importTargetType === 'STUDENT' ? '学生' : '教师'}账号`}
        open={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        width={960}
        footer={[
          <Button key="close" onClick={() => setImportModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={executingImport}
            disabled={!previewData || previewData.validCount === 0}
            onClick={handleExecuteImport}
          >
            确认执行导入 ({previewData ? previewData.validCount : 0} 条)
          </Button>
        ]}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="导入安全与业务规则说明"
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              <li>请先下载标准导入模板，严格按照模板格式与列定义填写。</li>
              <li>系统将自动执行逐行格式、学工号/用户名唯一性及院系专业班级层级归属校验。</li>
              <li><strong>密码安全规范</strong>：严禁共用默认密码。系统将为每个合规账号生成唯一高熵临时密码，首次登录强制修改。</li>
            </ul>
          }
        />

        <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => handleDownloadTemplate(importTargetType)}
          >
            下载 {importTargetType === 'STUDENT' ? '学生' : '教师'} 导入标准模板
          </Button>

          <Upload
            beforeUpload={handleUploadAndPreview}
            showUploadList={false}
            accept=".xlsx,.xls,.csv"
          >
            <Button icon={<UploadOutlined />} type="dashed" loading={previewLoading}>
              选择并上传 Excel / CSV 文件
            </Button>
          </Upload>
          {selectedFile && <Text type="secondary">已选择: {selectedFile.name}</Text>}
        </div>

        {previewData && (
          <div>
            <div style={{ display: 'flex', gap: 16, marginBottom: 12 }}>
              <Tag color="processing">总解析数据: {previewData.totalCount} 行</Tag>
              <Tag color="success">校验合规通过: {previewData.validCount} 行</Tag>
              <Tag color="error">校验不通过: {previewData.invalidCount} 行</Tag>
            </div>

            <Table<UserImportRowVO>
              rowKey="rowNum"
              columns={previewColumns}
              dataSource={previewData.previewRows}
              size="small"
              scroll={{ x: 900, y: 350 }}
              pagination={false}
            />
          </div>
        )}
      </Modal>

      {/* 导入后一次性临时凭据展示弹窗 */}
      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#52c41a' }} />
            <span>批量导入成功 — 临时凭据单次签发清单</span>
          </Space>
        }
        open={credentialModalVisible}
        onCancel={() => setCredentialModalVisible(false)}
        width={750}
        footer={[
          <Button key="close" type="primary" onClick={() => setCredentialModalVisible(false)}>
            我知道了并已妥善保存
          </Button>
        ]}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="高密凭证安全提示"
          description="系统数据库已严格采用安全哈希加盐存储，明文临时密码仅在本次导入结果中单次呈现，严禁写入日志或公开报告。请管理员通过学校受控信道将账号与临时密码发放给相关师生。"
        />
        <Table<UserImportCredentialVO>
          rowKey="username"
          size="small"
          dataSource={importedCredentials}
          pagination={{ pageSize: 5 }}
          columns={[
            { title: '学号/工号', dataIndex: 'userNumber', key: 'userNumber', width: 140 },
            { title: '登录用户名', dataIndex: 'username', key: 'username', width: 140 },
            { title: '真实姓名', dataIndex: 'realName', key: 'realName', width: 120 },
            {
              title: '独立临时密码 (首次登录强制修改)',
              dataIndex: 'temporaryPassword',
              key: 'temporaryPassword',
              render: (pwd) => (
                <Space>
                  <Text code copyable>{pwd}</Text>
                </Space>
              )
            }
          ]}
        />
      </Modal>

      {/* 单个重置密码成功展示弹窗 */}
      <Modal
        title="密码重置成功"
        open={resetResultModalVisible}
        onCancel={() => setResetResultModalVisible(false)}
        footer={[
          <Button key="confirm" type="primary" onClick={() => setResetResultModalVisible(false)}>
            完成
          </Button>
        ]}
      >
        {resetResultInfo && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Alert
              type="success"
              showIcon
              message={`已为 [${resetResultInfo.realName}] (${resetResultInfo.username}) 重置为新独立临时密码`}
              description="该账号已置为待首次改密状态，且历史签发的所有在线令牌已即时踢下线。"
            />
            <div style={{ textAlign: 'center', padding: '16px 0', background: '#f5f5f5', borderRadius: 6 }}>
              <Text type="secondary">新独立临时密码：</Text>
              <Title level={3} style={{ margin: '8px 0', letterSpacing: 2, color: '#1677ff' }}>
                {resetResultInfo.tempPwd}
              </Title>
              <Button icon={<CopyOutlined />} onClick={() => copyToClipboard(resetResultInfo.tempPwd)}>
                复制临时密码
              </Button>
            </div>
          </Space>
        )}
      </Modal>

      {/* 主动修改密码弹窗 */}
      <Modal
        title="修改个人密码"
        open={changePwdModalVisible}
        onCancel={() => setChangePwdModalVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setChangePwdModalVisible(false)}>
            取消
          </Button>,
          <Button key="submit" type="primary" loading={submittingChangePwd} onClick={handleChangePasswordSubmit}>
            确认修改
          </Button>
        ]}
      >
        <Form form={changePwdForm} layout="vertical">
          <Form.Item
            label="原密码 / 初始临时密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入原密码或临时密码' }]}
          >
            <Input.Password placeholder="请输入原密码或管理员下发的临时密码" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '密码长度至少8位' },
              {
                pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/,
                message: '密码必须包含大写字母、小写字母、数字和特殊字符'
              }
            ]}
          >
            <Input.Password placeholder="至少8位，包含大写、小写、数字和特殊字符" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'));
                }
              })
            ]}
          >
            <Input.Password placeholder="请再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
export default UserManagePage;
