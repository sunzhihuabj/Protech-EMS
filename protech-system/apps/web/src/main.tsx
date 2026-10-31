import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider, App as AntApp, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { BasicLayout } from './layouts/BasicLayout';
import Login from './pages/Login';
import Home from './pages/Home';
import ProjectList from './pages/ProjectList';
import ProjectDetail from './pages/ProjectDetail';
import MaterialList from './pages/MaterialList';
import QuoteList from './pages/QuoteList';
import ContractList from './pages/ContractList';
import ContractDetail from './pages/ContractDetail';
import BoardView from './pages/BoardView';
import ExecutiveBoard from './pages/ExecutiveBoard';
import Stub from './pages/Stub';
import 'dayjs/locale/zh-cn';

const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      { index: true, element: <Home /> },
      { path: 'home', element: <Home /> },
      { path: 'project', element: <ProjectList /> },
      { path: 'project/:id', element: <ProjectDetail /> },
      { path: 'supply/material', element: <MaterialList /> },
      { path: 'supply', element: <Stub title="BOM / 采购 / 库房 / 工单" slice="S3" /> },
      { path: 'biz/quote', element: <QuoteList /> },
      { path: 'biz/contract', element: <ContractList /> },
      { path: 'biz/contract/:id', element: <ContractDetail /> },
      { path: 'biz', element: <Stub title="报价 / 合同 / 评审（BR-01/02 卡点）" slice="S1" /> },
      { path: 'delivery', element: <Stub title="派工 / 销项 / 现场日志（BR-04）" slice="S4" /> },
      { path: 'kb', element: <BoardView /> },
      { path: 'kb/:code', element: <BoardView /> },
      { path: 'kb/executive', element: <ExecutiveBoard /> },
      { path: 'report', element: <Stub title="预置报表 / 导出 / 订阅" slice="S6" /> },
      { path: 'admin', element: <Stub title="用户 / 角色 / 编码规则 / 配置 / 审计" slice="S0 深化" /> },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        // 07 册 §五：默认深色导航 + 浅色内容；主题令牌化在 FR-M00-014（R2）接入 visual/ 12 主题
        algorithm: antdTheme.defaultAlgorithm,
        token: { colorPrimary: '#1d4a7a', borderRadius: 8, fontSize: 14 },
      }}
    >
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
