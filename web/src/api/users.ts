import { getJson, patchJson, postJson, putJson } from './client';
import type { Role } from '@/types/domain';

export interface AdminUser {
  id: string;
  username: string;
  display_name: string;
  role: Role;
  unit_id: string;
  unit_name?: string;
  active: boolean | number;
  must_change_password: boolean | number;
  last_login_at?: string;
  created_at?: string;
}

export const usersApi = {
  list() {
    return getJson<AdminUser[]>('/admin/users');
  },
  create(payload: { username: string; password: string; display_name: string; unit_id: string; role?: Role; must_change_password?: boolean }) {
    return postJson<AdminUser & { initial_password: string; message: string }>('/admin/users', {
      ...payload,
      role: 'unit_user',
      must_change_password: payload.must_change_password ?? true,
    });
  },
  update(userId: string, payload: Partial<AdminUser>) {
    return putJson<AdminUser>(`/admin/users/${userId}`, payload);
  },
  resetPassword(userId: string, payload: { new_password: string; must_change_password?: boolean }) {
    return postJson<{ ok: boolean; message: string; initial_password: string }>(`/admin/users/${userId}/reset-password`, {
      ...payload,
      must_change_password: payload.must_change_password ?? true,
    });
  },
  revokeSessions(userId: string) {
    return postJson<{ message: string }>(`/admin/users/${userId}/revoke-sessions`);
  },
  setStatus(userId: string, active: boolean) {
    return patchJson<AdminUser>(`/admin/users/${userId}/status`, { active });
  },
};
