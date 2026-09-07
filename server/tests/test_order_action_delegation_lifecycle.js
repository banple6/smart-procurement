"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");

function extractFunction(name) {
  const functionStart = source.indexOf(`function ${name}(`);
  const start = source.slice(Math.max(0, functionStart - 6), functionStart) === "async "
    ? functionStart - 6
    : functionStart;
  assert.notEqual(start, -1, `missing ${name}`);
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`unterminated ${name}`);
}

async function detailFailureDoesNotLeaveClickBlockingDialog() {
  const state = { openOrderDetailId: "" };
  const root = { isConnected: true };
  let closeCalls = 0;
  const sandbox = {
    state,
    openOrgDialog: () => ({ root, close: () => { closeCalls += 1; root.isConnected = false; } }),
    api: async () => { throw new Error("network failed"); },
    $: () => null,
    renderOrderDetail: () => assert.fail("a failed request must not render a detail"),
  };
  vm.runInNewContext(`${extractFunction("openOrderDetailDialog")}; globalThis.openOrderDetailDialog = openOrderDetailDialog;`, sandbox);

  await assert.rejects(() => sandbox.openOrderDetailDialog("o1"), /network failed/);
  assert.equal(closeCalls, 1, "a failed detail request must remove its blocking dialog backdrop");
  assert.equal(root.isConnected, false);
  assert.equal(state.openOrderDetailId, "");
}

function keyedRefreshKeepsTheDelegatedListAncestor() {
  const patch = extractFunction("patchOrdersRealtime");
  assert.doesNotMatch(patch, /list\.(?:replaceWith|remove)|list\.innerHTML\s*=/,
    "an incremental refresh must not replace the delegated order-list ancestor");
  assert.match(patch, /row\.replaceWith\(replacement\)/,
    "only the changed keyed row may be replaced");
  assert.match(patch, /list\.append\(group\)/,
    "a new month group stays beneath the same delegated list ancestor");

  const bind = extractFunction("bindOrderListEvents");
  assert.match(bind, /target\.dataset\.orderEventsBound === "1"/);
  assert.equal((bind.match(/target\.addEventListener\("click"/g) || []).length, 1,
    "the orders list installs one delegated click listener");
}

function delegatedActionsStillDispatchAfterKeyedRowAndMonthChanges() {
  const state = { orderItems: [], selectedOrderIds: new Set(), selectedOrderVersions: new Map() };
  const listeners = {};
  const target = {
    dataset: {},
    addEventListener(type, handler) { listeners[type] = handler; },
    querySelectorAll() { return []; },
  };
  const calls = [];
  const sandbox = {
    state,
    window: { AdminOrderSelectionPolicy: { nextSelection: () => new Set() } },
    renderOrderSelection: () => {},
    openOrderDetailDialog: (id) => { calls.push(["detail", id]); return Promise.resolve(); },
    updateOrderStatus: (button) => calls.push(["status", button.dataset.order, button.dataset.status]),
    chooseShipPhotos: (button) => calls.push(["ship", button.dataset.ship]),
    lifecycleOrder: (button, action) => calls.push(["lifecycle", button.dataset.order, action]),
  };
  vm.runInNewContext(`${extractFunction("bindOrderListEvents")}; globalThis.bindOrderListEvents = bindOrderListEvents;`, sandbox);

  sandbox.bindOrderListEvents(target);
  sandbox.bindOrderListEvents(target);
  const click = (selector, dataset) => listeners.click({ target: { closest: (value) => value === selector ? { dataset } : null } });

  // The same stable list ancestor receives events from a keyed replacement and a newly appended month group.
  click("[data-order-detail]", { orderDetail: "o1" });
  click("[data-order][data-status]", { order: "o1", status: "accepted" });
  click("[data-ship]", { ship: "o2" });
  click("[data-lifecycle]", { order: "o3", lifecycle: "cancel" });

  assert.deepEqual(calls, [
    ["detail", "o1"],
    ["status", "o1", "accepted"],
    ["ship", "o2"],
    ["lifecycle", "o3", "cancel"],
  ]);
  assert.equal(Object.keys(listeners).filter((type) => type === "click").length, 1);
}

detailFailureDoesNotLeaveClickBlockingDialog()
  .then(() => {
    keyedRefreshKeepsTheDelegatedListAncestor();
    delegatedActionsStillDispatchAfterKeyedRowAndMonthChanges();
    console.log("order action delegation lifecycle tests passed");
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
