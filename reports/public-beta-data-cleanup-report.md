# 公测测试数据清理报告

生成时间：2026-07-08 19:38:57 CST

## 执行方式

本轮执行的是 dry-run，不删除任何数据。

命令：

```bash
source server/.venv/bin/activate
python server/scripts/audit_public_beta_data.py --output-dir reports
python server/scripts/cleanup_public_beta_test_data.py
```

## 审计结果

- 数据库：`/Users/tianboyu/Downloads/智慧后勤采购/server/data/smart_procurement.db`
- integrity_check：ok
- quick_check：ok
- 表内疑似测试数据：
  - units：0
  - users：0
  - products：0
  - orders：0
  - order_items：0
  - inventory_logs：0
  - sessions：0
  - web_sessions：0
  - app_releases：0
  - notifications：0
  - QR challenge：0
- 文件类疑似测试数据：
  - uploads：0
  - private_uploads：0
  - backups：0
  - releases：19 个 staging APK

## 处理结果

- 正式业务数据删除数量：0。
- 生产数据库覆盖：未执行。
- 模糊 DELETE：未执行。
- 未备份数据删除：未执行。
- 清理脚本输出：`reports/public-beta-data-cleanup-report.json`。

## 后续建议

- `server/releases/staging/` 下 19 个 APK 需要人工确认是否仍需留存。
- 若要清理正式库中的测试数据，必须先生成明确 ID 白名单，再使用：

```bash
python server/scripts/cleanup_public_beta_test_data.py \
  --allowlist-json reports/public-beta-delete-allowlist.json \
  --execute \
  --confirm DELETE_PUBLIC_BETA_TEST_DATA
```

- 正式执行前脚本会先做 SQLite 一致性备份并生成 SHA-256。
