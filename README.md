# 景荣鲜配

XX 公安局后勤食材采购配送系统，包含 Android 客户端和 FastAPI 后端。

## 生鲜后勤后端部署

后端位于 `server/`，生产目录为 `/opt/smart-procurement/`。迁移一台新服务器只需要迁移：

- `/opt/smart-procurement/data/smart_procurement.db`
- `/opt/smart-procurement/uploads/`
- `/opt/smart-procurement/private_uploads/`
- `/opt/smart-procurement/app/.env`

手动备份：

```bash
ssh aliyun-procurement "cd /opt/smart-procurement/app && docker compose exec api bash /app/backup.sh"
```

备份文件位于 `/opt/smart-procurement/backups/`，默认保留最近 14 天。恢复时停止容器，按原相对路径恢复数据库、普通商品 `uploads` 和发货凭证 `private_uploads`，再重新启动 `docker compose up -d`。恢复后检查订单详情中的发货照片是否能通过登录账号读取。

管理员密码重置：

```bash
ssh aliyun-procurement
cd /opt/smart-procurement/app
read -s -p "New admin password: " NEW_ADMIN_PASSWORD
echo
export NEW_ADMIN_PASSWORD
docker compose exec -e NEW_ADMIN_PASSWORD api python -m app.cli reset-admin-password proc_admin
unset NEW_ADMIN_PASSWORD
```

重置工具只从临时环境变量读取新密码，不会打印密码值。

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Run the app on an emulator or physical device
