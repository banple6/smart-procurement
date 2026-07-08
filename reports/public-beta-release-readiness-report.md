# 公测上线收口总报告

生成时间：2026-07-08 19:38:57 CST

## 结论

最终结论：有条件进入公测。

判断依据：

- 上一阶段同机隔离高负载测试结论为“可以上线”。
- 本轮本地收口改动已通过后端测试、Android 单元测试、lint、debug/staging 构建和真机仪器测试。
- 本轮未执行生产部署，未执行部署后的线上 Web/App 冒烟，因此不能直接标记为“可以进入公测”。
- 当前状态适合作为“待部署公测候选版本”，生产发布前必须完成本文末尾的发布前必做项。

## 1. 正式库备份文件

上一阶段已记录的正式库备份：

- `/opt/smart-procurement/backups/pre-loadtest/smart_procurement_pre_loadtest_20260707202452.db`

说明：

- 该备份来自高负载测试前置与恢复验证阶段。
- 本轮按“不得生产部署、不得覆盖正式库”的约束，没有重新创建生产备份。

## 2. 正式库备份 SHA-256

上一阶段记录：

- `a4bcd208633ec9686a0e1599c888a990d455f7dd9df1b061991568a638a42054`

## 3. ECS 快照

上一阶段用户确认：

- 快照 ID：`s-2ze63b8hk0gnt2f8gu2i`
- 创建时间：`2026-07-07 23:20:32`

本轮未重新创建 ECS 快照。

## 4. 测试数据审计结果

报告文件：

- `reports/public-beta-data-audit.md`
- `reports/public-beta-data-audit.json`
- `reports/public-beta-data-audit.csv`

本地库检查：

- integrity_check：ok
- quick_check：ok
- 表内疑似测试数据：0
- `loadtest_environment` 记录：1，用于提醒确认压测环境隔离
- `server/releases/staging/` 疑似 staging APK：19

## 5. 实际清理的数据

- 实际删除数量：0
- 本轮只执行 dry-run，不删除正式数据。
- 未执行模糊 `DELETE`。
- 未覆盖正式数据库。

清理 dry-run 报告：

- `reports/public-beta-data-cleanup-report.json`
- `reports/public-beta-data-cleanup-report.md`

## 6. 未清理但需人工确认的数据

- `server/releases/staging/` 下 19 个 staging APK。
- 处理建议：确认不再需要后再人工清理或迁移归档；不得让 staging APK 出现在 production 下载入口。

## 7. 压测数据是否保留备份

上一阶段已记录压测库备份：

- `/srv/smart-procurement-loadtest/backups/loadtest-after-highload-20260708153639.db`
- SHA-256：`a21f85e061f2a3b343ee0db6b3954706c7e730df27e02b14e8d15d786f37db73`

原始压测结果保留在：

- `reports/load/20-users/`
- `reports/load/30-users/`
- `reports/load/30-users-stability/`
- `reports/load/40-users-peak/`
- `reports/load/high-load-final-report.md`

## 8. 压测环境是否关闭

上一阶段报告显示隔离压测容器已停止：

- `smart-procurement-loadtest-api-1`：exited
- `smart-procurement-loadtest-nginx-1`：exited

本轮未重新操作服务器容器。

## 9. App 欢迎页

本轮未大改欢迎页，避免上线前大 UI 变动。

已完成：

- App 内帮助与教程入口已接入。
- 原“查看引导”入口从提示消息改为进入教程页面。

## 10. 管理员引导

已新增管理员教程内容：

- 完善单位信息
- 维护食材与库存
- 处理订单
- 公测检查

位置：

- App：我的 → 帮助与教程
- Web：`/help/admin`

尚未实现：

- 自动打勾的“公测准备 0/6”任务清单。

## 11. 子单位引导

已新增子单位教程内容：

- 提交采购单
- 查看配送进度
- 确认收货
- 网页扫码

位置：

- App：我的 → 帮助与教程
- Web：`/help/unit`

## 12. App 帮助中心

已完成：

- `app/src/main/java/com/example/ui/ExtraScreens.kt` 增加 `HelpTutorialScreen`。
- `app/src/main/java/com/example/ui/Screens.kt` 增加“帮助与教程”菜单项。
- `app/src/main/java/com/example/ui/SupplyViewModel.kt` 将查看引导改为导航到教程页。

## 13. Web 登录页下载入口

已完成：

- 登录页二维码区域下方增加 App 下载面板。
- 文案说明“请先安装三公鲜配 App，使用已登录账号扫码进入网页端”。
- 通过 `/api/v1/app-update/latest` 获取版本信息。
- 不在 HTML 中硬编码生产 IP。

