"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../app/static/admin/dashboard.js"), "utf8");

function extractFunction(name) {
  const start = source.indexOf(`async function ${name}(`);
  assert.notEqual(start, -1, `missing ${name}`);
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`unterminated ${name}`);
}

assert.match(source, /await reconcileAffectedResources\(\["outbounds", "orders", "dashboard", "quota"\]\)/);
const outboundCompletion = source.slice(source.indexOf("async function openOutboundCompleteReview"), source.indexOf("function outboundRow"));
assert.doesNotMatch(outboundCompletion, /await loadCurrent\(true\);/);

const state = { realtime: { dirtyResources: new Set() } };
const refreshed = [];
const sandbox = {
  state,
  document: { hidden: false },
  Set,
  realtimeRouteUses: (resource) => resource === "outbounds",
  refreshRealtimeResource: async (resource) => {
    refreshed.push(resource);
    return true;
  },
};
vm.runInNewContext(extractFunction("reconcileAffectedResources"), sandbox);

(async () => {
  assert.equal(await sandbox.reconcileAffectedResources(["outbounds", "orders", "dashboard", "quota"]), true);
  assert.deepEqual(refreshed, ["outbounds"]);
  assert.deepEqual([...state.realtime.dirtyResources], []);

  refreshed.length = 0;
  sandbox.document.hidden = true;
  assert.equal(await sandbox.reconcileAffectedResources(["outbounds", "orders"]), false);
  assert.deepEqual(refreshed, []);
  assert.deepEqual([...state.realtime.dirtyResources], ["outbounds"]);
})();
