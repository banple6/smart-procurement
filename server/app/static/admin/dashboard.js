(function () {
  const statusText = {
    pending: "待接单",
    accepted: "已接单",
    preparing: "备货中",
    shipped: "已发货",
    completed: "已完成",
    cancelled: "已取消",
  };

  const supplyText = {
    normal: "正常供应",
    tight: "库存紧张",
    paused: "暂停供应",
    off_shelf: "已下架",
  };

  const nav = [
    ["", [["工作台", "/admin/dashboard", "▦"]]],
    ["采购管理", [["订单管理", "/admin/orders", "□"], ["当前备货", "/admin/preparation-summary", "▤"], ["单位配送", "/admin/delivery-sheets", "⇄"]]],
    ["食材管理", [["食材列表", "/admin/products", "◇"], ["库存记录", "/admin/inventory", "≡"]]],
    ["组织管理", [["子单位管理", "/admin/units", "⌂"], ["账号管理", "/admin/accounts", "☉"]]],
    ["统计报表", [["采购台账", "/admin/ledger", "▥"], ["导出 Excel", "/api/v1/admin/ledger/export.xlsx", "⇩"]]],
    ["系统", [["网页登录记录", "/admin/web-sessions", "◉"], ["系统状态", "/admin/system", "●"], ["退出登录", "#logout", "↩"]]],
  ];

  const quickActions = [
    ["当前备货单", "/admin/preparation-summary", "按食材汇总待备货需求"],
    ["单位配送单", "/admin/delivery-sheets", "按单位查看配送清单"],
    ["待接单订单", "/admin/orders?status=pending", "处理新提交采购单"],
    ["食材价格维护", "/admin/products?mode=price", "快速改价和库存"],
    ["系统状态", "/admin/system", "查看服务和备份"],
    ["导出今日台账", "/api/v1/admin/ledger/export.xlsx", "保存 Excel 台账"],
  ];

  const state = {
    loading: false,
    lastData: null,
    timer: null,
    rangeDays: 7,
    unitSort: "amount",
  };

  const staleSuffix = "，数据可能不是最新";

  function $(id) {
    return document.getElementById(id);
  }

  function content() {
    return document.querySelector(".content");
  }

  function html(value) {
    return String(display(value, ""))
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function display(value, fallback = "未填写") {
    const text = String(value ?? "").trim();
    if (!text || text === "null" || text === "undefined" || text === "None" || text === "NaN") return fallback;
    return text;
  }

  function money(cents) {
    return "¥" + (Number(cents || 0) / 100).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function num(value) {
    return Number(value || 0).toLocaleString("zh-CN");
  }

  function qty(value) {
    return String(value ?? "0").replace(/\\.0+$/, "");
  }

  function dateTime(value) {
    if (!display(value, "")) return "时间未记录";
    return String(value).replace("T", " ").slice(0, 19);
  }

  function shortTime(value) {
    if (!display(value, "")) return "时间未记录";
    return String(value).replace("T", " ").slice(5, 16);
  }

  function cookie(name) {
    const found = document.cookie.split("; ").find((item) => item.startsWith(name + "="));
    return found ? found.split("=").slice(1).join("=") : "";
  }

  function toast(message) {
    const box = $("toast");
    box.textContent = message;
    box.hidden = false;
    window.setTimeout(() => {
      box.hidden = true;
    }, 2200);
  }

  function empty(text) {
    return `<div class="empty-state">${html(text)}</div>`;
  }

  function setTitle(title, subtitle) {
    $("pageTitle").textContent = title;
    $("businessDate").textContent = subtitle || "";
  }

  function currentRoute() {
    const path = window.location.pathname;
    if (path === "/admin" || path === "/admin/") return "/admin/dashboard";
    return path;
  }

  async function api(path, options = {}) {
    const method = String(options.method || "GET").toUpperCase();
    const csrfHeaders = ["POST", "PUT", "PATCH", "DELETE"].includes(method) ? { "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")) } : {};
    const response = await fetch(path, {
      credentials: "same-origin",
      ...options,
      headers: { "Accept": "application/json", ...csrfHeaders, ...(options.headers || {}) },
    });
    if (response.status === 401) {
      window.location.replace("/login?expired=1");
      throw new Error("登录已过期，请重新登录");
    }
    if (!response.ok) {
      let detail = "";
      try {
        detail = (await response.json()).detail || "";
      } catch (_) {
        detail = "";
      }
      throw new Error(detail || "数据加载失败");
    }
    if (response.status === 204) return {};
    return response.json();
  }

  async function mutate(path, body, method = "PATCH") {
    const result = await api(path, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body || {}),
    });
    toast("操作已完成");
    await loadCurrent(true);
    return result;
  }

  function renderNav() {
    const current = window.location.pathname + window.location.search;
    $("navMenu").innerHTML = nav.map(([title, items]) => {
      const links = items.map(([label, href, icon]) => {
        const active = href !== "#logout" && current.startsWith(href.split("?")[0]);
        return `<a class="nav-item ${active ? "active" : ""}" href="${href}" data-href="${href}"><span class="nav-icon">${icon}</span><span>${label}</span></a>`;
      }).join("");
      return `<div class="nav-section">${title ? `<div class="nav-section-title">${title}</div>` : ""}${links}</div>`;
    }).join("");
    document.querySelectorAll('[data-href="#logout"]').forEach((item) => item.addEventListener("click", (event) => {
      event.preventDefault();
      logout();
    }));
  }

  function comparison(value, fallback) {
    if (value === null || value === undefined) return fallback || "昨日无可比数据";
    const sign = value > 0 ? "+" : "";
    return `较昨日 ${sign}${value}%`;
  }

  function waitText(seconds) {
    const value = Number(seconds || 0);
    if (value <= 0) return "暂无等待";
    const minutes = Math.floor(value / 60);
    if (minutes < 60) return `最早一笔已等待 ${minutes} 分钟`;
    return `最早一笔已等待 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
  }

  function target(url) {
    window.location.href = url;
  }

  function dashboardTemplate() {
    return `
      <div id="globalError" class="error-banner" hidden>工作台数据加载失败 <button id="retryButton" type="button">重新加载</button></div>
      <section id="metricGrid" class="metric-grid" aria-label="关键指标"></section>
      <section class="dashboard-grid first">
        <article class="panel todo-panel"><div class="panel-header"><div><h2>待办中心</h2><p>优先处理异常、超时和发货任务</p></div></div><div id="taskList" class="task-list"></div></article>
        <article class="panel trend-panel"><div class="panel-header"><div><h2>近 7 日采购趋势</h2><p id="trendSummary">--</p></div><select id="rangeSelect" aria-label="趋势范围"><option value="7">近 7 日</option><option value="14">近 14 日</option><option value="30">近 30 日</option></select></div><div id="trendChart" class="trend-chart"></div></article>
      </section>
      <section class="dashboard-grid">
        <article class="panel table-panel"><div class="panel-header"><div><h2>最近订单</h2><p>最多显示最近 10 笔</p></div><a href="/admin/orders">查看全部订单</a></div><div class="table-wrap"><table><thead><tr><th>订单编号</th><th>子单位</th><th>下单时间</th><th>商品种类</th><th>订单金额</th><th>当前状态</th><th>异常标记</th><th>操作</th></tr></thead><tbody id="recentOrders"></tbody></table></div></article>
        <article class="panel table-panel"><div class="panel-header"><div><h2>库存预警</h2><p>优先显示库存不足和暂停供应</p></div><a href="/admin/products?status=tight">查看食材列表</a></div><div id="inventoryAlerts" class="inventory-list"></div></article>
      </section>
      <section class="dashboard-grid">
        <article class="panel"><div class="panel-header"><div><h2>今日需求排行</h2><p>按实际供应数量统计</p></div><a href="/admin/preparation-summary">当前备货汇总</a></div><div id="demandRank" class="rank-list"></div></article>
        <article class="panel"><div class="panel-header"><div><h2>今日单位采购情况</h2><p>按金额或订单量查看</p></div><select id="unitSort" aria-label="单位排行排序"><option value="amount">按金额</option><option value="orders">按订单量</option></select></div><div id="unitRank" class="rank-list"></div></article>
      </section>
      <section class="dashboard-grid bottom">
        <article class="panel"><div class="panel-header"><div><h2>快捷操作</h2><p>常用入口，不包含高风险操作</p></div></div><div id="quickActions" class="quick-grid"></div></article>
        <article class="panel status-panel"><div class="panel-header"><div><h2>系统状态</h2><p>简要状态，不展示敏感信息</p></div><a href="/admin/system">查看详情</a></div><dl id="systemStatus" class="status-list"></dl></article>
      </section>
    `;
  }

  function renderMetrics(data) {
    const metrics = data.metrics || {};
    const comparisons = data.comparisons || {};
    const cards = [
      ["今日有效订单", num(metrics.today_valid_orders), comparison(comparisons.orders_vs_yesterday_percent), "查看今日订单 >", "/admin/orders?date=" + data.business_date, ""],
      ["今日采购金额", money(metrics.today_total_cents), comparison(comparisons.amount_vs_yesterday_percent), "查看采购台账 >", "/admin/ledger?date=" + data.business_date, ""],
      ["待接单", num(metrics.pending), "需要管理员接单", "查看待接单订单 >", "/admin/orders?status=pending", metrics.pending ? "danger" : ""],
      ["备货中", num(metrics.preparing), "已接单或正在备货", "查看备货订单 >", "/admin/orders?status=accepted,preparing", ""],
      ["待发货", num(metrics.waiting_shipment), "备货完成后确认发货", "查看待发货 >", "/admin/orders?status=preparing", metrics.waiting_shipment ? "warning" : ""],
      ["待确认收货", num(metrics.waiting_receipt), "等待子单位确认", "查看已发货 >", "/admin/orders?status=shipped", ""],
      ["待处理异常", num(metrics.open_receipt_issues), "收货异常需跟进", "查看异常 >", "/admin/orders", metrics.open_receipt_issues ? "danger" : ""],
      ["库存预警", num(metrics.tight_inventory), "库存不足或供应紧张", "查看库存 >", "/admin/products?status=tight", metrics.tight_inventory ? "warning" : ""],
    ];
    $("metricGrid").innerHTML = cards.map(([title, value, note, link, href, tone]) => `
      <a class="metric-card" href="${href}"><div class="metric-title">${title}</div><div class="metric-value ${tone}">${value}</div><div class="metric-note">${note}</div><div class="metric-link">${link}</div></a>
    `).join("");
  }

  function renderTasks(data) {
    const tasks = data.tasks || [];
    if (!tasks.length || tasks.every((task) => Number(task.count || 0) === 0)) {
      $("taskList").innerHTML = empty("暂无紧急待办，仍可通过下方入口查看业务");
      return;
    }
    $("taskList").innerHTML = tasks.map((task) => {
      const detail = task.oldest_wait_seconds ? waitText(task.oldest_wait_seconds) : (task.risk || "暂无风险");
      const unitLabel = task.unit_label || "笔";
      return `<div class="task-item"><div><div class="task-name">${html(task.name)}</div><div class="task-desc">${html(detail)}</div></div><div><div class="task-count ${task.priority === "urgent" ? "urgent" : task.priority === "warning" ? "warning" : ""}">${num(task.count)} ${unitLabel}</div><button class="table-action primary" type="button" data-target="${task.target_url}">${html(task.action_label)}</button></div></div>`;
    }).join("");
  }

  function renderTrend(data) {
    const rows = data.trend || [];
    if (!rows.length || rows.every((row) => row.order_count === 0 && row.amount_cents === 0)) {
      $("trendSummary").textContent = "该时间范围暂无采购数据";
      $("trendChart").innerHTML = empty("暂无趋势数据");
      return;
    }
    const maxOrders = Math.max(...rows.map((row) => row.order_count), 1);
    const maxAmount = Math.max(...rows.map((row) => row.amount_cents), 1);
    const width = 720;
    const height = 270;
    const pad = 34;
    const step = (width - pad * 2) / rows.length;
    const points = rows.map((row, index) => {
      const x = pad + step * index + step / 2;
      const y = height - pad - (row.amount_cents / maxAmount) * (height - pad * 2);
      return `${x},${y}`;
    }).join(" ");
    const bars = rows.map((row, index) => {
      const x = pad + step * index + step * 0.18;
      const barHeight = (row.order_count / maxOrders) * (height - pad * 2);
      const y = height - pad - barHeight;
      return `<g><rect class="bar" x="${x}" y="${y}" width="${Math.max(10, step * 0.64)}" height="${barHeight}"><title>${row.date}：${row.order_count} 单，${money(row.amount_cents)}</title></rect><text class="chart-label" x="${x}" y="${height - 10}">${row.date.slice(5)}</text></g>`;
    }).join("");
    const totalOrders = rows.reduce((sum, row) => sum + row.order_count, 0);
    const totalAmount = rows.reduce((sum, row) => sum + row.amount_cents, 0);
    $("trendSummary").textContent = `共 ${num(totalOrders)} 单，${money(totalAmount)}`;
    $("trendChart").innerHTML = `<svg class="chart-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="采购趋势图">${bars}<polyline class="line" points="${points}" /></svg>`;
  }

  function statusTag(status) {
    return `<span class="status-tag status-${html(status || "unknown")}">${html(statusText[status] || "未知状态")}</span>`;
  }

  function supplyTag(status, active) {
    const text = active ? (supplyText[status] || "未知状态") : "已停用";
    return `<span class="status-tag status-${active ? html(status) : "cancelled"}">${html(text)}</span>`;
  }

  function primaryAction(order) {
    if (order.status === "pending") return ["接单", "accepted"];
    if (order.status === "accepted") return ["开始备货", "preparing"];
    if (order.status === "shipped") return ["完成订单", "completed"];
    return ["查看", ""];
  }

  function renderRecentOrders(data) {
    const rows = data.recent_orders || [];
    if (!rows.length) {
      $("recentOrders").innerHTML = `<tr><td colspan="8">${empty("暂无订单")}</td></tr>`;
      return;
    }
    $("recentOrders").innerHTML = rows.map((order) => {
      const action = primaryAction(order);
      return `<tr><td><a href="/admin/orders/${order.id}">${html(order.order_no)}</a></td><td>${html(order.unit_name_snapshot || "未填写")}</td><td>${shortTime(order.created_at)}</td><td>${num(order.item_count)} 种</td><td>${money(order.total_cents)}</td><td>${statusTag(order.status)}</td><td>${Number(order.open_issue_count || 0) > 0 ? "有异常" : "—"}</td><td><a class="table-action primary" href="/admin/orders/${order.id}">${action[0]}</a></td></tr>`;
    }).join("");
  }

  function renderInventory(data) {
    const rows = data.inventory_alerts || [];
    if (!rows.length) {
      $("inventoryAlerts").innerHTML = empty("暂无库存预警");
      return;
    }
    $("inventoryAlerts").innerHTML = rows.map((item) => `
      <div class="inventory-row"><div><span class="cell-label">食材</span><span class="cell-value">${html(item.name)}</span></div><div><span class="cell-label">总库存</span><span class="cell-value">${qty(item.stock_quantity)} ${html(item.unit)}</span></div><div><span class="cell-label">预占</span><span class="cell-value">${qty(item.reserved_quantity)}</span></div><div><span class="cell-label">可用</span><span class="cell-value">${qty(item.available_quantity)}</span></div><a class="table-action" href="/admin/products/${item.id}">调整库存</a></div>
    `).join("");
  }

  function renderRanks(data) {
    const demand = data.demand_rank || [];
    $("demandRank").innerHTML = demand.length ? demand.map((item, index) => `
      <div class="rank-row"><strong>${index + 1}</strong><div><span class="cell-label">食材</span><span class="cell-value">${html(item.name)}</span></div><div><span class="cell-label">数量</span><span class="cell-value">${Number(item.quantity || 0).toLocaleString("zh-CN")} ${html(item.unit)}</span></div><div><span class="cell-label">单位</span><span class="cell-value">${num(item.unit_count)}</span></div><div><span class="cell-label">订单</span><span class="cell-value">${num(item.order_count)}</span></div></div>
    `).join("") : empty("今日暂无需求数据");
    const units = data.unit_rank || [];
    $("unitRank").innerHTML = units.length ? units.map((item, index) => `
      <a class="rank-row" href="/admin/orders?unit_id=${item.unit_id}&date=${data.business_date}"><strong>${index + 1}</strong><div><span class="cell-label">单位</span><span class="cell-value">${html(item.unit_name)}</span></div><div><span class="cell-label">订单</span><span class="cell-value">${num(item.order_count)}</span></div><div><span class="cell-label">金额</span><span class="cell-value">${money(item.total_cents)}</span></div><div><span class="cell-label">异常</span><span class="cell-value">${num(item.open_issue_count)}</span></div></a>
    `).join("") : empty("今日暂无单位采购数据");
  }

  function renderQuickActions() {
    $("quickActions").innerHTML = quickActions.map(([title, href, desc]) => `<a class="quick-card" href="${href}"><strong>${title}</strong><span>${desc}</span></a>`).join("");
  }

  function renderSystem(data) {
    const item = data.system_status || {};
    $("systemStatus").innerHTML = `<dt>服务状态</dt><dd>${item.service === "ok" ? "正常" : "异常"}</dd><dt>最近数据同步</dt><dd>${dateTime(item.last_data_sync)}</dd><dt>最近备份</dt><dd>${item.last_backup_at ? dateTime(item.last_backup_at) : "暂无备份记录"}</dd><dt>磁盘使用</dt><dd>${num(item.disk_usage_percent)}%</dd><dt>当前版本</dt><dd>${item.version || "Web 1.1.0"}</dd>`;
  }

  function renderDashboard(data) {
    setTitle("工作台", `${data.business_date} 业务日`);
    $("refreshTime").textContent = "更新于 " + dateTime(data.refreshed_at).slice(11);
    renderMetrics(data);
    renderTasks(data);
    renderTrend(data);
    renderRecentOrders(data);
    renderInventory(data);
    renderRanks(data);
    renderQuickActions();
    renderSystem(data);
    document.querySelectorAll("[data-target]").forEach((button) => button.addEventListener("click", () => target(button.dataset.target)));
  }

  async function loadDashboard(silent) {
    content().innerHTML = dashboardTemplate();
    $("retryButton").addEventListener("click", () => loadDashboard(false));
    $("rangeSelect").value = String(state.rangeDays);
    $("unitSort").value = state.unitSort;
    $("rangeSelect").addEventListener("change", (event) => {
      state.rangeDays = Number(event.target.value);
      loadDashboard(true);
    });
    $("unitSort").addEventListener("change", (event) => {
      state.unitSort = event.target.value;
      loadDashboard(true);
    });
    try {
      const data = await api(`/api/v1/admin/dashboard/overview?range_days=${state.rangeDays}&unit_sort=${state.unitSort}`);
      state.lastData = data;
      renderDashboard(data);
      if (!silent) toast("工作台已刷新");
    } catch (error) {
      $("globalError").hidden = false;
      const currentText = $("refreshTime").textContent || "加载失败";
      $("refreshTime").textContent = currentText.includes(staleSuffix) ? currentText : currentText + staleSuffix;
    }
  }

  function pageShell(title, subtitle, body = "") {
    setTitle(title, subtitle || "真实服务端数据");
    $("refreshTime").textContent = "";
    content().innerHTML = `<div id="globalError" class="error-banner" hidden>数据加载失败 <button id="retryButton" type="button">重新加载</button></div>${body}`;
    $("retryButton").addEventListener("click", () => loadCurrent(false));
  }

  async function updateOrderStatus(button) {
    const label = button.textContent;
    if (!confirm(`确认${label}这笔订单吗？`)) return;
    button.disabled = true;
    button.textContent = "提交中";
    try {
      await mutate(`/api/v1/admin/orders/${button.dataset.order}/status`, {
        status: button.dataset.status,
        expected_status: button.dataset.currentStatus,
        expected_version: Number(button.dataset.version || 0) || undefined,
      });
    } catch (error) {
      toast(error.message || "操作失败，请刷新后重试");
      button.disabled = false;
      button.textContent = label;
    }
  }

  function table(headers, rows, emptyText) {
    if (!rows.length) return empty(emptyText || "暂无数据");
    return `<div class="table-wrap"><table class="admin-table"><thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`;
  }

  async function loadOrders() {
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    if (params.get("status")) query.set("status", params.get("status"));
    if (params.get("unit_id")) query.set("unit_id", params.get("unit_id"));
    pageShell("订单管理", "接单、备货和完成订单");
    const data = await api(`/api/v1/admin/orders?include_items=true&page_size=100&${query.toString()}`);
    const items = data.items || data || [];
    content().innerHTML += table(["订单编号", "单位", "下单时间", "金额", "状态", "食材", "操作"], items.map((order) => {
      const action = primaryAction(order);
      const goods = (order.items || []).slice(0, 3).map((item) => `${html(item.product_name || item.product_name_snapshot)} x ${qty(item.quantity)}`).join("<br>");
      const button = action[1] ? `<button class="table-action primary" data-order="${order.id}" data-status="${action[1]}" data-current-status="${order.status}" data-version="${order.version || 1}">${action[0]}</button>` : `<a class="table-action" href="/admin/orders/${order.id}">查看</a>`;
      return `<tr><td>${html(order.order_no)}</td><td>${html(order.unit_name_snapshot || order.unit_name || "--")}</td><td>${dateTime(order.created_at)}</td><td>${money(order.total_cents)}</td><td>${statusTag(order.status)}</td><td>${goods || "--"}</td><td>${button}</td></tr>`;
    }), "暂无订单");
    document.querySelectorAll("[data-order]").forEach((button) => button.addEventListener("click", () => updateOrderStatus(button)));
  }

  async function loadOrderDetail(orderId) {
    pageShell("订单详情", "订单状态和食材明细");
    const order = await api(`/api/v1/admin/orders/${orderId}`);
    const action = primaryAction(order);
    content().innerHTML += `
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>${html(order.order_no)}</h2><p>${html(order.unit_name_snapshot || "--")} · ${dateTime(order.created_at)}</p></div><div>${statusTag(order.status)}</div></div>
        <dl class="status-list detail-list">
          <dt>配送点</dt><dd>${html(order.delivery_point || "--")}</dd>
          <dt>备注</dt><dd>${html(order.remark || "无")}</dd>
          <dt>订单金额</dt><dd>${money(order.total_cents)}</dd>
        </dl>
        ${action[1] ? `<div class="page-toolbar"><button class="primary-link" data-order="${order.id}" data-status="${action[1]}" data-current-status="${order.status}" data-version="${order.version || 1}">${action[0]}</button></div>` : ""}
      </article>
    `;
    content().innerHTML += table(["食材", "规格", "数量", "单价", "小计"], (order.items || []).map((item) => `
      <tr><td>${html(item.product_name_snapshot || item.product_name)}</td><td>${html(item.spec_snapshot || item.spec || "--")}</td><td>${qty(item.quantity)}</td><td>${money(item.unit_price_cents)}</td><td>${money(item.subtotal_cents)}</td></tr>
    `), "暂无食材明细");
    document.querySelectorAll("[data-order]").forEach((button) => button.addEventListener("click", () => updateOrderStatus(button)));
  }

  async function loadProducts() {
    const params = new URLSearchParams(window.location.search);
    pageShell("食材列表", "价格、库存和供应状态");
    const products = await api("/api/v1/admin/products");
    let rows = products;
    if (params.get("status") === "tight") {
      rows = products.filter((item) => item.supply_status === "tight" || Number(item.available_quantity || 0) <= Number(item.warning_quantity || 0));
    }
    content().innerHTML += table(["食材", "分类", "规格", "单价", "总库存", "预占", "可用", "状态", "操作"], rows.map((item) => `
      <tr><td>${html(item.name)}</td><td>${html(item.category || "--")}</td><td>${html(item.spec || "--")}</td><td>${money(item.price_cents)}</td><td>${qty(item.stock_quantity)} ${html(item.unit)}</td><td>${qty(item.reserved_quantity)}</td><td>${qty(item.available_quantity)}</td><td>${supplyTag(item.supply_status, item.active)}</td><td><button class="table-action" data-price="${item.id}" data-current="${item.price_cents}">改价</button><button class="table-action" data-stock="${item.id}" data-current="${item.stock_quantity}">调库存</button></td></tr>
    `), "暂无食材");
    document.querySelectorAll("[data-price]").forEach((button) => button.addEventListener("click", async () => {
      const value = prompt("请输入新单价，单位：元", (Number(button.dataset.current || 0) / 100).toFixed(2));
      if (value === null) return;
      const cents = Math.round(Number(value) * 100);
      if (!Number.isFinite(cents) || cents < 0) return toast("价格不正确");
      await mutate(`/api/v1/admin/products/${button.dataset.price}/price`, { price_cents: cents });
    }));
    document.querySelectorAll("[data-stock]").forEach((button) => button.addEventListener("click", async () => {
      const value = prompt("请输入新的总库存", button.dataset.current || "0");
      if (value === null) return;
      await mutate(`/api/v1/admin/products/${button.dataset.stock}/stock`, { stock_quantity: value, detail: "Web 后台调整库存" });
    }));
  }

  async function loadUnits() {
    pageShell("子单位管理", "单位名称、编码、配送点和启用状态");
    const units = await api("/api/v1/admin/units");
    content().innerHTML += table(["单位名称", "单位编码", "默认配送点", "账号数", "订单数", "状态", "操作"], units.map((unit) => `
      <tr><td>${html(unit.unit_name)}</td><td>${html(unit.unit_code)}</td><td>${html(unit.default_delivery_point || "--")}</td><td>${num(unit.account_count)}</td><td>${num(unit.order_count)}</td><td>${unit.active ? "启用" : "停用"}</td><td><button class="table-action" data-unit="${unit.id}" data-active="${unit.active ? "0" : "1"}">${unit.active ? "停用" : "启用"}</button></td></tr>
    `), "暂无子单位");
    document.querySelectorAll("[data-unit]").forEach((button) => button.addEventListener("click", async () => {
      await mutate(`/api/v1/admin/units/${button.dataset.unit}/status`, { active: button.dataset.active === "1" });
    }));
  }

  async function loadAccounts() {
    pageShell("账号管理", "内部账号和所属单位");
    const users = await api("/api/v1/admin/users");
    content().innerHTML += table(["登录账号", "显示名称", "角色", "所属单位", "首次改密", "最近登录", "状态", "操作"], users.map((user) => `
      <tr><td>${html(user.username)}</td><td>${html(user.display_name)}</td><td>${user.role === "admin" ? "管理员" : "子单位"}</td><td>${html(user.unit_name || "--")}</td><td>${user.must_change_password ? "是" : "否"}</td><td>${dateTime(user.last_login_at)}</td><td>${user.active ? "启用" : "停用"}</td><td><button class="table-action" data-user="${user.id}" data-active="${user.active ? "0" : "1"}">${user.active ? "停用" : "启用"}</button><button class="table-action" data-reset="${user.id}">重置密码</button></td></tr>
    `), "暂无账号");
    document.querySelectorAll("[data-user]").forEach((button) => button.addEventListener("click", async () => {
      await mutate(`/api/v1/admin/users/${button.dataset.user}/status`, { active: button.dataset.active === "1" });
    }));
    document.querySelectorAll("[data-reset]").forEach((button) => button.addEventListener("click", async () => {
      const password = prompt("请输入新初始密码，至少 8 位并包含字母和数字");
      if (!password) return;
      await mutate(`/api/v1/admin/users/${button.dataset.reset}/reset-password`, { new_password: password, must_change_password: true }, "POST");
    }));
  }

  async function loadLedger() {
    pageShell("采购台账", "按订单和商品明细查看，可导出 Excel");
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    ["start_date", "end_date", "unit_id", "status", "product", "order_no"].forEach((key) => {
      if (params.get(key)) query.set(key, params.get(key));
    });
    content().innerHTML += `<div class="page-toolbar"><a class="primary-link" href="/api/v1/admin/ledger/export.xlsx?${query.toString()}">导出 Excel</a></div>`;
    const rows = await api(`/api/v1/admin/ledger?${query.toString()}`);
    content().innerHTML += table(["订单编号", "单位", "下单时间", "状态", "食材", "数量", "单价", "小计"], rows.slice(0, 300).map((row) => `
      <tr><td>${html(row.order_no)}</td><td>${html(row.unit_name_snapshot || "--")}</td><td>${dateTime(row.created_at)}</td><td>${statusTag(row.status)}</td><td>${html(row.product_name_snapshot)}</td><td>${qty(row.quantity)}</td><td>${money(row.unit_price_cents)}</td><td>${money(row.subtotal_cents)}</td></tr>
    `), "暂无台账记录");
  }

  async function loadPreparationSummary() {
    pageShell("当前备货", "汇总所有待备货和备货中的订单");
    const data = await api("/api/v1/admin/preparation-summary");
    const rows = data.items || data || [];
    content().innerHTML += `<div class="page-toolbar"><a class="primary-link" href="/api/v1/admin/preparation-summary/export.xlsx">导出备货 Excel</a></div>`;
    content().innerHTML += table(["食材", "规格", "单位", "总需求", "订单数", "单位数"], rows.map((item) => `
      <tr><td>${html(item.name || item.product_name)}</td><td>${html(item.spec || "--")}</td><td>${html(item.unit || "--")}</td><td>${qty(item.quantity || item.total_quantity)}</td><td>${num(item.order_count)}</td><td>${num(item.unit_count)}</td></tr>
    `), "暂无备货需求");
  }

  async function loadDeliverySheets() {
    pageShell("单位配送", "按单位查看配送清单");
    const data = await api("/api/v1/admin/delivery-sheets");
    const units = data.units || data.items || data || [];
    content().innerHTML += `<div class="page-toolbar"><a class="primary-link" href="/api/v1/admin/delivery-sheets/export.xlsx">导出配送 Excel</a></div>`;
    content().innerHTML += units.length ? units.map((unit) => `
      <article class="panel section-panel"><div class="panel-header"><div><h2>${html(unit.unit_name || unit.name)}</h2><p>${html(unit.delivery_point || unit.default_delivery_point || "")}</p></div></div>${table(["食材", "数量", "单位", "备注"], (unit.items || []).map((item) => `<tr><td>${html(item.name || item.product_name)}</td><td>${qty(item.quantity)}</td><td>${html(item.unit || "")}</td><td>${html(item.remark || "")}</td></tr>`), "暂无配送明细")}</article>
    `).join("") : empty("暂无配送清单");
  }

  function serviceLabel(value) {
    if (value === "healthy") return "正常";
    if (value === "disabled") return "未启用";
    if (value === "unconfigured") return "未配置";
    return value || "--";
  }

  function bytes(value) {
    const n = Number(value || 0);
    if (n >= 1024 * 1024 * 1024) return (n / 1024 / 1024 / 1024).toFixed(1) + "GB";
    if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + "MB";
    if (n >= 1024) return (n / 1024).toFixed(1) + "KB";
    return n + "B";
  }

  async function loadSystem() {
    pageShell("系统状态", "服务、容量、备份和会话");
    const item = await api("/api/v1/admin/system/overview?detail=true");
    const resourceRows = [
      ["整体状态", item.overall_status === "healthy" ? "正常" : item.overall_status === "warning" ? "需关注" : "异常"],
      ["连续运行", `${Math.floor(Number(item.uptime_seconds || 0) / 3600)} 小时`],
      ["CPU 使用率", `${item.resources?.cpu_percent || 0}%`],
      ["内存使用", `${bytes(item.resources?.memory_used_bytes)} / ${bytes(item.resources?.memory_total_bytes)}`],
      ["磁盘使用", `${bytes(item.resources?.disk_used_bytes)} / ${bytes(item.resources?.disk_total_bytes)}`],
      ["API 平均响应", `${item.performance?.average_latency_ms || item.performance?.averageLatencyMs || 0}ms`],
      ["API P95 响应", `${item.performance?.p95_latency_ms || 0}ms`],
      ["App 会话", num(item.sessions?.active_app_sessions)],
      ["Web 会话", num(item.sessions?.active_web_sessions)],
      ["最近备份", item.latest_backup?.created_at ? `${dateTime(item.latest_backup.created_at)} / ${item.latest_backup.verified ? "已校验" : "未校验"}` : "暂无备份记录"],
    ];
    content().innerHTML += `<section class="metric-grid">${resourceRows.slice(0, 4).map(([label, value]) => `<div class="metric-card"><div class="metric-title">${label}</div><div class="metric-value">${html(value)}</div></div>`).join("")}</section>`;
    content().innerHTML += table(["项目", "状态"], resourceRows.slice(4).map(([label, value]) => `<tr><td>${label}</td><td>${html(value)}</td></tr>`), "");
    const services = item.services || {};
    content().innerHTML += `<article class="panel section-panel"><div class="panel-header"><div><h2>服务状态</h2><p>仅展示运行结果，不展示敏感配置</p></div></div>${table(["服务", "状态"], Object.entries(services).map(([key, value]) => `<tr><td>${html(key)}</td><td>${serviceLabel(value)}</td></tr>`), "暂无服务状态")}</article>`;
    const alerts = item.alerts || [];
    content().innerHTML += `<article class="panel section-panel"><div class="panel-header"><div><h2>系统提醒</h2><p>需要管理员关注的事项</p></div></div>${alerts.length ? alerts.map((alert) => `<div class="notice-row"><strong>${html(alert.title)}</strong><span>${html(alert.impact || alert.message || "")}</span></div>`).join("") : empty("暂无系统提醒")}</article>`;
  }

  async function loadWebSessions() {
    pageShell("网页登录记录", "浏览器登录和最近使用情况");
    const data = await api("/api/v1/web-auth/sessions");
    const rows = data.items || [];
    content().innerHTML += table(["账号", "显示名称", "浏览器", "系统", "登录地址", "创建时间", "最近使用", "状态"], rows.map((row) => `
      <tr><td>${html(row.username || "--")}</td><td>${html(row.display_name || "--")}</td><td>${html(row.browser_name || "浏览器")}</td><td>${html(row.browser_os || "未知系统")}</td><td>${html(row.browser_ip || "--")}</td><td>${dateTime(row.created_at)}</td><td>${dateTime(row.last_seen_at)}</td><td>${row.revoked_at ? "已退出" : "有效"}</td></tr>
    `), "暂无网页登录记录");
  }

  async function loadInventory() {
    pageShell("库存记录", "当前库存快照");
    await loadProducts();
    setTitle("库存记录", "当前库存快照");
  }

  async function loadPlaceholder(title, message) {
    pageShell(title, "页面正在接入");
    content().innerHTML += empty(message);
  }

  async function loadCurrent(silent) {
    if (state.loading) return;
    state.loading = true;
    try {
      const route = currentRoute();
      if (route === "/admin/dashboard") await loadDashboard(silent);
      else if (route === "/admin/orders") await loadOrders();
      else if (route.startsWith("/admin/orders/")) await loadOrderDetail(route.split("/").pop());
      else if (route === "/admin/products" || route.startsWith("/admin/products/")) await loadProducts();
      else if (route === "/admin/inventory") await loadInventory();
      else if (route === "/admin/units") await loadUnits();
      else if (route === "/admin/accounts") await loadAccounts();
      else if (route === "/admin/ledger") await loadLedger();
      else if (route === "/admin/preparation-summary") await loadPreparationSummary();
      else if (route === "/admin/delivery-sheets") await loadDeliverySheets();
      else if (route === "/admin/system") await loadSystem();
      else if (route === "/admin/web-sessions") await loadWebSessions();
      else await loadPlaceholder("管理后台", "该页面暂未接入，请从左侧菜单选择可用功能。");
      if (!silent && route !== "/admin/dashboard") toast("数据已刷新");
    } catch (error) {
      content().innerHTML = `<div class="error-banner">数据加载失败：${html(error.message || "请稍后重试")} <button id="retryButton" type="button">重新加载</button></div>`;
      $("retryButton").addEventListener("click", () => loadCurrent(false));
    } finally {
      state.loading = false;
    }
  }

  async function logout() {
    await fetch("/api/v1/web-auth/logout", {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")) },
    }).catch(() => {});
    window.location.replace("/login");
  }

  function schedule() {
    window.clearInterval(state.timer);
    state.timer = window.setInterval(() => {
      if (!document.hidden) loadCurrent(true);
    }, 60000);
  }

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) loadCurrent(true);
  });

  $("refreshButton").addEventListener("click", () => loadCurrent(false));
  $("logoutButton").addEventListener("click", logout);
  renderNav();
  loadCurrent(true);
  schedule();
})();
