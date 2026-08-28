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

  function reconcileSelection(pageOrders, selectedIds, selectedVersions) {
    const ids = selectedIds instanceof Set ? selectedIds : new Set(selectedIds || []);
    const versions = selectedVersions instanceof Map ? selectedVersions : new Map(selectedVersions || []);
    const currentById = new Map((pageOrders || []).map((order) => [order.id, order]));
    const retainedIds = new Set();
    const retainedVersions = new Map();
    let removed = 0;
    ids.forEach((id) => {
      const current = currentById.get(id);
      const selectedVersion = versions.get(id);
      if (!current || (selectedVersion !== undefined && Number(current.version || 1) !== Number(selectedVersion))) {
        removed += 1;
        return;
      }
      retainedIds.add(id);
      retainedVersions.set(id, Number(current.version || 1));
    });
    return { selectedIds: retainedIds, selectedVersions: retainedVersions, removed };
  }

  return { selectedOrders, canBulkAccept, canBulkDelete, nextSelection, reconcileSelection };
});
