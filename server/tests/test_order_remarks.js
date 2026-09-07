"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
const css = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.css"), "utf8");

function extractFunction(name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `missing ${name}`);
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`unterminated ${name}`);
}

const escapeHtml = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#39;");
const sandbox = { html: escapeHtml };
vm.runInNewContext(
  `${extractFunction("orderRemark")}\n${extractFunction("orderRemarkMarkup")}\n${extractFunction("orderRowRenderKey")}\nglobalThis.orderRemark = orderRemark; globalThis.orderRemarkMarkup = orderRemarkMarkup; globalThis.orderRowRenderKey = orderRowRenderKey;`,
  sandbox,
);

assert.equal(sandbox.orderRemark({ note: "  青菜要嫩一点  " }), "青菜要嫩一点");
assert.equal(sandbox.orderRemark({ note: "   " }), "");
assert.equal(sandbox.orderRemarkMarkup({ note: "" }), "");
assert.match(sandbox.orderRemarkMarkup({ note: "不要香菜\n上午送" }), /不要香菜\n上午送/);
const unsafe = sandbox.orderRemarkMarkup({ note: "<img src=x onerror=alert(1)>" });
assert.match(unsafe, /&lt;img src=x onerror=alert\(1\)&gt;/);
assert.doesNotMatch(unsafe, /<img/);
assert.notEqual(
  sandbox.orderRowRenderKey({ version: 1, status: "pending", note: "" }),
  sandbox.orderRowRenderKey({ version: 1, status: "pending", note: "不要辣椒" }),
  "a changed remark must replace only its keyed row",
);

assert.match(source, /data-order-render-key=/);
assert.match(source, /data-order-detail=/);
assert.match(source, /row\.dataset\.orderRenderKey !== orderRowRenderKey\(order\)/);
assert.doesNotMatch(extractFunction("patchOrdersRealtime"), /return loadOrders\(\)/);
assert.match(source, /class="order-detail-remark">\$\{html\(orderRemark\(order\) \|\| "无"\)\}/);
assert.match(css, /\.order-remark[\s\S]*white-space: pre-wrap/);

for (const action of ["updateOrderStatus", "openFastCompleteReview", "lifecycleOrder", "deleteOrder", "shipOrder", "bulkAcceptOrders", "bulkDeleteOrders"]) {
  const body = extractFunction(action);
  assert.match(body, /reconcileOrderMutation\(\)/, `${action} must reconcile without a whole-page reload`);
  assert.doesNotMatch(body, /loadCurrent\(/, `${action} must not call the whole-page loader`);
  assert.doesNotMatch(body, /location\.reload\(/, `${action} must not reload the browser`);
}

assert.match(extractFunction("reconcileOrderMutation"), /currentRoute\(\) === "\/admin\/orders"[\s\S]*refreshOrdersIncrementally\(\)/);
assert.match(extractFunction("refreshOrderDetailScoped"), /renderOrderDetail\(order\)/);
assert.match(extractFunction("openOrderDetailDialog"), /openOrgDialog\("订单详情"/);

console.log("order remark visibility and scoped action reconciliation tests passed");
