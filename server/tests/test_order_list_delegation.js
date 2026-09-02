"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
const start = source.indexOf("function bindOrderListEvents");
const end = source.indexOf("function patchOrdersRealtime", start);
assert.ok(start >= 0 && end > start, "order list event delegation must exist");

const state = {
  orderItems: [{ id: "o1", version: 3, status: "pending" }, { id: "o2", version: 4, status: "pending" }],
  selectedOrderIds: new Set(),
  selectedOrderVersions: new Map(),
};
const calls = { render: 0, accept: [], ship: [], lifecycle: [] };
const listeners = {};
const rows = [{ checked: false }, { checked: false }];
const target = {
  dataset: {},
  addEventListener(type, listener) {
    assert.equal(listeners[type], undefined, `only one ${type} listener is registered`);
    listeners[type] = listener;
  },
  querySelectorAll(selector) {
    assert.equal(selector, "[data-order-select]");
    return rows;
  },
};

const sandbox = {
  state,
  window: {
    AdminOrderSelectionPolicy: {
      nextSelection(items, selected, checked) {
        const next = new Set(selected);
        items.forEach((item) => checked ? next.add(item.id) : next.delete(item.id));
        return next;
      },
    },
  },
  renderOrderSelection: () => { calls.render += 1; },
  updateOrderStatus: (button) => { calls.accept.push(button.dataset.order); },
  chooseShipPhotos: (button) => { calls.ship.push(button.dataset.ship); },
  lifecycleOrder: (button, action) => { calls.lifecycle.push([button.dataset.order, action]); },
};
vm.runInNewContext(`${source.slice(start, end)}; globalThis.bindOrderListEvents = bindOrderListEvents;`, sandbox);

sandbox.bindOrderListEvents(target);
sandbox.bindOrderListEvents(target);
assert.equal(target.dataset.orderEventsBound, "1");
assert.deepEqual(Object.keys(listeners).sort(), ["change", "click"]);

const rowCheckbox = { checked: true, dataset: { orderSelect: "o1" } };
listeners.change({ target: { closest: (selector) => selector === "[data-order-select]" ? rowCheckbox : null } });
assert.deepEqual([...state.selectedOrderIds], ["o1"]);
assert.deepEqual([...state.selectedOrderVersions], [["o1", 3]]);

const selectAll = { checked: true };
listeners.change({ target: { closest: (selector) => selector === ".select-all-orders" ? selectAll : null } });
assert.deepEqual([...state.selectedOrderIds].sort(), ["o1", "o2"]);
assert.equal(rows.every((row) => row.checked), true);

const insertedAcceptButton = { dataset: { order: "o3", status: "accepted" } };
listeners.click({ target: { closest: (selector) => selector === "[data-order][data-status]" ? insertedAcceptButton : null } });
assert.deepEqual(calls.accept, ["o3"]);
assert.equal(calls.render, 2);
