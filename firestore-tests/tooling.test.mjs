// 규칙 테스트가 쓰는 CLI(firebase-tools) 쪽 전이 의존성을 보안 패치 판으로 고정했다(#225).
// 고정한 판이 CLI 가 실제로 부르는 진입점과 API 를 그대로 주는지 여기서 잠근다.
// 에뮬레이터가 필요 없는 검사라 규칙 테스트와 같은 실행에 얹어 둔다.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);

/** firebase-tools 의 `auth:import` 이 부르는 진입점. 5.x 에서 7.x 로 올렸다(GHSA-8cw4-87c7-c6xx). */
test("csv-parse 의 CommonJS 진입점이 그대로 파싱한다", async () => {
    const { parse } = require("csv-parse");
    const parser = parse("uid,email\nuid-owner,owner@example.com\n");
    const records = [];
    for await (const record of parser) {
        records.push(record);
    }
    assert.deepEqual(records, [
        ["uid", "email"],
        ["uid-owner", "owner@example.com"],
    ]);
});

/** 에뮬레이터 허브가 얹혀 있는 express 의 쿼리 파서. GHSA-x5fp-wj9c-mxmx 로 6.16.0 을 강제한다. */
test("qs 가 대괄호 키의 배열 한도를 지킨다", () => {
    const qs = require("qs");
    assert.throws(() => qs.parse("a[]=1,2,3,4", {
        comma: true,
        arrayLimit: 3,
        throwOnLimitExceeded: true,
    }), RangeError);
});

/** gaxios 가 쓰는 uuid. 11.1.1 미만은 작은 버퍼에 조용히 잘린 값을 썼다(GHSA-w5hq-g745-h8pq). */
test("uuid 가 출력 버퍼 경계를 검사한다", () => {
    const { v5 } = require("uuid");
    assert.throws(() => v5("slowclock", v5.DNS, new Uint8Array(8)), RangeError);
});

/** @google-cloud/pubsub 은 @opentelemetry/core ^1.30.1 을 적었지만 2.8.0 에서도 로드된다(GHSA-8988-4f7v-96qf). */
test("pubsub 모듈이 OpenTelemetry 2.x 위에서 로드된다", () => {
    const { PubSub } = require("@google-cloud/pubsub");
    assert.equal(typeof PubSub, "function");
    assert.match(require("@opentelemetry/core/package.json").version, /^2\./);
});
