import { useEffect, useState } from 'react';
import { Layout, Menu, Dropdown, Badge, Space, theme } from 'antd';
import {
  HomeOutlined,
  DollarOutlined,
  ProjectOutlined,
  DatabaseOutlined,
  ToolOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  SettingOutlined,
  UserOutlined,
  BellOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../stores/auth';
import { api } from '../api/client';
import { ThemeSwitcher } from '../components/ThemeSwitcher';
import { useTheme } from '../theme/theme';

const { Sider, Header, Content } = Layout;

/** 07 册 §1.1：一级导航 8 项（深色导航 + 浅色内容默认主题） */
const NAV = [
  { key: '/home', icon: <HomeOutlined />, label: '工作台' },
  { key: '/biz', icon: <DollarOutlined />, label: '商务' },
  { key: '/project', icon: <ProjectOutlined />, label: '项目' },
  { key: '/supply', icon: <DatabaseOutlined />, label: '供应链' },
  { key: '/delivery', icon: <ToolOutlined />, label: '工程交付' },
  { key: '/kb', icon: <AppstoreOutlined />, label: '看板' },
  { key: '/report', icon: <BarChartOutlined />, label: '报表' },
  { key: '/admin', icon: <SettingOutlined />, label: '系统管理' },
];

export function BasicLayout() {
  const { me, clear, accessToken, setMe } = useAuth();
  const themeMode = useTheme((s) => s.mode);
  const nav = useNavigate();
  const loc = useLocation();
  const [unread, setUnread] = useState(0);
  const { token } = theme.useToken();

  useEffect(() => {
    if (!accessToken) {
      nav('/login');
      return;
    }
    if (!me) {
      // 刷新/直跳后恢复当前用户上下文（FR-M00-002）
      api<Parameters<typeof setMe>[0]>('get', '/api/v1/auth/me').then(setMe).catch(() => undefined);
    }
  }, [accessToken, nav, me, setMe]);

  useEffect(() => {
    api<{ unread: number }>('get', '/api/v1/sys/notifies')
      .then((d) => setUnread(d.unread))
      .catch(() => undefined);
  }, [loc.pathname]);

  const selected = '/' + (loc.pathname.split('/')[1] ?? 'home');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={220} collapsible>
        <div style={{ color: '#fff', fontWeight: 700, fontSize: 16, padding: '16px', textAlign: 'center' }}>
          Protech 业务总台
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          items={NAV}
          onClick={(e) => nav(e.key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: token.colorBgContainer,
            padding: '0 16px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
          }}
        >
          <Space size={20}>
            <ThemeSwitcher />
            <Badge count={unread} size="small">
              <BellOutlined style={{ fontSize: 18 }} onClick={() => nav('/home')} />
            </Badge>
            <Dropdown
              menu={{
                items: [
                  { key: 'me', icon: <UserOutlined />, label: `${me?.user.displayName ?? ''}（${me?.roles.map((r) => r.name).join('/') ?? ''}）`, disabled: true },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
                ],
                onClick: (e) => {
                  if (e.key === 'logout') {
                    clear();
                    nav('/login');
                  }
                },
              }}
            >
              <span style={{ cursor: 'pointer' }}>
                <UserOutlined /> {me?.user.displayName ?? '未登录'}
              </span>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16, maxWidth: 1440 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
