# 公测生产部署记录

部署时间：2026-07-08

## GitHub

- 分支：`main`
- 提交：`99e0320 chore: prepare public beta release`
- 推送结果：`main -> main`
- 远端：`https://github.com/banple6/smart-procurement.git`

## 发布前本地验证

已执行：

```bash
python -m py_compile server/scripts/audit_public_beta_data.py server/scripts/cleanup_public_beta_test_data.py
python -m pytest server/tests -q
./gradlew test lint assembleDebug assembleStaging --console=plain --no-configuration-cache
```

结果：

- 后端：`45 passed, 628 warnings`
- Android test/lint/debug/staging：`BUILD SUCCESSFUL`
- 真机 connected 复跑：未通过，原因是手机系统拒绝安装测试 APK：`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`

说明：上一轮同设备 connected 测试曾通过；本次失败属于设备安装限制/用户确认问题，不是代码编译或单元测试失败。

## 生产备份

备份位置：

- `/app/backups/pre-public-beta/smart_procurement_pre_public_beta_20260708123346.db`

SHA-256：

- `092c33ad2de5b6206330bec4849970e35e2205de08fd97f8971259614a4e0a28`

备份后检查：

- `integrity_check`: `ok`
- `quick_check`: `ok`

## 部署方式

执行：

```bash
REMOTE=aliyun-procurement REMOTE_DIR=/opt/smart-procurement bash server/deploy.sh
```

部署脚本排除：

- `.env`
- `.venv`
- `data`
- `uploads`
- `private_uploads`
- `backups`
- `logs`
- `releases`
- `__pycache__`
- `.pytest_cache`

生产 `.env` 状态：

- `PRIVATE_UPLOAD_DIR` 已存在，部署脚本未新增该配置。
- 当前 `APP_ENV=staging`，未在本轮强行改成 `production`。

## Docker

部署后容器状态：

- `app-api-1`: running, Restart=0, OOM=false
- `app-nginx-1`: running, Restart=0, OOM=false

## 健康检查

服务器本机：

- `/api/v1/health`: `{"status":"ok"}`
- `/api/v1/health/ready`: `{"status":"ready"}`

公网：

- `/api/v1/health`: HTTP 200
- `/api/v1/health/ready`: HTTP 200
- `/login`: HTTP 200
- `/download`: HTTP 200
- `/help`: HTTP 200
- `/help/admin`: HTTP 200
- `/help/unit`: HTTP 200
- `/admin/dashboard`: HTTP 302 -> `/login?expired=1`
- `/unit/home`: HTTP 401，符合未登录状态

页面扫描：

- `/login`、`/download`、`/help`、`/help/admin`、`/help/unit` 未发现 `景荣鲜配`、`智慧后勤采购`、`undefined`、`null`、`XX公安局`。
- 页面包含“三公鲜配”。

## App 下载状态

当前生产服务器环境：

```json
{"environment":"staging","load_test_allowed":false}
```

默认 production 下载接口：

```json
{"available":false,"environment":"staging","channel":"production","message":"暂无可下载版本，请联系管理员"}
```

说明：

- 当前服务器仍使用 `APP_ENV=staging`，因此生产通道下载入口不会展示正式 APK。
- 服务器已有一个 staging 已发布 APK，但标题仍是旧测试文案，不应作为正式公测包直接暴露给客户。
- 正式公测前需要配置 HTTPS/production 环境，并发布 production 通道正式 APK。

## 未完成/风险

1. 未把 `APP_ENV` 切换为 `production`，因为当前公网入口是 HTTP；代码要求 production ready 必须配置 HTTPS `WEB_PUBLIC_ORIGIN`。
2. 未发布 production 通道 APK。
3. 真机 connected 复跑被手机系统安装限制拦截。
4. 需要现场用正式账号做完整 App 流程：登录、下单、接单、备货、发货、确认收货。

## 当前结论

生产 Web/API 部署完成，基础健康检查通过。

公测状态：有条件进入公测。

必须补齐：

- HTTPS/production 环境配置；
- production 通道正式 APK；
- 真机下载和业务闭环冒烟。
