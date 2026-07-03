import axios from 'axios';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
  ) {
    super(message);
  }
}

export function toChineseError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) return new ApiError('操作失败，请稍后重试');
  if (!error.response) return new ApiError('网络连接失败，请稍后重试');
  const status = error.response.status;
  const detail = error.response.data?.detail;
  if (typeof detail === 'string' && detail && status !== 500) return new ApiError(detail, status);
  if (status === 401) return new ApiError('登录已过期，请重新登录', status);
  if (status === 403) return new ApiError('当前账号无此操作权限', status);
  if (status === 404) return new ApiError('数据不存在或您无权查看', status);
  if (status === 409) return new ApiError('数据已变化，请刷新后重试', status);
  return new ApiError('操作失败，请稍后重试', status);
}
