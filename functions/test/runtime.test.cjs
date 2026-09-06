const assert = require("node:assert/strict");
const test = require("node:test");
const express = require("express");
const qs = require("qs");

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
