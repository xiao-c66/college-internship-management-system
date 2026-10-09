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
  Row,
  Col,
  Alert,
  Radio,
  Timeline,
  Typography,
  Empty,
  Upload,
  Tooltip,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  UploadOutlined,
  HistoryOutlined,
  CheckCircleOutlined,
  InboxOutlined,
  DownloadOutlined,
  SafetyCertificateOutlined,
  FileTextOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';
import { getTaskList, getTaskStudents, type TaskItem, type TaskStudentItem } from '../../api/task';
import {
  getMaterialItems,
  getMaterialVersions,
  submitMaterial,
  auditMaterial,
  uploadMaterialFile,
  type MaterialItemVO,
  type MaterialVersionVO
} from '../../api/material';

const { Title, Text, Paragraph } = Typography;

export const MaterialPage: React.FC = () => {
  const { userType, user } = useAuthStore();
  const isStudent = userType === 'STUDENT';
  const isTeacher = userType === 'TEACHER';

  // 基础数据与任务列表
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [taskStudents, setTaskStudents] = useState<TaskStudentItem[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<number | undefined>(undefined);
  const [materialList, setMaterialList] = useState<MaterialItemVO[]>([]);
  const [loading, setLoading] = useState(false);

  // 材料提报弹窗 (学生)
  const [submitModalOpen, setSubmitModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [activeItem, setActiveItem] = useState<MaterialItemVO | null>(null);
  const [submitForm] = Form.useForm();
  const [contentTextValue, setContentTextValue] = useState('');
  const [uploadedFileHash, setUploadedFileHash] = useState<string>('');

  // 查验打分弹窗 (教师/管理员)
  const [auditModalOpen, setAuditModalOpen] = useState(false);
  const [auditing, setAuditing] = useState(false);
  const [auditForm] = Form.useForm();

  // 版本快照与追溯弹窗
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [versionHistoryList, setVersionHistoryList] = useState<MaterialVersionVO[]>([]);

  // 最低字数要求
  const minLengthRequirement = useMemo(() => {
    if (activeItem?.materialCode === 'SUMMARY_REPORT') return 1500;
    if (activeItem?.materialCode === 'MIDTERM_SUMMARY') return 500;
    return 0;
  }, [activeItem]);

  const currentContentLength = useMemo(() => {
    return contentTextValue ? contentTextValue.trim().length : 0;
  }, [contentTextValue]);

  // 从材料清单中提取学生选项
  const studentOptions = useMemo(() => {
    const map = new Map<number, string>();
    for (const item of materialList) {
      if (item.studentId && item.studentName) {
        map.set(item.studentId, `${item.studentName} (${item.studentNo || item.studentId})`);
      }
    }
    return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
  }, [materialList]);

  const displayMaterialList = useMemo(() => {
    if (!isStudent && selectedStudentId) {
      return materialList.filter(item => item.studentId === selectedStudentId);
    }
    return materialList;
  }, [isStudent, selectedStudentId, materialList]);

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
          const firstTaskId = records[0].id;
          setSelectedTaskId(firstTaskId);
          fetchMaterials(firstTaskId);
        }
      }
    } catch (e: any) {
      console.error('加载任务列表失败', e);
    }
  };

  const fetchMaterials = async (taskId?: number, studentId?: number) => {
    const tid = taskId ?? selectedTaskId;
    if (!tid) return;
    const sid = studentId !== undefined ? studentId : selectedStudentId;
    setLoading(true);
    try {
      const res = await getMaterialItems(tid, sid);
      if (res.code === 200) {
        setMaterialList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取阶段材料列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleTaskChange = (val: number) => {
    setSelectedTaskId(val);
    setSelectedStudentId(undefined);
    fetchMaterials(val, undefined);
  };

  const handleStudentChange = (val?: number) => {
    setSelectedStudentId(val);
  };

  // 格式化辅助
  const getStatusTag = (status: string) => {
    switch (status) {
      case 'UNSUBMITTED': return <Tag color="default">未提交</Tag>;
      case 'SUBMITTED': return <Tag color="warning">待查验</Tag>;
      case 'APPROVED': return <Tag color="success">查验合格</Tag>;
      case 'RETURNED': return <Tag color="error">退回重修</Tag>;
      default: return <Tag color="default">{status}</Tag>;
    }
  };

  const getTypeText = (type: string) => {
    switch (type) {
      case 'VOUCHER_FILE': return '凭证附件';
      case 'REPORT_TEXT': return '长文本报告';
      case 'HYBRID': return '图文混合';
      default: return type;
    }
  };

  const formatTime = (str?: string) => {
    if (!str) return '-';
    return str.replace('T', ' ').substring(0, 19);
  };

  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const canAudit = (row: MaterialItemVO) => {
    return (isTeacher || !isStudent) && (row.status === 'SUBMITTED' || row.status === 'RETURNED');
  };

  // 下载附件文件
  const handleDownload = async (item: MaterialItemVO) => {
    if (item.attachmentUrl?.startsWith('http://') || item.attachmentUrl?.startsWith('https://')) {
      window.open(item.attachmentUrl, '_blank');
      return;
    }
    if (item.id) {
      try {
        const token = localStorage.getItem('token');
        const res = await fetch(`/api/v1/internship/materials/${item.id}/download`, {
          headers: {
            Authorization: token ? `Bearer ${token}` : ''
          }
        });
        if (!res.ok) {
          const errJson = await res.json().catch(() => ({}));
          message.error(errJson?.message || '下载材料附件失败');
          return;
        }
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = item.fileName || `material_${item.id}.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      } catch (e: any) {
        message.error('下载附件失败: ' + e?.message);
      }
    }
  };

  const handleVersionDownload = async (ver: MaterialVersionVO) => {
    if (ver.attachmentUrl?.startsWith('http://') || ver.attachmentUrl?.startsWith('https://')) {
      window.open(ver.attachmentUrl, '_blank');
      return;
    }
    if (ver.id) {
      try {
        const token = localStorage.getItem('token');
        const res = await fetch(`/api/v1/internship/materials/versions/${ver.id}/download`, {
          headers: {
            Authorization: token ? `Bearer ${token}` : ''
          }
        });
        if (!res.ok) {
          const errJson = await res.json().catch(() => ({}));
          message.error(errJson?.message || '下载历史附件失败');
          return;
        }
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = ver.fileName || `material_v${ver.version}.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      } catch (e: any) {
        message.error('下载历史附件失败: ' + e?.message);
      }
    }
  };

  // 提报弹窗
  const openSubmitDialog = (item: MaterialItemVO) => {
    setActiveItem(item);
    setContentTextValue(item.contentText || '');
    setUploadedFileHash(item.fileHash || '');
    submitForm.setFieldsValue({
      attachmentUrl: item.attachmentUrl || '',
      fileName: item.fileName || '',
      fileSize: item.fileSize,
      fileHash: item.fileHash || '',
      contentText: item.contentText || ''
    });
    setSubmitModalOpen(true);
  };

  const handleCustomUpload = async (options: any) => {
    const { file, onSuccess, onError } = options;
    if (!selectedTaskId || !activeItem) {
      onError(new Error('缺少任务或材料信息'));
      return;
    }
    setUploading(true);
    try {
      const res = await uploadMaterialFile(file as File, selectedTaskId, activeItem.materialCode);
      if (res.code === 200 && res.data) {
        const { fileName, fileSize, fileHash, attachmentUrl } = res.data;
        submitForm.setFieldsValue({
          fileName,
          fileSize,
          fileHash,
          attachmentUrl
        });
        setUploadedFileHash(fileHash);
        message.success(`文件上传成功！已计算防篡改 SHA-256 摘要`);
        onSuccess(res.data);
      } else {
        throw new Error(res.message || '上传失败');
      }
    } catch (e: any) {
      message.error(e?.message || '上传材料附件失败');
      onError(e);
    } finally {
      setUploading(false);
    }
  };

  const handleSubmitMaterial = async () => {
    if (!activeItem || !selectedTaskId) return;
    try {
      const values = await submitForm.validateFields();
      if (minLengthRequirement > 0 && currentContentLength < minLengthRequirement) {
        message.warning(`正文字数不足，最低要求 ${minLengthRequirement} 字 (当前 ${currentContentLength} 字)`);
        return;
      }
      setSubmitting(true);
      const res = await submitMaterial({
        taskId: selectedTaskId,
        materialCode: activeItem.materialCode,
        contentText: values.contentText,
        attachmentUrl: values.attachmentUrl,
        fileName: values.fileName,
        fileSize: values.fileSize,
        fileHash: values.fileHash || uploadedFileHash
      });
      if (res.code === 200) {
        message.success('材料提报成功！已留存新版本快照并绑定哈希');
        setSubmitModalOpen(false);
        fetchMaterials();
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '提报失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 查验打分弹窗
  const openAuditDialog = (item: MaterialItemVO) => {
    setActiveItem(item);
    auditForm.setFieldsValue({
      action: 'APPROVED',
      auditScore: item.auditScore ?? 90.0,
      auditComment: item.auditComment || ''
    });
    setAuditModalOpen(true);
  };

  const handleAuditMaterial = async () => {
    if (!activeItem?.id) return;
    try {
      const values = await auditForm.validateFields();
      if (!values.auditComment || !values.auditComment.trim()) {
        message.warning('请填写查验指导评语');
        return;
      }
      setAuditing(true);
      const res = await auditMaterial(activeItem.id, {
        action: values.action,
        auditScore: values.action === 'APPROVED' ? values.auditScore : undefined,
        auditComment: values.auditComment
      });
      if (res.code === 200) {
        message.success('材料查验打分完成！');
        setAuditModalOpen(false);
        fetchMaterials();
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '查验操作失败');
    } finally {
      setAuditing(false);
    }
  };

  // 版本快照追溯弹窗
  const openVersionHistory = async (item: MaterialItemVO) => {
    if (!item.id) return;
    setActiveItem(item);
    setHistoryModalOpen(true);
    setHistoryLoading(true);
    try {
      const res = await getMaterialVersions(item.id);
      if (res.code === 200) {
        setVersionHistoryList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取历史版本快照失败');
    } finally {
      setHistoryLoading(false);
    }
  };

  // 教师/管理员表格定义
  const columns: ColumnsType<MaterialItemVO> = [
    {
      title: '学生姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 100,
      render: v => <Text strong>{v || '-'}</Text>
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      width: 110,
      render: v => <Text type="secondary">{v || '-'}</Text>
    },
    {
      title: '材料名称',
      dataIndex: 'materialName',
      key: 'materialName',
      minWidth: 150
    },
    {
      title: '形态',
      dataIndex: 'materialType',
      key: 'materialType',
      width: 100,
      render: v => <Tag color="blue">{getTypeText(v)}</Tag>
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 70,
      align: 'center',
      render: v => <Tag color="purple">v{v}</Tag>
    },
    {
      title: '查验状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center',
      render: v => getStatusTag(v)
    },
    {
      title: '防篡改校验 (SHA-256)',
      key: 'fileHash',
      width: 180,
      render: (_, r) => {
        if (!r.fileHash) return <Text type="secondary">-</Text>;
        return (
          <Tooltip title={`完整SHA-256摘要: ${r.fileHash}`}>
            <Tag color="geekblue" icon={<SafetyCertificateOutlined />}>
              {r.fileHash.substring(0, 10)}...{r.fileHash.substring(r.fileHash.length - 6)}
            </Tag>
          </Tooltip>
        );
      }
    },
    {
      title: '提报时间',
      dataIndex: 'submitTime',
      key: 'submitTime',
      width: 150,
      render: v => formatTime(v)
    },
    {
      title: '考评分',
      dataIndex: 'auditScore',
      key: 'auditScore',
      width: 80,
      align: 'center',
      render: v => v != null ? <Text strong style={{ color: '#52c41a' }}>{v}</Text> : <Text type="secondary">未评</Text>
    },
    {
      title: '查验导师',
      dataIndex: 'auditTeacherName',
      key: 'auditTeacherName',
      width: 90,
      render: v => v || '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      fixed: 'right',
      align: 'center',
      render: (_, row) => (
        <Space size="small">
          {canAudit(row) && (
            <Button
              type="link"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => openAuditDialog(row)}
            >
              查验打分
            </Button>
          )}
          {row.id && (
            <Button
              type="link"
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => openVersionHistory(row)}
            >
              版本快照
            </Button>
          )}
          {row.attachmentUrl && (
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => handleDownload(row)}
            >
              凭证下载
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* 头部区域 */}
      <Card style={{ marginBottom: 20, borderRadius: 8 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Space align="center">
              <Title level={4} style={{ margin: 0 }}>阶段材料与实习总结报告</Title>
              <Tag color="cyan">API-060 ~ API-064</Tag>
            </Space>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              三方协议、录用通知、岗位记录、中期进展总结及毕业实习总结报告规范提报与导师查验
            </Paragraph>
          </div>
          <Space wrap>
            <Select
              value={selectedTaskId}
              placeholder="选择实习任务批次"
              style={{ width: 260 }}
              options={tasks.map(t => ({ label: t.taskName, value: t.id }))}
              onChange={handleTaskChange}
            />
            {!isStudent && (
              <Select
                value={selectedStudentId}
                placeholder="全部负责学生 (可筛选)"
                style={{ width: 220 }}
                allowClear
                showSearch
                optionFilterProp="label"
                options={studentOptions}
                onChange={handleStudentChange}
              />
            )}
            <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={() => fetchMaterials()}>
              刷新
            </Button>
          </Space>
        </div>
      </Card>

      {/* 提报规范说明 Alert */}
      <Alert
        type="info"
        showIcon
        message="阶段材料提报与防篡改规范说明"
        description="学生在各阶段须提报三方协议、入职通知书、岗位安全记录、中期进展总结及毕业实习总结报告。上传附件支持 PDF、Word、图片及 ZIP 格式，服务端自动检测文件魔数并实时计算 SHA-256 完整性摘要。历史版本与评语永久追溯，已查验合格的材料禁止修改。"
        style={{ marginBottom: 20 }}
      />

      {/* 学生端视图: 卡片网格 */}
      {isStudent ? (
        <div>
          {materialList.length > 0 ? (
            <Row gutter={[20, 20]}>
              {materialList.map(item => (
                <Col xs={24} sm={12} md={8} key={item.materialCode}>
                  <Card
                    hoverable
                    style={{
                      borderRadius: 8,
                      minHeight: 280,
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between'
                    }}
                    title={
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Space>
                          <Text strong style={{ fontSize: 16 }}>{item.materialName}</Text>
                          {item.required && <Tag color="error">必填</Tag>}
                        </Space>
                        {getStatusTag(item.status)}
                      </div>
                    }
                  >
                    <div>
                      <Paragraph style={{ margin: '4px 0' }}>
                        <Text strong>材料形态：</Text>{getTypeText(item.materialType)}
                      </Paragraph>
                      <Paragraph style={{ margin: '4px 0' }}>
                        <Text strong>当前版本：</Text><Tag color="purple">v{item.version || 1}</Tag>
                      </Paragraph>
                      {item.fileName && (
                        <Paragraph style={{ margin: '4px 0' }}>
                          <Text strong>附件文件：</Text>
                          <Text code>{item.fileName}</Text> {item.fileSize ? `(${formatFileSize(item.fileSize)})` : ''}
                        </Paragraph>
                      )}
                      {item.fileHash && (
                        <Paragraph style={{ margin: '4px 0' }}>
                          <Text strong>防篡改摘要：</Text>
                          <Tooltip title={`SHA-256: ${item.fileHash}`}>
                            <Tag color="geekblue" icon={<SafetyCertificateOutlined />}>
                              {item.fileHash.substring(0, 12)}...
                            </Tag>
                          </Tooltip>
                        </Paragraph>
                      )}
                      {item.minContentLength != null && item.minContentLength > 0 && (
                        <Paragraph style={{ margin: '4px 0' }}>
                          <Text strong>字数门槛：</Text>不少于 {item.minContentLength} 字
                        </Paragraph>
                      )}
                      {item.submitTime && (
                        <Paragraph style={{ margin: '4px 0' }}>
                          <Text strong>提交时刻：</Text>{formatTime(item.submitTime)}
                        </Paragraph>
                      )}
                      {item.auditScore != null && (
                        <Paragraph style={{ margin: '4px 0' }}>
                          <Text strong>查验得分：</Text>
                          <Text strong style={{ color: '#52c41a', fontSize: 16 }}> {item.auditScore} 分</Text>
                          {item.auditTeacherName && <Text type="secondary">（{item.auditTeacherName}）</Text>}
                        </Paragraph>
                      )}
                      {item.auditComment && (
                        <div style={{ marginTop: 8, background: '#fffbe6', padding: 8, borderRadius: 4, border: '1px solid #ffe58f' }}>
                          <Text strong style={{ color: '#d48806' }}>导师评语：</Text>
                          <div style={{ color: '#595959', fontSize: 13, marginTop: 2 }}>{item.auditComment}</div>
                        </div>
                      )}
                    </div>

                    <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: 12, marginTop: 12 }}>
                      <Space wrap>
                        <Button
                          type="primary"
                          size="small"
                          disabled={item.status === 'APPROVED'}
                          icon={<UploadOutlined />}
                          onClick={() => openSubmitDialog(item)}
                        >
                          {item.status === 'UNSUBMITTED' ? '提报材料' : item.status === 'APPROVED' ? '查验已合格' : '修改重提'}
                        </Button>
                        {item.id && (
                          <Button
                            size="small"
                            icon={<HistoryOutlined />}
                            onClick={() => openVersionHistory(item)}
                          >
                            版本追溯
                          </Button>
                        )}
                        {item.attachmentUrl && (
                          <Button
                            size="small"
                            type="link"
                            icon={<DownloadOutlined />}
                            onClick={() => handleDownload(item)}
                          >
                            下载凭证
                          </Button>
                        )}
                      </Space>
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          ) : (
            <Card style={{ borderRadius: 8 }}>
              <Empty description="该任务暂未配置阶段材料清单或正在加载中" />
            </Card>
          )}
        </div>
      ) : (
        /* 教师 / 管理员端视图: 表格 */
        <Card style={{ borderRadius: 8 }}>
          <Table
            columns={columns}
            dataSource={displayMaterialList}
            rowKey={(r, idx) => r.id || `${r.materialCode}_${idx}`}
            loading={loading}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            scroll={{ x: 1300 }}
          />
        </Card>
      )}

      {/* 材料提报弹窗 (学生) */}
      <Modal
        title={`提报材料: ${activeItem?.materialName} (v${(activeItem?.version || 0) + (activeItem?.status === 'UNSUBMITTED' ? 0 : 1)})`}
        open={submitModalOpen}
        onCancel={() => setSubmitModalOpen(false)}
        onOk={handleSubmitMaterial}
        confirmLoading={submitting}
        okText="确认提交提报"
        cancelText="取消"
        width={680}
        destroyOnClose
      >
        {activeItem?.status === 'RETURNED' && activeItem.auditComment && (
          <Alert
            type="error"
            showIcon
            message="上一版本查验退回指导意见"
            description={
              <div>
                <Paragraph style={{ margin: 0 }}>{activeItem.auditComment}</Paragraph>
                <Text type="secondary" style={{ fontSize: 12 }}>请严格对照导师意见修改或重新上传合规文件后提交。</Text>
              </div>
            }
            style={{ marginBottom: 16 }}
          />
        )}

        <Form form={submitForm} layout="vertical">
          <Form.Item label="材料编码">
            <Input value={activeItem?.materialCode} disabled />
          </Form.Item>

          {(activeItem?.materialType === 'VOUCHER_FILE' || activeItem?.materialType === 'HYBRID') && (
            <>
              <Form.Item label="上传佐证附件文件" extra="支持 PDF、Word(doc/docx)、图片(png/jpg)及 ZIP 压缩包，最大 10MB。系统将自动验证文件特征并计算 SHA-256 摘要。">
                <Upload.Dragger
                  name="file"
                  multiple={false}
                  showUploadList={false}
                  customRequest={handleCustomUpload}
                  disabled={uploading}
                >
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined style={{ fontSize: 32, color: '#1677ff' }} />
                  </p>
                  <p className="ant-upload-text">点击或将凭证文件拖拽到此区域上传</p>
                  <p className="ant-upload-hint">自动计算并保存文件防篡改哈希</p>
                </Upload.Dragger>
              </Form.Item>

              <Form.Item name="fileName" label="凭据文件名" rules={[{ required: true, message: '请上传凭证文件或输入文件名' }]}>
                <Input placeholder="上传文件后自动填入" />
              </Form.Item>

              <Form.Item name="attachmentUrl" label="凭据附件路径" rules={[{ required: true, message: '附件路径不能为空' }]}>
                <Input placeholder="上传文件后自动生成安全存储路径" readOnly />
              </Form.Item>

              <Form.Item name="fileSize" hidden>
                <InputNumber />
              </Form.Item>

              <Form.Item name="fileHash" label="防篡改完整性哈希 (SHA-256)">
                <Input
                  readOnly
                  placeholder="文件上传后自动生成 SHA-256 摘要"
                  prefix={<SafetyCertificateOutlined style={{ color: '#52c41a' }} />}
                />
              </Form.Item>
            </>
          )}

          {(activeItem?.materialType === 'REPORT_TEXT' || activeItem?.materialType === 'HYBRID') && (
            <Form.Item
              name="contentText"
              label="报告正文"
              rules={[
                {
                  validator: async (_, value) => {
                    const len = value ? value.trim().length : 0;
                    if (minLengthRequirement > 0 && len < minLengthRequirement) {
                      return Promise.reject(new Error(`正文字数不足，最低要求 ${minLengthRequirement} 字 (当前 ${len} 字)`));
                    }
                    return Promise.resolve();
                  }
                }
              ]}
            >
              <Input.TextArea
                rows={10}
                placeholder="请输入正文内容..."
                value={contentTextValue}
                onChange={e => setContentTextValue(e.target.value)}
              />
              <div style={{ marginTop: 6, fontSize: 13, color: currentContentLength < minLengthRequirement ? '#ff4d4f' : '#52c41a' }}>
                当前字数: <strong>{currentContentLength}</strong> 字 / 最低要求: {minLengthRequirement} 字
              </div>
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* 材料查验打分弹窗 (教师) */}
      <Modal
        title="导师查验材料与考评分数评定"
        open={auditModalOpen}
        onCancel={() => setAuditModalOpen(false)}
        onOk={handleAuditMaterial}
        confirmLoading={auditing}
        okText="确认查验结果"
        cancelText="取消"
        width={560}
        destroyOnClose
      >
        <Form form={auditForm} layout="vertical">
          <Form.Item label="学生信息">
            <Text strong>{activeItem?.studentName} ({activeItem?.studentNo})</Text>
          </Form.Item>
          <Form.Item label="材料名称">
            <Text strong>{activeItem?.materialName} (v{activeItem?.version})</Text>
          </Form.Item>
          {activeItem?.fileHash && (
            <Form.Item label="防篡改 SHA-256">
              <Tooltip title={activeItem.fileHash}>
                <Tag color="geekblue" icon={<SafetyCertificateOutlined />}>
                  {activeItem.fileHash}
                </Tag>
              </Tooltip>
            </Form.Item>
          )}
          <Form.Item name="action" label="查验结论" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="APPROVED">查验通过</Radio>
              <Radio value="RETURNED">退回修改</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.action !== cur.action}
          >
            {({ getFieldValue }) =>
              getFieldValue('action') === 'APPROVED' ? (
                <Form.Item
                  name="auditScore"
                  label="考评分数"
                  rules={[{ required: true, message: '请录入考评分数' }]}
                  extra="百分制 (0.00 ~ 100.00)"
                >
                  <InputNumber min={0} max={100} precision={2} step={1} style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="auditComment"
            label="指导评语"
            rules={[{ required: true, message: '请输入查验审核意见' }]}
          >
            <Input.TextArea rows={4} placeholder="请输入查验审核意见，若退回请详细说明修改要求..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 版本快照追溯弹窗 */}
      <Modal
        title={`版本快照追溯: ${activeItem?.materialName || ''}`}
        open={historyModalOpen}
        onCancel={() => setHistoryModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setHistoryModalOpen(false)}>
            关闭
          </Button>
        ]}
        width={780}
        destroyOnClose
      >
        <div style={{ maxHeight: 520, overflowY: 'auto', paddingRight: 8 }}>
          {versionHistoryList.length > 0 ? (
            <Timeline
              items={versionHistoryList.map(ver => ({
                color: ver.status === 'APPROVED' ? 'green' : ver.status === 'RETURNED' ? 'red' : 'blue',
                children: (
                  <Card size="small" style={{ marginBottom: 12, borderRadius: 6, background: '#fafafa' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong style={{ fontSize: 15 }}>版本 v{ver.version}</Text>
                      {getStatusTag(ver.status)}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      提报时刻: {formatTime(ver.submitTime || ver.createdAt)}
                    </Text>

                    {ver.auditScore != null && (
                      <Paragraph style={{ margin: '8px 0 4px 0' }}>
                        <Text strong>考评分：</Text>
                        <Text strong style={{ color: '#52c41a' }}>{ver.auditScore} 分</Text>
                        {ver.auditTeacherName && <Text type="secondary">（审核教师：{ver.auditTeacherName}）</Text>}
                      </Paragraph>
                    )}

                    {ver.auditComment && (
                      <Paragraph style={{ margin: '4px 0' }}>
                        <Text strong>查验评语：</Text>{ver.auditComment}
                      </Paragraph>
                    )}

                    {ver.fileHash && (
                      <Paragraph style={{ margin: '4px 0' }}>
                        <Text strong>防篡改 SHA-256：</Text>
                        <Tooltip title={ver.fileHash}>
                          <Tag color="geekblue" icon={<SafetyCertificateOutlined />}>
                            {ver.fileHash.substring(0, 16)}...
                          </Tag>
                        </Tooltip>
                      </Paragraph>
                    )}

                    {ver.attachmentUrl && (
                      <Paragraph style={{ margin: '4px 0' }}>
                        <Text strong>凭据附件：</Text>
                        <Button
                          type="link"
                          size="small"
                          icon={<DownloadOutlined />}
                          onClick={() => handleVersionDownload(ver)}
                        >
                          {ver.fileName || '下载此版本附件'} {ver.fileSize ? `(${formatFileSize(ver.fileSize)})` : ''}
                        </Button>
                      </Paragraph>
                    )}

                    {ver.contentText && (
                      <div style={{ marginTop: 8 }}>
                        <Text strong>正文内容 ({ver.contentText.length} 字)：</Text>
                        <div
                          style={{
                            background: '#fff',
                            border: '1px solid #e8e8e8',
                            borderRadius: 4,
                            padding: 8,
                            maxHeight: 120,
                            overflowY: 'auto',
                            whiteSpace: 'pre-wrap',
                            fontSize: 13,
                            marginTop: 4
                          }}
                        >
                          {ver.contentText}
                        </div>
                      </div>
                    )}
                  </Card>
                )
              }))}
            />
          ) : (
            <Empty description="暂无历史版本快照" />
          )}
        </div>
      </Modal>
    </div>
  );
};
