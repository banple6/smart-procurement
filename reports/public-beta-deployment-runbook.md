# 公测生产发布执行清单

本文件只提供发布步骤。本轮未执行生产部署。

## 一、发布前确认

- [ ] 当前代码已完成本地测试。
- [ ] 当前代码已完成必要代码评审。
- [ ] 已确认维护窗口。
- [ ] 已通知相关人员。
- [ ] 已确认无正式用户正在操作。
- [ ] 已确认 ECS 快照存在。
- [ ] 已确认生产数据库备份路径和 SHA-256。
- [ ] 已确认 APK production 版本号。

## 二、备份正式库

示例命令，需按服务器实际路径执行：

```bash
cd /opt/smart-procurement
mkdir -p backups/pre-public-beta
sqlite3 data/smart_procurement.db ".backup 'backups/pre-public-beta/smart_procurement_$(date +%Y%m%d%H%M%S).db'"
sha256sum backups/pre-public-beta/*.db
sqlite3 data/smart_procurement.db "PRAGMA integrity_check;"
sqlite3 data/smart_procurement.db "PRAGMA quick_check;"
```

## 三、部署代码

要求：

- 不覆盖 `.env`。
- 不覆盖 `data/`。
- 不覆盖 `uploads/`。
- 不覆盖 `backups/`。
- 不覆盖 release APK 文件，除非明确发布新 APK。

示例同步排除项：

```bash
rsync -av \
  --exclude '.env' \
  --exclude 'data/' \
  --exclude 'uploads/' \
  --exclude 'private_uploads/' \
  --exclude 'backups/' \
  --exclude '__pycache__/' \
  ./ server:/opt/smart-procurement/
```

## 四、重建服务

示例：

```bash
cd /opt/smart-procurement
docker compose build
docker compose up -d
docker compose ps
```

## 五、健康检查

```bash
curl -fsS http://127.0.0.1/api/v1/health
curl -fsS http://127.0.0.1/api/v1/health/ready
```

预期：

```json
{"status":"ok"}
{"status":"ready"}
```

## 六、发布 APK

1. 管理员进入 App 更新管理。
2. 上传 production 通道 APK。
3. 校验包名、版本号、签名摘要。
4. 发布前确认 manifest 签名。
5. 发布后检查：

```bash
curl -fsS https://生产域名/api/v1/app-update/latest
```

## 七、部署后冒烟

按以下清单执行：

- `reports/public-beta-production-smoke-checklist.md`

## 八、失败回滚

按以下方案执行：

- `reports/public-beta-rollback-runbook.md`

## 九、发布记录

发布完成后记录：

- 发布时间；
- 执行人；
- 代码版本；
- Docker 镜像；
- APK 版本；
- 数据库备份路径；
- 数据库备份 SHA-256；
- ECS 快照 ID；
- 冒烟测试结果；
- 是否发生回滚。
