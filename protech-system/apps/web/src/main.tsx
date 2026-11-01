import React, { useEffect } from 'react';
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
import DeptBoard from './pages/DeptBoard';
import ProjectMonitor from './pages/ProjectMonitor';
import StockMoveWork from './pages/StockMoveWork';
import AiAssistant from './pages/AiAssistant';
import ReportCenter from './pages/ReportCenter';
import MobileHome from './pages/MobileHome';
import Manual from './pages/Manual';
import Stub from './pages/Stub';
import { THEMES, useTheme, applyThemeCss } from './theme/theme';
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
      { path: 'biz/quote', element: <QuoteList /> },
      { path: 'biz/contract', element: <ContractList /> },
      { path: 'biz/contract/:id', element: <ContractDetail /> },
      { path: 'supply/material', element: <MaterialList /> },
      { path: 'supply/stock-move', element: <StockMoveWork /> },
      { path: 'supply', element: <Stub title="BOM / 采购 / 工单（S3 深化页）" slice="S3" /> },
      { path: 'delivery', element: <Stub title="派工 / 销项 / 现场日志（BR-04）" slice="S4" /> },
      { path: 'kb', element: <BoardView /> },
      { path: 'kb/:code', element: <BoardView /> },
      { path: 'kb/executive', element: <ExecutiveBoard /> },
      { path: 'kb/dept/:deptId', element: <DeptBoard /> },
      { path: 'kb/project/:projectId', element: <ProjectMonitor /> },
      { path: 'report', element: <ReportCenter /> },
      { path: 'ai', element: <AiAssistant /> },
      { path: 'admin', element: <Stub title="用户 / 角色 / 编码规则 / 配置 / 审计" slice="S0 深化" /> },
      { path: 'm', element: <MobileHome /> },
      { path: 'manual', element: <Manual /> },
    ],
  },
]);

/** FR-M00-014：主题（12 套 × 明暗）与 antd 算法/主色联动 */
function ThemedRoot() {
  const { themeKey, mode } = useTheme();
  useEffect(() => applyThemeCss(), [themeKey, mode]);
  const theme = THEMES.find((t) => t.key === themeKey) ?? THEMES[0];
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: { colorPrimary: theme.pri['500'], borderRadius: 8, fontSize: 14 },
      }}
    >
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemedRoot />
  </React.StrictMode>,
);
