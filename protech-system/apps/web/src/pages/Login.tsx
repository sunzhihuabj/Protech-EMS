import { useState } from 'react';
import { Form, Input, Button, Card, Typography, App } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../stores/auth';
import { BlockCodeAlert } from '../components/BlockCodeAlert';
import { ApiError } from '../api/client';

/** 07 册 3.1：登录 + 角色化引导（S0 简化为登录成功后进入工作台，引导语在工作台按角色展示） */
export default function Login() {
  const nav = useNavigate();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const setTokens = useAuth((s) => s.setTokens);

  const onFinish = async (v: { username: string; password: string }) => {
    setLoading(true);
    setError(null);
    try {
      const res = await axios.post('/api/v1/auth/login', v);
      if (res.data.code !== 0) throw new ApiError(res.data.code, res.data.msg, res.data.blockCode);
      setTokens(res.data.data.accessToken, res.data.data.refreshToken);
      message.success('登录成功');
      nav('/home');
    } catch (e) {
      if (axios.isAxiosError(e) && e.response?.data) {
        setError(new ApiError(e.response.data.code, e.response.data.msg, e.response.data.blockCode));
      } else {
        setError(e);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(160deg, #0f2540 0%, #16385f 60%, #1d4a7a 100%)',
      }}
    >
      <Card style={{ width: 400 }}>
        <Typography.Title level={4} style={{ textAlign: 'center', marginBottom: 4 }}>
          Protech 企业综合管理系统
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center', marginTop: 0 }}>
          一个项目号走到底 · 指标自动取数 · 红灯必闭环
        </Typography.Paragraph>
        <Form onFinish={onFinish} size="large">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名（如 admin / chain-sw / op-demo）" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          {error !== null && (
            <Form.Item>
              <AlertInForm error={error} />
            </Form.Item>
          )}
          <Button type="primary" htmlType="submit" block loading={loading}>
            登 录
          </Button>
        </Form>
      </Card>
    </div>
  );
}

function AlertInForm({ error }: { error: unknown }) {
  return <BlockCodeAlert error={error} />;
}
