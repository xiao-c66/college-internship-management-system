import React, { useState, useEffect } from 'react';
import {
  Modal,
  Form,
  Input,
  DatePicker,
  Radio,
  Button,
  Alert,
  Descriptions,
  Divider,
  Space,
  message,
  Typography
} from 'antd';
import {
  SwapOutlined,
  SendOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { submitApplyChangeApi, ApplyChangeDTO } from '../../api/applyChange';
import type { ApplyVO } from '../../api/apply';

const { Text, Paragraph } = Typography;
const { RangePicker } = DatePicker;

interface ApplyChangeModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  originalApply: ApplyVO | null;
}

export const ApplyChangeModal: React.FC<ApplyChangeModalProps> = ({
  visible,
  onCancel,
  onSuccess,
  originalApply
}) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (visible && originalApply) {
      form.setFieldsValue({
        newInternshipMode: originalApply.internshipMode || 'DISTRIBUTED',
        dateRange: originalApply.startDate && originalApply.endDate
          ? [dayjs(originalApply.startDate), dayjs(originalApply.endDate)]
          : [dayjs(), dayjs().add(90, 'day')]
      });
    }
  }, [visible, originalApply, form]);

  if (!originalApply) return null;

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const dto: ApplyChangeDTO = {
        applyId: originalApply.id,
        newCompanyName: values.newCompanyName.trim(),
        newJobPosition: values.newJobPosition.trim(),
        newJobAddress: values.newJobAddress.trim(),
        newContactPerson: values.newContactPerson.trim(),
        newContactPhone: values.newContactPhone.trim(),
        newContactEmail: values.newContactEmail ? values.newContactEmail.trim() : undefined,
        newStartDate: values.dateRange[0].format('YYYY-MM-DD'),
        newEndDate: values.dateRange[1].format('YYYY-MM-DD'),
        newInternshipMode: values.newInternshipMode,
        newJobDuties: values.newJobDuties ? values.newJobDuties.trim() : undefined,
        newAgreementFileUrl: values.newAgreementFileUrl ? values.newAgreementFileUrl.trim() : undefined,
        changeReason: values.changeReason.trim(),
        proofFileUrl: values.proofFileUrl ? values.proofFileUrl.trim() : undefined
      };

      const res: any = await submitApplyChangeApi(dto);
      if (res.code === 200) {
        message.success('实习重大信息变更申请已成功提交，进入指导教师初审阶段！');
        form.resetFields();
        onSuccess();
      } else {
        message.error(res.message || '提交变更申请失败');
      }
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error(err?.message || '提交异常，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <SwapOutlined style={{ color: '#1890ff' }} />
          <span>发起实习重大信息变更申请 (APPLY-009 合规流转)</span>
        </Space>
      }
      open={visible}
      onCancel={onCancel}
      width={800}
      footer={[
        <Button key="cancel" onClick={onCancel} disabled={submitting}>
          取消
        </Button>,
        <Button
          key="submit"
          type="primary"
          icon={<SendOutlined />}
          loading={submitting}
          onClick={handleSubmit}
        >
          提交变更申请
        </Button>
      ]}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        icon={<InfoCircleOutlined />}
        style={{ marginBottom: 16 }}
        message="重大信息变更审批机制与不可篡改审计说明"
        description="因原实习主数据已终审通过并锁定(APPROVED)，单位、岗位、地点变动必须提交双级审批；审批流程为：指导教师初审 → 二级学院管理员终审。只有终审批准通过后，主数据才会在同一事务内原子更新生效；若审批被驳回，原主数据保持不变。"
      />

      <div style={{ marginBottom: 16, backgroundColor: '#fcfcfc', padding: 12, borderRadius: 8, border: '1px solid #f0f0f0' }}>
        <Text strong style={{ display: 'block', marginBottom: 8, color: '#595959' }}>
          📋 当前已锁定的原实习信息快照
        </Text>
        <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }} bordered>
          <Descriptions.Item label="原实习单位">{originalApply.companyName}</Descriptions.Item>
          <Descriptions.Item label="原实习岗位">{originalApply.jobPosition}</Descriptions.Item>
          <Descriptions.Item label="原组织模式">{originalApply.internshipMode === 'CONCENTRATED' ? '集中实习' : '分散实习'}</Descriptions.Item>
          <Descriptions.Item label="原联系人">{originalApply.companyContactPerson} ({originalApply.companyContactPhone})</Descriptions.Item>
          <Descriptions.Item label="原实习周期" span={2}>
            {originalApply.startDate} ~ {originalApply.endDate}
          </Descriptions.Item>
          <Descriptions.Item label="原实习地点" span={3}>
            {originalApply.jobAddress}
          </Descriptions.Item>
        </Descriptions>
      </div>

      <Divider orientation="left" style={{ margin: '16px 0 12px 0', fontSize: 13, color: '#1890ff' }}>
        ✏️ 填写拟变更的新实习信息与变更事由
      </Divider>

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          newInternshipMode: originalApply.internshipMode || 'DISTRIBUTED'
        }}
      >
        <Form.Item
          name="changeReason"
          label={<span style={{ fontWeight: 'bold', color: '#ff4d4f' }}>* 变更详细事由 (不少于10字)</span>}
          rules={[
            { required: true, message: '请详细陈述申请变更的具体事由' },
            { min: 10, message: '变更事由陈述须不少于10字' },
            { max: 1000, message: '变更事由不能超过1000字' }
          ]}
        >
          <Input.TextArea
            rows={3}
            placeholder="请详细说明因何原因需要变更实习单位或岗位（例如：原单位因业务调整无法继续提供研发岗位，经校内外导师商议转入新单位继续实习）"
            showCount
            maxLength={1000}
          />
        </Form.Item>

        <Form.Item
          name="proofFileUrl"
          label="变更佐证材料或解约证明附件 (可选)"
        >
          <Input placeholder="请输入证明文件下载链接或附件路径，例如: /uploads/proofs/change_proof_2026.pdf" />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item
            name="newCompanyName"
            label="拟变更实习单位"
            rules={[
              { required: true, message: '请输入拟变更实习单位' },
              { min: 2, max: 128, message: '单位名称在2-128字之间' }
            ]}
          >
            <Input placeholder="请输入拟变更的接收单位全称" />
          </Form.Item>

          <Form.Item
            name="newJobPosition"
            label="拟变更实习岗位"
            rules={[
              { required: true, message: '请输入拟变更岗位' },
              { min: 2, max: 64, message: '岗位名称在2-64字之间' }
            ]}
          >
            <Input placeholder="请输入拟变更的专业对口岗位" />
          </Form.Item>
        </div>

        <Form.Item
          name="newJobAddress"
          label="拟变更工作地点"
          rules={[
            { required: true, message: '请输入详细工作地点' },
            { min: 2, max: 255, message: '工作地点在2-255字之间' }
          ]}
        >
          <Input placeholder="请输入省市区及详细门牌地址" />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
          <Form.Item
            name="newContactPerson"
            label="新企业联系人"
            rules={[{ required: true, message: '请输入新联系人姓名' }]}
          >
            <Input placeholder="姓名" />
          </Form.Item>

          <Form.Item
            name="newContactPhone"
            label="新联系人电话"
            rules={[{ required: true, message: '请输入新联系电话' }]}
          >
            <Input placeholder="手机或固定电话" />
          </Form.Item>

          <Form.Item
            name="newContactEmail"
            label="新联系人邮箱 (选填)"
          >
            <Input placeholder="电子邮箱" />
          </Form.Item>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item
            name="dateRange"
            label="新实习周期起止时间"
            rules={[{ required: true, message: '请选择拟调整的实习起止日期' }]}
          >
            <RangePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="newInternshipMode"
            label="新组织模式"
            rules={[{ required: true, message: '请选择组织模式' }]}
          >
            <Radio.Group>
              <Radio value="DISTRIBUTED">分散实习</Radio>
              <Radio value="CONCENTRATED">集中实习</Radio>
            </Radio.Group>
          </Form.Item>
        </div>

        <Form.Item
          name="newJobDuties"
          label="拟工作职责描述 (选填)"
        >
          <Input.TextArea rows={2} placeholder="简要描述新岗位的具体工作任务与学习目标" />
        </Form.Item>

        <Form.Item
          name="newAgreementFileUrl"
          label="新三方实习协议或补充协议附件 (选填)"
        >
          <Input placeholder="新协议附件地址，例如: /uploads/agreements/new_agreement.pdf" />
        </Form.Item>
      </Form>
    </Modal>
  );
};
