# 景荣鲜配 Web

独立网站前端，复用现有 FastAPI `/api/v1`、SQLite 数据库、商品、库存、订单和账号体系。

## 本地开发

```bash
npm ci
npm run api:generate
npm run dev
```

Vite 开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8000`。

## 构建

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

生产产物位于 `web/dist/`，由 Nginx 静态托管，不需要生产 Node.js 进程。
