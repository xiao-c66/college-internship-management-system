import React from 'react';
import { Card, Result, Button, Typography, Tag, Space } from 'antd';
import { ToolOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const { Paragraph, Text } = Typography;

interface ModulePlaceholderProps {
  title: string;
  phase: string;
  description: string;
}

export const ModulePlaceholder: React.FC<ModulePlaceholderProps> = ({ title, phase, description }) => {
  const navigate = useNavigate();

  return (
    <Card style={{ minHeight: '75vh', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <Result
        icon={<ToolOutlined style={{ color: '#1890ff' }} />}
        title={
          <Space>
            <span>{title}</span>
            <Tag color="processing">{phase}</Tag>
          </Space>
        }
        subTitle={
          <div style={{ maxWidth: 600, margin: '0 auto', textAlign: 'center' }}>
            <Paragraph type="secondary">{description}</Paragraph>
            <Text type="secondary" style={{ fontSize: 13 }}>
              当前处于 React 渐进式迁移第一阶段（基础能力与架构搭建）。该业务模块将在后续阶段按计划无损迁移替换。
            </Text>
          </div>
        }
        extra={
          <Button type="primary" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
            返回工作台
          </Button>
        }
      />
    </Card>
  );
};
