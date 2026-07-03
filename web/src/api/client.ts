import axios, { type AxiosRequestConfig } from 'axios';
import { toChineseError } from '@/utils/errors';

function readCookie(name: string): string {
  const match = document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.split('=')[1] || '') : '';
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  withCredentials: true,
  timeout: 20_000,
});

apiClient.interceptors.request.use((config) => {
  const method = (config.method || 'get').toUpperCase();
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const csrf = readCookie('csrf_token');
    if (csrf) config.headers.set('X-CSRF-Token', csrf);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiError = toChineseError(error);
    if (apiError.status === 401) {
      window.dispatchEvent(new CustomEvent('auth-expired'));
    }
    return Promise.reject(apiError);
  },
);

export async function getJson<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<T>(url, config);
  return response.data;
}

export async function postJson<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.post<T>(url, data, config);
  return response.data;
}

export async function putJson<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.put<T>(url, data);
  return response.data;
}

export async function patchJson<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiClient.patch<T>(url, data);
  return response.data;
}

export async function deleteJson<T>(url: string): Promise<T> {
  const response = await apiClient.delete<T>(url);
  return response.data;
}

export async function downloadBlob(url: string, params?: Record<string, unknown>): Promise<Blob> {
  const response = await apiClient.get(url, { params, responseType: 'blob' });
  return response.data;
}

export function saveBlob(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = href;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(href);
}
