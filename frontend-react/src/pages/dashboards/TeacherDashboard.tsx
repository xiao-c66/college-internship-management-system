import React from 'react';
import { Card, Row, Col, Typography, Tag, Space, Alert } from 'antd';
import { ReadOutlined, TeamOutlined, AuditOutlined, FormOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const TeacherDashboard: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        message="指导教师工作台 (React 18.3 架构就绪)"
        description="严格执行教师带教管辖隔离：仅允许批阅、指导本人负责的学生业务。"
        type="info"
        showIcon
      />

      <Card>
        <Space direction="vertical" size="small">
          <Title level={4} style={{ margin: 0 }}>
            欢迎李老师，{user?.realName || user?.username}！
          </Title>
          <Space>
            <Tag color="green"><ReadOutlined /> 指导教师</Tag>
            <Tag color="cyan">工号: {user?.username}</Tag>
            {user?.deptName && <Tag color="geekblue">{user.deptName}</Tag>}
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>
            在此工作台，您可以初审负责学生的顶岗实习申报、开展日常过程指导、批阅与退回周报、下达中期限期整改通知以及录入学生五维综合成绩。
          </Paragraph>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <TeamOutlined style={{ fontSize: 32, color: '#1890ff' }} />
              <div>
                <Text type="secondary">负责带教学生</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>1 人</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <AuditOutlined style={{ fontSize: 32, color: '#52c41a' }} />
              <div>
                <Text type="secondary">待审核申报</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>0 项待办</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <FormOutlined style={{ fontSize: 32, color: '#722ed1' }} />
              <div>
                <Text type="secondary">周报批阅进度</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>100% 已审</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <AuditOutlined style={{ fontSize: 32, color: '#faad14' }} />
              <div>
                <Text type="secondary">五维成绩录入</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>已发布</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
