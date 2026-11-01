import { Button, Result, Space } from 'antd';
import { useNavigate } from 'react-router-dom';

/** 404 兜底页（07 册 I-04 状态完整：错态可退出，不裸奔 react-router 默认错误） */
export default function NotFound() {
  const nav = useNavigate();
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="您访问的地址没有对应页面；请从左侧导航进入，或返回工作台。"
      extra={
        <Space>
          <Button type="primary" onClick={() => nav('/home')}>
            返回工作台
          </Button>
          <Button onClick={() => nav('/')}>首页</Button>
        </Space>
      }
    />
  );
}

