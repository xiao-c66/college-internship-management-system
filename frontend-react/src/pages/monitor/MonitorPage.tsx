import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Form,
  Select,
  Input,
  Tag,
  Space,
  Modal,
  Row,
  Col,
  Alert,
  Progress,
  Descriptions,
  Tabs,
  Typography,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  DeleteOutlined,
  SearchOutlined,
  UserDeleteOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ThunderboltOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../store/useAuthStore';
import {
  getServerMetrics,
  getCacheMetrics,
  clearCache,
  getSlowSqlMetrics,
  getLoginLogs,
  getOperationLogs,
  getActiveTokens,
  kickToken,
  type ServerMonitorVO,
  type CacheMonitorVO,
  type SlowSqlMonitorVO,
  type SlowCallRecordVO,
  type SysLoginLogVO,
  type SysOperationLogVO,
  type SysActiveTokenVO
} from '../../api/monitor';

const { Title, Text } = Typography;

export const MonitorPage: React.FC = () => {
  const { user } = useAuthStore();
  const currentUserId = user?.userId || 0;

  const [activeTab, setActiveTab] = useState('server');
  const [securitySubTab, setSecuritySubTab] = useState('tokens');
  const [loading, setLoading] = useState(false);
  const [clearingCache, setClearingCache] = useState(false);

  // 指标数据
  const [serverData, setServerData] = useState<ServerMonitorVO | null>(null);
  const [cacheData, setCacheData] = useState<CacheMonitorVO | null>(null);
  const [slowData, setSlowData] = useState<SlowSqlMonitorVO | null>(null);
  const [tokenList, setTokenList] = useState<SysActiveTokenVO[]>([]);
  const [loginLogs, setLoginLogs] = useState<SysLoginLogVO[]>([]);
  const [operationLogs, setOperationLogs] = useState<SysOperationLogVO[]>([]);

  // 登录审计筛选
  const [loginFilterForm] = Form.useForm();

  // 初始化加载当前选中的 tab
  useEffect(() => {
    fetchServerData();
  }, []);

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'server') fetchServerData();
    else if (key === 'cache') fetchCacheData();
    else if (key === 'slow') fetchSlowData();
    else if (key === 'security') fetchSecurityData();
  };

  const refreshCurrentTab = () => {
    handleTabChange(activeTab);
  };

  // 1. 服务器指标
  const fetchServerData = async () => {
    setLoading(true);
    try {
      const res: any = await getServerMetrics();
      if (res && res.code === 200) {
        setServerData(res.data);
      }
    } catch (e: any) {
      message.error(e?.message || '获取服务器监控指标失败');
    } finally {
      setLoading(false);
    }
  };

  // 2. 本地缓存指标
  const fetchCacheData = async () => {
    setLoading(true);
    try {
      const res: any = await getCacheMetrics();
      if (res && res.code === 200) {
        setCacheData(res.data);
      }
    } catch (e: any) {
      message.error(e?.message || '获取Caffeine缓存指标失败');
    } finally {
      setLoading(false);
    }
  };

  // 清空缓存 (前端二次确认交互，测试阶段不主动触发写入)
  const handleClearCache = () => {
    Modal.confirm({
      title: '清空缓存确认',
      content: '确认清空本地全部 Caffeine 缓存？此操作将使已缓存的系统配置立即重新加载。',
      okText: '确认清空',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setClearingCache(true);
        try {
          const res: any = await clearCache();
          if (res && res.code === 200) {
            message.success('本地 Caffeine 缓存已成功清空！');
            fetchCacheData();
          } else {
            message.error(res?.message || '清空缓存失败');
          }
        } catch (e: any) {
          message.error(e?.message || '清空缓存失败');
        } finally {
          setClearingCache(false);
        }
      }
    });
  };

  // 3. 慢调用指标
  const fetchSlowData = async () => {
    setLoading(true);
    try {
      const res: any = await getSlowSqlMetrics();
      if (res && res.code === 200) {
        setSlowData(res.data);
      }
    } catch (e: any) {
      message.error(e?.message || '获取慢调用度量看板失败');
    } finally {
      setLoading(false);
    }
  };

  // 4. 安全审计与会话数据
  const fetchSecurityData = () => {
    fetchTokens();
    fetchLoginLogs();
    fetchOperationLogs();
  };

  const fetchTokens = async () => {
    setLoading(true);
    try {
      const res: any = await getActiveTokens();
      if (res && res.code === 200) {
        setTokenList(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取在线会话列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 强制踢下线 (前端二次确认交互，测试阶段不主动触发写入)
  const handleKickToken = (record: SysActiveTokenVO) => {
    Modal.confirm({
      title: '强制踢下线确认',
      content: `确定强制踢下线用户 [${record.username} - ${record.realName}]？踢出后其历史签发的所有 JWT 令牌将即时失效返回 401。`,
      okText: '确认踢出',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res: any = await kickToken(record.userId);
          if (res && res.code === 200) {
            message.success(`用户 [${record.username}] 会话已被强制踢下线！`);
            fetchTokens();
          } else {
            message.error(res?.message || '强制踢下线失败');
          }
        } catch (e: any) {
          message.error(e?.message || '强制踢下线失败');
        }
      }
    });
  };

  const fetchLoginLogs = async () => {
    try {
      const values = loginFilterForm.getFieldsValue();
      const res: any = await getLoginLogs({
        username: values.username || undefined,
        status: values.status !== undefined ? values.status : undefined
      });
      if (res && res.code === 200) {
        setLoginLogs(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取登录审计流水失败');
    }
  };

  const fetchOperationLogs = async () => {
    try {
      const res: any = await getOperationLogs();
      if (res && res.code === 200) {
        setOperationLogs(res.data || []);
      }
    } catch (e: any) {
      message.error(e?.message || '获取操作审计流水失败');
    }
  };

  // 辅助计算函数
  const calcJvmPercent = (data: ServerMonitorVO | null) => {
    if (!data || !data.jvmTotalMemoryMB) return 0;
    return Math.min(100, Math.round((data.jvmUsedMemoryMB / data.jvmTotalMemoryMB) * 100));
  };

  const calcDiskPercent = (data: ServerMonitorVO | null) => {
    if (!data || !data.totalDiskSpaceGB) return 0;
    return Math.min(100, Math.round((data.usedDiskSpaceGB / data.totalDiskSpaceGB) * 100));
  };

  const formatUptime = (seconds?: number) => {
    if (!seconds) return '-';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h}小时 ${m}分 ${s}秒`;
  };

  const formatDateTime = (val?: string) => {
    if (!val) return '-';
    return val.replace('T', ' ').substring(0, 19);
  };

  const getUserTypeTag = (type: string) => {
    if (type === 'SYS_ADMIN') return 'error';
    if (type === 'DEPT_ADMIN') return 'warning';
    if (type === 'TEACHER') return 'processing';
    return 'default';
  };

  // 列定义: 慢调用流水
  const slowColumns: ColumnsType<SlowCallRecordVO> = [
    { title: '#', dataIndex: 'id', width: 60, align: 'center' },
    {
      title: '请求 URI',
      dataIndex: 'uri',
      minWidth: 200,
      render: (val: string) => (
        <span style={{ fontFamily: 'Consolas, Monaco, monospace' }}>{val}</span>
      )
    },
    {
      title: '方法',
      dataIndex: 'method',
      width: 80,
      align: 'center',
      render: (val: string) => <Tag color="default">{val}</Tag>
    },
    {
      title: '耗时',
      dataIndex: 'costMs',
      width: 110,
      align: 'center',
      render: (val: number) => (
        <Tag color="error" style={{ fontFamily: 'Consolas, Monaco, monospace' }}>
          {val} ms
        </Tag>
      )
    },
    { title: '操作人员', dataIndex: 'operatorName', width: 120, align: 'center' },
    { title: '客户端 IP', dataIndex: 'clientIp', width: 130, align: 'center' },
    {
      title: '发生时间',
      dataIndex: 'timestamp',
      width: 170,
      align: 'center',
      render: (val: string) => formatDateTime(val)
    }
  ];

  // 列定义: 在线活跃会话
  const tokenColumns: ColumnsType<SysActiveTokenVO> = [
    { title: '用户ID', dataIndex: 'userId', width: 80, align: 'center' },
    {
      title: '用户名',
      dataIndex: 'username',
      width: 130,
      render: (val: string) => <strong>{val}</strong>
    },
    { title: '真实姓名', dataIndex: 'realName', width: 120 },
    {
      title: '用户类型',
      dataIndex: 'userType',
      width: 120,
      align: 'center',
      render: (val: string) => <Tag color={getUserTypeTag(val)}>{val}</Tag>
    },
    {
      title: '所属院系',
      dataIndex: 'deptName',
      minWidth: 140,
      render: (val: string) => val || '全校/校级管理'
    },
    {
      title: 'Token版本',
      dataIndex: 'tokenVersion',
      width: 110,
      align: 'center',
      render: (val: number) => (
        <Tag color="default" style={{ fontFamily: 'Consolas, Monaco, monospace' }}>
          v{val}
        </Tag>
      )
    },
    {
      title: '最近登录时间',
      dataIndex: 'lastLoginTime',
      width: 170,
      align: 'center',
      render: (val: string) => formatDateTime(val)
    },
    {
      title: '登录IP',
      dataIndex: 'lastLoginIp',
      width: 130,
      align: 'center',
      render: (val: string) => val || '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      align: 'center',
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="primary"
          danger
          size="small"
          icon={<UserDeleteOutlined />}
          disabled={record.userId === 1 || record.userId === currentUserId}
          onClick={() => handleKickToken(record)}
        >
          强制踢下线
        </Button>
      )
    }
  ];

  // 列定义: 登录安全审计
  const loginColumns: ColumnsType<SysLoginLogVO> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '登录账号', dataIndex: 'operatorName', width: 130 },
    {
      title: '类型',
      dataIndex: 'businessType',
      width: 90,
      align: 'center',
      render: (val: string) => <Tag color={val === 'LOGIN' ? 'blue' : 'default'}>{val}</Tag>
    },
    { title: '客户端IP', dataIndex: 'operIp', width: 130, align: 'center' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      align: 'center',
      render: (status: number) => (
        <Tag color={status === 1 ? 'success' : 'error'}>
          {status === 1 ? '成功' : '失败'}
        </Tag>
      )
    },
    {
      title: '失败简因',
      dataIndex: 'errorMsg',
      minWidth: 180,
      ellipsis: true,
      render: (val: string, record) => (
        <span style={{ color: record.status === 0 ? '#ff4d4f' : 'inherit' }}>{val || '-'}</span>
      )
    },
    {
      title: '操作时间',
      dataIndex: 'operTime',
      width: 170,
      align: 'center',
      render: (val: string) => formatDateTime(val)
    }
  ];

  // 列定义: 全盘高危操作审计
  const operColumns: ColumnsType<SysOperationLogVO> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '模块/操作', dataIndex: 'title', width: 160, ellipsis: true },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 120,
      align: 'center',
      render: (val: string) => <Tag color="default">{val}</Tag>
    },
    { title: '操作人员', dataIndex: 'operatorName', width: 120, align: 'center' },
    {
      title: '所属院系',
      dataIndex: 'deptName',
      width: 130,
      ellipsis: true,
      render: (val: string) => val || '系统任务/校级'
    },
    { title: '操作IP', dataIndex: 'operIp', width: 140, align: 'center' },
    {
      title: '耗时',
      dataIndex: 'costMs',
      width: 100,
      align: 'center',
      render: (val?: number) => (
        val != null ? <span style={{ fontFamily: 'Consolas, Monaco, monospace' }}>{val} ms</span> : '-'
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      align: 'center',
      render: (status: number) => (
        <Tag color={status === 1 ? 'success' : 'error'}>
          {status === 1 ? '成功' : '失败'}
        </Tag>
      )
    },
    {
      title: '操作时间',
      dataIndex: 'operTime',
      width: 170,
      align: 'center',
      render: (val: string) => formatDateTime(val)
    }
  ];

  return (
    <div style={{ padding: '24px' }}>
      {/* 头部标题与操作 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>系统监控与安全审计控制台</Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            服务器硬件/JVM负载、Caffeine有界缓存命中率、接口P95/P99慢调用度量及在线会话Token管控
          </Text>
        </div>
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={refreshCurrentTab}
        >
          刷新指标
        </Button>
      </div>

      <Card size="small">
        <Tabs activeKey={activeTab} onChange={handleTabChange}>
          {/* Tab 1: 服务器与 JVM 监控 */}
          <Tabs.TabPane
            tab={
              <span>
                <DashboardOutlined /> 服务器与JVM监控 (API-116)
              </span>
            }
            key="server"
          >
            <div style={{ paddingTop: 8 }}>
              <Row gutter={16}>
                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>系统状态</div>
                    <div style={{ marginBottom: 6 }}>
                      <Tag color="success" style={{ fontSize: 16, padding: '4px 14px' }}>
                        {serverData?.status || 'UP'}
                      </Tag>
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                      运行时间: {formatUptime(serverData?.upTimeSeconds)}
                    </div>
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>CPU 核心数与负载</div>
                    <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace', marginBottom: 6 }}>
                      {serverData?.cpuCores || '-'} 核
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                      CPU 负载率: <strong>{serverData?.systemCpuLoad != null ? serverData.systemCpuLoad + '%' : 'N/A'}</strong>
                    </div>
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>JVM 内存占用</div>
                    <div style={{ fontSize: 18, fontWeight: 700, color: '#1677ff', fontFamily: 'Consolas, Monaco, monospace', marginBottom: 4 }}>
                      {serverData?.jvmUsedMemoryMB || 0} / {serverData?.jvmTotalMemoryMB || 0} MB
                    </div>
                    <Progress
                      percent={calcJvmPercent(serverData)}
                      status={calcJvmPercent(serverData) > 85 ? 'exception' : 'active'}
                      size="small"
                    />
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>磁盘空间使用</div>
                    <div style={{ fontSize: 18, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace', marginBottom: 4 }}>
                      {serverData?.usedDiskSpaceGB || 0} / {serverData?.totalDiskSpaceGB || 0} GB
                    </div>
                    <Progress
                      percent={calcDiskPercent(serverData)}
                      status={calcDiskPercent(serverData) > 90 ? 'exception' : 'normal'}
                      size="small"
                    />
                  </Card>
                </Col>
              </Row>

              <Card
                title={<strong>运行时环境详细参数</strong>}
                size="small"
                style={{ marginTop: 16 }}
              >
                <Descriptions bordered column={2} size="small">
                  <Descriptions.Item label="操作系统名称">{serverData?.osName || '-'}</Descriptions.Item>
                  <Descriptions.Item label="系统架构">{serverData?.osArch || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Java 运行版本">{serverData?.jvmVersion || '-'}</Descriptions.Item>
                  <Descriptions.Item label="JVM 最大可用内存">{serverData?.jvmMaxMemoryMB || '-'} MB</Descriptions.Item>
                  <Descriptions.Item label="JVM 空闲内存">{serverData?.jvmFreeMemoryMB || '-'} MB</Descriptions.Item>
                  <Descriptions.Item label="磁盘剩余可用空间">{serverData?.freeDiskSpaceGB || '-'} GB</Descriptions.Item>
                  <Descriptions.Item label="Java 安装根目录" span={2}>
                    <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4, fontFamily: 'monospace' }}>
                      {serverData?.jvmHome || '-'}
                    </code>
                  </Descriptions.Item>
                  <Descriptions.Item label="指标采样时间" span={2}>
                    {formatDateTime(serverData?.timestamp)}
                  </Descriptions.Item>
                </Descriptions>
              </Card>
            </div>
          </Tabs.TabPane>

          {/* Tab 2: 本地缓存监控 */}
          <Tabs.TabPane
            tab={
              <span>
                <DatabaseOutlined /> 本地缓存监控 (API-117 & API-118)
              </span>
            }
            key="cache"
          >
            <div style={{ paddingTop: 8 }}>
              <Alert
                message="阶段8 遵循有界单机缓存规约：最大对象上限 1000，写入后 30 分钟过期，严禁无界内存驻留。"
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
              />
              <div style={{ marginBottom: 16 }}>
                <Button
                  type="primary"
                  danger
                  icon={<DeleteOutlined />}
                  loading={clearingCache}
                  onClick={handleClearCache}
                >
                  一键清空本地 Caffeine 缓存 (API-118)
                </Button>
              </div>

              <Row gutter={16}>
                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>缓存命中率</div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: '#52c41a', fontFamily: 'Consolas, Monaco, monospace' }}>
                      {cacheData ? (cacheData.hitRate * 100).toFixed(2) : 0}%
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                      命中: {cacheData?.hitCount || 0} / 未命中: {cacheData?.missCount || 0}
                    </div>
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>当前预估对象数</div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: '#1677ff', fontFamily: 'Consolas, Monaco, monospace' }}>
                      {cacheData?.estimatedSize || 0} 项
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                      最大配额: {cacheData?.maxSize || 1000} 项
                    </div>
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>缓存驱逐总次数</div>
                    <div style={{ fontSize: 24, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace' }}>
                      {cacheData?.evictionCount || 0} 次
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                      LRU/容量淘汰计数
                    </div>
                  </Card>
                </Col>

                <Col span={6}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>写入后过期时效</div>
                    <div style={{ fontSize: 24, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace' }}>
                      {cacheData?.expireAfterWriteMinutes || 30} 分钟
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                      自动失效回收
                    </div>
                  </Card>
                </Col>
              </Row>
            </div>
          </Tabs.TabPane>

          {/* Tab 3: 接口耗时与慢调用分析 */}
          <Tabs.TabPane
            tab={
              <span>
                <ThunderboltOutlined /> 接口耗时与慢调用分析 (API-119)
              </span>
            }
            key="slow"
          >
            <div style={{ paddingTop: 8 }}>
              <Row gutter={16}>
                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>P95 响应耗时</div>
                    <div
                      style={{
                        fontSize: 22,
                        fontWeight: 700,
                        fontFamily: 'Consolas, Monaco, monospace',
                        color: slowData && slowData.p95CostMs > 500 ? '#ff4d4f' : '#1677ff'
                      }}
                    >
                      {slowData?.p95CostMs || 0} ms
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>95% 请求低于该值</div>
                  </Card>
                </Col>

                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>P99 响应耗时</div>
                    <div
                      style={{
                        fontSize: 22,
                        fontWeight: 700,
                        fontFamily: 'Consolas, Monaco, monospace',
                        color: slowData && slowData.p99CostMs > 500 ? '#ff4d4f' : '#1677ff'
                      }}
                    >
                      {slowData?.p99CostMs || 0} ms
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>99% 请求低于该值</div>
                  </Card>
                </Col>

                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>平均响应耗时</div>
                    <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace' }}>
                      {slowData?.avgCostMs || 0} ms
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                      最小: {slowData?.minCostMs || 0} / 最大: {slowData?.maxCostMs || 0}
                    </div>
                  </Card>
                </Col>

                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>实时吞吐量 QPS</div>
                    <div style={{ fontSize: 22, fontWeight: 700, color: '#52c41a', fontFamily: 'Consolas, Monaco, monospace' }}>
                      {slowData?.qps || 0} req/s
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>滑动窗口估算</div>
                  </Card>
                </Col>

                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>环形采样样本数</div>
                    <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'Consolas, Monaco, monospace' }}>
                      {slowData?.sampleCount || 0} / {slowData?.ringBufferSize || 1000}
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>严格有界RingBuffer</div>
                  </Card>
                </Col>

                <Col span={4}>
                  <Card size="small" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 6 }}>慢调用判定阈值</div>
                    <div style={{ fontSize: 22, fontWeight: 700, color: '#faad14', fontFamily: 'Consolas, Monaco, monospace' }}>
                      {slowData?.thresholdMs || 500} ms
                    </div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>动态配置白名单控制</div>
                  </Card>
                </Col>
              </Row>

              <Card
                title={<strong>最近慢调用与慢查询流水 (保留上限 500 条)</strong>}
                extra={<Tag color="error">耗时 ≥ {slowData?.thresholdMs || 500} ms</Tag>}
                size="small"
                style={{ marginTop: 16 }}
              >
                <Table<SlowCallRecordVO>
                  rowKey="id"
                  columns={slowColumns}
                  dataSource={slowData?.slowCalls || []}
                  pagination={{ pageSize: 10 }}
                  size="small"
                  bordered
                />
              </Card>
            </div>
          </Tabs.TabPane>

          {/* Tab 4: 安全审计与在线会话 */}
          <Tabs.TabPane
            tab={
              <span>
                <SafetyCertificateOutlined /> 安全审计与在线会话 (API-120 ~ API-122)
              </span>
            }
            key="security"
          >
            <div style={{ paddingTop: 8 }}>
              <Tabs
                type="card"
                activeKey={securitySubTab}
                onChange={(key) => setSecuritySubTab(key)}
              >
                {/* 4.1 在线会话与 Token 踢出 */}
                <Tabs.TabPane tab="在线活跃会话与Token踢出 (API-122)" key="tokens">
                  <div style={{ paddingTop: 8 }}>
                    <Alert
                      message="超管强制踢下线机制：原子递增目标用户的 token_version，历史签发的 JWT 令牌在下一次请求时将被 JwtAuthenticationFilter 即时拦截返回 401 Unauthorized。"
                      type="warning"
                      showIcon
                      style={{ marginBottom: 16 }}
                    />
                    <Table<SysActiveTokenVO>
                      rowKey="userId"
                      columns={tokenColumns}
                      dataSource={tokenList}
                      pagination={{ pageSize: 10 }}
                      size="small"
                      bordered
                    />
                  </div>
                </Tabs.TabPane>

                {/* 4.2 登录安全审计 */}
                <Tabs.TabPane tab="登录安全审计 (API-120)" key="loginLogs">
                  <div style={{ paddingTop: 8 }}>
                    <Form form={loginFilterForm} layout="inline" style={{ marginBottom: 16 }}>
                      <Form.Item name="username" label="账号检索">
                        <Input placeholder="输入用户名" allowClear style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name="status" label="登录状态">
                        <Select
                          placeholder="全部"
                          allowClear
                          style={{ width: 120 }}
                          options={[
                            { label: '成功', value: 1 },
                            { label: '失败', value: 0 }
                          ]}
                        />
                      </Form.Item>
                      <Form.Item>
                        <Button type="primary" icon={<SearchOutlined />} onClick={fetchLoginLogs}>
                          查询
                        </Button>
                      </Form.Item>
                    </Form>

                    <Table<SysLoginLogVO>
                      rowKey="id"
                      columns={loginColumns}
                      dataSource={loginLogs}
                      pagination={{ pageSize: 10 }}
                      size="small"
                      bordered
                    />
                  </div>
                </Tabs.TabPane>

                {/* 4.3 全盘高危操作审计 */}
                <Tabs.TabPane tab="全盘高危操作审计 (API-121)" key="operLogs">
                  <div style={{ paddingTop: 8 }}>
                    <Table<SysOperationLogVO>
                      rowKey="id"
                      columns={operColumns}
                      dataSource={operationLogs}
                      pagination={{ pageSize: 10 }}
                      size="small"
                      bordered
                    />
                  </div>
                </Tabs.TabPane>
              </Tabs>
            </div>
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  );
};
