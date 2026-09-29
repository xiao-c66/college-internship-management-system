import React, { useState, useEffect } from 'react';
import { Card, Button, Alert, Descriptions, Typography, Space, Tag, Divider } from 'antd';
import { CheckCircleOutlined, SyncOutlined, LinkOutlined } from '@ant-design/icons';
import request from '../../utils/request';

const { Title, Paragraph, Text } = Typography;

interface ProbeData {
  systemName?: string;
  version?: string;
  currentStage?: string;
  javaVersion?: string;
  springBootVersion?: string;
  timestamp?: string;
}

interface ProbeResponse {
  code: number;
  message: string;
  data: ProbeData;
}

export const DevDiagnosticPage: React.FC = () => {
  const [checking, setChecking] = useState(false);
  const [probeResult, setProbeResult] = useState<ProbeResponse | null>(null);

  const probeBackend = async () => {
    setChecking(true);
    try {
      const res = await request.get<any, ProbeResponse>('/health');
      setProbeResult(res);
    } catch (e: any) {
      console.error('探针探测失败', e);
      setProbeResult({
        code: 500,
        message: e?.message || '探测失败或网络异常',
        data: {}
      });
    } finally {
      setChecking(false);
    }
  };

  useEffect(() => {
    probeBackend();
  }, []);

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <Card style={{ marginBottom: 16 }}>
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Space align="center">
            <Title level={4} style={{ margin: 0 }}>
              系统基础环境与连通性诊断
            </Title>
            <Tag color="warning">开发运维专用</Tag>
          </Space>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            本页面仅供本地开发环境诊断 Spring Boot 3 后端服务、Vite 前端、接口文档与连通性使用。
          </Paragraph>
        </Space>
      </Card>

      {/* 后端连通性实时探测卡片 */}
      <Card
        title="后端 Spring Boot 3 探针状态"
        extra={
          <Button
            type="primary"
            size="small"
            icon={<SyncOutlined spin={checking} />}
            loading={checking}
            onClick={probeBackend}
          >
            重新探测服务连通性
          </Button>
        }
        style={{ marginBottom: 16 }}
      >
        {probeResult ? (
          <div>
            <Alert
              message={`探针回显 [Code: ${probeResult.code}]：${probeResult.message}`}
              type={probeResult.code === 200 ? 'success' : 'error'}
              showIcon
              icon={<CheckCircleOutlined />}
              style={{ marginBottom: 16 }}
            />
            <Descriptions bordered size="small" column={{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }}>
              <Descriptions.Item label="服务标识">{probeResult.data?.systemName || '-'}</Descriptions.Item>
              <Descriptions.Item label="骨架版本">{probeResult.data?.version || '-'}</Descriptions.Item>
              <Descriptions.Item label="当前阶段">{probeResult.data?.currentStage || '-'}</Descriptions.Item>
              <Descriptions.Item label="Java 运行时">{probeResult.data?.javaVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="Spring Boot 版本">{probeResult.data?.springBootVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="时间戳">{probeResult.data?.timestamp || '-'}</Descriptions.Item>
            </Descriptions>
          </div>
        ) : (
          <Tag color="default">尚未执行探针检测或后端未启动</Tag>
        )}
      </Card>

      {/* 开发文档与工具链接 */}
      <Card title="开发辅助工具与文档导航">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong>Knife4j 接口文档：</Text>{' '}
            <a href="http://localhost:8080/doc.html" target="_blank" rel="noreferrer">
              http://localhost:8080/doc.html <LinkOutlined />
            </a>{' '}
            <Text type="secondary">(后端启动后访问，基于 OpenAPI 3)</Text>
          </div>
          <Divider style={{ margin: '8px 0' }} />
          <div>
            <Text strong>后端健康探针端点：</Text>{' '}
            <a href="http://localhost:8080/api/v1/health" target="_blank" rel="noreferrer">
              http://localhost:8080/api/v1/health <LinkOutlined />
            </a>
          </div>
          <Divider style={{ margin: '8px 0' }} />
          <div>
            <Text strong>模拟验证码端点：</Text>{' '}
            <a href="http://localhost:8080/api/v1/auth/captcha" target="_blank" rel="noreferrer">
              http://localhost:8080/api/v1/auth/captcha <LinkOutlined />
            </a>
          </div>
        </Space>
      </Card>
    </div>
  );
};
