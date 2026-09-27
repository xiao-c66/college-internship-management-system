import React from 'react';
import { Card, Row, Col, Typography, Tag, Space, Alert, Button } from 'antd';
import { UserOutlined, SafetyCertificateOutlined, FileTextOutlined, WarningOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const StudentDashboard: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        message="学生端工作台 (React 18.3 架构就绪)"
        description="系统基础能力（JWT Token、Zustand 状态、Ant Design 5 布局、路由与 RBAC 权限）已成功挂载。"
        type="info"
        showIcon
      />

      <Card>
        <Space direction="vertical" size="small">
          <Title level={4} style={{ margin: 0 }}>
            欢迎同学，{user?.realName || user?.username}！
          </Title>
          <Space>
            <Tag color="blue"><UserOutlined /> 学生角色</Tag>
            <Tag color="cyan">学号: {user?.username}</Tag>
            {user?.deptName && <Tag color="geekblue">{user.deptName}</Tag>}
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>
            在此工作台，您可以查看本人被纳入的顶岗实习任务、完成安全教育准入考试、填报实习申请、提交每周顶岗周报以及查阅最终五维综合成绩。
          </Paragraph>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <SafetyCertificateOutlined style={{ fontSize: 32, color: '#52c41a' }} />
              <div>
                <Text type="secondary">安全准入状态</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>已签署达标</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <FileTextOutlined style={{ fontSize: 32, color: '#1890ff' }} />
              <div>
                <Text type="secondary">实习申报状态</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>审核通过</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <FileTextOutlined style={{ fontSize: 32, color: '#722ed1' }} />
              <div>
                <Text type="secondary">周报提交情况</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>1 / 1 篇已审</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <WarningOutlined style={{ fontSize: 32, color: '#faad14' }} />
              <div>
                <Text type="secondary">整改与预警</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>无活跃预警</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
