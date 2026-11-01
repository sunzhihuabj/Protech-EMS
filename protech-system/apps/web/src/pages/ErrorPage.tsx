import { Card, Button, Typography } from 'antd';
import { useNavigate, useRouteError } from 'react-router-dom';

const { Paragraph } = Typography;

/** 路由渲染异常兜底（替代 react-router 默认 "Unexpected Application Error!"） */
export default function ErrorPage() {
  const error = useRouteError() as Error | { statusText?: string; message?: string } | undefined;
  const nav = useNavigate();
  const msg =
    (error as { statusText?: string })?.statusText ??
    (error as { message?: string })?.message ??
    '未知错误';
  return (
    <Card style={{ margin: 24 }}>
      <Typography.Title level={4}>页面出了点问题</Typography.Title>
      <Paragraph type="secondary">错误信息：{String(msg)}</Paragraph>
      <Paragraph type="secondary">
        可返回工作台重试；若持续出现，请联系管理员并提供上述错误信息。
      </Paragraph>
      <Button type="primary" onClick={() => nav('/home')}>
        返回工作台
      </Button>
    </Card>
  );
}
