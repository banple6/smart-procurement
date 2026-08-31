"use strict";

const assert = require("node:assert/strict");
const policy = require("../app/static/admin/refresh-policy.js");

const idle = {
  formDirty: false,
  selectionCount: 0,
  dialogOpen: false,
  mutationInFlight: 0,
  activeTag: "BODY",
  recentScrollMs: 9000,
  scrollIdleThresholdMs: 5000,
};

assert.equal(policy.shouldDefer(idle), false);
for (const context of [
  { ...idle, recentScrollMs: 100 },
  { ...idle, selectionCount: 1 },
  { ...idle, activeTag: "INPUT" },
  { ...idle, formDirty: true },
  { ...idle, dialogOpen: true },
  { ...idle, mutationInFlight: 1 },
]) {
  assert.equal(policy.shouldDefer(context), true);
}

const original = { total: 1, items: [{ id: "one", status: "pending", version: 1, updated_at: "t1" }] };
assert.equal(policy.orderFingerprint(original), policy.orderFingerprint(structuredClone(original)));
assert.notEqual(policy.orderFingerprint(original), policy.orderFingerprint({ ...original, total: 2 }));
assert.notEqual(policy.orderFingerprint(original), policy.orderFingerprint({ total: 1, items: [{ ...original.items[0], version: 2 }] }));

assert.equal(policy.apiErrorMessage(400, ""), "请求参数错误，请检查后重试");
assert.equal(policy.apiErrorMessage(401, ""), "登录已失效，请重新登录");
assert.equal(policy.apiErrorMessage(403, ""), "没有权限执行该操作");
assert.equal(policy.apiErrorMessage(404, ""), "数据不存在或已被处理");
assert.equal(policy.apiErrorMessage(409, ""), "数据已被其他管理员修改，请刷新后重试");
assert.equal(policy.apiErrorMessage(500, ""), "服务器异常，请稍后重试");
assert.equal(policy.apiErrorMessage(409, "后端明确错误"), "后端明确错误");
assert.equal(policy.hasActiveSelections({ orders: 0, products: 0 }), false);
assert.equal(policy.hasActiveSelections({ orders: 1, products: 0 }), true);

console.log("admin refresh and delete policy tests passed");
