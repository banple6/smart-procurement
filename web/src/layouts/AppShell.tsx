import {
  AppstoreOutlined,
  AuditOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Button, Drawer, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthContext';
import { roleText } from '@/utils/status';
import { useResponsiveMode } from '@/utils/responsive';
import { policeTokens } from '@/theme/tokens';

const { Header, Sider, Content } = Layout;

function adminItems(): MenuProps['items'] {
  return [
    { key: '/admin/dashboard', icon: <HomeOutlined />, label: '工作台' },
    {
      key: 'purchase',
      icon: <FileTextOutlined />,
      label: '采购管理',
      children: [
        { key: '/admin/orders', icon: <FileTextOutlined />, label: '订单管理' },
        { key: '/admin/preparation', icon: <DatabaseOutlined />, label: '今日备货' },
        { key: '/admin/delivery', icon: <AuditOutlined />, label: '单位配送' },
        { key: '/admin/receipt-issues', icon: <WarningOutlined />, label: '收货异常' },
      ],
    },
    {
      key: 'products',
      icon: <ShopOutlined />,
      label: '食材管理',
      children: [
        { key: '/admin/products', icon: <ShopOutlined />, label: '食材列表' },
        { key: '/admin/products?quick=price', icon: <AuditOutlined />, label: '价格维护' },
        { key: '/admin/inventory', icon: <DatabaseOutlined />, label: '库存记录' },
      ],
    },
    {
      key: 'organization',
      icon: <TeamOutlined />,
      label: '组织管理',
      children: [
        { key: '/admin/units', icon: <TeamOutlined />, label: '子单位管理' },
        { key: '/admin/users', icon: <UserOutlined />, label: '账号管理' },
      ],
    },
    {
      key: 'reports',
      icon: <AuditOutlined />,
      label: '统计报表',
      children: [
        { key: '/admin/ledger', icon: <AuditOutlined />, label: '采购台账' },
        { key: '/admin/preparation', icon: <UnorderedListOutlined />, label: '商品汇总' },
        { key: '/admin/ledger?export=today', icon: <FileTextOutlined />, label: '数据导出' },
      ],
    },
    {
      key: 'system',
      icon: <AppstoreOutlined />,
      label: '系统',
      children: [
        { key: '/account', icon: <UserOutlined />, label: '当前账号' },
        { key: '/admin/settings', icon: <AppstoreOutlined />, label: '系统状态' },
        { key: '__logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
      ],
    },
  ];
}

function unitItems(): MenuProps['items'] {
  return [
    { key: '/unit/products', icon: <ShopOutlined />, label: '食材申领' },
    { key: '/unit/cart', icon: <ShoppingCartOutlined />, label: '采购清单' },
    { key: '/unit/orders', icon: <FileTextOutlined />, label: '我的订单' },
    { key: '/account', icon: <UserOutlined />, label: '账号信息' },
  ];
}

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const mode = useResponsiveMode();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const items = user?.role === 'admin' ? adminItems() : unitItems();
  const selectedKey = useMemo(() => {
    const flatten = (menuItems: MenuProps['items']): string[] =>
      (menuItems || []).flatMap((item) => {
        if (!item || !('key' in item)) return [];
        if ('children' in item && item.children) return flatten(item.children);
        return [String(item.key)];
      });
    return flatten(items).find((key) => key !== '__logout' && location.pathname === key.split('?')[0]) || location.pathname;
  }, [items, location.pathname]);
  const pageTitle = useMemo(() => {
    const titles: Record<string, string> = {
      '/admin/dashboard': '工作台',
      '/admin/orders': '订单管理',
      '/admin/preparation': '今日备货',
      '/admin/delivery': '单位配送',
      '/admin/ledger': '采购台账',
      '/admin/receipt-issues': '收货异常',
      '/admin/products': '食材列表',
      '/admin/inventory': '库存记录',
      '/admin/units': '子单位管理',
      '/admin/users': '账号管理',
      '/admin/settings': '系统状态',
      '/account': '当前账号',
      '/unit/products': '食材申领',
      '/unit/cart': '采购清单',
      '/unit/orders': '我的订单',
    };
    return titles[location.pathname] || '景荣鲜配';
  }, [location.pathname]);
  const businessDateText = useMemo(() => {
    const formatter = new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    });
    return formatter.format(new Date());
  }, []);

  const menu = (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      defaultOpenKeys={user?.role === 'admin' && mode !== 'tablet' ? ['purchase', 'products', 'organization', 'reports', 'system'] : undefined}
      items={items}
      onClick={({ key }) => {
        if (key === '__logout') {
          void logout();
          setDrawerOpen(false);
          return;
        }
        navigate(key);
        setDrawerOpen(false);
      }}
      className={user?.role === 'admin' ? 'admin-sidebar-menu' : undefined}
      style={{ borderInlineEnd: 0, paddingTop: 8 }}
    />
  );

  return (
    <Layout className={user?.role === 'admin' ? 'admin-shell' : undefined} style={{ minHeight: '100vh' }}>
      {user?.role === 'admin' && mode !== 'mobile' ? (
        <Sider
          width={240}
          collapsedWidth={76}
          collapsed={mode === 'tablet'}
          className="admin-sidebar no-print"
          style={{ background: policeTokens.PoliceNavy }}
        >
          <div className="admin-sidebar-brand">
            <img src="/brand/app-icon.svg" alt="" />
            {mode === 'desktop' ? (
              <div>
                <strong>景荣鲜配</strong>
                <span>公安后勤采购管理</span>
              </div>
            ) : null}
          </div>
          {menu}
        </Sider>
      ) : null}
      <Layout>
      <Header className="admin-topbar no-print">
        <Space size={14}>
          {mode === 'mobile' ? (
            <Button aria-label="打开菜单" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} />
          ) : null}
          <div className="admin-page-title">
            <Typography.Title level={1}>{pageTitle}</Typography.Title>
            <Typography.Text>{businessDateText}</Typography.Text>
          </div>
        </Space>
        <Dropdown
          menu={{
            items: [
              { key: 'account', label: '账号信息', icon: <UserOutlined /> },
              { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, danger: true },
            ],
            onClick: async ({ key }) => {
              if (key === 'account') navigate('/account');
              if (key === 'logout') await logout();
            },
          }}
        >
          <Button type="text" style={{ minHeight: 44 }}>
            <Space>
              <span>{user?.display_name || user?.username}</span>
              <Typography.Text type="secondary">{user ? roleText(user.role) : ''}</Typography.Text>
            </Space>
          </Button>
        </Dropdown>
      </Header>
      <Layout>
        {user?.role !== 'admin' && mode === 'desktop' ? (
          <Sider width={220} className="no-print" style={{ borderRight: `1px solid ${policeTokens.DividerColor}` }}>
            {menu}
          </Sider>
        ) : user?.role !== 'admin' && mode !== 'desktop' ? (
          <Drawer title="功能菜单" placement="left" width={280} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
            {menu}
          </Drawer>
        ) : mode === 'mobile' ? (
          <Drawer title="功能菜单" placement="left" width={304} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
            <div className="admin-drawer-brand">
              <img src="/brand/app-icon.svg" alt="" />
              <div>
                <strong>景荣鲜配</strong>
                <span>公安后勤采购管理</span>
              </div>
            </div>
            {menu}
          </Drawer>
        ) : null}
        <Content className={user?.role === 'admin' ? 'admin-content' : undefined}>
          <Outlet />
        </Content>
      </Layout>
      </Layout>
    </Layout>
  );
}
