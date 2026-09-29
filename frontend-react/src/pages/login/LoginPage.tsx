import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Button, Typography, Space, Tag, message, Modal, Alert } from 'antd';
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

  // 忘记密码状态
  const [forgotModalVisible, setForgotModalVisible] = useState(false);
  const [forgotForm] = Form.useForm();
  const [sendingCode, setSendingCode] = useState(false);
  const [codeCountdown, setCodeCountdown] = useState(0);
  const [submittingForgot, setSubmittingForgot] = useState(false);

  // 强制首次改密状态
  const [forceChangePwdVisible, setForceChangePwdVisible] = useState(false);
  const [forceChangePwdForm] = Form.useForm();
  const [submittingForceChange, setSubmittingForceChange] = useState(false);
  const [pendingLoginVO, setPendingLoginVO] = useState<any>(null);

  useEffect(() => {
    if (isAuthenticated && !pendingLoginVO && !forceChangePwdVisible) {
      redirectToDashboard(userType);
    } else if (!isAuthenticated) {
      fetchCaptcha();
    }
  }, [isAuthenticated, pendingLoginVO, forceChangePwdVisible]);

  useEffect(() => {
    let timer: any = null;
    if (codeCountdown > 0) {
      timer = setTimeout(() => setCodeCountdown(codeCountdown - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [codeCountdown]);

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

        // 首次登录改密拦截机制 (API-125 / 安全规范与环境策略区分)
        if (loginVO.mustChangePassword) {
          localStorage.setItem('token', loginVO.token);
          setPendingLoginVO({ ...loginVO, user });
          forceChangePwdForm.setFieldValue('oldPassword', values.password);
          setForceChangePwdVisible(true);
          if (loginVO.forcePasswordChange !== false) {
            message.warning('检测到您的账号为初始状态或密码已被重置，请首次登录修改密码！');
          } else {
            message.info('建议修改初始密码以提升账号安全性，测试环境可选择“暂不修改，直接进入”');
          }
          return;
        }

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

  // 发送找回密码验证码
  const handleSendForgotCode = async () => {
    try {
      const username = forgotForm.getFieldValue('username');
      const target = forgotForm.getFieldValue('target');
      if (!username || !target) {
        message.warning('请先输入账号及已绑定的手机号/邮箱');
        return;
      }
      setSendingCode(true);
      const { sendForgotCodeApi } = await import('../../api/user');
      await sendForgotCodeApi({ username: username.trim(), target: target.trim() });
      message.success('验证码已发送至安全接收端，5分钟内有效');
      setCodeCountdown(60);
    } catch (e: any) {
      message.error(e?.message || '发送验证码失败');
    } finally {
      setSendingCode(false);
    }
  };

  // 提交重置找回密码
  const handleResetForgotSubmit = async () => {
    try {
      const values = await forgotForm.validateFields();
      setSubmittingForgot(true);
      const { resetForgotPasswordApi } = await import('../../api/user');
      await resetForgotPasswordApi({
        username: values.username.trim(),
        target: values.target.trim(),
        code: values.code.trim(),
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword
      });
      message.success('密码重置成功！请使用新密码登录');
      setForgotModalVisible(false);
      forgotForm.resetFields();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '密码重置失败');
    } finally {
      setSubmittingForgot(false);
    }
  };

  // 提交首次登录强制改密
  const handleForceChangeSubmit = async () => {
    try {
      const values = await forceChangePwdForm.validateFields();
      setSubmittingForceChange(true);
      const { changePasswordApi } = await import('../../api/user');
      await changePasswordApi({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword
      });
      message.success('新密码设置成功！已解除初始保护状态');
      setForceChangePwdVisible(false);
      if (pendingLoginVO) {
        setAuth(pendingLoginVO.token, pendingLoginVO.user);
        redirectToDashboard(pendingLoginVO.userType);
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '设置新密码失败');
    } finally {
      setSubmittingForceChange(false);
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

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: -12, marginBottom: 16 }}>
            <Button
              type="link"
              size="small"
              style={{ padding: 0, fontSize: 13 }}
              onClick={() => setForgotModalVisible(true)}
            >
              忘记密码？
            </Button>
          </div>

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

        {/* 忘记密码安全找回模态框 */}
        <Modal
          title="找回登录密码"
          open={forgotModalVisible}
          onCancel={() => setForgotModalVisible(false)}
          footer={[
            <Button key="cancel" onClick={() => setForgotModalVisible(false)}>
              取消
            </Button>,
            <Button
              key="submit"
              type="primary"
              loading={submittingForgot}
              onClick={handleResetForgotSubmit}
            >
              确认重置密码
            </Button>
          ]}
        >
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="安全凭证核验须知"
            description="根据教育部与网络安全合规规范，本系统严禁仅凭学号找回密码。找回密码必须通过绑定的安全手机号或邮箱接收有时效的动态验证码。若未绑定或遗失，请联系院系管理员或超级管理员重置。"
          />
          <Form form={forgotForm} layout="vertical">
            <Form.Item
              name="username"
              label="登录账号 / 学号 / 工号"
              rules={[{ required: true, message: '请输入账号' }]}
            >
              <Input placeholder="请输入用户名或学号" />
            </Form.Item>

            <Form.Item
              name="target"
              label="绑定的手机号或电子邮箱"
              rules={[{ required: true, message: '请输入绑定的手机号或邮箱' }]}
            >
              <Input placeholder="输入该账号绑定的安全手机或邮箱" />
            </Form.Item>

            <Form.Item label="安全验证码" required style={{ marginBottom: 16 }}>
              <Space.Compact style={{ width: '100%' }}>
                <Form.Item
                  name="code"
                  noStyle
                  rules={[{ required: true, message: '请输入6位验证码' }]}
                >
                  <Input placeholder="请输入6位验证码" maxLength={6} />
                </Form.Item>
                <Button
                  onClick={handleSendForgotCode}
                  disabled={codeCountdown > 0}
                  loading={sendingCode}
                >
                  {codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码'}
                </Button>
              </Space.Compact>
            </Form.Item>

            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 8, message: '密码长度至少8位' },
                {
                  pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/,
                  message: '密码必须包含大写字母、小写字母、数字和特殊字符'
                }
              ]}
            >
              <Input.Password placeholder="至少8位，包含大写、小写、数字和特殊字符" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认新密码"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请再次输入新密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的新密码不一致'));
                  }
                })
              ]}
            >
              <Input.Password placeholder="请再次输入新密码" />
            </Form.Item>
          </Form>
        </Modal>

        {/* 首次登录 / 管理员重置后 修改密码模态框 (按环境区分：生产不可关闭，测试/开发环境可跳过) */}
        <Modal
          title={
            <Space>
              <SafetyCertificateOutlined style={{ color: pendingLoginVO?.forcePasswordChange === false ? '#1890ff' : '#faad14' }} />
              <span>{pendingLoginVO?.forcePasswordChange === false ? '建议修改初始密码 (测试环境可跳过)' : '首次登录安全修改密码'}</span>
            </Space>
          }
          open={forceChangePwdVisible}
          closable={pendingLoginVO?.forcePasswordChange === false}
          maskClosable={pendingLoginVO?.forcePasswordChange === false}
          onCancel={() => {
            if (pendingLoginVO?.forcePasswordChange === false) {
              setForceChangePwdVisible(false);
              if (pendingLoginVO) {
                setAuth(pendingLoginVO.token, pendingLoginVO.user);
                redirectToDashboard(pendingLoginVO.userType);
              }
            }
          }}
          footer={
            pendingLoginVO?.forcePasswordChange === false
              ? [
                  <Button
                    key="skip"
                    onClick={() => {
                      setForceChangePwdVisible(false);
                      if (pendingLoginVO) {
                        setAuth(pendingLoginVO.token, pendingLoginVO.user);
                        redirectToDashboard(pendingLoginVO.userType);
                      }
                    }}
                  >
                    暂不修改，直接进入
                  </Button>,
                  <Button
                    key="submit"
                    type="primary"
                    loading={submittingForceChange}
                    onClick={handleForceChangeSubmit}
                  >
                    立即修改
                  </Button>
                ]
              : [
                  <Button
                    key="submit"
                    type="primary"
                    loading={submittingForceChange}
                    onClick={handleForceChangeSubmit}
                  >
                    完成修改并进入系统
                  </Button>
                ]
          }
        >
          <Alert
            type={pendingLoginVO?.forcePasswordChange === false ? 'info' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            message={pendingLoginVO?.forcePasswordChange === false ? '密码安全建议 (开发/测试模式)' : '账号安全保护机制触发'}
            description={
              pendingLoginVO?.forcePasswordChange === false
                ? '检测到您的账号为初始状态。当前环境未开启强制改密策略，建议您修改密码，或点击“暂不修改，直接进入”。'
                : '您的账号处于初始导入或管理员重置状态，根据网络安全等级保护与高校数据安全管理办法，首次登录系统必须修改初始临时密码。'
            }
          />
          <Form form={forceChangePwdForm} layout="vertical">
            <Form.Item
              name="oldPassword"
              label="初始临时密码"
              rules={[{ required: true, message: '请输入管理员提供的初始临时密码' }]}
            >
              <Input.Password placeholder="请输入初始临时密码" />
            </Form.Item>

            <Form.Item
              name="newPassword"
              label="新密码 (私密保存)"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 8, message: '密码长度至少8位' },
                {
                  pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/,
                  message: '密码必须包含大写字母、小写字母、数字和特殊字符'
                }
              ]}
            >
              <Input.Password placeholder="至少8位，包含大写、小写、数字和特殊字符" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认新密码"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请再次输入新密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的新密码不一致'));
                  }
                })
              ]}
            >
              <Input.Password placeholder="请再次输入新密码" />
            </Form.Item>
          </Form>
        </Modal>

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
