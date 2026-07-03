import { Card } from 'antd';
import { PageHeader } from '@/components/Page';
import { brandConfig } from '@/theme/tokens';

export function SettingsPage() {
  return (
    <div className="page-shell">
      <PageHeader title="系统设置" description="第一版仅展示品牌配置和接口说明" />
      <Card title="品牌配置">
        <p>应用名称：{brandConfig.appName}</p>
        <p>单位名称：{brandConfig.departmentName}</p>
        <p>系统名称：{brandConfig.systemName}</p>
        <p>标识：{brandConfig.internalUseLabel}</p>
      </Card>
    </div>
  );
}
