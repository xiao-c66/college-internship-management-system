import React, { useState } from 'react';
import { Layout, Menu, Button, Space, Tag, Dropdown, Typography, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  BankOutlined,
  UserOutlined,
  ReadOutlined,
  SolutionOutlined,
  CrownOutlined,
  LogoutOutlined,
  AppstoreOutlined,
  SafetyCertificateOutlined,
  FileDoneOutlined,
  FormOutlined,
  AlertOutlined,
  TrophyOutlined,
  FileTextOutlined
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/useAuthStore';
import { logoutApi } from '../api/auth';
import type { UserType } from '../types/auth';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

export const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { user, userType, logout, hasRole } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();

  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch (e) {
      console.warn('Logout API error', e);
    }
    logout();
    navigate('/login', { replace: true });
  };

  const roleTagConfig: Record<UserType, { color: string; label: string; icon: React.ReactNode }> = {
    STUDENT: { color: 'blue', label: '学生', icon: <UserOutlined /> },
    TEACHER: { color: 'green', label: '指导教师', icon: <ReadOutlined /> },
    DEPT_ADMIN: { color: 'orange', label: '院系负责人', icon: <SolutionOutlined /> },
    SYS_ADMIN: { color: 'magenta', label: '系统管理员', icon: <CrownOutlined /> }
  };

  const currentRoleInfo = userType ? roleTagConfig[userType] : null;

  // 动态根据角色构建侧边栏菜单项
  const menuItems: MenuProps['items'] = [
    {
      key: 'group-workbench',
      type: 'group',
      label: '专属工作台',
      children: [
        ...(hasRole('STUDENT')
          ? [{ key: '/dashboard/student', icon: <UserOutlined />, label: '学生端工作台' }]
          : []),
        ...(hasRole('TEACHER')
          ? [{ key: '/dashboard/teacher', icon: <ReadOutlined />, label: '教师端工作台' }]
          : []),
        ...(hasRole('DEPT_ADMIN')
          ? [{ key: '/dashboard/dept', icon: <SolutionOutlined />, label: '院系负责人工作台' }]
          : []),
        ...(hasRole('SYS_ADMIN')
          ? [{ key: '/dashboard/admin', icon: <CrownOutlined />, label: '全校管理员工作台' }]
          : [])
      ]
    },
    {
      key: 'group-business',
      type: 'group',
      label: '实习业务全流程',
      children: [
        {
          key: '/task',
          icon: <AppstoreOutlined />,
          label: '实习任务管理',
          disabled: false
        },
        {
          key: '/safety',
          icon: <SafetyCertificateOutlined />,
          label: '安全教育与准入',
          disabled: false
        },
        {
          key: '/apply',
          icon: <FileDoneOutlined />,
          label: '实习申报管理',
          disabled: false
        },
        {
          key: '/weekly',
          icon: <FormOutlined />,
          label: '周报与过程指导',
          disabled: false
        },
        {
          key: '/inspect',
          icon: <SolutionOutlined />,
          label: '中期检查与整改',
          disabled: false
        },
        {
          key: '/warn',
          icon: <AlertOutlined />,
          label: '风险预警中心',
          disabled: false
        },
        {
          key: '/material/manage',
          icon: <FileTextOutlined />,
          label: '阶段材料与总结',
          disabled: false
        },
        {
          key: '/score',
          icon: <TrophyOutlined />,
          label: '五维成绩评定',
          disabled: false
        }
      ]
    }
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  const userDropdownItems: MenuProps['items'] = [
    {
      key: 'profile',
      label: (
        <div>
          <div><strong>{user?.realName || user?.username}</strong></div>
          <Text type="secondary" style={{ fontSize: 12 }}>{user?.username} ({currentRoleInfo?.label})</Text>
        </div>
      ),
      disabled: true
    },
    {
      type: 'divider'
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout
    }
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 顶部通栏 Header */}
      <Header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '0 24px',
          background: '#001529',
          color: '#fff',
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          zIndex: 10
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <BankOutlined style={{ fontSize: 24, color: '#1890ff' }} />
          <div>
            <span style={{ fontSize: 18, fontWeight: 'bold', color: '#fff' }}>
              高校实习全过程管理系统
            </span>
            <span style={{ fontSize: 12, color: 'rgba(255,255,255,0.65)', marginLeft: 8 }}>
              React 18.3 迁移版
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          {currentRoleInfo && (
            <Tag color={currentRoleInfo.color} style={{ margin: 0, padding: '2px 8px' }}>
              {currentRoleInfo.icon} {currentRoleInfo.label}
            </Tag>
          )}

          {user?.deptName && (
            <Tag color="geekblue" style={{ margin: 0 }}>
              {user.deptName}
            </Tag>
          )}

          <Dropdown menu={{ items: userDropdownItems }} placement="bottomRight">
            <Button type="text" style={{ color: '#fff', padding: '4px 8px' }}>
              <Space>
                <UserOutlined />
                <span>{user?.realName || user?.username}</span>
              </Space>
            </Button>
          </Dropdown>
        </div>
      </Header>

      <Layout>
        {/* 左侧可折叠 Sider */}
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={(val) => setCollapsed(val)}
          width={240}
          style={{
            background: token.colorBgContainer,
            boxShadow: '2px 0 6px rgba(0,21,41,0.08)'
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
            onClick={handleMenuClick}
          />
        </Sider>

        {/* 主内容区域 Content */}
        <Content
          style={{
            margin: '16px 20px',
            padding: 20,
            background: '#f5f7fa',
            minHeight: 280,
            overflowY: 'auto'
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};
