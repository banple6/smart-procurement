import { getJson, patchJson, postJson, putJson } from './client';
import type { Unit } from '@/types/domain';

export const unitsApi = {
  list() {
    return getJson<Unit[]>('/admin/units');
  },
  create(payload: { unit_code: string; unit_name: string; default_delivery_point: string; address_note?: string }) {
    return postJson<Unit>('/admin/units', payload);
  },
  update(unitId: string, payload: Partial<Unit>) {
    return putJson<Unit>(`/admin/units/${unitId}`, payload);
  },
  setStatus(unitId: string, active: boolean) {
    return patchJson<Unit>(`/admin/units/${unitId}/status`, { active });
  },
};
