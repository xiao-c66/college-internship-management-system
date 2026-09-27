import React, { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Radio,
  Checkbox,
  Space,
  Tag,
  Typography,
  Result,
  message,
  Spin,
  Alert,
  Divider
} from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SendOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  getExamPaper,
  submitExam,
  getSafetyStatus,
  QuestionVO,
  QuestionOption,
  ExamResultVO,
  SafetyStatusVO
} from '../../api/safety';

const { Title, Paragraph, Text } = Typography;

export const SafetyExamPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const taskId = Number(searchParams.get('taskId')) || 2093;

  const [questions, setQuestions] = useState<QuestionVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);

  // 答案映射：questionId -> 单选/判断 (string) 或 多选 (string[])
  const [answers, setAnswers] = useState<Record<number, string | string[]>>({});
  const [examResult, setExamResult] = useState<ExamResultVO | null>(null);
  const [safetyStatus, setSafetyStatus] = useState<SafetyStatusVO | null>(null);
  const isPassed = Boolean(safetyStatus?.isPassed);

  const loadPaper = async () => {
    setLoading(true);
    setExamResult(null);
    setAnswers({});
    try {
      const [paperRes, statusRes] = await Promise.all([
        getExamPaper(taskId),
        getSafetyStatus(taskId)
      ]);
      if (paperRes.code === 200 && paperRes.data) {
        setQuestions(paperRes.data);
      }
      if (statusRes.code === 200 && statusRes.data) {
        setSafetyStatus(statusRes.data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPaper();
  }, [taskId]);

  const parseOptions = (rawOptions: string | QuestionOption[]): QuestionOption[] => {
    if (Array.isArray(rawOptions)) return rawOptions;
    try {
      return JSON.parse(rawOptions);
    } catch {
      return [];
    }
  };

  const handleSingleAnswer = (qId: number, val: string) => {
    setAnswers((prev) => ({ ...prev, [qId]: val }));
  };

  const handleMultiAnswer = (qId: number, vals: string[]) => {
    setAnswers((prev) => ({ ...prev, [qId]: vals }));
  };

  const handleSubmit = async () => {
    const answeredCount = Object.keys(answers).filter((k) => {
      const v = answers[Number(k)];
      return Array.isArray(v) ? v.length > 0 : !!v;
    }).length;

    if (answeredCount < questions.length) {
      message.warning(`当前尚有 ${questions.length - answeredCount} 道题未作答，请全部作答完成后再交卷`);
      return;
    }

    setSubmitLoading(true);
    try {
      const payloadAnswers = Object.entries(answers).map(([qId, val]) => ({
        questionId: Number(qId),
        studentAnswer: Array.isArray(val) ? val.sort().join(',') : String(val),
        answer: Array.isArray(val) ? val.sort().join(',') : String(val)
      }));

      const res = await submitExam({
        taskId,
        answers: payloadAnswers
      });

      if (res.code === 200 && res.data) {
        setExamResult(res.data);
        message.success(res.data.isPassed ? '恭喜您通过安全教育准入考核！' : '测试未达标，请复习后重测');
      }
    } catch (err: any) {
      const errMsg = err?.response?.data?.message || err?.message;
      if (errMsg?.includes('已通过')) {
        message.info(errMsg);
      }
    } finally {
      setSubmitLoading(false);
    }
  };

  const formatQuestionType = (type: string) => {
    switch (type) {
      case 'SINGLE_CHOICE': return <Tag color="blue">单选题</Tag>;
      case 'MULTIPLE_CHOICE': return <Tag color="purple">多选题</Tag>;
      case 'JUDGMENT': return <Tag color="cyan">判断题</Tag>;
      default: return <Tag>{type}</Tag>;
    }
  };

  if (loading) {
    return (
      <Card style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" tip="正在从题库抽取随机安全考核试卷..." />
      </Card>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 顶部标题与返回 */}
      <Card>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/safety')}>
            返回安全教育主页
          </Button>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>
                实习安全教育与准入在线测试 (SAFE-004 ~ SAFE-006)
              </Title>
              <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
                题库客观题乱序抽取，单选、多选与判断题提交后由系统后端即时自动判分
              </Paragraph>
            </div>
            {examResult && (
              <Tag
                color={examResult.isPassed ? 'success' : 'error'}
                style={{ fontSize: 14, padding: '4px 12px' }}
              >
                {examResult.isPassed ? '测试合格 (通过)' : '未及格 (需重测)'}
              </Tag>
            )}
          </div>
        </Space>
      </Card>

      {/* 考核结果反馈 */}
      {examResult ? (
        <Card>
          <Result
            status={examResult.isPassed ? 'success' : 'warning'}
            title={examResult.resultDesc}
            subTitle={
              <div style={{ fontSize: 16, marginTop: 8 }}>
                本次成绩：<strong style={{ color: examResult.isPassed ? '#52c41a' : '#ff4d4f', fontSize: 24 }}>{examResult.totalScore}</strong> 分
                (及格线: {examResult.passingScore} 分) | 作答轮次: 第 {examResult.attemptNo} / {examResult.maxAttempts} 次
              </div>
            }
            extra={[
              <Button type="primary" size="large" key="back" onClick={() => navigate('/safety')}>
                {examResult.isPassed ? '前往签署安全承诺书 (准入闭环)' : '返回安全规约学习主页'}
              </Button>,
              !examResult.isPassed && examResult.remainingAttempts > 0 && (
                <Button size="large" key="retry" icon={<ReloadOutlined />} onClick={loadPaper}>
                  重新测验 (剩余 {examResult.remainingAttempts} 次机会)
                </Button>
              )
            ]}
          />
        </Card>
      ) : (
        /* 试卷作答区域 */
        <Card>
          {isPassed ? (
            <Alert
              message={`恭喜！您已在第 ${safetyStatus?.examAttempts} 轮成功通过本次实习安全准入考核（最高成绩 ${safetyStatus?.highestScore || 100} 分，及格线 ${safetyStatus?.passingScore || 80} 分）。`}
              description="根据安全准入规范 (SAFE-005)，准入资格已确认生效，无需重复交卷。此界面可供您随时查阅试题进行日常实习安全复习。"
              type="success"
              showIcon
              style={{ marginBottom: 20 }}
            />
          ) : (
            <Alert
              message={`本次安全准入试卷共 ${questions.length} 题，请认真审题作答，完成后点击底部交卷按钮。`}
              type="info"
              showIcon
              style={{ marginBottom: 20 }}
            />
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
            {questions.map((q, idx) => {
              const opts = parseOptions(q.options);
              return (
                <Card key={q.id} size="small" style={{ backgroundColor: '#fafafa' }}>
                  <div style={{ marginBottom: 12 }}>
                    <span style={{ fontSize: 16, fontWeight: 'bold', marginRight: 8 }}>
                      {idx + 1}.
                    </span>
                    {formatQuestionType(q.questionType)}
                    <span style={{ fontSize: 15, fontWeight: 500, marginLeft: 8 }}>
                      {q.stem}
                    </span>
                    <Text type="secondary" style={{ marginLeft: 8 }}>({q.score} 分)</Text>
                  </div>

                  {/* 单选题或判断题 */}
                  {q.questionType === 'SINGLE_CHOICE' || q.questionType === 'JUDGMENT' ? (
                    <Radio.Group
                      value={answers[q.id] as string}
                      onChange={(e) => handleSingleAnswer(q.id, e.target.value)}
                    >
                      <Space direction="vertical">
                        {opts.map((opt) => (
                          <Radio key={opt.key} value={opt.key} style={{ fontSize: 14 }}>
                            <strong>{opt.key}.</strong> {opt.text}
                          </Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  ) : (
                    /* 多选题 */
                    <Checkbox.Group
                      value={(answers[q.id] as string[]) || []}
                      onChange={(vals) => handleMultiAnswer(q.id, vals as string[])}
                    >
                      <Space direction="vertical">
                        {opts.map((opt) => (
                          <Checkbox key={opt.key} value={opt.key} style={{ fontSize: 14 }}>
                            <strong>{opt.key}.</strong> {opt.text}
                          </Checkbox>
                        ))}
                      </Space>
                    </Checkbox.Group>
                  )}
                </Card>
              );
            })}
          </div>

          <Divider />

          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              type="primary"
              size="large"
              icon={isPassed ? <CheckCircleOutlined /> : <SendOutlined />}
              loading={submitLoading}
              onClick={handleSubmit}
              disabled={isPassed}
              style={{ width: 320, height: 48, fontSize: 16 }}
            >
              {isPassed ? '已通过考核 (资格生效无需重考)' : '提交答卷并即时判分'}
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
};
