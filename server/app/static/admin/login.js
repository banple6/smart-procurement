(function () {
  let challengeId = "";
  let pollTimer = null;
  let countdownTimer = null;
  let busy = false;
  let consuming = false;
  let expiresAtMs = 0;
  let serverOffsetMs = 0;

  function $(id) {
    return document.getElementById(id);
  }

  function setStatus(text, state) {
    $("loginStatus").textContent = text;
    $("qrBox").dataset.state = state || "";
  }

  function stopPolling() {
    if (pollTimer) window.clearInterval(pollTimer);
    pollTimer = null;
  }

  function stopCountdown() {
    if (countdownTimer) window.clearInterval(countdownTimer);
    countdownTimer = null;
  }

  function updateCountdown() {
    if (!expiresAtMs) {
      $("qrTimer").textContent = "二维码将在 --:-- 后失效";
      return;
    }
    const remaining = Math.max(0, Math.ceil((expiresAtMs - (Date.now() + serverOffsetMs)) / 1000));
    const minutes = String(Math.floor(remaining / 60)).padStart(2, "0");
    const seconds = String(remaining % 60).padStart(2, "0");
    $("qrTimer").textContent = `二维码将在 ${minutes}:${seconds} 后失效`;
    if (remaining <= 0) {
      stopPolling();
      stopCountdown();
      setStatus("二维码已失效，请点击刷新", "expired");
    }
  }

  function startCountdown(data) {
    expiresAtMs = Number(data.expires_at || 0) * 1000;
    serverOffsetMs = Number(data.server_now || 0) * 1000 - Date.now();
    stopCountdown();
    updateCountdown();
    countdownTimer = window.setInterval(updateCountdown, 1000);
  }

  async function checkExistingSession() {
    const response = await fetch("/api/v1/web-auth/me", { credentials: "same-origin" }).catch(() => null);
    if (response && response.ok) {
      window.location.replace("/web/entry");
    }
  }

  async function createChallenge() {
    if (busy) return;
    busy = true;
    consuming = false;
    stopPolling();
    stopCountdown();
    challengeId = "";
    expiresAtMs = 0;
    $("qrBox").innerHTML = '<div class="skeleton square"></div>';
    setStatus("正在生成二维码...", "loading");
    updateCountdown();
    try {
      const response = await fetch("/api/v1/web-auth/qr/challenges", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Accept": "application/json" },
      });
      if (!response.ok) throw new Error("二维码生成失败，请稍后重试");
      const data = await response.json();
      challengeId = data.challenge_id;
      $("qrBox").innerHTML = `<img src="${data.qr_svg_data_url}" alt="网页登录二维码" />`;
      setStatus("请使用三公鲜配 App 扫码登录", "waiting");
      startCountdown(data);
      pollTimer = window.setInterval(pollStatus, 2000);
    } catch (error) {
      $("qrBox").innerHTML = '<div class="error-inline">二维码生成失败</div>';
      setStatus(error.message || "二维码生成失败，请稍后重试", "error");
    } finally {
      busy = false;
    }
  }

  async function pollStatus() {
    if (!challengeId || document.hidden || consuming) return;
    const response = await fetch(`/api/v1/web-auth/qr/challenges/${challengeId}/status`, { credentials: "same-origin" }).catch(() => null);
    if (!response) {
      setStatus("网络连接失败，请稍后重试", "error");
      return;
    }
    if (response.status === 410 || response.status === 404) {
      stopPolling();
      stopCountdown();
      setStatus("二维码已失效，请点击刷新", "expired");
      return;
    }
    if (!response.ok) return;
    const data = await response.json();
    startCountdown(data);
    if (data.status === "pending") setStatus("请使用三公鲜配 App 扫码登录", "waiting");
    if (data.status === "scanned") setStatus("已扫描，请在手机上确认登录", "scanned");
    if (data.status === "rejected") {
      stopPolling();
      stopCountdown();
      setStatus("登录已取消，请刷新二维码后重试", "rejected");
    }
    if (data.status === "expired") {
      stopPolling();
      stopCountdown();
      setStatus("二维码已失效，请点击刷新", "expired");
    }
    if (data.status === "consumed") {
      stopPolling();
      stopCountdown();
      setStatus("二维码已使用，请刷新后重试", "consumed");
    }
    if (data.status === "approved") consumeChallenge();
  }

  async function consumeChallenge() {
    if (consuming) return;
    consuming = true;
    stopPolling();
    setStatus("登录成功，正在进入系统", "approved");
    const response = await fetch(`/api/v1/web-auth/qr/challenges/${challengeId}/consume`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Accept": "application/json" },
    }).catch(() => null);
    if (!response || !response.ok) {
      consuming = false;
      setStatus("登录状态确认失败，请刷新二维码", "error");
      return;
    }
    await response.json();
    window.location.replace("/web/entry");
  }

  $("refreshQr").addEventListener("click", createChallenge);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && challengeId && !pollTimer && !consuming) {
      pollStatus();
      pollTimer = window.setInterval(pollStatus, 2000);
    }
  });
  checkExistingSession().finally(createChallenge);
})();
