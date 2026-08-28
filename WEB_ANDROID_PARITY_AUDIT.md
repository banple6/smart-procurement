# Web / Android 功能差异审计

审计基线：`499b3e8`（`codex/app-web-notifications`）  
审计范围：管理员 Web、子单位 Web、Android 管理端、Android 子单位端及现有 FastAPI 路由。  
结论时间：2026-08-28。

## 状态定义

| 状态 | 含义 |
| --- | --- |
| PARITY | 两端均有真实入口，调用同一服务端能力。 |
| PARTIAL | 已有部分页面或接口，但关键流程、入口或状态处理不完整。 |
| MISSING | 一端不存在所需的真实能力。 |
| WEB_ONLY_BY_DESIGN | Web 管理工具，不应强制复制到移动端。 |
| ANDROID_ONLY_BY_DESIGN | 设备能力或移动端特性，不应复制到 Web。 |
| BROKEN | 存在错误入口或与当前服务端流程不一致。 |

## 功能矩阵

| Module | Web | Android Admin | Android Unit | API | Gap | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| 管理员工作台 | 完整工作台、待办、快捷入口 | 完整工作台、待办、订单/批次/导入快捷入口 | 不适用 | `/admin/dashboard` | PARITY | P0 |
| 食材目录/详情 | 列表、详情、状态/库存/价格维护 | 列表、详情、创建、编辑、库存和价格操作 | 只读选购 | `/products`, `/admin/products` | PARITY | P0 |
| 子单位清单与下单 | 子单位 Web 可提交订单 | 管理员不适用 | 清单、数量、提交、历史订单 | `/orders` | PARITY | P0 |
| 订单列表/详情 | 管理员与子单位完整列表和详情 | 订单列表、详情、接单、取消、收货 | 我的订单、详情、收货 | `/orders`, `/admin/orders` | PARITY | P0 |
| 接单后批次组织 | Web 接单后预选进入备货单 | 一次性事件进入批次创建并预选订单 | 不适用 | `/admin/orders/{id}/status`, `/admin/batches/eligible-orders` | PARITY | P0 |
| 备货单创建/汇总/完成 | 创建、详情、按单位/食材汇总、完成、导出 | 创建、详情、按单位/食材汇总、完成、SAF 导出 | 不适用 | `/admin/batches` | PARITY | P0 |
| 出库单生成 | 已完成备货单可按单位生成出库单 | 没有出库单列表/详情入口；批次详情不能生成 | 不适用 | `/admin/outbounds/from-batch/{id}` | MISSING | P0 |
| 出库单列表/详情 | 完整列表、筛选、详情、Excel 导出 | 无对应 `Outbound` 模型、页面或导航 | 不适用 | `/admin/outbounds`, `/admin/outbounds/{id}` | MISSING | P0 |
| 出库单照片发货 | 出库单详情上传照片并确认发货 | 仅保留旧的“单订单发货凭证”页面，调用 `/admin/orders/{id}/ship`，与当前单位出库单流程不一致 | 收货由订单完成 | `/admin/outbounds/{id}/ship` | BROKEN | P0 |
| 子单位确认收货 | 订单详情确认收货 | 订单详情确认收货 | 订单详情确认收货 | `/orders/{id}/confirm-receipt` | PARITY | P0 |
| 数据分析 | 总览、单位、价格、库存分析 | 总览/食材分析 | 无管理员分析权限 | `/admin/analytics/*` | PARITY | P1 |
| 采购台账与导出 | 筛选、分页、Excel 导出 | 台账读取和 SAF 导出；筛选较少 | 不适用 | `/admin/ledger` | PARTIAL | P1 |
| 库存记录 | 管理员库存记录页 | 库存记录入口 | 不适用 | `/admin/products`, 库存调整 API | PARTIAL | P1 |
| Excel 智能导入 | 上传、字段映射、预览、默认值、应用 | SAF 上传、字段映射、人工关联、新品默认值、应用 | 不适用 | `/admin/price-imports` | PARITY | P1 |
| 子单位管理 | 新增、编辑、启停、关联账号入口 | 列表、创建子单位账号为主；编辑/状态能力需审计后补 | 不适用 | `/admin/units` | PARTIAL | P2 |
| 账号管理 | 新增单位/管理员账号、权限、重置、启停 | 用户列表、单位账号创建、状态/重置的基础能力；管理员创建和权限呈现需审计后补 | 个人资料 | `/admin/users`, `/admin/accounts/*` | PARTIAL | P2 |
| 系统状态 | Web 系统页 | 系统状态页 | 不适用 | `/admin/system/overview` | PARITY | P2 |
| Web 会话/扫码登录 | Web 会话管理 | 扫码、确认、会话撤销 | 同 | `/mobile/web-sessions/*` | PARITY | P2 |
| 推送与前后台同步 | RefreshManager + focus/visibility/online | Room 缓存、前后台刷新、JPush 失效通知、单飞刷新 | 同 | 现有列表/详情 API | PARITY | Cross-cutting |

## 已确认的主路径问题

1. Android 管理员可完成接单和备货单，但批次完成后没有“生成出库单”的下一步。
2. Android 没有 `/admin/outbounds` 的解析、缓存模型、列表或详情页面。
3. `ShippingProofScreen` 面向单一 `orderId`，调用旧的 `/admin/orders/{id}/ship`。正式按单位出库流程应由 `/admin/outbounds/{outbound_id}/ship` 驱动，不能把一个备货单中的多个订单拆回单订单发货。
4. 出库单 Excel 的服务端生成能力已存在；Android 需要通过 SAF 在明确点击导出后保存。

## 本轮实施边界

本轮优先完成 P0 出库单链路：

1. 增加 Android 出库单模型、JSON 解析和 `/admin/outbounds` API 调用。
2. 在完成备货的批次详情中提供“生成出库单”。
3. 增加出库单列表、详情、Excel 导出和带照片的发货页面。
4. 将管理员订单详情中的“确认发货”引导到所属出库单，而不是继续把单订单发货作为主流程；若订单尚无出库单，明确提示先完成备货并生成出库单。

不改动 FastAPI 业务状态机、SQLite 表、历史订单价格快照、库存事务或多管理员同步机制。
