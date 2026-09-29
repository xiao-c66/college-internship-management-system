import React, { useState } from 'react';
import {
  Drawer,
  Descriptions,
  Tag,
  Divider,
  Space,
  Button,
  Form,
  Radio,
  Input,
  Timeline,
  Card,
  Row,
  Col,
  Alert,
  message,
  Typography
} from 'antd';
import {
  AuditOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  UserOutlined,
  FileDoneOutlined,
  SwapOutlined
} from '@ant-design/icons';
import {
  teacherAuditApplyChangeApi,
  deptAuditApplyChangeApi,
  ApplyChangeVO,
  ApplyChangeAuditDTO
} from '../../api/applyChange';
import { useAuthStore } from '../../store/useAuthStore';

const { Text, Paragraph, Title } = Typography;

interface ApplyChangeAuditDrawerProps {
  visible: boolean;
  onClose: () => void;
  onSuccess: () => void;
  changeData: ApplyChangeVO | null;
}

export const getChangeStatusTag = (status?: string) => {
  switch (status) {
    case 'PENDING_TEACHER':
      return <Tag color="processing" icon={<ClockCircleOutlined />}>待导师初审</Tag>;
    case 'PENDING_DEPT':
      return <Tag color="warning" icon={<ClockCircleOutlined />}>待院系终审</Tag>;
    case 'APPROVED':
      return <Tag color="success" icon={<CheckCircleOutlined />}>变更已生效</Tag>;
    case 'REJECTED':
      return <Tag color="error" icon={<CloseCircleOutlined />}>变更已驳回</Tag>;
    default:
      return <Tag>{status || '未知'}</Tag>;
  }
};

