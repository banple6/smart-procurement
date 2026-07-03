import type { Role } from '@/types/domain';

export function landingPathForRole(role: Role): string {
  return role === 'admin' ? '/admin/dashboard' : '/unit/products';
}

export function canOpenAdmin(role: Role): boolean {
  return role === 'admin';
}

export function canOpenUnit(role: Role): boolean {
  return role === 'unit_user';
}
