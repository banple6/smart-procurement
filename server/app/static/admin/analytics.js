(function () {
  const charts = [];
  let resizeHandler = null;
  const categories = ["", "蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他"];
  const riskText = {
    out_of_stock: "库存不足",
    warning: "低于预警",
    tight: "库存紧张",
    paused: "暂停供应",
    normal: "正常",
  };

  function html(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function money(cents) {
    return "¥" + (Number(cents || 0) / 100).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function localDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function defaultDates(days) {
    const end = new Date();
    const start = new Date(end);
    start.setDate(start.getDate() - days + 1);
    return { start: localDate(start), end: localDate(end) };
  }

  function currentState() {
    const params = new URLSearchParams(window.location.search);
    const range = params.get("range") || "30";
    const fallback = defaultDates(Number(range) || 30);
    return {
      tab: params.get("tab") || "overview",
      range,
      start: params.get("start_date") || fallback.start,
      end: params.get("end_date") || fallback.end,
      unitId: params.get("unit_id") || "",
      category: params.get("category") || "",
      productId: params.get("product_id") || "",
    };
  }

  function setState(changes) {
    const state = { ...currentState(), ...changes };
    const params = new URLSearchParams();
    params.set("tab", state.tab);
    params.set("range", state.range);
    params.set("start_date", state.start);
    params.set("end_date", state.end);
    if (state.unitId) params.set("unit_id", state.unitId);
    if (state.category) params.set("category", state.category);
    if (state.productId) params.set("product_id", state.productId);
    window.history.replaceState({}, "", `/admin/analytics?${params.toString()}`);
  }

  async function api(path) {
    const response = await fetch(`/api/v1/${path}`, { credentials: "same-origin", headers: { Accept: "application/json" } });
    if (response.status === 401) {
      window.location.replace("/login?expired=1");
      throw new Error("登录已过期，请重新登录");
    }
    const text = await response.text();
    const body = text ? JSON.parse(text) : {};
    if (!response.ok) throw new Error(body.detail || "数据加载失败，请稍后重试");
    return body;
  }

  function query(state, includeUnit = true) {
    const params = new URLSearchParams({ start_date: state.start, end_date: state.end });
    if (includeUnit && state.unitId) params.set("unit_id", state.unitId);
    if (state.category) params.set("category", state.category);
    return params.toString();
  }

  function dispose() {
    while (charts.length) charts.pop().dispose();
    if (resizeHandler) window.removeEventListener("resize", resizeHandler);
    resizeHandler = null;
  }

  function chart(id, option) {
    const node = document.getElementById(id);
    if (!node || !window.echarts) return null;
    const instance = window.echarts.init(node, null, { renderer: "canvas" });
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    instance.setOption({
      animation: !reduceMotion,
      aria: { enabled: true, decal: { show: true } },
      ...option,
    });
    charts.push(instance);
    return instance;
  }

  function bindResize() {
    resizeHandler = () => charts.forEach((item) => item.resize());
    window.addEventListener("resize", resizeHandler, { passive: true });
  }

  function comparison(value) {
    if (value === null || value === undefined) return "上期无可比数据";
    const prefix = value > 0 ? "+" : "";
    return `较上期 ${prefix}${value.toFixed(1)}%`;
  }

  function table(headers, rows) {
    if (!rows.length) return '<div class="analytics-loading">当前范围暂无数据</div>';
    return `<div class="table-wrap"><table><thead><tr>${headers.map((item) => `<th>${html(item)}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`;
  }

  function shell(units, state) {
    const categoryOptions = categories.map((item) => `<option value="${html(item)}" ${item === state.category ? "selected" : ""}>${html(item || "全部分类")}</option>`).join("");
    const unitOptions = [`<option value="">全部单位</option>`, ...units.map((item) => `<option value="${html(item.id)}" ${item.id === state.unitId ? "selected" : ""}>${html(item.unit_name)}</option>`)].join("");
    const title = document.getElementById("pageTitle");
    const subtitle = document.getElementById("businessDate");
    title.textContent = "数据分析";
    subtitle.textContent = "采购、价格、库存与单位数据使用统一服务端口径";
    document.title = "数据分析 - 三公鲜配";
    document.querySelector(".content").innerHTML = `
      <section class="analytics-toolbar" aria-label="分析筛选">
        <div class="analytics-filters">
          <label class="analytics-filter"><span>开始日期</span><input id="analyticsStart" type="date" value="${html(state.start)}"></label>
          <label class="analytics-filter"><span>结束日期</span><input id="analyticsEnd" type="date" value="${html(state.end)}"></label>
          <label class="analytics-filter"><span>子单位</span><select id="analyticsUnit">${unitOptions}</select></label>
          <label class="analytics-filter"><span>分类</span><select id="analyticsCategory">${categoryOptions}</select></label>
        </div>
        <div class="analytics-range-buttons" aria-label="快捷日期范围">
          ${[7, 30, 90].map((days) => `<button class="analytics-range-button ${state.range === String(days) ? "is-active" : ""}" data-range="${days}" type="button">近 ${days} 日</button>`).join("")}
          <button id="analyticsApply" class="primary-button" type="button">应用筛选</button>
        </div>
      </section>
      <div class="analytics-tabs" role="tablist">
        <button class="analytics-tab ${state.tab === "overview" ? "is-active" : ""}" data-tab="overview" type="button">采购概览</button>
        <button class="analytics-tab ${state.tab === "price" ? "is-active" : ""}" data-tab="price" type="button">价格趋势</button>
        <button class="analytics-tab ${state.tab === "inventory" ? "is-active" : ""}" data-tab="inventory" type="button">库存风险</button>
      </div>
      <div id="analyticsBody"><div class="analytics-loading">正在读取真实业务数据...</div></div>`;
  }

  function bindControls() {
    document.querySelectorAll("[data-range]").forEach((button) => button.addEventListener("click", async () => {
      const range = button.dataset.range;
      const dates = defaultDates(Number(range));
      setState({ range, start: dates.start, end: dates.end });
      await load(window.AdminAnalytics.context);
    }));
    document.getElementById("analyticsApply").addEventListener("click", async () => {
      setState({
        range: "custom",
        start: document.getElementById("analyticsStart").value,
        end: document.getElementById("analyticsEnd").value,
        unitId: document.getElementById("analyticsUnit").value,
        category: document.getElementById("analyticsCategory").value,
      });
      await load(window.AdminAnalytics.context);
    });
    document.querySelectorAll("[data-tab]").forEach((button) => button.addEventListener("click", async () => {
      setState({ tab: button.dataset.tab });
      await load(window.AdminAnalytics.context);
    }));
  }

  async function renderOverview(state) {
    const overview = await api(`admin/analytics/overview?${query(state)}&limit=10`);
    const summary = overview.summary;
    const compare = overview.comparison;
    document.getElementById("analyticsBody").innerHTML = `
      <section class="analytics-summary">
        ${[
          ["有效订单", summary.valid_order_count, comparison(compare.valid_order_count_percent)],
          ["采购金额", money(summary.total_cents), comparison(compare.total_cents_percent)],
          ["采购单位", summary.unit_count, comparison(compare.unit_count_percent)],
          ["食材品种", summary.product_count, comparison(compare.product_count_percent)],
          ["库存预警", summary.inventory_alert_count, "当前库存快照"],
          ["待处理异常", summary.open_receipt_issues, "当前筛选范围"],
        ].map(([label, value, note]) => `<article class="analytics-metric"><span>${label}</span><strong>${html(value)}</strong><small>${html(note)}</small></article>`).join("")}
      </section>
      <section class="analytics-grid">
        <article class="analytics-panel"><h2>采购趋势</h2><p>金额使用订单及明细价格快照</p><div class="analytics-range-buttons"><button class="analytics-range-button is-active" data-trend-metric="amount" type="button">采购金额</button><button class="analytics-range-button" data-trend-metric="orders" type="button">订单量</button></div><div id="analyticsTrend" class="analytics-chart"></div></article>
        <article class="analytics-panel"><h2>食材需求排行</h2><p>按商品和计量单位分别汇总</p><div id="analyticsDemand" class="analytics-chart compact"></div><div id="analyticsDemandText"></div></article>
      </section>
      <article class="analytics-panel"><h2>单位采购情况</h2><p>订单金额先按订单聚合，不重复计算明细</p><div class="analytics-range-buttons"><button class="analytics-range-button is-active" data-unit-sort="amount" type="button">采购金额</button><button class="analytics-range-button" data-unit-sort="orders" type="button">订单数</button><button class="analytics-range-button" data-unit-sort="products" type="button">食材种类</button></div><div id="analyticsUnitChart" class="analytics-chart compact"></div><div id="analyticsUnits"></div></article>`;
    let trendChart = null;
    const drawTrend = (metric) => {
      if (trendChart) {
        trendChart.dispose();
        const index = charts.indexOf(trendChart);
        if (index >= 0) charts.splice(index, 1);
      }
      const isAmount = metric === "amount";
      if (!overview.summary.valid_order_count) {
        document.getElementById("analyticsTrend").innerHTML = '<div class="analytics-empty">暂无该时间范围的数据</div>';
        trendChart = null;
        return;
      }
      trendChart = chart("analyticsTrend", {
      tooltip: { trigger: "axis", valueFormatter: (value) => isAmount ? money(value) : `${value} 笔` },
      grid: { left: 68, right: 32, top: 24, bottom: 48 },
      dataset: { source: overview.trend.map((item) => ({ date: item.date.slice(5), amount: item.total_cents, orders: item.order_count })) },
      xAxis: { type: "category" },
      yAxis: { type: "value", minInterval: isAmount ? 0 : 1, axisLabel: { formatter: (value) => isAmount ? `¥${Math.round(value / 100)}` : value } },
      series: [{ name: isAmount ? "采购金额" : "订单量", type: "line", encode: { x: "date", y: isAmount ? "amount" : "orders" }, smooth: true, symbolSize: 7, lineStyle: { width: 3, color: "#123d72" }, itemStyle: { color: "#123d72" }, areaStyle: { color: "rgba(18,61,114,.08)" } }],
    });
      trendChart.on("click", (params) => {
        const day = overview.trend[params.dataIndex]?.date;
        if (day) window.location.href = `/admin/orders?start_date=${encodeURIComponent(day)}&end_date=${encodeURIComponent(day)}`;
      });
    };
    drawTrend("amount");
    document.querySelectorAll("[data-trend-metric]").forEach((button) => button.addEventListener("click", () => {
      document.querySelectorAll("[data-trend-metric]").forEach((item) => item.classList.toggle("is-active", item === button));
      drawTrend(button.dataset.trendMetric);
    }));
    const demand = [...overview.demand_rank].reverse();
    if (demand.length) {
      const demandChart = chart("analyticsDemand", {
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        dataset: { source: demand.map((item) => ({ name: item.product_name, quantity: Number(item.quantity), unit: item.unit })) },
        grid: { left: 90, right: 32, top: 16, bottom: 32 },
        xAxis: { type: "value" },
        yAxis: { type: "category" },
        series: [{ type: "bar", encode: { x: "quantity", y: "name" }, itemStyle: { color: "#2c6aa5" }, label: { show: true, position: "right", formatter: (params) => `${params.value.quantity} ${params.value.unit}` } }],
      });
      demandChart.on("click", (params) => {
        const item = demand[params.dataIndex];
        if (!item) return;
        setState({ tab: "price", productId: item.product_id });
        load(window.AdminAnalytics.context);
      });
      document.getElementById("analyticsDemandText").innerHTML = table(["食材", "需求量", "单位数", "订单数"], [...overview.demand_rank].map((item) => `<tr><td>${html(item.product_name)}</td><td>${html(item.quantity)} ${html(item.unit)}</td><td>${item.unit_count}</td><td>${item.order_count}</td></tr>`));
    } else {
      document.getElementById("analyticsDemand").innerHTML = '<div class="analytics-empty">暂无该时间范围的数据</div>';
    }
    let unitChart = null;
    const drawUnits = async (sort) => {
      const units = await api(`admin/analytics/units?${query(state, false)}&sort=${sort}`);
      if (unitChart) {
        unitChart.dispose();
        const index = charts.indexOf(unitChart);
        if (index >= 0) charts.splice(index, 1);
      }
      const unitChartItems = [...units.items].slice(0, 16).reverse();
      const field = sort === "orders" ? "order_count" : sort === "products" ? "product_count" : "total_cents";
      if (unitChartItems.length) {
        unitChart = chart("analyticsUnitChart", {
          tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter: (value) => sort === "amount" ? money(value) : String(value) },
          dataset: { source: unitChartItems },
          grid: { left: 110, right: 36, top: 16, bottom: 36 },
          xAxis: { type: "value", minInterval: sort === "amount" ? 0 : 1, axisLabel: { formatter: (value) => sort === "amount" ? `¥${Math.round(value / 100)}` : value } },
          yAxis: { type: "category" },
          series: [{ type: "bar", encode: { x: field, y: "unit_name" }, itemStyle: { color: "#527da7" } }],
        });
        unitChart.on("click", (params) => {
          const item = unitChartItems[params.dataIndex];
          if (item) window.location.href = `/admin/orders?unit_id=${encodeURIComponent(item.unit_id)}&start_date=${encodeURIComponent(state.start)}&end_date=${encodeURIComponent(state.end)}`;
        });
      } else {
        document.getElementById("analyticsUnitChart").innerHTML = '<div class="analytics-empty">暂无该时间范围的数据</div>';
      }
      document.getElementById("analyticsUnits").innerHTML = table(
        ["单位", "订单数", "食材品种", "采购金额", "待处理异常"],
        units.items.map((item) => `<tr><td>${html(item.unit_name)}</td><td>${item.order_count}</td><td>${item.product_count}</td><td>${money(item.total_cents)}</td><td>${item.open_issue_count}</td></tr>`),
      );
    };
    document.querySelectorAll("[data-unit-sort]").forEach((button) => button.addEventListener("click", async () => {
      document.querySelectorAll("[data-unit-sort]").forEach((item) => item.classList.toggle("is-active", item === button));
      await drawUnits(button.dataset.unitSort);
    }));
    await drawUnits("amount");
  }

  function changeText(value) {
    if (value === null || value === undefined) return '<span class="muted">新建或无可比基数</span>';
    const css = value > 0 ? "analytics-change-up" : value < 0 ? "analytics-change-down" : "";
    return `<span class="${css}">${value > 0 ? "+" : ""}${value.toFixed(1)}%</span>`;
  }

  async function showProduct(productId, state) {
    const detail = await api(`admin/analytics/products/${encodeURIComponent(productId)}?${query(state, false)}`);
    const panel = document.getElementById("analyticsProductDetail");
    const price = detail.price;
    panel.innerHTML = `<h2>${html(detail.product.name)}价格记录</h2><p>历史事件来自服务端价格变更日志</p>
      <div class="analytics-product-metrics">
        <span>当前价<strong>${money(price.current_cents)}</strong></span>
        <span>期初价<strong>${price.range_start_cents === null ? "—" : money(price.range_start_cents)}</strong></span>
        <span>区间变化<strong>${changeText(price.change_percent)}</strong></span>
        <span>最低 / 最高<strong>${money(price.min_cents)} / ${money(price.max_cents)}</strong></span>
      </div>
      <div id="analyticsProductPrice" class="analytics-chart compact"></div>
      <div id="analyticsProductHistory"></div>`;
    if (detail.price_history.length) {
      chart("analyticsProductPrice", {
        tooltip: { trigger: "axis", valueFormatter: (value) => money(value) },
        dataset: { source: detail.price_history.map((item) => ({ time: item.created_at.slice(0, 16), price: item.new_price_cents })) },
        grid: { left: 68, right: 32, top: 24, bottom: 48 },
        xAxis: { type: "category" },
        yAxis: { type: "value", axisLabel: { formatter: (value) => `¥${(value / 100).toFixed(2)}` } },
        series: [{ type: "line", encode: { x: "time", y: "price" }, step: "end", symbolSize: 8, lineStyle: { width: 3, color: "#123d72" }, itemStyle: { color: "#123d72" } }],
      });
    } else {
      document.getElementById("analyticsProductPrice").innerHTML = '<div class="analytics-empty">暂无价格变动记录</div>';
    }
    document.getElementById("analyticsProductHistory").innerHTML = table(
      ["变更时间", "原价格", "新价格"],
      detail.price_history.map((item) => `<tr><td>${html(item.created_at.replace("T", " ").slice(0, 19))}</td><td>${item.old_price_cents === null ? "—" : money(item.old_price_cents)}</td><td>${money(item.new_price_cents)}</td></tr>`),
    );
  }

  async function renderPrices(state) {
    const body = await api(`admin/analytics/prices?${query(state, false)}`);
    const increases = body.items.filter((item) => item.change_percent > 0).sort((a, b) => b.change_percent - a.change_percent).slice(0, 10);
    const decreases = body.items.filter((item) => item.change_percent < 0).sort((a, b) => a.change_percent - b.change_percent).slice(0, 10);
    document.getElementById("analyticsBody").innerHTML = `
      <section class="analytics-grid">
        <article class="analytics-panel"><h2>上涨最多</h2><p>按区间涨幅排序</p>${table(["食材", "期末价", "涨幅"], increases.map((item) => `<tr><td>${html(item.product_name)}</td><td>${money(item.current_price_cents)}</td><td>${changeText(item.change_percent)}</td></tr>`))}</article>
        <article class="analytics-panel"><h2>下降最多</h2><p>按区间跌幅排序</p>${table(["食材", "期末价", "跌幅"], decreases.map((item) => `<tr><td>${html(item.product_name)}</td><td>${money(item.current_price_cents)}</td><td>${changeText(item.change_percent)}</td></tr>`))}</article>
      </section>
      <section class="analytics-grid">
        <article class="analytics-panel"><h2>价格变动</h2><p>涨跌基数为 0 时不计算百分比</p><label class="analytics-filter"><span>搜索食材</span><input id="analyticsProductSearch" type="search" placeholder="输入食材名称"></label><div id="analyticsPricesTable"></div></article>
        <article id="analyticsProductDetail" class="analytics-panel"><h2>食材价格明细</h2><p>点击左侧食材查看真实价格事件</p><div class="analytics-loading">请选择食材</div></article>
      </section>`;
    document.getElementById("analyticsPricesTable").innerHTML = table(
      ["食材", "单位", "期初", "期末", "变化", "变更次数"],
      body.items.map((item) => `<tr data-product="${html(item.product_id)}" data-product-name="${html(item.product_name)}" tabindex="0"><td><a href="#">${html(item.product_name)}</a></td><td>${html(item.unit)}</td><td>${item.initial_price_cents === null ? "—" : money(item.initial_price_cents)}</td><td>${money(item.current_price_cents)}</td><td>${changeText(item.change_percent)}</td><td>${item.change_count}</td></tr>`),
    );
    document.querySelectorAll("[data-product]").forEach((row) => {
      const open = (event) => { event.preventDefault(); showProduct(row.dataset.product, state).catch((error) => { document.getElementById("analyticsProductDetail").innerHTML = `<div class="error-banner">${html(error.message)}</div>`; }); };
      row.addEventListener("click", open);
      row.addEventListener("keydown", (event) => { if (event.key === "Enter") open(event); });
    });
    document.getElementById("analyticsProductSearch").addEventListener("input", (event) => {
      const keyword = event.currentTarget.value.trim().toLowerCase();
      document.querySelectorAll("[data-product-name]").forEach((row) => { row.hidden = keyword && !row.dataset.productName.toLowerCase().includes(keyword); });
    });
    const selected = body.items.find((item) => item.product_id === state.productId) || body.items[0];
    if (selected) await showProduct(selected.product_id, state);
  }

  async function renderInventory() {
    const body = await api("admin/analytics/inventory");
    const items = body.items.slice(0, 15).reverse();
    document.getElementById("analyticsBody").innerHTML = `
      <section class="analytics-summary">
        ${[
          ["全部食材", body.summary.product_count], ["库存不足", body.summary.out_of_stock], ["低于预警", body.summary.warning],
          ["库存紧张", body.summary.tight], ["暂停供应", body.summary.paused],
        ].map(([label, value]) => `<article class="analytics-metric"><span>${label}</span><strong>${value}</strong><small>实时库存快照</small></article>`).join("")}
      </section>
      <section class="analytics-grid">
        <article class="analytics-panel"><h2>可用库存</h2><p>总库存减去已预占库存</p><div id="analyticsInventoryChart" class="analytics-chart"></div></article>
        <article class="analytics-panel"><h2>库存风险</h2><p>预计可供天数仅基于最近 14 天同单位真实需求</p><div id="analyticsInventoryTable"></div></article>
      </section>`;
    if (items.length) {
      chart("analyticsInventoryChart", {
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        dataset: { source: items.map((item) => ({ name: item.product_name, available: Number(item.available_quantity), unit: item.unit, risk: item.risk })) },
        grid: { left: 100, right: 36, top: 16, bottom: 36 },
        xAxis: { type: "value" },
        yAxis: { type: "category" },
        series: [{ type: "bar", encode: { x: "available", y: "name" }, itemStyle: { color: (params) => params.value.risk === "normal" ? "#2d7d5b" : "#b56b1d" }, label: { show: true, position: "right", formatter: (params) => `${params.value.available} ${params.value.unit}` } }],
      });
    } else {
      document.getElementById("analyticsInventoryChart").innerHTML = '<div class="analytics-empty">暂无库存食材数据</div>';
    }
    document.getElementById("analyticsInventoryTable").innerHTML = table(
      ["食材", "可用库存", "已预占", "预警值", "预计可用天数", "状态"],
      body.items.map((item) => `<tr><td><a href="/admin/products/${html(item.product_id)}">${html(item.product_name)}</a></td><td>${html(item.available_quantity)} ${html(item.unit)}<div class="analytics-stock-track" aria-hidden="true"><span style="width:${Math.max(0, Math.min(100, Number(item.stock_quantity) > 0 ? Number(item.available_quantity) * 100 / Number(item.stock_quantity) : 0))}%"></span></div></td><td>${html(item.reserved_quantity)} ${html(item.unit)}</td><td>${html(item.warning_quantity)} ${html(item.unit)}</td><td>${item.estimated_days_available === null ? "暂无足够需求数据" : `${html(item.estimated_days_available)} 天`}</td><td class="analytics-risk-${html(item.risk)}">${html(riskText[item.risk])}</td></tr>`),
    );
  }

  async function render(state) {
    dispose();
    if (state.tab === "price") await renderPrices(state);
    else if (state.tab === "inventory") await renderInventory(state);
    else await renderOverview(state);
    bindResize();
  }

  async function load(context) {
    window.AdminAnalytics.context = context || window.AdminAnalytics.context;
    dispose();
    const state = currentState();
    const units = await api("admin/units");
    shell(Array.isArray(units) ? units : (units.items || []), state);
    bindControls();
    try {
      await render(state);
    } catch (error) {
      document.getElementById("analyticsBody").innerHTML = `<div class="error-banner">数据加载失败：${html(error.message)} <button id="analyticsRetry" type="button">重新加载</button></div>`;
      document.getElementById("analyticsRetry").addEventListener("click", () => load(window.AdminAnalytics.context));
    }
  }

  window.AdminAnalytics = { load, dispose, context: null };
})();
