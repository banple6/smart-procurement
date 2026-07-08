# 公测上线回滚方案

## 触发条件

出现任一情况立即评估回滚：

- 登录大面积失败。
- App 下载或安装包签名异常。
- Web 管理员无法接单、备货、发货。
- 子单位无法提交订单。
- 出现跨单位数据可见。
- 出现负库存、重复扣减或订单残缺。
- 持续 HTTP 5xx。
- 持续 `database is locked`。
- 容器反复重启或 OOM。

## 回滚前保留现场

```bash
docker ps -a
docker logs --tail=300 app-api-1
docker logs --tail=300 app-nginx-1
curl -i http://127.0.0.1/api/v1/health
curl -i http://127.0.0.1/api/v1/health/ready
```

保存：

- 当前容器状态。
- API/Nginx 最近日志。
- 当前数据库副本。
- 当前上传目录只读清单。

## 应用版本回滚

1. 停止新版本容器。
2. 启动部署前记录的旧版本镜像/容器。
3. 不修改数据库文件。
4. 验证：
   - `/api/v1/health`
   - `/api/v1/health/ready`
   - `/login`
   - `/admin/dashboard` 未登录跳转

## 数据库回滚

仅在确认数据库已被错误写入且应用回滚无法恢复业务时执行。

步骤：

1. 停止 API 容器。
2. 复制当前异常数据库到备份目录，保留现场。
3. 使用上线前 SQLite `.backup` 文件恢复。
4. 校验 SHA-256 和文件大小。
5. 执行：

```sql
PRAGMA integrity_check;
PRAGMA quick_check;
```

6. 启动 API 容器。
7. 重新验证 health、ready、登录页、订单列表、库存列表。

## App 更新回滚

1. 在后台暂停或撤销异常 release。
2. 发布上一版 APK 为 production 通道。
3. 确认 `/api/v1/app-update/latest` 不再返回异常版本。
4. 真机重新检查更新。

## 回滚后检查

- [ ] 正式服务 health 正常。
- [ ] ready 正常。
- [ ] 管理员可登录。
- [ ] 子单位可登录。
- [ ] 订单列表可加载。
- [ ] 食材列表可加载。
- [ ] negative_stock = 0。
- [ ] 无跨单位数据可见。
- [ ] 回滚过程记录到上线日志。
