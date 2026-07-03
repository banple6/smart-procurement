import { getJson, postJson } from './client';
import type { UserProfile } from '@/types/domain';

export interface WebQrChallenge {
  challenge_id: string;
  qr_payload: string;
  qr_svg_data_url: string;
  status: WebQrStatus;
  expires_at: number;
  server_now: number;
}

export type WebQrStatus = 'pending' | 'scanned' | 'approved' | 'rejected' | 'expired' | 'consumed';

export const authApi = {
  createQrChallenge() {
    return postJson<WebQrChallenge>('/web-auth/qr/challenges');
  },
  qrStatus(challengeId: string) {
    return getJson<Omit<WebQrChallenge, 'qr_payload' | 'qr_svg_data_url'>>(`/web-auth/qr/challenges/${challengeId}/status`);
  },
  consumeQrChallenge(challengeId: string) {
    return postJson<{ ok: boolean; user: UserProfile; idle_expires_in: number; absolute_expires_in: number }>(`/web-auth/qr/challenges/${challengeId}/consume`);
  },
  me() {
    return getJson<UserProfile>('/web-auth/me');
  },
  logout() {
    return postJson<{ ok: boolean }>('/web-auth/logout');
  },
  changePassword(payload: { old_password: string; new_password: string }) {
    return postJson<{ ok: boolean }>('/auth/change-password', payload);
  },
};
