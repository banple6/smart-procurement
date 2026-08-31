(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.AdminRefreshPolicy = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function shouldDefer(context) {
    const activeTag = String(context.activeTag || "").toUpperCase();
    return Boolean(
      context.formDirty
      || Number(context.selectionCount || 0) > 0
      || context.dialogOpen
      || Number(context.mutationInFlight || 0) > 0
      || ["INPUT", "TEXTAREA", "SELECT"].includes(activeTag)
      || Number(context.recentScrollMs || Infinity) < Number(context.scrollIdleThresholdMs || 5000)
    );
  }

  function orderFingerprint(data) {
    const items = Array.isArray(data?.items) ? data.items : [];
    return JSON.stringify({
      total: Number(data?.total || items.length),
      items: items.map((item) => [item.id, item.status, Number(item.version || 0), item.updated_at || ""]),
    });
  }

  function apiErrorMessage(status, detail) {
    const message = String(detail || "").trim();
    if (message) return message;
    if (status === 400) return "请求参数错误，请检查后重试";
    if (status === 401) return "登录已失效，请重新登录";
    if (status === 403) return "没有权限执行该操作";
    if (status === 404) return "数据不存在或已被处理";
    if (status === 409) return "数据已被其他管理员修改，请刷新后重试";
    if (Number(status) >= 500) return "服务器异常，请稍后重试";
    return "请求失败，请稍后重试";
  }

  function hasActiveSelections(counts) {
    return Object.values(counts || {}).some((count) => Number(count || 0) > 0);
  }

  function batchRecommendation(group) {
    const pending = Number(group?.pending_order_count || 0);
    const open = Number(group?.open_batch_count || 0);
    if (open >= 2) return "reconcile";
    if (open === 1 && pending > 0) return "append";
    if (open === 0 && pending > 0) return "create";
    return "none";
  }

  return { shouldDefer, orderFingerprint, apiErrorMessage, hasActiveSelections, batchRecommendation };
});
