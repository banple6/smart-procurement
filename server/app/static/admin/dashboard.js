(function () {
  const statusText = {
    pending: "待接单",
    accepted: "已接单",
    preparing: "备货中",
    shipped: "已发货",
    completed: "已完成",
    cancelled: "已取消",
  };

  const nav = [
    ["", [["工作台", "/admin/dashboard", "▦"]]],
    ["采购管理", [["订单管理", "/admin/orders", "□"], ["今日备货", "/admin/preparation-summary", "▤"], ["单位配送", "/admin/delivery-sheets", "⇄"], ["收货异常", "/admin/receipt-issues", "!"]]],
    ["食材管理", [["食材列表", "/admin/products", "◇"], ["价格维护", "/admin/products?mode=price", "¥"], ["库存记录", "/admin/inventory", "≡"]]],
    ["组织管理", [["子单位管理", "/admin/units", "⌂"], ["账号管理", "/admin/accounts", "☉"]]],
    ["统计报表", [["采购台账", "/admin/ledger", "▥"], ["商品汇总", "/admin/ledger?tab=products", "↥"], ["数据导出", "/admin/ledger/export", "⇩"]]],
    ["系统", [["网页登录记录", "/admin/web-sessions", "◉"], ["系统状态", "/admin/system", "●"], ["退出登录", "#logout", "↩"]]],
  ];

  const quickActions = [
    ["今日备货单", "/admin/preparation-summary", "按食材汇总今日需求"],
    ["单位配送单", "/admin/delivery-sheets", "按单位查看配送清单"],
    ["待接单订单", "/admin/orders?status=pending", "处理新提交采购单"],
    ["收货异常", "/admin/receipt-issues?status=open", "查看待处理异常"],
    ["食材价格维护", "/admin/products?mode=price", "快速查看价格"],
    ["导出今日台账", "/admin/ledger/export.xlsx", "保存 Excel 台账"],
  ];

  const state = {
    loading: false,
    lastData: null,
    timer: null,
    rangeDays: 7,
    unitSort: "amount",
  };

  function $(id) {
    return document.getElementById(id);
  }

  function money(cents) {
    return "¥" + (Number(cents || 0) / 100).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function num(value) {
    return Number(value || 0).toLocaleString("zh-CN");
  }

  function dateTime(value) {
    if (!value) return "--";
    return String(value).replace("T", " ").slice(0, 19);
  }

  function shortTime(value) {
    if (!value) return "--";
    return String(value).replace("T", " ").slice(5, 16);
  }

  function cookie(name) {
    return document.cookie.split("; ").find((item) => item.startsWith(name + "="))?.split("=")[1] || "";
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
    return `<div class="empty-state">${text}</div>`;
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
      ["待处理异常", num(metrics.open_receipt_issues), "收货异常需跟进", "查看异常 >", "/admin/receipt-issues?status=open", metrics.open_receipt_issues ? "danger" : ""],
      ["库存预警", num(metrics.tight_inventory), "库存不足或供应紧张", "查看库存 >", "/admin/products?status=tight", metrics.tight_inventory ? "warning" : ""],
    ];
    $("metricGrid").innerHTML = cards.map(([title, value, note, link, href, tone]) => `
      <a class="metric-card" href="${href}">
        <div class="metric-title">${title}</div>
        <div class="metric-value ${tone}">${value}</div>
        <div class="metric-note">${note}</div>
        <div class="metric-link">${link}</div>
      </a>
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
      return `<div class="task-item">
        <div>
          <div class="task-name">${task.name}</div>
          <div class="task-desc">${detail}</div>
        </div>
        <div>
          <div class="task-count ${task.priority === "urgent" ? "urgent" : task.priority === "warning" ? "warning" : ""}">${num(task.count)} ${unitLabel}</div>
          <button class="table-action primary" type="button" data-target="${task.target_url}">${task.action_label}</button>
        </div>
      </div>`;
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
      return `<g><rect class="bar" x="${x}" y="${y}" width="${Math.max(10, step * 0.64)}" height="${barHeight}"><title>${row.date}：${row.order_count} 单，${money(row.amount_cents)}</title></rect>
        <text class="chart-label" x="${x}" y="${height - 10}">${row.date.slice(5)}</text></g>`;
    }).join("");
    const totalOrders = rows.reduce((sum, row) => sum + row.order_count, 0);
    const totalAmount = rows.reduce((sum, row) => sum + row.amount_cents, 0);
    $("trendSummary").textContent = `共 ${num(totalOrders)} 单，${money(totalAmount)}`;
    $("trendChart").innerHTML = `<svg class="chart-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="采购趋势图">${bars}<polyline class="line" points="${points}" /></svg>`;
  }

  function statusTag(status) {
    return `<span class="status-tag status-${status}">${statusText[status] || status || "--"}</span>`;
  }

  function primaryAction(order) {
    if (order.status === "pending") return ["接单", `/admin/orders/${order.id}`];
    if (order.status === "accepted" || order.status === "preparing") return ["查看", `/admin/orders/${order.id}`];
    if (order.status === "shipped") return ["跟进", `/admin/orders/${order.id}`];
    return ["查看", `/admin/orders/${order.id}`];
  }

  function renderRecentOrders(data) {
    const rows = data.recent_orders || [];
    if (!rows.length) {
      $("recentOrders").innerHTML = `<tr><td colspan="8">${empty("暂无订单")}</td></tr>`;
      return;
    }
    $("recentOrders").innerHTML = rows.map((order) => {
      const action = primaryAction(order);
      return `<tr>
        <td><a href="/admin/orders/${order.id}">${order.order_no}</a></td>
        <td>${order.unit_name_snapshot || "--"}</td>
        <td>${shortTime(order.created_at)}</td>
        <td>${num(order.item_count)} 种</td>
        <td>${money(order.total_cents)}</td>
        <td>${statusTag(order.status)}</td>
        <td>${Number(order.open_issue_count || 0) > 0 ? "有异常" : "—"}</td>
        <td><button class="table-action primary" type="button" data-target="${action[1]}">${action[0]}</button></td>
      </tr>`;
    }).join("");
  }

  function renderInventory(data) {
    const rows = data.inventory_alerts || [];
    if (!rows.length) {
      $("inventoryAlerts").innerHTML = empty("暂无库存预警");
      return;
    }
    $("inventoryAlerts").innerHTML = rows.map((item) => `
      <div class="inventory-row">
        <div><span class="cell-label">食材</span><span class="cell-value">${item.name}</span></div>
        <div><span class="cell-label">总库存</span><span class="cell-value">${item.stock_quantity} ${item.unit}</span></div>
        <div><span class="cell-label">预占</span><span class="cell-value">${item.reserved_quantity}</span></div>
        <div><span class="cell-label">可用</span><span class="cell-value">${item.available_quantity}</span></div>
        <button class="table-action" type="button" data-target="/admin/products/${item.id}">调整库存</button>
      </div>
    `).join("");
  }

  function renderRanks(data) {
    const demand = data.demand_rank || [];
    $("demandRank").innerHTML = demand.length ? demand.map((item, index) => `
      <div class="rank-row">
        <strong>${index + 1}</strong>
        <div><span class="cell-label">食材</span><span class="cell-value">${item.name}</span></div>
        <div><span class="cell-label">数量</span><span class="cell-value">${Number(item.quantity || 0).toLocaleString("zh-CN")} ${item.unit}</span></div>
        <div><span class="cell-label">单位</span><span class="cell-value">${num(item.unit_count)}</span></div>
        <div><span class="cell-label">订单</span><span class="cell-value">${num(item.order_count)}</span></div>
      </div>
    `).join("") : empty("今日暂无需求数据");

    const units = data.unit_rank || [];
    $("unitRank").innerHTML = units.length ? units.map((item, index) => `
      <a class="rank-row" href="/admin/orders?unit_id=${item.unit_id}&date=${data.business_date}">
        <strong>${index + 1}</strong>
        <div><span class="cell-label">单位</span><span class="cell-value">${item.unit_name}</span></div>
        <div><span class="cell-label">订单</span><span class="cell-value">${num(item.order_count)}</span></div>
        <div><span class="cell-label">金额</span><span class="cell-value">${money(item.total_cents)}</span></div>
        <div><span class="cell-label">异常</span><span class="cell-value">${num(item.open_issue_count)}</span></div>
      </a>
    `).join("") : empty("今日暂无单位采购数据");
  }

  function renderQuickActions() {
    $("quickActions").innerHTML = quickActions.map(([title, href, desc]) => `
      <a class="quick-card" href="${href}"><strong>${title}</strong><span>${desc}</span></a>
    `).join("");
  }

  function renderSystem(data) {
    const item = data.system_status || {};
    $("systemStatus").innerHTML = `
      <dt>服务状态</dt><dd>${item.service === "ok" ? "正常" : "异常"}</dd>
      <dt>最近数据同步</dt><dd>${dateTime(item.last_data_sync)}</dd>
      <dt>最近备份</dt><dd>${item.last_backup_at ? dateTime(item.last_backup_at) : "暂无备份记录"}</dd>
      <dt>磁盘使用</dt><dd>${num(item.disk_usage_percent)}%</dd>
      <dt>当前版本</dt><dd>${item.version || "Web 1.1.0"}</dd>
    `;
  }

  function render(data) {
    $("businessDate").textContent = `${data.business_date} 业务日`;
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
    if (state.loading) return;
    state.loading = true;
    $("globalError").hidden = true;
    try {
      const url = `/api/v1/admin/dashboard/overview?range_days=${state.rangeDays}&unit_sort=${state.unitSort}`;
      const response = await fetch(url, { credentials: "same-origin", headers: { "Accept": "application/json" } });
      if (response.status === 401) {
        window.location.replace("/login?expired=1");
        return;
      }
      if (response.status === 403) {
        throw new Error("当前账号无权限访问管理员工作台");
      }
      if (!response.ok) throw new Error("工作台数据加载失败");
      const data = await response.json();
      state.lastData = data;
      render(data);
      if (!silent) toast("工作台已刷新");
    } catch (error) {
      if (!state.lastData) $("globalError").hidden = false;
      const staleSuffix = "，数据可能不是最新";
      if (state.lastData) {
        const currentText = $("refreshTime").textContent;
        $("refreshTime").textContent = currentText.includes(staleSuffix) ? currentText : currentText + staleSuffix;
      } else {
        $("refreshTime").textContent = "加载失败";
      }
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
      if (!document.hidden) loadDashboard(true);
    }, 60000);
  }

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) loadDashboard(true);
  });

  $("refreshButton").addEventListener("click", () => loadDashboard(false));
  $("retryButton").addEventListener("click", () => loadDashboard(false));
  $("logoutButton").addEventListener("click", logout);
  $("rangeSelect").addEventListener("change", (event) => {
    state.rangeDays = Number(event.target.value);
    loadDashboard(true);
  });
  $("unitSort").addEventListener("change", (event) => {
    state.unitSort = event.target.value;
    loadDashboard(true);
  });
  renderNav();
  loadDashboard(true);
  schedule();
})();
