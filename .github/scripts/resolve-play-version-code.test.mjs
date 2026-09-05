import assert from "node:assert/strict";
import test from "node:test";

import { resolvePlayVersionCode } from "./resolve-play-version-code.mjs";

test("derives distinct increasing codes for runs and reruns", () => {
  const first = resolvePlayVersionCode("1", "1", "0");
  const rerun = resolvePlayVersionCode("1", "2", String(first));
  const nextRun = resolvePlayVersionCode("2", "1", String(rerun));

  assert.equal(first, 101);
  assert.ok(first < rerun);
  assert.ok(rerun < nextRun);
});

test("fails when the candidate is not greater than Play", () => {
  assert.throws(
    () => resolvePlayVersionCode("10", "1", "1001"),
    /must be greater than current Play versionCode/,
  );
});

test("rejects invalid run metadata and excessive attempts", () => {
  assert.throws(() => resolvePlayVersionCode("0", "1", "0"), /must be at least 1/);
  assert.throws(() => resolvePlayVersionCode("1", "0", "0"), /must be at least 1/);
  assert.throws(() => resolvePlayVersionCode("1", "100", "0"), /must be lower than 100/);
  assert.throws(() => resolvePlayVersionCode("1.5", "1", "0"), /base-10 integer/);
});

test("fails before exceeding the Google Play maximum", () => {
  assert.equal(resolvePlayVersionCode("20999999", "99", "0"), 2_099_999_999);
  assert.throws(
    () => resolvePlayVersionCode("21000000", "1", "0"),
    /exceeds Google Play maximum/,
  );
});
