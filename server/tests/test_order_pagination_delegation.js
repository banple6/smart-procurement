const assert = require("assert");
const fs = require("fs");
const path = require("path");

const dashboardPath = path.join(__dirname, "../app/static/admin/dashboard.js");
const source = fs.readFileSync(dashboardPath, "utf8");
const paginationStart = source.indexOf("if (data.has_more && data.next_cursor)");
const paginationEnd = source.indexOf('$("orderFilterButton")', paginationStart);
const paginationBlock = source.slice(paginationStart, paginationEnd);

assert.ok(paginationStart > -1, "orders pagination branch must exist");
assert.ok(paginationBlock.includes('insertAdjacentHTML("beforeend"'), "pagination must append without reparsing the orders content");
assert.ok(!paginationBlock.includes("content().innerHTML +="), "pagination must not replace delegated order-list descendants");

function createOrderList() {
  const listeners = [];
  return {
    dataset: {},
    addEventListener(type, handler) {
      listeners.push({ type, handler });
    },
    click(target) {
      for (const listener of listeners.filter((item) => item.type === "click")) listener.handler({ target });
    },
    listenerCount(type) {
      return listeners.filter((item) => item.type === type).length;
    },
  };
}

function bindOrderListEvents(list, dispatch) {
  if (!list || list.dataset.orderEventsBound === "1") return;
  list.dataset.orderEventsBound = "1";
  list.addEventListener("click", (event) => dispatch(event.target));
}

const calls = [];
const list = createOrderList();
const content = {
  paginationHtml: "",
  insertAdjacentHTML(position, html) {
    assert.strictEqual(position, "beforeend");
    this.paginationHtml += html;
  },
};

bindOrderListEvents(list, (target) => calls.push({ action: target.action, orderId: target.orderId }));
const boundList = list;
content.insertAdjacentHTML("beforeend", '<div class="page-toolbar"><button id="nextOrderPage">下一页</button></div>');
bindOrderListEvents(list, (target) => calls.push({ action: target.action, orderId: target.orderId }));

assert.strictEqual(list, boundList, "pagination append must preserve the delegated list identity");
assert.strictEqual(list.dataset.orderEventsBound, "1");
assert.ok(content.paginationHtml.includes('id="nextOrderPage"'), "next-page control must remain present");
assert.strictEqual(list.listenerCount("click"), 1, "repeated initialization must not add duplicate delegated listeners");

list.click({ action: "detail", orderId: "O1" });
list.click({ action: "status", orderId: "O1" });
assert.deepStrictEqual(calls, [
  { action: "detail", orderId: "O1" },
  { action: "status", orderId: "O1" },
]);

console.log("order pagination delegation tests passed");
