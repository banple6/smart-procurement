"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");
const dispatcher = source.slice(source.indexOf("async function refreshRealtimeResource"), source.indexOf("function queueRealtimeRefresh"));

assert.match(source, /function renderBatchList\(items\)/);
assert.match(source, /function refreshBatchListIncrementally\(\)/);
assert.match(source, /function bindBatchListEvents\(target\)/);
assert.match(source, /target\.dataset\.bound === "1"/);
assert.match(source, /data-batch-id/);
assert.match(source, /data-batch-version/);
assert.match(source, /patchKeyedRows\(target, items, "\[data-batch-id\]"/);

assert.match(source, /function renderOutboundList\(items\)/);
assert.match(source, /function refreshOutboundListIncrementally\(\)/);
assert.match(source, /function bindOutboundListEvents\(target\)/);
assert.match(source, /data-outbound-id/);
assert.match(source, /data-outbound-version/);
assert.match(source, /patchKeyedRows\(target, items, "\[data-outbound-id\]"/);

assert.match(source, /async function refreshQuotaSummary\(units\)/);
assert.match(source, /data-unit-quota-base/);
assert.match(source, /data-unit-quota-available/);
assert.match(dispatcher, /resource === "batches".*refreshBatchListIncrementally/s);
assert.match(dispatcher, /resource === "outbounds".*refreshOutboundListIncrementally/s);
assert.match(dispatcher, /resource === "quota".*refreshQuotaSummary/s);
assert.doesNotMatch(dispatcher, /loadBatches\(|loadOutbounds\(|loadUnits\(/);
assert.match(source, /route === "\/admin\/batches" \|\| route\.startsWith\("\/admin\/batches\/"\)\) return resource === "batches"/);
assert.match(source, /route === "\/admin\/outbounds" \|\| route\.startsWith\("\/admin\/outbounds\/"\)\) return resource === "outbounds"/);
assert.match(source, /if \(!window\.EventSource\) \{\s*state\.realtime\.connected = false;\s*pollRealtimeRevisions\(\)\.catch\(\(\) => \{\}\)\.finally\(scheduleRealtimeFallback\);/s);
assert.match(source, /function realtimeShouldDefer\(resource\)[\s\S]*state\.selectedProductIds\.size/);
assert.doesNotMatch(source, /resource !== "orders" && \(state\.selectedBatchIds\.size \|\| state\.selectedOutboundIds\.size/);
assert.match(source, /!\["checkbox", "radio", "button", "submit", "reset"\]\.includes\(active\.type\)/);
