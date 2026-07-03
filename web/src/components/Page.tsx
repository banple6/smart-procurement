import { Card, Empty, Space, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import { policeTokens } from '@/theme/tokens';
import { brandConfig } from '@/theme/tokens';
import type { OrderStatus, SupplyStatus } from '@/types/domain';
import { orderStatusText, supplyStatusText } from '@/utils/status';

export function PageHeader({ title, description, extra }: { title: string; description?: string; extra?: ReactNode }) {
  return (
    <div className="page-title-row">
      <div>
        <Typography.Title level={1}>{title}</Typography.Title>
        {description ? <p>{description}</p> : null}
      </div>
      {extra ? <Space wrap>{extra}</Space> : null}
    </div>
  );
}

export function StatCard({ label, value, extra }: { label: string; value: ReactNode; extra?: ReactNode }) {
  return (
    <Card>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <div style={{ marginTop: 8, fontSize: 26, fontWeight: 700, color: policeTokens.TextPrimary }}>{value}</div>
      {extra ? <div style={{ marginTop: 8 }}>{extra}</div> : null}
    </Card>
  );
}

export function StatusTag({ status }: { status: OrderStatus | SupplyStatus | string }) {
  const label = (orderStatusText as Record<string, string>)[status] || (supplyStatusText as Record<string, string>)[status] || status;
  const colorMap: Record<string, string> = {
    pending: policeTokens.StatusPending,
    accepted: policeTokens.StatusNormal,
    preparing: policeTokens.StatusPreparing,
    shipped: policeTokens.StatusPending,
    completed: policeTokens.StatusNormal,
    cancelled: policeTokens.StatusCancelled,
    normal: policeTokens.StatusNormal,
    tight: policeTokens.StatusWarning,
    paused: policeTokens.StatusError,
    off_shelf: policeTokens.StatusCancelled,
    open: policeTokens.StatusError,
    resolved: policeTokens.StatusNormal,
  };
  return <Tag color={colorMap[status] || policeTokens.StatusPending}>{label}</Tag>;
}

export function EmptyState({ description = '暂无数据' }: { description?: string }) {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />;
}

export function MobileCardList<T>({ items, render }: { items: T[]; render: (item: T) => ReactNode }) {
  if (!items.length) return <EmptyState />;
  return <div className="mobile-card-list">{items.map(render)}</div>;
}

export function PrintHeader({ title }: { title: string }) {
  return (
    <div className="print-header" style={{ display: 'none' }}>
      <div>
        <strong>{brandConfig.departmentName}</strong>
        <div>{title}</div>
      </div>
    </div>
  );
}
