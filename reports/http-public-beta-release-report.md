# HTTP 公测生产发布记录

发布时间：2026-07-08

## 发布范围

- App 启动页警徽显示。
- Web 登录页、下载页、帮助页、管理员后台和子单位门户入口显示警徽。
- Android production 通道 APK：`com.smartprocurement.internal`，`versionCode=7`，`versionName=1.1.0`。
- 服务器切换为 `APP_ENV=production`。
- 临时允许 HTTP production 访问：`ALLOW_INSECURE_PRODUCTION_HTTP=true`。

## 重要说明

当前未配置域名和 HTTPS 证书，本次按 HTTP 公测上线处理，不等同于完整 HTTPS 生产发布。

后续正式上线应补齐：

- 备案域名；
- HTTPS 证书；
- Nginx 443 配置；
- `WEB_PUBLIC_ORIGIN=https://...`；
- `APP_UPDATE_PUBLIC_ORIGIN=https://...`；
- 关闭 `ALLOW_INSECURE_PRODUCTION_HTTP`。

## 数据保护

上线前已完成正式库一致性备份：

- 备份路径：`/app/backups/pre-http-public-beta/smart_procurement_pre_http_public_beta_20260708131915.db`
- SHA-256：`e25f6688a1c16b515543458785736e7de1c92e60854f99e83d265bba4c24e3aa`
- `integrity_check=ok`
- `quick_check=ok`

部署脚本未覆盖 `.env`、数据库、上传图片、私有发货图片、备份和 APK release 目录。

## APK 信息

- 本地 APK：`app/build/outputs/apk/release/app-release.apk`
- 下载文件名：`sangongxianpei-1.1.0.apk`
- 生产 release ID：`6e5e134d-76a5-463b-a95a-a278199b8023`
- 包名：`com.smartprocurement.internal`
- 版本：`1.1.0`
- versionCode：`7`
- minSdk：`24`
- APK 大小：`37949991`
- APK SHA-256：`c8b21abb5aaadaa5a8ea181954412c2105a3357beeb16b43ed8cf6658ced5b8a`
- 签名证书 SHA-256：`5484e173de1fab2e6016d802734779f367cde98c350de22d7575c37db4525068`

说明：当前 APK 使用 Android Debug 证书签名，但与既有 `base.apk` 的包名和签名一致，可覆盖当前测试机安装包。正式企业分发前仍应建立稳定的正式签名 keystore。

## 服务器验证

- `GET /api/v1/health`：`ok`
- `GET /api/v1/health/ready`：`ready`
- `GET /api/v1/system/environment`：`environment=production`，`load_test_allowed=false`
- production 更新接口：可返回 `versionCode=7`
- production 下载接口：支持 Range，完整下载 SHA-256 与本地 APK 一致
- 容器状态：`app-api-1`、`app-nginx-1` 均 running，restart=0，OOM=false
- 发布后正式库：`integrity_check=ok`，`quick_check=ok`

## 本地验证

- `python -m pytest server/tests -q`：通过，`45 passed`
- `./gradlew test lint assembleDebug assembleStaging`：通过
- `ALLOW_INSECURE_HTTP_RELEASE=true API_BASE_URL=http://47.94.227.58/api/v1/ ./gradlew assembleRelease`：通过
- `git diff --check`：通过

## 风险与后续

- 当前为 HTTP 公测，传输链路不具备 HTTPS 机密性与完整性保护。
- APK 更新清单已做 Ed25519 签名，APK 下载后会校验 SHA-256 和签名证书，但网络层仍应尽快升级到 HTTPS。
- 后续拿到域名和证书后，需要重新切换生产环境 origin，并关闭 HTTP 临时开关。
