"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");

function extractFunction(name) {
  const functionStart = source.indexOf(`function ${name}(`);
  assert.notEqual(functionStart, -1, `missing ${name}`);
  const start = source.slice(Math.max(0, functionStart - 6), functionStart) === "async "
    ? functionStart - 6
    : functionStart;
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`unterminated ${name}`);
}

const timers = new Map();
let nextTimer = 1;
const scheduled = [];
function setTimer(callback, delay) {
  const id = nextTimer++;
  timers.set(id, { callback, delay });
  scheduled.push(delay);
  return id;
}
function clearTimer(id) {
  timers.delete(id);
}
async function runTimers(delay) {
  const due = [...timers.entries()].filter(([, timer]) => timer.delay === delay);
  due.forEach(([id]) => timers.delete(id));
  for (const [, timer] of due) await timer.callback();
}

class FakeEventSource {
  static instances = [];
  constructor(url) {
    this.url = url;
    this.listeners = new Map();
    FakeEventSource.instances.push(this);
  }
  addEventListener(type, callback) {
    this.listeners.set(type, callback);
  }
  emit(type, data) {
    this.listeners.get(type)({ data: JSON.stringify(data) });
  }
}

const state = {
  realtime: {
    eventSource: null,
    connected: false,
    revisions: {},
    dirtyResources: new Set(),
    timers: new Map(),
    fallbackTimer: null,
  },
};
let serverOrderRevision = 0;
const refreshes = [];
const sandbox = {
  state,
  window: { EventSource: FakeEventSource, clearTimeout: clearTimer, setTimeout: setTimer },
  EventSource: FakeEventSource,
  document: { hidden: false },
  setSyncStatus: () => {},
  fetchRealtime: async () => ({ orders: serverOrderRevision }),
  refreshRealtimeResource: async (resource) => {
    refreshes.push(resource);
    return true;
  },
  console,
};

vm.runInNewContext(
  ["queueRealtimeRefresh", "pollRealtimeRevisions", "scheduleRealtimeFallback", "connectRealtime"]
    .map(extractFunction)
    .join("\n"),
  sandbox,
);

const settle = () => new Promise((resolve) => setImmediate(resolve));
async function waitFor(predicate) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (predicate()) return;
    await settle();
  }
  throw new Error("timed out waiting for realtime initialization");
}

(async () => {
  sandbox.connectRealtime();
  await waitFor(() => Boolean(state.realtime.fallbackTimer));
  const source = FakeEventSource.instances[0];
  assert.equal(source.url, "/api/v1/admin/realtime/events");

  source.onopen();
  assert.equal(state.realtime.connected, true);
  assert.equal([...timers.values()].at(-1).delay, 120_000);

  source.onerror();
  assert.equal(state.realtime.connected, false);
  assert.equal([...timers.values()].at(-1).delay, 20_000);

  serverOrderRevision = 1;
  await sandbox.pollRealtimeRevisions();
  assert.equal([...timers.values()].filter((timer) => timer.delay === 300).length, 1);
  await runTimers(300);
  assert.deepEqual(refreshes, ["orders"]);

  source.onopen();
  assert.equal(state.realtime.connected, true);
  assert.equal([...timers.values()].at(-1).delay, 120_000);

  refreshes.length = 0;
  source.emit("resource_changed", { resource: "orders", revision: 2 });
  serverOrderRevision = 2;
  await sandbox.pollRealtimeRevisions();
  await runTimers(300);
  assert.deepEqual(refreshes, ["orders"]);

  refreshes.length = 0;
  source.emit("resource_changed", { resource: "orders", revision: 3 });
  source.emit("resource_changed", { resource: "orders", revision: 4 });
  await runTimers(300);
  assert.deepEqual(refreshes, ["orders"]);
  assert.equal(state.realtime.revisions.orders, 4);
})();
