# 公测 App 下载入口报告

生成时间：2026-07-08 19:38:57 CST

## 服务端接口

- 新增 `GET /api/v1/app-update/latest`：返回当前环境、通道和最新可公开下载版本。
- 新增 `GET /api/v1/app-update/latest/download`：下载最新发布 APK。
- 下载文件从服务端 release 目录读取，继续使用现有 `release_storage_path()` 校验，防止路径穿越。
- 下载响应使用 `application/vnd.android.package-archive`。
- 支持普通下载和 `Range` 断点下载。
- Staging/Loadtest 环境不会展示 production 下载入口，避免测试环境暴露正式 APK。

## Web 入口

- 登录页二维码区域下方增加 App 下载面板。
- `/download` 页面展示版本、更新时间、大小、下载按钮、复制链接和安装说明。
- 管理员后台增加“下载 App”“帮助”入口。
- 子单位 Web 首页和我的页面增加下载入口。
- 下载地址由服务端接口返回，未在 HTML 中硬编码生产 IP。

## 当前本地 APK 产物

- Debug APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/debug/app-debug.apk`
- Staging APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/staging/app-staging.apk`
- AndroidTest APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## 风险与待办

- 本地 `server/releases/staging/` 下存在 19 个 staging APK，已在数据审计中标记为“人工确认后再清理文件”。
- 未执行生产部署，因此生产 Web 下载入口尚未验证。
- 正式发布前需确认 production 通道已有已发布、签名正确、版本号递增的 APK。
