/*
 * Thinking Orbs adapter for the administrative static site.
 *
 * The drawing engine below is the unmodified 0.3.1 official engine, served
 * locally from vendor/thinking-orbs-engine-0.3.1.es.js. The adapter only adds
 * DOM lifecycle handling so the existing static dashboard can use it without
 * React. Source and MIT license are bundled alongside the engine.
 */
import { MODE_DRAWS, resolvePreset } from "/admin-assets/vendor/thinking-orbs-engine-0.3.1.es.js";

const instances = new WeakMap();
const labels = {
  working: "工作中",
  searching: "检索中",
  solving: "求解中"
};

function resolveDark(host) {
  const themed = host.closest("[data-theme]");
  if (themed?.dataset.theme === "dark") return true;
  if (themed?.dataset.theme === "light") return false;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches === true;
}

function mountThinkingOrb(host, options = {}) {
  if (!host) return;
  instances.get(host)?.destroy();

  const state = labels[options.state] ? options.state : "working";
  const active = options.active !== false;
  const size = 64;
  const canvas = document.createElement("canvas");
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const context = canvas.getContext("2d");
  if (!context) return;

  canvas.className = "thinking-orb-canvas";
  canvas.setAttribute("role", "img");
  canvas.setAttribute("aria-label", options.ariaLabel || labels[state]);
  canvas.width = Math.round(size * dpr);
  canvas.height = Math.round(size * dpr);
  canvas.style.width = `${size}px`;
  canvas.style.height = `${size}px`;
  canvas.style.display = "block";
  host.replaceChildren(canvas);

  const { mode, speed, opts } = resolvePreset(state, size);
  const draw = MODE_DRAWS[mode];
  const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
  let raf = 0;
  let running = false;
  let intersecting = true;

  const frame = (timeSeconds) => {
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, size, size);
    draw(context, size, timeSeconds, resolveDark(host), opts);
  };
  const stop = () => {
    running = false;
    window.cancelAnimationFrame(raf);
  };
  const loop = () => {
    frame((window.performance.now() / 1000) * speed);
    if (running) raf = window.requestAnimationFrame(loop);
  };
  const start = () => {
    if (running || !active || reducedMotion) return;
    running = true;
    raf = window.requestAnimationFrame(loop);
  };
  const onVisibilityChange = () => {
    if (document.visibilityState === "hidden") stop();
    else if (intersecting) start();
  };
  const observer = typeof IntersectionObserver === "undefined" ? null : new IntersectionObserver(([entry]) => {
    intersecting = entry.isIntersecting;
    if (intersecting && document.visibilityState !== "hidden") start();
    else stop();
  });

  // This is also the official component's deterministic reduced-motion frame.
  frame(reducedMotion ? 0.6 : (window.performance.now() / 1000) * speed);
  observer?.observe(canvas);
  document.addEventListener("visibilitychange", onVisibilityChange);
  if (!observer) start();

  instances.set(host, {
    destroy() {
      stop();
      observer?.disconnect();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    }
  });
}

window.SangongThinkingOrb = { mount: mountThinkingOrb };
window.dispatchEvent(new Event("sangong-thinking-orb-ready"));

export { mountThinkingOrb };
