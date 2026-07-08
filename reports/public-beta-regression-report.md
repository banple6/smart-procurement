# 公测前回归验证报告

生成时间：2026-07-08 19:38:57 CST

## 后端

命令：

```bash
source server/.venv/bin/activate
python -m py_compile server/scripts/audit_public_beta_data.py server/scripts/cleanup_public_beta_test_data.py
python server/scripts/audit_public_beta_data.py --output-dir reports
python server/scripts/cleanup_public_beta_test_data.py
python -m pytest server/tests -q
```

结果：

- 脚本编译通过。
- 数据审计完成并生成 `reports/public-beta-data-audit.*`。
- 清理脚本 dry-run 完成，未删除数据。
- `45 passed, 628 warnings in 26.50s`。
- 警告为 FastAPI/Starlette `asyncio.iscoroutinefunction` 与 `on_event` 弃用警告，非本轮新增失败。

## Android

命令：

```bash
./gradlew test --console=plain --no-configuration-cache
./gradlew lint --console=plain --no-configuration-cache
./gradlew assembleDebug --console=plain --no-configuration-cache
./gradlew assembleStaging --console=plain --no-configuration-cache
./gradlew connectedDebugAndroidTest --console=plain --no-configuration-cache
```

结果：

- 单元测试：`BUILD SUCCESSFUL in 1m 44s`。
- Lint：`BUILD SUCCESSFUL in 1m 59s`。
- Debug 构建：`BUILD SUCCESSFUL in 20s`。
- Staging 构建：`BUILD SUCCESSFUL in 1m 1s`。
- 真机仪器测试：设备 `24094RAD4C - 16`，`Finished 1 tests`，`BUILD SUCCESSFUL in 1m 41s`。

## 产物

- `/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/debug/app-debug.apk`
- `/Users/tianboyu/Downloads/智慧后勤采购/app/build/outputs/apk/staging/app-staging.apk`
- `/Users/tianboyu/Downloads/智慧后勤采购/app/build/reports/lint-results-debug.html`

## 未验证

- 未执行生产服务器部署后冒烟。
- 未执行生产 Web 下载入口实际下载。
- 未执行完整人工 App 业务链路截图复核。