export const ApplyChangeAuditDrawer: React.FC<ApplyChangeAuditDrawerProps> = ({
  visible,
  onClose,
  onSuccess,
  changeData
}) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const { userType } = useAuthStore();

  if (!changeData) return null;

  const canTeacherAudit =
    (userType === 'TEACHER' || userType === 'SYS_ADMIN') &&
    changeData.changeStatus === 'PENDING_TEACHER';

  const canDeptAudit =
    (userType === 'DEPT_ADMIN' || userType === 'SYS_ADMIN') &&
    changeData.changeStatus === 'PENDING_DEPT';

  const handleAuditSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const dto: ApplyChangeAuditDTO = {
        auditAction: values.auditAction,
        auditOpinion: values.auditOpinion.trim()
      };

      let res: any;
      if (canTeacherAudit) {
        res = await teacherAuditApplyChangeApi(changeData.id, dto);
      } else if (canDeptAudit) {
        res = await deptAuditApplyChangeApi(changeData.id, dto);
      } else {
        message.warning('当前状态或角色无法执行审核');
        return;
      }

      if (res.code === 200) {
        message.success('审核操作已成功提交并留存安全审计轨迹！');
        form.resetFields();
        onSuccess();
      } else {
        message.error(res.message || '审核提交失败');
      }
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error(err?.message || '审核异常');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={
        <Space>
          <SwapOutlined style={{ color: '#1890ff' }} />
          <span>实习重大信息变更单 #{changeData.id}</span>
          {getChangeStatusTag(changeData.changeStatus)}
        </Space>
      }
      open={visible}
      onClose={onClose}
      width={860}
      destroyOnClose
    >
      {/* 提示信息 */}
      <Alert
        type={
          changeData.changeStatus === 'APPROVED'
            ? 'success'
            : changeData.changeStatus === 'REJECTED'
            ? 'error'
            : 'info'
        }
        showIcon
        style={{ marginBottom: 16 }}
        message={`变更单状态：${
          changeData.changeStatus === 'APPROVED'
            ? '已由二级院系终审批准，主数据已原子更新生效！'
            : changeData.changeStatus === 'REJECTED'
            ? '该变更申请已被驳回，原实习主数据完整保持原状。'
            : changeData.changeStatus === 'PENDING_TEACHER'
            ? '正在等待指导教师进行专业对口性与安全初审。'
            : '已通过教师初审，正在等待二级院系管理员终审。'
        }`}
      />

      {/* 学生与导师基本信息 */}
      <Descriptions size="small" bordered column={{ xs: 1, sm: 2, md: 3 }} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="申请学生">{changeData.studentName} ({changeData.studentNumber})</Descriptions.Item>
        <Descriptions.Item label="指导教师">{changeData.teacherName || '未指定'}</Descriptions.Item>
        <Descriptions.Item label="提交时间">{changeData.createTime}</Descriptions.Item>
      </Descriptions>

      {/* 变更前后信息对比表格 */}
      <Card title="🔄 实习重大信息变更前后精准比对" size="small" style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col span={12}>
            <div style={{ backgroundColor: '#fffbe6', padding: 12, borderRadius: 6, border: '1px solid #ffe58f', height: '100%' }}>
              <Text strong style={{ color: '#d48806', display: 'block', marginBottom: 8 }}>
                📌 原实习主数据 (快照)
              </Text>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="单位名称">{changeData.origCompanyName}</Descriptions.Item>
                <Descriptions.Item label="实习岗位">{changeData.origJobPosition}</Descriptions.Item>
                <Descriptions.Item label="工作地点">{changeData.origJobAddress}</Descriptions.Item>
                <Descriptions.Item label="联系人员">{changeData.origContactPerson} ({changeData.origContactPhone})</Descriptions.Item>
                <Descriptions.Item label="起止周期">{changeData.origStartDate} ~ {changeData.origEndDate}</Descriptions.Item>
                <Descriptions.Item label="组织模式">{changeData.origInternshipMode === 'CONCENTRATED' ? '集中实习' : '分散实习'}</Descriptions.Item>
              </Descriptions>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ backgroundColor: '#e6f7ff', padding: 12, borderRadius: 6, border: '1px solid #91d5ff', height: '100%' }}>
              <Text strong style={{ color: '#096dd9', display: 'block', marginBottom: 8 }}>
                🚀 拟变更的新信息
              </Text>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="单位名称"><Text strong>{changeData.newCompanyName}</Text></Descriptions.Item>
                <Descriptions.Item label="实习岗位"><Text strong>{changeData.newJobPosition}</Text></Descriptions.Item>
                <Descriptions.Item label="工作地点">{changeData.newJobAddress}</Descriptions.Item>
                <Descriptions.Item label="联系人员">{changeData.newContactPerson} ({changeData.newContactPhone})</Descriptions.Item>
                <Descriptions.Item label="起止周期">{changeData.newStartDate} ~ {changeData.newEndDate}</Descriptions.Item>
                <Descriptions.Item label="组织模式">{changeData.newInternshipMode === 'CONCENTRATED' ? '集中实习' : '分散实习'}</Descriptions.Item>
              </Descriptions>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 变更事由 */}
      <Card size="small" title="📝 变更详细事由陈述" style={{ marginBottom: 16 }}>
        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
          {changeData.changeReason}
        </Paragraph>
        {changeData.proofFileUrl && (
          <div style={{ marginTop: 8 }}>
            <Text type="secondary">证明附件：</Text>
            <a href={changeData.proofFileUrl} target="_blank" rel="noreferrer">
              {changeData.proofFileUrl}
            </a>
          </div>
        )}
      </Card>

      {/* 审批与流转历史轨迹 */}
      <Card size="small" title="📜 审批流转轨迹与不可篡改历史记录" style={{ marginBottom: 16 }}>
        {changeData.histories && changeData.histories.length > 0 ? (
          <Timeline
            items={changeData.histories.map(h => ({
              color: h.auditAction === 'APPROVE' ? 'green' : h.auditAction === 'REJECT' ? 'red' : 'blue',
              dot:
                h.auditAction === 'APPROVE' ? (
                  <CheckCircleOutlined />
                ) : h.auditAction === 'REJECT' ? (
                  <CloseCircleOutlined />
                ) : (
                  <UserOutlined />
                ),
              children: (
                <div>
                  <Space>
                    <Text strong>{h.operatorName}</Text>
                    <Tag>{h.operatorRole}</Tag>
                    <Tag color={h.auditAction === 'APPROVE' ? 'success' : h.auditAction === 'REJECT' ? 'error' : 'default'}>
                      {h.auditAction === 'APPROVE' ? '审核通过' : h.auditAction === 'REJECT' ? '退回驳回' : '提交申请'}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {h.createTime}
                    </Text>
                  </Space>
                  <div style={{ marginTop: 4, color: '#595959', fontSize: 13, background: '#fafafa', padding: '6px 10px', borderRadius: 4 }}>
                    {h.auditOpinion}
                  </div>
                </div>
              )
            }))}
          />
        ) : (
          <Text type="secondary">暂无历史轨迹</Text>
        )}
      </Card>

      {/* 审核操作表单 (仅在对应角色且处于对应审批节点时展开) */}
      {(canTeacherAudit || canDeptAudit) && (
        <Card
          size="small"
          title={
            <Space>
              <AuditOutlined style={{ color: '#1890ff' }} />
              <span style={{ fontWeight: 'bold' }}>
                {canTeacherAudit ? '👨‍🏫 指导教师初审操作区' : '🏛️ 二级院系终审操作区'}
              </span>
            </Space>
          }
          style={{ borderColor: '#1890ff', backgroundColor: '#fcfdff' }}
        >
          <Form form={form} layout="vertical" initialValues={{ auditAction: 'APPROVE' }}>
            <Form.Item
              name="auditAction"
              label="审核判定"
              rules={[{ required: true, message: '请选择审核结果' }]}
            >
              <Radio.Group buttonStyle="solid">
                <Radio.Button value="APPROVE" style={{ marginRight: 16 }}>
                  <CheckCircleOutlined style={{ marginRight: 4 }} />
                  {canTeacherAudit ? '初审通过，同意流转院系' : '终审批准，正式生效更新主数据'}
                </Radio.Button>
                <Radio.Button value="REJECT">
                  <CloseCircleOutlined style={{ marginRight: 4 }} />
                  退回驳回
                </Radio.Button>
              </Radio.Group>
            </Form.Item>

            <Form.Item
              name="auditOpinion"
              label="审核意见与说明"
              rules={[
                { required: true, message: '请输入审核意见' },
                { min: 5, message: '审核意见至少5字' },
                { max: 500, message: '审核意见不能超过500字' }
              ]}
            >
              <Input.TextArea
                rows={3}
                placeholder="请输入详细的审核意见（如：对拟变更单位专业对口度、工作环境安全性的考察结论等）"
                showCount
                maxLength={500}
              />
            </Form.Item>

            <Button
              type="primary"
              icon={<FileDoneOutlined />}
              loading={submitting}
              onClick={handleAuditSubmit}
              style={{ width: '100%', marginTop: 8 }}
            >
              {canTeacherAudit ? '提交指导教师初审结果' : '提交二级院系终审裁定'}
            </Button>
          </Form>
        </Card>
      )}
    </Drawer>
  );
};
