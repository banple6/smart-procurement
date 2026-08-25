(function (root, factory) {
  const policy = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = policy;
  else root.AdminOrderSelectionPolicy = policy;
})(typeof globalThis === "object" ? globalThis : this, function () {
  function selectedOrders(pageOrders, selectedIds) {
    const ids = selectedIds instanceof Set ? selectedIds : new Set(selectedIds || []);
    return (pageOrders || []).filter((order) => ids.has(order.id));
  }

  function canBulkAccept(orders) {
    return orders.length > 0 && orders.every((order) => order.status === "pending");
  }

  function canBulkDelete(orders) {
    return orders.length > 0 && orders.every((order) => order.can_delete === true);
  }

  function nextSelection(pageOrders, selectedIds, checked) {
    const next = new Set(selectedIds || []);
    if (checked) (pageOrders || []).forEach((order) => next.add(order.id));
    else next.clear();
    return next;
  }

  return { selectedOrders, canBulkAccept, canBulkDelete, nextSelection };
});
