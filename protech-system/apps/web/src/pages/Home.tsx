import { useEffect, useState } from 'react';
import { Card, Col, List, Row, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../stores/auth';

interface NotifyItem {
  id: string;
  type: string;
  title: string;
  readAt: string | null;
  createdAt: string;
}
interface ProjectItem {
  id: string;
  projectCode: string;
  standardName: string;
  status: string;
}

const ROLE_GUIDE: Record<string, string> = {
  'R-EXEC': '您是高管：全局看板（BV-02）上线后，从这里直达实时经营快照；红灯升级清单自动推送。',
  'R-CHAIN': '您是链主：日站会 30 分钟只过红灯；跨部门卡点 24h 内挂出，48h 未响应自动升级。',
  'R-DEPT': '您是部门负责人：部门看板（BV-01）聚合本部门成员参与的全部业务。',
  'R-PM': '您是项目负责人：项目监管看板（BV-03）展示所负责项目全流程；结项 7 天后权限自动回收。',
  'R-OP': '您是业务操作员：从待办出发——建单、扫码、报工、建卡，少填快办。',
  'R-SYS': '您是系统管理员：编码规则/权限/集成配置在系统管理菜单。',
};

/** 07 册 3.1 工作台：左待办/中看板/右我的项目（S0 先落通知+我的项目+角色引导） */
export default function Home() {
  const me = useAuth((s) => s.me);
  const nav = useNavigate();
  const [notifies, setNotifies] = useState<NotifyItem[]>([]);
  const [projects, setProjects] = useState<ProjectItem[]>([]);

  useEffect(() => {
    api<{ list: NotifyItem[] }>('get', '/api/v1/sys/notifies').then((d) => setNotifies(d.list.slice(0, 10))).catch(() => undefined);
    api<{ list: ProjectItem[] }>('get', '/api/v1/mdm/projects?pageSize=10').then((d) => setProjects(d.list)).catch(() => undefined);
  }, []);

  const guide = me?.roles.map((r) => ROLE_GUIDE[r.code]).filter(Boolean)[0] ?? '欢迎登录 Protech 业务总台。';

  return (
    <Row gutter={[16, 16]}>
      <Col span={24}>
        <Card size="small">
          <Typography.Text type="secondary">角色引导：</Typography.Text>
          {guide}
        </Card>
      </Col>
      <Col xs={24} md={12}>
        <Card
          title="我的待办 / 通知"
          size="small"
          extra={<a onClick={() => nav('/home')}>全部</a>}
        >
          <List
            size="small"
            dataSource={notifies}
            locale={{ emptyText: '暂无待办——红灯卡与审批将自动推送到这里（FR-M00-007）' }}
            renderItem={(n) => (
              <List.Item>
                <Typography.Text delete={!!n.readAt} style={{ fontSize: 13 }}>
                  <Tag color={n.type === 'escalate' ? 'red' : n.type === 'remind' ? 'orange' : 'blue'}>
                    {n.type === 'escalate' ? '升级' : n.type === 'remind' ? '提醒' : '待办'}
                  </Tag>
                  {n.title}
                </Typography.Text>
              </List.Item>
            )}
          />
        </Card>
      </Col>
      <Col xs={24} md={12}>
        <Card title="我的项目（BV-03 数据源）" size="small" extra={<a onClick={() => nav('/project')}>项目档案</a>}>
          <List
            size="small"
            dataSource={projects}
            locale={{ emptyText: '暂无可见项目——数据范围按角色过滤（FR-M00-002）' }}
            renderItem={(p) => (
              <List.Item>
                <a onClick={() => nav(`/project/${p.id}`)} style={{ fontSize: 13 }}>
                  {p.projectCode} · {p.standardName}
                </a>
                <Tag style={{ marginLeft: 'auto' }}>{p.status}</Tag>
              </List.Item>
            )}
          />
        </Card>
      </Col>
    </Row>
  );
}
