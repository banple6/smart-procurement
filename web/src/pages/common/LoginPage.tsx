import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Spin, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { authApi, type WebQrChallenge, type WebQrStatus } from '@/api/auth';
import { LoginBrandPanel } from '@/components/Brand';
import { useAuth } from '@/auth/AuthContext';
import { landingPathForRole } from '@/utils/navigation';

const statusText: Record<WebQrStatus, string> = {
  pending: '等待 App 扫码',
  scanned: '已扫码，请在 App 上确认',
  approved: '已确认，正在进入系统',
  rejected: '已拒绝，请刷新二维码',
  expired: '二维码已过期',
  consumed: '已登录',
};

const statusHelp: Record<WebQrStatus, string> = {
  pending: '打开 Android App，在“我的”页面点“扫码登录网页版”，对准二维码扫描。',
  scanned: '请在手机上核对浏览器信息后点击确认登录。',
  approved: '正在建立网页版登录，请稍候。',
  rejected: '本次登录已在手机上拒绝，可刷新二维码重新登录。',
  expired: '二维码有效期 120 秒，请刷新后重新扫码。',
  consumed: '登录成功。',
};

export function LoginPage() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [challenge, setChallenge] = useState<WebQrChallenge | null>(null);
  const [status, setStatus] = useState<WebQrStatus>('pending');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const consumingRef = useRef(false);

  const loadChallenge = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      consumingRef.current = false;
      const next = await authApi.createQrChallenge();
      setChallenge(next);
      setStatus(next.status);
    } catch (err) {
      setError(err instanceof Error ? err.message : '二维码加载失败，请稍后重试');
      setChallenge(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadChallenge();
  }, [loadChallenge]);

  useEffect(() => {
    if (!challenge || ['rejected', 'expired', 'consumed'].includes(status)) return;
    const poll = window.setInterval(async () => {
      try {
        const next = await authApi.qrStatus(challenge.challenge_id);
        setStatus(next.status);
        setChallenge((current) => (current ? { ...current, ...next } : current));
        if (next.status === 'approved' && !consumingRef.current) {
          consumingRef.current = true;
          const consumed = await authApi.consumeQrChallenge(challenge.challenge_id);
          setStatus('consumed');
          const profile = await refresh();
          navigate(landingPathForRole((profile ?? consumed.user).role), { replace: true });
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : '登录状态获取失败，请刷新二维码');
      }
    }, 1800);
    return () => window.clearInterval(poll);
  }, [challenge, navigate, refresh, status]);

  const secondsLeft = useMemo(() => {
    if (!challenge) return 0;
    return Math.max(0, challenge.expires_at - challenge.server_now);
  }, [challenge]);

  if (user) return <Navigate to={landingPathForRole(user.role)} replace />;

  return (
    <main className="login-page">
      <LoginBrandPanel />
      <section className="login-form-section">
        <div className="login-form-box login-qr-box">
          <Typography.Title level={2}>扫码登录网页版</Typography.Title>
          <Typography.Paragraph type="secondary">网页版只支持已登录 Android App 的账号扫码确认。</Typography.Paragraph>

          <div className="login-qr-panel">
            {loading ? <Spin tip="正在生成二维码" /> : null}
            {!loading && challenge ? <img className="login-qr-image" src={challenge.qr_svg_data_url} alt="网页登录二维码" /> : null}
            {!loading && !challenge ? <div className="login-qr-empty">二维码加载失败</div> : null}
          </div>

          <div className="login-qr-status">
            <strong>{statusText[status]}</strong>
            <span>{statusHelp[status]}</span>
            {challenge && status === 'pending' ? <span>剩余约 {secondsLeft} 秒</span> : null}
          </div>

          {error ? <Alert type="error" showIcon message={error} /> : null}

          <Button icon={<ReloadOutlined />} size="large" block onClick={loadChallenge} loading={loading} style={{ minHeight: 48 }}>
            刷新二维码
          </Button>
        </div>
      </section>
    </main>
  );
}