## 14. Web 登录后下载入口

已完成：

- 管理员后台顶部增加“下载 App”“帮助”入口。
- 管理员侧边栏增加“下载 App”“帮助中心”入口。
- 子单位 Web 顶部、首页和我的页面增加下载入口。

## 15. 下载接口

新增接口：

- `GET /api/v1/app-update/latest`
- `GET /api/v1/app-update/latest/download`

安全处理：

- 复用 release 目录路径校验，防止路径穿越。
- 支持 `Range` 下载。
- 返回 APK Content-Type。
- Staging/Loadtest 环境不展示 production 下载入口。

## 16. APK 文件路径

本地构建产物：

- Debug APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/debug/app-debug.apk`
- Staging APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/staging/app-staging.apk`
- AndroidTest APK：`/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## 17. APK 版本号

来自 `app/build.gradle.kts`：

- `versionCode = 6`
- `versionName = "1.1-test5"`

说明：

- 当前本地产物仍是测试命名版本。
- 正式公测 production 发布前建议改为正式版本名，并通过后台发布 production 通道 APK。

## 18. Android 测试结果

命令：

```bash
./gradlew test --console=plain --no-configuration-cache
```

结果：

- `BUILD SUCCESSFUL in 1m 44s`

## 19. Android lint 结果

命令：

```bash
./gradlew lint --console=plain --no-configuration-cache
```

结果：

- `BUILD SUCCESSFUL in 1m 59s`
- 报告：`app/build/reports/lint-results-debug.html`

## 20. 后端 pytest 结果

命令：

```bash
source server/.venv/bin/activate
python -m pytest server/tests -q
```

结果：

- `45 passed, 628 warnings in 26.50s`

## 21. Web 冒烟测试结果

本地自动测试：

- 已通过后端 `server/tests` 中 Web 路由与静态资源测试。
- 新增测试覆盖登录页下载入口、`/download`、`/help`、`/help/admin`、`/help/unit`。

生产环境：

- 本轮未部署生产，因此生产 Web 冒烟未执行。

## 22. Bug 总数

本轮收口识别并修复 5 个上线体验/可用性问题。

## 23. P0/P1/P2/P3 数量

- P0：0
- P1：0
- P2：3
- P3：2

说明：

- P2：登录页无 App 下载入口、登录后无下载入口、缺少 Web 帮助页。
- P3：App 引导入口只弹提示、公共页面复用登录脚本时缺少 DOM 容错。

## 24. 已修复问题

1. Web 登录页增加 App 下载入口。
2. 管理员后台和子单位 Web 增加登录后下载入口。
3. 新增 `/help`、`/help/admin`、`/help/unit`。
4. App “帮助与教程”可从我的页面重新进入。
5. 公共页面加载 `login.js` 不再因缺少二维码 DOM 直接启动轮询。

## 25. 未修复问题

非阻塞待办：

1. App 自动化首次使用任务清单尚未实现。
2. 本地版本名仍是 `1.1-test5`，正式发布前应准备正式 production 版本。
3. `server/releases/staging/` 下 19 个 staging APK 尚未人工归档或清理。
4. 生产部署后 Web/App 下载链路尚未实测。

## 26. 是否存在上线阻塞

代码与本地验证层面：

- 未发现 P0/P1 阻塞。

生产发布层面：

- 存在发布前必做项，未完成前不应宣布“已经正式上线”。

## 27. 回滚方案

详见：

- `reports/public-beta-rollback-runbook.md`

摘要：

1. 保留当前生产数据库备份和 ECS 快照。
2. 部署前记录当前镜像/容器/代码版本。
3. 若新版本异常，先停止新容器并恢复旧容器。
4. 若数据库发生不可接受异常，使用部署前 SQLite 备份恢复。
5. 恢复后验证 health、ready、登录页、订单只读列表和库存一致性。

## 28. 是否建议进入公测

建议：有条件进入公测。

进入条件：

1. 部署前重新备份生产库并记录 SHA-256。
2. 确认 ECS 快照可用。
3. 发布 production 通道正式 APK。
4. 部署后完成 `reports/public-beta-production-smoke-checklist.md`。
5. 确认没有测试 APK、测试账号、测试订单进入正式业务统计。

## 本轮未执行的操作

- 未执行 `git push`。
- 未执行 `git pull`。
- 未执行 `git fetch`。
- 未执行 `gh pr create`。
- 未执行生产部署。
- 未删除生产数据。
- 未修改生产服务器配置。
