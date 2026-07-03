import { Navigate, createBrowserRouter } from 'react-router-dom';
import { Suspense, lazy, type ComponentType } from 'react';
import { Spin } from 'antd';
import { AppShell } from '@/layouts/AppShell';
import { RequireAdmin, RequireAuth, RequireUnitUser } from '@/routes/RequireAuth';
import { LoginPage } from '@/pages/common/LoginPage';
import { NotFoundPage } from '@/pages/common/NotFoundPage';

const AccountPage = lazy(() => import('@/pages/common/AccountPage').then((module) => ({ default: module.AccountPage })));
const DashboardPage = lazy(() => import('@/pages/admin/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const ProductsPage = lazy(() => import('@/pages/admin/ProductsPage').then((module) => ({ default: module.ProductsPage })));
const OrdersPage = lazy(() => import('@/pages/admin/OrdersPage').then((module) => ({ default: module.OrdersPage })));
const UnitsPage = lazy(() => import('@/pages/admin/UnitsPage').then((module) => ({ default: module.UnitsPage })));
const UsersPage = lazy(() => import('@/pages/admin/UsersPage').then((module) => ({ default: module.UsersPage })));
const LedgerPage = lazy(() => import('@/pages/admin/LedgerPage').then((module) => ({ default: module.LedgerPage })));
const PreparationPage = lazy(() => import('@/pages/admin/PreparationPage').then((module) => ({ default: module.PreparationPage })));
const DeliverySheetsPage = lazy(() => import('@/pages/admin/DeliverySheetsPage').then((module) => ({ default: module.DeliverySheetsPage })));
const ReceiptIssuesPage = lazy(() => import('@/pages/admin/ReceiptIssuesPage').then((module) => ({ default: module.ReceiptIssuesPage })));
const InventoryPage = lazy(() => import('@/pages/admin/InventoryPage').then((module) => ({ default: module.InventoryPage })));
const SettingsPage = lazy(() => import('@/pages/admin/SettingsPage').then((module) => ({ default: module.SettingsPage })));
const UnitProductsPage = lazy(() => import('@/pages/unit/UnitProductsPage').then((module) => ({ default: module.UnitProductsPage })));
const UnitCartPage = lazy(() => import('@/pages/unit/UnitCartPage').then((module) => ({ default: module.UnitCartPage })));
const UnitOrdersPage = lazy(() => import('@/pages/unit/UnitOrdersPage').then((module) => ({ default: module.UnitOrdersPage })));

function lazyPage(Page: ComponentType) {
  return (
    <Suspense fallback={<Spin fullscreen tip="正在加载页面" />}>
      <Page />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <Navigate to="/unit/products" replace /> },
          { path: '/account', element: lazyPage(AccountPage) },
          {
            element: <RequireAdmin />,
            children: [
              { path: '/admin/dashboard', element: lazyPage(DashboardPage) },
              { path: '/admin/products', element: lazyPage(ProductsPage) },
              { path: '/admin/orders', element: lazyPage(OrdersPage) },
              { path: '/admin/preparation', element: lazyPage(PreparationPage) },
              { path: '/admin/delivery', element: lazyPage(DeliverySheetsPage) },
              { path: '/admin/ledger', element: lazyPage(LedgerPage) },
              { path: '/admin/receipt-issues', element: lazyPage(ReceiptIssuesPage) },
              { path: '/admin/units', element: lazyPage(UnitsPage) },
              { path: '/admin/users', element: lazyPage(UsersPage) },
              { path: '/admin/inventory', element: lazyPage(InventoryPage) },
              { path: '/admin/settings', element: lazyPage(SettingsPage) },
            ],
          },
          {
            element: <RequireUnitUser />,
            children: [
              { path: '/unit/products', element: lazyPage(UnitProductsPage) },
              { path: '/unit/cart', element: lazyPage(UnitCartPage) },
              { path: '/unit/orders', element: lazyPage(UnitOrdersPage) },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]);
