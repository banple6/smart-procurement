"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
const start = source.indexOf("async function openProductOrderScopeDialog(productId)");
const end = source.indexOf("\n  function bindProductOptionButtons", start);
assert.notEqual(start, -1);
assert.notEqual(end, -1);
const scopeSource = source.slice(start, end);

function element() {
  return {
    hidden: false,
    disabled: false,
    textContent: "保存范围",
    listeners: {},
    addEventListener(name, listener) { this.listeners[name] = listener; },
  };
}

async function testScopeDialogBlocksEmptySelectionAndSavesSelectedUnits() {
  const calls = [];
  const toasts = [];
  const form = element();
  const unitsPanel = element();
  const error = element();
  const submit = element();
  const radios = [element(), element()];
  const checkbox = { ...element(), checked: false, dataset: { scopeUnitId: "unit-001" } };
  form.scopeMode = { value: "selected" };
  form.querySelectorAll = (selector) => {
    if (selector === 'input[name="scopeMode"]') return radios;
    if (selector === "[data-scope-unit-id]:checked") return checkbox.checked ? [checkbox] : [];
    if (selector === "[data-scope-unit-id]") return [checkbox];
    return [];
  };
  form.querySelector = (selector) => {
    if (selector === 'button[type="submit"]') return submit;
    return null;
  };
  const nodes = { productOrderScopeForm: form, productOrderScopeUnits: unitsPanel, productOrderScopeError: error };
  let closed = 0;
  const context = {
    api: async (url, options) => {
      calls.push([url, options]);
      if (url.endsWith("/order-scope") && !options) return { product_id: "product-1", mode: "selected", unit_ids: [], version: 7 };
      if (url === "/api/v1/admin/units") return [{ id: "unit-001", unit_code: "001", unit_name: "第一单位", active: true }];
      return { ok: true };
    },
    openOrgDialog: () => ({ close() { closed += 1; } }),
    table: () => "<table></table>",
    html: (value) => String(value),
    $: (id) => nodes[id],
    toast: (message) => toasts.push(message),
  };
  vm.runInNewContext(`${scopeSource}\nglobalThis.openProductOrderScopeDialog = openProductOrderScopeDialog;`, context);
  await context.openProductOrderScopeDialog("product-1");

  await form.listeners.submit({ preventDefault() {} });
  assert.equal(error.hidden, false);
  assert.equal(error.textContent, "指定单位时至少选择一个单位");
  assert.equal(calls.filter(([url, options]) => url.endsWith("/order-scope") && options).length, 0);

  checkbox.checked = true;
  await form.listeners.submit({ preventDefault() {} });
  const save = calls.find(([url, options]) => url.endsWith("/order-scope") && options);
  assert.equal(save[0], "/api/v1/admin/products/product-1/order-scope");
  assert.equal(save[1].method, "PUT");
  assert.deepEqual(JSON.parse(save[1].body), { mode: "selected", unit_ids: ["unit-001"], expected_version: 7 });
  assert.equal(closed, 1);
  assert.deepEqual(toasts, ["下单范围已保存"]);
  assert.equal(unitsPanel.hidden, false);
}

async function main() {
  await testScopeDialogBlocksEmptySelectionAndSavesSelectedUnits();
  assert.match(source, /api\("\/api\/v1\/admin\/units"\)/);
  assert.match(source, /data-order-scope=/);
  assert.match(source, /openProductOrderScopeDialog\(button\.dataset\.orderScope\)/);
  assert.doesNotMatch(scopeSource, /location\.reload\(|loadCurrent\(true\)|pageShell\(/);
}

main().then(() => console.log("product order scope UI tests passed"));
