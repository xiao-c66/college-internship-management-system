import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

export const ForbiddenPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '60px 0' }}>
      <Result
        status="403"
        title="403 访问被拒绝"
        subTitle="抱歉，您的角色或当前数据管辖范围未获得该模块的访问权限。"
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            返回工作台首页
          </Button>
        }
      />
    </div>
  );
};
