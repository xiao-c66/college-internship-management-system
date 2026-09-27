import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Button, Typography, Space, Tag, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined, BankOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { loginApi, getCaptchaApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';
import type { LoginParams } from '../../api/auth';

const { Title, Text, Paragraph } = Typography;

export const LoginPage: React.FC = () => {
  const [form] = Form.useForm<LoginParams>();
  const [loading, setLoading] = useState(false);
  const [captchaCode, setCaptchaCode] = useState('');
  const [captchaKey, setCaptchaKey] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const { setAuth, isAuthenticated, userType } = useAuthStore();

  const fetchCaptcha = async () => {
    try {
      const res: any = await getCaptchaApi();
      if (res.code === 200 && res.data) {
        const code = res.data.captchaCode || res.data.text || '';
        const key = res.data.captchaKey || '';
        setCaptchaCode(code);
        setCaptchaKey(key);
        form.setFieldValue('captcha', code);
      } else {
        const fallbackCode = String(Math.floor(1000 + Math.random() * 9000));
        setCaptchaCode(fallbackCode);
        form.setFieldValue('captcha', fallbackCode);
      }
    } catch {
      const fallbackCode = String(Math.floor(1000 + Math.random() * 9000));
      setCaptchaCode(fallbackCode);
      form.setFieldValue('captcha', fallbackCode);
    }
  };

  useEffect(() => {
    if (isAuthenticated) {
      redirectToDashboard(userType);
    } else {
      fetchCaptcha();
    }
  }, [isAuthenticated]);

  const redirectToDashboard = (type: string | null) => {
    const from = (location.state as any)?.from?.pathname;
    const isDashboardPath = from?.startsWith('/dashboard');
    if (from && from !== '/login' && !isDashboardPath) {
      navigate(from, { replace: true });
      return;
    }
    if (type === 'STUDENT') navigate('/dashboard/student', { replace: true });
    else if (type === 'TEACHER') navigate('/dashboard/teacher', { replace: true });
    else if (type === 'DEPT_ADMIN') navigate('/dashboard/dept', { replace: true });
    else if (type === 'SYS_ADMIN') navigate('/dashboard/admin', { replace: true });
    else navigate('/dashboard/student', { replace: true });
  };

  const onFinish = async (values: LoginParams) => {
    setLoading(true);
    try {
      const res: any = await loginApi({
        username: values.username.trim(),
        password: values.password,
        captcha: values.captcha ? values.captcha.trim() : captchaCode,
        captchaKey: captchaKey || undefined
      });

      if (res.code === 200 && res.data) {
        const loginVO = res.data;
        const user = {
          userId: loginVO.userId,
          username: loginVO.username,
          realName: loginVO.realName,
          userType: loginVO.userType,
          roleCode: loginVO.roleCode,
          deptId: loginVO.deptId,
          deptName: loginVO.deptName,
          permissions: loginVO.permissions || []
        };
        setAuth(loginVO.token, user);
        message.success(`欢迎登录，${user.realName || user.username}！`);
        redirectToDashboard(user.userType);
      } else {
        message.error(res.message || '登录认证失败，请检查账号密码');
        fetchCaptcha();
      }
    } catch (err: any) {
      message.error(err.message || '登录异常，请稍后重试');
      fetchCaptcha();
    } finally {
      setLoading(false);
    }
  };

  const fillDemoAccount = (username: string) => {
    if (!import.meta.env.DEV) return;
    form.setFieldsValue({
      username,
      password: '123456',
      captcha: captchaCode
    });
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #1890ff 0%, #001529 100%)',
        padding: '20px'
      }}
    >
      <Card
        style={{
          width: 440,
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.25)',
          borderRadius: 12
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <BankOutlined style={{ fontSize: 42, color: '#1890ff' }} />
          <Title level={3} style={{ marginTop: 12, marginBottom: 4 }}>
            高校实习全过程管理系统
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 13 }}>
            College Internship Management System (React 18.3)
          </Paragraph>
          <Tag color="processing">多角色前后端分离与权限闭环</Tag>
        </div>

        <Form
          form={form}
          name="login"
          layout="vertical"
          onFinish={onFinish}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入账号/学号/工号' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: 'rgba(0,0,0,.25)' }} />}
              placeholder="请输入学号/工号/管理账号"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入登录密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'rgba(0,0,0,.25)' }} />}
              placeholder="请输入登录密码"
              size="large"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 24 }}>
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item
                name="captcha"
                noStyle
                rules={[{ required: true, message: '请输入图形验证码' }]}
              >
                <Input
                  prefix={<SafetyCertificateOutlined style={{ color: 'rgba(0,0,0,.25)' }} />}
                  placeholder="请输入验证码"
                  size="large"
                />
              </Form.Item>
              <Button
                size="large"
                style={{
                  width: 110,
                  letterSpacing: 4,
                  fontWeight: 'bold',
                  color: '#1890ff',
                  backgroundColor: '#f0f5ff'
                }}
                onClick={fetchCaptcha}
                title="点击刷新验证码"
              >
                {captchaCode || '----'}
              </Button>
            </Space.Compact>
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={loading}
            >
              {loading ? '正在安全认证...' : '立即登录系统'}
            </Button>
          </Form.Item>
        </Form>

        {/* 仅在开发/联调测试环境展示快捷填入，生产环境自动禁用 */}
        {import.meta.env.DEV && (
          <div style={{ marginTop: 8, padding: 12, backgroundColor: '#fafafa', borderRadius: 8, border: '1px solid #f0f0f0' }}>
            <div style={{ fontSize: 12, color: '#ff4d4f', marginBottom: 8, textAlign: 'center', fontWeight: 'bold' }}>
              [仅限开发/联调测试环境] 演示账号快捷切换 (生产环境自动禁用)
            </div>
            <Space wrap size={[8, 8]} style={{ justifyContent: 'center', width: '100%' }}>
              <Button size="small" onClick={() => fillDemoAccount('student')}>
                学生 (student)
              </Button>
              <Button size="small" onClick={() => fillDemoAccount('teacher')}>
                教师 (teacher)
              </Button>
              <Button size="small" onClick={() => fillDemoAccount('deptadmin')}>
                院管 (deptadmin)
              </Button>
              <Button size="small" danger onClick={() => fillDemoAccount('admin')}>
                校级 (admin)
              </Button>
            </Space>
          </div>
        )}

        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            基于 Spring Boot 3 + MySQL 真实 JWT 认证架构
          </Text>
        </div>
      </Card>
    </div>
  );
};
