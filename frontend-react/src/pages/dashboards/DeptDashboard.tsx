import React from 'react';
import { Card, Row, Col, Typography, Tag, Space, Alert } from 'antd';
import { SolutionOutlined, AppstoreOutlined, CheckCircleOutlined, AlertOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';

const { Title, Paragraph, Text } = Typography;

export const DeptDashboard: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        message="院系负责人工作台 (React 18.3 架构就绪)"
        description="严格执行院系数据范围隔离：管理本院任务、分配带教导师、发起中期抽样与闭环预警工单。"
        type="info"
        showIcon
      />

      <Card>
        <Space direction="vertical" size="small">
          <Title level={4} style={{ margin: 0 }}>
            欢迎院系负责人，{user?.realName || user?.username}！
          </Title>
          <Space>
            <Tag color="orange"><SolutionOutlined /> 院系负责人</Tag>
            <Tag color="cyan">账号: {user?.username}</Tag>
            {user?.deptName && <Tag color="geekblue">{user.deptName}</Tag>}
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>
            在此工作台，您可以创建并发布专业顶岗实习任务、导入学生与指派教师、终审实习申报、组织中期检查方案与抽样、处置预警工单并把关归档电子卷宗。
          </Paragraph>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <AppstoreOutlined style={{ fontSize: 32, color: '#1890ff' }} />
              <div>
                <Text type="secondary">本院进行中任务</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>2 批次</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <CheckCircleOutlined style={{ fontSize: 32, color: '#52c41a' }} />
              <div>
                <Text type="secondary">本院实习申报终审</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>100% 办结</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <AlertOutlined style={{ fontSize: 32, color: '#f5222d' }} />
              <div>
                <Text type="secondary">风险预警工单</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>全部闭环</div>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Space align="center">
              <CheckCircleOutlined style={{ fontSize: 32, color: '#722ed1' }} />
              <div>
                <Text type="secondary">卷宗归档完整率</Text>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>100%</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
