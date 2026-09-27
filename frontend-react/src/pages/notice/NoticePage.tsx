import React, { useState, useEffect, useMemo } from 'react';
import {
  Card,
  Table,
  Button,
  Form,
  Select,
  Input,
  Radio,
  Tag,
  Space,
  Modal,
  Drawer,
  Descriptions,
  Divider,
  Typography,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  EyeOutlined,
  EditOutlined,
  RollbackOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import DOMPurify from 'dompurify';
import { useAuthStore } from '../../store/useAuthStore';
import {
  getNotices,
  getNoticeDetail,
  createNotice,
  updateNotice,
  type SysNoticeVO
} from '../../api/notice';

const { Title, Text } = Typography;

export const NoticePage: React.FC = () => {
  const { user, userType } = useAuthStore();
  const isAdmin = userType === 'SYS_ADMIN';
  const isDeptAdmin = userType === 'DEPT_ADMIN';

  // 列表与加载
  const [loading, setLoading] = useState(false);
  const [noticeList, setNoticeList] = useState<SysNoticeVO[]>([]);
  const [filterForm] = Form.useForm();

  // 发布弹窗
  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [publishForm] = Form.useForm();
  const [publishContent, setPublishContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 编辑弹窗
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editForm] = Form.useForm();
  const [editContent, setEditContent] = useState('');
  const [editingNoticeId, setEditingNoticeId] = useState<number | null>(null);

  // 详情抽屉
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [currentNotice, setCurrentNotice] = useState<SysNoticeVO | null>(null);

  // 挂载时加载数据
  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = filterForm.getFieldsValue();
      const res: any = await getNotices({
        status: values.status !== undefined ? values.status : undefined,
        noticeType: values.noticeType || undefined
      });
      if (res && (res.code === 200 || Array.isArray(res.data))) {
        setNoticeList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取通知公告列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleResetFilters = () => {
    filterForm.resetFields();
    loadData();
  };

  const canModify = (row: SysNoticeVO) => {
    if (isAdmin) return true;
    if (isDeptAdmin) {
      if (row.publisherId === user?.userId) return true;
      if (row.targetScope === 'DEPT' && row.targetDeptId === user?.deptId) return true;
    }
    return false;
  };

  // 生成业务防重键
  const generateDedupKey = () => {
    const prefix = isDeptAdmin ? `MANUAL_DEPT${user?.deptId || '0'}` : 'MANUAL_ALL';
    return `${prefix}_NOTICE_${Date.now()}`;
  };

  // 打开新建发布弹窗
  const openPublishDialog = () => {
    const defaultDedup = generateDedupKey();
    publishForm.resetFields();
    publishForm.setFieldsValue({
      dedupKey: defaultDedup,
      noticeTitle: '',
      noticeType: 'NOTICE',
      targetScope: isDeptAdmin ? 'DEPT' : 'ALL',
      noticeContent: ''
    });
    setPublishContent('');
    setPublishModalVisible(true);
  };

  // 提交发布 (只在前端交互测试，不主动触发真实写操作)
  const handlePublish = async () => {
    try {
      const values = await publishForm.validateFields();
      setSubmitting(true);
      const res: any = await createNotice({
        dedupKey: values.dedupKey.trim(),
        noticeTitle: values.noticeTitle.trim(),
        noticeType: values.noticeType,
        targetScope: isDeptAdmin ? 'DEPT' : values.targetScope,
        targetDeptId: isDeptAdmin ? (user?.deptId || 1) : null,
        noticeContent: values.noticeContent
      });
      if (res && res.code === 200) {
        message.success('通知公告发布成功');
        setPublishModalVisible(false);
        loadData();
      } else {
        message.error(res?.message || '发布通知失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '发布通知失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 打开编辑弹窗
  const openEditDialog = (row: SysNoticeVO) => {
    setEditingNoticeId(row.id);
    editForm.setFieldsValue({
      noticeTitle: row.noticeTitle,
      noticeType: row.noticeType,
      noticeContent: row.noticeContent,
      status: row.status
    });
    setEditContent(row.noticeContent || '');
    setEditModalVisible(true);
  };

  // 提交编辑 (只在前端交互测试)
  const handleEditSubmit = async () => {
    if (!editingNoticeId) return;
    try {
      const values = await editForm.validateFields();
      setSubmitting(true);
      const res: any = await updateNotice(editingNoticeId, {
        noticeTitle: values.noticeTitle.trim(),
        noticeType: values.noticeType,
        noticeContent: values.noticeContent,
        status: values.status
      });
      if (res && res.code === 200) {
        message.success('通知公告更新成功');
        setEditModalVisible(false);
        loadData();
      } else {
        message.error(res?.message || '更新通知失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '更新通知失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 撤回 / 重新发布
  const toggleNoticeStatus = (row: SysNoticeVO) => {
    const newStatus = row.status === 1 ? 0 : 1;
    const actionText = newStatus === 1 ? '重新发布' : '撤回';
    Modal.confirm({
      title: '操作确认',
      content: `确认要${actionText}公告【${row.noticeTitle}】吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res: any = await updateNotice(row.id, { status: newStatus });
          if (res && res.code === 200) {
            message.success(`通知已成功${actionText}`);
            loadData();
          } else {
            message.error(res?.message || `${actionText}操作失败`);
          }
        } catch (e: any) {
          message.error(e?.message || `${actionText}操作失败`);
        }
      }
    });
  };

  // 查看详情
  const viewDetail = async (row: SysNoticeVO) => {
    try {
      const res: any = await getNoticeDetail(row.id);
      if (res && res.code === 200) {
        setCurrentNotice(res.data);
        setDetailDrawerVisible(true);
        // 同步前端已读标记
        setNoticeList(prev =>
          prev.map(item => (item.id === row.id ? { ...item, isRead: true, readTime: res.data.readTime } : item))
        );
      } else {
        message.error(res?.message || '获取通知详情失败');
      }
    } catch (e: any) {
      message.error(e?.message || '获取通知详情失败');
    }
  };

  // 实时安全预览计算
  const sanitizedPublishPreview = useMemo(() => {
    return DOMPurify.sanitize(publishContent || '<p style="color:#909399;">暂无正文预览内容</p>');
  }, [publishContent]);

  const sanitizedEditPreview = useMemo(() => {
    return DOMPurify.sanitize(editContent || '<p style="color:#909399;">暂无正文预览内容</p>');
  }, [editContent]);

  const sanitizedDetailContent = useMemo(() => {
    if (!currentNotice?.noticeContent) return '';
    return DOMPurify.sanitize(currentNotice.noticeContent);
  }, [currentNotice]);

  // 表格列定义
  const columns: ColumnsType<SysNoticeVO> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      align: 'center'
    },
    {
      title: '业务防重键',
      dataIndex: 'dedupKey',
      key: 'dedupKey',
      minWidth: 170,
      render: (val: string) => (
        <Tag color="default" style={{ fontFamily: 'Consolas, Monaco, monospace' }}>
          {val}
        </Tag>
      )
    },
    {
      title: '公告标题',
      dataIndex: 'noticeTitle',
      key: 'noticeTitle',
      minWidth: 220,
      ellipsis: true,
      render: (text: string, record) => (
        <a
          onClick={() => viewDetail(record)}
          style={{ fontWeight: 600, color: '#1677ff' }}
        >
          {text}
        </a>
      )
    },
    {
      title: '类型',
      dataIndex: 'noticeType',
      key: 'noticeType',
      width: 110,
      align: 'center',
      render: (type: string) => (
        <Tag color={type === 'ANNOUNCE' ? 'error' : 'processing'}>
          {type === 'ANNOUNCE' ? '全校公告' : '教学通知'}
        </Tag>
      )
    },
    {
      title: '发布范围',
      dataIndex: 'targetScope',
      key: 'targetScope',
      width: 140,
      align: 'center',
      render: (scope: string, record) => {
        if (scope === 'ALL') {
          return <Tag color="success">全校师生</Tag>;
        }
        return <Tag color="warning">本院系 (ID:{record.targetDeptId || '-'})</Tag>;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center',
      render: (status: number) => (
        <Tag color={status === 1 ? 'success' : 'default'}>
          {status === 1 ? '正常发布' : '已撤回'}
        </Tag>
      )
    },
    {
      title: '发布人',
      dataIndex: 'publisherName',
      key: 'publisherName',
      width: 120,
      align: 'center'
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      key: 'publishTime',
      width: 170,
      align: 'center'
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      align: 'center',
      render: (_, record) => {
        const editable = canModify(record);
        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => viewDetail(record)}
            >
              查阅详情
            </Button>
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              disabled={!editable}
              onClick={() => openEditDialog(record)}
            >
              编辑
            </Button>
            <Button
              type="link"
              danger={record.status === 1}
              size="small"
              icon={record.status === 1 ? <RollbackOutlined /> : <CheckCircleOutlined />}
              disabled={!editable}
              onClick={() => toggleNoticeStatus(record)}
            >
              {record.status === 1 ? '撤回' : '重新发布'}
            </Button>
          </Space>
        );
      }
    }
  ];

  return (
    <div style={{ padding: '24px' }}>
      {/* 页面头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>教学通知与公共公告管理</Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            教学事务通知发布、按院系/全校定向广播、业务防重机制与服务端/客户端双重XSS安全防御
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openPublishDialog}
          >
            发布新通知公告
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            刷新
          </Button>
        </Space>
      </div>

      {/* 筛选面板 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={filterForm} layout="inline" onFinish={loadData}>
          <Form.Item name="status" label="状态筛选">
            <Select
              placeholder="全部状态"
              allowClear
              style={{ width: 140 }}
              onChange={loadData}
              options={[
                { label: '正常发布', value: 1 },
                { label: '已撤回/关闭', value: 0 }
              ]}
            />
          </Form.Item>
          <Form.Item name="noticeType" label="公告类型">
            <Select
              placeholder="全部类型"
              allowClear
              style={{ width: 180 }}
              onChange={loadData}
              options={[
                { label: '教学通知 (NOTICE)', value: 'NOTICE' },
                { label: '全校公告 (ANNOUNCE)', value: 'ANNOUNCE' }
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

      {/* 通知列表表格 */}
      <Card size="small">
        <Table<SysNoticeVO>
          rowKey="id"
          columns={columns}
          dataSource={noticeList}
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条通知记录` }}
          scroll={{ x: 1100 }}
          bordered
        />
      </Card>

      {/* 发布通知弹窗 */}
      <Modal
        title="人工发布教学通知/公告 (API-114)"
        open={publishModalVisible}
        onCancel={() => setPublishModalVisible(false)}
        width={700}
        footer={[
          <Button key="cancel" onClick={() => setPublishModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={submitting}
            onClick={handlePublish}
          >
            确认发布
          </Button>
        ]}
      >
        <Form form={publishForm} layout="vertical">
          <Form.Item
            name="dedupKey"
            label="业务防重键"
            rules={[{ required: true, message: '请输入业务防重键' }]}
            extra="数据库唯一索引 uk_notice_dedup 物理防重，严禁重复提交"
          >
            <Input
              placeholder="如 MANUAL_DEPT1_P8_NOTIFY_2026"
              addonAfter={
                <a
                  onClick={() => {
                    publishForm.setFieldValue('dedupKey', generateDedupKey());
                  }}
                  style={{ cursor: 'pointer' }}
                >
                  自动生成
                </a>
              }
            />
          </Form.Item>

          <Form.Item
            name="noticeTitle"
            label="公告标题"
            rules={[{ required: true, message: '请输入公告标题' }]}
          >
            <Input maxLength={200} showCount placeholder="请输入通知公告标题" />
          </Form.Item>

          <Form.Item
            name="noticeType"
            label="公告类型"
            rules={[{ required: true, message: '请选择公告类型' }]}
          >
            <Radio.Group>
              <Radio value="NOTICE">教学通知</Radio>
              <Radio value="ANNOUNCE">学校公告</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="targetScope"
            label="发布范围"
            rules={[{ required: true, message: '请选择发布范围' }]}
            extra={
              isDeptAdmin && (
                <span style={{ color: '#e6a23c', fontSize: 12 }}>
                  院系负责人权限仅允许发布本院系通知 (当前院系ID: {user?.deptId || '-'})
                </span>
              )
            }
          >
            <Radio.Group disabled={isDeptAdmin}>
              <Radio value="ALL" disabled={isDeptAdmin}>全校师生 (ALL)</Radio>
              <Radio value="DEPT">本院系 (DEPT)</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="noticeContent"
            label="公告正文"
            rules={[{ required: true, message: '请输入公告正文' }]}
          >
            <Input.TextArea
              rows={5}
              placeholder="支持HTML富文本内容。系统在服务端与客户端执行 Jsoup / DOMPurify 双重净化拦截 XSS 恶意脚本。"
              onChange={(e) => setPublishContent(e.target.value)}
            />
          </Form.Item>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 6, display: 'block' }}>
              实时安全预览 (DOMPurify 净化):
            </Text>
            <div
              style={{
                width: '100%',
                minHeight: 70,
                maxHeight: 150,
                overflowY: 'auto',
                border: '1px dashed #d9d9d9',
                borderRadius: 4,
                padding: '8px 12px',
                backgroundColor: '#fafafa',
                fontSize: 13,
                lineHeight: 1.6
              }}
              dangerouslySetInnerHTML={{ __html: sanitizedPublishPreview }}
            />
          </div>
        </Form>
      </Modal>

      {/* 编辑通知弹窗 */}
      <Modal
        title="修改通知公告 (API-115)"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        width={700}
        footer={[
          <Button key="cancel" onClick={() => setEditModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={submitting}
            onClick={handleEditSubmit}
          >
            保存修改
          </Button>
        ]}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="noticeTitle"
            label="公告标题"
            rules={[{ required: true, message: '请输入公告标题' }]}
          >
            <Input maxLength={200} showCount placeholder="请输入公告标题" />
          </Form.Item>

          <Form.Item
            name="noticeType"
            label="公告类型"
            rules={[{ required: true, message: '请选择公告类型' }]}
          >
            <Radio.Group>
              <Radio value="NOTICE">教学通知</Radio>
              <Radio value="ANNOUNCE">学校公告</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="status"
            label="发布状态"
            rules={[{ required: true, message: '请选择发布状态' }]}
          >
            <Radio.Group>
              <Radio value={1}>正常发布</Radio>
              <Radio value={0}>撤回/关闭</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="noticeContent"
            label="公告正文"
            rules={[{ required: true, message: '请输入公告正文' }]}
          >
            <Input.TextArea
              rows={5}
              placeholder="支持HTML富文本内容"
              onChange={(e) => setEditContent(e.target.value)}
            />
          </Form.Item>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 6, display: 'block' }}>
              安全渲染预览 (DOMPurify 净化):
            </Text>
            <div
              style={{
                width: '100%',
                minHeight: 70,
                maxHeight: 150,
                overflowY: 'auto',
                border: '1px dashed #d9d9d9',
                borderRadius: 4,
                padding: '8px 12px',
                backgroundColor: '#fafafa',
                fontSize: 13,
                lineHeight: 1.6
              }}
              dangerouslySetInnerHTML={{ __html: sanitizedEditPreview }}
            />
          </div>
        </Form>
      </Modal>

      {/* 详情抽屉 */}
      <Drawer
        title="通知公告详情"
        placement="right"
        width={600}
        open={detailDrawerVisible}
        onClose={() => setDetailDrawerVisible(false)}
      >
        {currentNotice && (
          <div>
            <div>
              <Title level={4} style={{ marginBottom: 12 }}>
                {currentNotice.noticeTitle}
              </Title>
              <Space size="middle" style={{ color: '#8c8c8c', fontSize: 13, flexWrap: 'wrap' }}>
                <Tag color={currentNotice.noticeType === 'ANNOUNCE' ? 'error' : 'processing'}>
                  {currentNotice.noticeType === 'ANNOUNCE' ? '全校公告' : '教学通知'}
                </Tag>
                <span>发布人：{currentNotice.publisherName}</span>
                <span>发布时间：{currentNotice.publishTime}</span>
                <span>范围：{currentNotice.targetScope === 'ALL' ? '全校' : '院系'}</span>
              </Space>
            </div>

            <Divider style={{ margin: '16px 0' }} />

            {/* DOMPurify 防 XSS 安全净化渲染 */}
            <div
              style={{
                padding: '12px 0',
                lineHeight: 1.8,
                fontSize: 14,
                minHeight: 160,
                color: '#262626'
              }}
              dangerouslySetInnerHTML={{ __html: sanitizedDetailContent }}
            />

            <Divider style={{ margin: '16px 0' }} />

            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="业务防重键" span={2}>
                <span style={{ fontFamily: 'Consolas, Monaco, monospace' }}>
                  {currentNotice.dedupKey}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="当前状态">
                <Tag color={currentNotice.status === 1 ? 'success' : 'default'}>
                  {currentNotice.status === 1 ? '正常发布' : '已撤回'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="已读状态">
                <Tag color={currentNotice.isRead ? 'success' : 'default'}>
                  {currentNotice.isRead ? '已读' : '未读'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="查阅时间" span={2}>
                {currentNotice.readTime || '-'}
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Drawer>
    </div>
  );
};
