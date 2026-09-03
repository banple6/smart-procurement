(function () {
  const statusText = {
    pending: "待接单",
    accepted: "已接单",
    preparing: "已接单 / 待完成",
    shipped: "已发货",
    completed: "已完成",
    cancelled: "已取消",
    voided: "已作废",
  };

  const supplyText = {
    normal: "正常供应",
    tight: "库存紧张",
    paused: "暂停供应",
    off_shelf: "已下架",
  };

  const productCategories = ["蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他"];
  const productUnits = ["公斤", "斤", "箱", "袋", "个", "筐", "盒", "瓶", "份", "包"];
  const productStorageMethods = ["常温", "冷藏", "冷冻", "阴凉干燥"];
  const productSupplyStatuses = [["normal", "正常供应"], ["tight", "库存紧张"], ["paused", "暂停供应"]];
  const SIDEBAR_STORAGE_KEY = "adminSidebarCollapsed";

  const navIconPaths = {
    dashboard: '<rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect>',
    orders: '<rect x="5" y="4" width="14" height="17" rx="2"></rect><path d="M9 4V2.5M15 4V2.5M8.5 10h7M8.5 14h7M8.5 18h4"></path>',
    batches: '<path d="M4 8.5 12 4l8 4.5-8 4.5-8-4.5Z"></path><path d="M4 8.5V16l8 4.5 8-4.5V8.5M12 13v7.5"></path>',
    outbound: '<path d="M3 6h11v10H3zM14 9h3l3 3v4h-6z"></path><circle cx="7" cy="18" r="1.7"></circle><circle cx="17" cy="18" r="1.7"></circle>',
    products: '<path d="M4 10h16l-1.5 10h-13z"></path><path d="M8 10c0-3 1.5-5 4-5s4 2 4 5M9 15h6"></path>',
    spreadsheet: '<path d="M5 3h10l4 4v14H5z"></path><path d="M15 3v5h4M8 12h8M8 16h8M11 10v8M15 10v8"></path>',
    inventory: '<path d="M3 10 12 4l9 6v10H3z"></path><path d="M7 20v-6h10v6M7 10h10"></path>',
    units: '<path d="M4 21V5h10v16M14 10h6v11M7 8h2M7 12h2M7 16h2M16.5 13h1"></path>',
    accounts: '<circle cx="9" cy="8" r="3"></circle><path d="M3.5 20c.6-4 2.4-6 5.5-6s4.9 2 5.5 6M16 6.5a2.5 2.5 0 0 1 0 5M17.5 14c2 .7 3 2.6 3 5"></path>',
    analytics: '<path d="M4 20V4M4 20h17"></path><path d="m7 15 4-4 3 2 5-6"></path><circle cx="7" cy="15" r="1"></circle><circle cx="11" cy="11" r="1"></circle><circle cx="14" cy="13" r="1"></circle><circle cx="19" cy="7" r="1"></circle>',
    ledger: '<path d="M5 3h12a2 2 0 0 1 2 2v16H7a2 2 0 0 1-2-2z"></path><path d="M5 5h12M8 10h7M8 14h7M8 18h5"></path>',
    download: '<path d="M12 3v12M8 11l4 4 4-4M4 20h16"></path>',
    help: '<circle cx="12" cy="12" r="9"></circle><path d="M9.5 9a2.7 2.7 0 1 1 4.2 2.2c-1.1.8-1.7 1.3-1.7 2.8M12 17.5h.01"></path>',
    sessions: '<rect x="3" y="4" width="18" height="14" rx="2"></rect><path d="M8 21h8M12 18v3M8 9h.01M12 9h5"></path>',
    announcements: '<path d="M5 5h14v11H9l-4 3V5Z"></path><path d="M8 9h8M8 12h5"></path>',
    system: '<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.3 2.3-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-3.2v-.2a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L6 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H4.6v-3.2h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L6 7.8 8.3 5.5l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h3.2v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1 2.3 2.3-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2V14h-.2a1.7 1.7 0 0 0-1.6 1Z"></path>',
    status: '<circle cx="12" cy="12" r="8"></circle><path d="M12 8v4l2.5 2.5"></path>',
    logout: '<path d="M10 5H5v14h5M14 8l4 4-4 4M9 12h9"></path>',
  };

  const nav = [
    ["", [["工作台", "/admin/dashboard", "dashboard"]]],
    ["采购管理", [["订单管理", "/admin/orders", "orders"], ["备货单", "/admin/batches", "batches"], ["出库单", "/admin/outbounds", "outbound"]]],
    ["食材管理", [["食材列表", "/admin/products", "products"], ["Excel 智能导入", "/admin/price-imports", "spreadsheet"], ["库存记录", "/admin/inventory", "inventory"]]],
    ["组织管理", [["子单位管理", "/admin/units", "units"], ["账号管理", "/admin/accounts", "accounts"]]],
    ["统计报表", [["数据分析", "/admin/analytics", "analytics"], ["采购台账", "/admin/ledger", "ledger"]]],
    ["系统", [["公告管理", "/admin/announcements", "announcements"], ["下载 App", "/download", "download"], ["帮助中心", "/help/admin", "help"], ["网页登录记录", "/admin/web-sessions", "sessions"], ["系统日志", "/admin/system-logs", "system"], ["系统状态", "/admin/system", "status"], ["退出登录", "#logout", "logout"]]],
  ];

  const quickActions = [
    ["备货单", "/admin/batches", "选择订单并汇总今日备货需求"],
    ["待接单订单", "/admin/orders?status=pending", "处理新提交采购单"],
    ["食材价格维护", "/admin/products?mode=price", "快速改价和库存"],
    ["Excel 智能导入", "/admin/price-imports", "批量建立食材并同步供应商报价"],
    ["系统状态", "/admin/system", "查看服务和备份"],
    ["采购台账", "/admin/ledger", "按日期、单位、状态查询并导出"],
  ];

  const state = {
    loading: false,
    queuedRefresh: false,
    refreshSequence: 0,
    lastSuccessfulSyncAt: 0,
    formDirty: false,
    lastData: null,
    timer: null,
    reminderTimer: null,
    pendingOrderIds: null,
    baseTitle: document.title,
    rangeDays: 7,
    unitSort: "amount",
    productFormOpen: false,
    productSelectionMode: false,
    selectedProductIds: new Set(),
    selectedOrderIds: new Set(),
    selectedOrderVersions: new Map(),
    selectedBatchIds: new Set(),
    batchItems: [],
    batchWorkbench: [],
    selectedOutboundIds: new Set(),
    outboundItems: [],
    unitItems: [],
    orderItems: [],
    orderBulkBusy: false,
    mutationInFlight: 0,
    lastScrollInteractionAt: 0,
    routeFingerprint: "",
    pendingRefresh: false,
    dashboardAnnouncements: [],
    realtime: {
      eventSource: null,
      connected: false,
      revisions: {},
      dirtyResources: new Set(),
      timers: new Map(),
      fetches: new Map(),
      fallbackTimer: null,
    },
    productFormDefaults: {
      productCategory: "蔬菜",
      productUnit: "公斤",
      productSupplyStatus: "normal",
      productActive: true,
      productStorageMethod: "冷藏",
      productMinOrder: "1",
      productStep: "1",
      productWarning: "0",
    },
  };

  const staleSuffix = "，数据可能不是最新";

  function $(id) {
    return document.getElementById(id);
  }

  function navIcon(name) {
    const path = navIconPaths[name] || navIconPaths.dashboard;
    return `<svg viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">${path}</svg>`;
  }

  function sidebarCollapsed() {
    try {
      return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true";
    } catch (_) {
      return false;
    }
  }

  function updateSidebar(collapsed, persist = true) {
    document.body.classList.toggle("sidebar-collapsed", collapsed);
    const toggle = $("sidebarToggle");
    const glyph = $("sidebarToggleGlyph");
    const label = collapsed ? "展开侧边栏" : "折叠侧边栏";
    if (toggle) {
      toggle.setAttribute("aria-label", label);
      toggle.setAttribute("title", label);
      toggle.setAttribute("aria-expanded", String(!collapsed));
    }
    if (glyph) glyph.textContent = collapsed ? "›" : "‹";
    if (!persist) return;
    try {
      window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed));
    } catch (_) {
      // Navigation remains usable when storage is unavailable.
    }
  }

  function closeMobileSidebar() {
    document.body.classList.remove("sidebar-mobile-open");
    const toggle = $("mobileNavToggle");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
  }

  function toggleMobileSidebar() {
    const opening = !document.body.classList.contains("sidebar-mobile-open");
    document.body.classList.toggle("sidebar-mobile-open", opening);
    const toggle = $("mobileNavToggle");
    if (toggle) toggle.setAttribute("aria-expanded", String(opening));
  }

  function setupSidebar() {
    updateSidebar(sidebarCollapsed(), false);
    $("sidebarToggle")?.addEventListener("click", () => updateSidebar(!document.body.classList.contains("sidebar-collapsed")));
    $("mobileNavToggle")?.addEventListener("click", toggleMobileSidebar);
    $("mobileNavBackdrop")?.addEventListener("click", closeMobileSidebar);
    window.addEventListener("resize", () => {
      if (window.innerWidth >= 768) closeMobileSidebar();
    });
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

  function orderMonthKey(order) {
    const value = display(order.created_at, "");
    return /^\d{4}-\d{2}/.test(value) ? value.slice(0, 7) : "unknown";
  }

  function orderMonthLabel(month) {
    if (month === "unknown") return "时间未记录";
    const [year, value] = month.split("-");
    return `${year}年${Number(value)}月`;
  }

  function cookie(name) {
    const found = document.cookie.split("; ").find((item) => item.startsWith(name + "="));
    return found ? found.split("=").slice(1).join("=") : "";
  }

  function requestId(prefix) {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
      return `${prefix}-${window.crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function toast(message) {
    const box = $("toast");
    box.textContent = message;
    box.hidden = false;
    window.setTimeout(() => {
      box.hidden = true;
    }, 2200);
  }

  function setSyncStatus(message) {
    const target = $("refreshTime");
    if (target) target.textContent = message;
  }

  function formatSyncTime() {
    return new Date().toLocaleTimeString("zh-CN", { hour12: false });
  }

  function automaticRefreshShouldWait() {
    const active = document.activeElement;
    const pendingBatchSelections = document.querySelectorAll("[data-unit-pending-order]:checked").length;
    return window.AdminRefreshPolicy.shouldDefer({
      formDirty: state.formDirty,
      selectionCount: state.selectedOrderIds.size + state.selectedProductIds.size + state.selectedBatchIds.size + state.selectedOutboundIds.size + pendingBatchSelections,
      dialogOpen: Boolean(document.querySelector('.org-dialog-backdrop, [role="dialog"]')),
      mutationInFlight: state.mutationInFlight,
      activeTag: active?.tagName || "",
      recentScrollMs: state.lastScrollInteractionAt ? Date.now() - state.lastScrollInteractionAt : Infinity,
    });
  }

  function showNewDataBanner(message = "检测到新数据，当前页面不会自动跳动。") {
    state.pendingRefresh = true;
    const banner = $("newDataBanner");
    if (!banner) return;
    banner.querySelector("span").textContent = message;
    banner.hidden = false;
    setSyncStatus("检测到新数据，等待手动刷新");
  }

  function hideNewDataBanner() {
    state.pendingRefresh = false;
    const banner = $("newDataBanner");
    if (banner) banner.hidden = true;
  }

  function shouldMarkFormDirty(target) {
    if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement)) return false;
    return Boolean(target.closest("form"));
  }

  function renderAdminOrderReminder(orders, total) {
    const count = Math.max(0, Number(total || 0));
    const badge = $("orderReminderBadge");
    const notice = $("orderReminderNotice");
    badge.textContent = count > 99 ? "99+" : String(count);
    badge.hidden = count === 0;
    notice.hidden = count === 0;
    notice.textContent = count === 0 ? "" : `新订单提醒：有 ${count} 笔采购单等待接单，点击查看`;
    document.title = count > 0 ? `(${count}) ${state.baseTitle}` : state.baseTitle;

    const ids = orders.map((order) => order.id);
    if (Array.isArray(state.pendingOrderIds)) {
      const previous = new Set(state.pendingOrderIds);
      const created = orders.filter((order) => !previous.has(order.id));
      if (created.length > 0) {
        const first = created[0];
        toast(created.length === 1 ? `收到新订单 ${first.order_no}` : `收到 ${created.length} 笔新订单`);
      }
    }
    state.pendingOrderIds = ids;
  }

  async function checkAdminOrderReminders() {
    try {
      const data = await api("/api/v1/admin/orders?status=pending&page=1&page_size=100");
      renderAdminOrderReminder(data.items || [], data.total || 0);
    } catch (error) {
      reportClientError(error.message || "订单提醒同步失败", "/api/v1/admin/orders", { reminder: true });
    }
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
    const mutation = ["POST", "PUT", "PATCH", "DELETE"].includes(method);
    const csrfHeaders = ["POST", "PUT", "PATCH", "DELETE"].includes(method) ? { "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")) } : {};
    try {
      if (mutation) state.mutationInFlight += 1;
      const response = await fetch(path, {
        credentials: "same-origin",
        ...options,
        headers: { "Accept": "application/json", ...csrfHeaders, ...(options.headers || {}) },
      });
      if (response.status === 401) {
        window.location.replace("/login?expired=1");
        throw Object.assign(new Error("登录已过期，请重新登录"), { status: 401 });
      }
      if (!response.ok) {
        let detail = "";
        let payload = {};
        try {
          payload = await response.json();
          detail = typeof payload.detail === "string" ? payload.detail : "";
        } catch (_) {
          detail = "";
        }
        const message = window.AdminRefreshPolicy.apiErrorMessage(response.status, detail);
        reportClientError(message, path, { method, status: response.status });
        const error = new Error(message);
        error.status = response.status;
        error.code = payload.code || "";
        error.latest = payload.latest || null;
        throw error;
      }
      if (response.status === 204) return {};
      return response.json();
    } catch (error) {
      if (error?.name === "AbortError") throw error;
      if (error.status) throw error;
      reportClientError("网络请求失败", path, { method });
      throw Object.assign(new Error("网络异常，请重试"), { status: 0 });
    } finally {
      if (mutation) state.mutationInFlight = Math.max(0, state.mutationInFlight - 1);
    }
  }

  async function mutate(path, body, method = "PATCH") {
    try {
      const result = await api(path, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
      });
      state.formDirty = false;
      toast("操作已完成");
      await loadCurrent(true, "write");
      return result;
    } catch (error) {
      if (error.status === 409) {
        state.formDirty = false;
        await loadCurrent(true, "conflict");
        toast("该数据已被其他管理员更新，已为您刷新最新状态。");
      }
      throw error;
    }
  }

  function reportClientError(message, path = window.location.pathname, context = {}) {
    fetch("/api/v1/admin/system/client-errors", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")),
      },
      body: JSON.stringify({
        message: String(message || "前端操作失败").slice(0, 300),
        path,
        context,
      }),
    }).catch(() => {});
  }

  function renderNav() {
    const current = window.location.pathname + window.location.search;
    $("navMenu").innerHTML = nav.map(([title, items]) => {
      const links = items.map(([label, href, icon]) => {
        const active = href !== "#logout" && current.startsWith(href.split("?")[0]);
        const safeLabel = html(label);
        return `<a class="nav-item ${active ? "active" : ""}" href="${href}" data-href="${href}" data-label="${safeLabel}" aria-label="${safeLabel}" title="${safeLabel}"><span class="nav-icon">${navIcon(icon)}</span><span class="nav-label">${safeLabel}</span></a>`;
      }).join("");
      return `<div class="nav-section">${title ? `<div class="nav-section-title">${title}</div>` : ""}${links}</div>`;
    }).join("");
    document.querySelectorAll('[data-href="#logout"]').forEach((item) => item.addEventListener("click", (event) => {
      event.preventDefault();
      logout();
    }));
    document.querySelectorAll(".nav-item").forEach((item) => item.addEventListener("click", () => {
      if (window.innerWidth < 768) closeMobileSidebar();
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
      <a id="urgentAnnouncementBanner" class="announcement-urgent-banner" href="/admin/announcements" hidden></a>
      <section id="metricGrid" class="metric-grid" aria-label="关键指标"></section>
      <section class="dashboard-grid"><article class="panel"><div class="panel-header"><div><h2>公告通知</h2><p>当前有效公告</p></div><a href="/admin/announcements">查看全部</a></div><div id="dashboardAnnouncements" class="simple-list"></div></article></section>
      <section class="dashboard-grid first">
        <article class="panel todo-panel"><div class="panel-header"><div><h2>待办中心</h2><p>优先处理异常、超时和发货任务</p></div></div><div id="taskList" class="task-list"></div></article>
        <article class="panel trend-panel"><div class="panel-header"><div><h2>近 7 日采购趋势</h2><p id="trendSummary">--</p></div><select id="rangeSelect" aria-label="趋势范围"><option value="7">近 7 日</option><option value="14">近 14 日</option><option value="30">近 30 日</option></select></div><div id="trendChart" class="trend-chart"></div></article>
      </section>
      <section class="dashboard-grid">
        <article class="panel table-panel"><div class="panel-header"><div><h2>最近订单</h2><p>最多显示最近 10 笔</p></div><a href="/admin/orders">查看全部订单</a></div><div class="table-wrap"><table><thead><tr><th>订单编号</th><th>子单位</th><th>下单时间</th><th>食材种类</th><th>订单金额</th><th>当前状态</th><th>异常标记</th><th>操作</th></tr></thead><tbody id="recentOrders"></tbody></table></div></article>
        <article class="panel table-panel"><div class="panel-header"><div><h2>库存预警</h2><p>优先显示库存不足和暂停供应</p></div><a href="/admin/products?status=tight">查看食材列表</a></div><div id="inventoryAlerts" class="inventory-list"></div></article>
      </section>
      <section class="dashboard-grid">
        <article class="panel"><div class="panel-header"><div><h2>今日需求排行</h2><p>按实际供应数量统计</p></div><a href="/admin/batches">查看备货单</a></div><div id="demandRank" class="rank-list"></div></article>
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
      ["待完成", num(metrics.waiting_shipment), "已接单订单可直接完成并自动生成出库单", "查看待完成 >", "/admin/orders?status=preparing", metrics.waiting_shipment ? "warning" : ""],
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

  function productOptionButtons(inputName, options, selectedValue) {
    return `<div class="option-row" data-option-group="${html(inputName)}">${options.map((option) => {
      const value = Array.isArray(option) ? option[0] : option;
      const label = Array.isArray(option) ? option[1] : option;
      return `<button class="option-button ${value === selectedValue ? "active" : ""}" type="button" data-option-input="${html(inputName)}" data-option-value="${html(value)}">${html(label)}</button>`;
    }).join("")}</div><input type="hidden" name="${html(inputName)}" value="${html(selectedValue)}" />`;
  }

  function defaultProductSpec(unit) {
    return ["公斤", "斤"].includes(String(unit || "").trim()) ? "散装" : "预包装";
  }

  function syncProductSpec(root, unit) {
    const input = root.querySelector('input[name="productSpec"]');
    if (!input) return;
    const current = String(input.value || "").trim();
    if (!current || ["散装", "预包装"].includes(current)) input.value = defaultProductSpec(unit);
  }

  function productFormTemplate() {
    const defaults = state.productFormDefaults;
    if (!state.productFormOpen) return "";
    return `
      <article class="panel section-panel product-create-panel" id="productCreatePanel">
        <div class="panel-header">
          <div><h2>添加食材</h2><p>一页快速录入，保存后立即进入食材列表</p></div>
        </div>
        <form id="productCreateForm" class="product-form">
          <div class="form-grid compact">
            <label class="form-field span-2"><span>食材名称</span><input name="productName" type="text" autocomplete="off" required placeholder="例如：青菜" /></label>
            <label class="form-field span-2"><span>食材分类</span>${productOptionButtons("productCategory", productCategories, defaults.productCategory)}</label>
            <label class="form-field"><span>规格</span><input name="productSpec" type="text" value="${defaultProductSpec(defaults.productUnit)}" required /></label>
            <label class="form-field"><span>计量单位</span>${productOptionButtons("productUnit", productUnits, defaults.productUnit)}</label>
            <label class="form-field"><span>单价（元）</span><input name="productPrice" type="number" min="0" step="0.01" inputmode="decimal" placeholder="0.00" /></label>
            <label class="form-field"><span>当前库存</span><input name="productStock" type="number" min="0" step="0.01" inputmode="decimal" value="0" /></label>
            <label class="form-field span-2"><span>供应状态</span>${productOptionButtons("productSupplyStatus", productSupplyStatuses, defaults.productSupplyStatus)}</label>
            <label class="switch-field"><input name="productActive" type="checkbox" ${defaults.productActive ? "checked" : ""} /> <span>是否上架</span></label>
          </div>
          <details class="form-details">
            <summary>申领规则：最小 ${html(defaults.productMinOrder)} · 步长 ${html(defaults.productStep)} · 预警 ${html(defaults.productWarning)}</summary>
            <div class="form-grid compact">
              <label class="form-field"><span>最小申领量</span><input name="productMinOrder" type="number" min="0.01" step="0.01" value="${html(defaults.productMinOrder)}" /></label>
              <label class="form-field"><span>数量步长</span><input name="productStep" type="number" min="0.01" step="0.01" value="${html(defaults.productStep)}" /></label>
              <label class="form-field"><span>库存预警值</span><input name="productWarning" type="number" min="0" step="0.01" value="${html(defaults.productWarning)}" /></label>
            </div>
          </details>
          <details class="form-details">
            <summary>更多资料：编码、产地、供应商、储存方式等</summary>
            <div class="form-grid compact">
              <label class="form-field"><span>食材编码</span><input name="productCode" type="text" autocomplete="off" placeholder="不填将自动生成" /></label>
              <label class="form-field"><span>产地</span><input name="productOrigin" type="text" autocomplete="off" /></label>
              <label class="form-field"><span>供应商</span><input name="productSupplier" type="text" autocomplete="off" /></label>
              <label class="form-field span-2"><span>储存方式</span>${productOptionButtons("productStorageMethod", productStorageMethods, defaults.productStorageMethod)}</label>
              <label class="form-field"><span>保质期说明</span><input name="productShelfLife" type="text" autocomplete="off" /></label>
              <label class="form-field span-2"><span>食材说明</span><input name="productDescription" type="text" autocomplete="off" /></label>
            </div>
          </details>
          <div class="page-toolbar form-actions">
            <button class="table-action" type="button" data-cancel-create-product>取消</button>
            <button class="primary-link secondary" type="submit" data-save-product="continue">保存并继续添加</button>
            <button class="primary-link" type="submit" data-save-product="close">保存食材</button>
          </div>
        </form>
      </article>
    `;
  }

  function bindProductOptionButtons(root) {
    root.querySelectorAll("[data-option-input]").forEach((button) => {
      button.addEventListener("click", () => {
        const name = button.dataset.optionInput;
        const input = root.querySelector(`input[name="${name}"]`);
        if (input) input.value = button.dataset.optionValue || "";
        root.querySelectorAll(`[data-option-input="${name}"]`).forEach((item) => item.classList.toggle("active", item === button));
        if (name === "productUnit") syncProductSpec(root, button.dataset.optionValue || "");
      });
    });
  }

  function productPayload(form) {
    const data = new FormData(form);
    const price = Number(data.get("productPrice") || 0);
    return {
      product_code: String(data.get("productCode") || "").trim() || `WEB-${Date.now()}-${Math.random().toString(16).slice(2, 8).toUpperCase()}`,
      name: String(data.get("productName") || "").trim(),
      category: String(data.get("productCategory") || "蔬菜"),
      spec: String(data.get("productSpec") || "").trim() || defaultProductSpec(data.get("productUnit")),
      unit: String(data.get("productUnit") || "公斤"),
      price_cents: Math.round(price * 100),
      stock_quantity: String(data.get("productStock") || "0"),
      reserved_quantity: "0",
      min_order_quantity: String(data.get("productMinOrder") || "1"),
      quantity_step: String(data.get("productStep") || "1"),
      warning_quantity: String(data.get("productWarning") || "0"),
      origin: String(data.get("productOrigin") || "").trim(),
      supplier: String(data.get("productSupplier") || "").trim(),
      shelf_life: String(data.get("productShelfLife") || "").trim(),
      storage_method: String(data.get("productStorageMethod") || "冷藏"),
      description: String(data.get("productDescription") || "").trim(),
      supply_status: String(data.get("productSupplyStatus") || "normal"),
      active: Boolean(data.get("productActive")),
    };
  }

  function rememberProductDefaults(form) {
    const data = new FormData(form);
    state.productFormDefaults = {
      productCategory: String(data.get("productCategory") || "蔬菜"),
      productUnit: String(data.get("productUnit") || "公斤"),
      productSupplyStatus: String(data.get("productSupplyStatus") || "normal"),
      productActive: Boolean(data.get("productActive")),
      productStorageMethod: String(data.get("productStorageMethod") || "冷藏"),
      productMinOrder: String(data.get("productMinOrder") || "1"),
      productStep: String(data.get("productStep") || "1"),
      productWarning: String(data.get("productWarning") || "0"),
    };
  }

  function primaryAction(order) {
    if (order.status === "pending") return ["接单", "accepted"];
    if (order.status === "accepted") return ["完成", "fast_complete"];
    if (order.status === "preparing") return ["完成", "fast_complete"];
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

  function announcementLevelLabel(level) {
    return ({ normal: "通知", important: "重要", urgent: "紧急" })[level] || "通知";
  }

  function renderDashboardAnnouncements(items) {
    const target = $("dashboardAnnouncements");
    const announcements = items || [];
    target.innerHTML = announcements.length ? announcements.map((item) => `
      <button class="announcement-row" type="button" data-announcement-detail="${html(item.id)}">
        <span class="announcement-level ${html(item.level)}">${item.is_pinned ? "置顶 · " : ""}${html(announcementLevelLabel(item.level))}</span>
        <strong>${html(item.title)}</strong><time>${html((item.display_publish_at || item.display_created_at || "").slice(5, 16))}</time>
      </button>
    `).join("") : empty("暂无公告");
    target.querySelectorAll("[data-announcement-detail]").forEach((button) => button.addEventListener("click", () => {
      openAnnouncementDetail(button.dataset.announcementDetail).catch((error) => toast(error.message || "加载公告失败"));
    }));
    const urgent = announcements.find((item) => item.level === "urgent");
    const banner = $("urgentAnnouncementBanner");
    banner.hidden = !urgent;
    banner.textContent = urgent ? `紧急公告：${urgent.title} · 点击查看` : "";
  }

  function renderDashboard(data, announcements) {
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
    renderDashboardAnnouncements(announcements);
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
      const [data, announcements] = await Promise.all([
        api(`/api/v1/admin/dashboard/overview?range_days=${state.rangeDays}&unit_sort=${state.unitSort}`),
        api("/api/v1/announcements?limit=5"),
      ]);
      state.lastData = data;
      state.dashboardAnnouncements = announcements.items || [];
      renderDashboard(data, state.dashboardAnnouncements);
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
    const isAccept = button.dataset.status === "accepted";
    const orderId = button.dataset.order;
    try {
      await api(`/api/v1/admin/orders/${orderId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          status: button.dataset.status,
          expected_status: button.dataset.currentStatus,
          expected_version: Number(button.dataset.version || 0) || undefined,
        }),
      });
      if (isAccept) {
        toast("接单成功，可直接完成订单");
        await loadCurrent(true);
      } else {
        toast("操作已完成");
        await loadCurrent(true);
      }
    } catch (error) {
      toast(error.message || "操作失败，请刷新后重试");
      button.disabled = false;
      button.textContent = label;
    }
  }

  function fastCompleteConflict(error) {
    const detail = String(error?.message || "").trim();
    const stale = !detail
      || detail.includes("订单已被其他管理员修改")
      || detail.includes("订单状态已被其他操作员更新");
    if (stale) {
      return {
        message: detail ? "订单状态已更新，请重新确认。" : "订单状态已更新，请刷新后重新确认。",
        refresh: true,
      };
    }
    return { message: detail, refresh: false };
  }

  async function openFastCompleteReview(button) {
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "加载中...";
    try {
      const order = await api(`/api/v1/admin/orders/${button.dataset.fastComplete}`);
      if (order.status !== "preparing" && order.status !== "accepted") {
        throw new Error("订单已被其他管理员修改，请刷新后重试。");
      }
      const dialog = openOrgDialog("完成订单", `
        <div class="batch-review">
          <dl class="status-list detail-list">
            <dt>单位</dt><dd>${html(order.unit_code || "--")} · ${html(order.unit_name_snapshot || "--")}</dd>
            <dt>订单</dt><dd>${html(order.order_no)}</dd>
            <dt>食材</dt><dd>${num(order.item_count || (order.items || []).length)} 种</dd>
          </dl>
          <p>完成后系统将结束本订单处理、自动生成并完成对应出库单；无需上传照片，子单位无需进行后续确认。</p>
          <div class="page-toolbar dialog-actions"><button id="cancelFastComplete" class="secondary-button" type="button">取消</button><button id="confirmFastComplete" class="primary-link" type="button">确认完成</button></div>
        </div>
      `);
      $("cancelFastComplete").addEventListener("click", dialog.close);
      $("confirmFastComplete").addEventListener("click", async () => {
        const confirmButton = $("confirmFastComplete");
        formButtonBusy(confirmButton, true, "确认完成");
        try {
          const result = await api(`/api/v1/admin/orders/${order.id}/complete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              expected_version: Number(order.version || 0),
              client_request_id: requestId("web-fast-complete"),
            }),
          });
          dialog.close();
          toast(`订单已完成，出库单 ${result.outbound?.outbound_no || ""} 已自动生成并完成`.trim());
          await loadCurrent(true);
        } catch (error) {
          if (error.status === 409) {
            const conflict = fastCompleteConflict(error);
            dialog.close();
            if (conflict.refresh) await loadCurrent(true, "conflict");
            toast(conflict.message);
            return;
          }
          toast(error.message || "完成订单失败，请刷新后重试");
          formButtonBusy(confirmButton, false, "确认完成");
        }
      });
    } catch (error) {
      toast(error.message || "无法加载订单明细，请刷新后重试");
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  }

  async function lifecycleOrder(button, action) {
    const labels = { cancel: "取消", void: "作废", archive: "归档", unarchive: "取消归档" };
    const label = labels[action] || "处理";
    let reason = "";
    if (action === "cancel" || action === "void") {
      reason = window.prompt(`请填写${label}原因`, action === "cancel" ? "重复提交" : "订单作废");
      if (reason === null) return;
      if (!reason.trim()) { toast(`请填写${label}原因`); return; }
    } else if (!confirm(`确认${label}这笔订单吗？`)) {
      return;
    }
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "提交中";
    try {
      const path = action === "archive" || action === "unarchive"
        ? `/api/v1/orders/${button.dataset.order}/${action}`
        : `/api/v1/admin/orders/${button.dataset.order}/${action}`;
      await api(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: (action === "cancel" || action === "void") ? JSON.stringify({ reason }) : undefined,
      });
      toast(`订单已${label}`);
      await loadCurrent(true);
    } catch (error) {
      toast(error.message || "操作失败，请刷新后重试");
      button.disabled = false;
      button.textContent = original;
    }
  }

  async function deleteOrder(button) {
    if (button.dataset.deleteBlocked) {
      toast(button.dataset.deleteBlocked);
      return;
    }
    if (!window.confirm("确认删除该订单吗？\n订单将从订单管理列表隐藏，历史采购台账和业务记录仍会保留。")) return;
    const reason = window.prompt("请填写删除原因：", "录入错误");
    if (reason === null) return;
    if (!reason.trim()) return toast("请填写删除原因");
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "删除中";
    try {
      await api(`/api/v1/admin/orders/${button.dataset.deleteOrder}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: reason.trim() }),
      });
      toast("订单已删除");
      if (currentRoute().startsWith("/admin/orders/")) window.location.assign("/admin/orders");
      else await loadCurrent(true);
    } catch (error) {
      if (error.status === 409) showNewDataBanner("该订单已被其他管理员修改，请刷新后重试。");
      toast(error.message || "删除失败，请刷新后重试");
      button.disabled = false;
      button.textContent = original;
    }
  }

  async function shipOrder(button, files, note = "") {
    const selected = Array.from(files || []).filter(Boolean);
    if (!selected.length) {
      toast("请先上传发货照片");
      return;
    }
    if (selected.length > 3) {
      toast("最多上传三张发货照片");
      return;
    }
    const label = button.textContent;
    if (!confirm("确认发货这笔订单吗？")) return;
    button.disabled = true;
    button.textContent = "提交中";
    state.mutationInFlight += 1;
    try {
      const form = new FormData();
      selected.forEach((file) => form.append("photos", file));
      form.append("note", note || "");
      form.append("client_request_id", requestId("web-ship"));
      const response = await fetch(`/api/v1/admin/orders/${button.dataset.order}/ship`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")) },
        body: form,
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
        throw new Error(detail || "发货失败，请刷新后重试");
      }
      toast("已确认发货");
      await loadCurrent(true);
    } catch (error) {
      toast(error.message || "发货失败，请刷新后重试");
      button.disabled = false;
      button.textContent = label;
    } finally {
      state.mutationInFlight = Math.max(0, state.mutationInFlight - 1);
    }
  }

  function chooseShipPhotos(button) {
    const input = document.createElement("input");
    input.type = "file";
    input.name = "photos";
    input.accept = "image/*";
    input.multiple = true;
    input.addEventListener("change", () => shipOrder(button, input.files));
    input.click();
  }

  function table(headers, rows, emptyText) {
    if (!rows.length) return empty(emptyText || "暂无数据");
    return `<div class="table-wrap"><table class="admin-table"><thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`;
  }

  function selectedOrders() {
    return window.AdminOrderSelectionPolicy.selectedOrders(state.orderItems, state.selectedOrderIds);
  }

  function renderOrderSelection() {
    const selected = selectedOrders();
    document.querySelectorAll(".select-all-orders").forEach((checkbox) => {
      const count = state.orderItems.length;
      checkbox.checked = count > 0 && selected.length === count;
      checkbox.indeterminate = selected.length > 0 && selected.length < count;
    });
    const toolbar = $("orderBatchToolbar");
    if (!toolbar) return;
    toolbar.hidden = selected.length === 0;
    $("orderSelectionCount").textContent = `已选择 ${selected.length} 个订单`;
    const allPending = window.AdminOrderSelectionPolicy.canBulkAccept(selected);
    const allDeletable = window.AdminOrderSelectionPolicy.canBulkDelete(selected);
    const acceptButton = $("bulkAcceptOrders");
    const deleteButton = $("bulkDeleteOrders");
    const batchButton = $("bulkCreateBatchOrders");
    acceptButton.disabled = state.orderBulkBusy || !allPending;
    deleteButton.disabled = state.orderBulkBusy || !allDeletable;
    const canCreateBatch = selected.length > 0 && selected.every((order) => ["accepted", "preparing"].includes(order.status));
    if (batchButton) batchButton.disabled = state.orderBulkBusy || !canCreateBatch;
    if (state.orderBulkBusy) {
      acceptButton.textContent = "处理中…";
      deleteButton.textContent = "处理中…";
    } else {
      acceptButton.textContent = "批量接单";
      deleteButton.textContent = "批量删除";
      if (batchButton) batchButton.textContent = "生成备货单";
    }
    document.querySelectorAll("[data-order-select], [data-order][data-status], [data-lifecycle], [data-delete-order], [data-ship]").forEach((control) => {
      control.disabled = state.orderBulkBusy;
    });
    const clearButton = $("clearOrderSelection");
    if (clearButton) clearButton.disabled = state.orderBulkBusy;
    const note = $("orderSelectionNote");
    note.textContent = selected.length && canCreateBatch
      ? "所选订单均可生成备货单。"
      : selected.length && !allDeletable
      ? "所选订单中包含履约中的订单，不可批量删除。请取消选择后重试。"
      : selected.length && !allPending
        ? "所选订单状态不一致，请仅选择待接单订单。"
        : "当前仅支持操作本页已选择的订单。";
  }

  function clearOrderSelection() {
    state.selectedOrderIds.clear();
    state.selectedOrderVersions.clear();
    renderOrderSelection();
    document.querySelectorAll("[data-order-select]").forEach((checkbox) => { checkbox.checked = false; });
  }

  async function bulkAcceptOrders() {
    const selected = selectedOrders();
    if (!selected.length || !selected.every((order) => order.status === "pending")) {
      toast("所选订单状态不一致，请仅选择待接单订单");
      return;
    }
    if (!window.confirm(`确认接单所选 ${selected.length} 笔订单吗？`)) return;
    state.orderBulkBusy = true;
    renderOrderSelection();
    let successCount = 0;
    const failures = [];
    for (const order of selected) {
      try {
        await api(`/api/v1/admin/orders/${order.id}/status`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "accepted", expected_status: order.status, expected_version: Number(order.version || 0) || undefined }),
        });
        successCount += 1;
      } catch (error) {
        failures.push(`${order.order_no}: ${error.message || "操作失败"}`);
      }
    }
    state.orderBulkBusy = false;
    state.selectedOrderIds.clear();
    state.selectedOrderVersions.clear();
    toast(failures.length ? `成功接单 ${successCount} 笔，失败 ${failures.length} 笔：${failures[0]}` : `已成功接单 ${successCount} 笔订单`);
    await loadCurrent(true);
  }

  async function bulkCreatePreparationOrder() {
    const selected = selectedOrders();
    if (!selected.length || !selected.every((order) => ["accepted", "preparing"].includes(order.status))) {
      toast("所选订单中包含不能生成备货单的订单，请仅选择已接单且未进入备货流程的订单");
      return;
    }
    if (!window.confirm(`确认用所选 ${selected.length} 笔订单生成备货单吗？`)) return;
    state.orderBulkBusy = true;
    renderOrderSelection();
    try {
      const params = new URLSearchParams();
      selected.forEach((order) => params.append("preselect", order.id));
      window.location.assign(`/admin/batches?${params.toString()}`);
    } finally {
      state.orderBulkBusy = false;
    }
  }

  async function bulkDeleteOrders() {
    const selected = selectedOrders();
    if (!selected.length || !selected.every((order) => order.can_delete)) {
      toast("所选订单中包含正在履约的订单，请取消选择后重试");
      return;
    }
    if (!window.confirm(`确定删除所选 ${selected.length} 个订单吗？\n订单将从订单管理列表移除，历史采购台账和业务记录仍会保留。`)) return;
    const reason = window.prompt("请填写删除原因：", "批量清理已完成订单");
    if (reason === null) return;
    if (!reason.trim()) { toast("请填写删除原因"); return; }
    state.orderBulkBusy = true;
    renderOrderSelection();
    let successCount = 0;
    const failures = [];
    for (const order of selected) {
      try {
        await api(`/api/v1/admin/orders/${order.id}`, {
          method: "DELETE",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ reason: reason.trim() }),
        });
        successCount += 1;
      } catch (error) {
        failures.push(`${order.order_no}: ${error.message || "操作失败"}`);
      }
    }
    state.orderBulkBusy = false;
    state.selectedOrderIds.clear();
    state.selectedOrderVersions.clear();
    toast(failures.length ? `成功删除 ${successCount} 笔，失败 ${failures.length} 笔：${failures[0]}` : `已删除 ${successCount} 笔订单`);
    await loadCurrent(true);
  }

  function orderListQuery() {
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    if (params.get("status")) query.set("status", params.get("status"));
    if (params.get("unit_id")) query.set("unit_id", params.get("unit_id"));
    if (params.get("date_from")) query.set("date_from", params.get("date_from"));
    if (params.get("date_to")) query.set("date_to", params.get("date_to"));
    if (params.get("query")) query.set("query", params.get("query"));
    if (params.get("archived") === "true") query.set("archived", "true");
    if (params.get("cursor")) query.set("cursor", params.get("cursor"));
    return query;
  }

  function retainOrderFiltersInLocation() {
    if (currentRoute() !== "/admin/orders") return;
    const current = new URLSearchParams(window.location.search);
    const next = new URLSearchParams();
    const text = $("orderQueryInput")?.value.trim() || "";
    const status = $("orderStatusSelect")?.value || "";
    const dateFrom = $("orderDateFrom")?.value || "";
    const dateTo = $("orderDateTo")?.value || "";
    if (text) next.set("query", text);
    if (status) next.set("status", status);
    if (dateFrom) next.set("date_from", dateFrom);
    if (dateTo) next.set("date_to", dateTo);
    if (current.get("unit_id")) next.set("unit_id", current.get("unit_id"));
    if (current.get("archived") === "true") next.set("archived", "true");
    window.history.replaceState({}, "", `/admin/orders${next.toString() ? `?${next.toString()}` : ""}`);
  }

  function orderRow(order) {
    const action = primaryAction(order);
    const button = action[1] === "fast_complete"
      ? `<button class="table-action primary" data-fast-complete="${order.id}" type="button">${action[0]}</button>`
      : action[1] === "ship"
      ? `<button class="table-action primary" data-ship="${order.id}" data-order="${order.id}">${action[0]}</button>`
      : action[1] ? `<button class="table-action primary" data-order="${order.id}" data-status="${action[1]}" data-current-status="${order.status}" data-version="${order.version || 1}">${action[0]}</button>` : `<a class="table-action" href="/admin/orders/${order.id}">查看</a>`;
    const lifecycle = order.can_cancel ? `<button class="table-action" data-lifecycle="cancel" data-order="${order.id}">取消</button>`
      : order.can_void ? `<button class="table-action" data-lifecycle="void" data-order="${order.id}">作废</button>`
      : order.can_archive ? `<button class="table-action" data-lifecycle="archive" data-order="${order.id}">归档</button>`
      : order.can_unarchive ? `<button class="table-action" data-lifecycle="unarchive" data-order="${order.id}">取消归档</button>` : "";
    const deleteButton = order.can_delete
      ? `<button class="table-action danger" data-delete-order="${order.id}">删除</button>`
      : `<button class="table-action danger" type="button" data-delete-order="${order.id}" data-delete-blocked="${html(order.delete_reason || "当前状态不能删除")}">删除</button>`;
    return `<tr data-order-id="${html(order.id)}" data-order-version="${html(order.version || 1)}"><td><input class="order-row-check" type="checkbox" data-order-select="${order.id}" aria-label="选择订单 ${html(order.order_no)}" ${state.selectedOrderIds.has(order.id) ? "checked" : ""} /></td><td>${html(order.order_no)}</td><td>${html(order.unit_code || "--")}</td><td>${html(order.unit_name_snapshot || order.unit_name || "--")}</td><td>${dateTime(order.created_at)}</td><td>${money(order.total_cents)}</td><td>${statusTag(order.status)}</td><td><a class="table-action" href="/admin/orders/${order.id}">查看明细</a> ${button} ${lifecycle} ${deleteButton}</td></tr>`;
  }

  function orderMonthTable(month, rows) {
    const headers = [`<input class="select-all-orders" type="checkbox" aria-label="全选当前页订单" />`, "订单编号", "单位编码", "单位", "下单时间", "金额", "状态", "操作"];
    return table(headers, rows, "暂无订单").replace("<tbody>", `<tbody data-order-month-rows="${html(month)}">`);
  }

  function orderMonthGroup(month, rows, open = false) {
    return `<details class="order-month-group" data-order-month="${html(month)}" ${open ? "open" : ""}><summary><span>${html(orderMonthLabel(month))}</span><small data-order-month-count>${rows.length} 笔订单</small></summary>${orderMonthTable(month, rows)}</details>`;
  }

  function bindOrderListEvents(target) {
    if (!target || target.dataset.orderEventsBound === "1") return;
    target.dataset.orderEventsBound = "1";
    target.addEventListener("change", (event) => {
      const rowCheckbox = event.target.closest?.("[data-order-select]");
      if (rowCheckbox) {
        if (rowCheckbox.checked) {
          state.selectedOrderIds.add(rowCheckbox.dataset.orderSelect);
          const order = state.orderItems.find((item) => item.id === rowCheckbox.dataset.orderSelect);
          state.selectedOrderVersions.set(rowCheckbox.dataset.orderSelect, Number(order?.version || 1));
        } else {
          state.selectedOrderIds.delete(rowCheckbox.dataset.orderSelect);
          state.selectedOrderVersions.delete(rowCheckbox.dataset.orderSelect);
        }
        renderOrderSelection();
        return;
      }
      const selectAll = event.target.closest?.(".select-all-orders");
      if (!selectAll) return;
      state.selectedOrderIds = window.AdminOrderSelectionPolicy.nextSelection(state.orderItems, state.selectedOrderIds, selectAll.checked);
      state.selectedOrderVersions = new Map(selectAll.checked
        ? state.orderItems.map((order) => [order.id, Number(order.version || 1)])
        : []);
      target.querySelectorAll("[data-order-select]").forEach((checkbox) => { checkbox.checked = selectAll.checked; });
      renderOrderSelection();
    });
    target.addEventListener("click", (event) => {
      const statusButton = event.target.closest?.("[data-order][data-status]");
      if (statusButton) return updateOrderStatus(statusButton);
      const shipButton = event.target.closest?.("[data-ship]");
      if (shipButton) return chooseShipPhotos(shipButton);
      const lifecycleButton = event.target.closest?.("[data-lifecycle]");
      if (lifecycleButton) return lifecycleOrder(lifecycleButton, lifecycleButton.dataset.lifecycle);
    });
  }

  function patchOrdersRealtime(data) {
    const items = data.items || [];
    const list = document.querySelector(".order-month-list");
    if (!list) return loadOrders();
    const reconciliation = window.AdminOrderSelectionPolicy.reconcileSelection(items, state.selectedOrderIds, state.selectedOrderVersions);
    state.selectedOrderIds = reconciliation.selectedIds;
    state.selectedOrderVersions = reconciliation.selectedVersions;
    state.orderItems = items;
    const wanted = new Set(items.map((item) => item.id));
    list.querySelectorAll("tr[data-order-id]").forEach((row) => {
      if (!wanted.has(row.dataset.orderId)) row.remove();
    });
    const byMonth = new Map();
    items.forEach((item) => {
      const month = orderMonthKey(item);
      if (!byMonth.has(month)) byMonth.set(month, []);
      byMonth.get(month).push(item);
    });
    byMonth.forEach((orders, month) => {
      let group = list.querySelector(`[data-order-month="${CSS.escape(month)}"]`);
      if (!group) {
        const wrapper = document.createElement("div");
        wrapper.innerHTML = orderMonthGroup(month, [], list.children.length === 0);
        group = wrapper.firstElementChild;
        list.append(group);
      }
      const body = group.querySelector(`[data-order-month-rows="${CSS.escape(month)}"]`);
      orders.forEach((order, index) => {
        const current = list.querySelector(`tr[data-order-id="${CSS.escape(order.id)}"]`);
        let row = current;
        if (!row || row.dataset.orderVersion !== String(order.version || 1)) {
          const wrapper = document.createElement("tbody");
          wrapper.innerHTML = orderRow(order);
          const replacement = wrapper.firstElementChild;
          if (row) row.replaceWith(replacement);
          else row = replacement;
          row = replacement;
        }
        const next = orders.slice(index + 1).map((item) => body.querySelector(`tr[data-order-id="${CSS.escape(item.id)}"]`)).find(Boolean);
        if (next) body.insertBefore(row, next);
        else body.append(row);
      });
      const count = group.querySelector("[data-order-month-count]");
      if (count) count.textContent = `${orders.length} 笔订单`;
    });
    list.querySelectorAll("[data-order-month]").forEach((group) => {
      if (!group.querySelector("tr[data-order-id]")) group.remove();
    });
    const summary = document.querySelector(".order-result-summary");
    if (summary) summary.textContent = `共 ${num(data.total || items.length)} 笔，本页显示 ${num(items.length)} 笔`;
    renderOrderSelection();
    state.routeFingerprint = window.AdminRefreshPolicy.orderFingerprint(data);
    setSyncStatus(`已同步 · ${formatSyncTime()}`);
  }

  async function loadOrders() {
    const params = new URLSearchParams(window.location.search);
    const query = orderListQuery();
    state.orderItems = [];
    state.orderBulkBusy = false;
    pageShell("订单管理", "接单并完成订单；备货单仅作采购和分拣辅助");
    content().innerHTML += `
      <div class="page-toolbar">
        <input id="orderQueryInput" type="search" value="${html(params.get("query") || "")}" placeholder="搜索订单编号或单位" />
        <input id="orderDateFrom" type="date" value="${html(params.get("date_from") || "")}" aria-label="开始日期" />
        <input id="orderDateTo" type="date" value="${html(params.get("date_to") || "")}" aria-label="结束日期" />
        <select id="orderStatusSelect">
          <option value="">全部状态</option>
          ${["pending", "accepted", "preparing", "shipped", "completed", "cancelled", "voided"].map((status) => `<option value="${status}" ${params.get("status") === status ? "selected" : ""}>${statusText[status]}</option>`).join("")}
        </select>
        <button id="orderFilterButton" class="table-action primary" type="button">查询</button>
        <button id="orderClearFilterButton" class="table-action" type="button">清除筛选</button>
        <button id="orderArchiveToggle" class="table-action" type="button">${params.get("archived") === "true" ? "查看当前订单" : "查看归档订单"}</button>
      </div>`;
    const data = await api(`/api/v1/admin/orders?limit=30&${query.toString()}`);
    state.routeFingerprint = window.AdminRefreshPolicy.orderFingerprint(data);
    const items = data.items || data || [];
    state.orderItems = items;
    const reconciliation = window.AdminOrderSelectionPolicy.reconcileSelection(items, state.selectedOrderIds, state.selectedOrderVersions);
    state.selectedOrderIds = reconciliation.selectedIds;
    state.selectedOrderVersions = reconciliation.selectedVersions;
    if (reconciliation.removed) toast(`${reconciliation.removed} 条需求单状态已变化，已从选择中移除。`);
    const groups = new Map();
    items.forEach((order) => {
      const month = orderMonthKey(order);
      if (!groups.has(month)) groups.set(month, []);
      groups.get(month).push(orderRow(order));
    });
    content().innerHTML += items.length ? `
      <div id="orderBatchToolbar" class="order-bulk-toolbar" hidden>
        <div><strong id="orderSelectionCount">已选择 0 个订单</strong><span id="orderSelectionNote">当前仅支持操作本页已选择的订单。</span></div>
        <div class="page-toolbar"><button id="bulkAcceptOrders" class="table-action primary" type="button">批量接单</button><button id="bulkCreateBatchOrders" class="table-action primary" type="button">生成备货单</button><button id="bulkDeleteOrders" class="table-action danger" type="button">批量删除</button><button id="clearOrderSelection" class="table-action" type="button">取消选择</button></div>
      </div>
      <div class="order-result-summary">共 ${num(data.total || items.length)} 笔，本页显示 ${num(items.length)} 笔</div>
      <div class="order-month-list">
        ${Array.from(groups.entries()).map(([month, rows], index) => orderMonthGroup(month, rows, index === 0)).join("")}
      </div>` : empty("没有符合条件的订单");
    bindOrderListEvents(document.querySelector(".order-month-list"));
    $("clearOrderSelection")?.addEventListener("click", clearOrderSelection);
    $("bulkAcceptOrders")?.addEventListener("click", bulkAcceptOrders);
    $("bulkCreateBatchOrders")?.addEventListener("click", bulkCreatePreparationOrder);
    $("bulkDeleteOrders")?.addEventListener("click", bulkDeleteOrders);
    renderOrderSelection();
    if (data.has_more && data.next_cursor) {
      content().innerHTML += `<div class="page-toolbar"><button id="nextOrderPage" class="table-action" type="button">下一页</button></div>`;
      $("nextOrderPage").addEventListener("click", () => {
        const next = new URLSearchParams(window.location.search);
        next.set("cursor", data.next_cursor);
        window.location.assign(`/admin/orders?${next.toString()}`);
      });
    }
    $("orderFilterButton").addEventListener("click", () => {
      const next = new URLSearchParams();
      const text = $("orderQueryInput").value.trim();
      const status = $("orderStatusSelect").value;
      const dateFrom = $("orderDateFrom").value;
      const dateTo = $("orderDateTo").value;
      if (text) next.set("query", text);
      if (status) next.set("status", status);
      if (dateFrom) next.set("date_from", dateFrom);
      if (dateTo) next.set("date_to", dateTo);
      if (params.get("unit_id")) next.set("unit_id", params.get("unit_id"));
      if (params.get("archived") === "true") next.set("archived", "true");
      window.location.assign(`/admin/orders?${next.toString()}`);
    });
    $("orderClearFilterButton").addEventListener("click", () => {
      const next = new URLSearchParams();
      if (params.get("archived") === "true") next.set("archived", "true");
      window.location.assign(`/admin/orders${next.toString() ? `?${next.toString()}` : ""}`);
    });
    $("orderArchiveToggle").addEventListener("click", () => {
      const next = new URLSearchParams(window.location.search);
      next.delete("cursor");
      if (next.get("archived") === "true") next.delete("archived"); else next.set("archived", "true");
      window.location.assign(`/admin/orders?${next.toString()}`);
    });
  }

  async function loadOrderDetail(orderId) {
    pageShell("订单详情", "订单状态和食材明细");
    const order = await api(`/api/v1/admin/orders/${orderId}`);
    const action = primaryAction(order);
    const lifecycle = order.can_cancel ? `<button class="secondary-button" data-lifecycle="cancel" data-order="${order.id}">取消订单</button>`
      : order.can_void ? `<button class="secondary-button" data-lifecycle="void" data-order="${order.id}">作废订单</button>`
      : order.can_archive ? `<button class="secondary-button" data-lifecycle="archive" data-order="${order.id}">归档订单</button>`
      : order.can_unarchive ? `<button class="secondary-button" data-lifecycle="unarchive" data-order="${order.id}">取消归档</button>` : "";
    const deleteButton = order.can_delete
      ? `<button class="danger-button" data-delete-order="${order.id}">删除订单</button>`
      : `<button class="danger-button" type="button" data-delete-order="${order.id}" data-delete-blocked="${html(order.delete_reason || "当前状态不能删除")}">删除订单</button>`;
    content().innerHTML += `
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>${html(order.order_no)}</h2><p>${html(order.unit_code || "--")} · ${html(order.unit_name_snapshot || "--")} · ${dateTime(order.created_at)}</p></div><div>${statusTag(order.status)}</div></div>
        <dl class="status-list detail-list">
          <dt>单位编码</dt><dd>${html(order.unit_code || "--")}</dd>
          <dt>配送点</dt><dd>${html(order.delivery_point || "--")}</dd>
          <dt>备注</dt><dd>${html(order.remark || "无")}</dd>
          <dt>订单金额</dt><dd>${money(order.total_cents)}</dd>
        </dl>
        <div class="page-toolbar">${action[1] === "fast_complete" ? `<button class="primary-link" data-fast-complete="${order.id}" type="button">完成</button>` : action[1] && action[1] !== "ship" ? `<button class="primary-link" data-order="${order.id}" data-status="${action[1]}" data-current-status="${order.status}" data-version="${order.version || 1}">${action[0]}</button>` : ""}${lifecycle}${deleteButton}</div>
      </article>
    `;
    if ((order.shipping_photos || []).length) {
      content().innerHTML += `
        <article class="panel section-panel">
          <div class="panel-header"><div><h2>发货照片</h2><p>${html(order.shipping_note || "无备注")}</p></div></div>
          <div class="photo-grid">${order.shipping_photos.map((photo) => `<a href="${photo.full_url}" target="_blank" rel="noreferrer"><img src="${photo.thumbnail_url}" alt="发货照片" /></a>`).join("")}</div>
        </article>
      `;
    }
    content().innerHTML += table(["食材", "规格", "数量", "单价", "小计"], (order.items || []).map((item) => `
      <tr><td>${html(item.product_name_snapshot || item.product_name)}</td><td>${html(item.spec_snapshot || item.spec || "--")}</td><td>${qty(item.quantity)}</td><td>${money(item.price_cents_snapshot)}</td><td>${money(item.subtotal_cents)}</td></tr>
    `), "暂无食材明细");
    document.querySelectorAll("[data-order][data-status]").forEach((button) => button.addEventListener("click", () => updateOrderStatus(button)));
    document.querySelectorAll("[data-lifecycle]").forEach((button) => button.addEventListener("click", () => lifecycleOrder(button, button.dataset.lifecycle)));
  }

  async function loadProducts() {
    const params = new URLSearchParams(window.location.search);
    pageShell("食材列表", "价格、库存和供应状态");
    const products = await api("/api/v1/admin/products");
    let rows = products;
    if (params.get("status") === "tight") {
      rows = products.filter((item) => item.supply_status === "tight" || Number(item.available_quantity || 0) <= Number(item.warning_quantity || 0));
    }
    const visibleIds = new Set(rows.map((item) => item.id));
    state.selectedProductIds = new Set(Array.from(state.selectedProductIds).filter((id) => visibleIds.has(id)));
    content().innerHTML += `
      <div class="page-toolbar">
        <button class="primary-link" data-create-product type="button">添加食材</button>
        <button class="secondary-button" id="exportProductMenu" type="button">导出商品菜单</button>
        <a class="secondary-button as-link" href="/api/v1/admin/products/import-template.xlsx">下载标准模板</a>
        <a class="secondary-button as-link" href="/admin/price-imports">上传 Excel</a>
        <button class="secondary-button" id="productSelectionToggle" type="button">${state.productSelectionMode ? "退出批量管理" : "批量管理"}</button>
        ${state.productSelectionMode ? `<button class="danger-button" id="deleteSelectedProducts" type="button" ${state.selectedProductIds.size ? "" : "disabled"}>删除已选（${state.selectedProductIds.size}）</button>` : ""}
        <button class="danger-button" id="clearProducts" type="button" ${products.length ? "" : "disabled"}>清空食材</button>
      </div>
      <p class="muted">已进入历史订单的食材只会从当前目录归档，历史订单、台账和审计记录仍会保留。</p>
      ${productFormTemplate()}
    `;
    const productHeaders = state.productSelectionMode ? ["选择", "食材", "分类", "规格", "单价", "总库存", "预占", "可用", "状态", "操作"] : ["食材", "分类", "规格", "单价", "总库存", "预占", "可用", "状态", "操作"];
    content().innerHTML += table(productHeaders, rows.map((item) => `
      <tr>${state.productSelectionMode ? `<td><input class="row-check" type="checkbox" data-product-select="${item.id}" ${state.selectedProductIds.has(item.id) ? "checked" : ""} aria-label="选择${html(item.name)}" /></td>` : ""}<td>${html(item.name)}</td><td>${html(item.category || "--")}</td><td>${html(item.spec || "--")}</td><td>${money(item.price_cents)}</td><td>${qty(item.stock_quantity)} ${html(item.unit)}</td><td>${qty(item.reserved_quantity)}</td><td>${qty(item.available_quantity)}</td><td>${supplyTag(item.supply_status, item.active)}</td><td><button class="table-action" data-price="${item.id}" data-current="${item.price_cents}">改价</button><button class="table-action" data-stock="${item.id}" data-current="${item.stock_quantity}">调库存</button></td></tr>
    `), "暂无食材");
    $("exportProductMenu").addEventListener("click", () => downloadProductMenu($("exportProductMenu")));
    const form = $("productCreateForm");
    document.querySelectorAll("[data-create-product]").forEach((button) => button.addEventListener("click", () => {
      state.productFormOpen = true;
      loadProducts();
    }));
    document.querySelectorAll("[data-cancel-create-product]").forEach((button) => button.addEventListener("click", () => {
      state.productFormOpen = false;
      loadProducts();
    }));
    $("productSelectionToggle").addEventListener("click", () => {
      state.productSelectionMode = !state.productSelectionMode;
      if (!state.productSelectionMode) state.selectedProductIds.clear();
      loadProducts();
    });
    document.querySelectorAll("[data-product-select]").forEach((checkbox) => checkbox.addEventListener("change", () => {
      if (checkbox.checked) state.selectedProductIds.add(checkbox.dataset.productSelect);
      else state.selectedProductIds.delete(checkbox.dataset.productSelect);
      const button = $("deleteSelectedProducts");
      if (button) {
        button.disabled = state.selectedProductIds.size === 0;
        button.textContent = `删除已选（${state.selectedProductIds.size}）`;
      }
    }));
    const deleteSelected = $("deleteSelectedProducts");
    if (deleteSelected) deleteSelected.addEventListener("click", async () => {
      const ids = Array.from(state.selectedProductIds);
      if (!ids.length || !confirm(`确认删除已选的 ${ids.length} 项食材吗？\n历史业务记录仍会保留。`)) return;
      deleteSelected.disabled = true;
      deleteSelected.textContent = "删除中";
      try {
        await api("/api/v1/admin/products/batch", { method: "DELETE", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids, confirmed: true }) });
        state.selectedProductIds.clear();
        toast("已删除所选食材");
        await loadProducts();
      } catch (error) {
        toast(error.message || "批量删除失败");
        deleteSelected.disabled = false;
      }
    });
    $("clearProducts").addEventListener("click", async () => {
      const confirmationText = window.prompt(`即将从当前目录移除 ${products.length} 项食材。请输入“确认删除”继续。`, "");
      if (confirmationText === null) return;
      if (confirmationText.trim() !== "确认删除") return toast("确认文字不正确，操作已取消");
      const button = $("clearProducts");
      button.disabled = true;
      button.textContent = "删除中";
      try {
        await api("/api/v1/admin/products/all", { method: "DELETE", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ confirmed: true, confirmation_text: confirmationText.trim(), expected_count: products.length }) });
        state.selectedProductIds.clear();
        toast("食材目录已清空");
        await loadProducts();
      } catch (error) {
        toast(error.message || "清空食材失败");
        button.disabled = false;
        button.textContent = "清空食材";
      }
    });
    if (form) {
      bindProductOptionButtons(form);
      form.querySelectorAll("[data-save-product]").forEach((button) => {
        button.addEventListener("click", () => {
          form.dataset.submitMode = button.dataset.saveProduct || "close";
        });
      });
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const submitter = event.submitter;
        const mode = submitter?.dataset?.saveProduct || form.dataset.submitMode || "close";
        const activeButton = submitter || form.querySelector(`[data-save-product="${mode}"]`);
        const payload = productPayload(form);
        if (!payload.name) return toast("请填写食材名称");
        if (!Number.isFinite(payload.price_cents) || payload.price_cents < 0) return toast("单价不正确");
        if (payload.active && ["normal", "tight"].includes(payload.supply_status) && payload.price_cents <= 0) return toast("正常供应并上架时，请先填写单价");
        if (activeButton) {
          activeButton.disabled = true;
          activeButton.textContent = "保存中";
        }
        try {
          await api("/api/v1/admin/products", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
          });
          toast("食材已保存");
          rememberProductDefaults(form);
          state.productFormOpen = mode === "continue";
          await loadProducts();
        } catch (error) {
          toast(error.message || "保存失败，请稍后重试");
          if (activeButton) {
            activeButton.disabled = false;
            activeButton.textContent = mode === "continue" ? "保存并继续添加" : "保存食材";
          }
        }
      });
    }
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

  async function downloadProductMenu(button) {
    if (button.dataset.exporting === "1") return;
    button.dataset.exporting = "1";
    button.disabled = true;
    button.textContent = "正在导出...";
    try {
      const response = await fetch("/api/v1/admin/products/export.xlsx", {
        credentials: "same-origin",
        headers: { "Accept": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
      });
      if (response.status === 401) {
        window.location.replace("/login?expired=1");
        throw Object.assign(new Error("登录已过期，请重新登录"), { status: 401 });
      }
      if (!response.ok) {
        let detail = "";
        try { detail = (await response.json()).detail || ""; } catch (_) {}
        throw new Error(window.AdminRefreshPolicy.apiErrorMessage(response.status, detail));
      }
      const link = document.createElement("a");
      link.href = URL.createObjectURL(await response.blob());
      link.download = decodeURIComponent(response.headers.get("Content-Disposition")?.split("filename*=UTF-8''")[1] || "三公鲜配商品菜单.xlsx");
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(link.href);
    } catch (error) {
      toast(error.message || "导出商品菜单失败，请稍后重试");
    } finally {
      delete button.dataset.exporting;
      button.disabled = false;
      button.textContent = "导出商品菜单";
    }
  }

  function priceImportStatus(status) {
    return ({ UPLOADED: "待分析", ANALYZING: "分析中", READY_FOR_REVIEW: "待审核", APPLYING: "正在应用", APPLIED: "已应用", FAILED: "分析失败", CANCELLED: "已取消" })[status] || display(status, "未知");
  }

  function priceImportRowStatus(row) {
    if (row.validation_status === "IGNORED") return "本次忽略";
    if (row.validation_status === "READY" && row.operation_type === "NEW_PRODUCT") return "待新增";
    if (row.validation_status === "READY" && row.operation_type === "EXISTING_PRODUCT") return "待同步";
    if (row.validation_status === "NEEDS_REVIEW") return "需要确认";
    return ({ NEEDS_PRODUCT_SELECTION: "需要确认", UNMATCHED: "需要确认", INVALID: "数据异常", DUPLICATE_CONFLICT: "数据异常", PRICE_CONFLICT: "需要重新确认" })[row.validation_status] || display(row.validation_status, "未知");
  }

  function priceImportChange(oldCents, newCents) {
    if (oldCents == null || newCents == null) return "--";
    if (Number(oldCents) === 0) return "新增价格";
    const rate = ((Number(newCents) - Number(oldCents)) / Number(oldCents)) * 100;
    return `${rate > 0 ? "+" : ""}${rate.toFixed(1)}%`;
  }

  function priceImportLinkLabel(row) {
    if (row.match_method === "exact_code" || row.match_method === "exact_name") return "已自动关联";
    if (row.match_method === "manual_selection" || row.match_method === "manual") return "已选择";
    return "";
  }

  function priceImportProductLabel(product) {
    return `${product.name} · ${product.unit} · 当前 ${money(product.price_cents)}${product.product_code ? ` · ${product.product_code}` : ""}`;
  }

  function priceImportMapping(raw) {
    try { return JSON.parse(raw || "{}"); } catch (_) { return {}; }
  }

  function priceImportActivityPanel(state, title, detail, active = true) {
    return `<section class="thinking-orb-status-panel${active ? " is-active" : ""}" aria-live="polite"><div class="thinking-orb-host" data-thinking-orb="${html(state)}" data-thinking-orb-active="${active ? "1" : "0"}"></div><div><h2>${html(title)}</h2><p>${html(detail)}</p></div></section>`;
  }

  function mountThinkingOrbs(root = document) {
    if (!window.SangongThinkingOrb) return;
    root.querySelectorAll("[data-thinking-orb]").forEach((host) => {
      window.SangongThinkingOrb.mount(host, {
        state: host.dataset.thinkingOrb || "working",
        active: host.dataset.thinkingOrbActive !== "0"
      });
    });
  }

  function setPriceImportMotion(state, active = true) {
    const host = document.querySelector("[data-thinking-orb]");
    if (!host || !window.SangongThinkingOrb) return;
    host.dataset.thinkingOrb = state;
    host.dataset.thinkingOrbActive = active ? "1" : "0";
    host.closest(".thinking-orb-status-panel")?.classList.toggle("is-active", active);
    window.SangongThinkingOrb.mount(host, { state, active });
  }

  // The Canvas engine is an ES module. If the dashboard renders before the
  // module finishes loading, mount the current lifecycle state once it is ready.
  window.addEventListener("sangong-thinking-orb-ready", () => mountThinkingOrbs(content()));

  async function loadPriceImportHistory() {
    const result = await api("/api/v1/admin/price-imports");
    return result.items || [];
  }

  async function uploadPriceImport(file, button) {
    if (!file) return toast("请选择供应商报价 Excel");
    const form = new FormData();
    form.append("file", file);
    button.disabled = true;
    button.textContent = "正在上传";
    setPriceImportMotion("solving", true);
    try {
      const batch = await api("/api/v1/admin/price-imports", { method: "POST", body: form });
      button.textContent = "正在分析";
      await api(`/api/v1/admin/price-imports/${batch.id}/analyze`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
      window.history.pushState({}, "", `/admin/price-imports/${batch.id}`);
      await loadPriceImportDetail(batch.id);
    } catch (error) {
      toast(error.message || "报价单识别失败，请检查文件或手动选择对应列");
      button.disabled = false;
      button.textContent = "开始分析";
      await loadPriceImports();
    }
  }

  async function loadPriceImports() {
    pageShell("Excel 智能导入与价格同步", "上传供应商报价，批量建立新食材并同步已有食材价格");
    const items = await loadPriceImportHistory();
    content().innerHTML += `
      ${priceImportActivityPanel("searching", "智能导入已就绪", "选择报价表后将自动识别字段；应用前仍需由管理员确认。")}
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>上传供应商报价 Excel</h2><p>支持 .xlsx、.xls、.csv，最大 10 MB。首次可批量建立食材目录，之后会同步已有食材价格；应用前始终需要确认。</p></div></div>
        <div class="form-grid"><label class="form-field span-2"><span>报价文件</span><input id="priceImportFile" type="file" accept=".xlsx,.xls,.csv" /></label></div>
        <div class="page-toolbar"><button class="primary-link" id="startPriceImport" type="button">开始分析</button></div>
      </article>
      <article class="panel table-panel"><div class="panel-header"><div><h2>导入记录</h2><p>可查看历史导入批次和应用结果</p></div></div>
      ${table(["上传时间", "文件", "状态", "操作"], items.map((item) => `<tr><td>${dateTime(item.created_at)}</td><td>${html(item.source_filename)}</td><td>${html(priceImportStatus(item.status))}</td><td><a class="table-action" href="/admin/price-imports/${item.id}">查看</a></td></tr>`), "暂无导入记录")}</article>`;
    mountThinkingOrbs(content());
    $("startPriceImport").addEventListener("click", () => uploadPriceImport($("priceImportFile").files[0], $("startPriceImport")));
  }

  function newProductEditor(row) {
    const categoryOptions = productCategories.map((item) => `<option value="${html(item)}" ${item === row.proposed_category ? "selected" : ""}>${html(item)}</option>`).join("");
    const unitOptions = productUnits.map((item) => `<option value="${html(item)}" ${item === row.proposed_unit ? "selected" : ""}>${html(item)}</option>`).join("");
    const statusOptions = productSupplyStatuses.map(([value, label]) => `<option value="${value}" ${value === row.proposed_supply_status ? "selected" : ""}>${label}</option>`).join("");
    return `<details class="mapping-details"><summary>修改本项资料</summary><div class="form-grid compact-form">
      <label class="form-field"><span>食材编码</span><input data-new-code="${row.id}" value="${html(row.proposed_product_code || "")}" /></label>
      <label class="form-field"><span>分类</span><select data-new-category="${row.id}">${categoryOptions}</select></label>
      <label class="form-field"><span>规格</span><input data-new-spec="${row.id}" value="${html(row.proposed_spec || "散装")}" /></label>
      <label class="form-field"><span>单位</span><select data-new-unit="${row.id}">${unitOptions}</select></label>
      <label class="form-field"><span>初始库存</span><input data-new-stock="${row.id}" inputmode="decimal" value="${html(row.proposed_stock_quantity || "0")}" /></label>
      <label class="form-field"><span>供应状态</span><select data-new-status="${row.id}">${statusOptions}</select></label>
    </div><button class="table-action" type="button" data-price-save-new="${row.id}">保存本项</button></details>`;
  }

  function reviewRows(batch, filter = "ALL") {
    const rows = (batch.rows || []).filter((row) => {
      if (filter === "EXISTING") return row.operation_type === "EXISTING_PRODUCT";
      if (filter === "NEW") return row.operation_type === "NEW_PRODUCT";
      if (filter === "NEEDS_REVIEW") return row.operation_type === "NEEDS_REVIEW" || row.validation_status === "NEEDS_REVIEW";
      if (filter === "EXCEPTION") return ["INVALID", "DUPLICATE_CONFLICT", "PRICE_CONFLICT"].includes(row.validation_status);
      return true;
    });
    return rows.map((row) => `<tr>
      <td>${html(row.source_product_name || "--")}${row.source_spec ? `<br><small>${html(row.source_spec)}</small>` : ""}</td>
      <td data-price-product-cell="${row.id}">${row.operation_type === "NEW_PRODUCT" ? `新增食材${newProductEditor(row)}` : row.matched_product_name ? `${html(row.matched_product_name)}<br><small>${priceImportLinkLabel(row)}${row.system_unit ? ` · ${html(row.system_unit)}` : ""}</small><br><button class="table-action" data-price-change="${row.id}">更换</button>` : `<input class="price-product-picker" list="priceImportProducts" data-price-product-input="${row.id}" placeholder="搜索并选择食材" /><button class="table-action" data-price-select="${row.id}">确认</button>`}</td>
      <td>${html(row.operation_type === "NEW_PRODUCT" ? (row.proposed_spec || "--") : (row.source_spec || "--"))}</td>
      <td>${html(row.operation_type === "NEW_PRODUCT" ? (row.proposed_unit || "--") : (row.system_unit || row.normalized_unit || "--"))}</td>
      <td>${row.operation_type === "NEW_PRODUCT" || row.current_price_cents == null ? "--" : money(row.current_price_cents)}</td>
      <td>${row.proposed_price_cents == null ? "--" : money(row.proposed_price_cents)}${row.conversion_factor && row.conversion_factor !== "1" ? "<br><small>已换算</small>" : ""}</td>
      <td>${row.operation_type === "NEW_PRODUCT" ? html(row.proposed_stock_quantity || "0") : "--"}</td>
      <td>${row.operation_type === "NEW_PRODUCT" ? html(supplyText[row.proposed_supply_status] || "--") : "--"}</td>
      <td>${html(priceImportRowStatus(row))}${row.warning ? `<br><small>${html(row.warning)}</small>` : ""}${!["READY", "IGNORED"].includes(row.validation_status) ? `<br><button class="table-action" data-price-ignore="${row.id}">忽略本项</button>` : ""}</td></tr>`);
  }

  async function selectPriceImportProduct(batch, rowId, products) {
    const input = document.querySelector(`[data-price-product-input="${rowId}"]`);
    const selected = products.find((item) => priceImportProductLabel(item) === input?.value.trim());
    if (!selected) return toast("请从搜索结果中选择系统食材");
    await api(`/api/v1/admin/price-imports/${batch.id}/rows/${rowId}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ matched_product_id: selected.id }) });
    await loadPriceImportDetail(batch.id);
  }

  function enablePriceImportProductChange(rowId, products, batch) {
    const cell = document.querySelector(`[data-price-product-cell="${rowId}"]`);
    if (!cell) return;
    cell.innerHTML = `<input class="price-product-picker" list="priceImportProducts" data-price-product-input="${rowId}" placeholder="搜索并选择食材" /><button class="table-action" data-price-select="${rowId}">确认</button>`;
    cell.querySelector("[data-price-select]").addEventListener("click", () => selectPriceImportProduct(batch, rowId, products).catch((error) => toast(error.message || "选择系统食材失败")));
    cell.querySelector("[data-price-product-input]").focus();
  }

  async function ignorePriceImportRow(batch, rowId) {
    if (!confirm("确定不导入此项吗？")) return;
    await api(`/api/v1/admin/price-imports/${batch.id}/rows/${rowId}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ignore: true }) });
    await loadPriceImportDetail(batch.id);
  }

  async function reanalyzePriceImport(batch, button) {
    if (!confirm("即将用当前食材目录重新识别此报价单，之前尚未保存的内容会丢失。是否继续？")) return;
    button.disabled = true;
    button.textContent = "正在重新分析";
    setPriceImportMotion("solving", true);
    try {
      await api(`/api/v1/admin/price-imports/${batch.id}/analyze`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
      await loadPriceImportDetail(batch.id);
    } catch (error) {
      toast(error.message || "重新分析失败，请稍后重试");
      button.disabled = false;
      button.textContent = "重新分析报价表";
    }
  }

  async function loadPriceImportDetail(batchId, selectedFilter = "ALL") {
    pageShell("Excel 智能导入与价格同步", "核对新增食材、价格和异常项后，再确认应用");
    const batch = await api(`/api/v1/admin/price-imports/${batchId}`);
    const blockers = (batch.rows || []).filter((row) => !["READY", "IGNORED"].includes(row.validation_status));
    const mapping = priceImportMapping(batch.column_mapping_json);
    const products = await api("/api/v1/admin/products");
    const metrics = batch.metrics || {};
    const existingCount = Number(metrics.existing_product_rows || 0);
    const newCount = Number(metrics.new_product_rows || 0);
    const reviewCount = Number(metrics.needs_review_rows || 0);
    const exceptionCount = Number(metrics.exception_rows || 0);
    const readyExisting = (batch.rows || []).filter((row) => row.validation_status === "READY" && row.operation_type === "EXISTING_PRODUCT").length;
    const readyNew = (batch.rows || []).filter((row) => row.validation_status === "READY" && row.operation_type === "NEW_PRODUCT").length;
    const applyText = readyExisting && readyNew ? `确认更新 ${num(readyExisting)} 个价格并新增 ${num(readyNew)} 种食材` : readyExisting ? `确认更新 ${num(readyExisting)} 个价格` : `确认新增 ${num(readyNew)} 种食材`;
    const filters = [["ALL", "全部"], ["EXISTING", "待同步"], ["NEW", "待新增"], ["NEEDS_REVIEW", "需要确认"], ["EXCEPTION", "异常"]];
    const defaults = batch.new_product_defaults || { category: "其他", spec: "散装", stock_quantity: "0", supply_status: "paused", fallback_unit: "", active: true };
    const defaultCategoryOptions = productCategories.map((item) => `<option value="${html(item)}" ${item === defaults.category ? "selected" : ""}>${html(item)}</option>`).join("");
    const defaultUnitOptions = `<option value="" ${!defaults.fallback_unit ? "selected" : ""}>按 Excel 单位</option>${productUnits.map((item) => `<option value="${html(item)}" ${item === defaults.fallback_unit ? "selected" : ""}>${html(item)}</option>`).join("")}`;
    const defaultStatusOptions = productSupplyStatuses.map(([value, label]) => `<option value="${value}" ${value === defaults.supply_status ? "selected" : ""}>${label}</option>`).join("");
    content().innerHTML += `
      ${priceImportActivityPanel(["UPLOADED", "ANALYZING", "APPLYING"].includes(batch.status) ? "solving" : "working", ["UPLOADED", "ANALYZING", "APPLYING"].includes(batch.status) ? "正在处理导入批次" : "导入结果已就绪", ["UPLOADED", "ANALYZING", "APPLYING"].includes(batch.status) ? "正在与服务端核对食材和价格，请不要关闭当前页面。" : "请完成需要确认的项目，再一次性应用新增食材和价格同步。", true)}
      <article class="panel section-panel"><div class="panel-header"><div><h2>${html(batch.source_filename)}</h2><p>Excel 提供的信息会预填到新增候选；本次操作不会修改任何历史订单价格。</p></div><a class="table-action" href="/admin/price-imports">返回记录</a></div>
        <dl class="status-list detail-list"><dt>共识别</dt><dd>${num((batch.rows || []).length)} 条</dd><dt>更新已有食材</dt><dd>${num(existingCount)}</dd><dt>新增食材</dt><dd>${num(newCount)}</dd><dt>需要确认</dt><dd>${num(reviewCount)}</dd><dt>异常</dt><dd>${num(exceptionCount)}</dd></dl>
        ${products.length === 0 && newCount ? `<p class="notice-banner">系统当前尚无食材，本次识别的 ${num(newCount)} 项将作为新增食材候选。请检查食材资料并补充缺失字段后批量导入。</p>` : ""}
        ${newCount ? `<details class="mapping-details" open><summary>新增食材默认设置</summary><p>以下设置会应用到本批次缺失对应字段的新增食材；Excel 中明确提供的库存、规格和单位优先保留。</p><form id="newProductDefaultsForm"><div class="form-grid compact-form"><label class="form-field"><span>分类</span><select name="category">${defaultCategoryOptions}</select></label><label class="form-field"><span>规格</span><input name="spec" value="${html(defaults.spec || "散装")}" /></label><label class="form-field"><span>初始库存</span><input name="stock_quantity" inputmode="decimal" value="${html(defaults.stock_quantity || "0")}" /></label><label class="form-field"><span>供应状态</span><select name="supply_status">${defaultStatusOptions}</select></label><label class="form-field"><span>缺失单位时使用</span><select name="fallback_unit">${defaultUnitOptions}</select></label></div><button class="secondary-button" type="submit">应用到本批新增食材</button></form></details>` : ""}
        <details class="mapping-details"><summary>修改 Excel 字段识别</summary><p>当前使用：食材名称列“${html(mapping.product_name || "--")}”，价格列“${html(mapping.price || "--")}”。</p><a class="table-action" href="/admin/price-imports/${batch.id}/mapping">重新选择字段</a></details>
        <div class="page-toolbar">${["READY_FOR_REVIEW", "FAILED"].includes(batch.status) ? `<button class="secondary-button" id="reanalyzePriceImport" type="button">重新分析报价表</button>` : ""}${batch.status === "READY_FOR_REVIEW" ? `<button class="primary-link" id="applyPriceImport" type="button" ${blockers.length || (!readyExisting && !readyNew) ? "disabled" : ""}>${applyText}</button>` : ""}${batch.status === "FAILED" ? `<button class="secondary-button" id="manualPriceMapping" type="button">手动确认 Excel 字段</button>` : ""}</div>
        ${blockers.length ? `<p class="error-banner">还有 ${blockers.length} 项需要处理；请确认食材资料、处理异常或忽略本次不导入的项目。</p>` : ""}</article>
      <article class="panel table-panel"><div class="page-toolbar">${filters.map(([value, label]) => `<button class="${value === selectedFilter ? "primary-link" : "secondary-button"}" type="button" data-price-filter="${value}">${label}</button>`).join("")}</div>
        <datalist id="priceImportProducts">${products.map((item) => `<option value="${html(priceImportProductLabel(item))}"></option>`).join("")}</datalist>
        ${table(["原表食材", "系统处理", "规格", "单位", "当前价格", "新价格", "库存", "供应状态", "状态"], reviewRows(batch, selectedFilter), "当前筛选没有报价行")}</article>`;
    mountThinkingOrbs(content());
    document.querySelectorAll("[data-price-filter]").forEach((button) => button.addEventListener("click", () => loadPriceImportDetail(batch.id, button.dataset.priceFilter)));
    document.querySelectorAll("[data-price-select]").forEach((button) => button.addEventListener("click", () => selectPriceImportProduct(batch, button.dataset.priceSelect, products).catch((error) => toast(error.message || "选择系统食材失败"))));
    document.querySelectorAll("[data-price-change]").forEach((button) => button.addEventListener("click", () => enablePriceImportProductChange(button.dataset.priceChange, products, batch)));
    document.querySelectorAll("[data-price-ignore]").forEach((button) => button.addEventListener("click", () => ignorePriceImportRow(batch, button.dataset.priceIgnore).catch((error) => toast(error.message || "忽略项目失败"))));
    document.querySelectorAll("[data-price-save-new]").forEach((button) => button.addEventListener("click", async () => {
      const rowId = button.dataset.priceSaveNew;
      const value = (selector) => document.querySelector(`${selector}="${rowId}"]`)?.value || "";
      button.disabled = true;
      try {
        await api(`/api/v1/admin/price-imports/${batch.id}/rows/${rowId}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ product_code: value("[data-new-code"), category: value("[data-new-category"), spec: value("[data-new-spec"), unit: value("[data-new-unit"), stock_quantity: value("[data-new-stock"), supply_status: value("[data-new-status") }) });
        await loadPriceImportDetail(batch.id, selectedFilter);
      } catch (error) {
        toast(error.message || "保存食材资料失败");
        button.disabled = false;
      }
    }));
    const defaultsForm = $("newProductDefaultsForm");
    if (defaultsForm) defaultsForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      const data = new FormData(defaultsForm);
      const submit = defaultsForm.querySelector("button[type=submit]");
      submit.disabled = true;
      try {
        await api(`/api/v1/admin/price-imports/${batch.id}/new-product-defaults`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ category: data.get("category"), spec: data.get("spec"), stock_quantity: data.get("stock_quantity"), supply_status: data.get("supply_status"), fallback_unit: data.get("fallback_unit"), active: true }) });
        await loadPriceImportDetail(batch.id, selectedFilter);
      } catch (error) {
        toast(error.message || "应用默认设置失败");
        submit.disabled = false;
      }
    });
    const reanalyze = $("reanalyzePriceImport");
    if (reanalyze) reanalyze.addEventListener("click", () => reanalyzePriceImport(batch, reanalyze));
    const apply = $("applyPriceImport");
    if (apply) apply.addEventListener("click", async () => {
      if (!confirm(`确认${applyText}吗？历史订单价格不会变更。`)) return;
      apply.disabled = true; apply.textContent = "正在应用";
      setPriceImportMotion("solving", true);
      try {
        await api(`/api/v1/admin/price-imports/${batch.id}/apply`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ confirmed: true }) });
        toast("批量导入已完成，新价格已生效");
        await loadPriceImportDetail(batch.id);
      } catch (error) {
        toast(error.message || "应用失败，请重新核对");
        apply.disabled = false; apply.textContent = applyText;
      }
    });
    const manual = $("manualPriceMapping");
    if (manual) manual.addEventListener("click", () => loadPriceImportMapping(batch.id));
  }

  function mappingSelect(name, header, selected) {
    const labels = { product_name: "食材名称列", product_code: "食材编码列", category: "分类列", spec: "规格列", unit: "单位列", stock: "库存列", price: "本次执行价格列" };
    return `<label class="form-field"><span>${labels[name] || name}</span><select name="${name}"><option value="">不使用</option>${header.map((value) => `<option value="${html(value)}" ${value === selected ? "selected" : ""}>${html(value)}</option>`).join("")}</select></label>`;
  }

  async function loadPriceImportMapping(batchId) {
    pageShell("确认 Excel 字段", "选择报价 Sheet、表头行和本次执行价格列后重新分析");
    const structure = await api(`/api/v1/admin/price-imports/${batchId}/structure`);
    const sheets = structure.sheets || [];
    if (!sheets.length) return toast("文件中没有可用 Sheet");
    const initial = sheets[0];
    const render = (sheet) => {
      const row = sheet.preview[(sheet.header_candidate || 1) - 1] || [];
      content().innerHTML = `<article class="panel section-panel"><form id="priceMappingForm"><div class="form-grid"><label class="form-field"><span>报价 Sheet</span><select name="sheetName">${sheets.map((item) => `<option value="${html(item.name)}" ${item.name === sheet.name ? "selected" : ""}>${html(item.name)}</option>`).join("")}</select></label><label class="form-field"><span>表头行</span><input name="headerRow" type="number" min="1" max="12" value="${sheet.header_candidate || 1}" /></label>${mappingSelect("product_name", row, "")}${mappingSelect("product_code", row, "")}${mappingSelect("category", row, "")}${mappingSelect("spec", row, "")}${mappingSelect("unit", row, "")}${mappingSelect("stock", row, "")}${mappingSelect("price", row, "")}</div><div class="page-toolbar"><button class="primary-link" type="submit">使用此字段重新分析</button><a class="secondary-button as-link" href="/admin/price-imports/${batchId}">返回</a></div></form><p class="muted">只选择 Excel 中真实存在的列。若多个价格列都可能生效，请选择已确认的本期执行价格。</p>${table(row.map((_, index) => `第 ${index + 1} 列`), sheet.preview.slice(0, 10).map((values) => `<tr>${row.map((_, index) => `<td>${html(values[index] || "")}</td>`).join("")}</tr>`), "暂无预览")}</article>`;
      const form = $("priceMappingForm");
      form.sheetName.addEventListener("change", () => render(sheets.find((item) => item.name === form.sheetName.value) || initial));
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const data = new FormData(form); const mapping = {};
        ["product_name", "product_code", "category", "spec", "unit", "stock", "price"].forEach((key) => { if (data.get(key)) mapping[key] = data.get(key); });
        try {
          await api(`/api/v1/admin/price-imports/${batchId}/analyze`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sheet_name: data.get("sheetName"), header_row: Number(data.get("headerRow")), mapping }) });
          await loadPriceImportDetail(batchId);
        } catch (error) { toast(error.message || "字段确认失败"); }
      });
    };
    render(initial);
  }

  const accountPermissionDefinitions = [
    ["can_manage_accounts", "账号管理", "可创建、停用和重置内部账号"],
    ["can_issue_manager_invites", "发放管理员邀请", "可签发管理员邀请注册链接"],
    ["can_view_system_status", "查看系统状态", "可查看服务状态与运行信息"],
    ["can_view_detailed_metrics", "查看详细指标", "可查看系统详细运行指标"],
    ["can_manage_backups", "备份管理", "可执行备份管理操作"],
    ["can_restore_backups", "恢复备份", "高风险权限，可发起恢复相关操作"],
  ];

  function accountTypeLabel(role) {
    return role === "admin" ? "管理员" : "子单位账号";
  }

  function activeLabel(active) {
    return active ? "启用" : "停用";
  }

  function accountPermissionInputs(account = {}) {
    return accountPermissionDefinitions.map(([key, label, note]) => `
      <label class="permission-option ${key === "can_restore_backups" ? "high-risk" : ""}">
        <input type="checkbox" name="${key}" ${account[key] ? "checked" : ""} />
        <span><strong>${label}</strong><small>${note}</small></span>
      </label>
    `).join("");
  }

  function openOrgDialog(title, body) {
    $("orgDialog")?.remove();
    document.body.insertAdjacentHTML("beforeend", `
      <div id="orgDialog" class="org-dialog-backdrop" role="presentation">
        <section class="org-dialog" role="dialog" aria-modal="true" aria-labelledby="orgDialogTitle">
          <header class="org-dialog-header"><div><h2 id="orgDialogTitle">${html(title)}</h2></div><button id="closeOrgDialog" class="dialog-close" type="button" aria-label="关闭">×</button></header>
          <div class="org-dialog-body">${body}</div>
        </section>
      </div>
    `);
    const close = () => $("orgDialog")?.remove();
    $("closeOrgDialog").addEventListener("click", close);
    $("orgDialog").addEventListener("click", (event) => {
      if (event.target === $("orgDialog")) close();
    });
    return { root: $("orgDialog"), close };
  }

  function formButtonBusy(button, busy, idleText) {
    button.disabled = busy;
    button.textContent = busy ? "提交中..." : idleText;
  }

  function applyOrgFilters(route, values) {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
      const text = String(value || "").trim();
      if (text) params.set(key, text);
    });
    window.history.replaceState({}, "", `${route}${params.toString() ? `?${params}` : ""}`);
  }

  function openUnitDialog(unit = null) {
    const editing = Boolean(unit);
    const dialog = openOrgDialog(editing ? "编辑子单位" : "新增子单位", `
      <form id="unitForm">
        <div class="form-grid compact">
          <label class="form-field"><span>单位名称 *</span><input name="unit_name" maxlength="80" required value="${html(unit?.unit_name || "")}" /></label>
          <label class="form-field"><span>单位编码 *</span><input name="unit_code" maxlength="40" required pattern="[A-Za-z0-9_-]+" value="${html(unit?.unit_code || "")}" /></label>
          <label class="form-field span-2"><span>默认配送点</span><input name="default_delivery_point" maxlength="160" value="${html(unit?.default_delivery_point || "")}" /></label>
          <label class="form-field span-2"><span>地址备注</span><input name="address_note" maxlength="300" value="${html(unit?.address_note || "")}" /></label>
          ${editing ? `<label class="switch-field span-2"><input name="active" type="checkbox" ${unit.active ? "checked" : ""} />启用该子单位</label>` : ""}
        </div>
        <p id="unitFormError" class="error-inline" hidden></p>
        <div class="page-toolbar dialog-actions"><button class="secondary-button" id="cancelUnitForm" type="button">取消</button><button class="primary-link" type="submit">${editing ? "保存修改" : "创建子单位"}</button></div>
      </form>
    `);
    $("cancelUnitForm").addEventListener("click", dialog.close);
    $("unitForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      const submit = form.querySelector('[type="submit"]');
      const error = $("unitFormError");
      const payload = {
        unit_name: data.get("unit_name"), unit_code: data.get("unit_code"),
        default_delivery_point: data.get("default_delivery_point"), address_note: data.get("address_note"),
      };
      if (editing) payload.active = form.active.checked;
      formButtonBusy(submit, true, editing ? "保存修改" : "创建子单位");
      try {
        const result = await api(editing ? `/api/v1/admin/units/${unit.id}` : "/api/v1/admin/units", {
          method: editing ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
        });
        dialog.close();
        if (!editing) {
          state.orgCreatedUnit = result;
          toast("子单位创建成功");
        } else toast("子单位已更新");
        await loadUnits();
      } catch (requestError) {
        error.textContent = requestError.message || "保存失败";
        error.hidden = false;
        formButtonBusy(submit, false, editing ? "保存修改" : "创建子单位");
      }
    });
  }

  function unitAccountLink(unit) {
    return `/admin/accounts?unit_id=${encodeURIComponent(unit.id)}&create=unit`;
  }

  function quotaCents(value) {
    const text = String(value || "").trim();
    if (!/^-?\d+(\.\d{1,2})?$/.test(text)) throw new Error("金额最多保留两位小数");
    const negative = text.startsWith("-");
    const [whole, fraction = ""] = text.replace(/^[+-]/, "").split(".");
    const amount = Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
    return negative ? -amount : amount;
  }

  function quotaMonthLabel(value) {
    const [year, month] = String(value || "").split("-");
    return year && month ? `${year}年${Number(month)}月` : "未来月份";
  }

  function quotaSummaryCells(unit) {
    return {
      base: unit.quota?.enabled ? money(unit.quota.base_quota_cents) : "未启用",
      available: unit.quota?.enabled ? money(unit.quota.available_cents) : "--",
    };
  }

  async function refreshQuotaSummary(units) {
    const items = units || await fetchRealtime("/api/v1/admin/units", "quota");
    if (!items) return false;
    state.unitItems = items;
    const byId = new Map(items.map((unit) => [unit.id, unit]));
    document.querySelectorAll("[data-unit-id]").forEach((row) => {
      const unit = byId.get(row.dataset.unitId);
      if (!unit) return;
      const cells = quotaSummaryCells(unit);
      const base = row.querySelector("[data-unit-quota-base]");
      const available = row.querySelector("[data-unit-quota-available]");
      if (base) base.textContent = cells.base;
      if (available) available.textContent = cells.available;
    });
    return true;
  }

  async function refreshQuotaAfterMutation() {
    state.formDirty = false;
    state.realtime.dirtyResources.delete("quota");
    await refreshQuotaSummary();
  }

  function openFutureQuotaPlanDialog(unit, quota, plan) {
    const explicit = plan.source === "explicit";
    const dialog = openOrgDialog(`${unit.unit_name} · ${quotaMonthLabel(plan.quota_month)}计划额度`, `
      <form id="futureQuotaPlanForm">
        <p class="row-sub">当前来源：${explicit ? "单独设置" : "默认月额度"}</p>
        <label class="form-field"><span>计划额度（元）</span><input name="planned_amount" type="number" min="0.01" step="0.01" value="${(Number(plan.planned_quota_cents || 0) / 100).toFixed(2)}" required /></label>
        <p id="futureQuotaPlanError" class="error-inline" hidden></p>
        <div class="page-toolbar dialog-actions"><button id="cancelFutureQuotaPlan" class="secondary-button" type="button">取消</button>${explicit ? '<button id="restoreFutureQuotaDefault" class="secondary-button" type="button">恢复默认</button>' : ""}<button class="primary-link" type="submit">保存计划</button></div>
      </form>
    `);
    const request = async (path, method, body) => api(path, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify({ expected_version: quota.version, client_request_id: requestId("web-quota-plan"), ...body }) });
    $("cancelFutureQuotaPlan").addEventListener("click", dialog.close);
    $("futureQuotaPlanForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const submit = form.querySelector('[type="submit"]');
      const error = $("futureQuotaPlanError");
      formButtonBusy(submit, true, "保存计划");
      try {
        await request(`/api/v1/admin/units/${unit.id}/quota/future-months/${plan.quota_month}`, "PUT", { planned_quota_cents: quotaCents(new FormData(form).get("planned_amount")) });
        dialog.close();
        toast(`${quotaMonthLabel(plan.quota_month)}计划额度已保存`);
        await refreshQuotaAfterMutation();
      } catch (requestError) {
        error.textContent = requestError.message || "保存计划失败";
        error.hidden = false;
        formButtonBusy(submit, false, "保存计划");
      }
    });
    $("restoreFutureQuotaDefault")?.addEventListener("click", async (event) => {
      if (!confirm(`恢复 ${quotaMonthLabel(plan.quota_month)} 的默认月额度吗？`)) return;
      const button = event.currentTarget;
      formButtonBusy(button, true, "恢复默认");
      try {
        await request(`/api/v1/admin/units/${unit.id}/quota/future-months/${plan.quota_month}/restore-default`, "POST", {});
        dialog.close();
        toast(`${quotaMonthLabel(plan.quota_month)}已恢复默认额度`);
        await refreshQuotaAfterMutation();
      } catch (requestError) {
        $("futureQuotaPlanError").textContent = requestError.message || "恢复默认失败";
        $("futureQuotaPlanError").hidden = false;
        formButtonBusy(button, false, "恢复默认");
      }
    });
  }

  function openUnitQuotaDialog(unit) {
    const quota = unit.quota || {};
    const futurePlans = (quota.future_months || []).map((plan) => `<div class="row-item"><div class="row-head"><strong>${html(quotaMonthLabel(plan.quota_month))}</strong><span>${money(plan.planned_quota_cents)}</span></div><div class="row-sub">来源：${plan.source === "explicit" ? "单独设置" : plan.source === "default" ? "默认额度" : "已形成实际月份账户"}</div>${plan.editable ? `<div class="page-toolbar"><button class="table-action" type="button" data-future-quota-month="${html(plan.quota_month)}">${plan.source === "explicit" ? "修改" : "设置"}</button>${plan.source === "explicit" ? `<button class="table-action" type="button" data-future-quota-restore="${html(plan.quota_month)}">恢复默认</button>` : ""}</div>` : ""}</div>`).join("") || '<div class="row-sub">暂无未来月份计划</div>';
    const dialog = openOrgDialog(`${unit.unit_code || "--"} · ${unit.unit_name} · 采购额度`, `
      <section class="simple-list"><h3>当前月份 · ${html(quotaMonthLabel(quota.quota_month))}</h3><div class="row-sub">原始基础额度 ${money(quota.base_quota_cents)} · 本月额度修正 ${Number(quota.monthly_correction_cents || 0) >= 0 ? "+" : ""}${money(quota.monthly_correction_cents)} · 本月有效额度 ${money(quota.effective_quota_cents)} · 当前可用额度 ${money(quota.available_cents)}</div><div class="page-toolbar"><button id="correctCurrentQuota" class="table-action" type="button">修正本月额度</button><button id="adjustCurrentBalance" class="table-action" type="button">调整当前可用额度</button></div></section>
      <form id="quotaSettingsForm"><div class="form-grid compact"><label class="switch-field span-2"><input name="enabled" type="checkbox" ${quota.enabled ? "checked" : ""} />启用采购额度控制</label><label class="form-field span-2"><span>默认月额度（元）</span><input name="monthly_amount" type="number" min="0" step="0.01" value="${((quota.default_monthly_quota_cents || 0) / 100).toFixed(2)}" required /></label></div><p class="row-sub">未单独设置且尚未激活的未来月份使用此额度。</p><p id="quotaSettingsError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelQuotaSettings" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">保存默认额度</button></div></form>
      <section class="simple-list"><h3>未来月份计划</h3>${futurePlans}</section>
      <section class="simple-list"><h3>过去月份</h3><div class="row-sub">历史月份只读；额度流水和订单额度关联不会被重写。</div></section>
      <section id="quotaLedger" class="simple-list"><div class="row-sub">正在加载额度流水…</div></section>
    `);
    const renderLedger = async () => {
      const data = await api(`/api/v1/admin/units/${unit.id}/quota/ledger`);
      $("quotaLedger").innerHTML = `<h3>本月额度流水</h3>${(data.items || []).map((item) => `<div class="row-item"><div class="row-head"><strong>${html(item.event_type)}</strong><span>${item.delta_cents >= 0 ? "+" : ""}${money(item.delta_cents)}</span></div><div class="row-sub">余额 ${money(item.balance_after_cents)} · ${html(item.order_no || item.note || "--")} · ${html(dateTime(item.display_created_at || item.created_at))}</div></div>`).join("") || '<div class="row-sub">暂无额度流水</div>'}`;
    };
    const send = (path, method, body) => api(path, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify({ expected_version: quota.version, client_request_id: requestId("web-quota"), ...body }) });
    $("cancelQuotaSettings").addEventListener("click", dialog.close);
    $("quotaSettingsForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const error = $("quotaSettingsError"); const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "保存默认额度"); try { await send(`/api/v1/admin/units/${unit.id}/quota`, "PUT", { enabled: form.enabled.checked, default_monthly_quota_cents: quotaCents(new FormData(form).get("monthly_amount")) }); dialog.close(); toast("默认月额度已保存"); await refreshQuotaAfterMutation(); } catch (requestError) { error.textContent = requestError.message || "保存失败"; error.hidden = false; formButtonBusy(submit, false, "保存默认额度"); } });
    $("correctCurrentQuota").addEventListener("click", () => openCurrentQuotaCorrectionDialog(unit, quota, dialog));
    $("adjustCurrentBalance").addEventListener("click", () => openCurrentBalanceAdjustmentDialog(unit, quota, dialog));
    document.querySelectorAll("[data-future-quota-month]").forEach((button) => button.addEventListener("click", () => openFutureQuotaPlanDialog(unit, quota, (quota.future_months || []).find((plan) => plan.quota_month === button.dataset.futureQuotaMonth))));
    document.querySelectorAll("[data-future-quota-restore]").forEach((button) => button.addEventListener("click", () => openFutureQuotaPlanDialog(unit, quota, (quota.future_months || []).find((plan) => plan.quota_month === button.dataset.futureQuotaRestore))));
    renderLedger().catch((error) => { $("quotaLedger").innerHTML = `<div class="row-sub">${html(error.message || "额度流水加载失败")}</div>`; });
  }

  function openCurrentQuotaCorrectionDialog(unit, quota, parentDialog) {
    const dialog = openOrgDialog(`${unit.unit_name} · 修正本月额度`, `<form id="currentQuotaCorrectionForm"><p class="row-sub">原始基础额度 ${money(quota.base_quota_cents)}，当前有效额度 ${money(quota.effective_quota_cents)}。此操作调整本月整体预算上限，不等同于临时余额调整。</p><div class="form-grid compact"><label class="form-field"><span>修正后本月有效额度（元）</span><input name="effective_amount" type="number" min="0" step="0.01" value="${(Number(quota.effective_quota_cents || 0) / 100).toFixed(2)}" required /></label><label class="form-field"><span>修正原因 *</span><input name="reason" maxlength="300" required /></label></div><p id="currentQuotaCorrectionError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelCurrentQuotaCorrection" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">保存修正</button></div></form>`);
    $("cancelCurrentQuotaCorrection").addEventListener("click", dialog.close);
    $("currentQuotaCorrectionForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "保存修正"); try { await api(`/api/v1/admin/units/${unit.id}/quota/current-month-correction`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ effective_quota_cents: quotaCents(new FormData(form).get("effective_amount")), reason: new FormData(form).get("reason"), expected_version: quota.version, client_request_id: requestId("web-quota-correction") }) }); dialog.close(); parentDialog.close(); toast("本月额度已修正"); await refreshQuotaAfterMutation(); } catch (error) { $("currentQuotaCorrectionError").textContent = error.message || "本月额度修正失败"; $("currentQuotaCorrectionError").hidden = false; formButtonBusy(submit, false, "保存修正"); } });
  }

  function openCurrentBalanceAdjustmentDialog(unit, quota, parentDialog) {
    const dialog = openOrgDialog(`${unit.unit_name} · 调整当前可用额度`, `<form id="currentBalanceAdjustmentForm"><p class="row-sub">当前可用额度 ${money(quota.available_cents)}。此操作仅临时增减当前可用余额，不改变本月整体预算上限。</p><div class="form-grid compact"><label class="form-field"><span>调整金额（元，可为负）</span><input name="delta" type="number" step="0.01" required /></label><label class="form-field"><span>调整原因 *</span><input name="reason" maxlength="300" required /></label></div><p id="currentBalanceAdjustmentError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelCurrentBalanceAdjustment" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">提交调整</button></div></form>`);
    $("cancelCurrentBalanceAdjustment").addEventListener("click", dialog.close);
    $("currentBalanceAdjustmentForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "提交调整"); try { await api(`/api/v1/admin/units/${unit.id}/quota/adjustments`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ delta_cents: quotaCents(new FormData(form).get("delta")), reason: new FormData(form).get("reason"), expected_version: quota.version, client_request_id: requestId("web-quota-balance") }) }); dialog.close(); parentDialog.close(); toast("当前可用额度已调整"); await refreshQuotaAfterMutation(); } catch (error) { $("currentBalanceAdjustmentError").textContent = error.message || "调整失败"; $("currentBalanceAdjustmentError").hidden = false; formButtonBusy(submit, false, "提交调整"); } });
  }

  async function loadUnits() {
    const params = new URLSearchParams(window.location.search);
    const query = (params.get("query") || "").trim().toLowerCase();
    const status = params.get("status") || "all";
    pageShell("子单位管理", "维护单位资料、配送点和关联账号");
    const [viewer, units] = await Promise.all([api("/api/v1/auth/me"), api("/api/v1/admin/units")]);
    const canManage = Boolean(viewer.can_manage_accounts);
    const rows = units.filter((unit) => {
      const haystack = `${unit.unit_name} ${unit.unit_code}`.toLowerCase();
      return (!query || haystack.includes(query)) && (status === "all" || String(Boolean(unit.active)) === status);
    });
    state.unitItems = units;
    const created = state.orgCreatedUnit;
    state.orgCreatedUnit = null;
    content().innerHTML += `
      ${created ? `<article class="notice-banner org-success"><strong>子单位创建成功</strong><span>${html(created.unit_name)} 已创建。下一步可为该单位创建登录账号。</span><a class="primary-link" href="${unitAccountLink(created)}">创建登录账号</a></article>` : ""}
      ${canManage ? `<div class="page-toolbar"><button id="createUnitButton" class="primary-link" type="button">+ 新增子单位</button></div>` : `<div class="notice-banner">当前账号仅可查看子单位，组织和账号变更需要“账号管理”权限。</div>`}
      <form id="unitFilters" class="compact-form page-toolbar"><label class="form-field"><span>单位名称 / 编码</span><input name="query" value="${html(params.get("query") || "")}" placeholder="搜索单位" /></label><label class="form-field"><span>状态</span><select name="status"><option value="all" ${status === "all" ? "selected" : ""}>全部</option><option value="true" ${status === "true" ? "selected" : ""}>启用</option><option value="false" ${status === "false" ? "selected" : ""}>停用</option></select></label><button class="secondary-button" type="submit">筛选</button></form>
    `;
    content().innerHTML += table(["单位编码", "单位名称", "采购额度", "当前可用", "关联账号", "订单数", "状态", "更新时间", "操作"], rows.map((unit) => `
      <tr data-unit-id="${unit.id}"><td><strong>${html(unit.unit_code)}</strong></td><td>${html(unit.unit_name)}</td><td data-unit-quota-base>${quotaSummaryCells(unit).base}</td><td data-unit-quota-available>${quotaSummaryCells(unit).available}</td><td><a class="text-link" href="${unitAccountLink(unit)}">${num(unit.account_count)} 个</a></td><td>${num(unit.order_count)}</td><td>${activeLabel(unit.active)}</td><td>${dateTime(unit.updated_at)}</td><td>${canManage ? `<button class="table-action" data-edit-unit="${unit.id}">编辑</button><button class="table-action" data-unit-quota="${unit.id}">额度</button><details class="action-menu"><summary>更多</summary><button type="button" data-unit-status="${unit.id}" data-next-active="${unit.active ? "0" : "1"}">${unit.active ? "停用" : "启用"}</button></details>` : "--"}</td></tr>
    `), "暂无符合条件的子单位");
    $("createUnitButton")?.addEventListener("click", () => openUnitDialog());
    $("unitFilters").addEventListener("submit", (event) => { event.preventDefault(); const data = new FormData(event.currentTarget); applyOrgFilters("/admin/units", { query: data.get("query"), status: data.get("status") === "all" ? "" : data.get("status") }); loadUnits(); });
    document.querySelectorAll("[data-edit-unit]").forEach((button) => button.addEventListener("click", () => openUnitDialog(units.find((unit) => unit.id === button.dataset.editUnit))));
    document.querySelectorAll("[data-unit-quota]").forEach((button) => button.addEventListener("click", () => openUnitQuotaDialog(units.find((unit) => unit.id === button.dataset.unitQuota))));
    document.querySelectorAll("[data-unit-status]").forEach((button) => button.addEventListener("click", async () => {
      const active = button.dataset.nextActive === "1";
      if (!confirm(`确认${active ? "启用" : "停用"}该子单位吗？${active ? "" : "停用后关联账号不能继续登录，历史记录不会删除。"}`)) return;
      button.disabled = true;
      try { await api(`/api/v1/admin/units/${button.dataset.unitStatus}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ active }) }); toast(`子单位已${active ? "启用" : "停用"}`); await loadUnits(); } catch (error) { toast(error.message || "操作失败"); button.disabled = false; }
    }));
  }

  function accountFormFields(type, units, selectedUnitId) {
    const activeUnits = units.filter((unit) => unit.active);
    const unitField = type === "unit" ? `<label class="form-field span-2"><span>所属子单位 *</span><select name="unit_id" required><option value="">请选择所属子单位</option>${activeUnits.map((unit) => `<option value="${unit.id}" ${unit.id === selectedUnitId ? "selected" : ""}>${html(unit.unit_name)}（${html(unit.unit_code)}）</option>`).join("")}</select></label>` : "";
    return `<div class="form-grid compact"><label class="form-field"><span>账号 *</span><input name="username" minlength="3" maxlength="32" required placeholder="字母、数字、点、横线或下划线" /></label><label class="form-field"><span>显示名称 *</span><input name="display_name" maxlength="80" required /></label>${unitField}<label class="form-field"><span>临时密码 *</span><input name="password" type="password" minlength="8" autocomplete="new-password" required /></label><label class="form-field"><span>确认临时密码 *</span><input name="password_confirm" type="password" minlength="8" autocomplete="new-password" required /></label>${type === "admin" ? `<fieldset class="permission-grid span-2"><legend>权限</legend>${accountPermissionInputs()}</fieldset>` : ""}</div>`;
  }

  function openAccountDialog(units, preselectUnitId = "") {
    let type = "unit";
    const dialog = openOrgDialog("新增账号", `<div class="account-type-picker"><label><input type="radio" name="accountType" value="unit" checked /> 子单位账号</label><label><input type="radio" name="accountType" value="admin" /> 管理员账号</label></div><form id="accountForm"><div id="accountFormFields">${accountFormFields(type, units, preselectUnitId)}</div><p id="accountFormError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelAccountForm" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">创建账号</button></div></form>`);
    const redraw = () => {
      dialog.root.querySelectorAll('input[name="accountType"]').forEach((radio) => { radio.checked = radio.value === type; });
      $("accountFormFields").innerHTML = accountFormFields(type, units, preselectUnitId);
    };
    dialog.root.querySelectorAll('input[name="accountType"]').forEach((radio) => radio.addEventListener("change", (event) => { type = event.currentTarget.value; redraw(); }));
    $("cancelAccountForm").addEventListener("click", dialog.close);
    $("accountForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      const password = String(data.get("password") || "");
      const error = $("accountFormError");
      if (password !== String(data.get("password_confirm") || "")) { error.textContent = "两次输入的密码不一致"; error.hidden = false; return; }
      const payload = { username: data.get("username"), display_name: data.get("display_name"), password };
      if (type === "unit") payload.unit_id = data.get("unit_id");
      accountPermissionDefinitions.forEach(([key]) => { if (type === "admin") payload[key] = data.get(key) === "on"; });
      if (payload.can_restore_backups && !confirm("“恢复备份”属于高风险权限，确认授予吗？")) return;
      const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "创建账号");
      try {
        const created = await api(type === "unit" ? "/api/v1/admin/accounts/unit-user" : "/api/v1/admin/accounts/admin", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
        dialog.close();
        showOneTimeCredentials(created, password, () => loadAccounts());
      } catch (requestError) {
        error.textContent = requestError.message || "创建账号失败"; error.hidden = false; formButtonBusy(submit, false, "创建账号");
      }
    });
  }

  function showOneTimeCredentials(account, password, done) {
    const dialog = openOrgDialog("账号创建成功", `<div class="credential-notice"><p>请立即安全告知使用人。关闭此窗口后，系统不会再次显示临时密码。</p><dl><dt>账号</dt><dd>${html(account.username)}</dd><dt>临时密码</dt><dd>${html(password)}</dd></dl><div class="page-toolbar dialog-actions"><button id="credentialsDone" class="primary-link" type="button">我已记录</button></div></div>`);
    $("credentialsDone").addEventListener("click", async () => { dialog.close(); await done(); toast("账号创建成功"); });
  }

  function openAccountEditDialog(account, units) {
    const dialog = openOrgDialog("编辑账号", `<form id="accountEditForm"><div class="form-grid compact"><label class="form-field"><span>账号</span><input value="${html(account.username)}" disabled /></label><label class="form-field"><span>账号类型</span><input value="${accountTypeLabel(account.role)}" disabled /></label><label class="form-field"><span>显示名称 *</span><input name="display_name" required value="${html(account.display_name)}" /></label>${account.role === "unit_user" ? `<label class="form-field"><span>所属子单位 *</span><select name="unit_id" required>${units.filter((unit) => unit.active || unit.id === account.unit_id).map((unit) => `<option value="${unit.id}" ${unit.id === account.unit_id ? "selected" : ""}>${html(unit.unit_name)}（${html(unit.unit_code)}）</option>`).join("")}</select></label>` : ""}</div><p id="accountEditError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelAccountEdit" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">保存修改</button></div></form>`);
    $("cancelAccountEdit").addEventListener("click", dialog.close);
    $("accountEditForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "保存修改"); try { await api(`/api/v1/admin/accounts/${account.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ display_name: data.get("display_name"), ...(account.role === "unit_user" ? { unit_id: data.get("unit_id") } : {}) }) }); dialog.close(); toast("账号已更新"); await loadAccounts(); } catch (error) { $("accountEditError").textContent = error.message || "保存失败"; $("accountEditError").hidden = false; formButtonBusy(submit, false, "保存修改"); } });
  }

  function openPasswordResetDialog(account) {
    const dialog = openOrgDialog("重置密码", `<form id="passwordResetForm"><p>重置后该账号现有登录状态会立即失效，并要求下次登录后修改密码。</p><div class="form-grid compact"><label class="form-field"><span>新临时密码 *</span><input name="password" type="password" minlength="8" autocomplete="new-password" required /></label><label class="form-field"><span>确认新临时密码 *</span><input name="password_confirm" type="password" minlength="8" autocomplete="new-password" required /></label></div><p id="passwordResetError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelPasswordReset" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">重置密码</button></div></form>`);
    $("cancelPasswordReset").addEventListener("click", dialog.close);
    $("passwordResetForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const password = String(data.get("password") || ""); if (password !== String(data.get("password_confirm") || "")) { $("passwordResetError").textContent = "两次输入的密码不一致"; $("passwordResetError").hidden = false; return; } const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "重置密码"); try { await api(`/api/v1/admin/accounts/${account.id}/reset-password`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ new_password: password }) }); dialog.close(); showOneTimeCredentials(account, password, () => loadAccounts()); } catch (error) { $("passwordResetError").textContent = error.message || "重置失败"; $("passwordResetError").hidden = false; formButtonBusy(submit, false, "重置密码"); } });
  }

  function openPermissionsDialog(account) {
    const dialog = openOrgDialog("管理员权限", `<form id="permissionForm"><p>权限修改立即生效。恢复备份为高风险权限，默认关闭。</p><fieldset class="permission-grid">${accountPermissionInputs(account)}</fieldset><p id="permissionError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelPermissionForm" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">保存权限</button></div></form>`);
    $("cancelPermissionForm").addEventListener("click", dialog.close);
    $("permissionForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const payload = {}; accountPermissionDefinitions.forEach(([key]) => { payload[key] = data.get(key) === "on"; }); if (payload.can_restore_backups && !confirm("“恢复备份”属于高风险权限，确认保存吗？")) return; const submit = form.querySelector('[type="submit"]'); formButtonBusy(submit, true, "保存权限"); try { await api(`/api/v1/admin/accounts/${account.id}/permissions`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); dialog.close(); toast("权限已更新"); await loadAccounts(); } catch (error) { $("permissionError").textContent = error.message || "保存失败"; $("permissionError").hidden = false; formButtonBusy(submit, false, "保存权限"); } });
  }

  async function loadAccounts() {
    const params = new URLSearchParams(window.location.search);
    const query = (params.get("query") || "").trim().toLowerCase();
    const role = params.get("role") || "all";
    const unitId = params.get("unit_id") || "";
    const status = params.get("status") || "all";
    pageShell("账号管理", "创建和维护子单位、管理员登录账号");
    const [viewer, units, users] = await Promise.all([api("/api/v1/auth/me"), api("/api/v1/admin/units"), api(`/api/v1/admin/users${unitId ? `?unit_id=${encodeURIComponent(unitId)}` : ""}`)]);
    const canManage = Boolean(viewer.can_manage_accounts);
    const rows = users.filter((user) => {
      const haystack = `${user.username} ${user.display_name}`.toLowerCase();
      return (!query || haystack.includes(query)) && (role === "all" || user.role === role) && (status === "all" || String(Boolean(user.active)) === status);
    });
    content().innerHTML += `
      ${canManage ? `<div class="page-toolbar"><button id="createAccountButton" class="primary-link" type="button">+ 新增账号</button></div>` : `<div class="notice-banner">当前账号仅可查看账号信息，创建、重置、停用和权限变更需要“账号管理”权限。</div>`}
      <form id="accountFilters" class="compact-form page-toolbar"><label class="form-field"><span>账号 / 姓名</span><input name="query" value="${html(params.get("query") || "")}" placeholder="搜索账号" /></label><label class="form-field"><span>账号类型</span><select name="role"><option value="all" ${role === "all" ? "selected" : ""}>全部</option><option value="unit_user" ${role === "unit_user" ? "selected" : ""}>子单位账号</option><option value="admin" ${role === "admin" ? "selected" : ""}>管理员</option></select></label><label class="form-field"><span>所属单位</span><select name="unit_id"><option value="">全部</option>${units.map((unit) => `<option value="${unit.id}" ${unit.id === unitId ? "selected" : ""}>${html(unit.unit_name)}</option>`).join("")}</select></label><label class="form-field"><span>状态</span><select name="status"><option value="all" ${status === "all" ? "selected" : ""}>全部</option><option value="true" ${status === "true" ? "selected" : ""}>启用</option><option value="false" ${status === "false" ? "selected" : ""}>停用</option></select></label><button class="secondary-button" type="submit">筛选</button></form>
    `;
    content().innerHTML += table(["账号", "显示名称", "类型", "所属单位", "状态", "最后登录", "操作"], rows.map((user) => `
      <tr><td>${html(user.username)}</td><td>${html(user.display_name)}</td><td>${accountTypeLabel(user.role)}</td><td>${html(user.unit_name || "--")}</td><td>${activeLabel(user.active)}</td><td>${dateTime(user.last_login_at)}</td><td>${canManage ? `<button class="table-action" data-edit-account="${user.id}">编辑</button><details class="action-menu"><summary>更多</summary>${user.role === "admin" ? `<button type="button" data-permissions="${user.id}">权限</button>` : ""}<button type="button" data-reset-account="${user.id}">重置密码</button>${user.id === viewer.id ? "" : `<button type="button" data-account-status="${user.id}" data-next-active="${user.active ? "0" : "1"}">${user.active ? "停用" : "启用"}</button>`}</details>` : "--"}</td></tr>
    `), "暂无符合条件的账号");
    $("createAccountButton")?.addEventListener("click", () => openAccountDialog(units, unitId));
    $("accountFilters").addEventListener("submit", (event) => { event.preventDefault(); const data = new FormData(event.currentTarget); applyOrgFilters("/admin/accounts", { query: data.get("query"), role: data.get("role") === "all" ? "" : data.get("role"), unit_id: data.get("unit_id"), status: data.get("status") === "all" ? "" : data.get("status") }); loadAccounts(); });
    document.querySelectorAll("[data-edit-account]").forEach((button) => button.addEventListener("click", () => openAccountEditDialog(users.find((user) => user.id === button.dataset.editAccount), units)));
    document.querySelectorAll("[data-reset-account]").forEach((button) => button.addEventListener("click", () => openPasswordResetDialog(users.find((user) => user.id === button.dataset.resetAccount))));
    document.querySelectorAll("[data-permissions]").forEach((button) => button.addEventListener("click", () => openPermissionsDialog(users.find((user) => user.id === button.dataset.permissions))));
    document.querySelectorAll("[data-account-status]").forEach((button) => button.addEventListener("click", async () => { const active = button.dataset.nextActive === "1"; const user = users.find((item) => item.id === button.dataset.accountStatus); if (!confirm(`确认${active ? "启用" : "停用"}账号“${user.username}”吗？${active ? "" : "停用后该账号不能继续登录，历史业务记录不会删除。"}`)) return; button.disabled = true; try { await api(`/api/v1/admin/accounts/${user.id}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ active }) }); toast(`账号已${active ? "启用" : "停用"}`); await loadAccounts(); } catch (error) { toast(error.message || "操作失败"); button.disabled = false; } }));
    if (params.get("create") === "unit" && canManage) {
      const next = new URLSearchParams(params); next.delete("create"); window.history.replaceState({}, "", `/admin/accounts?${next.toString()}`); openAccountDialog(units, unitId);
    }
  }

  async function loadLedger() {
    pageShell("采购台账", "按订单和食材明细查看，可导出 Excel");
    const params = new URLSearchParams(window.location.search);
    const filters = new URLSearchParams();
    const filterKeys = ["start_date", "end_date", "unit_id", "status", "order_no"];
    filterKeys.forEach((key) => {
      if (params.get(key)) filters.set(key, params.get(key));
    });
    const page = Math.max(1, Number(params.get("page") || "1"));
    const pageQuery = new URLSearchParams(filters);
    pageQuery.set("page", String(page));
    pageQuery.set("page_size", "20");
    const [units, ledgerData] = await Promise.all([
      api("/api/v1/admin/units"),
      api(`/api/v1/admin/ledger?${pageQuery.toString()}`),
    ]);
    const rows = ledgerData.items || [];
    const total = Number(ledgerData.total || 0);
    const pageSize = Number(ledgerData.page_size || 20);
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const startDate = filters.get("start_date") || "";
    const endDate = filters.get("end_date") || "";
    const selectedUnit = filters.get("unit_id") || "";
    const selectedStatus = filters.get("status") || "";
    const orderNo = filters.get("order_no") || "";
    const currentExportQuery = filters.toString();
    const pageLink = (targetPage) => {
      const target = new URLSearchParams(filters);
      target.set("page", String(targetPage));
      return `/admin/ledger?${target.toString()}`;
    };
    const statusOptions = [["", "全部状态"], ...Object.entries(statusText)];
    content().innerHTML += `
      <article class="panel section-panel">
        <form id="ledgerFilterForm">
          <div class="form-grid compact">
            <label class="form-field"><span>开始日期</span><input name="start_date" type="date" value="${html(startDate)}" /></label>
            <label class="form-field"><span>结束日期</span><input name="end_date" type="date" value="${html(endDate)}" /></label>
            <label class="form-field"><span>下单单位</span><select name="unit_id"><option value="">全部单位</option>${units.map((unit) => `<option value="${html(unit.id)}" ${unit.id === selectedUnit ? "selected" : ""}>${html(unit.unit_name)}${unit.active ? "" : "（已停用）"}</option>`).join("")}</select></label>
            <label class="form-field"><span>订单状态</span><select name="status">${statusOptions.map(([value, label]) => `<option value="${html(value)}" ${value === selectedStatus ? "selected" : ""}>${html(label)}</option>`).join("")}</select></label>
            <label class="form-field"><span>订单号</span><input name="order_no" type="search" value="${html(orderNo)}" placeholder="例如 SP2026" /></label>
          </div>
          <div class="page-toolbar"><button class="table-action primary" type="submit">查询</button><button id="ledgerClearFilters" class="table-action" type="button">清除筛选</button><a class="primary-link" href="/api/v1/admin/ledger/export.xlsx?${currentExportQuery}">导出当前筛选</a><a class="table-action" href="/api/v1/admin/ledger/export.xlsx?all=true">导出全部台账</a></div>
        </form>
      </article>
      <article class="panel table-panel">
        <div class="panel-header"><div><h2>采购明细</h2><p>共 ${num(total)} 条，当前第 ${num(page)} / ${num(totalPages)} 页。</p></div></div>
        ${table(["订单编号", "单位", "下单时间", "状态", "食材", "规格", "数量", "单价", "小计", "订单金额"], rows.map((row) => `
          <tr><td>${html(row.order_no)}</td><td>${html(row.unit_name_snapshot || "--")}</td><td>${dateTime(row.created_at)}</td><td>${statusTag(row.status)}</td><td>${html(row.product_name_snapshot)}</td><td>${html(row.spec_snapshot || "--")}</td><td>${qty(row.quantity)}${html(row.unit_snapshot ? ` ${row.unit_snapshot}` : "")}</td><td>${money(row.price_cents_snapshot)}</td><td>${money(row.subtotal_cents)}</td><td>${money(row.total_cents)}</td></tr>
        `), "暂无台账记录")}
        <div class="page-toolbar">
          ${page > 1 ? `<a class="table-action" href="${pageLink(page - 1)}">上一页</a>` : `<button class="table-action" type="button" disabled>上一页</button>`}
          ${page < totalPages ? `<a class="table-action" href="${pageLink(page + 1)}">下一页</a>` : `<button class="table-action" type="button" disabled>下一页</button>`}
        </div>
      </article>`;
    $("ledgerFilterForm").addEventListener("submit", (event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      const nextStartDate = String(form.get("start_date") || "").trim();
      const nextEndDate = String(form.get("end_date") || "").trim();
      if (nextStartDate && nextEndDate && nextStartDate > nextEndDate) {
        toast("开始日期不能晚于结束日期");
        return;
      }
      const next = new URLSearchParams();
      filterKeys.forEach((key) => {
        const value = String(form.get(key) || "").trim();
        if (value) next.set(key, value);
      });
      next.set("page", "1");
      window.location.assign(`/admin/ledger?${next.toString()}`);
    });
    $("ledgerClearFilters").addEventListener("click", () => window.location.assign("/admin/ledger"));
  }

  function deliveryBatchStatus(status) {
    return ({ open: "备货中", closed: "已完成", cancelled: "已归档" })[status] || "未知状态";
  }

  function batchDisplayName(batch) {
    const explicitName = String(batch.name || "").trim();
    if (explicitName) return explicitName;
    const orderLabels = new Map();
    (batch.orders || []).forEach((order) => {
      const unitId = String(order.unit_id || "");
      if (!unitId || orderLabels.has(unitId)) return;
      const unitCode = String(order.unit_code || "--").trim() || "--";
      const unitName = String(order.unit_name_snapshot || "单位名称未记录").trim();
      orderLabels.set(unitId, `${unitCode} · ${unitName}`);
    });
    const labels = orderLabels.size
      ? [...orderLabels.values()]
      : String(batch.unit_labels || "").split(",").map((value) => value.trim()).filter(Boolean);
    if (labels.length) return [...new Set(labels)].sort((left, right) => left.localeCompare(right, "zh-CN")).join("；");
    const codes = String(batch.unit_codes || "").split(",").map((value) => value.trim()).filter(Boolean).sort();
    if (codes.length) return `${codes.join("、")} · 单位备货单`;
    return batch.batch_no ? `备货单 ${batch.batch_no}` : "备货单";
  }

  function batchOrderId(order) {
    return String(order.id || order.order_id || "");
  }

  function batchReviewDialog({ title, group, targetLabel, orders, warning = "", confirmLabel = "确认", onConfirm, onCancel = null, onComplete = null }) {
    const dialog = openOrgDialog(title, `
      <div class="batch-review">
        ${warning ? `<div class="notice-banner batch-warning">${html(warning)}</div>` : ""}
        <dl class="status-list detail-list">
          <dt>单位</dt><dd>${html(group.unit_code || "--")} · ${html(group.unit_name)}</dd>
          <dt>目标备货单</dt><dd>${html(targetLabel)}</dd>
          <dt>订单数量</dt><dd>${num(orders.length)} 张</dd>
          <dt>食材种类</dt><dd>${Number.isFinite(Number(group.product_count)) ? `${num(group.product_count)} 种` : "以最新汇总为准"}</dd>
        </dl>
        ${table(["订单编号", "状态", "下单时间"], orders.map((order) => `<tr><td>${html(order.order_no)}</td><td>${statusTag(order.status)}</td><td>${dateTime(order.created_at)}</td></tr>`), "没有可处理订单")}
        <p class="row-sub">相同商品、规格和计量单位将按历史订单快照汇总；不同计量单位不会相加。</p>
        <p id="batchReviewError" class="error-inline" hidden></p>
        <div class="page-toolbar dialog-actions"><button id="cancelBatchReview" class="secondary-button" type="button">返回修改</button><button id="confirmBatchReview" class="primary-link" type="button">${html(confirmLabel)}</button></div>
      </div>
    `);
    $("cancelBatchReview").addEventListener("click", () => {
      dialog.close();
      if (onCancel) onCancel();
    });
    $("confirmBatchReview").addEventListener("click", async (event) => {
      const button = event.currentTarget;
      formButtonBusy(button, true, confirmLabel);
      try {
        await onConfirm();
        dialog.close();
        state.formDirty = false;
        if (onComplete) await onComplete(); else await loadBatches();
      } catch (error) {
        $("batchReviewError").textContent = error.message || "备货单操作失败";
        $("batchReviewError").hidden = false;
        formButtonBusy(button, false, confirmLabel);
      }
    });
  }

  function selectedPendingOrders(group) {
    const selected = new Set(
      Array.from(document.querySelectorAll("[data-unit-pending-order]"))
        .filter((checkbox) => checkbox.checked && checkbox.dataset.unitId === group.unit_id)
        .map((checkbox) => checkbox.dataset.unitPendingOrder)
    );
    return (group.pending_orders || []).filter((order) => selected.has(batchOrderId(order)));
  }

  function openGroupBatchAction(group, action) {
    const pendingOrders = selectedPendingOrders(group);
    const openBatches = group.open_batches || [];
    const target = openBatches[0];
    if (["create", "append", "separate"].includes(action) && !pendingOrders.length) {
      toast("请至少选择一张待处理订单");
      return;
    }
    if (action === "create" || action === "separate") {
      const duplicateWarning = action === "separate" && openBatches.length
        ? "该单位已经存在未完成备货单。通常应追加到现有备货单；继续新建将产生多张现场单据。"
        : "";
      batchReviewDialog({
        title: action === "separate" ? "确认单独新建" : "即将生成备货单",
        group,
        targetLabel: "新备货单",
        orders: pendingOrders,
        warning: duplicateWarning,
        confirmLabel: action === "separate" ? "仍然新建" : "确认生成",
        onConfirm: async () => {
          const batch = await api("/api/v1/admin/batches", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              name: `${group.unit_code || ""} ${group.unit_name}备货单`.trim(),
              note: "按单位整理生成",
              order_ids: pendingOrders.map(batchOrderId),
              client_request_id: requestId("batch-create"),
            }),
          });
          toast(`已生成 ${batch.batch_no}`);
        },
      });
      return;
    }
    if (action === "append" && target) {
      batchReviewDialog({
        title: "即将追加备货需求",
        group,
        targetLabel: target.batch_no,
        orders: pendingOrders,
        confirmLabel: "确认追加",
        onConfirm: async () => {
          await api(`/api/v1/admin/batches/${target.id}/orders`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              add_order_ids: pendingOrders.map(batchOrderId),
              remove_order_ids: [],
              expected_version: target.version,
              client_request_id: requestId("batch-append"),
            }),
          });
          toast(`已追加到 ${target.batch_no}`);
        },
      });
      return;
    }
    if (action === "reconcile" && openBatches.length >= 2) {
      const sourceBatches = openBatches.slice(1);
      const linkedOrders = openBatches.flatMap((batch) => batch.orders || []);
      batchReviewDialog({
        title: "即将整理为一张备货单",
        group,
        targetLabel: target.batch_no,
        orders: linkedOrders,
        warning: `将把该单位在 ${num(openBatches.length)} 张未完成备货单中的订单整理到 ${target.batch_no}。其它单位订单不受影响。`,
        confirmLabel: "确认整理",
        onConfirm: async () => {
          await api("/api/v1/admin/batches/reconcile", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              unit_id: group.unit_id,
              target_batch_id: target.id,
              source_batch_ids: sourceBatches.map((batch) => batch.id),
              order_ids: linkedOrders.map(batchOrderId),
              expected_versions: Object.fromEntries(openBatches.map((batch) => [batch.id, batch.version])),
              client_request_id: requestId("batch-reconcile"),
            }),
          });
          toast(`已整理到 ${target.batch_no}`);
        },
      });
    }
  }

  function batchWorkbenchGroup(group) {
    const recommendation = window.AdminRefreshPolicy.batchRecommendation(group);
    const primaryLabel = ({ create: "合并生成 1 张备货单", append: `追加到 ${group.open_batches?.[0]?.batch_no || "现有备货单"}`, reconcile: "整理为一张" })[recommendation] || "查看";
    const pendingRows = (group.pending_orders || []).map((order) => `
      <tr><td><input type="checkbox" data-unit-pending-order="${html(order.id)}" data-unit-id="${html(group.unit_id)}" checked aria-label="选择订单 ${html(order.order_no)}" /></td><td>${html(order.order_no)}</td><td>${statusTag(order.status)}</td><td>${dateTime(order.created_at)}</td><td>${money(order.total_cents)}</td></tr>
    `);
    const batches = (group.open_batches || []).map((batch) => `<a class="table-action" href="/admin/batches/${batch.id}">${html(batch.batch_no)}（${num(batch.orders?.length)} 张订单）</a>`).join(" ");
    return `
      <section class="unit-batch-group" data-unit-group="${html(group.unit_id)}">
        <div class="panel-header"><div><h3>${html(group.unit_code || "--")} · ${html(group.unit_name)}</h3><p>待处理订单 ${num(group.pending_order_count)} 张 · 现有未完成备货单 ${num(group.open_batch_count)} 张 · 食材种类 ${num(group.product_count)}</p></div></div>
        ${group.open_batch_count > 1 ? `<div class="notice-banner batch-warning">该单位存在多张未完成备货单：${batches}</div>` : group.open_batch_count === 1 ? `<div class="notice-banner">该单位已有未完成备货单：${batches}</div>` : ""}
        ${group.pending_order_count ? table(["选择", "订单编号", "状态", "下单时间", "金额"], pendingRows, "暂无待处理订单") : ""}
        <div class="page-toolbar">
          ${recommendation !== "none" ? `<button class="primary-link" type="button" data-batch-v2-action="${recommendation}" data-unit-id="${html(group.unit_id)}">${html(primaryLabel)}</button>` : ""}
          ${group.open_batch_count > 0 && group.pending_order_count > 0 ? `<button class="secondary-button" type="button" data-batch-v2-action="separate" data-unit-id="${html(group.unit_id)}">单独新建</button>` : ""}
        </div>
      </section>
    `;
  }

  function openBatchAdjustDialog(batch, eligible, selectedIds = null) {
    const currentIds = new Set((batch.orders || []).map(batchOrderId));
    const allOrders = [...(batch.orders || []), ...eligible.filter((order) => !currentIds.has(batchOrderId(order)))];
    const checkedIds = selectedIds || new Set(currentIds);
    const dialog = openOrgDialog("调整备货单订单", `
      <form id="batchAdjustForm">
        <p>仅调整订单与当前未完成备货单的关联，不修改订单状态、商品快照、数量或金额。</p>
        ${table(["保留/加入", "订单编号", "单位编码", "单位", "状态", "当前关系"], allOrders.map((order) => `
          <tr><td><input type="checkbox" name="orderId" value="${html(batchOrderId(order))}" ${checkedIds.has(batchOrderId(order)) ? "checked" : ""} /></td><td>${html(order.order_no)}</td><td>${html(order.unit_code || "--")}</td><td>${html(order.unit_name_snapshot)}</td><td>${statusTag(order.status)}</td><td>${currentIds.has(batchOrderId(order)) ? "当前备货单" : "待整理"}</td></tr>
        `), "没有可调整订单")}
        <p id="batchAdjustError" class="error-inline" hidden></p>
        <div class="page-toolbar dialog-actions"><button id="cancelBatchAdjust" class="secondary-button" type="button">取消</button><button class="primary-link" type="submit">查看并确认</button></div>
      </form>
    `);
    $("cancelBatchAdjust").addEventListener("click", dialog.close);
    $("batchAdjustForm").addEventListener("submit", (event) => {
      event.preventDefault();
      const selected = new Set(new FormData(event.currentTarget).getAll("orderId").map(String));
      const addIds = Array.from(selected).filter((id) => !currentIds.has(id));
      const removeIds = Array.from(currentIds).filter((id) => !selected.has(id));
      if (!addIds.length && !removeIds.length) {
        $("batchAdjustError").textContent = "订单选择没有变化";
        $("batchAdjustError").hidden = false;
        return;
      }
      const resultOrders = allOrders.filter((order) => selected.has(batchOrderId(order)));
      const unitIds = new Set(resultOrders.map((order) => order.unit_id));
      const first = resultOrders[0] || batch.orders?.[0] || {};
      dialog.close();
      batchReviewDialog({
        title: "即将调整备货需求",
        group: {
          unit_code: unitIds.size === 1 ? first.unit_code : "多个",
          unit_name: unitIds.size === 1 ? first.unit_name_snapshot : "多个单位",
          product_count: "--",
        },
        targetLabel: batch.batch_no,
        orders: resultOrders,
        warning: resultOrders.length ? `将加入 ${num(addIds.length)} 张、移出 ${num(removeIds.length)} 张订单。` : "移出全部订单后，该空备货单将安全归档。",
        confirmLabel: "确认调整",
        onCancel: () => openBatchAdjustDialog(batch, eligible, selected),
        onComplete: () => loadBatchDetail(batch.id),
        onConfirm: async () => {
          await api(`/api/v1/admin/batches/${batch.id}/orders`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              add_order_ids: addIds,
              remove_order_ids: removeIds,
              expected_version: batch.version,
              client_request_id: requestId("batch-adjust"),
            }),
          });
          toast("备货单订单已调整");
        },
      });
    });
  }

  function batchRow(batch) {
    return `<tr data-batch-id="${batch.id}" data-batch-version="${html(batch.version)}"><td><input type="checkbox" data-batch-select="${batch.id}" aria-label="选择备货单 ${html(batch.batch_no)}" ${state.selectedBatchIds.has(batch.id) ? "checked" : ""} /></td><td>${html(batch.batch_no)}</td><td>${html(batchDisplayName(batch))}</td><td>${html(String(batch.unit_codes || "").split(",").filter(Boolean).sort().join("、") || "--")}</td><td>${dateTime(batch.created_at)}</td><td>${num(batch.order_count)}</td><td>${num(batch.unit_count)}</td><td>${num(batch.product_count)}</td><td>${html(deliveryBatchStatus(batch.status))}</td><td><a class="table-action" href="/admin/batches/${batch.id}">查看</a> <a class="table-action" href="/api/v1/admin/batches/${batch.id}/picking-list.xlsx">导出</a> ${batch.status === "closed" ? `<button class="table-action primary" data-generate-outbound="${batch.id}">生成出库单</button>` : ""} ${batch.status === "open" ? `<button class="table-action primary" data-batch-complete="${batch.id}" data-version="${batch.version}">完成备货</button><button class="table-action danger" data-batch-archive="${batch.id}">归档</button>` : ""}</td></tr>`;
  }

  function batchListMarkup(items) {
    return table([`<input id="selectAllBatches" type="checkbox" aria-label="全选当前页备货单" />`, "备货单号", "单位 / 名称", "单位编码", "创建时间", "订单", "单位", "食材", "状态", "操作"], items.map(batchRow), "暂无备货单");
  }

  function renderBatchSelection() {
    const items = state.batchItems;
    const selected = Array.from(state.selectedBatchIds);
    $("batchSelectionSummary").textContent = `已选择 ${selected.length} 张备货单`;
    $("batchBulkExport").disabled = selected.length === 0;
    const selectAll = $("selectAllBatches");
    if (selectAll) {
      selectAll.checked = items.length > 0 && selected.length === items.length;
      selectAll.indeterminate = selected.length > 0 && selected.length < items.length;
    }
  }

  function patchKeyedRows(target, items, rowSelector, idKey, versionKey, rowMarkup, versionOf, listMarkup) {
    const tbody = target.querySelector("tbody");
    if (!tbody || !items.length) {
      target.innerHTML = listMarkup(items);
      return;
    }
    const existing = new Map(Array.from(tbody.querySelectorAll(rowSelector)).map((row) => [row.dataset[idKey], row]));
    let cursor = tbody.firstElementChild;
    items.forEach((item) => {
      let row = existing.get(item.id);
      if (!row || row.dataset[versionKey] !== String(versionOf(item))) {
        const template = document.createElement("template");
        template.innerHTML = rowMarkup(item).trim();
        const replacement = template.content.firstElementChild;
        if (row) row.replaceWith(replacement);
        row = replacement;
      }
      if (row !== cursor) tbody.insertBefore(row, cursor);
      cursor = row.nextElementSibling;
      existing.delete(item.id);
    });
    existing.forEach((row) => row.remove());
  }

  function renderBatchList(items) {
    const target = $("batchRealtimeList");
    if (!target) return;
    state.batchItems = items;
    state.selectedBatchIds = new Set(Array.from(state.selectedBatchIds).filter((id) => items.some((batch) => batch.id === id)));
    patchKeyedRows(target, items, "[data-batch-id]", "batchId", "batchVersion", batchRow, (batch) => batch.version, batchListMarkup);
    renderBatchSelection();
    bindBatchListEvents(target);
  }

  async function refreshBatchListIncrementally() {
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    ["date_from", "date_to"].forEach((key) => { if (params.get(key)) query.set(key, params.get(key)); });
    const data = await fetchRealtime(`/api/v1/admin/batches?${query.toString()}`, "batches");
    if (!data) return false;
    renderBatchList(data.items || []);
    return true;
  }

  function bindBatchListEvents(target) {
    if (target.dataset.bound === "1") return;
    target.dataset.bound = "1";
    target.addEventListener("change", (event) => {
      const checkbox = event.target.closest?.("[data-batch-select]");
      if (checkbox) {
        if (checkbox.checked) state.selectedBatchIds.add(checkbox.dataset.batchSelect); else state.selectedBatchIds.delete(checkbox.dataset.batchSelect);
        renderBatchSelection();
        return;
      }
      if (event.target.id === "selectAllBatches") {
        state.batchItems.forEach((batch) => { if (event.target.checked) state.selectedBatchIds.add(batch.id); else state.selectedBatchIds.delete(batch.id); });
        target.querySelectorAll("[data-batch-select]").forEach((input) => { input.checked = event.target.checked; });
        renderBatchSelection();
      }
    });
    target.addEventListener("click", async (event) => {
      const button = event.target.closest?.("[data-batch-complete], [data-batch-archive], [data-generate-outbound]");
      if (!button) return;
      event.preventDefault();
      if (button.dataset.batchComplete) {
        if (!confirm("确认完成备货吗？完成后可按单位生成出库单，但不会自动发货。")) return;
        button.disabled = true;
        try {
          await api(`/api/v1/admin/batches/${button.dataset.batchComplete}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status: "closed", expected_version: Number(button.dataset.version) }) });
          toast("备货已完成");
          await refreshBatchListIncrementally();
        } catch (error) { toast(error.message || "完成备货失败"); button.disabled = false; }
        return;
      }
      if (button.dataset.batchArchive) {
        if (!confirm("确认归档这张备货单吗？订单和历史记录不会删除，订单可重新组织备货。")) return;
        button.disabled = true;
        try {
          await api(`/api/v1/admin/batches/${button.dataset.batchArchive}`, { method: "DELETE" });
          toast("备货单已归档");
          await refreshBatchListIncrementally();
        } catch (error) { toast(error.message || "归档备货单失败"); button.disabled = false; }
        return;
      }
      if (!confirm("确认按单位生成出库单吗？重复操作不会重复生成。")) return;
      button.disabled = true;
      const label = button.textContent;
      button.textContent = "生成中";
      try {
        const result = await api(`/api/v1/admin/outbounds/from-batch/${button.dataset.generateOutbound}`, { method: "POST" });
        toast(`已生成 ${num(result.created_count)} 张出库单`);
        window.location.assign("/admin/outbounds");
      } catch (error) { toast(error.message || "生成出库单失败"); button.disabled = false; button.textContent = label; }
    });
  }

  async function loadBatches() {
    pageShell("备货单", "把多笔订单汇总成一张备货清单");
    const params = new URLSearchParams(window.location.search);
    const dateFrom = params.get("date_from") || "";
    const dateTo = params.get("date_to") || "";
    const query = new URLSearchParams();
    if (dateFrom) query.set("date_from", dateFrom);
    if (dateTo) query.set("date_to", dateTo);
    const [batchData, eligibleData, workbenchData] = await Promise.all([
      api(`/api/v1/admin/batches?${query.toString()}`),
      api("/api/v1/admin/batches/eligible-orders"),
      api("/api/v1/admin/batches/unit-workbench"),
    ]);
    const batches = batchData.items || [];
    const eligible = eligibleData.items || [];
    const workbench = workbenchData.items || [];
    state.batchWorkbench = workbench;
    const preselectIds = params.getAll("preselect");
    const eligibleIds = new Set(eligible.map((order) => order.id));
    const missingPreselect = preselectIds.filter((id) => !eligibleIds.has(id));
    content().innerHTML += `
      <article class="panel section-panel batch-workbench">
        <div class="panel-header"><div><h2>按单位整理待备货需求</h2><p>优先把同一单位的新订单追加到现有未完成备货单；多张未完成单可按订单安全整理。</p></div></div>
        ${workbench.length ? workbench.map(batchWorkbenchGroup).join("") : empty("当前没有需要整理的待备货订单或重复备货单")}
      </article>
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>手动选择订单</h2><p>用于跨单位或特殊分批场景；日常操作请优先使用上方按单位建议。</p></div></div>
        <form id="batchCreateForm">
          <div class="form-grid compact">
            <label class="form-field"><span>备货单名称</span><input name="name" type="text" required placeholder="例如：上午第一批" /></label>
            <label class="form-field"><span>备注</span><input name="note" type="text" placeholder="选填" /></label>
          </div>
          ${table(["选择", "订单编号", "单位编码", "单位", "配送点", "状态", "下单时间", "金额"], eligible.map((order) => `
            <tr><td><input type="checkbox" name="orderId" value="${order.id}" aria-label="选择订单${html(order.order_no)}" /></td><td>${html(order.order_no)}</td><td>${html(order.unit_code || "--")}</td><td>${html(order.unit_name_snapshot)}</td><td>${html(order.delivery_point_snapshot || "--")}</td><td>${statusTag(order.status)}</td><td>${dateTime(order.created_at)}</td><td>${money(order.total_cents)}</td></tr>
          `), "当前没有可生成备货单的订单")}
          <div class="page-toolbar"><button class="primary-link" type="submit" ${eligible.length ? "" : "disabled"}>生成备货单</button></div>
        </form>
      </article>
      <article class="panel table-panel">
        <div class="panel-header"><div><h2>备货单记录</h2><p>按日期查看、导出或完成备货；完成后可按单位生成出库单。</p></div></div>
        <div class="page-toolbar"><input id="batchDateFrom" type="date" value="${html(dateFrom)}" aria-label="开始日期" /><input id="batchDateTo" type="date" value="${html(dateTo)}" aria-label="结束日期" /><button id="batchFilterButton" class="table-action primary" type="button">查询</button><button id="batchClearFilterButton" class="table-action" type="button">清除筛选</button><button id="batchBulkExport" class="table-action" type="button" disabled>批量导出</button></div>
        <div id="batchSelectionSummary" class="order-result-summary">已选择 0 张备货单</div>
        <div id="batchRealtimeList"></div>
      </article>`;
    if (preselectIds.length) {
      document.querySelectorAll('input[name="orderId"]').forEach((checkbox) => { checkbox.checked = preselectIds.includes(checkbox.value); });
      const nameInput = document.querySelector('#batchCreateForm input[name="name"]');
      if (nameInput && !nameInput.value) nameInput.value = new Date().toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) + " 备货";
    }
    if (missingPreselect.length) toast("部分订单已不能加入备货单，请刷新后检查订单状态。");
    renderBatchList(batches);
    $("batchBulkExport")?.addEventListener("click", () => {
      const query = new URLSearchParams();
      Array.from(state.selectedBatchIds).forEach((id) => query.append("batch_ids", id));
      window.location.assign(`/api/v1/admin/batches/bulk.xlsx?${query.toString()}`);
    });
    $("batchFilterButton")?.addEventListener("click", () => {
      const next = new URLSearchParams();
      if ($("batchDateFrom").value) next.set("date_from", $("batchDateFrom").value);
      if ($("batchDateTo").value) next.set("date_to", $("batchDateTo").value);
      window.location.assign(`/admin/batches${next.toString() ? `?${next.toString()}` : ""}`);
    });
    $("batchClearFilterButton")?.addEventListener("click", () => { window.location.assign("/admin/batches"); });
    $("batchCreateForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      const orderIds = data.getAll("orderId").map(String);
      if (!orderIds.length) return toast("请至少选择一笔订单");
      const selectedOrders = eligible.filter((order) => orderIds.includes(order.id));
      const unitIds = new Set(selectedOrders.map((order) => order.unit_id));
      const first = selectedOrders[0] || {};
      const reviewGroup = {
        unit_code: unitIds.size === 1 ? first.unit_code : "多个",
        unit_name: unitIds.size === 1 ? first.unit_name_snapshot : "多个单位",
        product_count: "--",
      };
      batchReviewDialog({
        title: "即将手动生成备货单",
        group: reviewGroup,
        targetLabel: "新备货单",
        orders: selectedOrders,
        warning: unitIds.size > 1 ? "当前选择包含多个单位，将生成包含多个单位的备货单。" : "",
        confirmLabel: "确认生成",
        onConfirm: async () => {
          const batch = await api("/api/v1/admin/batches", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              name: String(data.get("name") || "").trim(),
              note: String(data.get("note") || "").trim(),
              order_ids: orderIds,
              client_request_id: requestId("batch-create-manual"),
            }),
          });
          toast(`已生成 ${batch.batch_no}`);
        },
      });
    });
  }

  function batchUnitTable(summary) {
    const rows = (summary.by_unit || []).flatMap((unit) => (unit.items || []).map((item, index) => `
      <tr><td>${index === 0 ? html(unit.unit_code || "--") : ""}</td><td>${index === 0 ? html(unit.unit_name) : ""}</td><td>${index === 0 ? html(unit.delivery_point || "--") : ""}</td><td>${html(item.category || "--")}</td><td>${html(item.product_name)}</td><td>${qty(item.quantity)} ${html(item.unit)}</td></tr>
    `));
    return table(["单位编码", "单位", "配送点", "分类", "食材", "需求数量"], rows, "该批次暂无有效订单");
  }

  function batchProductTable(summary) {
    const rows = (summary.by_product || []).map((item) => `
      <tr><td>${html(item.category || "--")}</td><td>${html(item.product_name)}</td><td>${html(item.spec || "--")}</td><td><strong>${qty(item.total_quantity)} ${html(item.unit)}</strong></td><td>${(item.unit_breakdown || []).map((unit) => `${html(unit.unit_code || "--")} · ${html(unit.unit_name)}：${qty(unit.quantity)} ${html(item.unit)}`).join("<br>")}</td></tr>
    `);
    return table(["分类", "食材", "规格", "总需求", "单位分解"], rows, "该批次暂无有效订单");
  }

  async function loadBatchDetail(batchId, selectedTab = "product") {
    pageShell("备货单详情", "按食材汇总总需求，并按单位核对明细");
    const [batch, summary, eligibleData, workbenchData] = await Promise.all([
      api(`/api/v1/admin/batches/${batchId}`),
      api(`/api/v1/admin/batches/${batchId}/summary`),
      api("/api/v1/admin/batches/eligible-orders"),
      api("/api/v1/admin/batches/unit-workbench"),
    ]);
    const eligible = eligibleData.items || [];
    state.batchWorkbench = workbenchData.items || [];
    const duplicateGroups = state.batchWorkbench.filter((group) => group.open_batch_count > 1 && (group.open_batches || []).some((item) => item.id === batch.id));
    const canPick = (batch.orders || []).some((order) => ["accepted", "preparing"].includes(order.status));
    const downloadOrDisabled = (enabled, href, label, reason) => enabled
      ? `<a class="primary-link secondary" href="${href}">${label}</a>`
      : `<span class="secondary-button disabled" title="${html(reason)}">${label}</span>`;
    content().innerHTML += `
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>${html(batch.batch_no)} · ${html(batchDisplayName(batch))}</h2><p>${num(summary.order_count)} 笔订单 · ${num(summary.unit_count)} 个单位 · ${num(summary.product_count)} 类食材</p></div><span class="status-tag">${html(deliveryBatchStatus(batch.status))}</span></div>
        <dl class="status-list detail-list"><dt>备货单备注</dt><dd>${html(batch.note || "无")}</dd><dt>创建时间</dt><dd>${dateTime(batch.created_at)}</dd></dl>
        <div class="page-toolbar">
          ${downloadOrDisabled(canPick, `/api/v1/admin/batches/${batch.id}/picking-list.xlsx`, "导出备货单", "该备货单暂无有效备货需求")}
          ${batch.status === "closed" ? `<button class="primary-link" data-generate-outbound-detail="${batch.id}">生成出库单</button>` : ""}
          ${batch.status === "open" ? `<button class="secondary-button" id="adjustBatchOrders" type="button">调整订单</button>${duplicateGroups.map((group) => `<button class="secondary-button" type="button" data-batch-v2-action="reconcile" data-unit-id="${html(group.unit_id)}">整理 ${html(group.unit_code || "--")} 同单位备货单</button>`).join("")}<button class="secondary-button" data-batch-complete-detail="${batch.id}" data-version="${batch.version}">完成备货</button><button class="danger-button" data-batch-archive-detail="${batch.id}">归档备货单</button>` : ""}
          <a class="table-action" href="/admin/batches">返回备货单列表</a>
        </div>
      </article>
      <article class="panel table-panel">
        <div class="page-toolbar"><button class="table-action ${selectedTab === "unit" ? "primary" : ""}" data-batch-tab="unit">按单位</button><button class="table-action ${selectedTab === "product" ? "primary" : ""}" data-batch-tab="product">按食材</button></div>
        <div id="batchSummaryBody">${selectedTab === "unit" ? batchUnitTable(summary) : batchProductTable(summary)}</div>
      </article>
      <article class="panel table-panel"><div class="panel-header"><div><h2>备货单订单</h2><p>软删除订单不会进入正常备货汇总和单据。</p></div></div>${table(["订单编号", "单位编码", "单位", "状态", "下单时间", "金额"], (batch.orders || []).map((order) => `<tr><td><a href="/admin/orders/${order.id}">${html(order.order_no)}</a></td><td>${html(order.unit_code || "--")}</td><td>${html(order.unit_name_snapshot)}</td><td>${statusTag(order.status)}</td><td>${dateTime(order.created_at)}</td><td>${money(order.total_cents)}</td></tr>`), "暂无订单")}</article>`;
    document.querySelectorAll("[data-batch-tab]").forEach((button) => button.addEventListener("click", () => loadBatchDetail(batchId, button.dataset.batchTab)));
    $("adjustBatchOrders")?.addEventListener("click", () => openBatchAdjustDialog(batch, eligible));
    document.querySelectorAll("[data-batch-complete-detail]").forEach((button) => button.addEventListener("click", async () => {
      if (!confirm("确认完成备货吗？完成后可按单位生成出库单，但不会自动发货。")) return;
      button.disabled = true;
      try {
        await api(`/api/v1/admin/batches/${batchId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status: "closed", expected_version: Number(button.dataset.version) }) });
        toast("备货已完成");
        await loadBatchDetail(batchId, selectedTab);
      } catch (error) { toast(error.message || "完成备货失败"); button.disabled = false; }
    }));
    document.querySelectorAll("[data-batch-archive-detail]").forEach((button) => button.addEventListener("click", async () => {
      if (!confirm("确认归档这张备货单吗？订单和历史记录不会删除。")) return;
      button.disabled = true;
      try { await api(`/api/v1/admin/batches/${batchId}`, { method: "DELETE" }); toast("备货单已归档"); window.location.assign("/admin/batches"); }
      catch (error) { toast(error.message || "归档备货单失败"); button.disabled = false; }
    }));
    document.querySelectorAll("[data-generate-outbound-detail]").forEach((button) => button.addEventListener("click", async () => {
      if (!confirm("确认按单位生成出库单吗？重复操作不会重复生成。")) return;
      button.disabled = true;
      const label = button.textContent;
      button.textContent = "生成中";
      try {
        const result = await api(`/api/v1/admin/outbounds/from-batch/${button.dataset.generateOutboundDetail}`, { method: "POST" });
        toast(`已生成 ${num(result.created_count)} 张出库单`);
        window.location.assign("/admin/outbounds");
      } catch (error) { toast(error.message || "生成出库单失败"); button.disabled = false; button.textContent = label; }
    }));
  }

  function outboundStatus(status) {
    return ({ pending: "待完成", shipped: "已完成", archived: "已归档" })[status] || "未知状态";
  }

  function outboundStatusTag(status) {
    return `<span class="status-tag status-${html(status || "unknown")}">${html(outboundStatus(status))}</span>`;
  }

  function patchOutboundDetailAfterComplete(outbound) {
    const route = currentRoute();
    if (!route.startsWith("/admin/outbounds/") || route.split("/").pop() !== outbound.id) return false;
    const status = document.querySelector("[data-outbound-detail-status]");
    if (!status) return false;
    status.innerHTML = outboundStatusTag(outbound.status);
    document.querySelector("[data-outbound-complete-section]")?.remove();
    (outbound.orders || []).forEach((order) => {
      const row = document.querySelector(`[data-outbound-detail-order-id="${CSS.escape(order.id)}"]`);
      const orderStatus = row?.querySelector("[data-outbound-detail-order-status]");
      if (orderStatus) orderStatus.innerHTML = statusTag(order.status);
    });
    return true;
  }

  async function openOutboundCompleteReview(button) {
    const label = button.textContent;
    button.disabled = true;
    button.textContent = "加载中...";
    try {
      const outbound = await api(`/api/v1/admin/outbounds/${button.dataset.outboundComplete}`);
      if (outbound.status !== "pending") {
        throw new Error("出库单已被其他管理员处理，请刷新后重试。");
      }
      const dialog = openOrgDialog("完成出库", `
        <div class="batch-review">
          <dl class="status-list detail-list">
            <dt>单位</dt><dd>${html(outbound.unit_code || "--")} · ${html(outbound.unit_name_snapshot || "--")}</dd>
            <dt>出库单</dt><dd>${html(outbound.outbound_no)}</dd>
            <dt>关联订单</dt><dd>${num(outbound.order_count)} 张</dd>
            <dt>食材</dt><dd>${num(outbound.product_count)} 种</dd>
          </dl>
          <p>完成后本出库单及其关联订单将更新为已完成；无需上传发货照片，已有历史照片不会删除。</p>
          <div class="page-toolbar dialog-actions"><button id="cancelOutboundComplete" class="secondary-button" type="button">取消</button><button id="confirmOutboundComplete" class="primary-link" type="button">确认完成</button></div>
        </div>
      `);
      $("cancelOutboundComplete").addEventListener("click", dialog.close);
      $("confirmOutboundComplete").addEventListener("click", async () => {
        const confirmButton = $("confirmOutboundComplete");
        formButtonBusy(confirmButton, true, "确认完成");
        try {
          const result = await api(`/api/v1/admin/outbounds/${outbound.id}/complete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              expected_version: Number(outbound.version || 0),
              client_request_id: requestId("web-outbound-complete"),
            }),
          });
          dialog.close();
          toast(`出库单 ${result.outbound_no || ""} 已完成，无需上传照片`.trim());
          if (!patchOutboundDetailAfterComplete(result)) {
            await reconcileAffectedResources(["outbounds", "orders", "dashboard", "quota"]);
          }
        } catch (error) {
          if (error.status === 409) {
            dialog.close();
            await loadCurrent(true, "conflict");
            toast("出库单已被其他管理员修改，已加载最新状态，请重新确认。");
            return;
          }
          toast(error.message || "完成出库失败，请刷新后重试");
          formButtonBusy(confirmButton, false, "确认完成");
        }
      });
    } catch (error) {
      toast(error.message || "无法加载出库单明细，请刷新后重试");
    } finally {
      button.disabled = false;
      button.textContent = label;
    }
  }

  function outboundRow(item) {
    return `<tr data-outbound-id="${item.id}" data-outbound-version="${html(item.version)}"><td><input type="checkbox" data-outbound-select="${item.id}" aria-label="选择出库单 ${html(item.outbound_no)}" ${state.selectedOutboundIds.has(item.id) ? "checked" : ""} /></td><td>${html(item.outbound_no)}</td><td>${html(item.unit_code || "--")}</td><td>${html(item.unit_name_snapshot)}</td><td>${html(item.batch_no)}</td><td>${dateTime(item.created_at)}</td><td>${num(item.order_count)}</td><td>${num(item.product_count)}</td><td>${outboundStatusTag(item.status)}</td><td><a class="table-action" href="/admin/outbounds/${item.id}">查看</a> <a class="table-action" href="/api/v1/admin/outbounds/${item.id}/export.xlsx">导出</a> ${item.status === "pending" ? `<button class="table-action primary" type="button" data-outbound-complete="${item.id}">完成</button>` : ""}</td></tr>`;
  }

  function outboundListMarkup(items) {
    return table([`<input id="selectAllOutbounds" type="checkbox" aria-label="全选当前页出库单" />`, "出库单号", "单位编码", "单位", "来源备货单", "日期", "订单数量", "食材种类", "状态", "操作"], items.map(outboundRow), "暂无出库单。请先在已完成备货单中生成出库单。");
  }

  function renderOutboundSelection() {
    const items = state.outboundItems;
    const selected = Array.from(state.selectedOutboundIds);
    $("outboundSelectionSummary").textContent = `已选择 ${selected.length} 张出库单`;
    $("outboundBulkExport").disabled = selected.length === 0;
    const all = $("selectAllOutbounds");
    if (all) {
      all.checked = items.length > 0 && selected.length === items.length;
      all.indeterminate = selected.length > 0 && selected.length < items.length;
    }
  }

  function renderOutboundList(items) {
    const target = $("outboundRealtimeList");
    if (!target) return;
    state.outboundItems = items;
    state.selectedOutboundIds = new Set(Array.from(state.selectedOutboundIds).filter((id) => items.some((item) => item.id === id)));
    patchKeyedRows(target, items, "[data-outbound-id]", "outboundId", "outboundVersion", outboundRow, (item) => item.version, outboundListMarkup);
    renderOutboundSelection();
    bindOutboundListEvents(target);
  }

  async function refreshOutboundListIncrementally() {
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    ["date_from", "date_to", "status"].forEach((key) => { if (params.get(key)) query.set(key, params.get(key)); });
    const data = await fetchRealtime(`/api/v1/admin/outbounds?${query.toString()}`, "outbounds");
    if (!data) return false;
    renderOutboundList(data.items || []);
    return true;
  }

  function bindOutboundListEvents(target) {
    if (target.dataset.bound === "1") return;
    target.dataset.bound = "1";
    target.addEventListener("change", (event) => {
      const checkbox = event.target.closest?.("[data-outbound-select]");
      if (checkbox) {
        if (checkbox.checked) state.selectedOutboundIds.add(checkbox.dataset.outboundSelect); else state.selectedOutboundIds.delete(checkbox.dataset.outboundSelect);
        renderOutboundSelection();
        return;
      }
      if (event.target.id === "selectAllOutbounds") {
        state.outboundItems.forEach((item) => { if (event.target.checked) state.selectedOutboundIds.add(item.id); else state.selectedOutboundIds.delete(item.id); });
        target.querySelectorAll("[data-outbound-select]").forEach((input) => { input.checked = event.target.checked; });
        renderOutboundSelection();
      }
    });
    target.addEventListener("click", (event) => {
      const button = event.target.closest?.("[data-outbound-complete]");
      if (!button) return;
      event.preventDefault();
      event.stopPropagation();
      if (button.dataset.outboundCompleteOpening === "1") return;
      button.dataset.outboundCompleteOpening = "1";
      openOutboundCompleteReview(button)
        .catch((error) => {
          reportClientError(error.message || "完成出库交互失败", "/api/v1/admin/outbounds/complete", { action: "outbound_direct_complete" });
          toast(error.message || "完成出库失败，请刷新后重试");
        })
        .finally(() => { delete button.dataset.outboundCompleteOpening; });
    });
  }

  async function loadOutbounds() {
    pageShell("出库单", "按单位查看、导出和完成出库");
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    ["date_from", "date_to", "status"].forEach((key) => { if (params.get(key)) query.set(key, params.get(key)); });
    const data = await api(`/api/v1/admin/outbounds?${query.toString()}`);
    const outbounds = data.items || [];
    content().innerHTML += `
      <article class="panel table-panel">
        <div class="panel-header"><div><h2>单位出库单</h2><p>每个单位独立一张出库单；发货仅影响该单位对应订单。</p></div></div>
        <div class="page-toolbar">
          <input id="outboundDateFrom" type="date" value="${html(params.get("date_from") || "")}" aria-label="开始日期" />
          <input id="outboundDateTo" type="date" value="${html(params.get("date_to") || "")}" aria-label="结束日期" />
          <select id="outboundStatus" aria-label="出库单状态"><option value="" ${!params.get("status") ? "selected" : ""}>全部有效单据</option><option value="pending" ${params.get("status") === "pending" ? "selected" : ""}>待完成</option><option value="shipped" ${params.get("status") === "shipped" ? "selected" : ""}>已完成</option><option value="archived" ${params.get("status") === "archived" ? "selected" : ""}>已归档</option></select>
          <button id="outboundFilterButton" class="table-action primary" type="button">查询</button>
          <button id="outboundClearFilterButton" class="table-action" type="button">清除筛选</button>
          <button id="outboundBulkExport" class="table-action" type="button" disabled>批量导出</button>
        </div>
        <div id="outboundSelectionSummary" class="order-result-summary">已选择 0 张出库单</div>
        <div id="outboundRealtimeList"></div>
      </article>`;
    renderOutboundList(outbounds);
    $("outboundBulkExport").addEventListener("click", () => {
      const exportQuery = new URLSearchParams();
      Array.from(state.selectedOutboundIds).forEach((id) => exportQuery.append("outbound_ids", id));
      window.location.assign(`/api/v1/admin/outbounds/bulk.zip?${exportQuery.toString()}`);
    });
    $("outboundFilterButton").addEventListener("click", () => {
      const next = new URLSearchParams();
      if ($("outboundDateFrom").value) next.set("date_from", $("outboundDateFrom").value);
      if ($("outboundDateTo").value) next.set("date_to", $("outboundDateTo").value);
      if ($("outboundStatus").value) next.set("status", $("outboundStatus").value);
      window.location.assign(`/admin/outbounds${next.toString() ? `?${next.toString()}` : ""}`);
    });
    $("outboundClearFilterButton").addEventListener("click", () => window.location.assign("/admin/outbounds"));
  }

  async function loadOutboundDetail(outboundId) {
    pageShell("出库单详情", "按单位查看本次配送食材和完成状态");
    const outbound = await api(`/api/v1/admin/outbounds/${outboundId}`);
    const status = outboundStatus(outbound.status);
    content().innerHTML += `
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>三公鲜配出库单</h2><p>${html(outbound.unit_code || "--")} · ${html(outbound.unit_name_snapshot)} · ${html(outbound.outbound_no)}</p></div><div data-outbound-detail-status>${outboundStatusTag(outbound.status)}</div></div>
        <dl class="status-list detail-list"><dt>单位编码</dt><dd>${html(outbound.unit_code || "--")}</dd><dt>单位</dt><dd>${html(outbound.unit_name_snapshot)}</dd><dt>配送点</dt><dd>${html(outbound.delivery_point_snapshot || "未填写")}</dd><dt>来源备货单</dt><dd><a href="/admin/batches/${outbound.preparation_batch_id}">${html(outbound.batch_no)}</a></dd><dt>生成时间</dt><dd>${dateTime(outbound.created_at)}</dd></dl>
        <div class="page-toolbar"><a class="primary-link secondary" href="/api/v1/admin/outbounds/${outbound.id}/export.xlsx">导出出库单</a>${outbound.status === "pending" ? `<button class="danger-button" id="archiveOutboundButton" type="button">归档出库单</button>` : ""}<a class="table-action" href="/admin/outbounds">返回出库单列表</a></div>
      </article>
      <article class="panel table-panel"><div class="panel-header"><div><h2>配送食材</h2><p>只包含该单位在来源备货单中的订单明细。</p></div></div>${table(["序号", "食品分类", "食材名称", "规格", "计量单位", "需求数量"], (outbound.lines || []).map((item, index) => `<tr><td>${index + 1}</td><td>${html(item.category || "其他")}</td><td>${html(item.product_name)}</td><td>${html(item.spec || "--")}</td><td>${html(item.unit)}</td><td><strong>${qty(item.quantity)}</strong></td></tr>`), "暂无配送食材")}</article>
      <article class="panel table-panel"><div class="panel-header"><div><h2>关联订单</h2><p>完成出库只会更新以下订单；已有历史发货照片仍可保留查看。</p></div></div>${table(["订单编号", "状态", "下单时间", "历史照片"], (outbound.orders || []).map((order) => `<tr data-outbound-detail-order-id="${order.id}"><td><a href="/admin/orders/${order.id}">${html(order.order_no)}</a></td><td data-outbound-detail-order-status>${statusTag(order.status)}</td><td>${dateTime(order.created_at)}</td><td>${Number(order.shipping_photo_count || 0) ? `<a class="table-action" href="/admin/orders/${order.id}">查看 ${num(order.shipping_photo_count)} 张</a>` : "无"}</td></tr>`), "暂无订单")}</article>
      ${outbound.status === "pending" ? `<article class="panel section-panel" data-outbound-complete-section><div class="panel-header"><div><h2>完成出库</h2><p>完成后本出库单及其关联订单将更新为已完成，无需上传发货照片。</p></div></div><div class="page-toolbar"><button class="primary-link" data-outbound-complete="${outbound.id}" type="button">完成</button></div></article>` : ""}`;
    $("archiveOutboundButton")?.addEventListener("click", async () => {
      if (!confirm("确认归档这张待发货出库单吗？订单、备货单和历史记录不会删除。")) return;
      try { await api(`/api/v1/admin/outbounds/${outbound.id}`, { method: "DELETE" }); toast("出库单已归档"); window.location.assign("/admin/outbounds"); }
      catch (error) { toast(error.message || "归档失败，请刷新后重试"); }
    });
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
    pageShell("单位配送", "按单位查看当前待配送清单");
    const data = await api("/api/v1/admin/delivery-sheets");
    const units = data.units || data.items || data || [];
    content().innerHTML += `<div class="page-toolbar"><a class="primary-link" href="/api/v1/admin/delivery-sheets/export.xlsx">导出配送 Excel</a></div>`;
    content().innerHTML += units.length ? units.map((unit) => {
      const orders = unit.orders || [];
      const rows = orders.flatMap((order) => (order.items || []).map((item) => `
        <tr>
          <td>${html(order.order_no || "--")}</td>
          <td>${statusTag(order.status)}</td>
          <td>${html(item.product_name || item.name || "--")}</td>
          <td>${html(item.spec || "--")}</td>
          <td>${qty(item.actual_quantity || item.quantity || item.requested_quantity)} ${html(item.unit || "")}</td>
          <td>${html(item.adjustment_reason || "无")}</td>
        </tr>
      `));
      const totalItems = rows.length;
      return `
        <article class="panel section-panel">
          <div class="panel-header">
            <div>
              <h2>${html(unit.unit_name || unit.name)}</h2>
              <p>${html(unit.delivery_point || unit.default_delivery_point || "未填写配送点")} · ${num(orders.length)} 单 · ${num(totalItems)} 项食材</p>
            </div>
          </div>
          ${table(["订单编号", "状态", "食材", "规格", "配送数量", "备注"], rows, "暂无配送明细")}
        </article>
      `;
    }).join("") : empty("暂无配送清单");
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

  async function loadSystemLogs() {
    pageShell("系统日志", "用户操作、错误记录和服务器日志下载");
    const params = new URLSearchParams(window.location.search);
    const query = new URLSearchParams();
    ["start_date", "end_date", "actor_role", "result", "path", "action", "q"].forEach((key) => {
      if (params.get(key)) query.set(key, params.get(key));
    });
    const data = await api(`/api/v1/admin/system/audit-logs?${query.toString()}`);
    const rows = data.items || [];
    const exportQuery = query.toString();
    content().innerHTML += `
      <article class="panel section-panel">
        <div class="panel-header"><div><h2>筛选日志</h2><p>只记录必要操作信息，不展示密码、Token 和 Cookie</p></div></div>
        <form id="auditFilterForm" class="form-grid compact">
          <label class="form-field"><span>开始日期</span><input name="start_date" type="date" value="${html(params.get("start_date") || "")}" /></label>
          <label class="form-field"><span>结束日期</span><input name="end_date" type="date" value="${html(params.get("end_date") || "")}" /></label>
          <label class="form-field"><span>角色</span><select name="actor_role"><option value="">全部</option><option value="admin" ${params.get("actor_role") === "admin" ? "selected" : ""}>管理员</option><option value="unit_user" ${params.get("actor_role") === "unit_user" ? "selected" : ""}>子单位</option><option value="anonymous" ${params.get("actor_role") === "anonymous" ? "selected" : ""}>未登录</option></select></label>
          <label class="form-field"><span>结果</span><select name="result"><option value="">全部</option><option value="success" ${params.get("result") === "success" ? "selected" : ""}>成功</option><option value="failure" ${params.get("result") === "failure" ? "selected" : ""}>失败</option></select></label>
          <label class="form-field"><span>路径</span><input name="path" type="text" value="${html(params.get("path") || "")}" placeholder="/api/v1/admin/products" /></label>
          <label class="form-field"><span>关键词</span><input name="q" type="text" value="${html(params.get("q") || "")}" placeholder="账号、路径、错误" /></label>
          <div class="page-toolbar span-2">
            <button class="primary-link" type="submit">查询日志</button>
            <a class="primary-link secondary" href="/api/v1/admin/system/audit-logs/export.csv?${exportQuery}">下载当前筛选 CSV</a>
            <a class="primary-link secondary" href="/api/v1/admin/system/audit-logs/server-log.txt">下载服务器日志</a>
          </div>
        </form>
      </article>
    `;
    content().innerHTML += table(["时间", "账号", "角色", "方法", "路径", "结果", "状态码", "错误"], rows.map((row) => `
      <tr>
        <td>${dateTime(row.created_at)}</td>
        <td>${html(row.display_name || row.username || "--")}</td>
        <td>${row.actor_role === "admin" ? "管理员" : row.actor_role === "unit_user" ? "子单位" : html(row.actor_role || "--")}</td>
        <td>${html(row.method || "--")}</td>
        <td>${html(row.path || row.action || "--")}</td>
        <td>${row.result === "success" ? "成功" : "失败"}</td>
        <td>${html(row.status_code || "--")}</td>
        <td>${html(row.error_message || "--")}</td>
      </tr>
    `), "暂无系统日志");
    $("auditFilterForm").addEventListener("submit", (event) => {
      event.preventDefault();
      const formData = new FormData(event.currentTarget);
      const next = new URLSearchParams();
      for (const [key, value] of formData.entries()) {
        const text = String(value || "").trim();
        if (text) next.set(key, text);
      }
      window.location.href = `/admin/system-logs?${next.toString()}`;
    });
  }

  async function loadInventory() {
    pageShell("库存记录", "当前库存快照");
    await loadProducts();
    setTitle("库存记录", "当前库存快照");
  }

  function announcementDateInput(value) {
    return String(value || "").replace(" ", "T").slice(0, 16);
  }

  function announcementAudienceLabel(value) {
    return ({ all: "全部", admins: "管理员", units: "全部子单位", specific_units: "指定子单位" })[value] || "全部";
  }

  function announcementStatusLabel(value) {
    return ({ draft: "草稿", published: "已发布", offline: "已下线" })[value] || "--";
  }

  function announcementFormPayload(form) {
    const data = new FormData(form);
    return {
      title: String(data.get("title") || "").trim(),
      content: String(data.get("content") || "").trim(),
      level: String(data.get("level") || "normal"),
      audience_type: String(data.get("audience_type") || "all"),
      unit_ids: data.getAll("unit_ids").map((value) => String(value)).filter(Boolean),
      is_pinned: Boolean(data.get("is_pinned")),
      publish_at: String(data.get("publish_at") || ""),
      expire_at: String(data.get("expire_at") || ""),
    };
  }

  function announcementReview(item) {
    const units = (item.units || []).map((unit) => `${unit.unit_code || "--"} · ${unit.unit_name}`).join("、") || announcementAudienceLabel(item.audience_type);
    const dialog = openOrgDialog("确认发布公告", `
      <div class="batch-review"><dl class="status-list detail-list">
        <dt>标题</dt><dd>${html(item.title)}</dd><dt>等级</dt><dd>${html(announcementLevelLabel(item.level))}</dd>
        <dt>范围</dt><dd>${html(units)}</dd><dt>发布时间</dt><dd>${html(item.display_publish_at || "立即发布")}</dd>
        <dt>失效时间</dt><dd>${html(item.display_expire_at || "长期有效")}</dd>
      </dl><p class="announcement-content">${html(item.content).replaceAll("\n", "<br>")}</p>
      <div class="page-toolbar dialog-actions"><button id="cancelAnnouncementPublish" class="secondary-button" type="button">返回修改</button><button id="confirmAnnouncementPublish" class="primary-link" type="button">确认发布</button></div></div>
    `);
    $("cancelAnnouncementPublish").addEventListener("click", dialog.close);
    $("confirmAnnouncementPublish").addEventListener("click", async (event) => {
      const button = event.currentTarget;
      formButtonBusy(button, true, "确认发布");
      try {
        await api(`/api/v1/admin/announcements/${item.id}/publish`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ expected_version: item.version, client_request_id: requestId("web-announcement-publish") }) });
        dialog.close();
        toast("公告已发布");
        await loadCurrent(true, "write");
      } catch (error) {
        toast(error.message || "发布公告失败");
        formButtonBusy(button, false, "确认发布");
      }
    });
  }

  async function openAnnouncementEditor(existing = null) {
    const units = await api("/api/v1/admin/units");
    const selected = new Set((existing?.units || []).map((unit) => unit.id));
    const editing = Boolean(existing);
    const dialog = openOrgDialog(editing ? "编辑公告" : "新建公告", `
      <form id="announcementForm"><div class="form-grid compact">
        <label class="form-field span-2"><span>标题 *</span><input name="title" maxlength="160" required value="${html(existing?.title || "")}" /></label>
        <label class="form-field span-2"><span>正文 *</span><textarea name="content" maxlength="4000" rows="7" required>${html(existing?.content || "")}</textarea></label>
        <label class="form-field"><span>公告等级</span><select name="level"><option value="normal">普通</option><option value="important">重要</option><option value="urgent">紧急</option></select></label>
        <label class="form-field"><span>公告范围</span><select name="audience_type"><option value="all">全部</option><option value="admins">管理员</option><option value="units">全部子单位</option><option value="specific_units">指定子单位</option></select></label>
        <label class="switch-field"><input name="is_pinned" type="checkbox" ${existing?.is_pinned ? "checked" : ""} /><span>置顶公告</span></label>
        <label class="form-field"><span>发布时间</span><input name="publish_at" type="datetime-local" value="${html(announcementDateInput(existing?.display_publish_at))}" /><small>留空则在发布时立即生效</small></label>
        <label class="form-field span-2"><span>失效时间</span><input name="expire_at" type="datetime-local" value="${html(announcementDateInput(existing?.display_expire_at))}" /><small>留空表示长期有效</small></label>
      </div><fieldset id="announcementUnitPicker" class="announcement-unit-picker" hidden><legend>指定子单位 *</legend>${units.map((unit) => `<label><input type="checkbox" name="unit_ids" value="${html(unit.id)}" ${selected.has(unit.id) ? "checked" : ""} />${html(unit.unit_code || "--")} · ${html(unit.unit_name)}</label>`).join("")}</fieldset>
      <p id="announcementFormError" class="error-inline" hidden></p><div class="page-toolbar dialog-actions"><button id="cancelAnnouncementEdit" class="secondary-button" type="button">取消</button><button class="secondary-button" type="submit" data-announcement-save="draft">保存草稿</button><button class="primary-link" type="submit" data-announcement-save="publish">发布</button></div></form>
    `);
    const form = $("announcementForm");
    form.level.value = existing?.level || "normal";
    form.audience_type.value = existing?.audience_type || "all";
    const syncAudience = () => { $("announcementUnitPicker").hidden = form.audience_type.value !== "specific_units"; };
    syncAudience();
    form.audience_type.addEventListener("change", syncAudience);
    $("cancelAnnouncementEdit").addEventListener("click", dialog.close);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const submit = event.submitter;
      const intent = submit?.dataset.announcementSave || "draft";
      const error = $("announcementFormError");
      const payload = announcementFormPayload(form);
      formButtonBusy(submit, true, intent === "publish" ? "发布" : "保存草稿");
      try {
        let item;
        if (editing) {
          item = await api(`/api/v1/admin/announcements/${existing.id}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...payload, expected_version: existing.version, client_request_id: requestId("web-announcement-update") }) });
        } else {
          item = await api("/api/v1/admin/announcements", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...payload, client_request_id: requestId("web-announcement-create") }) });
        }
        dialog.close();
        if (intent === "publish" && item.status !== "published") announcementReview(item);
        else {
          toast(intent === "publish" ? "公告已更新" : "公告草稿已保存");
          await loadCurrent(true, "write");
        }
      } catch (requestError) {
        error.textContent = requestError.message || "保存公告失败";
        error.hidden = false;
        formButtonBusy(submit, false, intent === "publish" ? "发布" : "保存草稿");
      }
    });
  }

  async function openAnnouncementDetail(announcementId) {
    const item = await api(`/api/v1/admin/announcements/${announcementId}`);
    openOrgDialog("公告详情", `<div class="batch-review"><dl class="status-list detail-list"><dt>等级</dt><dd>${html(announcementLevelLabel(item.level))}</dd><dt>状态</dt><dd>${html(announcementStatusLabel(item.status))}</dd><dt>发布人</dt><dd>${html(item.created_by_name || "--")}</dd><dt>发布时间</dt><dd>${html(item.display_publish_at || "--")}</dd><dt>有效期</dt><dd>${html(item.display_expire_at || "长期有效")}</dd></dl><h3>${html(item.title)}</h3><p class="announcement-content">${html(item.content).replaceAll("\n", "<br>")}</p></div>`);
  }

  async function offlineAnnouncement(item) {
    if (!confirm(`确认下线公告“${item.title}”吗？已发布历史将被保留。`)) return;
    await api(`/api/v1/admin/announcements/${item.id}/offline`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ expected_version: item.version, client_request_id: requestId("web-announcement-offline") }) });
    toast("公告已下线");
    await loadCurrent(true, "write");
  }

  function renderAnnouncementList(items) {
    const target = $("announcementList");
    if (!target) return;
    target.innerHTML = items.length ? items.map((item) => `<div class="announcement-admin-row" data-announcement-id="${html(item.id)}"><div><div class="row-head"><strong>${html(item.title)}</strong><span class="announcement-level ${html(item.level)}">${item.is_pinned ? "置顶 · " : ""}${html(announcementLevelLabel(item.level))}</span></div><div class="row-sub">${html(announcementStatusLabel(item.status))} · ${html(announcementAudienceLabel(item.audience_type))} · ${html(item.display_publish_at || "未发布")} · 更新 ${html(item.display_updated_at || "--")}</div></div><div class="page-toolbar"><button class="table-action" type="button" data-announcement-view="${item.id}">查看</button>${item.status !== "offline" ? `<button class="table-action" type="button" data-announcement-edit="${item.id}">编辑</button>` : ""}${item.status !== "published" ? `<button class="table-action primary" type="button" data-announcement-publish="${item.id}">发布</button>` : `<button class="table-action danger" type="button" data-announcement-offline="${item.id}">下线</button>`}</div></div>`).join("") : empty("暂无公告");
    const byId = new Map(items.map((item) => [item.id, item]));
    target.querySelectorAll("[data-announcement-view]").forEach((button) => button.addEventListener("click", () => openAnnouncementDetail(button.dataset.announcementView).catch((error) => toast(error.message || "加载公告失败"))));
    target.querySelectorAll("[data-announcement-edit]").forEach((button) => button.addEventListener("click", () => openAnnouncementEditor(byId.get(button.dataset.announcementEdit)).catch((error) => toast(error.message || "加载公告表单失败"))));
    target.querySelectorAll("[data-announcement-publish]").forEach((button) => button.addEventListener("click", () => announcementReview(byId.get(button.dataset.announcementPublish))));
    target.querySelectorAll("[data-announcement-offline]").forEach((button) => button.addEventListener("click", () => offlineAnnouncement(byId.get(button.dataset.announcementOffline)).catch((error) => toast(error.message || "下线公告失败"))));
  }

  async function loadAnnouncements() {
    const params = new URLSearchParams(window.location.search);
    const status = params.get("status") || "";
    const level = params.get("level") || "";
    const q = params.get("q") || "";
    pageShell("公告管理", "新建、发布、下线和查看历史公告", `<section class="panel section-panel"><div class="panel-header"><div><h2>公告管理</h2><p>已发布公告只可下线，不会物理删除。</p></div><button id="createAnnouncementButton" class="primary-link" type="button">新建公告</button></div><form id="announcementFilterForm" class="filter-bar"><select name="status"><option value="">全部状态</option><option value="draft">草稿</option><option value="published">已发布</option><option value="offline">已下线</option></select><select name="level"><option value="">全部等级</option><option value="normal">普通</option><option value="important">重要</option><option value="urgent">紧急</option></select><input name="q" placeholder="搜索标题或正文" value="${html(q)}" /><button class="secondary-button" type="submit">筛选</button></form><div id="announcementList" class="simple-list"></div></section>`);
    const form = $("announcementFilterForm");
    form.status.value = status;
    form.level.value = level;
    form.addEventListener("submit", (event) => { event.preventDefault(); const data = new FormData(form); applyOrgFilters("/admin/announcements", { status: data.get("status"), level: data.get("level"), q: data.get("q") }); loadCurrent(true); });
    $("createAnnouncementButton").addEventListener("click", () => openAnnouncementEditor().catch((error) => toast(error.message || "加载公告表单失败")));
    const data = await api(`/api/v1/admin/announcements?${new URLSearchParams({ ...(status ? { status } : {}), ...(level ? { level } : {}), ...(q ? { q } : {}) })}`);
    const items = data.items || [];
    renderAnnouncementList(items);
  }

  function realtimeRouteUses(resource) {
    const route = currentRoute();
    if (route === "/admin/dashboard") return ["orders", "outbounds", "announcements", "dashboard", "quota"].includes(resource);
    if (route === "/admin/orders") return resource === "orders";
    if (route.startsWith("/admin/orders/")) return resource === "orders" || resource === "outbounds";
    if (route === "/admin/batches" || route.startsWith("/admin/batches/")) return resource === "batches";
    if (route === "/admin/outbounds" || route.startsWith("/admin/outbounds/")) return resource === "outbounds";
    if (route === "/admin/announcements") return resource === "announcements";
    if (route === "/admin/units") return resource === "quota";
    return false;
  }

  function realtimeShouldDefer(resource) {
    const active = document.activeElement;
    if (state.formDirty || state.mutationInFlight || document.querySelector('.org-dialog-backdrop, [role="dialog"]')) return true;
    if ((active instanceof HTMLInputElement && !["checkbox", "radio", "button", "submit", "reset"].includes(active.type)) || active instanceof HTMLTextAreaElement || active instanceof HTMLSelectElement) return true;
    // Batch and outbound list renderers retain their own selected ids during a keyed patch.
    if (resource !== "orders" && state.selectedProductIds.size) return true;
    return false;
  }

  async function fetchRealtime(path, resource) {
    const previous = state.realtime.fetches.get(resource);
    previous?.controller.abort();
    const controller = new AbortController();
    const sequence = (previous?.sequence || 0) + 1;
    state.realtime.fetches.set(resource, { controller, sequence });
    try {
      const value = await api(path, { signal: controller.signal });
      if (state.realtime.fetches.get(resource)?.sequence !== sequence) return null;
      return value;
    } catch (error) {
      if (error.name === "AbortError") return null;
      throw error;
    }
  }

  async function refreshRealtimeResource(resource) {
    if (!realtimeRouteUses(resource)) return;
    if (realtimeShouldDefer(resource)) {
      state.realtime.dirtyResources.add(resource);
      showNewDataBanner(resource === "quota" ? "额度数据已更新，请刷新后继续编辑。" : "当前内容已有更新，完成当前操作后可刷新。");
      return false;
    }
    if (resource === "orders" && currentRoute() === "/admin/orders") return refreshOrdersIncrementally();
    if (resource === "announcements" && currentRoute() === "/admin/announcements") {
      const params = new URLSearchParams(window.location.search);
      const data = await fetchRealtime(`/api/v1/admin/announcements?${new URLSearchParams({ ...(params.get("status") ? { status: params.get("status") } : {}), ...(params.get("level") ? { level: params.get("level") } : {}), ...(params.get("q") ? { q: params.get("q") } : {}) })}`, resource);
      if (data) renderAnnouncementList(data.items || []);
      return Boolean(data);
    }
    if (resource === "batches" && currentRoute() === "/admin/batches") return refreshBatchListIncrementally();
    if (resource === "outbounds" && currentRoute() === "/admin/outbounds") return refreshOutboundListIncrementally();
    if (resource === "quota" && currentRoute() === "/admin/units") return refreshQuotaSummary();
    if (currentRoute() === "/admin/dashboard") return refreshDashboardScoped(resource);
    // Detail pages retain their open dialog/page context until the user refreshes.
    state.realtime.dirtyResources.add(resource);
    showNewDataBanner("当前列表已有更新，点击刷新获取最新数据。");
    return false;
  }

  async function refreshOrdersIncrementally() {
    const data = await fetchRealtime(`/api/v1/admin/orders?limit=30&${orderListQuery().toString()}`, "orders");
    if (data) patchOrdersRealtime(data);
    return Boolean(data);
  }

  async function refreshDashboardScoped(resource) {
    const [overview, announcements] = await Promise.all([
      fetchRealtime(`/api/v1/admin/dashboard/overview?range_days=${state.rangeDays}&unit_sort=${state.unitSort}`, "dashboard"),
      resource === "announcements" || resource === "dashboard" ? fetchRealtime("/api/v1/announcements?limit=5", "announcements") : Promise.resolve(null),
    ]);
    if (overview) {
      state.lastData = overview;
      if (announcements) state.dashboardAnnouncements = announcements.items || [];
      renderDashboard(overview, state.dashboardAnnouncements);
      setSyncStatus(`已同步 · ${formatSyncTime()}`);
    } else if (announcements) {
      renderDashboardAnnouncements(announcements.items || []);
    }
    return Boolean(overview || announcements);
  }

  async function reconcileAffectedResources(resources) {
    const relevant = [...new Set(resources)].filter(realtimeRouteUses);
    if (!relevant.length) return false;
    if (document.hidden) {
      relevant.forEach((resource) => state.realtime.dirtyResources.add(resource));
      return false;
    }
    const applied = await refreshRealtimeResource(relevant[0]);
    if (applied) relevant.forEach((resource) => state.realtime.dirtyResources.delete(resource));
    return Boolean(applied);
  }

  function queueRealtimeRefresh(resource) {
    state.realtime.dirtyResources.add(resource);
    window.clearTimeout(state.realtime.timers.get(resource));
    state.realtime.timers.set(resource, window.setTimeout(() => {
      state.realtime.timers.delete(resource);
      if (document.hidden) return;
      refreshRealtimeResource(resource)
        .then((applied) => { if (applied) state.realtime.dirtyResources.delete(resource); })
        .catch(() => setSyncStatus(`实时同步失败${staleSuffix}`));
    }, 300));
  }

  async function pollRealtimeRevisions() {
    const revisions = await fetchRealtime("/api/v1/admin/realtime/revisions", "revisions");
    if (!revisions) return;
    Object.entries(revisions).forEach(([resource, revision]) => {
      const hasBaseline = Object.hasOwn(state.realtime.revisions, resource);
      const previous = Number(state.realtime.revisions[resource] || 0);
      state.realtime.revisions[resource] = Number(revision || 0);
      if (hasBaseline && Number(revision) > previous) queueRealtimeRefresh(resource);
    });
  }

  function scheduleRealtimeFallback() {
    window.clearTimeout(state.realtime.fallbackTimer);
    const delay = state.realtime.connected ? 120_000 : 20_000;
    state.realtime.fallbackTimer = window.setTimeout(async () => {
      if (!document.hidden) await pollRealtimeRevisions().catch(() => {});
      scheduleRealtimeFallback();
    }, delay);
  }

  function connectRealtime() {
    if (!window.EventSource) {
      state.realtime.connected = false;
      pollRealtimeRevisions().catch(() => {}).finally(scheduleRealtimeFallback);
      return;
    }
    if (state.realtime.eventSource) return;
    const source = new EventSource("/api/v1/admin/realtime/events");
    state.realtime.eventSource = source;
    source.onopen = () => {
      state.realtime.connected = true;
      setSyncStatus("实时同步已连接");
      scheduleRealtimeFallback();
    };
    source.addEventListener("resource_changed", (event) => {
      try {
        const change = JSON.parse(event.data || "{}");
        if (!change.resource) return;
        const revision = Number(change.revision || 0);
        const hasPrevious = Object.hasOwn(state.realtime.revisions, change.resource);
        if (!Number.isFinite(revision) || (hasPrevious && revision <= Number(state.realtime.revisions[change.resource] || 0))) return;
        state.realtime.revisions[change.resource] = revision;
        queueRealtimeRefresh(change.resource);
      } catch (_) {
        // Ignore a malformed non-authoritative invalidation and keep the fallback alive.
      }
    });
    source.onerror = () => {
      state.realtime.connected = false;
      scheduleRealtimeFallback();
    };
    pollRealtimeRevisions().catch(() => {}).finally(scheduleRealtimeFallback);
  }

  async function loadPlaceholder(title, message) {
    pageShell(title, "页面正在接入");
    content().innerHTML += empty(message);
  }

  async function loadCurrent(silent, source = "manual") {
    const automatic = source === "timer" || source === "focus" || source === "online" || source === "visibility" || source === "queued";
    const route = currentRoute();
    if (automatic && automaticRefreshShouldWait()) {
      showNewDataBanner("自动刷新已暂停，点击刷新确认最新数据。");
      return;
    }
    if (state.loading) {
      state.queuedRefresh = true;
      return;
    }
    state.loading = true;
    const sequence = ++state.refreshSequence;
    const scrollTop = window.scrollY;
    try {
      if (route !== "/admin/analytics") window.AdminAnalytics?.dispose();
      if (route === "/admin/dashboard") await loadDashboard(silent);
      else if (route === "/admin/orders") await loadOrders();
      else if (route.startsWith("/admin/orders/")) await loadOrderDetail(route.split("/").pop());
      else if (route === "/admin/batches") await loadBatches();
      else if (route.startsWith("/admin/batches/")) await loadBatchDetail(route.split("/").pop());
      else if (route === "/admin/outbounds") await loadOutbounds();
      else if (route.startsWith("/admin/outbounds/")) await loadOutboundDetail(route.split("/").pop());
      else if (route === "/admin/products" || route.startsWith("/admin/products/")) await loadProducts();
      else if (route === "/admin/price-imports") await loadPriceImports();
      else if (route.endsWith("/mapping") && route.startsWith("/admin/price-imports/")) await loadPriceImportMapping(route.split("/")[3]);
      else if (route.startsWith("/admin/price-imports/")) await loadPriceImportDetail(route.split("/").pop());
      else if (route === "/admin/inventory") await loadInventory();
      else if (route === "/admin/units") await loadUnits();
      else if (route === "/admin/accounts") await loadAccounts();
      else if (route === "/admin/ledger") await loadLedger();
      else if (route === "/admin/analytics") await window.AdminAnalytics.load();
      else if (route === "/admin/preparation-summary") await loadPreparationSummary();
      else if (route === "/admin/delivery-sheets") await loadDeliverySheets();
      else if (route === "/admin/system") await loadSystem();
      else if (route === "/admin/system-logs") await loadSystemLogs();
      else if (route === "/admin/web-sessions") await loadWebSessions();
      else if (route === "/admin/announcements") await loadAnnouncements();
      else await loadPlaceholder("管理后台", "该页面暂未接入，请从左侧菜单选择可用功能。");
      if (sequence !== state.refreshSequence) return;
      state.lastSuccessfulSyncAt = Date.now();
      hideNewDataBanner();
      setSyncStatus(`已同步 · ${formatSyncTime()}`);
      if (scrollTop > 0 && source !== "manual") window.requestAnimationFrame(() => window.scrollTo(0, scrollTop));
      if (!silent && route !== "/admin/dashboard") toast("数据已刷新");
    } catch (error) {
      reportClientError(error.message || "页面加载失败", window.location.pathname, { route: currentRoute() });
      if (automatic) {
        setSyncStatus(`自动同步失败${staleSuffix}`);
        return;
      }
      content().innerHTML = `<div class="error-banner">数据加载失败：${html(error.message || "请稍后重试")} <button id="retryButton" type="button">重新加载</button></div>`;
      $("retryButton").addEventListener("click", () => loadCurrent(false));
    } finally {
      state.loading = false;
      if (state.queuedRefresh) {
        state.queuedRefresh = false;
        window.setTimeout(() => loadCurrent(true, "queued"), 0);
      }
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
    window.clearInterval(state.reminderTimer);
    connectRealtime();
  }

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      const dirty = Array.from(state.realtime.dirtyResources);
      dirty.forEach(queueRealtimeRefresh);
      pollRealtimeRevisions().catch(() => {});
    }
  });

  window.addEventListener("focus", () => {
    if (!document.hidden && Date.now() - state.lastSuccessfulSyncAt > 5_000) {
      pollRealtimeRevisions().catch(() => {});
    }
  });

  window.addEventListener("online", () => {
    setSyncStatus("网络已恢复，正在同步");
    pollRealtimeRevisions().catch(() => {});
  });

  window.addEventListener("offline", () => setSyncStatus("网络已断开，当前数据可能不是最新"));

  window.addEventListener("scroll", () => {
    state.lastScrollInteractionAt = Date.now();
  }, { passive: true });

  document.addEventListener("click", (event) => {
    const fastCompleteButton = event.target.closest?.("[data-fast-complete]");
    if (fastCompleteButton) {
      event.preventDefault();
      if (fastCompleteButton.dataset.fastCompleteOpening === "1") return;
      fastCompleteButton.dataset.fastCompleteOpening = "1";
      openFastCompleteReview(fastCompleteButton)
        .catch((error) => {
          reportClientError(error.message || "完成订单交互失败", "/api/v1/admin/orders/complete", { action: "fast_complete" });
          toast(error.message || "完成订单失败，请刷新后重试");
        })
        .finally(() => { delete fastCompleteButton.dataset.fastCompleteOpening; });
      return;
    }
    const outboundCompleteButton = event.target.closest?.("[data-outbound-complete]");
    if (outboundCompleteButton) {
      event.preventDefault();
      if (outboundCompleteButton.dataset.outboundCompleteOpening === "1") return;
      outboundCompleteButton.dataset.outboundCompleteOpening = "1";
      openOutboundCompleteReview(outboundCompleteButton)
        .catch((error) => {
          reportClientError(error.message || "完成出库交互失败", "/api/v1/admin/outbounds/complete", { action: "outbound_direct_complete" });
          toast(error.message || "完成出库失败，请刷新后重试");
        })
        .finally(() => { delete outboundCompleteButton.dataset.outboundCompleteOpening; });
      return;
    }
    const button = event.target.closest?.("[data-delete-order]");
    if (button) deleteOrder(button);
    const batchAction = event.target.closest?.("[data-batch-v2-action]");
    if (batchAction) {
      const group = state.batchWorkbench.find((item) => item.unit_id === batchAction.dataset.unitId);
      if (group) openGroupBatchAction(group, batchAction.dataset.batchV2Action);
      else toast("单位备货数据已变化，请刷新后重试");
    }
  });

  document.addEventListener("input", (event) => {
    if (shouldMarkFormDirty(event.target)) {
      state.formDirty = true;
    }
  }, true);
  document.addEventListener("change", (event) => {
    if (event.target.matches?.("[data-unit-pending-order]")) state.formDirty = true;
    if (shouldMarkFormDirty(event.target)) {
      state.formDirty = true;
    }
  }, true);

  $("refreshButton").addEventListener("click", () => {
    state.formDirty = false;
    loadCurrent(false);
  });
  $("applyNewDataButton").addEventListener("click", () => {
    if (window.AdminRefreshPolicy.hasActiveSelections({
      orders: state.selectedOrderIds.size,
      products: state.selectedProductIds.size,
      batches: state.selectedBatchIds.size,
      batchOrders: document.querySelectorAll("[data-unit-pending-order]:checked").length,
      outbounds: state.selectedOutboundIds.size,
    })) {
      toast("请先完成或取消当前选择，再刷新数据");
      return;
    }
    retainOrderFiltersInLocation();
    state.formDirty = false;
    loadCurrent(false, "banner");
  });
  $("logoutButton").addEventListener("click", logout);
  setupSidebar();
  renderNav();
  loadCurrent(true);
  checkAdminOrderReminders();
  schedule();
})();
