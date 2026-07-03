import { Space, Typography } from 'antd';
import { brandConfig } from '@/theme/tokens';

export function PoliceBadge({ size = 36, label = false }: { size?: number; label?: boolean }) {
  return (
    <img
      className="police-badge"
      src={brandConfig.badgePath}
      alt={label ? '人民警察警徽' : ''}
      aria-hidden={!label}
      style={{ width: size, height: size }}
    />
  );
}

export function LoginBrandPanel() {
  return (
    <div className="login-brand-panel">
      <PoliceBadge size={48} label />
      <Typography.Title level={1}>{brandConfig.appName}</Typography.Title>
      <Typography.Text>{brandConfig.systemName}</Typography.Text>
      <Typography.Text className="login-internal-label">{brandConfig.internalUseLabel}</Typography.Text>
    </div>
  );
}

export function TopBrand() {
  return (
    <Space size={12} align="center">
      <span className="top-brand-name">{brandConfig.appName}</span>
      <span className="top-brand-unit">{brandConfig.departmentName}</span>
    </Space>
  );
}
