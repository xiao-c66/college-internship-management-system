import React from 'react';
import { Card, Row, Col, Typography, Tag, Space, Alert } from 'antd';
import { CrownOutlined, ClusterOutlined, SafetyOutlined, ControlOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const AdminDashboard: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        message="全校管理员总控工作台 (React 18.3 架构就绪)"
        description="具备全校各院系业务总览、全量任务审查、规则与基线配置、操作审计与安全监控权限。"
        type="info"
        showIcon
      />

      <Card>
        <Space direction="vertical" size="small">
          <Title level={4} style={{ margin: 0 }}>
            欢迎系统管理员，{user?.realName || user?.username}！
          </Title>
          <Space>
            <Tag color="magenta"><CrownOutlined /> 全校系统管理员</Tag>
            <Tag color="cyan">账号: {user?.username}</Tag>
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>
            在此工作台，您可以统筹全校各院系顶岗实习部署进度、调配专业规则、监控安全学习通过率、查验预警处置时效并调阅全校审计日志。
          </Paragraph>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <ClusterOutlined style={{ fontSize: 32, color: '#1890ff' }} />
              <div>
                <Text type="secondary">全校院系数</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>2 所学院</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <SafetyOutlined style={{ fontSize: 32, color: '#52c41a' }} />
              <div>
                <Text type="secondary">安全准入达标率</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>100%</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <ControlOutlined style={{ fontSize: 32, color: '#722ed1' }} />
              <div>
                <Text type="secondary">全校顶岗实习任务</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>3 批次</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <CrownOutlined style={{ fontSize: 32, color: '#faad14' }} />
              <div>
                <Text type="secondary">系统运行健康度</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>良好 (100分)</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
