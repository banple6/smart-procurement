import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { App as AntApp } from 'antd';
import { authApi } from '@/api/auth';
import type { UserProfile } from '@/types/domain';

interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  logout: () => Promise<void>;
  refresh: () => Promise<UserProfile | null>;
  clearSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const { message } = AntApp.useApp();

  const clearSession = useCallback(() => setUser(null), []);

  const refresh = useCallback(async () => {
    try {
      const profile = await authApi.me();
      setUser(profile);
      return profile;
    } catch {
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const listener = () => {
      setUser(null);
      message.warning('登录已过期，请重新登录');
    };
    window.addEventListener('auth-expired', listener);
    return () => window.removeEventListener('auth-expired', listener);
  }, [message]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({ user, loading, logout, refresh, clearSession }), [user, loading, logout, refresh, clearSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
