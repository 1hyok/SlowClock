const assert = require("node:assert/strict");
const test = require("node:test");
const express = require("express");
const qs = require("qs");
const {v4, v5} = require("uuid");

test("qs enforces bracket-key comma array limits", () => {
  assert.throws(() => qs.parse("a[]=1,2,3,4", {
    comma: true,
    arrayLimit: 3,
    throwOnLimitExceeded: true,
  }), RangeError);
});

test("Functions Express parent still parses nested form data", async (t) => {
  const app = express();
  app.use(express.urlencoded({extended: true}));
  app.post("/form", (request, response) => response.json(request.body));
  const server = await new Promise((resolve) => {
    const listening = app.listen(0, "127.0.0.1", () => resolve(listening));
  });
  t.after(() => new Promise((resolve) => server.close(resolve)));
  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/form`, {
    method: "POST",
    headers: {"content-type": "application/x-www-form-urlencoded"},
    body: "person[name]=tester&items[]=one&items[]=two",
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    person: {name: "tester"},
    items: ["one", "two"],
  });
});

// firebase-admin 이 쓰는 uuid 가 v3/v5/v6 의 출력 버퍼 경계를 검사하는 판(11.1.1
// 이상)인지 본다. GHSA-w5hq-g745-h8pq 는 작은 버퍼에 조용히 잘린 값을 쓰는 문제였다.
test("uuid rejects out-of-bounds buffer writes", () => {
  assert.throws(
      () => v5("slowclock", v5.DNS, new Uint8Array(8)),
      RangeError,
  );
  const buffer = new Uint8Array(16);
  assert.equal(v5("slowclock", v5.DNS, buffer), buffer);
  assert.ok(buffer.some((byte) => byte !== 0));
});

// 실제로 호출되는 경로는 firebase-admin 의 v4 다. 11.x 로 올린 뒤에도
// CommonJS 로 불러 쓸 수 있는지 함께 잠근다(12.0.0 이 CommonJS 를 뺐다).
test("uuid still exposes v4 through CommonJS", () => {
  assert.match(
      v4(),
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
});
