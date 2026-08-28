const test = require("node:test");
const assert = require("node:assert/strict");
const policy = require("../app/static/admin/order-selection-policy.js");

const pending = { id: "a", status: "pending", can_delete: true };
const completed = { id: "b", status: "completed", can_delete: true };
const preparing = { id: "c", status: "preparing", can_delete: false };

test("selects only the current page and supports select all / clear", () => {
  const page = [pending, completed];
  const selected = policy.nextSelection(page, new Set(), true);
  assert.deepEqual([...selected], ["a", "b"]);
  assert.deepEqual(policy.selectedOrders(page, selected).map((order) => order.id), ["a", "b"]);
  assert.deepEqual([...policy.nextSelection(page, selected, false)], []);
});

test("mixed statuses cannot be bulk accepted", () => {
  assert.equal(policy.canBulkAccept([pending]), true);
  assert.equal(policy.canBulkAccept([pending, completed]), false);
  assert.equal(policy.canBulkAccept([]), false);
});

test("mixed invalid deletion cannot be bulk deleted", () => {
  assert.equal(policy.canBulkDelete([completed]), true);
  assert.equal(policy.canBulkDelete([completed, preparing]), false);
});

test("selection policy does not mutate the original selection set", () => {
  const original = new Set(["old"]);
  const next = policy.nextSelection([pending], original, true);
  assert.deepEqual([...original], ["old"]);
  assert.deepEqual([...next], ["old", "a"]);
});

test("refresh keeps unchanged selections and removes orders whose server version changed", () => {
  const current = [{ ...pending, version: 3 }, { ...preparing, version: 2 }];
  const result = policy.reconcileSelection(current, new Set(["a", "c"]), new Map([["a", 3], ["c", 1]]));
  assert.deepEqual([...result.selectedIds], ["a"]);
  assert.deepEqual([...result.selectedVersions], [["a", 3]]);
  assert.equal(result.removed, 1);
});
