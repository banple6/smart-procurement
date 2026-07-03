import { Alert, Spin } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/auth/AuthContext';

export function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spin fullscreen tip="正在加载账号信息" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.must_change_password && location.pathname !== '/account') return <Navigate to="/account" replace />;
  return <Outlet />;
}

export function RequireAdmin() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'admin') {
    return <Navigate to="/unit/products" replace state={{ warning: '当前账号无此操作权限' }} />;
  }
  return <Outlet />;
}

export function RequireUnitUser() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'unit_user') {
    return <Navigate to="/admin/dashboard" replace state={{ warning: '当前账号无此操作权限' }} />;
  }
  return <Outlet />;
}

export function ForbiddenInline() {
  return <Alert type="warning" showIcon message="当前账号无此操作权限" />;
}
