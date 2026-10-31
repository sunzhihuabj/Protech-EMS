import { Card, Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

/** 未到切片的路由占位（07 册：骨架页按页面族在对应切片落地） */
export default function Stub({ title, slice }: { title: string; slice: string }) {
  const nav = useNavigate();
  return (
    <Card>
      <Result
        status="info"
        title={title}
        subTitle={`该页面族在切片 ${slice} 交付（依据 06 册 §4.3 切片计划）`}
        extra={<Button onClick={() => nav('/home')}>返回工作台</Button>}
      />
    </Card>
  );
}
