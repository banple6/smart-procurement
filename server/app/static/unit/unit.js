(function () {
  function $(id) { return document.getElementById(id); }
  function money(cents) { return "¥" + (Number(cents || 0) / 100).toFixed(2); }
  function qty(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return String(value ?? "");
    return String(Number.isInteger(n) ? n : Number(n.toFixed(3)));
  }
  function html(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
  }
  function cookie(name) {
    const found = document.cookie.split("; ").find((item) => item.startsWith(name + "="));
    return found ? found.split("=").slice(1).join("=") : "";
  }
  function toast(text) {
    const box = $("toast");
    box.textContent = text;
    box.hidden = false;
    window.setTimeout(() => { box.hidden = true; }, 1800);
  }
  async function api(path, options = {}) {
    const method = String(options.method || "GET").toUpperCase();
    const csrfHeaders = ["POST", "PUT", "PATCH", "DELETE"].includes(method) ? { "X-CSRF-Token": decodeURIComponent(cookie("csrf_token")) } : {};
    const response = await fetch(path, {
      credentials: "same-origin",
      headers: { "Accept": "application/json", ...csrfHeaders, ...(options.headers || {}) },
      ...options,
    });
    if (response.status === 401) {
      window.location.replace("/login?expired=1");
      throw new Error("登录已过期");
    }
    if (!response.ok) {
      let detail = "操作失败，请稍后重试";
      try { detail = (await response.json()).detail || detail; } catch (_) {}
      throw new Error(detail);
    }
    return response.json();
  }
  function activateNav() {
    const route = window.location.pathname;
    document.querySelectorAll(".unit-nav a").forEach((item) => {
      item.classList.toggle("active", route === item.dataset.route || route.startsWith(item.dataset.route + "/"));
    });
  }
  async function logout() {
    await api("/api/v1/web-auth/logout", { method: "POST" }).catch(() => {});
    window.location.replace("/login");
  }
  async function loadHome() {
    const data = await api("/unit/home/data");
    $("homeUnitName").textContent = data.unit?.unit_name || "--";
    $("homeDeliveryPoint").textContent = "默认配送点：" + (data.unit?.default_delivery_point || "--");
    $("cutoffTime").textContent = data.cutoff?.enabled ? data.cutoff.cutoff_time : "未限制";
    $("cartCount").textContent = data.cart_count || 0;
    $("waitingReceipt").textContent = data.waiting_receipt || 0;
    $("recentOrders").innerHTML = (data.recent_orders || []).map((order) => `<a class="row-item" href="/unit/orders/${order.id}"><div class="row-head"><strong>${html(order.order_no)}</strong><span>${html(order.status)}</span></div><div class="row-sub">${money(order.total_cents)} · ${html(order.created_at)}</div></a>`).join("") || '<div class="row-sub">暂无订单</div>';
    $("purchaseTips").innerHTML = (data.tips || []).map((tip) => `<div class="row-item">${html(tip)}</div>`).join("");
  }
  async function loadProducts() {
    const q = $("productSearch")?.value || "";
    const data = await api("/unit/products/data" + (q ? "?q=" + encodeURIComponent(q) : ""));
    $("productList").innerHTML = (data.items || []).map((item) => `
      <article class="product-item">
        <div class="row-head"><strong>${html(item.name)}</strong><span>${money(item.price_cents)}</span></div>
        <div class="row-sub">${html(item.spec)} / ${html(item.unit)} · 可用 ${html(qty(item.available_quantity))}</div>
        <div class="product-actions">
          <input data-qty="${item.id}" type="number" min="${html(item.min_order_quantity)}" step="${html(item.quantity_step)}" value="${html(item.min_order_quantity)}" />
          <button data-add="${item.id}" type="button">加入清单</button>
        </div>
      </article>
    `).join("") || '<div class="row-sub">暂无可申领食材</div>';
    document.querySelectorAll("[data-add]").forEach((button) => {
      button.addEventListener("click", async () => {
        const quantity = document.querySelector(`[data-qty="${button.dataset.add}"]`).value;
        await api("/unit/cart/items", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ product_id: button.dataset.add, quantity }) });
        toast("已加入清单");
      });
    });
  }
  async function loadCart() {
    const data = await api("/unit/cart/data");
    $("cartDeliveryPoint").textContent = "配送点：" + (data.delivery_point || "--");
    $("cartTotal").textContent = money(data.total_cents);
    $("cartList").innerHTML = (data.items || []).map((item) => `
      <div class="row-item">
        <div class="row-head"><strong>${html(item.name)}</strong><span>${money(item.subtotal_cents)}</span></div>
        <div class="row-sub">${html(item.spec)} · ${money(item.price_cents)} x <input data-cart-qty="${item.id}" type="number" value="${html(item.quantity)}" step="${html(item.quantity_step)}" /></div>
        <div class="product-actions"><button data-cart-update="${item.id}" type="button">保存数量</button><button data-cart-delete="${item.id}" type="button">删除</button></div>
      </div>
    `).join("") || '<div class="row-sub">清单为空</div>';
    document.querySelectorAll("[data-cart-update]").forEach((button) => button.addEventListener("click", async () => {
      const quantity = document.querySelector(`[data-cart-qty="${button.dataset.cartUpdate}"]`).value;
      await api(`/unit/cart/items/${button.dataset.cartUpdate}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ quantity }) });
      toast("数量已保存");
      loadCart();
    }));
    document.querySelectorAll("[data-cart-delete]").forEach((button) => button.addEventListener("click", async () => {
      await api(`/unit/cart/items/${button.dataset.cartDelete}`, { method: "DELETE" });
      toast("已删除");
      loadCart();
    }));
  }
  async function submitOrder() {
    const note = $("orderNote").value || "";
    const order = await api("/unit/orders", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ note }) });
    window.location.replace(`/unit/orders/${order.id}`);
  }
  async function loadOrders() {
    const data = await api("/unit/orders/data");
    $("orderList").innerHTML = (data.items || []).map((order) => `<a class="row-item" href="/unit/orders/${order.id}"><div class="row-head"><strong>${html(order.order_no)}</strong><span>${html(order.status)}</span></div><div class="row-sub">${money(order.total_cents)} · ${html(order.created_at)}</div></a>`).join("") || '<div class="row-sub">暂无订单</div>';
  }
  async function loadOrderDetail() {
    const orderId = window.location.pathname.split("/").pop();
    const order = await api(`/unit/orders/${orderId}/data`);
    $("orderNo").textContent = order.order_no;
    $("orderStatus").textContent = order.status;
    $("orderMeta").innerHTML = `<span>配送点</span><strong>${html(order.delivery_point_snapshot)}</strong><span>金额</span><strong>${money(order.total_cents)}</strong><span>备注</span><strong>${html(order.note || "无")}</strong>`;
    $("orderItems").innerHTML = (order.items || []).map((item) => `<div class="row-item"><div class="row-head"><strong>${html(item.product_name_snapshot)}</strong><span>${money(item.subtotal_cents)}</span></div><div class="row-sub">${html(item.spec_snapshot)} · ${html(item.quantity)} ${html(item.unit_snapshot)}</div></div>`).join("");
    $("shippingPhotos").innerHTML = (order.shipping_photos || []).map((photo) => `<a href="${photo.full_url}" target="_blank" rel="noreferrer"><img src="${photo.thumbnail_url}" alt="发货照片" /></a>`).join("") || '<div class="row-sub">暂无发货照片</div>';
    if (order.status === "shipped") $("confirmReceiptButton").hidden = false;
  }
  async function confirmReceipt() {
    const orderId = window.location.pathname.split("/").pop();
    await api(`/unit/orders/${orderId}/confirm-receipt`, { method: "POST" });
    toast("已确认收货");
    loadOrderDetail();
  }
  async function loadProfile() {
    const me = await api("/api/v1/web-auth/me");
    $("profileRows").innerHTML = `<span>账号</span><strong>${html(me.username)}</strong><span>所属单位</span><strong>${html(me.unit_name)}</strong><span>默认配送点</span><strong>${html(me.default_delivery_point)}</strong>`;
  }

  activateNav();
  $("logoutButton")?.addEventListener("click", logout);
  $("productSearchButton")?.addEventListener("click", loadProducts);
  $("clearCartButton")?.addEventListener("click", async () => { await api("/unit/cart/items", { method: "DELETE" }); loadCart(); });
  $("submitOrderButton")?.addEventListener("click", submitOrder);
  $("confirmReceiptButton")?.addEventListener("click", confirmReceipt);
  const route = window.location.pathname;
  if (route === "/unit/home") loadHome();
  if (route === "/unit/products") loadProducts();
  if (route === "/unit/cart") loadCart();
  if (route === "/unit/orders") loadOrders();
  if (route.startsWith("/unit/orders/")) loadOrderDetail();
  if (route === "/unit/profile") loadProfile();
})();
