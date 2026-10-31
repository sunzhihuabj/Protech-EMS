import { Alert } from 'antd';
import { BR_MAP } from '@protech/shared-types';
import { ApiError } from '../api/client';

/**
 * 硬卡点统一展示组件（07 册 I-05）：
 * blockCode → 红线名称 + 解释 + 下一步指引（BR 规则文案前后端同源 shared-types）。
 */
export function BlockCodeAlert({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return null;
  const br = error.blockCode && BR_MAP[error.blockCode];
  if (!br) return null;
  return (
    <Alert
      type="error"
      showIcon
      style={{ marginTop: 8 }}
      message={`${br.code} ${br.rule}`}
      description={br.message}
    />
  );
}
