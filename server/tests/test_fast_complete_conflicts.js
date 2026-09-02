"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
function extractFunction(name) {
  const functionStart = source.indexOf(`function ${name}(`);
  assert.notEqual(functionStart, -1, `missing ${name}`);
  const start = source.slice(Math.max(0, functionStart - 6), functionStart) === "async "
    ? functionStart - 6
    : functionStart;
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`unterminated ${name}`);
}

const sandbox = {};
vm.runInNewContext(`${extractFunction("fastCompleteConflict")}; globalThis.fastCompleteConflict = fastCompleteConflict;`, sandbox);
const classify = sandbox.fastCompleteConflict;
const conflict = (error) => ({ ...classify(error) });

assert.deepEqual(
  conflict({ message: "该订单已经进入出库流程，请使用现有出库单继续处理" }),
  { message: "该订单已经进入出库流程，请使用现有出库单继续处理", refresh: false },
);
assert.deepEqual(
  conflict({ message: "订单已被其他管理员修改，请刷新后重试" }),
  { message: "订单状态已更新，请重新确认。", refresh: true },
);
assert.deepEqual(
  conflict({ message: "订单状态已被其他操作员更新，页面已刷新" }),
  { message: "订单状态已更新，请重新确认。", refresh: true },
);
assert.deepEqual(
  conflict({ message: "当前订单状态不允许完成" }),
  { message: "当前订单状态不允许完成", refresh: false },
);
assert.deepEqual(
  conflict({}),
  { message: "订单状态已更新，请刷新后重新确认。", refresh: true },
);

async function confirmWith(error) {
  const toasts = [];
  const loads = [];
  const elements = {
    cancelFastComplete: { addEventListener() {} },
    confirmFastComplete: {
      addEventListener(_event, callback) { this.callback = callback; },
    },
  };
  const dialog = { close() { this.closed = true; } };
  const runtime = {
    api: async (path) => {
      if (path.endsWith("/complete")) throw error;
      return { id: "order-1", status: "preparing", version: 2, unit_code: "015", unit_name_snapshot: "齐心庄派出所", order_no: "SP20260831-000039", item_count: 1 };
    },
    openOrgDialog: () => dialog,
    $: (id) => elements[id],
    formButtonBusy() {},
    requestId: () => "request-1",
    html: (value) => value,
    num: (value) => value,
    toast: (message) => toasts.push(message),
    loadCurrent: async (...args) => loads.push(args),
  };
  vm.runInNewContext(
    `${extractFunction("fastCompleteConflict")}\n${extractFunction("openFastCompleteReview")}\nglobalThis.openFastCompleteReview = openFastCompleteReview;`,
    runtime,
  );
  await runtime.openFastCompleteReview({ textContent: "完成", disabled: false, dataset: { fastComplete: "order-1" } });
  await elements.confirmFastComplete.callback();
  return { dialog, loads, toasts };
}

async function main() {
  const existing = await confirmWith(Object.assign(new Error("该订单已经进入出库流程，请使用现有出库单继续处理"), { status: 409 }));
  assert.equal(existing.dialog.closed, true);
  assert.deepEqual(existing.toasts, ["该订单已经进入出库流程，请使用现有出库单继续处理"]);
  assert.deepEqual(existing.loads, []);

  const stale = await confirmWith(Object.assign(new Error("订单已被其他管理员修改，请刷新后重试"), { status: 409 }));
  assert.deepEqual(stale.toasts, ["订单状态已更新，请重新确认。"]);
  assert.deepEqual(stale.loads, [[true, "conflict"]]);

  const generic = await confirmWith(Object.assign(new Error("当前订单状态不允许完成"), { status: 409 }));
  assert.deepEqual(generic.toasts, ["当前订单状态不允许完成"]);
  assert.deepEqual(generic.loads, []);

  const missing = await confirmWith(Object.assign(new Error(""), { status: 409 }));
  assert.deepEqual(missing.toasts, ["订单状态已更新，请刷新后重新确认。"]);
  assert.deepEqual(missing.loads, [[true, "conflict"]]);

  const server = await confirmWith(Object.assign(new Error("服务器异常，请稍后重试"), { status: 500 }));
  assert.deepEqual(server.toasts, ["服务器异常，请稍后重试"]);
  assert.deepEqual(server.loads, []);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
