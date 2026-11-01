import { Card, Collapse, Typography } from 'antd';

const { Paragraph, Title } = Typography;

/** FR-M00-012 角色化使用手册（S/R2；关键卡点页内嵌提示已在各表单 BlockCodeAlert 落地） */
export default function Manual() {
  return (
    <Card title="操作手册（角色化指南 FR-M00-012）">
      <Collapse
        items={[
          {
            key: 'all',
            label: '通用：一条铁律',
            children: (
              <Paragraph>
                <b>指标自动取数，人工只维护红灯卡。</b>凡单据/档案，"能选不填"（BR-08），编码系统生成不可改（BR-09），金额整数分（BR-10），当日事当日毕（BR-12）。
              </Paragraph>
            ),
          },
          {
            key: 'exec',
            label: 'R-EXEC 总经理/高管',
            children: (
              <>
                <Title level={5}>BV-02 全局看板</Title>
                <Paragraph>出差打开 /kb/executive：指标区（自动取数≤2h）+ 红灯总览 + 九板健康度。红灯点击直达；"要求今日答复"一键催办。</Paragraph>
              </>
            ),
          },
          {
            key: 'chain',
            label: 'R-CHAIN 链主',
            children: (
              <>
                <Title level={5}>日站会 30 分钟</Title>
                <Paragraph>看板 → 会议模式：0-3 指标速览 / 3-22 红灯过堂（三问必填才能过下一张）/ 22-27 今日承诺 / 27-30 上报协调。红灯 3 天自动升级总板（BR-07），无权压卡；跨部门卡点 24h 挂出、48h 未响应升级周会。</Paragraph>
              </>
            ),
          },
          {
            key: 'biz',
            label: 'R-OP 商务/建档',
            children: (
              <>
                <Title level={5}>建档取码</Title>
                <Paragraph>合同评审前必须先建档取码（BR-01）。项目建档用名称生成器（业主+风场+期次）；主体三重查重命中走确认。用印后 3 个工作日内完成 25 要素备案，否则开票冻结（BR-02）。</Paragraph>
              </>
            ),
          },
          {
            key: 'wh',
            label: 'R-OP 仓管',
            children: (
              <>
                <Title level={5}>扫码过账</Title>
                <Paragraph>供应链 → 扫码作业：选单据 → 扫码 → 数量确认 → 提交。无单据不可入库（源头单据驱动）；旧码自动换主码；T+ 队列提交后自动生成。发货必须挂项目（BR-03）。</Paragraph>
              </>
            ),
          },
          {
            key: 'pm',
            label: 'R-PM 项目负责人',
            children: (
              <>
                <Title level={5}>BV-03 项目监管</Title>
                <Paragraph>/kb/project/:id：九阶段时间线 + 关键节点卡 + 红灯 + 团队 + 财务。项目关闭 7 天后授权自动回收（FR-M00-003）。</Paragraph>
              </>
            ),
          },
          {
            key: 'ops',
            label: 'R-SYS/R-GA 管理员',
            children: (
              <Paragraph>编码规则/权限/配置中心在 /admin（深化页）；治理工单/查重工作台 API：/gov/*；集成监控台：/api/v1/integration/monitor。</Paragraph>
            ),
          },
        ]}
      />
    </Card>
  );
}
