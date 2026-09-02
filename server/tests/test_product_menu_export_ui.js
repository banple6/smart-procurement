"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
const start = source.indexOf("async function downloadProductMenu(button)");
const end = source.indexOf("\n  function priceImportStatus", start);
assert.notEqual(start, -1);
assert.notEqual(end, -1);
const downloadSource = source.slice(start, end);

function makeRuntime(responseFactory) {
  const toasts = [];
  const clicks = [];
  const body = { appendChild(node) { node.attached = true; } };
  const context = {
    URL: {
      createObjectURL(blob) { return `blob:${blob.id}`; },
      revokeObjectURL(value) { clicks.push(["revoke", value]); },
    },
    decodeURIComponent,
    document: {
      body,
      createElement() {
        return {
          click() { clicks.push(["click", this.download, this.href]); },
          remove() { this.attached = false; },
        };
      },
    },
    window: {
      AdminRefreshPolicy: { apiErrorMessage: (_status, detail) => detail || "导出失败" },
      location: { replace(value) { clicks.push(["redirect", value]); } },
    },
    toast(message) { toasts.push(message); },
    fetch: responseFactory,
  };
  vm.runInNewContext(`${downloadSource}\nglobalThis.downloadProductMenu = downloadProductMenu;`, context);
  return { context, toasts, clicks };
}

async function testSuccessAndDoubleClickProtection() {
  let resolveResponse;
  let requests = 0;
  const runtime = makeRuntime(() => {
    requests += 1;
    return new Promise((resolve) => { resolveResponse = resolve; });
  });
  const button = { dataset: {}, disabled: false, textContent: "导出商品菜单" };
  const first = runtime.context.downloadProductMenu(button);
  const second = runtime.context.downloadProductMenu(button);
  assert.equal(requests, 1);
  assert.equal(button.disabled, true);
  assert.equal(button.textContent, "正在导出...");
  resolveResponse({
    status: 200,
    ok: true,
    headers: { get: () => "attachment; filename*=UTF-8''%E4%B8%89%E5%85%AC%E9%B2%9C%E9%85%8D%E5%95%86%E5%93%81%E8%8F%9C%E5%8D%95_20260902.xlsx" },
    blob: async () => ({ id: "menu" }),
  });
  await Promise.all([first, second]);
  assert.deepEqual(runtime.clicks, [
    ["click", "三公鲜配商品菜单_20260902.xlsx", "blob:menu"],
    ["revoke", "blob:menu"],
  ]);
  assert.equal(button.disabled, false);
  assert.equal(button.textContent, "导出商品菜单");
  assert.deepEqual(runtime.toasts, []);
}

async function testFailureRestoresButton() {
  const runtime = makeRuntime(async () => ({
    status: 500,
    ok: false,
    json: async () => ({ detail: "导出服务暂不可用" }),
  }));
  const button = { dataset: {}, disabled: false, textContent: "导出商品菜单" };
  await runtime.context.downloadProductMenu(button);
  assert.equal(button.disabled, false);
  assert.equal(button.textContent, "导出商品菜单");
  assert.deepEqual(runtime.toasts, ["导出服务暂不可用"]);
}

async function main() {
  await testSuccessAndDoubleClickProtection();
  await testFailureRestoresButton();
  assert.match(source, /id="exportProductMenu"/);
  assert.match(source, /downloadProductMenu\(\$\("exportProductMenu"\)\)/);
  assert.match(source, /fetch\("\/api\/v1\/admin\/products\/export\.xlsx"/);
  assert.doesNotMatch(downloadSource, /content\(\)\.innerHTML|pageShell\(|loadProducts\(/);
}

main().then(() => console.log("product menu export UI tests passed"));
