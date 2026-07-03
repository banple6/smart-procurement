import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

export function NotFoundPage() {
  const navigate = useNavigate();
  return <Result status="404" title="页面不存在" subTitle="数据不存在或您无权查看" extra={<Button type="primary" onClick={() => navigate('/')}>返回首页</Button>} />;
}
